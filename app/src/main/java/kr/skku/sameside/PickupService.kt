package kr.skku.sameside

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 진행 중인 픽업 하나를 들고, 차 신호·위치·서버 상태를 Pick.step에 넣고 돌려받은 할 일을 실행한다.
 * 판단은 하지 않는다(Pick.kt). 모든 상태는 메인 스레드에서만 만진다. HTTP는 io 스레드 하나에서 순서대로 보낸다.
 * 느린 망에서 요청이 쌓이지 않게 조회는 하나, 올리기는 하나만 띄운다. 그사이 바뀐 것은 끝난 뒤 최신 상태로 한 번 보낸다.
 */
class PickupService : Service() {

    companion object {
        private const val TAG = "PickupService"
        private const val CH = "pickup"
        private const val ID = 1
        private const val CALLING_MS = 60_000L // 전화 건 뒤 이만큼은 페이지에 전화 띠(FR-067)
        private const val POLL_MS = 2_000L
        private const val RETRY_MS = 10_000L
        private const val LOCATION = "pleos.car.permission.FUSED_LOCATION"

        // 같은 프로세스의 MainActivity가 메인 스레드에서 읽는다
        var pickup: Pickup? = null
            private set
        var park: Boolean? = null
            private set
        var serverDown = false
            private set
        /** 링크를 만드는 중이다(서버에 못 만들어 다시 시도하는 중 포함) */
        val creating get() = instance?.waiting != null
        /** 3차: 기기에 진행 중인 통화가 있다(null = 볼 수 없음)와 그 상태가 시작된 때(elapsedRealtime). research R22 */
        var inCall: Boolean? = null
            private set
        var callSince: Long? = null
            private set
        var onUpdate: (() -> Unit)? = null
        private var instance: PickupService? = null

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, PickupService::class.java))

        /** 사람 줄을 누르면(정차 중). 진행 중인 픽업이 있으면 무시한다 */
        fun begin(person: Person) = instance?.begin(listOf(person))

        /** 3차 "n명 함께 데리러 가기": 사람마다 기록을 만든다 */
        fun beginGroup(persons: List<Person>) = instance?.begin(persons)

        fun send(e: Event) = instance?.handle(e)

        /** 끝 화면의 "확인" */
        fun dismiss() {
            if (pickup?.status?.ended == true) { pickup = null; onUpdate?.invoke() }
        }
    }

    private val area = DEMO
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val relay = Relay()
    private val vehicle by lazy { Vehicle(applicationContext) }
    private var fused: FusedLocation? = null
    private var navi: NaviHelper? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var stopped = false

    // 픽업이 없을 때도 받아 두는 마지막 값(새 픽업의 시작값, data-model §7 보충)
    private var lastPos: LatLon? = null
    private val seats = mutableMapOf<String, Boolean>()
    private var waiting: List<Person>? = null // 서버에 못 만든 채 다시 시도하는 사람들
    private var made = mutableListOf<Relay.Created?>() // waiting 순서. 만든 사람은 다시 만들지 않는다
    private var making: List<Person>? = null // 만들기 요청이 가는 사람들(같은 사람을 다시 눌러도 하나만)
    private var pendingWaypoint: Spot? = null
    // 내비 방식(2차 data-model §7). 픽업마다 새로 시작하고, SetNavi마다 토큰을 바꿔 늦은 콜백을 버린다
    private enum class Navi { NONE, ASKING, WAYPOINT } // NONE은 목적지 방식(또는 아직 넣지 않음)
    private var naviMode = Navi.NONE
    private var naviOf: Pickup? = null
    private var token = 0
    private var asking: Spot? = null
    private var dest: String? = null // 내비 답. null이면 아직 안 왔다
    private var waypoints: List<String>? = null
    private var nextAdd: Spot? = null // 경유지 방식: 진입 경유지 ADD를 받으면 이 지점을 SECOND로 넣는다
    private var afterRemove: Runnable? = null
    // 끝 상태를 서버가 아직 받지 않은 사람 기록. 확인을 누르거나 새 픽업을 시작해도 받을 때까지 poll마다 다시 보낸다
    private class EndSend(val id: String, val key: String, val json: JSONObject)
    private val unsentEnds = mutableListOf<EndSend>()
    private var endSending = false
    private var getting = false
    private var putting = false
    private var dirty = false // 올릴 최신 상태가 있다(보내는 중에 바뀌었거나 보내기가 실패했다)

    private fun now() = SystemClock.elapsedRealtime()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel(CH, "픽업 도우미", NotificationManager.IMPORTANCE_MIN))
        }
        ServiceCompat.startForeground(this, ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        startTts()
        watchCar()
        watchLocation()
        startNavi()
        main.postDelayed(::poll, POLL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        stopped = true
        instance = null
        main.removeCallbacksAndMessages(null)
        runCatching { vehicle.release() }
        runCatching { fused?.release() }
        runCatching { navi?.release() }
        runCatching { tts?.release() }
        io.shutdownNow()
        super.onDestroy()
    }

    /* ---------- 픽업 시작과 이벤트 ---------- */

    /** 사람마다 서버 기록을 만든다. 실패한 사람만 2초 뒤 다시 보내고, 모두 만들어지면 픽업이다(3차 data-model §9) */
    private fun begin(persons: List<Person>) {
        if (pickup?.status?.ended == false) return
        if (waiting == persons && making == persons) return
        if (waiting != persons) {
            // 다시 시도하는 사이 다른 사람을 골랐다: 이미 만든 기록은 링크가 살아 있지 않게 끝낸다
            made.filterNotNull().forEach { unsentEnds += EndSend(it.id!!, it.key!!, JSONObject().put("status", Status.CANCELED.wire)) }
            sendEnds()
            waiting = persons; made = MutableList(persons.size) { null }
        }
        making = persons
        val car = People.loadCar(this)
        val todo = persons.indices.filter { made[it] == null }
        io.execute {
            val got = todo.map { it to relay.create(persons[it].name, car) }
            main.post {
                if (making == persons) making = null
                if (waiting != persons) { // 그사이 다른 사람을 골랐다: 방금 만든 기록도 끝낸다
                    got.forEach { (_, c) -> if (c.id != null && c.key != null) unsentEnds += EndSend(c.id, c.key, JSONObject().put("status", Status.CANCELED.wire)) }
                    sendEnds()
                    return@post
                }
                got.forEach { (i, c) -> if (c.code == 201 && c.id != null && c.key != null) made[i] = c }
                if (made.all { it != null }) {
                    waiting = null
                    serverDown = false
                    pickup = Pickup(persons.indices.map { Member(persons[it], made[it]!!.id!!, made[it]!!.key!!) }, now()).also { p ->
                        p.park = park
                        lastPos?.let { p.track += Fix(it, now()) }
                        p.emptySeatsAtStart = seats.filterValues { !it }.keys
                    }
                    // 링크 id는 위치를 볼 수 있는 열쇠라 로그에 남기지 않는다
                    Log.i(TAG, "픽업 시작, 빈 좌석 ${pickup?.emptySeatsAtStart}" + if (persons.size > 1) ", ${persons.size}명" else "")
                    push()
                } else {
                    serverDown = true
                    main.postDelayed({ if (waiting == persons) begin(persons) }, POLL_MS)
                }
                onUpdate?.invoke()
            }
        }
        onUpdate?.invoke()
    }

    private var batching = false // poll 결과를 한꺼번에 넣는 동안 화면은 마지막에 한 번만 그린다

    private fun handle(e: Event) {
        when (e) {
            is Event.Gear -> park = e.park
            is Event.CarPos -> lastPos = e.pos
            is Event.Seat -> seats[e.area] = e.occupied
            else -> Unit
        }
        val p = pickup?.takeUnless { it.status.ended }
        if (e is Event.Gear) Log.i(TAG, "기어 ${if (e.park) "P" else "P 아님"}")
        if (p != null) {
            val wasOut = p.members.map { it.outNow }
            run(Pick.step(area, p, e, now()))
            p.members.forEachIndexed { i, m -> if (!wasOut[i] && m.outNow) Log.i(TAG, "알림 보냄") } // 시연이 알림 시각을 기기 로그로 잰다
            if (e is Event.Arrive) Log.i(TAG, "도착 확인 ${if (e.send) "보내기" else "아니요"}: arrived=${p.arrived}")
        }
        if (!batching && (p != null || e is Event.Gear)) onUpdate?.invoke() // 기어는 픽업이 없어도 사람 줄을 켜고 끈다
    }

    private fun run(actions: List<Action>) = actions.forEach { a ->
        when (a) {
            is Action.Speak -> speak(a.text)
            is Action.CallOrNotify -> callOrNotify(a)
            is Action.SetNavi -> setNavi(a.spot)
            Action.ClearNavi -> clearNavi()
            Action.Push -> push() // 끝난 픽업은 보내지 않는다. End가 끝 상태를 보낸다
            Action.End -> pickup?.let { p -> // 빠지지 않은 사람마다 끝 상태(빠진 사람은 이미 끝났다)
                p.active.forEach { unsentEnds += EndSend(it.id, it.key, Relay.carJson(p, it, null)) }
                sendEnds()
            }
            is Action.EndMember -> pickup?.members?.getOrNull(a.index)?.let { // 함께 태우기에서 거절한 사람
                unsentEnds += EndSend(it.id, it.key, JSONObject().put("status", Status.CANCELED.wire))
                sendEnds()
            }
        }
    }

    /** 2초마다: 통화 상태를 보고, 밀린 끝 상태와 올리기를 다시 보내고, 사람마다 서버 상태를 읽은 뒤 Tick */
    private fun poll() {
        if (stopped) return
        main.postDelayed(::poll, POLL_MS)
        watchCall()
        sendEnds()
        flush()
        val p = pickup?.takeUnless { it.status.ended } ?: return
        if (getting) return
        getting = true
        val who = p.members.withIndex().filter { !it.value.out }.map { Triple(it.index, it.value.id, it.value.key) }
        io.execute {
            val got = who.map { (i, id, key) -> i to relay.get(id, key, i) }
            main.post {
                getting = false
                if (pickup !== p) return@post
                serverDown = got.any { it.second.first == -1 }
                batching = true
                for ((i, r) in got) {
                    val (code, remote) = r
                    when {
                        code == 404 -> handle(Event.Gone(i))
                        remote != null -> handle(remote)
                    }
                }
                handle(Event.Tick)
                batching = false
                onUpdate?.invoke()
            }
        }
    }

    /** 통화 상태(READ_PHONE_STATE). 바뀌면 연락 줄과 페이지 전화 띠를 다시 올린다(3차 data-model §9) */
    private fun watchCall() {
        val now = runCatching {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                getSystemService(TelecomManager::class.java).isInCall else null
        }.getOrNull()
        if (now == inCall) return
        inCall = now
        callSince = if (now == true) now() else null
        Log.i(TAG, "통화 중 $now")
        if (pickup?.status?.ended == false) { push(); onUpdate?.invoke() }
    }

    /** 페이지 전화 띠: 전화한 뒤 60초, 또는 이 사람이 가장 최근에 전화한 사람이고 통화가 이어지는 동안(FR-067) */
    private fun calling(p: Pickup, m: Member): Boolean {
        val at = m.callAt ?: return false
        if (m.phoned != true) return false
        return now() - at < CALLING_MS || (p.lastCaller === m && inCallWith(m, inCall, callSince))
    }

    private fun push() {
        dirty = true
        flush()
    }

    /** 빠지지 않은 사람마다 그 사람 값을 차례로 올린다. 409는 그 사람 기록만 다시 읽는다 */
    private fun flush() {
        val p = pickup?.takeUnless { it.status.ended } ?: return
        if (putting || !dirty) return
        dirty = false
        putting = true
        p.lastPushAt = now()
        val car = People.loadCar(this)
        val jobs = p.members.withIndex().filter { !it.value.out }.map { (i, m) -> Triple(i, m, Relay.carJson(p, m, car, calling(p, m))) }
        io.execute {
            val codes = jobs.map { (i, m, json) -> i to relay.putCar(m.id, m.key, json) }
            main.post {
                putting = false
                if (pickup !== p) return@post
                serverDown = codes.any { it.second == -1 }
                for ((i, code) in codes) when (code) {
                    404 -> handle(Event.Gone(i))
                    409 -> regain(p, i) // 끝 상태가 서버에 있다(예: 공유 끄기). 다시 읽어 그 상태로 끝낸다
                    -1, in 500..599 -> dirty = true // 다음 poll에서 최신 상태로 다시
                }
                if (codes.none { it.second == -1 || it.second in 500..599 }) flush() // 보내는 사이 바뀐 것
                onUpdate?.invoke()
            }
        }
    }

    private fun regain(p: Pickup, i: Int) {
        val m = p.members[i]
        io.execute {
            val (c, r) = relay.get(m.id, m.key, i)
            main.post { if (pickup === p) if (r != null) handle(r) else if (c == 404) handle(Event.Gone(i)) }
        }
    }

    /** 끝 상태는 서버가 받을 때까지(204, 이미 끝났으면 409, 지워졌으면 404) 하나씩 다시 보낸다 */
    private fun sendEnds() {
        val e = unsentEnds.firstOrNull() ?: return
        if (endSending) return
        endSending = true
        io.execute {
            val code = relay.putCar(e.id, e.key, e.json)
            main.post {
                endSending = false
                if (code == 204 || code == 409 || code == 404) { unsentEnds.remove(e); sendEnds() }
            }
        }
    }

    /* ---------- 할 일 실행 ---------- */

    private fun startTts() {
        val t = TextToSpeech(applicationContext, Mode.ON_DEVICE)
        tts = t
        val retry = { why: Any? ->
            Log.w(TAG, "TTS 초기화 실패, ${RETRY_MS / 1000}초 뒤 다시 시도: $why")
            runCatching { t.release() }
            main.postDelayed({ if (!stopped) startTts() }, RETRY_MS)
        }
        try {
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching { t.setLocale(Locale.KOREA) }
                    ttsReady = true
                }
                override fun onFailure(error: ErrorType) { main.post { retry(error) } }
            })
        } catch (e: Exception) {
            retry(e)
        }
    }

    private fun speak(text: String) {
        Log.i(TAG, "말하기: $text")
        if (!ttsReady) return Unit.also { Log.w(TAG, "TTS 준비 안 됨, 음성 생략") }
        runCatching { tts?.speak(text, true) }.onFailure { Log.w(TAG, "TTS 발화 실패", it) }
    }

    /**
     * research R4: 권한을 먼저 보고, 되면 말하고 2초 뒤 placeCall. 안 되면 "폰으로 알렸어요" 한 줄.
     * 3차: 한 일을 그 사람의 phoned로 남기고, 60초 뒤 전화 띠가 꺼진 값을 한 번 더 올린다
     */
    private fun callOrNotify(a: Action.CallOrNotify) {
        val p = pickup
        val can = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val m = p?.members?.getOrNull(a.who)
        m?.phoned = can
        if (p != null) {
            push() // 같은 걸음에서 이 전화보다 먼저 나간 Push는 전화 띠가 꺼진 값이었다(코드 리뷰 반영)
            main.postDelayed({ if (pickup === p && !p.status.ended) { push(); onUpdate?.invoke() } }, CALLING_MS)
        }
        if (!can) {
            if (a.speak) speak("${a.name}님 폰으로 알렸어요")
            return
        }
        if (a.speak) speak("${a.name}님께 전화 걸게요")
        // ponytail: 고정 지연. TTS 끝 콜백으로 바꿀 수 있다
        main.postDelayed({
            try {
                getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", a.phone, null), Bundle())
                Log.i(TAG, "전화 걸기 요청") // 번호는 로그에 남기지 않는다. 주행 중 헤드업은 Pleos가 보이지 않아 두지 않는다(research R23)
            } catch (e: Exception) {
                Log.w(TAG, "전화 걸기 실패", e) // 이미 "전화 걸게요"라고 했다. 음성 상한을 넘기지 않게 더 말하지 않는다
                if (pickup === p && m != null) { m.phoned = false; push(); onUpdate?.invoke() } // 화면·페이지는 대체 알림으로
            }
        }, 2_000)
    }

    /**
     * 2차 data-model §7: 내비에 원래 목적지가 있으면 그대로 두고 진입·지점을 경유지로 끼워 넣는다(경유지 방식).
     * 아니면 1차처럼 지점을 목적지로 넣는다(목적지 방식)
     */
    private fun setNavi(spot: Spot) {
        val n = navi ?: return Unit.also { Log.w(TAG, "내비 연결 전이라 목적지를 넣지 못함") }
        val p = pickup ?: return
        if (naviOf !== p) { naviOf = p; naviMode = Navi.NONE }
        val t = ++token
        pendingWaypoint = null; nextAdd = null; afterRemove = null
        if (naviMode == Navi.WAYPOINT) { // 대중교통으로 지점이 바뀌었다: 경유지만 지우고 다시 넣는다
            val go = object : Runnable {
                override fun run() { if (afterRemove === this && valid(t)) { afterRemove = null; addWaypoints(n, spot, t) } }
            }
            afterRemove = go
            runCatching { n.removeWaypoint(WaypointIndex.ALL) }
            main.postDelayed(go, 3_000)
            return
        }
        naviMode = Navi.ASKING
        asking = spot
        dest = null; waypoints = null
        runCatching { n.getDestinationInfo(); n.getWaypointInfo() }.onFailure { Log.w(TAG, "내비 상태 묻기 실패", it) }
        main.postDelayed({ if (naviMode == Navi.ASKING && valid(t)) decide(n, spot) }, 3_000) // 3초 안에 안 온 답은 없음
    }

    private fun valid(t: Int) = t == token && pickup === naviOf && pickup?.status?.ended == false

    /** 목적지·경유지 답이 둘 다 오면 바로, 아니면 3초에 정한다 */
    private fun answered() {
        val n = navi ?: return
        val s = asking ?: return
        if (naviMode == Navi.ASKING && dest != null && waypoints != null && valid(token)) decide(n, s)
    }

    private fun decide(n: NaviHelper, spot: Spot) {
        asking = null
        val own = People.loadNavOwn(this)
        val original = dest?.takeIf { it.isNotBlank() && it != own }
        // 경유지 답이 오지 않았으면 차 있는 것으로 본다. 모르는 채 경유지 방식으로 가면 나중에 removeWaypoint(ALL)이 운전자의 경유지까지 지운다
        val busy = waypoints?.isNotEmpty() ?: true
        if (original != null && !busy) {
            naviMode = Navi.WAYPOINT
            Log.i(TAG, "내비: 경유지 방식, 원래 목적지 $original")
            addWaypoints(n, spot, token)
            handle(Event.Route(original, null))
        } else {
            naviMode = Navi.NONE
            Log.i(TAG, "내비: 목적지 방식" + (original?.let { " (경유지가 차 있어 원래 목적지 $it 대신)" } ?: ""))
            destination(n, spot)
            People.saveNavOwn(this, spot.line)
            handle(Event.Route(null, original))
        }
    }

    /** 경유지 방식: 진입 경유지를 FIRST로 넣고, ADD를 받으면 지점을 SECOND로 넣는다. 10초 안에 안 오면 로그만 */
    private fun addWaypoints(n: NaviHelper, spot: Spot, t: Int) {
        nextAdd = spot
        runCatching { n.addWaypoint(waypoint(spot.waypoint, "진입", WaypointIndex.FIRST)) }.onFailure { Log.w(TAG, "경유지 넣기 실패", it) }
        main.postDelayed({
            if (t == token && nextAdd === spot) { nextAdd = null; Log.w(TAG, "경유지 ADD가 10초 안에 오지 않아 지점을 넣지 못함") }
        }, 10_000)
    }

    private fun waypoint(pos: LatLon, name: String, i: WaypointIndex) = RequestWaypointInfo(latitude = pos.lat, longitude = pos.lon,
        poiName = name, waypointIndex = i, poiId = "", address = "", poiSubId = "0")

    /** 대기 중이면 대기만 버린다(아직 아무것도 넣지 않았다). 경유지 방식이면 경유지만, 목적지 방식이면 안내를 지운다 */
    private fun clearNavi() {
        val mode = naviMode
        token++
        naviMode = Navi.NONE
        asking = null; pendingWaypoint = null; nextAdd = null; afterRemove = null
        when (mode) {
            Navi.ASKING -> Unit
            Navi.WAYPOINT -> runCatching { navi?.removeWaypoint(WaypointIndex.ALL) }
            else -> runCatching { navi?.cancelRoute() }
        }
    }

    /** 목적지 방식(1차, research R1): cancelRoute → requestRoute → onRouteStarted를 받으면 addWaypoint(FIRST) */
    private fun destination(n: NaviHelper, spot: Spot) {
        runCatching { n.cancelRoute() }
        pendingWaypoint = spot
        runCatching {
            n.requestRoute(RouteInfo(latitude = spot.pos.lat, longitude = spot.pos.lon, displayLatitude = null,
                displayLongitude = null, poiName = spot.line, poiId = "", address = "", poiSubId = "0"))
        }.onFailure { Log.w(TAG, "목적지 넣기 실패", it) }
        main.postDelayed({
            if (pendingWaypoint === spot) { pendingWaypoint = null; Log.w(TAG, "onRouteStarted가 10초 안에 오지 않아 경유지를 넣지 못함") }
        }, 10_000)
    }

    private fun startNavi() {
        val n = NaviHelper(applicationContext)
        val retry = { e: Throwable ->
            Log.w(TAG, "내비 초기화 실패, ${RETRY_MS / 1000}초 뒤 다시 시도", e)
            runCatching { n.release() }
            main.postDelayed({ if (!stopped) startNavi() }, RETRY_MS)
        }
        runCatching {
            n.initialize({
                n.addListener(object : NaviHelperEventListener {
                    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
                        main.post {
                            val s = pendingWaypoint ?: return@post
                            pendingWaypoint = null
                            runCatching { n.addWaypoint(waypoint(s.waypoint, "진입", WaypointIndex.FIRST)) }.onFailure { Log.w(TAG, "경유지 넣기 실패", it) }
                            Log.i(TAG, "내비: 목적지 ${s.line}, 진입 경유지 넣음")
                        }
                    }
                    override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
                        val cmd = waypointChangedInfo.cmdType
                        Log.i(TAG, "내비 경유지 $cmd ${waypointChangedInfo.index}")
                        main.post {
                            if (cmd == WaypointCmdType.REMOVE) afterRemove?.run()
                            val s = nextAdd
                            if (cmd == WaypointCmdType.ADD && s != null && valid(token)) {
                                nextAdd = null
                                runCatching { n.addWaypoint(waypoint(s.pos, s.line, WaypointIndex.SECOND)) }.onFailure { Log.w(TAG, "지점 경유지 넣기 실패", it) }
                                Log.i(TAG, "내비: 경유지 진입·${s.line} 넣음")
                            }
                        }
                    }
                    override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                        val name = destinationInfo.name
                        main.post { dest = name.orEmpty(); answered() }
                    }
                    override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
                        val names = waypointInfoList.map { it.name }
                        main.post { waypoints = names; answered() }
                    }
                    override fun onError(errorCode: NaviErrorCode) { Log.w(TAG, "내비 오류 $errorCode") }
                })
                navi = n
            }, { e -> main.post { retry(e) } })
        }.onFailure { retry(it) }
    }

    /* ---------- 차 신호와 위치(DRIVE DNA CarWatcher 방식) ---------- */

    private fun watchCar() {
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle 초기화 실패, ${RETRY_MS / 1000}초 뒤 다시 시도", e)
            main.postDelayed({ if (!stopped) watchCar() }, RETRY_MS)
            return
        }
        runCatching {
            vehicle.getGear().registerGearSelection(object : GearSelectionListener {
                override fun onSelected(g: VehicleGear?) {
                    if (g != null && g != VehicleGear.GEAR_UNKNOWN) main.post { handle(Event.Gear(g == VehicleGear.GEAR_PARK)) }
                }
                override fun onFailed(e: Exception) { Log.i(TAG, "기어 일시 실패: ${e.message}") }
            })
        }.onFailure { Log.w(TAG, "기어 구독 실패", it) }
        for (d in listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)) runCatching {
            vehicle.getDoor().registerDoorPosition(DoorArea(d), object : DoorPositionListener {
                override fun onPositionUpdated(position: Int?) {
                    Log.i(TAG, "문 ${d.name} = $position")
                    if (position != null) main.post { handle(Event.Door(d.name, position > 0)) }
                }
                override fun onFailed(e: Exception) { Log.i(TAG, "문 $d 일시 실패: ${e.message}") }
            })
        }.onFailure { Log.w(TAG, "문 $d 구독 실패", it) }
        runCatching { // 2차 도착 확인(research R16): 오른쪽 깜빡이
            vehicle.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                    Log.i(TAG, "깜빡이 = $value")
                    if (value != null) main.post { handle(Event.TurnSignal(value == TurnSignalValue.RIGHT)) }
                }
                override fun onFailed(e: Exception) { Log.i(TAG, "깜빡이 일시 실패: ${e.message}") }
            })
        }.onFailure { Log.w(TAG, "깜빡이 구독 실패", it) }
        for (s in listOf(SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)) runCatching {
            vehicle.getSeat().registerSeatOccupancy(SeatArea(s), object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occupied: Boolean?) {
                    Log.i(TAG, "좌석 ${s.name} = $occupied")
                    if (occupied != null) main.post { handle(Event.Seat(s.name, occupied)) }
                }
                override fun onFailed(e: Exception) { Log.i(TAG, "좌석 $s 일시 실패: ${e.message}") }
            })
        }.onFailure { Log.w(TAG, "좌석 $s 구독 실패", it) }
    }

    private fun watchLocation() {
        // 첫 실행에는 권한 창이 떠 있는 동안 서비스가 먼저 뜬다. 허용되면 다음 확인에서 켠다
        if (checkSelfPermission(LOCATION) != PackageManager.PERMISSION_GRANTED) {
            main.postDelayed({ if (!stopped) watchLocation() }, RETRY_MS)
            return
        }
        val f = FusedLocation(applicationContext)
        if (!f.isSupported()) return Unit.also { Log.w(TAG, "FusedLocation을 지원하지 않는 환경, 차 위치 없이 동작") }
        try {
            f.initialize()
            f.registerFusedLocationCallback(object : FusedLocationListener {
                override fun onChanged(data: LocationData?) {
                    if (data != null) main.post { handle(Event.CarPos(LatLon(data.latitude, data.longitude))) }
                }
                override fun onError(e: Exception) { Log.w(TAG, "위치 오류 ${e.message}") }
            })
            fused = f
        } catch (e: Exception) {
            Log.w(TAG, "위치 초기화 실패, ${RETRY_MS / 1000}초 뒤 다시 시도", e)
            main.postDelayed({ if (!stopped) watchLocation() }, RETRY_MS)
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_pin)
            .setContentTitle("같은편")
            .setContentText("픽업을 도와요")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
