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

/** 지도에 그릴 사람(4차 data-model §1). 위치가 없으면 그리지 않는다. leg는 대중교통으로 오면 그 구간, slot은 사람 색 번호 */
class MapPerson(val name: String, val pos: LatLon?, val leg: TransitLeg? = null, val slot: Int = 0) {
    val line get() = leg?.line // 가는 중 SpotMap의 가장자리 글("민지 · 1호선")
}

private val SCALES = intArrayOf(20, 50, 100, 200, 500, 1000, 2000)

/** 축척 막대: 막대 길이(m × k px)가 지도 폭의 1/4에 가장 가까운 후보(4차 data-model §6) */
fun scaleBar(k: Double, widthPx: Float): Int = SCALES.minBy { abs(it * k - widthPx / 4) }

/** 1000m 미만은 "50m", 그 위는 "1km" */
fun scaleLabel(m: Int) = if (m < 1000) "${m}m" else "${m / 1000}km"

/** 이동 경로 구간. leg가 null이면 걷기(4차 data-model §2) */
data class Seg(val from: LatLon, val to: LatLon, val leg: TransitLeg?)

fun route(pos: LatLon, leg: TransitLeg?, spot: LatLon): List<Seg> =
    if (leg == null) listOf(Seg(pos, spot, null))
    else listOf(Seg(pos, leg.board.pos, null), Seg(leg.board.pos, leg.alight.pos, leg), Seg(leg.alight.pos, spot, null))

/** 사람 지도 범위: 가운데(m)와 축척(px/m). 짧은 변은 minShortM보다 좁아지지 않는다(data-model §3) */
data class Fit(val cx: Double, val cy: Double, val k: Double)

fun fit(points: List<XY>, w: Float, h: Float, pad: Float, minShortM: Double = 100.0): Fit {
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    var k = minOf(w, h) / minShortM
    if (maxX > minX) k = minOf(k, (w - 2 * pad) / (maxX - minX))
    if (maxY > minY) k = minOf(k, (h - 2 * pad) / (maxY - minY))
    return Fit((minX + maxX) / 2, (minY + maxY) / 2, k)
}

/** 사람 색 번호: 첫 글자 원과 같은 번호. 이 픽업에서 앞사람이 썼으면 다음 칸부터 안 쓴 첫 번호(data-model §4) */
fun colorSlots(names: List<String>, n: Int = 5): List<Int> {
    val used = mutableSetOf<Int>()
    return names.map { name ->
        val first = Math.floorMod(name.hashCode(), n)
        val slot = (0 until n).map { (first + it) % n }.firstOrNull { it !in used } ?: first
        used += slot
        slot
    }
}

/** 진행 방향(북 0°, 시계 방향)의 오른쪽 단위 벡터. 우측 통행이라 같은 편 길가는 늘 이쪽이다(data-model §5) */
fun rightOf(headingDeg: Double) = headingVec(headingDeg + 90.0)

/** 차선 폭(m). 서부로 두 방향 길 선 간격(8.6~9.3m)에 3차로가 맞는 값(4차 research R41) */
const val LANE_M = 3.1

/** 길 선을 왼쪽(+)·오른쪽(−)으로 d(m) 나란히 옮긴 선. 꼭짓점은 양옆 선분 법선의 평균 방향으로 옮긴다(굽은 곳이 조금 좁아짐) */
fun offsetLine(pts: List<XY>, d: Double): List<XY> = pts.indices.map { i ->
    fun left(a: XY, b: XY) = (b - a).let { v -> XY(-v.y / v.len, v.x / v.len) }
    val n = when (i) {
        0 -> left(pts[0], pts[1])
        pts.lastIndex -> left(pts[i - 1], pts[i])
        else -> (left(pts[i - 1], pts[i]) + left(pts[i], pts[i + 1])).let { it * (1.0 / it.len) }
    }
    pts[i] + n * d
}

/** 차 세울 곳 기하(지점 원점 m). r은 지점을 지점 길 선에 투영한 점이다(data-model §5). 길 폭은 차선 수 × LANE_M */
data class StopGeom(val r: XY, val band: XY, val arrowFrom: XY, val arrowTo: XY, val stand: List<XY>)

fun stopGeom(area: Area, spot: Spot, n: Int): StopGeom {
    val road = area.road(spot.landmark.roadId)
    val r = xy(spot.pos, pointAlong(area.center, road, project(area.center, road, spot.pos).along))
    val d = headingVec(spot.heading)
    val e = rightOf(spot.heading)
    val half = road.lanes * LANE_M / 2
    val arrowTo = r + e * (half - LANE_M / 2) - d * 9.0 // 맨 오른쪽 차로 가운데, 띠(18m) 뒤 끝
    return StopGeom(r, r + e * (half - 1.5), arrowTo - d * 30.0, arrowTo,
        List(n) { k -> r + e * (half + 3.0) + d * ((k - (n - 1) / 2.0) * 4.0) }) // 서 있을 곳: 길 가장자리에서 3m 인도 쪽
}

/**
 * 세 지도(가는 중 SpotMap, 4차 PeopleMap·StopMap)가 함께 쓰는 그리기 도우미(4차 plan Complexity Tracking).
 * 지역 데이터(OSM)의 길 선으로 그려 타일 지도와 망이 필요 없다
 */
abstract class MapBase(context: Context, protected val area: Area, private val heightDp: Float) : View(context) {
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val bold = textPaint(700)
    protected val small = textPaint(500)
    private val namePaint = textPaint(700)
    private val clip = Path()
    protected val taken = mutableListOf<Box>() // 이번 그리기에서 핀·점·화살표·이름표가 차지한 칸

    /** 4차 두 지도의 이름표 양옆 여유. 3dp면 "성균관대역"과 "민지"가 붙어 한 낱말처럼 읽혔다(에뮬레이터 캡처 11/12) */
    protected val labelGap get() = dp(8f)
    protected val walkDash by lazy { DashPathEffect(floatArrayOf(dp(8f), dp(7f)), 0f) } // 걷는 점선(그릴 때마다 만들지 않게)
    protected val bandDash by lazy { DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f) } // 세울 자리 띠 테두리
    protected val laneDash by lazy { DashPathEffect(floatArrayOf(dp(10f), dp(9f)), 0f) } // 차선(4차 FR-085)
    private var plate: Box? = null // 지도 이름표 칸

    /** 지역 데이터 점들을 px()로 옮긴 길 선 */
    protected fun path(pts: List<LatLon>, px: (LatLon) -> Pair<Float, Float>) = Path().apply {
        pts.forEachIndexed { i, p -> px(p).let { (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) } }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, w), dp(heightDp).toInt())

    protected fun color(id: Int) = context.getColor(id)

    /** 글자 둘레를 지도 바탕색으로 감싸 길 위에서도 읽히게 */
    protected fun label(c: Canvas, s: String, x: Float, y: Float, p: Paint, fg: Int) {
        p.textAlign = Paint.Align.CENTER
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(4f); p.color = color(R.color.map_bg)
        c.drawText(s, x, y, p)
        p.style = Paint.Style.FILL; p.color = fg
        c.drawText(s, x, y, p)
    }

    protected fun box(x: Float, y: Float, r: Float) = Box(x - r, y - r, x + r, y + r)

    /** 이름표를 at 후보 가운데 겹치지 않는 첫 자리에 그린다(가운데 x, 글자 기준선 y). gap은 이름표 양옆 여유 */
    protected fun place(c: Canvas, s: String, at: List<Pair<Float, Float>>, p: Paint, fg: Int, gap: Float = dp(3f)) {
        val half = p.measureText(s) / 2 + gap
        val boxes = at.map { (x, y) -> Box(x - half, y + p.ascent(), x + half, y + p.descent()) }
        val i = firstFree(boxes, taken, width.toFloat(), height.toFloat())
        taken += boxes[i]
        label(c, s, at[i].first, at[i].second, p, fg)
    }

    /** 점(x, y) 둘레 위·아래·오른쪽·왼쪽 */
    protected fun around(s: String, x: Float, y: Float, up: Float, p: Paint): List<Pair<Float, Float>> {
        val side = p.measureText(s) / 2 + dp(16f)
        return listOf(x to y - up, x to y + up + dp(14f), x + side to y + dp(6f), x - side to y + dp(6f))
    }

    /** around에 네 대각선과 한 칸 더 먼 위·아래를 더한 후보(4차 contracts/ui.md §2). 두 사람이 가까워도 이름표가 겹치지 않게 */
    protected fun aroundMore(s: String, x: Float, y: Float, up: Float, p: Paint): List<Pair<Float, Float>> {
        val side = p.measureText(s) / 2 + dp(16f)
        return around(s, x, y, up, p) + listOf(x + side to y - up, x - side to y - up, x + side to y + up + dp(14f),
            x - side to y + up + dp(14f), x to y - up - dp(22f), x to y + up + dp(36f))
    }

    /** 선을 따라 10dp마다 반지름 r 칸을 예약해 이름표가 선 위에 놓이지 않게 한다(4차 contracts/ui.md §2) */
    protected fun reserveLine(a: Pair<Float, Float>, b: Pair<Float, Float>, r: Float) {
        val n = maxOf(1, (hypot(b.first - a.first, b.second - a.second) / dp(10f)).toInt())
        for (i in 0..n) {
            val t = i.toFloat() / n
            taken += box(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t, r)
        }
    }

    protected fun arrowHead(c: Canvas, x: Float, y: Float, dx: Float, dy: Float, size: Float, col: Int) {
        val path = Path().apply {
            moveTo(x + dx * size, y + dy * size)
            lineTo(x - dy * size * 0.7f, y + dx * size * 0.7f)
            lineTo(x + dy * size * 0.7f, y - dx * size * 0.7f)
            close()
        }
        fill.color = col
        c.drawPath(path, fill)
    }

    /** 둥글게 자르고 바탕을 칠하고 칸을 비운다. 맨 끝 corners()가 자르기를 되돌린다 */
    protected fun begin(c: Canvas) {
        clip.reset()
        clip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), dp(16f), dp(16f), Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        c.drawColor(color(R.color.map_bg))
        taken.clear()
    }

    /** 길: 테두리 → 안쪽. 끝나면 stroke 굵기는 roadW다 */
    protected fun roads(c: Canvas, paths: List<Path>, roadW: Float) {
        stroke.pathEffect = null
        stroke.color = color(R.color.road_edge); stroke.strokeWidth = roadW + dp(3f)
        paths.forEach { c.drawPath(it, stroke) }
        stroke.color = color(R.color.road); stroke.strokeWidth = roadW
        paths.forEach { c.drawPath(it, stroke) }
    }

    /** 3차 핀. 꼭지가 (x, y)다 */
    protected fun pin(c: Canvas, x: Float, y: Float) {
        fill.color = color(R.color.blue)
        c.drawPath(Path().apply {
            moveTo(x - dp(8f), y - dp(18f)); lineTo(x + dp(8f), y - dp(18f)); lineTo(x, y); close()
        }, fill)
        c.drawCircle(x, y - dp(24f), dp(12f), fill)
        fill.color = color(R.color.on_accent)
        c.drawCircle(x, y - dp(24f), dp(4.5f), fill)
        taken += Box(x - dp(12f), y - dp(36f), x + dp(12f), y)
    }

    /** 북쪽·축척·출처 칸과(name이 있으면) 지도 이름표 칸. 이름표를 놓기 전에 부른다 */
    protected fun reserveCorners(w: Float, h: Float, barPx: Float, name: String?) {
        val nx = w - dp(26f)
        taken += Box(nx - dp(14f), dp(6f), nx + dp(14f), dp(50f))
        taken += Box(dp(10f), h - dp(42f), dp(24f) + maxOf(barPx, dp(40f)), h - dp(6f))
        taken += Box(w - dp(120f), h - dp(22f), w, h)
        plate = name?.let {
            namePaint.textSize = sp(13f)
            Box(dp(8f), dp(8f), dp(20f) + namePaint.measureText(it), dp(34f)).also { b -> taken += b }
        }
    }

    /** 지도 이름표("사람과 이동 경로" 등): 왼쪽 위, 카드색 85% 바탕(contracts/ui.md §2). 그 자리에 핀·점·선이 왔으면 그것을 가리지 않게 그리지 않는다 */
    protected fun mapName(c: Canvas, s: String) {
        val b = plate ?: return
        if (taken.any { it !== b && it.hits(b) }) return
        namePaint.textSize = sp(13f)
        val tw = namePaint.measureText(s)
        fill.color = color(R.color.card); fill.alpha = 217
        c.drawRoundRect(dp(8f), dp(8f), dp(20f) + tw, dp(34f), dp(6f), dp(6f), fill)
        fill.alpha = 255
        namePaint.textAlign = Paint.Align.LEFT; namePaint.color = color(R.color.text2)
        c.drawText(s, dp(14f), dp(26f), namePaint)
    }

    /** 북쪽, 축척 막대와 글, 출처를 그리고 자르기를 되돌린다(맨 끝에 부른다) */
    protected fun corners(c: Canvas, barM: Int, k: Float, w: Float, h: Float) {
        val nx = w - dp(26f)
        arrowHead(c, nx, dp(18f), 0f, -1f, dp(9f), color(R.color.text2))
        bold.textSize = sp(19f)
        label(c, "N", nx, dp(44f), bold, color(R.color.text2))
        stroke.pathEffect = null
        stroke.color = color(R.color.text2); stroke.strokeWidth = dp(3f)
        c.drawLine(dp(16f), h - dp(14f), dp(16f) + barM * k, h - dp(14f), stroke)
        small.textAlign = Paint.Align.LEFT
        small.textSize = sp(15f)
        small.style = Paint.Style.FILL; small.color = color(R.color.text2)
        c.drawText(scaleLabel(barM), dp(16f), h - dp(22f), small)
        small.textAlign = Paint.Align.RIGHT
        small.textSize = sp(12f); small.color = color(R.color.text3)
        c.drawText("© OpenStreetMap", w - dp(10f), h - dp(8f), small)
        c.restore()
    }
}

/**
 * 가는 중 지도(3차 contracts/ui.md §3, research R24, 4차에서 그림 그대로). 지점을 가운데 두고 짧은 변의 반이 halfM(m)이다.
 * 화면을 다시 붙일지는 tag(지점·사람·차를 5m 단위로)로 정한다
 */
class SpotMap(context: Context, area: Area, private val spot: Spot, private val people: List<MapPerson>,
              private val car: LatLon?, private val halfM: Double, heightDp: Float) : MapBase(context, area, heightDp) {

    init {
        tag = "map ${spot.landmark.id} ${people.joinToString(";") { "${it.name}@${r5(spot.pos, it.pos)}${it.line.orEmpty()}" }} car@${r5(spot.pos, car)}"
        contentDescription = "${spot.line} 둘레 지도"
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        begin(c)
        val k = (minOf(w, h) / 2) / halfM.toFloat() // px / m
        val o = spot.pos
        fun px(p: LatLon): Pair<Float, Float> = xy(o, p).let { w / 2 + it.x.toFloat() * k to h / 2 - it.y.toFloat() * k }
        // 길: 테두리 → 안쪽 → 지점 길
        val roadW = 10f * k
        roads(c, area.roads.map { path(it.points, ::px) }, roadW)
        stroke.color = color(R.color.blue_soft)
        c.drawPath(path(spot.road, ::px), stroke)

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
            stroke.pathEffect = walkDash
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
        pin(c, sx, sy)
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
        reserveCorners(w, h, 50f * k, null)
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
        corners(c, 50, k, w, h)
    }
}

/** 위치를 5m 단위로 줄인 글(화면을 다시 붙일지 정하는 tag용) */
private fun r5(o: LatLon, p: LatLon?) = p?.let { xy(o, it) }?.let { "${(it.x / 5).roundToInt()},${(it.y / 5).roundToInt()}" }

/**
 * 4차 "사람과 이동 경로"(contracts/ui.md §2). 그릴 사람·지점·역이 모두 들어오게 맞추고(fit),
 * 사람마다 색으로 걷기 점선, 노선 색으로 탐 실선과 역을 그린다
 */
class PeopleMap(context: Context, area: Area, private val spot: Spot, people: List<MapPerson>, heightDp: Float) : MapBase(context, area, heightDp) {
    private val shown = people.filter { it.pos != null }

    init {
        tag = "people ${spot.landmark.id} ${shown.joinToString(";") { "${it.name}@${r5(spot.pos, it.pos)}${it.leg?.id.orEmpty()}#${it.slot}" }}"
        contentDescription = "사람과 이동 경로: ${shown.joinToString(", ") { "${it.name} ${it.leg?.line ?: "걸어서"}" }} · ${spot.line}"
    }

    /** 노선 글 후보: 선 가운데에서 선과 수직인 양쪽, 글 상자가 선에 닿지 않는 거리(contracts/ui.md §2) */
    private fun beside(s: String, a: Pair<Float, Float>, b: Pair<Float, Float>, p: Paint): List<Pair<Float, Float>> {
        val mx = (a.first + b.first) / 2
        val my = (a.second + b.second) / 2
        val len = hypot(b.first - a.first, b.second - a.second).takeIf { it > 0f } ?: 1f
        val nx = -(b.second - a.second) / len
        val ny = (b.first - a.first) / len
        val t = (p.measureText(s) / 2 + dp(3f)) * abs(nx) + (p.descent() - p.ascent()) / 2 * abs(ny) + dp(6f)
        val base = (p.ascent() + p.descent()) / 2 // 글 가운데 → 기준선
        return listOf(mx + nx * t to my + ny * t - base, mx - nx * t to my - ny * t - base)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        begin(c)
        val o = spot.pos
        val legs = shown.mapNotNull { it.leg }.distinctBy { it.id }
        val f = fit(listOf(XY(0.0, 0.0)) + shown.map { xy(o, it.pos!!) } + legs.flatMap { listOf(xy(o, it.board.pos), xy(o, it.alight.pos)) },
            w, h, dp(40f))
        val k = f.k.toFloat()
        fun px(p: LatLon) = xy(o, p).let { w / 2 + ((it.x - f.cx) * f.k).toFloat() to h / 2 - ((it.y - f.cy) * f.k).toFloat() }
        val bar = scaleBar(f.k, w)
        reserveCorners(w, h, bar * k, NAME)
        roads(c, area.roads.map { path(it.points, ::px) }, maxOf(10f * k, dp(2.5f))) // 넓은 범위에서도 길이 사라지지 않게(R29)
        stroke.color = color(R.color.blue_soft)
        c.drawPath(path(spot.road, ::px), stroke)
        val (sx, sy) = px(spot.pos)
        // 걷기 점선(사람 색)과 탐 실선(노선 색, 같은 구간은 한 번)
        for (m in shown) for (seg in route(m.pos!!, m.leg, spot.pos)) if (seg.leg == null) {
            val a = px(seg.from)
            val b = px(seg.to)
            stroke.pathEffect = walkDash
            stroke.color = color(AVATAR_LINES[m.slot]); stroke.strokeWidth = dp(3f)
            c.drawLine(a.first, a.second, b.first, b.second, stroke)
            stroke.pathEffect = null
            reserveLine(a, b, dp(4f))
        }
        val line1 = color(R.color.line1)
        for (l in legs) {
            val a = px(l.board.pos)
            val b = px(l.alight.pos)
            stroke.color = line1; stroke.strokeWidth = dp(6f)
            c.drawLine(a.first, a.second, b.first, b.second, stroke)
            reserveLine(a, b, dp(5f))
        }
        val stations = legs.flatMap { listOf(it.board, it.alight) }.distinctBy { it.name }.map { st ->
            val (x, y) = px(st.pos)
            fill.color = color(R.color.card)
            c.drawRoundRect(x - dp(7f), y - dp(7f), x + dp(7f), y + dp(7f), dp(4f), dp(4f), fill)
            stroke.color = line1; stroke.strokeWidth = dp(3f)
            c.drawRoundRect(x - dp(7f), y - dp(7f), x + dp(7f), y + dp(7f), dp(4f), dp(4f), stroke)
            taken += box(x, y, dp(9f))
            Triple(st.name, x, y)
        }
        pin(c, sx, sy)
        val dots = shown.map { m ->
            val (x, y) = px(m.pos!!)
            fill.color = color(R.color.on_accent); c.drawCircle(x, y, dp(10f), fill)
            fill.color = color(AVATAR_LINES[m.slot]); c.drawCircle(x, y, dp(7.5f), fill)
            taken += box(x, y, dp(10f))
            Triple(m, x, y)
        }
        // 이름표: 지점 → 사람 → 역 → 노선 글
        bold.textSize = sp(17f)
        val side = bold.measureText(spot.line) / 2 + dp(18f)
        place(c, spot.line, listOf(sx to sy - dp(44f), sx to sy + dp(30f), sx + side to sy - dp(18f), sx - side to sy - dp(18f)),
            bold, color(R.color.text1), labelGap)
        for ((m, x, y) in dots) place(c, m.name, aroundMore(m.name, x, y, dp(16f), bold), bold, color(AVATAR_LINES[m.slot]), labelGap)
        small.textSize = sp(14f)
        for ((name, x, y) in stations) place(c, name, aroundMore(name, x, y, dp(14f), small), small, color(R.color.text2), labelGap)
        bold.textSize = sp(15f)
        for (l in legs) {
            val t = "${l.line} ${l.stops}정거장"
            place(c, t, beside(t, px(l.board.pos), px(l.alight.pos), bold), bold, line1, labelGap)
        }
        mapName(c, NAME)
        corners(c, bar, k, w, h)
    }

    private companion object { const val NAME = "사람과 이동 경로" }
}

/**
 * 4차 "차 세울 곳"(contracts/ui.md §3). 지점을 길 선에 투영한 점을 가운데 두고 짧은 변의 반이 35m다.
 * 세울 자리 띠, 들어오는 화살표, 핀과 "여기 세워요", 들어오는 방향 오른쪽 인도의 서 있을 곳
 */
class StopMap(context: Context, area: Area, private val spot: Spot, people: List<MapPerson>, heightDp: Float) : MapBase(context, area, heightDp) {
    private val shown = people.filter { it.pos != null }
    private val g = stopGeom(area, spot, shown.size)

    init {
        tag = "stop ${spot.landmark.id} ${shown.joinToString(",") { "${it.slot}" }}"
        contentDescription = "차 세울 곳: ${spot.line}, 들어오는 방향 오른쪽 길가"
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        begin(c)
        val k = (minOf(w, h) / 2) / 35f
        val o = spot.pos
        fun at(q: XY) = w / 2 + ((q.x - g.r.x) * k).toFloat() to h / 2 - ((q.y - g.r.y) * k).toFloat()
        fun px(p: LatLon) = at(xy(o, p))
        val bar = scaleBar(k.toDouble(), w)
        reserveCorners(w, h, bar * k, NAME)
        lanes(c, k, ::at)
        val d = headingVec(spot.heading)
        val e = rightOf(spot.heading)
        val blue = color(R.color.blue)
        // 세울 자리 띠: 진행 방향 18m × 3m
        val band = Path().apply {
            listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).forEachIndexed { i, (sd, se) ->
                at(g.band + d * (sd * 9.0) + e * (se * 1.5)).let { (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
            }
            close()
        }
        fill.color = color(R.color.blue_soft)
        c.drawPath(band, fill)
        stroke.color = blue; stroke.strokeWidth = dp(2f); stroke.pathEffect = bandDash
        c.drawPath(band, stroke)
        stroke.pathEffect = null
        // 들어오는 방향: 띠 뒤 끝보다 30m 뒤에서 띠 뒤 끝까지
        val a = at(g.arrowFrom)
        val b = at(g.arrowTo)
        val len = hypot(b.first - a.first, b.second - a.second)
        val ux = (b.first - a.first) / len
        val uy = (b.second - a.second) / len
        stroke.color = color(R.color.on_accent); stroke.strokeWidth = dp(9f) // 아스팔트 위에서 보이게 흰 테두리
        c.drawLine(a.first, a.second, b.first - ux * dp(8f), b.second - uy * dp(8f), stroke)
        arrowHead(c, b.first - ux * dp(9f), b.second - uy * dp(9f), ux, uy, dp(15f), color(R.color.on_accent))
        stroke.color = blue; stroke.strokeWidth = dp(5f)
        c.drawLine(a.first, a.second, b.first - ux * dp(8f), b.second - uy * dp(8f), stroke)
        arrowHead(c, b.first - ux * dp(8f), b.second - uy * dp(8f), ux, uy, dp(11f), blue)
        reserveLine(a, b, dp(6f))
        val (bx, by) = at(g.band)
        pin(c, bx, by)
        // 서 있을 곳(사람 색)
        val stand = shown.zip(g.stand).map { (m, q) ->
            val (x, y) = at(q)
            fill.color = color(R.color.on_accent); c.drawCircle(x, y, dp(8f), fill)
            fill.color = color(AVATAR_LINES[m.slot]); c.drawCircle(x, y, dp(6f), fill)
            taken += box(x, y, dp(8f))
            x to y
        }
        // 기준 랜드마크(범위 안일 때)
        val passing = spot.passing?.let { l -> px(l.pos).takeIf { (x, y) -> x in 0f..w && y in 0f..h }?.let { (x, y) ->
            fill.color = color(R.color.text3)
            c.drawCircle(x, y, dp(5f), fill)
            taken += box(x, y, dp(5f))
            Triple(l.name, x, y)
        } }
        // 이름표 차례(사용자 결정): 여기 세워요 → 지점 이름 → 기준 랜드마크
        bold.textSize = sp(17f)
        place(c, STOP, around(STOP, bx, by, dp(44f), bold), bold, blue, labelGap) // 핀 둘레: 위 → 아래 → 오른쪽 → 왼쪽(contracts/ui.md §3)
        bold.textSize = sp(15f)
        val name = spot.landmark.name
        val (gx, gy) = if (stand.isEmpty()) at(g.r + e * 8.0)
        else stand.map { it.first }.average().toFloat() to stand.map { it.second }.average().toFloat()
        val half = bold.measureText(name) / 2 + dp(16f)
        place(c, name, listOf(gx + e.x.toFloat() * half to gy - e.y.toFloat() * dp(20f) + dp(6f), gx to gy - dp(28f), gx to gy + dp(34f)),
            bold, color(R.color.text1), labelGap)
        small.textSize = sp(14f)
        passing?.let { (n, x, y) -> place(c, n, around(n, x, y, dp(10f), small), small, color(R.color.text3), labelGap) }
        mapName(c, NAME)
        corners(c, bar, k, w, h)
    }

    /**
     * 4차 FR-085: 길마다 아스팔트(차선 수 × LANE_M), 오른쪽 가장자리 흰 실선, 왼쪽(중앙선 쪽) 노란 실선, 차선 사이 흰 점선,
     * 지역 데이터의 횡단보도(정차 금지 점) 줄무늬. 지역 길은 모두 한 방향 길이다
     */
    private fun lanes(c: Canvas, k: Float, at: (XY) -> Pair<Float, Float>) {
        val o = spot.pos
        fun line(pts: List<XY>) = Path().apply { pts.forEachIndexed { i, q -> at(q).let { (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) } } }
        for (rd in area.roads) {
            // ponytail: 양방향 길이 지역에 생기면 가운데 노란 선을 따로 그린다
            val pts = rd.points.map { xy(o, it) }
            val half = rd.lanes * LANE_M / 2
            stroke.pathEffect = null
            stroke.color = color(R.color.road_edge); stroke.strokeWidth = (2 * half * k).toFloat() + dp(3f)
            c.drawPath(line(pts), stroke)
            stroke.color = color(R.color.asphalt); stroke.strokeWidth = (2 * half * k).toFloat()
            c.drawPath(line(pts), stroke)
            stroke.strokeWidth = dp(2f)
            stroke.color = color(R.color.lane_white); c.drawPath(line(offsetLine(pts, -(half - 0.3))), stroke)
            stroke.color = color(R.color.lane_yellow); c.drawPath(line(offsetLine(pts, half - 0.3)), stroke)
            stroke.color = color(R.color.lane_white); stroke.pathEffect = laneDash
            for (i in 1 until rd.lanes) c.drawPath(line(offsetLine(pts, -half + i * LANE_M)), stroke)
            stroke.pathEffect = null
        }
        fill.color = color(R.color.lane_white)
        for (ns in area.noStops) if (ns.kind == NoStopKind.CROSSWALK) {
            val rd = area.roads.minBy { project(area.center, it, ns.pos).distance }
            val pr = project(area.center, rd, ns.pos)
            if (pr.distance > 5.0) continue
            val c0 = xy(o, pointAlong(area.center, rd, pr.along))
            val d = headingVec(pr.dirDeg)
            val n = rightOf(pr.dirDeg)
            val half = rd.lanes * LANE_M / 2
            var t = -half + 0.3
            while (t + 0.45 <= half - 0.3) { // 길 방향 4m × 폭 0.45m 줄을 0.9m마다
                c.drawPath(line(listOf(c0 + n * t - d * 2.0, c0 + n * t + d * 2.0, c0 + n * (t + 0.45) + d * 2.0, c0 + n * (t + 0.45) - d * 2.0)).apply { close() }, fill)
                t += 0.9
            }
        }
    }

    private companion object {
        const val NAME = "차 세울 곳"
        const val STOP = "여기 세워요"
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
