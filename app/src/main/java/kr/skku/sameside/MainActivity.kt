package kr.skku.sameside

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** 왼쪽 칸(사람과 차)과 오른쪽 칸(진행 중인 픽업). 조작은 기어가 P일 때만 받는다(원칙 I) */
class MainActivity : Activity() {

    private lateinit var ui: Ui
    private var full = false // 20명이 찬 상태에서 "+ 사람 추가"를 눌렀다
    // 3차 "여러 명 함께"(contracts/ui.md §1): 켜짐, 고른 차례, 4번째를 눌렀음
    private var together = false
    private val picked = mutableListOf<Person>()
    private var tooMany = false
    private var cancelUntil = 0L // 6차: "픽업 취소" 확인 중이면 이 uptime까지(contracts §5)
    private val disarm = Runnable { render() } // 4초 뒤 "픽업 취소"로 되돌린다. onDestroy에서 지운다

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ui = Ui(this)
        // 6차: 낮·밤 전환처럼 다시 만들어질 때 "여러 명 함께" 고르기를 되살린다(research R6).
        // 저장 상태에는 전화번호를 넣지 않고 사람 목록 안 위치만 둔다(PR 리뷰). 목록 밖 위치는 뺀다
        savedInstanceState?.let { b ->
            together = b.getBoolean("together")
            val all = People.load(this)
            picked += b.getIntArray("picked")?.asList().orEmpty().mapNotNull { all.getOrNull(it) }
        }
        findViewById<View>(R.id.peoplePane).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(getColor(R.color.pane_top), getColor(R.color.pane_bottom)),
        )
        // CALL_PHONE이 거부되면 자동 전화는 대체 알림으로 간다(FR-018). 차 위치는 런타임 권한이다.
        // 3차: READ_PHONE_STATE가 없으면 통화 중 표시 대신 "전화를 걸었어요"다(research R22)
        val need = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.POST_NOTIFICATIONS, "pleos.car.permission.FUSED_LOCATION",
            Manifest.permission.READ_PHONE_STATE)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 0)
        PickupService.start(this)
    }

    override fun onResume() {
        super.onResume()
        PickupService.onUpdate = ::render
        render()
    }

    override fun onPause() {
        PickupService.onUpdate = null
        super.onPause()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putBoolean("together", together)
        val all = People.load(this)
        out.putIntArray("picked", picked.map { all.indexOf(it) }.filter { it >= 0 }.toIntArray())
    }

    override fun onDestroy() {
        ui.closeDialog() // 다시 만들어질 때 옛 창이 남지 않게(contracts §1)
        window.decorView.removeCallbacks(disarm)
        super.onDestroy()
    }

    /** 6차 뒤로 버튼: "여러 명 함께" 고르기 중이면 끄고, 아니면 앱을 나간다 */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 주행 중에는 사람 칸이 숨어 있으니 보이지 않는 고르기를 지우지 않는다
        if (together && PickupService.park == true) { together = false; picked.clear(); tooMany = false; render() } else super.onBackPressed()
    }

    private fun render() {
        val park = PickupService.park == true
        val p = PickupService.pickup
        val busy = p != null && !p.status.ended
        // 6차 주행 중 한 줄 카드(contracts §1): 사람 칸을 숨기고 카드 하나만. 기어를 모르면 주행 중으로 본다
        findViewById<View>(R.id.peopleScroll).visibility = if (park) View.VISIBLE else View.GONE
        findViewById<View>(R.id.divider).visibility = if (park) View.VISIBLE else View.GONE
        if (!park || !busy) cancelUntil = 0 // 버튼이 없는 화면에서는 취소 확인을 지운다
        if (!park) {
            ui.closeDialog()
            val now = SystemClock.elapsedRealtime()
            // 기어 신호가 아직 없으면(앱 시작 직후) 주행 중으로 보되, 운전 중이라고 단정하지 않는다
            val g = if (PickupService.park == null) Glance(null, "기어 신호를 기다리고 있어요")
            else glance(p, PickupService.creating) { m -> contact(m, PickupService.inCall, PickupService.callSince, p?.lastCaller === m, now) }
            replace(R.id.screen, ui.column(28, *listOfNotNull(
                if (PickupService.serverDown) ui.banner(SERVER_DOWN) else null,
                ui.glanceCard(g),
            ).toTypedArray()))
            return
        }
        val list = People.load(this)
        replace(R.id.peoplePane, ui.people(list, People.loadCar(this), canPick = park && !busy, together, picked, tooMany,
            onPick = { person ->
                tooMany = false
                when {
                    !together -> PickupService.begin(person)
                    person in picked -> picked.remove(person)
                    picked.size >= Rules.MAX_GROUP -> tooMany = true
                    else -> picked += person
                }
                render()
            },
            onTogether = { together = !together; picked.clear(); tooMany = false; render() },
            onGo = { PickupService.beginGroup(picked.toList()); together = false; picked.clear(); render() },
            onAdd = {
                if (list.size >= Rules.MAX_PEOPLE) { full = true; render() }
                else ui.addPerson { person -> People.save(this, list + person); render() }
            },
            onCar = { ui.editCar(People.loadCar(this)) { People.saveCar(this, it); render() } },
        ).also { if (full) (it as LinearLayout).addView(ui.small("최대 20명까지 저장해요")); full = false })
        replace(R.id.screen, ui.situation(p, park, PickupService.serverDown, waiting = p == null && PickupService.creating,
            contactOf = { m -> contact(m, PickupService.inCall, PickupService.callSince, p?.lastCaller === m, SystemClock.elapsedRealtime()) },
            cancelArmed = SystemClock.uptimeMillis() < cancelUntil,
            onConfirm = { PickupService.send(Event.Confirm(it)) },
            onChain = { PickupService.send(Event.ConfirmChain) },
            onCancel = {
                val now = SystemClock.uptimeMillis()
                if (now < cancelUntil) { cancelUntil = 0; PickupService.send(Event.Cancel) }
                else { cancelUntil = now + 4_000; render(); window.decorView.removeCallbacks(disarm); window.decorView.postDelayed(disarm, 4_000) }
            },
            onDismiss = { PickupService.dismiss() },
            onArrive = { PickupService.send(Event.Arrive(it)) },
        ))
    }

    /** 위치가 올 때마다 다시 그리므로, 글자·켜짐이 같으면 그대로 둔다(접근성 이벤트와 깜빡임을 막는다) */
    private fun replace(id: Int, v: View) = findViewById<LinearLayout>(id).run {
        if (childCount == 1 && sig(getChildAt(0)) == sig(v)) return@run
        removeAllViews(); addView(v)
    }

    private fun sig(v: View): String = when (v) {
        is ViewGroup -> (0 until v.childCount).joinToString(",", "${v.isEnabled}[", "]") { sig(v.getChildAt(it)) }
        is TextView -> "${v.text}${v.isEnabled}"
        else -> "${v.javaClass.simpleName}${v.tag}${v.isEnabled}" // 그리기 View(지도·막대·연락 줄)는 tag가 그 값이다(research R25)
    }
}
