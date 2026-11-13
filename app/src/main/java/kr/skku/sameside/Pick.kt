package kr.skku.sameside

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign

/** 판단에 쓰는 수치는 모두 여기 있다. 검증 전 잠정값이다(research R12) */
object Rules {
    const val WALK_M = 300.0
    const val WALK_M_CAREFUL = 150.0
    const val AMBIGUOUS_M = 3.0
    const val UTURN_DEG = 100.0
    const val UTURN_SKIP_M = 50.0
    const val WAYPOINT_M = 100.0
    const val DETOUR = 1.3
    const val WALK_M_PER_MIN = 67.0
    const val CROSS_SEC = 60.0
    const val CROSS_NEAR_M = 80.0
    const val PICK_KMH = 25.0
    const val MIN_KMH = 20.0
    const val SPEED_WINDOW_MS = 30_000L
    const val TRACK_MS = 60_000L
    const val HEADING_M = 8.0
    const val STOP_WINDOW_MS = 5_000L
    const val STOP_M = 5.0
    const val PASSED_WAYPOINT_M = 30.0
    const val CALL_BUFFER_SEC = 60.0
    const val CALL_MIN_SEC = 120.0
    const val OUT_M = 30.0
    const val ON_ROAD_M = 25.0 // 지점 길 위: 서부로처럼 상·하행 선이 17m 떨어진 길은 맞은편 줄도 같은 길로 본다
    const val RIGHT_DEG = 45.0 // 150m: 진행 방향이 지점 방향과 이만큼 안이면 오른쪽
    const val CROSS_DEG = 135.0 // 이만큼 넘게 어긋나면 반대 방향 진입. 사이는 꺾는 중이라 기다린다
    const val NEAR_SPOT_M = 150.0
    const val LAST_SPOT_M = 50.0
    const val WRONG_SIDE_SEC = 180.0
    const val GAIN_SEC = 300.0
    const val BOARD_M = 50.0
    const val GOING_EXTRA_MS = 30 * 60_000L
    const val WAIT_MS = 60 * 60_000L
    const val VOICE_CAP = 5
    const val PUSH_GAP_MS = 1_000L
    const val DRAW_M = 150.0
    const val MAX_PEOPLE = 20
    const val DRIVER_DOOR = "ROW_1_LEFT" // 운전자가 타고 내리는 문은 탑승이 아니다
    // 2차(research R19)
    const val CALL_WAIT_MS = 60_000L // 알림 뒤 반응을 기다리는 시간
    const val MOVED_M = 20.0 // 알림 때 자리에서 이만큼 넘게 움직이면 반응
    const val ARRIVE_STOP_MS = 3_000L // 도착 확인: 이 시간 동안 STOP_M 미만으로 움직였으면 서 있음
    const val UTURN_NEAR_M = 600.0
    const val UTURN_WAIT_SEC = 90.0
    const val PASS_MIN_M = 20.0
    const val PASS_MAX_M = 150.0
    // 3차(specs/003-group-pickup)
    const val MAX_GROUP = 3 // 함께 태울 수 있는 사람 수
    const val CALL_GAP_MS = 60_000L // 함께 태우기: 앞 전화 뒤 이만큼은 다음 전화를 미룬다(FR-058)
    const val STOP_SEC = 60.0 // 근처로 데리러 가기 어림: 들를 때마다(FR-055)
    const val NEARBY_GAP_SEC = 60.0 // 근처 어림이 함께 만나는 시간보다 이만큼 넘게 늦을 때만 보인다(FR-055)
    const val OUR_CALL_MS = 30_000L // 앱이 건 전화 뒤 이만큼 안에 시작된 통화만 그 사람과의 통화로 본다(코드 리뷰 반영)
    // 5차(specs/005-chain-pickup research R43)
    const val ROAD_KMH = 40.0 // 마지막 지점 → 원래 목적지 어림 속도
}

/* ---------- 기하(data-model §3). 원점 o를 기준으로 m 단위 평면(x 동쪽, y 북쪽)에서 계산한다 ---------- */

data class XY(val x: Double, val y: Double) {
    operator fun minus(b: XY) = XY(x - b.x, y - b.y)
    operator fun plus(b: XY) = XY(x + b.x, y + b.y)
    operator fun times(k: Double) = XY(x * k, y * k)
    val len get() = hypot(x, y)
}

private fun cross(a: XY, b: XY) = a.x * b.y - a.y * b.x

fun xy(o: LatLon, p: LatLon) = XY((p.lon - o.lon) * cos(Math.toRadians(o.lat)) * 111_320.0, (p.lat - o.lat) * 110_540.0)

fun ll(o: LatLon, q: XY) = LatLon(o.lat + q.y / 110_540.0, o.lon + q.x / (cos(Math.toRadians(o.lat)) * 111_320.0))

fun dist(o: LatLon, a: LatLon, b: LatLon) = (xy(o, b) - xy(o, a)).len

/** 0°가 북쪽, 시계 방향 */
fun bearing(o: LatLon, a: LatLon, b: LatLon): Double = vecBearing(xy(o, b) - xy(o, a))

private fun vecBearing(v: XY) = (Math.toDegrees(atan2(v.x, v.y)) + 360.0) % 360.0

fun headingVec(deg: Double) = XY(kotlin.math.sin(Math.toRadians(deg)), cos(Math.toRadians(deg)))

fun angleDiff(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return if (d > 180.0) 360.0 - d else d
}

/**
 * 길 선에서 가장 가까운 선분. offset은 선분 직선까지의 부호 있는 거리다(왼쪽 +, 오른쪽 −).
 * along은 길 선 처음부터 발까지의 거리, dirDeg는 그 선분의 진행 방위다.
 */
data class Proj(val offset: Double, val distance: Double, val along: Double, val dirDeg: Double)

fun project(o: LatLon, road: Road, p: LatLon): Proj {
    val q = xy(o, p)
    val pts = road.points.map { xy(o, it) }
    var best: Proj? = null
    var start = 0.0
    for (i in 0 until pts.size - 1) {
        val a = pts[i]
        val seg = pts[i + 1] - a
        val l = seg.len
        if (l > 0) {
            val t = (((q - a).x * seg.x + (q - a).y * seg.y) / (l * l)).coerceIn(0.0, 1.0)
            val foot = a + seg * t
            val d = (q - foot).len
            if (best == null || d < best.distance) {
                best = Proj(cross(seg, q - a) / l, d, start + t * l, vecBearing(seg))
            }
        }
        start += l
    }
    return requireNotNull(best) { "길 ${road.id}의 점이 모자라다" }
}

/** 길 선을 따라 처음부터 s(m) 간 점. 범위 밖이면 끝점이다 */
fun pointAlong(o: LatLon, road: Road, s: Double): LatLon {
    val pts = road.points.map { xy(o, it) }
    var left = s.coerceAtLeast(0.0)
    for (i in 0 until pts.size - 1) {
        val seg = pts[i + 1] - pts[i]
        if (left <= seg.len) return ll(o, pts[i] + seg * (if (seg.len == 0.0) 0.0 else left / seg.len))
        left -= seg.len
    }
    return road.points.last()
}

/** 길 선 중 [s0, s1] 구간의 점들(그림용) */
fun slice(o: LatLon, road: Road, s0: Double, s1: Double): List<LatLon> {
    val pts = road.points.map { xy(o, it) }
    val out = mutableListOf(pointAlong(o, road, s0))
    var start = 0.0
    for (i in 1 until pts.size) {
        start += (pts[i] - pts[i - 1]).len
        if (start > s0 && start < s1) out += road.points[i]
    }
    out += pointAlong(o, road, s1)
    return out
}

fun roadLength(o: LatLon, road: Road): Double =
    road.points.map { xy(o, it) }.zipWithNext { a, b -> (b - a).len }.sum()

/* ---------- 픽업 상태(data-model §7). 전이는 Pick.step 하나에 있다 ---------- */

enum class Status {
    LINK, SPOTS, NOSPOT, GOING, MET, CANCELED, STOPPED, EXPIRED;

    val wire get() = name.lowercase()
    val ended get() = this == MET || this == CANCELED || this == STOPPED || this == EXPIRED

    companion object {
        fun of(s: String) = entries.firstOrNull { it.wire == s }
    }
}

/** 고른 지점. pos는 랜드마크 길가 점, heading은 들어오는 방향, offset은 길가 쪽(오른쪽 −) */
data class Spot(
    val landmark: Landmark, val heading: Double, val waypoint: LatLon, val offset: Double,
    val walkM: Double, val walkSec: Double, val carSec: Double, val meetSec: Double, val crossing: Int,
    val reason: String, val road: List<LatLon>,
    val uturnSec: Double? = null, val passing: Landmark? = null, val standLine: String = "${landmark.name} 앞 인도에 서 계세요",
    val plans: List<Plan?> = emptyList(), // 3차 함께 태우기: members와 같은 순서, 빠진 사람은 null
) {
    val pos get() = landmark.pos
    val line get() = "${landmark.name} 앞"
}

data class Offer(val leg: TransitLeg, val gainMin: Int, val spot: Spot, val arriveSec: Double) // 4차: arriveSec = 탈 역까지 걷기 + 기다림 + 탐 + 내려서 걷기

/** 한 사람이 한 지점까지 가는 방법(3차 data-model §2). leg가 null이면 걷기 */
data class Plan(val leg: TransitLeg?, val walkM: Double, val arriveSec: Double, val crossing: Int, val standLine: String)

data class Fix(val pos: LatLon, val at: Long)

/** 5차 차례의 한 지점(data-model §1). who = members 번호. Area.kt의 Stop(정류장)과 다른 이름이다 */
data class ChainStop(val spot: Spot, val who: List<Int>)

/** legs[0] = 차 → 첫 지점, legs[k] = 앞 지점 → k번째 지점(들르는 60초 포함). destSec = 마지막 지점 → 목적지(없으면 null) */
data class Chain(val stops: List<ChainStop>, val legs: List<Double>, val destSec: Double?)

class Passenger {
    var consent: Boolean? = null
    var pos: LatLon? = null
    var ready = false
    var ack = false // "지금 나가요"
}

/** 함께 타는 사람 한 명(3차 data-model §1). 한 명 픽업은 이 사람 하나다. 값의 뜻은 1·2차 Pickup 그대로다 */
class Member(val person: Person, val id: String, val key: String) {
    val passenger = Passenger()
    var offer: Offer? = null // 한 명 픽업의 대중교통 안(1차)
    var transitAccepted = false // 한 명: 안을 받아들임, 함께: 안내에 "알겠어요"
    var transitDeclined = false // 4차: 한 명 픽업에서 안을 거절("그냥 걸어갈게요")
    var walkSec: Double? = null
    var called = false
    var readySpoken = false
    var lateSpoken = false
    var notifiedAt: Long? = null // "지금 나오세요"를 보낸 때
    var notifiedPos: LatLon? = null // 그때 태울 사람 위치
    var outNow = false
    var wrongSide = false
    // 3차
    var callAt: Long? = null // 전화(또는 대체 알림)를 정한 때
    var phoned: Boolean? = null // 서비스가 한 일: true 전화, false 폰으로 알림
    var aboard = false // 페이지 "탔어요"(함께 태우기)
    var out = false // 빠짐(FR-059)
    var stop = -1 // 5차: 차례에서 이 사람 지점 번호(spots와 chain.stops의 같은 번호)
}

class Pickup(val members: List<Member>, now: Long) {
    constructor(id: String, key: String, person: Person, now: Long) : this(listOf(Member(person, id, key)), now)

    val group get() = members.size > 1
    val lead get() = members[0]
    val active get() = members.filter { !it.out }
    // 한 명 픽업 코드·화면이 쓰던 이름(첫 사람)
    val id get() = lead.id
    val key get() = lead.key
    val person get() = lead.person
    val passenger get() = lead.passenger

    var status = Status.LINK
    var expireAt = now + Rules.WAIT_MS
    val track = mutableListOf<Fix>()
    var park: Boolean? = null // 모르면 null이고, P가 아닌 것으로 본다(원칙 I)
    var spots: List<Spot> = emptyList()
    var chosen = -1
    var etaSec: Double? = null
    var heading: Double? = null
    var passedWaypoint = false
    var spoke150 = false
    var spokenWhileDriving = 0
    var crossOver = false
    var lastPushAt = 0L
    var emptySeatsAtStart: Set<String> = emptySet()
    val openDoors = mutableSetOf<String>()
    var doorCycledNearSpot = false
    val takenSeats = mutableSetOf<String>() // emptySeatsAtStart 중 going·nospot에서 앉은 자리
    var reached = false // 지점 50m 안에 들어온 적이 있다. 그 뒤로는 만료를 미루지 않는다(FR-027)
    var signalRight = false
    var arriveAsked = false // 도착 확인은 한 픽업에 한 번
    var arriveCard = false
    var arrived = false
    var nextDest: String? = null // 경유지 방식일 때 원래 목적지
    var lostDest: String? = null // 경유지를 넣을 수 없어 대신 바꾼 원래 목적지
    // 3차
    var lastCallAt: Long? = null // 함께: 전화 간격(FR-058)
    var transitSpoken = false // 함께: 대중교통 한 줄(FR-057)
    var confirmedAt: Long? = null // 확정 시각(만남 요약)
    var startM: Double? = null // 확정 때 남은 거리(막대)
    var metAt: Long? = null
    var nearbySec: Double? = null // 고를 때 1순위 기준으로 센 근처 어림(비교 줄이 있을 때만). 1순위가 아닌 곳을 확정하면 지운다
    var lastLeft: Member? = null // 함께 태우기를 끝낸 마지막 빠짐(끝 화면 이름)
    // 5차
    var chain: Chain? = null // 고를 때 만든 차례 계획. 확정 뒤에는 빈 지점을 지울 때만 바뀐다
    var chained = false // 차례로 확정함
    var destPos: LatLon? = null // 내비 원래 목적지(research R44)

    val spot get() = spots.getOrNull(chosen)

    /** 가장 최근에 전화(또는 대체 알림)한 사람. 통화 중 표시는 이 사람에게만 붙는다(data-model §7) */
    val lastCaller get() = members.filter { it.callAt != null }.maxByOrNull { it.callAt!! }
}

/** "민지님, 준호님"(3차 data-model §4). 한 명이면 1·2차 문장과 같다 */
fun names(list: List<Member>) = list.joinToString(", ") { "${it.person.name}님" }

/**
 * 그 사람과 통화 중인가: 앱이 그 사람에게 전화했고(phoned), 기기에 통화가 있고, 그 통화가 건 뒤 30초 안에 시작됐다.
 * 그래야 나중에 걸려 온 다른 전화를 그 사람과의 통화로 보지 않는다(FR-066, 코드 리뷰 반영)
 */
fun inCallWith(m: Member, inCall: Boolean?, callSince: Long?): Boolean {
    val at = m.callAt ?: return false
    return m.phoned == true && inCall == true && callSince != null && callSince - at in 0..Rules.OUR_CALL_MS
}

/** 가는 중 연락 줄 상태(3차 data-model §7) */
enum class ContactKind { OUT, ABOARD, CALLING, READY, ACK, MOVING, CALLED, PHONE_NOTIFIED, NOTIFIED, WAIT }

/** sec: CALLING은 통화 경과, CALLED는 건 뒤 경과, NOTIFIED는 전화까지 남은 초(0 아래면 0) */
data class Contact(val kind: ContactKind, val sec: Int?)

/** 6차 주행 중 한 줄 카드(specs/006-car-screen contracts §1). minutes는 가는 중에만 */
data class Glance(val minutes: Int?, val line: String)

/** 한 줄 카드의 연락 상태 글: 정차 화면 연락 줄과 같은 말이되 초가 흐르는 숫자는 뺀다(research R3) */
fun shortStatus(k: ContactKind) = when (k) {
    ContactKind.OUT -> "함께 안 타요"
    ContactKind.ABOARD -> "탔어요"
    ContactKind.CALLING -> "통화 중"
    ContactKind.READY -> "나와 있어요"
    ContactKind.ACK -> "지금 나간대요"
    ContactKind.MOVING -> "나오고 있어요"
    ContactKind.CALLED -> "전화를 걸었어요"
    ContactKind.PHONE_NOTIFIED -> "폰으로 알렸어요"
    ContactKind.NOTIFIED -> "나오라고 알렸어요"
    ContactKind.WAIT -> "가까워지면 알려요"
}

/** 주행 중 한 줄: 위에서부터 처음 맞는 줄. 다음 사람은 차례면 지금 지점 사람, 아니면 안 탄 첫 사람 */
fun glance(p: Pickup?, creating: Boolean, contactOf: (Member) -> Contact): Glance = when (p?.status) {
    null -> Glance(null, if (creating) "링크를 만들고 있어요" else "운전 중이에요 · 픽업은 차를 세우고 시작해요")
    Status.MET -> Glance(null, "만났어요")
    Status.CANCELED -> Glance(null, "픽업을 취소했어요")
    Status.STOPPED -> Glance(null, "${(p.lastLeft ?: p.lead).person.name}님이 위치 공유를 껐어요")
    Status.EXPIRED -> Glance(null, "시간이 지나 위치 공유를 끝냈어요")
    Status.LINK -> Glance(null, if (p.group) "함께 데리러 가요 · 위치를 기다려요" else linkLine(p))
    Status.SPOTS -> Glance(null, if (p.spots.isEmpty()) "차례로 태우기 · 차를 세우면 골라요" else "만날 곳 ${p.spots.size}곳 · 차를 세우면 골라요")
    Status.NOSPOT -> Glance(null, if (p.group) "함께 만날 곳이 없어요" else "가까운 곳에 같은 편 자리가 없어요")
    Status.GOING -> {
        val waiting = p.active.filter { !it.aboard }
        val next = (if (p.chained) waiting.firstOrNull { it.stop == p.chosen } else null) ?: waiting.firstOrNull()
        if (next == null) Glance(null, "모두 탔어요")
        else Glance(p.etaSec?.let { minutes(it) }, "${next.person.name}님 · ${shortStatus(contactOf(next).kind)}") // 남은 시간을 모르면 분을 숨긴다
    }
}

/** 위에서부터 처음 맞는 상태. inCall이 null이면 통화 상태를 볼 수 없다. latest: m이 가장 최근에 전화한 사람 */
fun contact(m: Member, inCall: Boolean?, callSince: Long?, latest: Boolean, now: Long): Contact {
    fun since(t: Long?) = ((now - (t ?: now)) / 1000).toInt()
    return when {
        m.out -> Contact(ContactKind.OUT, null)
        m.aboard -> Contact(ContactKind.ABOARD, null)
        latest && inCallWith(m, inCall, callSince) -> Contact(ContactKind.CALLING, since(callSince))
        // 문턱에서 위치로 나와 있다고 봤으면 called만 켜지고 알림은 없다
        m.passenger.ready || (m.called && m.notifiedAt == null && m.phoned == null) -> Contact(ContactKind.READY, null)
        m.passenger.ack -> Contact(ContactKind.ACK, null)
        m.notifiedAt != null && m.called && m.phoned == null -> Contact(ContactKind.MOVING, null) // 알림 뒤 20m 넘게 움직여 전화하지 않음
        m.phoned == true -> Contact(ContactKind.CALLED, since(m.callAt))
        m.phoned == false -> Contact(ContactKind.PHONE_NOTIFIED, null)
        m.notifiedAt != null -> Contact(ContactKind.NOTIFIED, maxOf(0L, (Rules.CALL_WAIT_MS - (now - m.notifiedAt!!)) / 1000).toInt())
        else -> Contact(ContactKind.WAIT, null)
    }
}

/** 분 표시는 올림(data-model §8) */
fun minutes(sec: Double) = ceil(sec / 60.0).toInt().coerceAtLeast(0)

/** "○○을/를"(2차 data-model §3): 한글은 받침, 숫자는 읽는 소리(영·일·삼·육·칠·팔이 "을"), 그 밖은 "를" */
fun toObject(word: String): String = when (val c = word.lastOrNull()) {
    null -> "를"
    in '가'..'힣' -> if ((c - '가') % 28 == 0) "를" else "을"
    in "013678" -> "을"
    else -> "를"
}

/** "○○로/○○으로": 마지막 글자에 받침이 없거나 받침이 ㄹ이면 "로"(contracts/ui.md §2) */
fun toParticle(word: String): String {
    val c = word.lastOrNull()
    if (c == null || c !in '가'..'힣') return "로"
    val jong = (c - '가') % 28
    return if (jong == 0 || jong == 8) "로" else "으로"
}

sealed interface Event {
    data class CarPos(val pos: LatLon) : Event
    data class Gear(val park: Boolean) : Event
    data class Door(val area: String, val open: Boolean) : Event
    data class Seat(val area: String, val occupied: Boolean) : Event
    data class Remote(
        val status: String, val consent: Boolean?, val pos: LatLon?,
        val ready: Boolean, val late: Boolean, val boarded: Boolean, val transit: String?, val ack: Boolean = false,
        val who: Int = 0, // 3차: 함께 태우기의 사람 순서
    ) : Event
    data class Gone(val who: Int = 0) : Event
    data class Confirm(val index: Int) : Event
    data object Cancel : Event
    data object Tick : Event
    data class TurnSignal(val right: Boolean) : Event
    data class Arrive(val send: Boolean) : Event
    data class Route(val next: String?, val lost: String?) : Event
    data object ConfirmChain : Event // 5차: "차례로 태울게요"
    data class Dest(val pos: LatLon?) : Event // 5차: 내비 원래 목적지 좌표(앱이 넣은 지점이면 null)
}

sealed interface Action {
    data class Speak(val text: String) : Action
    data class CallOrNotify(val name: String, val phone: String, val speak: Boolean, val who: Int = 0) : Action
    data class EndMember(val index: Int) : Action // 3차: 그 사람 기록만 canceled로 끝낸다
    data class SetNavi(val spot: Spot) : Action
    data object ClearNavi : Action
    data object Push : Action
    data object End : Action
}

object Pick {
    private const val PICK_MPS = Rules.PICK_KMH / 3.6
    private const val MIN_MPS = Rules.MIN_KMH / 3.6
    private const val ROAD_MPS = Rules.ROAD_KMH / 3.6

    /* ---------- 지점 고르기(data-model §4) ---------- */

    fun choose(area: Area, car: LatLon, passenger: LatLon, careful: Boolean): List<Spot> {
        val o = area.center
        val limit = if (careful) Rules.WALK_M_CAREFUL else Rules.WALK_M
        return area.landmarks.filter { it.spot }.mapNotNull { l ->
            val walkM = dist(o, passenger, l.pos) * Rules.DETOUR
            if (walkM > limit) return@mapNotNull null
            val bare = side(area, car, l) ?: return@mapNotNull null
            val (walkSec, crossing) = walk(area, passenger, bare)
            bare.copy(walkM = walkM, walkSec = walkSec, meetSec = max(bare.carSec, walkSec), crossing = crossing,
                reason = "차가 오는 쪽 · " + (if (crossing == 0) "길 안 건너도 됨" else "횡단보도 한 번") + " · 세워도 되는 곳",
                standLine = standLine(area, bare, passenger))
        }.sortedWith(compareBy({ ceil(it.meetSec / 60.0) }, { it.walkM }, { it.crossing })).take(3)
    }

    /**
     * 사람과 무관한 같은 편 후보(3차 data-model §2-1): 애매한 길가, 일방통행 반대편, 정차 금지, 유턴이면 null.
     * 아니면 들어오는 방향·진입 경유지·차 시간·그림용 길·유턴 시간·기준 랜드마크를 채운 지점이다(사람 값은 비어 있다)
     */
    fun side(area: Area, car: LatLon, l: Landmark): Spot? {
        val o = area.center
        val road = area.road(l.roadId)
        val pr = project(o, road, l.pos)
        if (abs(pr.offset) < Rules.AMBIGUOUS_M) return null // 어느 길가인지 애매하다
        val forward = pr.offset < 0 // 오른쪽 길가 = 길 선 방향으로 들어온다
        if (!forward && road.oneway) return null
        if (area.noStops.any { dist(o, l.pos, it.pos) < it.kind.meters }) return null
        val h = if (forward) pr.dirDeg else (pr.dirDeg + 180.0) % 360.0
        // ponytail: 직선 방위 근사. 길이 복잡하면 틀린다. 2차에 경로 API(research R10)
        if (dist(o, car, l.pos) >= Rules.UTURN_SKIP_M && angleDiff(h, bearing(o, car, l.pos)) > Rules.UTURN_DEG) return null
        val total = roadLength(o, road)
        val waypoint = pointAlong(o, road, (if (forward) pr.along - Rules.WAYPOINT_M else pr.along + Rules.WAYPOINT_M).coerceIn(0.0, total))
        val carSec = (dist(o, car, waypoint) * Rules.DETOUR + Rules.WAYPOINT_M) / PICK_MPS
        val drawn = slice(o, road, (pr.along - Rules.DRAW_M).coerceAtLeast(0.0), (pr.along + Rules.DRAW_M).coerceAtMost(total))
        // 2차: 유턴 시간은 지점에서 가장 가까운 교차로 모퉁이까지 왕복(research R17)
        // ponytail: 직선 근사. 진행 방향 앞쪽 교차로만 고르면 더 맞다
        val corner = area.noStops.filter { it.kind == NoStopKind.CORNER }.minOfOrNull { dist(o, l.pos, it.pos) }
        val uturn = corner?.takeIf { it <= Rules.UTURN_NEAR_M }?.let { 2 * it * Rules.DETOUR / PICK_MPS + Rules.UTURN_WAIT_SEC }
        return Spot(l, h, waypoint, pr.offset, 0.0, 0.0, carSec, 0.0, 0, "", drawn, uturnSec = uturn, passing = passing(area, road, pr))
    }

    /** 서 있을 곳 문장: pos가 기준 랜드마크보다 차가 오는 쪽이면 "○○을 지나 △△ 앞 인도에 서 계세요"(2차) */
    private fun standLine(area: Area, s: Spot, pos: LatLon): String {
        val b = s.passing?.takeIf { before(area.center, area.road(s.landmark.roadId), s.offset < 0, pos, it.pos) }
        return (if (b != null) "${b.name}${toObject(b.name)} 지나 " else "") + "${s.landmark.name} 앞 인도에 서 계세요"
    }

    /* ---------- 함께 고르기(3차 data-model §2·§3) ---------- */

    /** 한 사람이 지점까지 가는 방법: 걷기 한도 안이면 걷기, 아니면 두 정류장이 다 한도 안인 가장 빠른 구간, 둘 다 아니면 null */
    fun plan(area: Area, s: Spot, pos: LatLon, careful: Boolean): Plan? {
        val o = area.center
        val limit = if (careful) Rules.WALK_M_CAREFUL else Rules.WALK_M
        val walkM = dist(o, pos, s.pos) * Rules.DETOUR
        if (walkM <= limit) return walkPlan(area, s, pos)
        return area.transit.mapNotNull { t ->
            val d1 = dist(o, pos, t.board.pos) * Rules.DETOUR
            val d2 = dist(o, t.alight.pos, s.pos) * Rules.DETOUR
            if (d1 > limit || d2 > limit) return@mapNotNull null
            val (sec, c) = walk(area, t.alight.pos, s) // 건너기와 서 있을 곳은 내린 정류장 기준
            Plan(t, d1 + d2, d1 / Rules.WALK_M_PER_MIN * 60.0 + t.waitSec + t.rideSec + sec, c, standLine(area, s, t.alight.pos))
        }.minByOrNull { it.arriveSec }
    }

    /** 걸어서 가는 방법(한도를 보지 않는다). 5차 차례는 늘 자기 1순위까지 걷는다 */
    private fun walkPlan(area: Area, s: Spot, pos: LatLon) =
        walk(area, pos, s).let { (sec, c) -> Plan(null, dist(area.center, pos, s.pos) * Rules.DETOUR, sec, c, standLine(area, s, pos)) }

    /**
     * 모두 갈 수 있는 같은 편 지점(FR-052). people[i]가 null이면 빠진 사람, Boolean은 가까이만.
     * 순위: 만나는 분(대중교통 탈 사람이 있으면 5분 더함) → 탈 사람 수 → 걷는 거리 합 → 건너기 수
     */
    fun chooseGroup(area: Area, car: LatLon, people: List<Pair<LatLon, Boolean>?>): List<Spot> =
        area.landmarks.filter { it.spot }.mapNotNull { l ->
            val bare = side(area, car, l) ?: return@mapNotNull null
            val plans = people.map { q -> q?.let { (pos, careful) -> plan(area, bare, pos, careful) ?: return@mapNotNull null } }
            val live = plans.filterNotNull().ifEmpty { return@mapNotNull null }
            val arrive = live.maxOf { it.arriveSec }
            val lines = live.mapNotNull { it.leg?.line }.distinct()
            val how = when (lines.size) {
                0 -> "모두 걸어서"
                1 -> "${lines[0]}${toParticle(lines[0])} 모여서"
                else -> "대중교통으로 모여서"
            }
            bare.copy(walkM = live.sumOf { it.walkM }, walkSec = arrive, meetSec = max(bare.carSec, arrive), crossing = live.sumOf { it.crossing },
                reason = "차가 오는 쪽 · $how · 세워도 되는 곳", plans = plans)
        }.sortedWith(compareBy({ ceil((it.meetSec + if (riders(it) > 0) Rules.GAIN_SEC else 0.0) / 60.0) }, { riders(it) },
            { it.walkM }, { it.crossing })).take(3)

    private fun riders(s: Spot) = s.plans.count { it?.leg != null }

    /** 근처로 데리러 가기 어림(초): 사람마다 한 명 픽업 1순위를 차에서 가까운 차례로 들른다. 같은 곳은 한 번. 1순위가 없는 사람이 있으면 null */
    fun nearby(area: Area, car: LatLon, people: List<Pair<LatLon, Boolean>>): Double? {
        val o = area.center
        val stops = people.map { (pos, careful) -> choose(area, car, pos, careful).firstOrNull()?.landmark ?: return null }
            .distinctBy { it.id }.sortedBy { dist(o, car, it.pos) }
        var at = car
        var sec = 0.0
        for (l in stops) {
            sec += dist(o, at, l.pos) * Rules.DETOUR / PICK_MPS + Rules.STOP_SEC
            at = l.pos
        }
        return sec
    }

    /**
     * 차례 계획(5차 data-model §1): 사람마다 한 명 픽업 1순위, 같은 곳은 한 지점, 차에서 가까운 곳부터 앞 지점에서 가까운 곳 차례.
     * 1순위가 없는 사람이 있거나 지점이 2곳 미만이면 null
     */
    // ponytail: 욕심 방식. 3명까지라 모든 순서(6가지)도 볼 수 있지만 시연 지역에서는 같다(research R42)
    fun chain(area: Area, car: LatLon, people: List<Pair<LatLon, Boolean>?>, dest: LatLon?): Chain? {
        val o = area.center
        val firsts = people.mapIndexedNotNull { i, q -> q?.let { (pos, careful) -> i to (choose(area, car, pos, careful).firstOrNull() ?: return null) } }
        val left = firsts.groupBy { it.second.landmark.id }.values.map { g -> ChainStop(g[0].second, g.map { it.first }) }.toMutableList()
        if (left.size < 2) return null
        val stops = mutableListOf<ChainStop>()
        var at = car
        while (left.isNotEmpty()) {
            val next = left.minBy { dist(o, at, it.spot.pos) }
            left -= next; stops += next; at = next.spot.pos
        }
        val legs = listOf(stops[0].spot.carSec) + stops.zipWithNext { a, b -> dist(o, a.spot.pos, b.spot.pos) * Rules.DETOUR / PICK_MPS + Rules.STOP_SEC }
        return Chain(stops, legs, toDest(o, stops.last().spot.pos, dest))
    }

    private fun toDest(o: LatLon, last: LatLon, dest: LatLon?) = dest?.let { dist(o, last, it) * Rules.DETOUR / ROAD_MPS }

    /** 사람별 남은 시간(5차 data-model §4). 차례가 아니면 p.etaSec, 탔거나 지난 지점이면 null */
    fun memberEta(p: Pickup, i: Int): Double? {
        val m = p.members[i]
        val c = p.chain
        if (!p.chained || c == null) return p.etaSec
        val eta = p.etaSec ?: return null
        if (m.aboard || m.stop < p.chosen) return null
        return eta + (p.chosen + 1..m.stop).sumOf { c.legs[it] }
    }

    /** 남은 거리 막대(FR-069): 1 − 지금 남은 거리 / 확정 때 남은 거리. 확정 때 차 위치가 없었으면 null */
    fun progress(o: LatLon, p: Pickup): Double? {
        val start = p.startM ?: return null
        val s = p.spot ?: return null
        val car = p.track.lastOrNull()?.pos ?: return null
        return (1 - left(o, car, s, p.passedWaypoint) / start).coerceIn(0.0, 1.0)
    }

    /** 지점까지 남은 거리(m). 경유지를 지나기 전에는 경유지를 거쳐 간다 */
    private fun left(o: LatLon, car: LatLon, s: Spot, passedWaypoint: Boolean) =
        if (passedWaypoint) dist(o, car, s.pos) * Rules.DETOUR else dist(o, car, s.waypoint) * Rules.DETOUR + Rules.WAYPOINT_M

    /** 지점 길에서 같은 길가, 차가 오는 쪽으로 20~150m 안의 가장 가까운 랜드마크(2차 data-model §3). spot과 상관없다 */
    private fun passing(area: Area, road: Road, prS: Proj): Landmark? {
        val forward = prS.offset < 0
        return area.landmarks.filter { it.roadId == road.id }.mapNotNull { l ->
            val pr = project(area.center, road, l.pos)
            val u = if (forward) prS.along - pr.along else pr.along - prS.along
            if (sign(pr.offset) == sign(prS.offset) && u in Rules.PASS_MIN_M..Rules.PASS_MAX_M) l to u else null
        }.minByOrNull { it.second }?.first
    }

    /** 길을 건너야 하고 유턴이 건너기보다 오래 걸릴 때만 (건너기 분, 유턴 분)(2차 data-model §2) */
    private fun compare(s: Spot): Pair<Int, Int>? {
        val cross = minutes(Rules.CROSS_SEC)
        val turn = s.uturnSec?.let(::minutes) ?: return null
        return if (s.crossing == 1 && turn > cross) cross to turn else null
    }

    /** 차 카드 비교 줄 */
    fun compareLine(s: Spot) = compare(s)?.let { (c, t) -> "건너기 ${c}분 · 유턴 ${t}분" }

    /** 대중교통 강조 카드(4차 FR-077·078): (굵은 줄, 작은 줄). 한 명 픽업에서 안이 있고 거절하지 않았을 때만 */
    fun transitNote(p: Pickup): Pair<String, String>? {
        val m = p.lead
        val o = m.offer ?: return null
        if (p.group || m.transitDeclined) return null
        val l = o.leg
        val stops = "${l.board.name} → ${l.alight.name} ${l.stops}정거장"
        return if (m.transitAccepted) "${p.person.name}님이 ${l.line} 타고 ${l.alight.name}${toParticle(l.alight.name)} 와요" to stops
        else "${p.person.name}님께 ${l.line} 안을 보냈어요" to "$stops · ${o.spot.line}에서 만나면 ${o.gainMin}분 빨라요"
    }

    /** 카드 사람 줄(4차 FR-076): "민지 걸어서 3분" / "준호 1호선 1정거장(성균관대역 → 화서역) · 12분" */
    fun howLine(name: String, leg: TransitLeg?, sec: Double) =
        if (leg == null) "$name 걸어서 ${minutes(sec)}분"
        else "$name ${leg.line} ${leg.stops}정거장(${leg.board.name} → ${leg.alight.name}) · ${minutes(sec)}분"

    /** 페이지 "k분 빨라요"의 k. 차가 세어 보낸다 */
    fun fasterMin(s: Spot) = compare(s)?.let { (c, t) -> t - c }

    /** 대중교통 안(data-model §5): 5분 넘게 빨리 만나는 안 중 가장 빠른 것. 걷는 지점이 없으면 내지 않는다 */
    fun transit(area: Area, car: LatLon, passenger: LatLon, careful: Boolean, best: Spot?): Offer? {
        if (best == null) return null
        val o = area.center
        val limit = if (careful) Rules.WALK_M_CAREFUL else Rules.WALK_M
        return area.transit.mapNotNull { t ->
            val d1 = dist(o, passenger, t.board.pos) * Rules.DETOUR
            if (d1 > limit) return@mapNotNull null
            val s = choose(area, car, t.alight.pos, careful).firstOrNull() ?: return@mapNotNull null
            val arrive = d1 / Rules.WALK_M_PER_MIN * 60.0 + t.waitSec + t.rideSec + s.walkSec
            val gain = best.meetSec - max(s.carSec, arrive)
            if (gain > Rules.GAIN_SEC) gain to Offer(t, floor(gain / 60.0).toInt(), s, arrive) else null
        }.maxByOrNull { it.first }?.second
    }

    /** 걷는 시간(초)과 길 건너기(0·1). 지점을 정한 뒤에도 태울 사람 위치가 오면 다시 부른다 */
    fun walk(area: Area, passenger: LatLon, s: Spot): Pair<Double, Int> {
        val o = area.center
        val pr = project(o, area.road(s.landmark.roadId), passenger)
        val crossing = if (pr.distance <= Rules.CROSS_NEAR_M && sign(pr.offset) != sign(s.offset)) 1 else 0
        return dist(o, passenger, s.pos) * Rules.DETOUR / Rules.WALK_M_PER_MIN * 60.0 + crossing * Rules.CROSS_SEC to crossing
    }

    /* ---------- 진행 방향, 속도, 남은 시간(data-model §6) ---------- */

    /** 최근 위치와, 거기서 8m 넘게 떨어진 가장 최근 위치의 방위. 그런 위치가 없으면 null */
    fun heading(o: LatLon, track: List<Fix>): Double? {
        val last = track.lastOrNull() ?: return null
        val from = track.lastOrNull { dist(o, it.pos, last.pos) > Rules.HEADING_M } ?: return null
        return bearing(o, from.pos, last.pos)
    }

    private fun window(track: List<Fix>, now: Long, ms: Long) = track.filter { now - it.at <= ms }

    private fun meters(o: LatLon, fixes: List<Fix>) = fixes.zipWithNext { a, b -> dist(o, a.pos, b.pos) }.sum()

    /** 최근 30초 이동 거리 합 ÷ 경과 시간(m/s) */
    fun speed(o: LatLon, track: List<Fix>, now: Long): Double {
        val w = window(track, now, Rules.SPEED_WINDOW_MS)
        val sec = if (w.size < 2) 0.0 else (w.last().at - w.first().at) / 1000.0
        return if (sec <= 0) 0.0 else meters(o, w) / sec
    }

    /** 최근 5초(도착 확인은 3초) 동안 5m 미만으로 움직였다 */
    fun stopped(o: LatLon, track: List<Fix>, now: Long, ms: Long = Rules.STOP_WINDOW_MS) = meters(o, window(track, now, ms)) < Rules.STOP_M

    /** 지점까지 남은 시간(초). 경유지를 지나기 전에는 경유지를 거쳐 간다. 신호 대기처럼 잠깐 서도 시속 20km로 본다 */
    // ponytail: 직선 근사. 2차에 내비 DrivingInfo.duration
    fun remaining(o: LatLon, track: List<Fix>, spot: Spot, passedWaypoint: Boolean, now: Long): Double? {
        val car = track.lastOrNull()?.pos ?: return null
        return left(o, car, spot, passedWaypoint) / max(speed(o, track, now), MIN_MPS)
    }

    /* ---------- 상태 전이(data-model §7) ---------- */

    fun step(area: Area, p: Pickup, e: Event, now: Long): List<Action> {
        if (p.status.ended) return emptyList()
        val out = mutableListOf<Action>()
        if (p.group && e is Event.Remote) return out.also { remoteGroup(area, p, e, now, it) }.distinct()
        if (p.group && e is Event.Gone) return out.also { leave(area, p, e.who, Status.EXPIRED, now, it) }.distinct()
        val ended = (e as? Event.Remote)?.let { Status.of(it.status) }?.takeIf { it.ended }
        when {
            ended != null -> { end(p, ended, out); return out + Action.End } // 서버가 이미 끝냈다(공유 끄기 등)
            e is Event.Remote && e.boarded -> { met(p, now, out); return out }
        }
        when (e) {
            is Event.Gear -> p.park = e.park
            is Event.CarPos -> {
                val o = area.center
                p.track += Fix(e.pos, now)
                p.track.removeAll { now - it.at > Rules.TRACK_MS }
                p.heading = heading(o, p.track)
                when (p.status) {
                    Status.LINK -> if (p.group) chooseGroup(area, p, now, out) else choose(area, p, now, out)
                    Status.GOING -> going(area, p, now, out)
                    Status.NOSPOT -> if (!p.group) nospotEta(o, p, now) // 함께 태우기는 nospot에 데리러 가지 않는다
                    else -> Unit
                }
                if (p.status != Status.LINK && now - p.lastPushAt > Rules.PUSH_GAP_MS) out += Action.Push
            }
            is Event.Remote -> {
                val who = p.person.name
                p.passenger.apply { consent = e.consent; pos = e.pos; ready = e.ready; ack = e.ack }
                val offer = p.lead.offer
                val open = (p.status == Status.SPOTS || p.status == Status.GOING) && !p.lead.transitAccepted && !p.lead.transitDeclined && offer != null
                if (open && e.transit == "accept") accept(p, offer!!, out)
                if (open && e.transit == "decline") p.lead.transitDeclined = true // 4차: 기록만(지점·음성·내비 그대로)
                when (p.status) {
                    Status.LINK -> choose(area, p, now, out)
                    Status.GOING -> {
                        val s = p.spot
                        // 버스 안의 위치로는 걷는 시간을 셀 수 없다
                        if (e.pos != null && s != null && !p.lead.transitAccepted) {
                            val (w, c) = walk(area, e.pos, s)
                            p.lead.walkSec = w
                            if (c != s.crossing) p.spots = p.spots.map { if (it === s) s.copy(crossing = c) else it } // 건넌 뒤에는 비교 줄을 내린다
                        }
                        if (e.ready && !p.lead.readySpoken) { p.lead.readySpoken = true; say(p, out, "${who}님이 나와 있어요") }
                        if (e.late && !p.lead.lateSpoken) { p.lead.lateSpoken = true; say(p, out, "${who}님이 조금 늦는대요") }
                    }
                    else -> Unit
                }
            }
            is Event.Confirm -> {
                val s = p.spots.getOrNull(e.index)
                if (p.status == Status.SPOTS && p.park == true && s != null) {
                    // 함께 태우기는 걷는 사람만 걷는 시간이 있다(대중교통 탈 사람은 2분 문턱)
                    if (p.group) p.members.forEachIndexed { i, m -> m.walkSec = s.plans.getOrNull(i)?.takeIf { it.leg == null }?.arriveSec }
                    else p.lead.walkSec = s.walkSec
                    if (e.index != 0 || p.chain != null) p.nearbySec = null // 근처 어림은 1순위와 견준 값이다. 5차: 계획이 있으면 카드에서 숨겼다
                    p.confirmedAt = now
                    go(area, p, e.index, now, out)
                }
            }
            Event.ConfirmChain -> {
                val c = p.chain
                if (p.status == Status.SPOTS && p.park == true && c != null) {
                    // 지점마다 그 지점 사람의 걷기 plan(3차)을 둔다. 합친 지점에서도 서 있을 곳·건너기가 사람마다다
                    p.spots = c.stops.map { st ->
                        st.spot.copy(plans = p.members.mapIndexed { i, m -> m.passenger.pos?.takeIf { i in st.who }?.let { walkPlan(area, st.spot, it) } })
                    }
                    c.stops.forEachIndexed { k, st -> st.who.forEach { p.members[it].stop = k } }
                    p.members.forEachIndexed { i, m -> m.walkSec = p.spots.getOrNull(m.stop)?.plans?.getOrNull(i)?.arriveSec }
                    p.chained = true
                    p.nearbySec = null // 만남 화면의 3차 비교 줄을 끈다
                    p.confirmedAt = now
                    go(area, p, 0, now, out)
                }
            }
            is Event.Dest -> if (!p.chained) { // 확정 뒤에는 바꾸지 않는다(목적지 방식이면 내비 목적지가 지점이 된다, research R44)
                p.destPos = e.pos
                // 만날 곳에서는 목적지 구간만 다시 센다(카드 순서·만료는 그대로). 한 명 픽업은 값만
                p.chain = p.chain?.let { it.copy(destSec = toDest(area.center, it.stops.last().spot.pos, e.pos)) }
            }
            is Event.Door -> when {
                e.area == Rules.DRIVER_DOOR -> Unit
                e.open -> p.openDoors += e.area
                p.openDoors.remove(e.area) -> {
                    // 거리는 문이 닫힐 때만 본다. 기준은 지점, 같은 편 자리가 없으면 태울 사람의 마지막 위치
                    val ref = if (p.status == Status.GOING) p.spot?.pos else if (p.status == Status.NOSPOT) p.passenger.pos else null
                    val car = p.track.lastOrNull()?.pos
                    if (ref != null && car != null && dist(area.center, car, ref) <= Rules.BOARD_M) p.doorCycledNearSpot = true
                    board(area, p, now, out)
                }
            }
            is Event.Seat -> if (e.area in p.emptySeatsAtStart && (p.status == Status.GOING || p.status == Status.NOSPOT)) {
                if (e.occupied) p.takenSeats += e.area else p.takenSeats -= e.area
                board(area, p, now, out)
            }
            Event.Cancel -> if (p.park == true) {
                end(p, Status.CANCELED, out)
                out += listOf(Action.Push, Action.End)
            }
            Event.Tick -> if (now > p.expireAt) {
                end(p, Status.EXPIRED, out)
                out += listOf(Action.Push, Action.End)
            } else {
                if (p.status == Status.GOING) { // 서 있어 위치가 안 와도 마감과 도착 확인은 본다
                    p.members.indices.forEach { callIfNoAnswer(area.center, p, it, now, out) }
                    askArrive(area, p, now, out)
                }
                board(area, p, now, out) // 막 선 직후에 문·좌석이 와서 "서 있음"이 아직 아니었으면 여기서 다시 본다
            }
            is Event.Gone -> {
                end(p, Status.EXPIRED, out)
                out += Action.End
            }
            is Event.TurnSignal -> {
                p.signalRight = e.right
                if (p.status == Status.GOING) askArrive(area, p, now, out) // 서 있다가 깜빡이를 켰다
            }
            // 잃은 원래 목적지는 그 픽업 동안 남긴다(대중교통으로 다시 물으면 목적지가 앱이 넣은 지점이다)
            is Event.Route -> { p.nextDest = e.next; p.lostDest = e.lost ?: p.lostDest }
            is Event.Arrive -> if (p.park == true && p.arriveCard) { // 카드 버튼은 P에서만 눌린다
                p.arriveCard = false
                if (e.send) { p.arrived = true; out += Action.Push }
            }
        }
        return out.distinct() // 한 이벤트에서 Push가 여러 번 나와도 한 번만 올린다
    }

    /** 확정·넘어가기 공통: 그 지점으로 가는 중이 된다(남은 시간·만료·막대 기준을 그 지점으로) */
    private fun go(area: Area, p: Pickup, index: Int, now: Long, out: MutableList<Action>) {
        val s = p.spots[index]
        p.chosen = index
        p.etaSec = remaining(area.center, p.track, s, false, now) // 서 있는 동안 위치가 안 와도 남은 시간을 보인다
        p.status = Status.GOING
        p.expireAt = now + (s.carSec * 1000).toLong() + Rules.GOING_EXTRA_MS
        p.startM = p.track.lastOrNull()?.let { left(area.center, it.pos, s, false) }
        out += Action.SetNavi(s)
        out += Action.Push
    }

    /** 5차: 다음 지점으로 넘어간다(research R45). 지점 값은 처음으로, 탄 자리 수는 그대로(운전 내내 누적), 음성 셈은 지점마다 */
    private fun advance(area: Area, p: Pickup, spoken: String, next: Int, now: Long, out: MutableList<Action>) {
        p.passedWaypoint = false; p.spoke150 = false; p.reached = false; p.doorCycledNearSpot = false
        p.arriveAsked = false; p.arriveCard = false; p.arrived = false; p.crossOver = false
        p.spokenWhileDriving = 0
        go(area, p, next, now, out)
        say(p, out, spoken + "다음은 ${names(p.active.filter { it.stop == next })}, ${p.spots[next].line}이에요")
    }

    /** 5차: 사람이 없는 지점 k를 차례에서 지우고 앞뒤 구간을 잇는다(들르는 1분은 한 번만, data-model §3) */
    private fun dropStop(p: Pickup, k: Int) {
        val c = p.chain ?: return
        val legs = c.legs.toMutableList()
        var dest = c.destSec
        if (k + 1 < legs.size) legs[k + 1] = legs[k] + legs[k + 1] - Rules.STOP_SEC else dest = dest?.let { legs[k] + it - Rules.STOP_SEC }
        legs.removeAt(k)
        p.chain = Chain(c.stops.filterIndexed { i, _ -> i != k }, legs, dest)
        p.spots = p.spots.filterIndexed { i, _ -> i != k }
        p.members.forEach { if (it.stop > k) it.stop-- }
    }

    /** going에서 끝나면 어떤 끝이든 앱이 내비에 넣은 것을 지운다(경유지가 원래 길이나 다음 픽업에 남지 않게) */
    private fun end(p: Pickup, s: Status, out: MutableList<Action>) {
        if (p.status == Status.GOING) out += Action.ClearNavi
        p.status = s
    }

    private fun met(p: Pickup, now: Long, out: MutableList<Action>) {
        // 경유지 방식이면 내비가 원래 목적지로 이어 간다(2차 FR-034). 함께 태우기는 남은 사람 이름을 묶는다(FR-060)
        val who = names(p.active)
        say(p, out, when {
            p.nextDest == null -> "${who}이 탔어요"
            p.group -> "모두 탑승 확인. 다음 목적지로 안내할게요"
            else -> "$who 탑승 확인. 다음 목적지로 안내할게요"
        })
        p.metAt = now
        end(p, Status.MET, out)
        out += listOf(Action.Push, Action.End)
    }

    /**
     * 탑승(FR-026, 3차 FR-060). 탄 사람 수 = 큰 쪽(같은 사람이 앉고 "탔어요"도 누르면 두 번 세지 않게)
     * - 서 있고 지점 가까이에서 문이 열렸다 닫혔을 때, 처음에 비어 있다가 앉은 좌석 수
     * - 페이지에서 "탔어요"를 누른 사람 수(함께 태우기)
     * 남은 사람 수에 닿으면 만남이다. 순서는 상관없다
     */
    private fun board(area: Area, p: Pickup, now: Long, out: MutableList<Action>) {
        if ((p.status != Status.GOING && p.status != Status.NOSPOT) || p.active.isEmpty()) return
        val seats = if (p.doorCycledNearSpot && stopped(area.center, p.track, now)) p.takenSeats.size else 0
        // 5차 차례: 지금 지점까지 태울 사람(research R46). 빈자리 수는 운전 내내 누적이라 앞 지점 사람과 함께 센다
        val due = if (p.chained) p.active.filter { it.stop <= p.chosen } else p.active
        if (max(seats, due.count { it.aboard }) < due.size) return
        val next = nextStop(p, p.chosen + 1)
        if (!p.chained || next == null) return met(p, now, out)
        val here = due.filter { it.stop == p.chosen }
        due.forEach { it.aboard = true } // 착좌로만 탄 사람도 탄 사람이다(줄 체크, 연락 줄, 페이지, 전화 거르기)
        advance(area, p, "${names(here)}이 탔어요. ", next, now, out)
    }

    /** 5차: from부터 아직 안 탄 사람이 있는 첫 지점(먼저 와서 탄 사람의 지점은 건너뛴다). 없으면 null */
    private fun nextStop(p: Pickup, from: Int) = (from until p.spots.size).firstOrNull { k -> p.active.any { it.stop == k && !it.aboard } }

    /** 대중교통을 받아들임: 정류장 근처 지점 하나만 남긴다. 가는 중이면 내비를 바꾼다 */
    private fun accept(p: Pickup, offer: Offer, out: MutableList<Action>) {
        p.lead.transitAccepted = true
        p.spots = listOf(offer.spot)
        if (p.status == Status.GOING) {
            p.chosen = 0
            p.passedWaypoint = false // 지점이 바뀌었다
            p.spoke150 = false
            p.reached = false
            out += Action.SetNavi(offer.spot)
        }
        val stop = offer.leg.alight.name
        say(p, out, "${p.person.name}님이 ${offer.leg.line} 타고 $stop${toParticle(stop)} 와요")
        out += Action.Push
    }

    /** going에서 차 위치가 올 때: 남은 시간, 자동 전화, 150m 한 줄. 사람 판단은 빠지지도 타지도 않은 사람마다(3차) */
    private fun going(area: Area, p: Pickup, now: Long, out: MutableList<Action>) {
        val o = area.center
        val s = p.spot ?: return
        val car = p.track.last().pos
        if (dist(o, car, s.waypoint) <= Rules.PASSED_WAYPOINT_M) p.passedWaypoint = true
        val eta = remaining(o, p.track, s, p.passedWaypoint, now) ?: return
        p.etaSec = eta
        if (!p.reached) { // 처음 도착한 때가 "마지막 도착 예정 시각"이다. 지점에서 기다리는 동안 만료가 밀리지 않는다
            p.expireAt = now + (eta * 1000).toLong() + Rules.GOING_EXTRA_MS
            p.reached = dist(o, car, s.pos) <= Rules.LAST_SPOT_M
        }
        val waiting = p.members.withIndex().filter { !it.value.out && !it.value.aboard }
        val here = if (p.chained) waiting.filter { it.value.stop == p.chosen } else waiting // 5차: 지금 지점 사람

        // 자동 전화(FR-016·017). 대중교통 탈 사람은 걷는 시간을 셀 수 없어 2분 기준이다.
        // 2차: 문턱에서는 페이지에 "지금 나오세요"만 보내고(운전자 음성 없음), 60초 안에 반응이 없을 때만 전화한다
        // 5차 차례: 사람마다 자기 지점과 자기 남은 시간(memberEta). 길을 건너오라는 말(crossOver)은 지금 지점 사람에게만이다
        for ((i, m) in waiting) {
            val ms = if (p.chained) p.spots[m.stop] else s
            val mine = memberEta(p, i) ?: continue
            val threshold = if (rider(p, i)) Rules.CALL_MIN_SEC
            else max((m.walkSec ?: ms.walkSec) + Rules.CALL_BUFFER_SEC, Rules.CALL_MIN_SEC)
            if (!m.called && m.notifiedAt == null && mine <= threshold) {
                val isOut = m.passenger.ready || m.passenger.pos?.let { dist(o, it, ms.pos) <= Rules.OUT_M && onCurb(area, ms, it, p.crossOver && ms === s) } == true
                if (isOut) {
                    m.called = true
                    if (!m.readySpoken) { m.readySpoken = true; say(p, out, "${m.person.name}님이 나와 있어요") }
                } else {
                    m.notifiedAt = now
                    m.notifiedPos = m.passenger.pos
                    m.outNow = true
                    out += Action.Push
                }
            }
            callIfNoAnswer(o, p, i, now, out)
        }

        // 150m 한 줄(FR-019·020). 지점 길에 들어선 뒤에만 방향을 판정한다
        if (!p.spoke150 && here.isNotEmpty()) {
            val d = dist(o, car, s.pos)
            val onRoad = project(o, area.road(s.landmark.roadId), car).distance <= Rules.ON_ROAD_M
            val diff = p.heading?.let { angleDiff(it, s.heading) }
            val cross = onRoad && diff != null && diff >= Rules.CROSS_DEG
            val line = when {
                d > Rules.NEAR_SPOT_M -> null
                cross -> "${names(here.map { it.value })}이 길을 건너 오른쪽으로 와요"
                onRoad && diff != null && diff <= Rules.RIGHT_DEG -> rightLine(area, s, car)
                (onRoad && diff == null) || d <= Rules.LAST_SPOT_M -> "곧 ${s.landmark.name} 앞이에요"
                else -> null // 꺾는 중이다. 방향이 정해질 때까지 기다린다
            }
            if (line != null) {
                p.spoke150 = true
                if (cross) { p.crossOver = true; out += Action.Push }
                say(p, out, line)
            }
        }

        // 건너편 경고(FR-023). 음성은 없고 페이지 띠만 바뀐다. 올린 값과 다를 때만 올린다
        for ((_, m) in here) {
            val pos = m.passenger.pos
            val wrong = eta <= Rules.WRONG_SIDE_SEC && pos != null &&
                project(o, area.road(s.landmark.roadId), pos).distance <= Rules.CROSS_NEAR_M && !onCurb(area, s, pos, p.crossOver)
            if (wrong != m.wrongSide) {
                m.wrongSide = wrong
                out += Action.Push
            }
        }
        askArrive(area, p, now, out)
    }

    /** 150m 오른쪽 문장. 기준 랜드마크가 있고 차가 아직 지나지 않았으면 "지나서"(2차 data-model §6) */
    private fun rightLine(area: Area, s: Spot, car: LatLon): String {
        val b = s.passing?.takeIf { before(area.center, area.road(s.landmark.roadId), s.offset < 0, car, it.pos) }
        return if (b != null) "오른쪽 ${b.name} 지나서 ${s.landmark.name} 앞에 세우세요" else "오른쪽 ${s.landmark.name} 앞이에요"
    }

    /** 길 위에서 a가 b보다 차가 오는 쪽인가. forward(오른쪽 길가)면 along이 작은 쪽이다 */
    private fun before(o: LatLon, road: Road, forward: Boolean, a: LatLon, b: LatLon): Boolean {
        val x = project(o, road, a).along
        val y = project(o, road, b).along
        return if (forward) x < y else x > y
    }

    /** 도착 확인(2차 FR-038): 오른쪽 깜빡이, 지점 50m 안, 3초 서 있음. 한 픽업에 한 번 묻는다 */
    private fun askArrive(area: Area, p: Pickup, now: Long, out: MutableList<Action>) {
        val s = p.spot ?: return
        val car = p.track.lastOrNull()?.pos ?: return
        if (p.arriveAsked || !p.signalRight || dist(area.center, car, s.pos) > Rules.BOARD_M ||
            !stopped(area.center, p.track, now, Rules.ARRIVE_STOP_MS)) return
        // 옆길에서 지점 길로 꺾으려고 선 것은 도착이 아니다. 반대 방향(건너편 도착, 위임 결정 D1)은 묻는다
        if (project(area.center, area.road(s.landmark.roadId), car).distance > Rules.ON_ROAD_M) return
        val diff = p.heading?.let { angleDiff(it, s.heading) }
        if (diff != null && diff > Rules.RIGHT_DEG && diff < Rules.CROSS_DEG) return
        p.arriveAsked = true
        p.arriveCard = true
        say(p, out, "${names(p.active.filter { !it.aboard && (!p.chained || it.stop == p.chosen) })}께 도착했다고 알릴까요?")
    }

    /**
     * 알림 60초 뒤 한 번: 반응(지금 나가요, 나와 있어요, 알림 때 자리에서 20m 넘게 움직임)이 없을 때만 전화한다.
     * 3차: 앞 전화 뒤 60초가 안 지났으면 미루고, 지나면 그때 반응을 다시 본다(FR-058)
     */
    private fun callIfNoAnswer(o: LatLon, p: Pickup, i: Int, now: Long, out: MutableList<Action>) {
        val m = p.members[i]
        val at = m.notifiedAt ?: return
        if (m.called || m.out || m.aboard || now - at < Rules.CALL_WAIT_MS) return
        if (p.lastCallAt?.let { now - it < Rules.CALL_GAP_MS } == true) return // 다음 Tick·위치에서 다시 본다
        m.called = true
        val from = m.notifiedPos
        val to = m.passenger.pos
        // 버스·지하철 안이면 늘 움직이므로 이동은 반응으로 보지 않는다
        val moved = !rider(p, i) && from != null && to != null && dist(o, from, to) > Rules.MOVED_M
        if (m.passenger.ack || m.passenger.ready || moved) return
        val speak = p.park == true || p.spokenWhileDriving < Rules.VOICE_CAP
        if (p.park != true && speak) p.spokenWhileDriving++
        p.lastCallAt = now
        m.callAt = now
        out += Action.CallOrNotify(m.person.name, m.person.phone, speak, i)
        out += Action.Push
    }

    /** 대중교통 탈 사람(FR-056): 한 명은 안을 받아들인 사람, 함께는 고른 지점까지 대중교통인 사람("알겠어요"와 무관) */
    private fun rider(p: Pickup, i: Int) = p.members[i].transitAccepted || p.spot?.plans?.getOrNull(i)?.leg != null

    /** 나와 있음·건너편 경고의 기준 길가: 지점 쪽. 길을 건너오라고 했으면(crossOver) 그 반대쪽 */
    private fun onCurb(area: Area, s: Spot, pos: LatLon, crossOver: Boolean): Boolean {
        val side = sign(project(area.center, area.road(s.landmark.roadId), pos).offset)
        return side == (if (crossOver) -sign(s.offset) else sign(s.offset))
    }

    /** 주행 중(P가 아님) 음성은 5줄까지다(FR-015) */
    private fun say(p: Pickup, out: MutableList<Action>, text: String) {
        if (p.park != true) {
            if (p.spokenWhileDriving >= Rules.VOICE_CAP) return
            p.spokenWhileDriving++
        }
        out += Action.Speak(text)
    }

    /** link에서 동의, 태울 사람 위치, 차 위치가 처음으로 모두 갖춰지면 고른다 */
    private fun choose(area: Area, p: Pickup, now: Long, out: MutableList<Action>) {
        val passenger = p.passenger.pos ?: return
        val car = p.track.lastOrNull()?.pos ?: return
        if (p.passenger.consent != true) return
        p.spots = choose(area, car, passenger, p.person.careful)
        p.lead.offer = transit(area, car, passenger, p.person.careful, p.spots.firstOrNull())
        p.status = if (p.spots.isEmpty()) Status.NOSPOT else Status.SPOTS
        if (p.status == Status.NOSPOT) nospotEta(area.center, p, now) // 서 있어 위치가 안 와도 페이지에 차 분이 나온다
        p.expireAt = now + Rules.WAIT_MS
        out += Action.Push
    }

    /** nospot: 지점이 없으니 태울 사람의 마지막 위치까지 */
    private fun nospotEta(o: LatLon, p: Pickup, now: Long) {
        val car = p.track.lastOrNull()?.pos ?: return
        p.passenger.pos?.let { p.etaSec = dist(o, car, it) * Rules.DETOUR / max(speed(o, p.track, now), MIN_MPS) }
    }

    /* ---------- 함께 태우기 전이(3차 data-model §4). 한 명 픽업은 위 1·2차 경로 그대로다 ---------- */

    /** 한 사람의 서버 상태. 끝 상태면 빠지고, boarded면 탄 것으로 센다 */
    private fun remoteGroup(area: Area, p: Pickup, e: Event.Remote, now: Long, out: MutableList<Action>) {
        val m = p.members.getOrNull(e.who)?.takeUnless { it.out } ?: return
        Status.of(e.status)?.takeIf { it.ended }?.let { return leave(area, p, e.who, it, now, out) }
        if (e.boarded) {
            val first = !m.aboard
            m.aboard = true
            board(area, p, now, out)
            if (first && !p.status.ended) out += Action.Push // 다른 사람 페이지의 "함께 타요" 칩
            return
        }
        m.passenger.apply { consent = e.consent; pos = e.pos; ready = e.ready; ack = e.ack }
        val s = if (p.chained) p.spots.getOrNull(m.stop) else p.spot // 5차: 그 사람 지점
        val plan = s?.plans?.getOrNull(e.who)
        when (p.status) {
            Status.LINK -> {
                if (e.consent == false) { m.out = true; out += Action.EndMember(e.who) } // 거절한 사람은 빠진다(FR-059)
                chooseGroup(area, p, now, out)
            }
            Status.GOING -> {
                if (e.transit == "accept" && !m.transitAccepted && plan?.leg != null) {
                    m.transitAccepted = true
                    out += Action.Push
                    transitSaid(p, out)
                }
                if (e.pos != null && s != null && plan != null && plan.leg == null) { // 걷는 사람만 다시 센다
                    val (w, c) = walk(area, e.pos, s)
                    m.walkSec = w
                    if (c != plan.crossing) { // 건넌 뒤에는 페이지 "…빨라요"를 내린다
                        val moved = s.copy(plans = s.plans.mapIndexed { k, x -> if (k == e.who) x?.copy(crossing = c) else x })
                        p.spots = p.spots.map { if (it === s) moved else it }
                    }
                }
                if (e.ready && !m.readySpoken) { m.readySpoken = true; say(p, out, "${m.person.name}님이 나와 있어요") }
                if (e.late && !m.lateSpoken) { m.lateSpoken = true; say(p, out, "${m.person.name}님이 조금 늦는대요") }
            }
            else -> Unit
        }
    }

    /** 한 사람이 빠진다(FR-059). 남은 사람이 없으면 그 끝 상태로 끝내고, 확정 전이면 남은 사람으로 다시 고른다 */
    private fun leave(area: Area, p: Pickup, who: Int, ended: Status, now: Long, out: MutableList<Action>) {
        val m = p.members.getOrNull(who)?.takeUnless { it.out } ?: return
        if (p.chained && m.aboard) return // 5차: 이미 탄 사람이 공유를 꺼도 차에 있다. 남은 지점까지 길어서 빠짐으로 보지 않는다
        m.out = true
        if (p.active.isEmpty()) {
            p.lastLeft = m
            end(p, ended, out)
            out += Action.End
            return
        }
        if (p.status == Status.GOING) {
            val k = m.stop
            if (p.chained && !m.aboard && p.active.none { it.stop == k }) { // 5차: 그 지점에 남은 사람이 없다
                dropStop(p, k)
                if (k == p.chosen) { // 지금 가던 지점이 비었다: 당겨진 다음 지점으로(음성 한 줄), 없으면 만남
                    val next = nextStop(p, k)
                    if (next != null) advance(area, p, "${m.person.name}님은 이번에 함께 타지 않아요. ", next, now, out) else met(p, now, out)
                    return
                }
            }
            say(p, out, "${m.person.name}님은 이번에 함께 타지 않아요")
            out += Action.Push
            transitSaid(p, out) // 아직 "알겠어요" 전이던 사람이 빠지면 남은 사람은 모두 받아들인 것일 수 있다
            board(area, p, now, out) // 남은 사람이 모두 탔으면 만남
        } else chooseGroup(area, p, now, out)
    }

    /** link(또는 확정 전 다시 고르기): 남은 모두가 동의와 위치를 보냈고 차 위치가 있으면 함께 고른다 */
    private fun chooseGroup(area: Area, p: Pickup, now: Long, out: MutableList<Action>) {
        val car = p.track.lastOrNull()?.pos ?: return
        val act = p.active
        if (act.isEmpty() || act.any { it.passenger.consent != true || it.passenger.pos == null }) return
        val people = p.members.map { m -> if (m.out) null else m.passenger.pos!! to m.person.careful }
        p.spots = chooseGroup(area, car, people)
        p.nearbySec = p.spots.firstOrNull()?.takeIf { riders(it) > 0 }?.let { s ->
            nearby(area, car, people.filterNotNull())?.takeIf { it - s.meetSec > Rules.NEARBY_GAP_SEC }
        }
        p.chain = chain(area, car, people, p.destPos) // 5차
        p.status = if (p.spots.isEmpty() && p.chain == null) Status.NOSPOT else Status.SPOTS
        p.expireAt = now + Rules.WAIT_MS
        out += Action.Push
    }

    /** 대중교통 탈 사람이 모두 "알겠어요"를 누르면 한 줄(FR-057) */
    private fun transitSaid(p: Pickup, out: MutableList<Action>) {
        val s = p.spot ?: return
        val riders = p.members.indices.filter { !p.members[it].out && s.plans.getOrNull(it)?.leg != null }
        if (p.transitSpoken || riders.isEmpty() || riders.any { !p.members[it].transitAccepted }) return
        p.transitSpoken = true
        // ponytail: 노선이 하나인 시연 지역. 노선이 여럿이면 첫 사람의 노선으로 말한다
        val leg = s.plans[riders[0]]!!.leg!!
        val stop = leg.alight.name
        say(p, out, "${names(riders.map { p.members[it] })}이 ${leg.line} 타고 $stop${toParticle(stop)} 와요")
    }
}
