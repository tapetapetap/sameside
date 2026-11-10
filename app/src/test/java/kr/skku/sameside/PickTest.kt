package kr.skku.sameside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/** data-model §3 기하. 원점은 직접 넘긴다 */
class GeoTest {
    private val o = LatLon(37.3, 126.97)

    @Test fun distanceMatchesMetersPerDegree() {
        val north = dist(o, o, LatLon(37.301, 126.97))
        val east = dist(o, o, LatLon(37.3, 126.971))
        assertEquals(110.54, north, 110.54 * 0.01)
        val eastExpected = cos(Math.toRadians(37.3)) * 111.32
        assertEquals(eastExpected, east, eastExpected * 0.01)
    }

    @Test fun bearingNorthZeroEastNinety() {
        assertEquals(0.0, bearing(o, o, LatLon(37.301, 126.97)), 0.5)
        assertEquals(90.0, bearing(o, o, LatLon(37.3, 126.971)), 0.5)
        assertEquals(180.0, bearing(o, o, LatLon(37.299, 126.97)), 0.5)
    }

    @Test fun angleDiffWrapsAround() {
        assertEquals(20.0, angleDiff(350.0, 10.0), 1e-9)
        assertEquals(180.0, angleDiff(0.0, 180.0), 1e-9)
        assertEquals(90.0, angleDiff(45.0, 315.0), 1e-9)
    }

    @Test fun offsetIsNegativeOnTheRightOfTheRoad() {
        val road = Road("r", "북쪽 길", listOf(o, LatLon(37.302, 126.97)), oneway = false)
        val east = project(o, road, LatLon(37.301, 126.9701)) // 동쪽 약 9m
        val west = project(o, road, LatLon(37.301, 126.9699))
        assertTrue(east.offset < 0)
        assertTrue(west.offset > 0)
        assertEquals(8.86, -east.offset, 0.2)
    }
}

/** data-model §1 */
class PeopleTest {
    @Test fun roundTrip() {
        val list = listOf(Person("민지", "01012345678", false), Person("할머니", "0311234567", true))
        assertEquals(list, People.decode(People.encode(list)))
    }

    @Test fun brokenLinesAreSkipped() {
        val text = "민지\t01012345678\t0\n깨진 줄\n\t0101\t1\n지수\t01099998888\t2\n할머니\t0311234567\t1"
        assertEquals(listOf("민지", "할머니"), People.decode(text).map { it.name })
    }

    @Test fun nameAndPhoneValidation() {
        assertEquals("민지", People.name("  민\t지 \n"))
        assertNull(People.name("   "))
        assertNull(People.name("열한글자가넘는이름입니다"))
        assertEquals("01012345678", People.phone("010-1234-5678"))
        assertNull(People.phone("1234"))
        assertNull(People.phone("010123456789"))
    }

    @Test fun carValidation() {
        val car = People.car(" 그랜저 ", "흰\t색", "12가 3456", "")
        assertEquals(Car("그랜저", "흰색", "12가 3456", ""), car)
        assertEquals("01098765432", People.car("", "", "", "010 9876 5432")!!.phone)
        assertNull(People.car("", "", "", "12"))
        assertNull(People.car("스물한글자가넘는아주아주긴차종이름입니다요", "", "", "")) // 21자
        assertEquals(car, People.decodeCar(People.encodeCar(car!!)))
    }
}

/** scripts/scenes.txt(data-model §9). `#` 뒤는 버린다. Gradle 테스트 작업 디렉터리(app/) 기준 */
object Scenes {
    private val lines = java.io.File("../scripts/scenes.txt").readLines()
        .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.map { it.split(Regex("\\s+")) }

    fun values(scene: String, key: String) = lines.filter { it[0] == scene && it[1] == key }.map { it.drop(2) }
    fun point(scene: String, key: String) = values(scene, key).first().let { LatLon(it[0].toDouble(), it[1].toDouble()) }
    fun route(scene: String) = values(scene, "route").map { LatLon(it[0].toDouble(), it[1].toDouble()) }
    fun text(scene: String, key: String) = values(scene, key).first()[0]
}

/** 시연 지역 DEMO 점검(tasks T009, data-model §2) */
class AreaTest {
    @Test fun everyLandmarkHasItsRoadAndRoadsHavePoints() {
        DEMO.landmarks.forEach { DEMO.road(it.roadId) }
        assertTrue(DEMO.roads.all { it.points.size >= 2 })
    }

    @Test fun bothCurbsOfSeoburoHaveLandmarks() {
        // 서부로는 상행·하행이 나뉜 길이라 두 일방 길로 둔다. 각 줄의 오른쪽 길가가 서로 반대편이다
        assertTrue(DEMO.landmarks.any { it.roadId == "seobu_n" })
        assertTrue(DEMO.landmarks.any { it.roadId == "seobu_s" })
    }

    @Test fun someLandmarkIsFilteredByABusStop() {
        val o = DEMO.center
        assertTrue(DEMO.landmarks.any { l -> DEMO.noStops.any { it.kind == NoStopKind.BUS && dist(o, l.pos, it.pos) < 10 } })
    }

    @Test fun scenesFileParses() {
        assertTrue(Scenes.route("opposite").size >= 2)
        assertEquals("seven", Scenes.text("opposite", "expect"))
    }
}

/** 합성 지역: 원점 기준 m 좌표로 만든다. 남북 양방향 길 r(x=0)과 북쪽 일방 길 o(x=100) */
object Synth {
    val o = LatLon(37.3, 126.97)
    fun at(x: Double, y: Double) = ll(o, XY(x, y))
    fun area(landmarks: List<Landmark>, noStops: List<NoStop> = emptyList(), transit: List<TransitLeg> = emptyList()) = Area(
        o,
        listOf(Road("r", "남북 길", listOf(at(0.0, -3000.0), at(0.0, 3000.0)), oneway = false),
            Road("o", "일방 길", listOf(at(100.0, -3000.0), at(100.0, 3000.0)), oneway = true)),
        noStops, landmarks, transit,
    )
    val east = Landmark("east", "동쪽 가게", at(6.0, 0.0), "r")
    val west = Landmark("west", "서쪽 가게", at(-6.0, 0.0), "r")
    val vague = Landmark("vague", "가운데", at(2.0, 20.0), "r")
    val busy = Landmark("busy", "정류장 옆", at(6.0, 50.0), "r")
    val oneLeft = Landmark("oneLeft", "일방 왼쪽", at(94.0, 0.0), "o")
    val oneRight = Landmark("oneRight", "일방 오른쪽", at(106.0, 0.0), "o")
    val all = area(listOf(east, west, vague, busy, oneLeft, oneRight), listOf(NoStop(NoStopKind.BUS, at(6.0, 55.0))))
    fun minji() = Pickup("id", "key", Person("민지", "01012345678", false), 0L)

    /** 차는 남쪽 2km에서 북쪽으로 온다(기어 D). 지점은 동쪽 가게(경유지 (0,-100)) */
    fun going(passenger: XY = XY(-10.0, 0.0)): Pickup {
        val p = minji()
        Pick.step(all, p, Event.Gear(true), 0)
        Pick.step(all, p, Event.CarPos(at(0.0, -2000.0)), 0)
        Pick.step(all, p, remoteOf(ll(o, passenger), status = "link"), 0)
        Pick.step(all, p, Event.Confirm(0), 0)
        Pick.step(all, p, Event.Gear(false), 0)
        assertEquals("east", p.spot?.landmark?.id)
        return p
    }
}

/** data-model §4 지점 고르기(tasks T014) */
class ChooseTest {
    private val carSouth = Synth.at(0.0, -500.0)

    @Test fun keepsOnlySameSideStoppableCertainSpots() {
        val got = Pick.choose(Synth.all, carSouth, Synth.at(-10.0, 0.0), careful = false)
        assertEquals(listOf("east", "oneRight"), got.map { it.landmark.id }) // west: U턴, vague: 애매, busy: 정류장, oneLeft: 일방 왼쪽
        assertEquals(1, got[0].crossing)
        assertEquals("차가 오는 쪽 · 횡단보도 한 번 · 세워도 되는 곳", got[0].reason)
        assertEquals(0, got[1].crossing)
        assertEquals("차가 오는 쪽 · 길 안 건너도 됨 · 세워도 되는 곳", got[1].reason)
    }

    @Test fun carefulMeansOneHundredFifty() {
        assertEquals(listOf("east"), Pick.choose(Synth.all, carSouth, Synth.at(-10.0, 0.0), careful = true).map { it.landmark.id })
    }

    @Test fun uTurnRuleSkippedWhenCarIsAlmostThere() {
        val got = Pick.choose(Synth.all, Synth.at(0.0, -30.0), Synth.at(-10.0, 0.0), careful = false)
        assertTrue(got.any { it.landmark.id == "west" })
    }

    @Test fun waypointIsHundredMetersUpstreamOnTheRoad() {
        val s = Pick.choose(Synth.all, carSouth, Synth.at(-10.0, 0.0), careful = false).first()
        val o = Synth.o
        assertEquals(100.0, dist(o, s.waypoint, Synth.at(0.0, 0.0)), 1.0)
        assertTrue(xy(o, s.waypoint).y < 0) // 차가 오는 남쪽
        assertEquals(0.0, s.heading, 1.0)
    }

    @Test fun sameMinuteThenShorterWalk() {
        val a = Landmark("a", "가", Synth.at(6.0, 0.0), "r")
        val b = Landmark("b", "나", Synth.at(6.0, -30.0), "r") // 차에 30m 가깝지만 같은 분
        val got = Pick.choose(Synth.area(listOf(a, b)), Synth.at(0.0, -2000.0), Synth.at(20.0, 10.0), careful = false)
        assertEquals(listOf("a", "b"), got.map { it.landmark.id })
    }

    @Test fun noCandidateGivesEmptyList() {
        assertTrue(Pick.choose(Synth.all, carSouth, Synth.at(1000.0, 1000.0), careful = false).isEmpty())
    }

    @Test fun referenceOnlyLandmarkIsNeverASpot() {
        // 2차 T002: 올리브영은 기준 랜드마크로만 쓴다(data-model §1-1). 넣어도 1순위가 바뀌지 않는다
        for (scene in listOf("handsfree", "wrongside", "opposite", "transit")) {
            val spots = Pick.choose(DEMO, Scenes.point(scene, "car"), Scenes.point(scene, "passenger"), false)
            assertEquals(scene, "seven", spots.first().landmark.id)
        }
        for (scene in listOf("opposite", "handsfree", "wrongside", "transit", "transit-small", "reverse", "nospot")) {
            val spots = Pick.choose(DEMO, Scenes.point(scene, "car"), Scenes.point(scene, "passenger"), false)
            assertTrue(scene, spots.none { it.landmark.id == "oliveyoung" })
        }
        val ref = Landmark("ref", "기준", Synth.at(6.0, 0.0), "r", spot = false)
        assertTrue(Pick.choose(Synth.area(listOf(ref)), carSouth, Synth.at(-10.0, 0.0), false).isEmpty())
    }

    @Test fun oppositeSceneChoosesTheArrivalSide() {
        val got = Pick.choose(DEMO, Scenes.point("opposite", "car"), Scenes.point("opposite", "passenger"), careful = false)
        assertEquals(Scenes.text("opposite", "expect"), got.first().landmark.id)
        assertTrue(got.none { it.landmark.id == "skku1" || it.landmark.id == "skku2" }) // 반대편 출구는 내지 않는다
    }
}

/** data-model §7 시작부(tasks T014) */
class StepStartTest {
    private fun remote(consent: Boolean?, pos: LatLon?) = Event.Remote("link", consent, pos, false, false, false, null)

    @Test fun waitsForCarPositionThenChooses() {
        val p = Synth.minji()
        val passenger = Synth.at(-10.0, 0.0)
        assertTrue(Pick.step(Synth.all, p, remote(true, passenger), 1_000).isEmpty())
        assertEquals(Status.LINK, p.status) // 차 위치가 아직 없다
        val acts = Pick.step(Synth.all, p, Event.CarPos(Synth.at(0.0, -500.0)), 2_000)
        assertEquals(Status.SPOTS, p.status)
        assertEquals("east", p.spots.first().landmark.id)
        assertTrue(Action.Push in acts)
    }

    @Test fun declineStaysInLink() {
        val p = Synth.minji()
        Pick.step(Synth.all, p, Event.CarPos(Synth.at(0.0, -500.0)), 1_000)
        Pick.step(Synth.all, p, remote(false, null), 2_000)
        assertEquals(Status.LINK, p.status)
        assertEquals(false, p.passenger.consent)
    }

    @Test fun noSpotGoesToNospot() {
        val p = Synth.minji()
        Pick.step(Synth.all, p, Event.CarPos(Synth.at(0.0, -500.0)), 1_000)
        Pick.step(Synth.all, p, remote(true, Synth.at(1000.0, 1000.0)), 2_000)
        assertEquals(Status.NOSPOT, p.status)
    }

    @Test fun confirmOnlyInPark() {
        val p = Synth.minji()
        Pick.step(Synth.all, p, Event.CarPos(Synth.at(0.0, -500.0)), 1_000)
        Pick.step(Synth.all, p, remote(true, Synth.at(-10.0, 0.0)), 2_000)
        assertTrue(Pick.step(Synth.all, p, Event.Confirm(0), 3_000).isEmpty()) // 기어를 아직 모른다 = P 아님
        Pick.step(Synth.all, p, Event.Gear(park = false), 3_000)
        assertTrue(Pick.step(Synth.all, p, Event.Confirm(0), 3_000).isEmpty())
        Pick.step(Synth.all, p, Event.Gear(park = true), 4_000)
        val acts = Pick.step(Synth.all, p, Event.Confirm(0), 5_000)
        assertEquals(Status.GOING, p.status)
        assertEquals("east", p.spot?.landmark?.id)
        assertTrue(acts.any { it is Action.SetNavi } && Action.Push in acts)
        assertEquals(5_000 + (p.spot!!.carSec * 1000).toLong() + Rules.GOING_EXTRA_MS, p.expireAt)
        assertNotNull(p.etaSec) // 확인 직후 페이지·차 화면에 "0분"이 나오지 않게
    }
}

fun remoteOf(pos: LatLon? = null, ready: Boolean = false, late: Boolean = false, boarded: Boolean = false,
             transit: String? = null, status: String = "going", consent: Boolean? = true, ack: Boolean = false) =
    Event.Remote(status, consent, pos, ready, late, boarded, transit, ack)

/** 합성 지역에서 1초마다 위치를 넣는다. 꺾인 경로는 점을 여러 개 준다. (시각, 할 일) 목록을 돌려준다 */
fun drive(area: Area, p: Pickup, v: Double, vararg pts: XY, t0: Long = 1_000): List<Pair<Long, Action>> {
    val path = Road("path", "", pts.map { ll(area.center, it) }, oneway = false)
    val total = roadLength(area.center, path)
    val out = mutableListOf<Pair<Long, Action>>()
    var t = 0
    while (true) {
        val s = minOf(t * v, total)
        val now = t0 + t * 1_000L
        Pick.step(area, p, Event.CarPos(pointAlong(area.center, path, s)), now).forEach { out += now to it }
        if (s >= total) return out
        t++
    }
}

/** data-model §6 진행 방향·속도·서 있음·남은 시간(tasks T021) */
class MotionTest {
    private val o = Synth.o
    private fun at(x: Double, y: Double) = Synth.at(x, y)

    @Test fun headingNeedsEightMeters() {
        val t = mutableListOf(Fix(at(0.0, 0.0), 0), Fix(at(0.0, 5.0), 1_000))
        assertNull(Pick.heading(o, t))
        t += Fix(at(0.0, 10.0), 2_000)
        assertEquals(0.0, Pick.heading(o, t)!!, 0.5)
        t += Fix(at(9.0, 10.0), 3_000) // 8m 넘게 떨어진 가장 최근 점은 (0,10)
        assertEquals(90.0, Pick.heading(o, t)!!, 0.5)
    }

    @Test fun speedUsesTheLastThirtySeconds() {
        val t = mutableListOf(Fix(at(0.0, 0.0), 0))
        for (k in 0..30) t += Fix(at(0.0, 10.0 * k), 10_000L + k * 1_000) // 0~10초는 서 있었다
        assertEquals(10.0, Pick.speed(o, t, 40_000), 0.01)
    }

    @Test fun stoppedMeansUnderFiveMetersInFiveSeconds() {
        assertTrue(Pick.stopped(o, listOf(Fix(at(0.0, -50.0), 0), Fix(at(0.0, 2.0), 3_000), Fix(at(0.0, 4.0), 6_000)), 6_000))
        assertFalse(Pick.stopped(o, listOf(Fix(at(0.0, 0.0), 2_000), Fix(at(0.0, 6.0), 6_000)), 6_000))
    }

    @Test fun remainingBeforeAndAfterTheWaypoint() {
        val s = Pick.choose(Synth.all, at(0.0, -2000.0), at(-10.0, 0.0), careful = false).first() // 경유지 (0,-100)
        val fast = listOf(Fix(at(0.0, -510.0), 0), Fix(at(0.0, -500.0), 1_000))
        assertEquals((400 * 1.3 + 100) / 10.0, Pick.remaining(o, fast, s, false, 1_000)!!, 0.5)
        val slow = listOf(Fix(at(0.0, -502.0), 0), Fix(at(0.0, -500.0), 1_000)) // 초속 2m → 시속 20km로 본다
        assertEquals((400 * 1.3 + 100) / (20 / 3.6), Pick.remaining(o, slow, s, false, 1_000)!!, 0.5)
        val near = listOf(Fix(at(0.0, -60.0), 0), Fix(at(0.0, -50.0), 1_000))
        assertEquals(dist(o, at(0.0, -50.0), s.pos) * 1.3 / 10.0, Pick.remaining(o, near, s, true, 1_000)!!, 0.5)
        assertNull(Pick.remaining(o, emptyList(), s, false, 1_000))
    }
}

/** data-model §7 going: 자동 전화, 150m, 음성 상한(tasks T021) */
class GoingTest {
    private val o = Synth.o
    private fun at(x: Double, y: Double) = Synth.at(x, y)
    private val call = Action.CallOrNotify("민지", "01012345678", speak = true)

    private fun going(passenger: XY = XY(-10.0, 0.0)) = Synth.going(passenger)

    private fun north(p: Pickup) = drive(Synth.all, p, 10.0, XY(0.0, -2000.0), XY(0.0, -20.0))

    @Test fun callsOnceAtWalkPlusOneMinute() {
        val p = going() // 길 건너 서쪽: 걷는 시간 20.8m/67×60 + 60 = 78.6초 → 문턱 138.6초
        assertEquals(78.6, p.lead.walkSec!!, 0.5)
        val acts = north(p)
        val calls = acts.filter { it.second is Action.CallOrNotify }
        assertEquals(listOf(call), calls.map { it.second })
        // (거리×1.3+100)/10 ≤ 138.6 → 경유지까지 989m 안 → y ≥ -1089 → 출발 92초 뒤 알림, 2차: 그 60초 뒤 전화
        assertEquals(153_000L, calls[0].first)
        assertTrue(p.lead.called && p.lead.outNow)
        assertTrue(acts.any { it.first == 93_000L && it.second == Action.Push })
    }

    @Test fun thresholdIsAtLeastTwoMinutesAndFollowsNewPassengerPositions() {
        val p = going()
        Pick.step(Synth.all, p, remoteOf(at(20.0, 40.0)), 500) // 같은 편 42m: 걷는 시간 49초 → 문턱 120초
        assertEquals(55.2 / 67 * 60, p.lead.walkSec!!, 0.5)
        val calls = north(p).filter { it.second is Action.CallOrNotify }
        assertEquals(167_000L, calls.single().first) // 경유지까지 846m 안 → y ≥ -946 → 출발 106초 뒤 알림 + 60초
    }

    @Test fun readyButtonMeansNoCallAndNoSecondLine() {
        val p = going()
        val first = Pick.step(Synth.all, p, remoteOf(at(-10.0, 0.0), ready = true), 500)
        assertEquals(listOf(Action.Speak("민지님이 나와 있어요")), first.filterIsInstance<Action.Speak>())
        val acts = north(p).map { it.second }
        assertTrue(p.lead.called)
        assertTrue(acts.none { it is Action.CallOrNotify || it == Action.Speak("민지님이 나와 있어요") })
    }

    @Test fun standingByTheSpotOnTheSameSideMeansOut() {
        val p = going(passenger = XY(20.0, 40.0))
        Pick.step(Synth.all, p, remoteOf(at(12.0, 10.0)), 500) // 지점에서 12m, 같은 편
        val acts = north(p).map { it.second }
        assertTrue(acts.none { it is Action.CallOrNotify })
        assertEquals(1, acts.count { it == Action.Speak("민지님이 나와 있어요") })
    }

    @Test fun twentyFiveMetersAcrossTheRoadIsNotOut() {
        val p = going()
        Pick.step(Synth.all, p, remoteOf(at(-19.0, 0.0)), 500) // 지점에서 25m지만 길 건너편
        assertEquals(listOf(call), north(p).map { it.second }.filterIsInstance<Action.CallOrNotify>())
    }

    @Test fun oneFiftyRightWhenComingAlongTheRoad() {
        val p = going(passenger = XY(20.0, 40.0))
        val speaks = north(p).map { it.second }.filterIsInstance<Action.Speak>()
        assertTrue(Action.Speak("오른쪽 동쪽 가게 앞이에요") in speaks)
        assertTrue(p.spoke150 && !p.crossOver)
    }

    @Test fun oneFiftyFromTheOtherDirectionAsksHerToCross() {
        val p = going(passenger = XY(20.0, 40.0))
        val acts = drive(Synth.all, p, 10.0, XY(0.0, 1000.0), XY(0.0, 20.0), t0 = 100_000).map { it.second } // 정한 뒤 북쪽에서 내려온다
        assertTrue(Action.Speak("민지님이 길을 건너 오른쪽으로 와요") in acts)
        assertTrue(p.crossOver)
    }

    @Test fun oneFiftyWithoutHeadingSaysSoon() {
        val p = going(passenger = XY(20.0, 40.0))
        val acts = Pick.step(Synth.all, p, Event.CarPos(at(0.0, -100.0)), 100_000) // 옛 위치는 60초가 지나 지워졌다
        assertNull(p.heading)
        assertTrue(Action.Speak("곧 동쪽 가게 앞이에요") in acts)
    }

    @Test fun turningInFromACrossStreetDecidesOnlyAfterTheTurn() {
        val p = going(passenger = XY(20.0, 40.0))
        val acts = drive(Synth.all, p, 10.0, XY(-600.0, -100.0), XY(0.0, -100.0), XY(0.0, -20.0), t0 = 100_000)
        val said = acts.filter { (it.second as? Action.Speak)?.text?.contains("가게") == true }
        assertEquals(listOf(Action.Speak("오른쪽 동쪽 가게 앞이에요")), said.map { it.second })
        assertTrue(said[0].first > 100_000 + 60_000) // (0,-100)에서 꺾은 뒤
    }

    @Test fun nearTheSpotWithoutReachingItsRoadSaysSoon() {
        val p = going(passenger = XY(20.0, 40.0))
        val acts = drive(Synth.all, p, 10.0, XY(300.0, 30.0), XY(30.0, 30.0), t0 = 100_000).map { it.second }
        assertTrue(Action.Speak("곧 동쪽 가게 앞이에요") in acts)
        assertTrue(acts.none { it == Action.Speak("오른쪽 동쪽 가게 앞이에요") || it == Action.Speak("민지님이 길을 건너 오른쪽으로 와요") })
    }

    @Test fun passedWaypointStaysTrue() {
        val p = going(passenger = XY(20.0, 40.0))
        north(p)
        assertTrue(p.passedWaypoint)
        Pick.step(Synth.all, p, Event.CarPos(at(0.0, -1500.0)), 200_000)
        assertTrue(p.passedWaypoint)
    }

    @Test fun carPositionPushesAtMostOncePerSecond() {
        val p = going()
        p.lastPushAt = 10_000
        assertFalse(Action.Push in Pick.step(Synth.all, p, Event.CarPos(at(0.0, -1990.0)), 10_500))
        assertTrue(Action.Push in Pick.step(Synth.all, p, Event.CarPos(at(0.0, -1980.0)), 11_500))
        assertEquals(p.expireAt, 11_500 + (p.etaSec!! * 1000).toLong() + Rules.GOING_EXTRA_MS)
    }

    @Test fun nospotSendsCarToPassengerEta() {
        val p = Synth.minji()
        Pick.step(Synth.all, p, Event.CarPos(at(0.0, -500.0)), 0)
        Pick.step(Synth.all, p, remoteOf(at(1000.0, 1000.0), status = "link"), 0)
        assertEquals(Status.NOSPOT, p.status)
        assertNotNull(p.etaSec) // 서 있어 위치가 더 오지 않아도 차 분이 있다
        p.lastPushAt = 0
        assertTrue(Action.Push in Pick.step(Synth.all, p, Event.CarPos(at(0.0, -490.0)), 2_000))
        assertEquals(dist(o, at(0.0, -490.0), at(1000.0, 1000.0)) * 1.3 / (20 / 3.6), p.etaSec!!, 1.0)
    }

    @Test fun voiceStopsAtFiveWhileDriving() {
        val p = going(passenger = XY(20.0, 40.0))
        p.spokenWhileDriving = 4
        val acts = north(p).map { it.second }
        assertEquals(listOf(call), acts.filterIsInstance<Action.CallOrNotify>()) // 다섯 번째 줄
        assertTrue(acts.none { it is Action.Speak })
        val q = going(passenger = XY(20.0, 40.0))
        q.spokenWhileDriving = 5
        assertEquals(listOf(call.copy(speak = false)), north(q).map { it.second }.filterIsInstance<Action.CallOrNotify>())
    }

    @Test fun handsfreeSceneCallsWithinThirtySecondsOfTheRealThreshold() {
        val o = DEMO.center
        val route = Road("route", "", Scenes.route("handsfree"), oneway = false)
        val v = Scenes.text("handsfree", "speed").toDouble() / 3.6
        val walk = Scenes.text("handsfree", "walk").toDouble()
        val p = Pickup("id", "key", Person("민지", "01012345678", false), 0)
        Pick.step(DEMO, p, Event.Gear(true), 0)
        Pick.step(DEMO, p, Event.CarPos(Scenes.point("handsfree", "car")), 0)
        Pick.step(DEMO, p, remoteOf(Scenes.point("handsfree", "passenger"), status = "link"), 0)
        assertEquals(Scenes.text("handsfree", "expect"), p.spots.first().landmark.id)
        Pick.step(DEMO, p, Event.Confirm(0), 0)
        Pick.step(DEMO, p, Event.Gear(false), 0)
        assertEquals(walk, p.lead.walkSec!!, 20.0)
        val total = roadLength(o, route)
        var callAt: Double? = null
        var t = 0
        while (t * v < total) {
            if (Pick.step(DEMO, p, Event.CarPos(pointAlong(o, route, t * v)), 1_000L + t * 1_000).any { it is Action.CallOrNotify }) callAt = t.toDouble()
            t++
        }
        val real = total / v - (walk + 60) // 실제 남은 시간이 "걷는 시간 + 1분"에 닿는 때(출발 기준 초)
        // 2차: 문턱에서는 알림만, 전화는 반응 없이 60초가 지난 뒤
        assertTrue("전화 ${callAt}초, 실제 문턱 ${real}초", callAt!! - (real + 60) in 0.0..30.0)
        assertTrue(real >= 60) // 출발하자마자 걸리는 장면이 아니다
    }
}

/** 2차 data-model §6 알리고 나서 전화(2차 tasks T006). 지점은 동쪽 가게, 민지는 길 건너 서쪽 10m */
class NotifyTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)
    private val call = Action.CallOrNotify("민지", "01012345678", speak = true)

    /** 문턱(출발 92초 뒤, 93_000)을 지나 y=-1000(101_000)에서 멈춘다. 그 뒤는 Tick만 온다 */
    private fun notified(): Pickup {
        val p = Synth.going()
        drive(Synth.all, p, 10.0, XY(0.0, -2000.0), XY(0.0, -1000.0))
        assertEquals(93_000L, p.lead.notifiedAt)
        return p
    }

    @Test fun thresholdOnlyNotifiesThenCallsOnceSixtySecondsLater() {
        val p = Synth.going()
        val acts = drive(Synth.all, p, 10.0, XY(0.0, -2000.0), XY(0.0, -20.0))
        assertEquals(at(-10.0, 0.0), p.lead.notifiedPos)
        assertTrue(acts.any { it.first == 93_000L && it.second == Action.Push })
        assertTrue(acts.none { it.first == 93_000L && it.second is Action.Speak }) // 알림 단계에 운전자 음성은 없다
        assertEquals(listOf(153_000L to call), acts.filter { it.second is Action.CallOrNotify })
        assertTrue(p.lead.outNow && p.lead.called)
    }

    @Test fun tickAloneMeetsTheDeadline() {
        val p = notified()
        assertTrue(Pick.step(Synth.all, p, Event.Tick, 152_999).none { it is Action.CallOrNotify })
        assertEquals(listOf(call), Pick.step(Synth.all, p, Event.Tick, 153_000).filterIsInstance<Action.CallOrNotify>())
        assertTrue(Pick.step(Synth.all, p, Event.Tick, 155_000).none { it is Action.CallOrNotify })
    }

    @Test fun ackReadyOrMovingMoreThanTwentyMetersStopsTheCall() {
        for (answer in listOf(remoteOf(at(-10.0, 0.0), ack = true), remoteOf(at(-10.0, 0.0), ready = true), remoteOf(at(-10.0, 21.0)))) {
            val p = notified()
            Pick.step(Synth.all, p, answer, 110_000)
            assertTrue(Pick.step(Synth.all, p, Event.Tick, 153_000).none { it is Action.CallOrNotify })
            assertTrue(p.lead.called) // 다시 묻지 않는다
        }
    }

    @Test fun nineteenMetersIsNotAnAnswer() {
        val p = notified()
        Pick.step(Synth.all, p, remoteOf(at(-10.0, 19.0)), 110_000)
        assertEquals(listOf(call), Pick.step(Synth.all, p, Event.Tick, 153_000).filterIsInstance<Action.CallOrNotify>())
    }

    @Test fun onTransitMovingIsNotAnAnswer() {
        // 버스·지하철 안이면 늘 움직이므로 이동으로 반응을 보지 않는다(코드 리뷰 반영)
        val p = notified()
        p.lead.transitAccepted = true
        Pick.step(Synth.all, p, remoteOf(at(-10.0, 500.0), transit = "accept"), 110_000)
        assertEquals(listOf(call), Pick.step(Synth.all, p, Event.Tick, 153_000).filterIsInstance<Action.CallOrNotify>())
    }

    @Test fun withoutAPositionAtTheNoticeMovingIsNotJudged() {
        val p = Synth.going()
        Pick.step(Synth.all, p, remoteOf(null), 500) // 위치가 끊겼다
        drive(Synth.all, p, 10.0, XY(0.0, -2000.0), XY(0.0, -1000.0))
        assertNull(p.lead.notifiedPos)
        Pick.step(Synth.all, p, remoteOf(at(-10.0, 100.0)), 110_000)
        assertEquals(listOf(call), Pick.step(Synth.all, p, Event.Tick, 153_000).filterIsInstance<Action.CallOrNotify>())
    }

    @Test fun beingOutMeansNoNotice() {
        val p = Synth.going()
        Pick.step(Synth.all, p, remoteOf(at(-10.0, 0.0), ready = true), 500)
        drive(Synth.all, p, 10.0, XY(0.0, -2000.0), XY(0.0, -20.0))
        assertNull(p.lead.notifiedAt)
        assertFalse(p.lead.outNow)
    }
}

/** data-model §7 건너편 경고, 나와 있어요·조금 늦어요(tasks T026) */
class WrongSideTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)

    @Test fun acrossTheRoadWithinThreeMinutesPushedOnlyWhenItChanges() {
        val p = Synth.going(passenger = XY(20.0, 40.0))
        p.lead.called = true // 전화의 Push와 섞이지 않게
        Pick.step(Synth.all, p, remoteOf(at(-15.0, 30.0)), 500) // 길 건너편 15m
        val pushes = mutableListOf<Long>()
        for (t in 0..150) {
            val now = 1_000L + t * 1_000
            p.lastPushAt = now // 위치 올리기는 막고, 바뀜으로 생기는 Push만 본다
            if (Action.Push in Pick.step(Synth.all, p, Event.CarPos(at(0.0, -2000.0 + 10 * t)), now)) pushes += now
        }
        assertEquals(listOf(61_000L), pushes) // (1300×1.3+100)/10 = 179초 ≤ 180
        assertTrue(p.lead.wrongSide)
        Pick.step(Synth.all, p, remoteOf(at(12.0, 20.0)), 200_000) // 지점 쪽으로 건넜다
        p.lastPushAt = 200_000
        assertTrue(Action.Push in Pick.step(Synth.all, p, Event.CarPos(at(0.0, -480.0)), 200_500))
        assertFalse(p.lead.wrongSide)
    }

    @Test fun afterCrossOverTheOtherCurbIsTheRightOne() {
        val p = Synth.going(passenger = XY(20.0, 40.0))
        drive(Synth.all, p, 10.0, XY(0.0, 1000.0), XY(0.0, 100.0), t0 = 100_000) // 북쪽에서 내려와 "길을 건너 오른쪽으로"
        assertTrue(p.crossOver)
        Pick.step(Synth.all, p, remoteOf(at(-12.0, 20.0)), 300_000) // 안내대로 서쪽으로 건넜다
        Pick.step(Synth.all, p, Event.CarPos(at(0.0, 90.0)), 300_500)
        assertFalse(p.lead.wrongSide)
        Pick.step(Synth.all, p, remoteOf(at(12.0, 20.0)), 301_000) // 원래 지점 쪽에 남았다
        Pick.step(Synth.all, p, Event.CarPos(at(0.0, 80.0)), 301_500)
        assertTrue(p.lead.wrongSide)
    }

    @Test fun readyAndLateAreSaidOnceEach() {
        val p = Synth.going()
        val acts = (0..3).flatMap { Pick.step(Synth.all, p, remoteOf(at(-10.0, 0.0), ready = true, late = true), 500L + it * 2_000) }
        assertEquals(listOf(Action.Speak("민지님이 나와 있어요"), Action.Speak("민지님이 조금 늦는대요")), acts.filterIsInstance<Action.Speak>())
    }
}

/** data-model §5 대중교통 안과 받아들임(tasks T030). 민지는 북쪽 정류장 옆, 차는 남쪽에서 온다 */
class TransitTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)
    private val far = Landmark("far", "북쪽 가게", at(6.0, 2000.0), "r")
    private val station = Landmark("station", "남쪽역 가게", at(6.0, -1000.0), "r")
    private val car = at(0.0, -2900.0)
    private val passenger = at(-10.0, 2000.0)
    private fun leg(id: String = "bus", board: XY = XY(-20.0, 2000.0), rideSec: Int = 180) = TransitLeg(
        id, "버스 7번", Stop("북쪽역", ll(Synth.o, board)), Stop("남쪽역", at(-20.0, -1000.0)), stops = 2, rideSec = rideSec, waitSec = 240,
    )
    private fun area(vararg legs: TransitLeg) = Synth.area(listOf(far, station), transit = legs.toList())
    private fun offer(vararg legs: TransitLeg): Offer? {
        val a = area(*legs)
        return Pick.transit(a, car, passenger, false, Pick.choose(a, car, passenger, false).firstOrNull())
    }

    @Test fun offersOnlyWhenItSavesMoreThanFiveMinutes() {
        val o = offer(leg())!! // 걸어서 만나면 913초, 버스 타면 522초 → 391초 빠르다
        assertEquals("station", o.spot.landmark.id)
        assertEquals(6, o.gainMin)
        assertNull(offer(leg(rideSec = 300))) // 271초
    }

    @Test fun skipsStopsBeyondWalkingDistance() = assertNull(offer(leg(board = XY(-20.0, 2400.0))))

    @Test fun noWalkingSpotMeansNoOffer() = assertNull(Pick.transit(area(leg()), car, passenger, false, null))

    @Test fun picksTheLargestGain() = assertEquals("fast", offer(leg("slow", rideSec = 250), leg("fast", rideSec = 120))!!.leg.id)

    @Test fun particleFollowsTheLastSyllable() {
        assertEquals("으로", toParticle("화서역"))
        assertEquals("로", toParticle("화서"))
        assertEquals("로", toParticle("서울"))
    }

    private fun spotsWithOffer(): Pickup {
        val p = Synth.minji()
        Pick.step(area(leg()), p, Event.Gear(true), 0)
        Pick.step(area(leg()), p, Event.CarPos(car), 0)
        Pick.step(area(leg()), p, remoteOf(passenger, status = "link"), 0)
        assertEquals(Status.SPOTS, p.status)
        assertNotNull(p.lead.offer)
        return p
    }

    @Test fun acceptingInSpotsLeavesOnlyTheStationSpot() {
        val p = spotsWithOffer()
        val acts = Pick.step(area(leg()), p, remoteOf(passenger, transit = "accept", status = "spots"), 1_000)
        assertEquals(listOf("station"), p.spots.map { it.landmark.id })
        assertEquals(-1, p.chosen)
        assertEquals(listOf(Action.Speak("민지님이 버스 7번 타고 남쪽역으로 와요"), Action.Push), acts)
        assertTrue(Pick.step(area(leg()), p, remoteOf(passenger, transit = "accept", status = "spots"), 3_000).isEmpty())
    }

    @Test fun acceptingWhileGoingMovesTheNaviAndCallsAtTwoMinutes() {
        val p = spotsWithOffer()
        Pick.step(area(leg()), p, Event.Confirm(0), 500)
        Pick.step(area(leg()), p, Event.Gear(false), 600)
        val acts = Pick.step(area(leg()), p, remoteOf(passenger, transit = "accept"), 1_000)
        assertEquals("station", acts.filterIsInstance<Action.SetNavi>().single().spot.landmark.id)
        assertEquals(0, p.chosen)
        assertTrue(Action.Speak("민지님이 버스 7번 타고 남쪽역으로 와요") in acts)
        val walk = p.lead.walkSec
        Pick.step(area(leg()), p, remoteOf(at(-20.0, 500.0), transit = "accept"), 2_000) // 버스 안
        assertEquals(walk, p.lead.walkSec)
        val calls = drive(area(leg()), p, 10.0, XY(0.0, -2900.0), XY(0.0, -1200.0), t0 = 3_000).filter { it.second is Action.CallOrNotify }
        assertEquals(3_000L + 156_000, calls.single().first) // (840×1.3+100)/10 = 119초 ≤ 120에서 알림, 60초 뒤 전화
    }

    @Test fun demoScenesOfferTransitOnlyWhenItPays() {
        fun first(scene: String) = Pick.choose(DEMO, Scenes.point(scene, "car"), Scenes.point(scene, "passenger"), false).firstOrNull()
        fun offer(scene: String) = Pick.transit(DEMO, Scenes.point(scene, "car"), Scenes.point(scene, "passenger"), false, first(scene))
        assertEquals(Scenes.text("transit", "expect"), first("transit")?.landmark?.id)
        assertEquals("yes", Scenes.text("transit", "offer"))
        assertTrue(offer("transit")!!.gainMin >= 5)
        assertEquals("no", Scenes.text("transit-small", "offer"))
        assertNull(offer("transit-small"))
    }
}

/** data-model §7 탑승과 끝(tasks T034) */
class EndTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)
    private val seat = "ROW_1_RIGHT"

    /** 지점 앞 (0, y)에 와서 6초 서 있다. 조수석은 처음에 비어 있었다. (픽업, 지금 시각) */
    private fun stoppedAt(y: Double = -5.0, standSec: Int = 6): Pair<Pickup, Long> {
        val p = Synth.going(passenger = XY(20.0, 40.0))
        p.emptySeatsAtStart = setOf(seat)
        drive(Synth.all, p, 10.0, XY(0.0, y - 300), XY(0.0, y), t0 = 100_000)
        val t = 130_000L
        for (k in 1..standSec) Pick.step(Synth.all, p, Event.CarPos(at(0.0, y)), t + k * 1_000)
        return p to t + standSec * 1_000
    }

    @Test fun doorCycleThenEmptySeatNearTheSpotIsMet() {
        val (p, t) = stoppedAt()
        assertTrue(Pick.step(Synth.all, p, Event.Door(seat, true), t + 100).isEmpty())
        assertTrue(Pick.step(Synth.all, p, Event.Door(seat, false), t + 200).isEmpty())
        assertEquals(listOf(Action.Speak("민지님이 탔어요"), Action.ClearNavi, Action.Push, Action.End), Pick.step(Synth.all, p, Event.Seat(seat, true), t + 300))
        assertEquals(Status.MET, p.status)
    }

    @Test fun seatFirstThenDoorAlsoCounts() {
        val (p, t) = stoppedAt()
        Pick.step(Synth.all, p, Event.Door(seat, true), t + 100)
        Pick.step(Synth.all, p, Event.Seat(seat, true), t + 200)
        assertEquals(Status.GOING, p.status)
        Pick.step(Synth.all, p, Event.Door(seat, false), t + 300)
        assertEquals(Status.MET, p.status)
    }

    @Test fun farAwayOrNoNewSeatOrMovingIsNotMet() {
        val (far, t) = stoppedAt(y = -200.0) // 지점에서 200m: 문이 50m 밖에서 닫혔다
        listOf(Event.Door(seat, true), Event.Door(seat, false), Event.Seat(seat, true)).forEach { Pick.step(Synth.all, far, it, t + 100) }
        assertEquals(Status.GOING, far.status)
        val (same, u) = stoppedAt()
        listOf(Event.Door(seat, true), Event.Door(seat, false), Event.Seat("ROW_2_LEFT", true)).forEach { Pick.step(Synth.all, same, it, u + 100) }
        assertEquals(Status.GOING, same.status) // 처음부터 앉아 있던 자리
        val (moving, v) = stoppedAt(standSec = 0) // 막 도착해 아직 움직이는 중
        listOf(Event.Door(seat, true), Event.Door(seat, false), Event.Seat(seat, true)).forEach { Pick.step(Synth.all, moving, it, v + 100) }
        assertEquals(Status.GOING, moving.status)
    }

    @Test fun boardingRightAfterStoppingIsConfirmedOnTheNextTick() {
        val (p, v) = stoppedAt(standSec = 0) // 막 섰다: 최근 5초에 움직임이 남아 있다
        listOf(Event.Door(seat, true), Event.Door(seat, false), Event.Seat(seat, true)).forEach { Pick.step(Synth.all, p, it, v + 100) }
        assertEquals(Status.GOING, p.status)
        assertEquals(listOf(Action.Speak("민지님이 탔어요"), Action.ClearNavi, Action.Push, Action.End), Pick.step(Synth.all, p, Event.Tick, v + 6_000))
    }

    @Test fun nospotBoardsNearThePassengersLastPosition() {
        val p = Synth.minji()
        p.emptySeatsAtStart = setOf(seat)
        Pick.step(Synth.all, p, Event.CarPos(at(0.0, -500.0)), 0)
        Pick.step(Synth.all, p, remoteOf(at(1000.0, 1000.0), status = "link"), 0)
        assertEquals(Status.NOSPOT, p.status)
        for (k in 0..6) Pick.step(Synth.all, p, Event.CarPos(at(1000.0, 980.0)), 10_000L + k * 1_000)
        listOf(Event.Door(seat, true), Event.Door(seat, false), Event.Seat(seat, true)).forEach { Pick.step(Synth.all, p, it, 17_000) }
        assertEquals(Status.MET, p.status)
    }

    @Test fun boardedButtonEndsAnyState() {
        val p = Synth.minji()
        // link에서는 앱이 내비에 넣은 것이 없어 지우지 않는다
        assertEquals(listOf(Action.Speak("민지님이 탔어요"), Action.Push, Action.End), Pick.step(Synth.all, p, remoteOf(boarded = true, status = "link"), 1_000))
        assertEquals(Status.MET, p.status)
    }

    @Test fun cancelBeforeGoingLeavesTheNaviAlone() {
        val p = Synth.minji()
        Pick.step(Synth.all, p, Event.Gear(true), 0)
        Pick.step(Synth.all, p, Event.CarPos(at(0.0, -2000.0)), 0)
        Pick.step(Synth.all, p, remoteOf(at(-10.0, 0.0), status = "link"), 0)
        assertEquals(Status.SPOTS, p.status)
        assertEquals(listOf(Action.Push, Action.End), Pick.step(Synth.all, p, Event.Cancel, 1_000))
    }

    @Test fun driverDoorIsNotBoarding() {
        val (p, t) = stoppedAt()
        listOf(Event.Door(Rules.DRIVER_DOOR, true), Event.Door(Rules.DRIVER_DOOR, false), Event.Seat(seat, true)).forEach { Pick.step(Synth.all, p, it, t + 100) }
        assertEquals(Status.GOING, p.status)
    }

    @Test fun seatCountsOnlyWhileGoingAndOnlyWhileTaken() {
        val early = Synth.minji()
        early.emptySeatsAtStart = setOf(seat)
        Pick.step(Synth.all, early, Event.Seat(seat, true), 0) // link에서 짐을 올렸다
        assertTrue(early.takenSeats.isEmpty())
        val (p, t) = stoppedAt()
        Pick.step(Synth.all, p, Event.Seat(seat, true), t + 100)
        Pick.step(Synth.all, p, Event.Seat(seat, false), t + 200) // 다시 비었다
        listOf(Event.Door(seat, true), Event.Door(seat, false)).forEach { Pick.step(Synth.all, p, it, t + 300) }
        assertEquals(Status.GOING, p.status)
    }

    @Test fun waitingAtTheSpotDoesNotPushTheDeadline() {
        val (p, t) = stoppedAt()
        val deadline = p.expireAt
        for (k in 1..5) Pick.step(Synth.all, p, Event.CarPos(at(0.0, -5.0)), t + k * 60_000L)
        assertEquals(deadline, p.expireAt)
        assertEquals(listOf(Action.ClearNavi, Action.Push, Action.End), Pick.step(Synth.all, p, Event.Tick, deadline + 1))
    }

    @Test fun serverEndStateIsTaken() {
        val p = Synth.going()
        // going에서 끝나면 어떤 끝이든 앱이 넣은 내비를 지운다(코드 리뷰 반영: 경유지가 다음 픽업에 남지 않게)
        assertEquals(listOf(Action.ClearNavi, Action.End), Pick.step(Synth.all, p, Event.Remote("stopped", null, null, false, false, false, null), 1_000))
        assertEquals(Status.STOPPED, p.status)
    }

    @Test fun cancelOnlyInPark() {
        val p = Synth.going()
        assertTrue(Pick.step(Synth.all, p, Event.Cancel, 1_000).isEmpty())
        Pick.step(Synth.all, p, Event.Gear(true), 2_000)
        assertEquals(listOf(Action.ClearNavi, Action.Push, Action.End), Pick.step(Synth.all, p, Event.Cancel, 3_000))
        assertEquals(Status.CANCELED, p.status)
    }

    @Test fun tickExpiresAfterTheDeadline() {
        val p = Synth.minji() // link: 만든 때 + 60분
        assertTrue(Pick.step(Synth.all, p, Event.Tick, Rules.WAIT_MS).isEmpty())
        assertEquals(listOf(Action.Push, Action.End), Pick.step(Synth.all, p, Event.Tick, Rules.WAIT_MS + 1))
        assertEquals(Status.EXPIRED, p.status)
    }

    @Test fun goneExpiresAndEndedIgnoresEverything() {
        val p = Synth.going()
        assertEquals(listOf(Action.ClearNavi, Action.End), Pick.step(Synth.all, p, Event.Gone(), 1_000))
        assertEquals(Status.EXPIRED, p.status)
        assertTrue(Pick.step(Synth.all, p, remoteOf(boarded = true), 2_000).isEmpty())
    }
}

/** 2차 원래 목적지로 이어 가기(tasks T011). 운전석 문은 EndTest.driverDoorIsNotBoarding */
class ContinueTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)
    private val seat = "ROW_1_RIGHT"

    /** 지점 앞에 와서 6초 서 있다가 조수석 문을 열고 닫고 앉는다. 탑승 때 할 일을 돌려준다 */
    private fun boardAfter(route: Event.Route?): List<Action> {
        val p = Synth.going(passenger = XY(20.0, 40.0))
        route?.let { Pick.step(Synth.all, p, it, 50_000) }
        p.emptySeatsAtStart = setOf(seat)
        drive(Synth.all, p, 10.0, XY(0.0, -305.0), XY(0.0, -5.0), t0 = 100_000)
        for (k in 1..6) Pick.step(Synth.all, p, Event.CarPos(at(0.0, -5.0)), 130_000L + k * 1_000)
        Pick.step(Synth.all, p, Event.Door(seat, true), 136_100)
        Pick.step(Synth.all, p, Event.Door(seat, false), 136_200)
        return Pick.step(Synth.all, p, Event.Seat(seat, true), 136_300)
    }

    @Test fun withTheOriginalDestinationTheBoardingLineSaysSo() {
        assertEquals(listOf(Action.Speak("민지님 탑승 확인. 다음 목적지로 안내할게요"), Action.ClearNavi, Action.Push, Action.End),
            boardAfter(Event.Route("집", null)))
    }

    @Test fun withoutItTheFirstLineStays() {
        assertEquals(Action.Speak("민지님이 탔어요"), boardAfter(Event.Route(null, null)).first())
        assertEquals(Action.Speak("민지님이 탔어요"), boardAfter(null).first())
    }

    @Test fun lostDestinationIsKept() {
        val p = Synth.going()
        Pick.step(Synth.all, p, Event.Route(null, "집"), 1_000)
        assertEquals("집", p.lostDest)
        assertNull(p.nextDest)
        Pick.step(Synth.all, p, Event.Route(null, null), 2_000) // 대중교통으로 다시 물었을 때 목적지는 앱이 넣은 지점이다
        assertEquals("집", p.lostDest)
    }
}

/** 2차 도착 알림 확인(tasks T017). 지점은 동쪽 가게 (6, 0) */
class ArriveTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)
    private val ask = Action.Speak("민지님께 도착했다고 알릴까요?")

    /** (0, y)에 130_000에 와서 sec초 서 있는다. 깜빡이는 미리 넣는다(null이면 넣지 않음) */
    private fun standing(signal: Boolean?, sec: Int, y: Double = -30.0): Pair<Pickup, List<Action>> {
        val p = Synth.going(passenger = XY(20.0, 40.0))
        p.lead.called = true // 알림·전화와 섞이지 않게
        signal?.let { Pick.step(Synth.all, p, Event.TurnSignal(it), 99_000) }
        val acts = drive(Synth.all, p, 10.0, XY(0.0, y - 300), XY(0.0, y), t0 = 100_000).map { it.second }.toMutableList()
        for (k in 1..sec) acts += Pick.step(Synth.all, p, Event.CarPos(at(0.0, y)), 130_000L + k * 1_000)
        return p to acts
    }

    @Test fun rightSignalNearTheSpotAfterThreeSecondsAsksOnce() {
        val (p, acts) = standing(true, 6)
        assertEquals(1, acts.count { it == ask })
        assertTrue(p.arriveAsked && p.arriveCard)
        assertTrue(standing(true, 2).second.none { it == ask }) // 2초는 아직 서 있지 않다
    }

    @Test fun leftOffOrFarDoesNotAsk() {
        assertTrue(standing(false, 6).second.none { it == ask })
        assertTrue(standing(null, 6).second.none { it == ask })
        assertTrue(standing(true, 6, y = -60.0).second.none { it == ask }) // 지점에서 60m
    }

    @Test fun onlyOnTheSpotRoadEitherWay() {
        // 옆길 신호 대기(지점 길로 꺾으려고 오른쪽 깜빡이)는 도착이 아니다(코드 리뷰 반영)
        val side = Synth.going(passenger = XY(20.0, 40.0))
        side.lead.called = true
        Pick.step(Synth.all, side, Event.TurnSignal(true), 99_000)
        val acts = drive(Synth.all, side, 10.0, XY(-330.0, 30.0), XY(-30.0, 30.0), t0 = 100_000).map { it.second }.toMutableList()
        for (k in 1..6) acts += Pick.step(Synth.all, side, Event.CarPos(at(-30.0, 30.0)), 130_000L + k * 1_000)
        assertTrue(acts.none { it == ask })
        // 반대 방향으로 지점 길에 섰다(건너편 도착, 위임 결정 D1): 묻는다
        val back = Synth.going(passenger = XY(20.0, 40.0))
        back.lead.called = true
        Pick.step(Synth.all, back, Event.TurnSignal(true), 99_000)
        val b = drive(Synth.all, back, 10.0, XY(0.0, 330.0), XY(0.0, 30.0), t0 = 100_000).map { it.second }.toMutableList()
        for (k in 1..6) b += Pick.step(Synth.all, back, Event.CarPos(at(0.0, 30.0)), 130_000L + k * 1_000)
        assertEquals(1, b.count { it == ask })
    }

    @Test fun signalAfterStoppingAsksOnTheSignal() {
        val (p, _) = standing(null, 6)
        assertTrue(ask in Pick.step(Synth.all, p, Event.TurnSignal(true), 136_500))
    }

    @Test fun tickAloneSeesTheStop() {
        val (p, _) = standing(true, 0) // 막 왔다. 그 뒤 위치가 오지 않는다
        assertTrue(Pick.step(Synth.all, p, Event.Tick, 132_000).none { it == ask })
        assertTrue(ask in Pick.step(Synth.all, p, Event.Tick, 133_000))
    }

    @Test fun sendingInParkMarksArrivedAndClosesTheCard() {
        val (p, _) = standing(true, 6)
        assertTrue(Pick.step(Synth.all, p, Event.Arrive(true), 137_000).isEmpty()) // 주행 중에는 무시
        assertTrue(p.arriveCard)
        Pick.step(Synth.all, p, Event.Gear(true), 137_500)
        assertEquals(listOf(Action.Push), Pick.step(Synth.all, p, Event.Arrive(true), 138_000))
        assertTrue(p.arrived)
        assertFalse(p.arriveCard)
        assertTrue(Pick.step(Synth.all, p, Event.Arrive(true), 139_000).isEmpty()) // 카드 없이 온 것
    }

    @Test fun noClosesTheCardWithoutSending() {
        val (p, _) = standing(true, 6)
        Pick.step(Synth.all, p, Event.Gear(true), 137_500)
        assertTrue(Pick.step(Synth.all, p, Event.Arrive(false), 138_000).isEmpty())
        assertFalse(p.arrived || p.arriveCard)
    }
}

/** 2차 누가 건널지 비교(tasks T024). 민지는 길 건너 서쪽 10m */
class CompareTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)

    private fun east(vararg corners: XY, passenger: LatLon = at(-10.0, 0.0)) = Pick.choose(
        Synth.area(listOf(Synth.east), corners.map { NoStop(NoStopKind.CORNER, at(it.x, it.y)) }),
        at(0.0, -500.0), passenger, false,
    ).single()

    @Test fun uTurnUsesTheNearestCornerWithinSixHundredMeters() {
        assertEquals(2 * 300 * 1.3 / (25 / 3.6) + 90, east(XY(6.0, 300.0), XY(6.0, -450.0)).uturnSec!!, 0.5)
        assertNull(east(XY(6.0, 601.0)).uturnSec)
    }

    @Test fun lineOnlyWhenSheCrossesAndTheUTurnIsSlower() {
        val s = east(XY(6.0, 300.0))
        assertEquals(1, s.crossing)
        assertEquals("건너기 1분 · 유턴 4분", Pick.compareLine(s))
        val same = east(XY(6.0, 300.0), passenger = at(20.0, 40.0)) // 같은 편
        assertEquals(0, same.crossing)
        assertNotNull(same.uturnSec)
        assertNull(Pick.compareLine(same))
        assertNull(Pick.compareLine(s.copy(uturnSec = 60.0))) // 유턴도 1분이면 비교하지 않는다
    }

    @Test fun pageMinutesComeFromTheCarAndVanishOnceSheHasCrossed() {
        val s = east(XY(6.0, 300.0))
        assertEquals(3, Pick.fasterMin(s)) // 유턴 4분 − 건너기 1분
        assertNull(Pick.fasterMin(east(XY(6.0, 300.0), passenger = at(20.0, 40.0))))
        val area = Synth.area(listOf(Synth.east), listOf(NoStop(NoStopKind.CORNER, at(6.0, 300.0))))
        val p = Synth.minji()
        Pick.step(area, p, Event.Gear(true), 0)
        Pick.step(area, p, Event.CarPos(at(0.0, -500.0)), 0)
        Pick.step(area, p, remoteOf(at(-10.0, 0.0), status = "link"), 0)
        Pick.step(area, p, Event.Confirm(0), 0)
        assertNotNull(Pick.compareLine(p.spot!!))
        Pick.step(area, p, remoteOf(at(15.0, 5.0)), 1_000) // 길을 건너 지점 쪽에 왔다
        assertEquals(0, p.spot!!.crossing)
        assertNull(Pick.fasterMin(p.spot!!))
    }

    @Test fun demoOppositeSevenHasTheLine() {
        val s = Pick.choose(DEMO, Scenes.point("opposite", "car"), Scenes.point("opposite", "passenger"), false).first()
        assertEquals("seven", s.landmark.id)
        assertNotNull(Pick.compareLine(s))
    }
}

/** 2차 지나서 문구(tasks T027) */
class PassingTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)
    private fun ref(name: String, x: Double, y: Double, road: String = "r") = Landmark(name, name, at(x, y), road, spot = false)

    private fun east(vararg refs: Landmark, passenger: XY = XY(20.0, 40.0)) =
        Pick.choose(Synth.area(listOf(Synth.east) + refs), at(0.0, -500.0), at(passenger.x, passenger.y), false).single()

    @Test fun nearestReferenceUpstreamOnTheSameCurb() {
        assertEquals("near", east(ref("near", 6.0, -30.0), ref("far", 6.0, -60.0)).passing?.id)
        assertNull(east(ref("19", 6.0, -19.0), ref("151", 6.0, -151.0), ref("west", -6.0, -60.0),
            ref("other", 106.0, -60.0, "o"), ref("ahead", 6.0, 60.0)).passing)
    }

    @Test fun onTheLeftCurbUpstreamIsTheOtherWay() {
        val area = Synth.area(listOf(Synth.west, ref("n", -6.0, 60.0), ref("s", -6.0, -60.0)))
        assertEquals("n", Pick.choose(area, at(0.0, 500.0), at(-20.0, -40.0), false).single().passing?.id)
    }

    @Test fun objectParticle() {
        assertEquals(listOf("을", "을", "를", "를", "를", "를"),
            listOf("올리브영", "세븐일레븐", "GS25", "CU", "이마트24", "7번가").map(::toObject))
        assertEquals("을", toObject("3"))
    }

    @Test fun standLineNamesTheReferenceOnlyWhenSheIsBeforeIt() {
        assertEquals("약국을 지나 동쪽 가게 앞 인도에 서 계세요", east(ref("약국", 6.0, -60.0), passenger = XY(20.0, -100.0)).standLine)
        assertEquals("동쪽 가게 앞 인도에 서 계세요", east(ref("약국", 6.0, -60.0)).standLine)
        assertEquals("동쪽 가게 앞 인도에 서 계세요", east(passenger = XY(20.0, -100.0)).standLine)
    }

    @Test fun oneFiftyNamesTheReferenceUntilTheCarPassesIt() {
        fun said(ref: Landmark?, vararg path: XY): List<String> {
            val area = Synth.area(listOfNotNull(Synth.east, ref))
            val p = Synth.minji()
            Pick.step(area, p, Event.Gear(true), 0)
            Pick.step(area, p, Event.CarPos(at(path[0].x, path[0].y)), 0)
            Pick.step(area, p, remoteOf(at(20.0, 40.0), status = "link"), 0)
            Pick.step(area, p, Event.Confirm(0), 0)
            Pick.step(area, p, Event.Gear(false), 0)
            return drive(area, p, 10.0, *path).mapNotNull { (it.second as? Action.Speak)?.text }.filter { "가게" in it }
        }
        assertEquals(listOf("오른쪽 약국 지나서 동쪽 가게 앞에 세우세요"), said(ref("약국", 6.0, -60.0), XY(0.0, -2000.0), XY(0.0, -20.0)))
        assertEquals(listOf("오른쪽 동쪽 가게 앞이에요"), said(null, XY(0.0, -2000.0), XY(0.0, -20.0)))
        // 옆길에서 기준과 지점 사이로 꺾어 들어왔다: 이미 지났으니 1차 문장
        assertEquals(listOf("오른쪽 동쪽 가게 앞이에요"), said(ref("약국", 6.0, -140.0), XY(-600.0, -100.0), XY(0.0, -100.0), XY(0.0, -20.0)))
    }

    @Test fun demoSevenIsPassedAfterOliveYoung() {
        val s = Pick.choose(DEMO, Scenes.point("opposite", "car"), Scenes.point("opposite", "passenger"), false).first()
        assertEquals("oliveyoung", s.passing?.id)
        assertEquals("세븐일레븐 앞 인도에 서 계세요", s.standLine) // 민지가 세븐일레븐보다 북쪽(위임 결정 D7)
        val a = Pick.choose(DEMO, Scenes.point("arrive", "car"), Scenes.point("arrive", "passenger"), false).first()
        assertEquals("올리브영을 지나 세븐일레븐 앞 인도에 서 계세요", a.standLine) // arrive: 올리브영 남쪽
    }
}
