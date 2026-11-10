package kr.skku.sameside

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/* 3차 그리기 View(지도 SpotMap, 연락 줄 ContactRow)와 그 순수 계산(data-model §7·§8, contracts/ui.md §3·§4) */

/** 지도 밖 점(지점 원점 m 좌표)을 반폭·반높이 사각형 위로 옮긴다. 방향은 그대로다. 안이면 null */
fun edge(dx: Double, dy: Double, halfW: Double, halfH: Double): Pair<Double, Double>? {
    if (abs(dx) <= halfW && abs(dy) <= halfH) return null
    val k = minOf(if (dx == 0.0) Double.MAX_VALUE else halfW / abs(dx), if (dy == 0.0) Double.MAX_VALUE else halfH / abs(dy))
    return dx * k to dy * k
}

/** 1000m 미만은 10m 단위("350m"), 그 위는 소수 한 자리("1.9km") */
fun distLabel(m: Double): String {
    val r = (m / 10).roundToInt() * 10
    return if (r < 1000) "${r}m" else String.format(Locale.ROOT, "%.1fkm", m / 1000)
}

/** 이름표 칸(px, 왼·위·오른·아래) */
data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    fun hits(o: Box) = l < o.r && o.l < r && t < o.b && o.t < b
}

/** 후보 칸 가운데 지도(w×h) 안에 들고 앞서 놓은 칸과 겹치지 않는 첫 번째. 없으면 첫 후보(기본 자리) */
fun firstFree(candidates: List<Box>, taken: List<Box>, w: Float, h: Float): Int =
    candidates.indexOfFirst { b -> b.l >= 0 && b.t >= 0 && b.r <= w && b.b <= h && taken.none { it.hits(b) } }.coerceAtLeast(0)

private fun View.dp(v: Float) = v * resources.displayMetrics.density
private fun View.sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
private fun textPaint(weight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create(Typeface.SANS_SERIF, weight, false)
}

/** 지도에 그릴 사람. 위치가 없으면 그리지 않는다. line은 대중교통으로 오는 사람의 노선 */
class MapPerson(val name: String, val pos: LatLon?, val line: String? = null)

/**
 * 만날 곳 둘레 지도(contracts/ui.md §3, research R24). 지점을 가운데 두고 짧은 변의 반이 halfM(m)이다.
 * 지역 데이터(OSM)의 길 선으로 그려 타일 지도와 망이 필요 없다. 화면을 다시 붙일지는 tag(지점·사람·차를 5m 단위로)로 정한다
 */
class SpotMap(context: Context, private val area: Area, private val spot: Spot, private val people: List<MapPerson>,
              private val car: LatLon?, private val halfM: Double, private val heightDp: Float) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bold = textPaint(700)
    private val small = textPaint(500)
    private val clip = Path()
    private val taken = mutableListOf<Box>() // 이번 그리기에서 핀·점·화살표·이름표가 차지한 칸

    init {
        fun r5(p: LatLon?) = p?.let { xy(spot.pos, it) }?.let { "${(it.x / 5).roundToInt()},${(it.y / 5).roundToInt()}" }
        tag = "map ${spot.landmark.id} ${people.joinToString(";") { "${it.name}@${r5(it.pos)}${it.line.orEmpty()}" }} car@${r5(car)}"
        contentDescription = "${spot.line} 둘레 지도"
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, w), dp(heightDp).toInt())

    private fun color(id: Int) = context.getColor(id)

    /** 글자 둘레를 지도 바탕색으로 감싸 길 위에서도 읽히게 */
    private fun label(c: Canvas, s: String, x: Float, y: Float, p: Paint, fg: Int) {
        p.textAlign = Paint.Align.CENTER
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(4f); p.color = color(R.color.map_bg)
        c.drawText(s, x, y, p)
        p.style = Paint.Style.FILL; p.color = fg
        c.drawText(s, x, y, p)
    }

    private fun box(x: Float, y: Float, r: Float) = Box(x - r, y - r, x + r, y + r)

    /** 이름표를 at 후보 가운데 겹치지 않는 첫 자리에 그린다(가운데 x, 글자 기준선 y) */
    private fun place(c: Canvas, s: String, at: List<Pair<Float, Float>>, p: Paint, fg: Int) {
        val half = p.measureText(s) / 2 + dp(3f)
        val boxes = at.map { (x, y) -> Box(x - half, y + p.ascent(), x + half, y + p.descent()) }
        val i = firstFree(boxes, taken, width.toFloat(), height.toFloat())
        taken += boxes[i]
        label(c, s, at[i].first, at[i].second, p, fg)
    }

    /** 점(x, y) 둘레 위·아래·오른쪽·왼쪽 */
    private fun around(s: String, x: Float, y: Float, up: Float, p: Paint): List<Pair<Float, Float>> {
        val side = p.measureText(s) / 2 + dp(16f)
        return listOf(x to y - up, x to y + up + dp(14f), x + side to y + dp(6f), x - side to y + dp(6f))
    }

    private fun arrowHead(c: Canvas, x: Float, y: Float, dx: Float, dy: Float, size: Float, col: Int) {
        val path = Path().apply {
            moveTo(x + dx * size, y + dy * size)
            lineTo(x - dy * size * 0.7f, y + dx * size * 0.7f)
            lineTo(x + dy * size * 0.7f, y - dx * size * 0.7f)
            close()
        }
        fill.color = col
        c.drawPath(path, fill)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        clip.reset()
        clip.addRoundRect(0f, 0f, w, h, dp(16f), dp(16f), Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        c.drawColor(color(R.color.map_bg))
        taken.clear()
        val k = (minOf(w, h) / 2) / halfM.toFloat() // px / m
        val o = spot.pos
        fun px(p: LatLon): Pair<Float, Float> = xy(o, p).let { w / 2 + it.x.toFloat() * k to h / 2 - it.y.toFloat() * k }
        fun path(pts: List<LatLon>) = Path().apply {
            pts.forEachIndexed { i, p -> px(p).let { (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) } }
        }
        // 길: 테두리 → 안쪽 → 지점 길
        val roadW = 10f * k
        val roads = area.roads.map { path(it.points) }
        stroke.pathEffect = null
        stroke.color = color(R.color.road_edge); stroke.strokeWidth = roadW + dp(3f)
        roads.forEach { c.drawPath(it, stroke) }
        stroke.color = color(R.color.road); stroke.strokeWidth = roadW
        roads.forEach { c.drawPath(it, stroke) }
        stroke.color = color(R.color.blue_soft)
        c.drawPath(path(spot.road), stroke)

        val (sx, sy) = w / 2 to h / 2
        val margin = dp(22f) / k
        val halfW = (w / 2 / k - margin).toDouble()
        val halfH = (h / 2 / k - margin).toDouble()
        val green = color(R.color.green)
        small.textSize = sp(17f)
        bold.textSize = sp(19f)
        // 걷는 점선(지도 안 사람만)
        for (m in people) {
            val q = m.pos?.let { xy(o, it) } ?: continue
            if (edge(q.x, q.y, halfW, halfH) != null) continue
            val (x, y) = px(m.pos)
            stroke.pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(7f)), 0f)
            stroke.color = green; stroke.strokeWidth = dp(3f)
            c.drawLine(x, y, sx, sy, stroke)
            stroke.pathEffect = null
        }
        // 기준 랜드마크(2차 "○○ 지나")
        val passing = spot.passing?.let { l ->
            val (x, y) = px(l.pos)
            fill.color = color(R.color.text3)
            c.drawCircle(x, y, dp(5f), fill)
            taken += box(x, y, dp(5f))
            Triple(l.name, x, y)
        }
        // 지점 핀과 이름
        val blue = color(R.color.blue)
        fill.color = blue
        c.drawPath(Path().apply {
            moveTo(sx - dp(8f), sy - dp(18f)); lineTo(sx + dp(8f), sy - dp(18f)); lineTo(sx, sy); close()
        }, fill)
        c.drawCircle(sx, sy - dp(24f), dp(12f), fill)
        fill.color = color(R.color.on_accent)
        c.drawCircle(sx, sy - dp(24f), dp(4.5f), fill)
        taken += Box(sx - dp(12f), sy - dp(36f), sx + dp(12f), sy)
        // 들어오는 방향: 지점 뒤 70m에서 지점까지
        val dx = sin(Math.toRadians(spot.heading)).toFloat()
        val dy = -cos(Math.toRadians(spot.heading)).toFloat() // 화면 y는 아래로
        val ax = sx - dx * 70f * k
        val ay = sy - dy * 70f * k
        val ex = sx - dx * dp(14f)
        val ey = sy - dy * dp(14f)
        stroke.color = blue; stroke.strokeWidth = dp(5f)
        c.drawLine(ax, ay, ex - dx * dp(8f), ey - dy * dp(8f), stroke)
        arrowHead(c, ex - dx * dp(8f), ey - dy * dp(8f), dx, dy, dp(11f), blue)
        // 사람: 지도 안이면 점과 이름, 밖이면 가장자리 화살표와 거리(대중교통이면 노선)
        val outside = mutableListOf<Triple<MutableList<MapPerson>, Pair<Float, Float>, XY>>() // 같은 쪽 가장자리면 이름표 하나로 묶는다
        val inside = mutableListOf<Triple<String, Float, Float>>()
        for (m in people) {
            val pos = m.pos ?: continue
            val q = xy(o, pos)
            val e = edge(q.x, q.y, halfW, halfH)
            if (e == null) {
                val (x, y) = px(pos)
                fill.color = color(R.color.on_accent); c.drawCircle(x, y, dp(10f), fill)
                fill.color = green; c.drawCircle(x, y, dp(7.5f), fill)
                taken += box(x, y, dp(10f))
                inside += Triple(m.name, x, y)
                continue
            }
            val at = sx + e.first.toFloat() * k to sy - e.second.toFloat() * k
            outside.firstOrNull { abs(it.second.first - at.first) < dp(48f) && abs(it.second.second - at.second) < dp(48f) }
                ?.first?.add(m) ?: outside.add(Triple(mutableListOf(m), at, q))
        }
        for ((_, at, q) in outside) {
            val len = hypot(q.x, q.y).toFloat()
            arrowHead(c, at.first, at.second, q.x.toFloat() / len, -q.y.toFloat() / len, dp(12f), green)
            taken += box(at.first, at.second, dp(12f))
        }
        // 차(가는 중, 지도 안일 때)
        car?.let { cp ->
            val q = xy(o, cp)
            if (edge(q.x, q.y, halfW, halfH) == null) {
                val (x, y) = px(cp)
                fill.color = color(R.color.on_accent)
                c.drawRoundRect(x - dp(13f), y - dp(13f), x + dp(13f), y + dp(13f), dp(6f), dp(6f), fill)
                fill.color = blue
                c.drawRoundRect(x - dp(10f), y - dp(10f), x + dp(10f), y + dp(10f), dp(4f), dp(4f), fill)
                taken += box(x, y, dp(13f))
            }
        }
        // 이름표: 북쪽·축척·출처 자리를 비워 두고, 지점 → 지도 안 사람 → 가장자리 사람 → 기준 랜드마크 차례로 빈 자리에
        val nx = w - dp(26f)
        taken += Box(nx - dp(14f), dp(6f), nx + dp(14f), dp(50f))
        taken += Box(dp(10f), h - dp(42f), dp(24f) + maxOf(50f * k, dp(40f)), h - dp(6f))
        taken += Box(w - dp(120f), h - dp(22f), w, h)
        place(c, spot.line, listOf(sx to sy - dp(44f), sx to sy + dp(30f)), bold, color(R.color.text1))
        for ((name, x, y) in inside) place(c, name, around(name, x, y, dp(16f), bold), bold, green)
        for ((group, at, q) in outside) {
            val (ex2, ey2) = at
            val who = group.joinToString("·") { it.name }
            val text = group[0].line?.let { "$who · $it" } ?: "$who ${distLabel(q.len)}"
            val half = bold.measureText(text) / 2 + dp(8f)
            fun cx(x: Float) = if (half * 2 >= w) w / 2 else x.coerceIn(half, w - half) // 지도보다 긴 이름표도 죽지 않게
            val ty = (ey2 + if (q.y > 0) dp(30f) else -dp(16f)).coerceIn(dp(22f), h - dp(10f))
            place(c, text, listOf(cx(ex2) to ty, cx(ex2 + half + dp(20f)) to ey2 + dp(6f), cx(ex2 - half - dp(20f)) to ey2 + dp(6f),
                cx(ex2) to ty + dp(26f), cx(ex2) to ty - dp(26f)), bold, green)
        }
        passing?.let { (name, x, y) -> place(c, name, around(name, x, y, dp(10f), small), small, color(R.color.text3)) }
        // 북쪽, 축척 50m, 출처
        arrowHead(c, nx, dp(18f), 0f, -1f, dp(9f), color(R.color.text2))
        label(c, "N", nx, dp(44f), bold, color(R.color.text2))
        val bar = 50f * k
        stroke.color = color(R.color.text2); stroke.strokeWidth = dp(3f)
        c.drawLine(dp(16f), h - dp(14f), dp(16f) + bar, h - dp(14f), stroke)
        small.textAlign = Paint.Align.LEFT
        small.textSize = sp(15f)
        small.style = Paint.Style.FILL; small.color = color(R.color.text2)
        c.drawText("50m", dp(16f), h - dp(22f), small)
        small.textAlign = Paint.Align.RIGHT
        small.textSize = sp(12f); small.color = color(R.color.text3)
        c.drawText("© OpenStreetMap", w - dp(10f), h - dp(8f), small)
        c.restore()
    }
}

/** 남은 거리 막대(FR-069). f는 0~1 */
class Bar(context: Context, private val f: Double) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init { tag = "bar ${(f * 50).roundToInt()}" }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, w), dp(12f).toInt())

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        p.color = context.getColor(R.color.line)
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, p)
        p.color = context.getColor(R.color.blue)
        c.drawRoundRect(0f, 0f, maxOf(height.toFloat(), width * f.toFloat()), height.toFloat(), r, r, p)
    }
}

/**
 * 가는 중 사람마다 연락 줄(contracts/ui.md §4, research R25). 상태는 그릴 때마다 read()로 다시 읽어
 * 초읽기 숫자가 바뀌어도 화면을 다시 붙이지 않는다. 효과 위상은 uptimeMillis라 다시 붙어도 이어진다
 */
class ContactRow(context: Context, private val name: String, private val read: () -> Contact) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val title = textPaint(700)
    private val line = textPaint(500)
    private val num = textPaint(800).apply { textAlign = Paint.Align.CENTER }
    private var said: ContactKind? = null

    init {
        val x = read()
        tag = "contact $name ${x.kind}"
        contentDescription = "${name}님 ${status(x)}"
        said = x.kind
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, w), dp(88f).toInt())

    private fun color(id: Int) = context.getColor(id)

    private fun status(x: Contact) = when (x.kind) {
        ContactKind.OUT -> "함께 안 타요"
        ContactKind.ABOARD -> "탔어요"
        ContactKind.CALLING -> "${name}님과 통화 중 · ${(x.sec ?: 0) / 60}:${"%02d".format((x.sec ?: 0) % 60)}"
        ContactKind.READY -> "나와 있어요"
        ContactKind.ACK -> "지금 나간대요"
        ContactKind.MOVING -> "나오고 있어요"
        ContactKind.CALLED -> "전화를 걸었어요 · ${x.sec ?: 0}초 전"
        ContactKind.PHONE_NOTIFIED -> "${name}님 폰으로 알렸어요"
        ContactKind.NOTIFIED -> "지금 나오세요를 보냈어요 · " + if ((x.sec ?: 0) > 0) "${x.sec}초 뒤 전화" else "곧 전화"
        ContactKind.WAIT -> "알림 전"
    }

    override fun onDraw(c: Canvas) {
        val x = read()
        if (x.kind != said) { said = x.kind; contentDescription = "${name}님 ${status(x)}" }
        val cy = height / 2f
        val cx = dp(28f)
        val r = dp(28f)
        val anim = ValueAnimator.areAnimatorsEnabled()
        val green = color(R.color.green)
        val amber = color(R.color.amber)
        // 통화 중: 원에서 초록 물결 두 겹이 1.6초마다 퍼진다(움직임 줄이기면 멈춘 한 겹)
        if (x.kind == ContactKind.CALLING) {
            ring.color = green; ring.strokeWidth = dp(3f)
            if (anim) for (k in 0..1) {
                val f = ((SystemClock.uptimeMillis() + k * 800) % 1600) / 1600f
                ring.alpha = ((1 - f) * 170).toInt()
                c.drawCircle(cx, cy, r + f * dp(16f), ring)
            } else { ring.alpha = 130; c.drawCircle(cx, cy, r + dp(7f), ring) }
        }
        fill.color = color(when (x.kind) {
            ContactKind.OUT, ContactKind.WAIT -> R.color.line
            ContactKind.NOTIFIED, ContactKind.PHONE_NOTIFIED -> R.color.amber_soft
            ContactKind.CALLED -> R.color.card
            else -> R.color.green_soft
        })
        c.drawCircle(cx, cy, r, fill)
        ring.alpha = 255
        if (x.kind == ContactKind.CALLED) { ring.color = green; ring.strokeWidth = dp(2.5f); c.drawCircle(cx, cy, r - dp(1.5f), ring) }
        // 알림 보냄: 호박색 링이 60초 동안 시계 방향으로 줄어든다
        if (x.kind == ContactKind.NOTIFIED) {
            val left = (x.sec ?: 0) / 60f
            ring.strokeWidth = dp(4f)
            val g = r - dp(2f)
            ring.color = amber
            c.drawArc(cx - g, cy - g, cx + g, cy + g, -90f + 360f * (1 - left), 360f * left, false, ring)
        }
        val icon = when (x.kind) {
            ContactKind.OUT -> 0
            ContactKind.ABOARD, ContactKind.READY, ContactKind.ACK -> R.drawable.ic_check
            ContactKind.MOVING -> R.drawable.ic_walk
            ContactKind.CALLING, ContactKind.CALLED -> R.drawable.ic_call
            else -> R.drawable.ic_bell
        }
        val tint = when (x.kind) {
            ContactKind.WAIT -> color(R.color.text3)
            ContactKind.NOTIFIED, ContactKind.PHONE_NOTIFIED -> amber
            else -> green
        }
        if (icon != 0) context.getDrawable(icon)?.mutate()?.let { d ->
            d.setTint(tint)
            val s = if (x.kind == ContactKind.NOTIFIED) dp(10f) else dp(13f)
            val iy = if (x.kind == ContactKind.NOTIFIED) cy - dp(7f) else cy
            d.setBounds((cx - s).toInt(), (iy - s).toInt(), (cx + s).toInt(), (iy + s).toInt())
            d.draw(c)
        }
        if (x.kind == ContactKind.NOTIFIED) {
            num.textSize = sp(15f); num.color = amber
            c.drawText("${x.sec ?: 0}", cx, cy + dp(19f), num)
        }
        title.textSize = sp(26f); title.color = color(R.color.text1)
        c.drawText("${name}님", dp(76f), cy - dp(4f), title)
        line.textSize = sp(22f); line.color = color(if (x.kind == ContactKind.OUT) R.color.text3 else R.color.text2)
        c.drawText(status(x), dp(76f), cy + dp(28f), line)
        when (x.kind) {
            ContactKind.CALLING -> if (anim) postInvalidateOnAnimation() else postInvalidateDelayed(1_000)
            ContactKind.NOTIFIED, ContactKind.CALLED -> postInvalidateDelayed(1_000) // 숫자는 1초마다
            else -> Unit
        }
    }
}
