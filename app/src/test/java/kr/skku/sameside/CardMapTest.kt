package kr.skku.sameside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4차 카드 두 지도의 순수 계산(specs/004-maps-transit-video data-model §2~§8). GroupTest.kt에 MapTest가 있어 이름을 나눴다 */
class CardMapTest {
    @Test fun scaleBarIsClosestToAQuarterOfTheWidth() {
        assertEquals(100, scaleBar(1.0, 400f))
        assertEquals(1000, scaleBar(0.1, 400f))
        assertEquals(20, scaleBar(4.0, 330f))
    }

    @Test fun scaleLabels() {
        assertEquals("50m", scaleLabel(50))
        assertEquals("1km", scaleLabel(1000))
        assertEquals("2km", scaleLabel(2000))
    }

    /** data-model §3: 모든 점이 여백 안, 짧은 변은 100m 아래로 좁아지지 않는다 */
    @Test fun fitKeepsEveryPointInsideThePadding() {
        val f = fit(listOf(XY(0.0, 0.0), XY(600.0, -300.0)), 400f, 200f, 20f)
        for (p in listOf(XY(0.0, 0.0), XY(600.0, -300.0))) {
            val x = 200 + (p.x - f.cx) * f.k
            val y = 100 - (p.y - f.cy) * f.k
            assertTrue("$p → $x,$y", x in 19.999..380.001 && y in 19.999..180.001)
        }
        assertEquals(100.0, 200 / fit(listOf(XY(0.0, 0.0)), 400f, 200f, 20f).k, 1e-9)
        assertTrue(200 / fit(listOf(XY(0.0, 0.0), XY(10.0, 0.0)), 400f, 200f, 20f).k >= 100.0 - 1e-9)
    }

    /** data-model §2: 걷기는 한 구간, 대중교통은 걷기 → 탐 → 걷기로 이어진다 */
    @Test fun routeSegments() {
        val pos = LatLon(37.2991996, 126.9727588)
        val spot = LatLon(37.2846337, 126.9900286)
        val walk = route(pos, null, spot)
        assertEquals(listOf(Seg(pos, spot, null)), walk)
        val leg = DEMO.transit[0]
        val ride = route(pos, leg, spot)
        assertEquals(3, ride.size)
        assertEquals(pos, ride[0].from)
        assertEquals(ride[0].to, ride[1].from)
        assertEquals(ride[1].to, ride[2].from)
        assertEquals(spot, ride[2].to)
        assertEquals(listOf(null, leg, null), ride.map { it.leg })
    }

    /** data-model §4: 첫 글자 원과 같은 번호, 겹치면 다음 칸부터 안 쓴 번호 */
    @Test fun colorSlotsFollowTheAvatarAndAvoidRepeats() {
        assertEquals(listOf(2, 1, 3, 4, 0), colorSlots(listOf("민지", "준호", "서연", "할머니", "도윤")))
        val a = Math.floorMod("민지".hashCode(), 5)
        assertEquals(listOf(a, (a + 1) % 5), colorSlots(listOf("민지", "민지")))
        assertEquals(listOf(2, 1, 3, 4, 0, 2), colorSlots(listOf("민지", "준호", "서연", "할머니", "도윤", "민지")))
    }

    @Test fun rightOfTheHeading() {
        rightOf(0.0).let { assertEquals(1.0, it.x, 1e-9); assertEquals(0.0, it.y, 1e-9) }
        rightOf(90.0).let { assertEquals(0.0, it.x, 1e-9); assertEquals(-1.0, it.y, 1e-9) }
    }

    /** data-model §5: 길 폭은 차선 수 × LANE_M. 띠는 길가 안쪽 1.5m, 서 있을 곳은 길가 밖 3m, 여럿이면 진행 방향으로 4m 간격 */
    @Test fun stopGeometyOnTheRightCurb() {
        val seven = DEMO.landmarks.first { it.id == "seven" }
        val spot = Pick.side(DEMO, LatLon(37.2935193, 126.9700141), seven)!!
        val e = rightOf(spot.heading)
        fun along(a: XY, b: XY) = (a.x - b.x) * e.x + (a.y - b.y) * e.y
        val half = DEMO.road("seobu_n").lanes * LANE_M / 2 // 서부로 3차로
        val g1 = stopGeom(DEMO, spot, 1)
        assertEquals(half - 1.5, along(g1.band, g1.r), 1e-6)
        assertEquals(half + 3.0, along(g1.stand[0], g1.r), 1e-6)
        assertEquals(half - LANE_M / 2, along(g1.arrowTo, g1.r), 1e-6)
        val g2 = stopGeom(DEMO, spot, 2)
        assertEquals(4.0, (g2.stand[0] - g2.stand[1]).len, 1e-6)
        assertEquals(30.0, (g2.arrowTo - g2.arrowFrom).len, 1e-6)
    }

    /** data-model §5: 차선 선은 길 선을 왼쪽(+)·오른쪽(−)으로 나란히 옮긴다 */
    @Test fun offsetLineShiftsSideways() {
        val east = listOf(XY(0.0, 0.0), XY(10.0, 0.0))
        assertEquals(listOf(XY(0.0, 2.0), XY(10.0, 2.0)), offsetLine(east, 2.0)) // 동쪽으로 가면 왼쪽은 북쪽
        assertEquals(listOf(XY(0.0, -2.0), XY(10.0, -2.0)), offsetLine(east, -2.0))
        val bend = offsetLine(listOf(XY(0.0, 0.0), XY(10.0, 0.0), XY(10.0, 10.0)), -1.0) // 꺾이는 꼭짓점은 두 법선의 평균 방향
        assertEquals(10.0 + Math.sqrt(0.5), bend[1].x, 1e-9)
        assertEquals(-Math.sqrt(0.5), bend[1].y, 1e-9)
    }

    /** data-model §8 사람 줄 */
    @Test fun howLines() {
        assertEquals("민지 걸어서 3분", Pick.howLine("민지", null, 170.0))
        assertEquals("준호 1호선 1정거장(성균관대역 → 화서역) · 12분", Pick.howLine("준호", DEMO.transit[0], 700.0))
    }
}

/** 4차 대중교통 강조 카드와 거절 기록(data-model §7·§8, FR-077·078). 좌표는 시연 transit 장면 */
class TransitNoteTest {
    private val car = LatLon(37.2570158, 127.0198273)
    private val passenger = LatLon(37.2991996, 126.9727588)
    private fun offer() = Pick.transit(DEMO, car, passenger, false, Pick.choose(DEMO, car, passenger, false).first())!!

    @Test fun offerCarriesTheArrivalTime() {
        val o = offer()
        val leg = o.leg
        val expect = dist(DEMO.center, passenger, leg.board.pos) * Rules.DETOUR / Rules.WALK_M_PER_MIN * 60.0 + leg.waitSec + leg.rideSec + o.spot.walkSec
        assertEquals(expect, o.arriveSec, 0.01)
    }

    @Test fun noteBeforeAndAfterAccepting() {
        val p = Synth.minji()
        val o = offer()
        p.lead.offer = o
        assertEquals("민지님께 1호선 안을 보냈어요" to "성균관대역 → 화서역 1정거장 · ${o.spot.line}에서 만나면 ${o.gainMin}분 빨라요", Pick.transitNote(p))
        p.lead.transitAccepted = true
        assertEquals("민지님이 1호선 타고 화서역으로 와요" to "성균관대역 → 화서역 1정거장", Pick.transitNote(p))
    }

    @Test fun noNoteWithoutOfferInGroupsOrAfterDeclining() {
        val p = Synth.minji()
        assertNull(Pick.transitNote(p))
        p.lead.offer = offer()
        p.lead.transitDeclined = true
        assertNull(Pick.transitNote(p))
        val g = Group.pickup()
        g.members[0].offer = offer()
        assertNull(Pick.transitNote(g))
    }

    /** 거절("그냥 걸어갈게요")은 기록만 한다. 지점·음성·내비는 그대로이고, 뒤늦은 받아들임은 받지 않는다 */
    @Test fun decliningIsRecordedAndBlocksALateAccept() {
        val p = Synth.minji()
        Pick.step(DEMO, p, Event.Gear(true), 0)
        Pick.step(DEMO, p, Event.CarPos(car), 0)
        Pick.step(DEMO, p, remoteOf(passenger, status = "link"), 0)
        assertEquals(Status.SPOTS, p.status)
        assertNotNull(p.lead.offer)
        val spots = p.spots.map { it.landmark.id }
        val acts = Pick.step(DEMO, p, remoteOf(passenger, transit = "decline", status = "spots"), 1_000)
        assertTrue(p.lead.transitDeclined)
        assertFalse(p.lead.transitAccepted)
        assertEquals(spots, p.spots.map { it.landmark.id })
        assertTrue(acts.none { it is Action.Speak || it is Action.SetNavi })
        Pick.step(DEMO, p, remoteOf(passenger, transit = "accept", status = "spots"), 2_000)
        assertFalse(p.lead.transitAccepted)
        assertEquals(spots, p.spots.map { it.landmark.id })
    }
}
