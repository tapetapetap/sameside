package kr.skku.sameside

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** 왼쪽 칸(사람과 차)과 오른쪽 칸(진행 중인 픽업). 조작은 기어가 P일 때만 받는다(원칙 I) */
class MainActivity : Activity() {

    private lateinit var ui: Ui
    private var full = false // 20명이 찬 상태에서 "+ 사람 추가"를 눌렀다

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ui = Ui(this)
        findViewById<View>(R.id.peoplePane).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(getColor(R.color.pane_top), getColor(R.color.pane_bottom)),
        )
        // CALL_PHONE이 거부되면 자동 전화는 대체 알림으로 간다(FR-018). 차 위치는 런타임 권한이다
        val need = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.POST_NOTIFICATIONS, "pleos.car.permission.FUSED_LOCATION")
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

    private fun render() {
        val park = PickupService.park == true
        val p = PickupService.pickup
        val busy = p != null && !p.status.ended
        val list = People.load(this)
        replace(R.id.peoplePane, ui.people(list, People.loadCar(this), canPick = park && !busy,
            onPick = { PickupService.begin(it); render() },
            onAdd = {
                if (list.size >= Rules.MAX_PEOPLE) { full = true; render() }
                else ui.addPerson { person -> People.save(this, list + person); render() }
            },
            onCar = { ui.editCar(People.loadCar(this)) { People.saveCar(this, it); render() } },
        ).also { if (full) (it as LinearLayout).addView(ui.small("최대 20명까지 저장해요")); full = false })
        replace(R.id.screen, ui.situation(p, park, PickupService.serverDown, waiting = p == null && PickupService.creating,
            onConfirm = { PickupService.send(Event.Confirm(it)) },
            onCancel = { PickupService.send(Event.Cancel) },
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
        else -> "${v.javaClass.simpleName}${v.isEnabled}"
    }
}
