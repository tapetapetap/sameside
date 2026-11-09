package kr.skku.sameside

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/* 정차 중 화면(contracts/ui.md §1). 문구는 계약 그대로 쓴다 */

/** 분 표시는 모두 올림(data-model §8) */

/** 앞 3자리와 끝 4자리만 보인다 */
fun masked(phone: String) = if (phone.length < 7) phone else "${phone.take(3)}-****-${phone.takeLast(4)}"

fun link(id: String) = "http://localhost:8090/p/$id"

fun Ui.button(label: String, enabled: Boolean, primary: Boolean = true, onClick: () -> Unit) =
    text(label, 24f, if (primary) R.color.on_accent else R.color.blue, 700).apply {
        gravity = Gravity.CENTER
        background = round(c(if (primary) R.color.blue else R.color.blue_soft), 18f)
        setPadding(px(28), px(18), px(28), px(18))
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.4f
        if (enabled) setOnClickListener { onClick() }
    }

/** 왼쪽 칸: 데리러 갈 사람과 내 차 */
fun Ui.people(list: List<Person>, car: Car?, canPick: Boolean, onPick: (Person) -> Unit, onAdd: () -> Unit, onCar: () -> Unit): View {
    val rows = list.map { p ->
        row(16, text(p.name, 28f, R.color.text1, 700), text(masked(p.phone), 22f, R.color.text3),
            *listOfNotNull(if (p.careful) chip("가까이만", R.color.blue, R.color.blue_soft) else null).toTypedArray(), grow = 1).apply {
            background = round(c(R.color.card), 18f, c(R.color.line))
            setPadding(px(24), px(20), px(24), px(20))
            isEnabled = canPick
            alpha = if (canPick) 1f else 0.5f
            if (canPick) setOnClickListener { onPick(p) }
        }
    }
    val carLine = car?.let { listOf(it.color, it.model, it.plate).filter(String::isNotBlank).joinToString(" ") }.orEmpty()
    return column(18,
        headline("데리러 갈 사람", 32f),
        *rows.toTypedArray(),
        button("+ 사람 추가", canPick, primary = false, onClick = onAdd),
        small("내 차"),
        button(carLine.ifBlank { "차종·색·번호판 넣기" }, canPick, primary = false, onClick = onCar),
    )
}

/** 오른쪽 칸: 진행 중인 픽업의 상황 */
fun Ui.situation(p: Pickup?, park: Boolean, serverDown: Boolean, waiting: Boolean,
                 onConfirm: (Int) -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit, onArrive: (Boolean) -> Unit): View {
    val top = listOfNotNull(if (serverDown) banner("서버에 연결하지 못했어요 · 2초마다 다시 시도해요") else null)
    val cancel = button("픽업 취소", park, primary = false, onClick = onCancel)
    val who = p?.person?.name.orEmpty()
    val body: List<View> = when (p?.status) {
        null -> listOf(headline(if (waiting) "링크를 만들고 있어요" else "누구를 데리러 갈까요?"), body("왼쪽에서 사람을 고르면 링크를 만들어요"))
        Status.LINK -> listOf(
            qr(link(p.id)),
            text(link(p.id), 22f, R.color.text3),
            headline(linkLine(p), 36f),
            cancel,
        )
        Status.SPOTS -> listOf(headline("만날 곳", 40f)) + p.spots.mapIndexed { i, s -> spotCard(s, who, primary = i == 0, park) { onConfirm(i) } } +
            listOfNotNull(transitLine(p)?.let { body(it) }) + cancel
        Status.NOSPOT -> listOf(headline("가까운 곳에 같은 편 자리가 없어요", 36f), body("위치 공유는 이어서 해요"), cancel)
        Status.GOING -> listOfNotNull(
            // 2차 도착 확인 카드: 버튼은 P에서만 눌린다(contracts/ui.md 2차 §1)
            if (p.arriveCard) card(headline("도착했다고 알릴까요?", 40f),
                row(16, button("보내기", park) { onArrive(true) }, button("아니요", park, primary = false) { onArrive(false) })) else null,
            headline("${who}님 · ${p.spot?.line.orEmpty()} · ${minutes(p.etaSec ?: 0.0)}분", 36f),
            body("내비에 넣었어요. 출발하세요"),
            p.lostDest?.let { small("원래 목적지($it) 대신 만날 곳으로 안내해요") },
            transitLine(p)?.let { body(it) },
            cancel,
        )
        Status.MET -> listOf(headline("만났어요"), body("위치 공유를 끝냈어요"), button("확인", true, onClick = onDismiss))
        Status.CANCELED -> listOf(headline("픽업을 취소했어요"), button("확인", true, onClick = onDismiss))
        Status.STOPPED -> listOf(headline("${who}님이 위치 공유를 껐어요"), button("확인", true, onClick = onDismiss))
        Status.EXPIRED -> listOf(headline("시간이 지나 위치 공유를 끝냈어요", 40f), button("확인", true, onClick = onDismiss))
    }
    return column(28, *(top + body).toTypedArray())
}

/** link 상태 안내 한 줄: 위에서부터 처음 맞는 줄(contracts/ui.md §1) */
fun linkLine(p: Pickup): String {
    val who = p.person.name
    return when {
        p.passenger.consent == false -> "${who}님이 위치를 공유하지 않았어요"
        p.passenger.consent == true && p.passenger.pos == null -> "${who}님 위치를 기다리고 있어요"
        p.passenger.consent == true && p.track.isEmpty() -> "차 위치를 확인하고 있어요"
        else -> "${who}님이 링크를 열면 만날 곳을 골라요"
    }
}

private fun transitLine(p: Pickup): String? {
    val o = p.offer ?: return null
    return if (p.transitAccepted) "${p.person.name}님이 ${o.leg.line} 타고 와요"
    else "${p.person.name}님께 ${o.leg.line} ${o.leg.stops}정거장 안을 보냈어요"
}

private fun Ui.spotCard(s: Spot, who: String, primary: Boolean, park: Boolean, onPick: () -> Unit) = card(
    text(s.line, if (primary) 40f else 30f, R.color.text1, 700),
    body(s.reason, if (primary) 26f else 22f),
    small("${who}님 걸어서 ${minutes(s.walkSec)}분 · 차 ${minutes(s.carSec)}분"),
    *listOfNotNull(Pick.compareLine(s)?.let { small(it) }).toTypedArray(), // 2차: 건너기 n분 · 유턴 m분
    button("이 자리로 갈게요", park, primary = primary, onClick = onPick),
)

private var lastQr: Pair<String, Bitmap>? = null // 위치마다 다시 그려도 QR은 링크가 바뀔 때만 만든다

private fun Ui.qr(text: String): View {
    val size = px(300)
    val bmp = lastQr?.takeIf { it.first == text }?.second ?: run {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        }.also { lastQr = text to it }
    }
    return ImageView(ctx).apply { setImageBitmap(bmp); layoutParams = LinearLayout.LayoutParams(size, size) }
}

/* ---------- 대화상자 ---------- */

private fun field(ctx: Context, hint: String, phone: Boolean = false) = EditText(ctx).apply {
    this.hint = hint
    inputType = if (phone) InputType.TYPE_CLASS_PHONE else InputType.TYPE_CLASS_TEXT
}

private fun error(ui: Ui) = ui.text("", 20f, R.color.red_text, 600).apply { visibility = View.GONE }

private fun show(ctx: Context, title: String, view: View, onSave: () -> Boolean) {
    val d = AlertDialog.Builder(ctx).setTitle(title).setView(view)
        .setPositiveButton("저장", null).setNegativeButton("취소", null).create()
    d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (onSave()) d.dismiss() } }
    d.show()
}

private fun TextView.say(s: String?) { text = s.orEmpty(); visibility = if (s == null) View.GONE else View.VISIBLE }

/** "+ 사람 추가". 틀리면 그 칸 아래에 문구를 보이고 닫지 않는다 */
fun Ui.addPerson(onSaved: (Person) -> Unit) {
    val name = field(ctx, "이름"); val nameErr = error(this)
    val phone = field(ctx, "전화번호", phone = true); val phoneErr = error(this)
    val careful = CheckBox(ctx).apply { text = "아이·어르신(가까운 곳만 찾아요)" }
    val view = column(8, name, nameErr, phone, phoneErr, careful).apply { setPadding(px(24), px(8), px(24), 0) }
    show(ctx, "데리러 갈 사람", view) {
        val n = People.name(name.text.toString()); val p = People.phone(phone.text.toString())
        nameErr.say(if (n == null) "이름은 1~10자로 적어 주세요" else null)
        phoneErr.say(if (p == null) "전화번호를 확인해 주세요" else null)
        if (n != null && p != null) onSaved(Person(n, p, careful.isChecked))
        n != null && p != null
    }
}

/** "내 차" */
fun Ui.editCar(now: Car?, onSaved: (Car) -> Unit) {
    val model = field(ctx, "차종").apply { setText(now?.model) }
    val color = field(ctx, "색").apply { setText(now?.color) }
    val plate = field(ctx, "번호판").apply { setText(now?.plate) }
    val phone = field(ctx, "내 번호(선택)", phone = true).apply { setText(now?.phone) }
    val err = error(this)
    val view = column(8, model, color, plate, phone, err).apply { setPadding(px(24), px(8), px(24), 0) }
    show(ctx, "내 차", view) {
        val c = People.car(model.text.toString(), color.text.toString(), plate.text.toString(), phone.text.toString())
        err.say(if (c == null) "전화번호를 확인해 주세요" else null)
        c?.let(onSaved)
        c != null
    }
}
