package kr.skku.sameside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5차 차례로 태우기(specs/005-chain-pickup data-model §1~§5). 시연 지역과 research R42 좌표 */
object Chained {
    val car = Scenes.point("group", "car")
    val minji = Scenes.point("group", "p1") // 1순위 세븐일레븐 앞
    val junho = Scenes.point("group-transit", "meet2") // 1순위 화서역 4번 출구 앞
    val seoyeon = LatLon(37.2848334, 126.9896194) // 화서역 3번 출구 앞: 지점이 3곳이 되게
    val home = Scenes.point("group-transit", "car") // 목적지 구간 계산용 아무 점
    private val seoyeonP = Person("서연", "01034567890", false)

    /** P에서 사람마다 위치를 받아 만날 곳(SPOTS)까지. 사람은 민지, 준호, 서연 순서다 */
    fun spots(vararg pos: LatLon = arrayOf(minji, junho)): Pickup {
        val p = Group.pickup(*listOf(Group.minji, Group.junho, seoyeonP).take(pos.size).toTypedArray())
        Pick.step(DEMO, p, Event.Gear(true), 0)
        Pick.step(DEMO, p, Event.CarPos(car), 0)
        pos.forEachIndexed { i, q -> Pick.step(DEMO, p, Group.remote(i, q, status = "link"), 0) }
        assertEquals(Status.SPOTS, p.status)
        return p
    }

    /** 차례로 확정하고 D로 둔다 */
    fun going(vararg pos: LatLon = arrayOf(minji, junho)): Pickup = spots(*pos).also { p ->
        Pick.step(DEMO, p, Event.ConfirmChain, 0)
        Pick.step(DEMO, p, Event.Gear(false), 0)
        assertEquals(Status.GOING, p.status)
    }

    /** research R49 route2: 첫 지점에서 덕영대로 남쪽 방향으로 4번 출구 앞까지 */
    val route2 = listOf(LatLon(37.2890036, 126.9867147), LatLon(37.2873087, 126.9881254), LatLon(37.2864192, 126.9887353),
        LatLon(37.2857011, 126.9891743), LatLon(37.2846387, 126.9897801))
}

/** data-model §1 차례 계획 */
class ChainPlanTest {
    private val o = DEMO.center
    private fun people(vararg pos: LatLon) = pos.map { it to false }

    @Test fun visitsEachFirstSpotNearestFirst() {
        val c = Pick.chain(DEMO, Chained.car, people(Chained.minji, Chained.junho), null)!!
        assertEquals(listOf("seven", "hwaseo4"), c.stops.map { it.spot.landmark.id })
        assertEquals(listOf(listOf(0), listOf(1)), c.stops.map { it.who })
        assertEquals(c.stops[0].spot.carSec, c.legs[0], 1e-6)
        assertEquals(dist(o, c.stops[0].spot.pos, c.stops[1].spot.pos) * 1.3 / (25 / 3.6) + 60, c.legs[1], 1e-6)
        assertNull(c.destSec)
        assertEquals(listOf(2, 8), c.legs.map(::minutes)) // 카드 "약 10분"(research R42)
        assertEquals(10, minutes(c.legs.sum()))
        val withHome = Pick.chain(DEMO, Chained.car, people(Chained.minji, Chained.junho), Chained.home)!!
        assertEquals(dist(o, c.stops[1].spot.pos, Chained.home) * 1.3 / (40 / 3.6), withHome.destSec!!, 1e-6)
    }

    @Test fun sameSpotOrSomeoneWithoutASpotMeansNoPlan() {
        assertNull(Pick.chain(DEMO, Chained.car, people(Scenes.point("group", "p1"), Scenes.point("group", "p2")), null)) // 둘 다 세븐일레븐
        assertNull(Pick.chain(DEMO, Chained.car, people(Chained.minji, LatLon(37.31, 126.95)), null)) // 1순위 없음
        assertEquals(2, Pick.chain(DEMO, Chained.car, listOf(Chained.minji to false, null, Chained.junho to false), null)!!.stops.size)
    }

    @Test fun threePeopleThreeStops() {
        val c = Pick.chain(DEMO, Chained.car, people(Chained.minji, Chained.seoyeon, Chained.junho), null)!!
        assertEquals(listOf("seven", "hwaseo3", "hwaseo4"), c.stops.map { it.spot.landmark.id })
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), c.stops.map { it.who })
    }

    @Test fun planWithoutAMeetingPlaceIsStillSpots() {
        // 합성 길에 800m 떨어진 가게 둘. 서로의 가게는 걷기 한도 밖이라 함께 모이는 곳이 없다
        val a = Landmark("a", "남쪽 가게", Synth.at(6.0, 0.0), "r")
        val b = Landmark("b", "북쪽 가게", Synth.at(6.0, 800.0), "r")
        val area = Synth.area(listOf(a, b))
        val p = Group.pickup()
        Pick.step(area, p, Event.Gear(true), 0)
        Pick.step(area, p, Event.CarPos(Synth.at(0.0, -2000.0)), 0)
        Pick.step(area, p, Group.remote(0, Synth.at(10.0, 0.0), status = "link"), 0)
        Pick.step(area, p, Group.remote(1, Synth.at(10.0, 800.0), status = "link"), 0)
        assertTrue(p.spots.isEmpty())
        assertEquals(listOf("a", "b"), p.chain!!.stops.map { it.spot.landmark.id })
        assertEquals(Status.SPOTS, p.status)
    }
}

/** data-model §3 전이 */
class ChainStepTest {
    private val seat = "ROW_1_RIGHT"
    private val sevenPos = DEMO.landmarks.first { it.id == "seven" }.pos

    @Test fun confirmGoesToTheFirstStopWithEachPersonsOwnWalk() {
        val p = Chained.spots()
        val out = Pick.step(DEMO, p, Event.ConfirmChain, 0)
        assertEquals(Status.GOING, p.status)
        assertTrue(p.chained)
        assertEquals(listOf("seven", "hwaseo4"), p.spots.map { it.landmark.id })
        assertEquals(0, p.chosen)
        assertEquals(listOf(0, 1), p.members.map { it.stop })
        assertNull(p.nearbySec)
        assertTrue(Action.SetNavi(p.spots[0]) in out)
        // 둘째 지점의 서 있을 곳·걷는 시간은 준호 것이다(합친 지점에서도 사람마다)
        val mine = Pick.plan(DEMO, p.chain!!.stops[1].spot, Chained.junho, false)!!
        assertEquals(mine.standLine, p.spots[1].plans[1]!!.standLine)
        assertNull(p.spots[1].plans[0])
        assertEquals(mine.arriveSec, p.members[1].walkSec!!, 1e-6)
    }

    @Test fun confirmNeedsParkAndAPlan() {
        val p = Chained.spots()
        Pick.step(DEMO, p, Event.Gear(false), 0)
        Pick.step(DEMO, p, Event.ConfirmChain, 0)
        assertEquals(Status.SPOTS, p.status)
    }

    @Test fun boardingTheFirstStopMovesOnAndStartsTheStopOver() {
        val p = Chained.going()
        p.passedWaypoint = true; p.spoke150 = true; p.reached = true; p.doorCycledNearSpot = true
        p.arriveAsked = true; p.arriveCard = true; p.arrived = true; p.crossOver = true; p.spokenWhileDriving = Rules.VOICE_CAP
        Pick.step(DEMO, p, Event.CarPos(sevenPos), 1_000)
        val out = Pick.step(DEMO, p, Group.remote(0, boarded = true), 2_000)
        assertEquals(1, p.chosen)
        assertTrue(p.members[0].aboard)
        assertTrue(Action.SetNavi(p.spots[1]) in out)
        assertEquals(listOf(Action.Speak("민지님이 탔어요. 다음은 준호님, 화서역 4번 출구 앞이에요")), out.filterIsInstance<Action.Speak>())
        assertEquals(Pick.remaining(DEMO.center, p.track, p.spots[1], false, 2_000)!!, p.etaSec!!, 1e-6)
        assertEquals(dist(DEMO.center, sevenPos, p.spots[1].waypoint) * 1.3 + 100, p.startM!!, 1e-6)
        assertFalse(p.passedWaypoint || p.spoke150 || p.reached || p.doorCycledNearSpot || p.arriveAsked || p.arriveCard || p.arrived || p.crossOver)
        assertEquals(1, p.spokenWhileDriving) // 넘어가기 한 줄부터 지점마다 다시 센다
    }

    /** 세븐일레븐 앞에 6초 서 있다. 조수석은 처음에 비어 있었다 */
    private fun stoppedAtSeven(p: Pickup): Long {
        p.emptySeatsAtStart = setOf(seat)
        for (k in 0..6) Pick.step(DEMO, p, Event.CarPos(sevenPos), 10_000L + k * 1_000)
        return 16_100L
    }

    @Test fun seatAloneMovesOnAndMarksThemAboard() {
        val p = Chained.going()
        val t = stoppedAtSeven(p)
        listOf(Event.Door(seat, true), Event.Door(seat, false), Event.Seat(seat, true)).forEach { Pick.step(DEMO, p, it, t) }
        assertEquals(1, p.chosen)
        assertTrue(p.members[0].aboard)
        assertEquals(Status.GOING, p.status)
        Pick.step(DEMO, p, Event.Gone(1), t + 1_000) // 착좌로 넘어간 뒤 둘째 사람이 빠짐
        assertEquals(Status.MET, p.status)
    }

    @Test fun leavingTheCurrentStopSkipsToTheNextWithOneLine() {
        val p = Chained.going()
        val out = Pick.step(DEMO, p, Event.Gone(0), 1_000)
        assertEquals(Status.GOING, p.status)
        assertEquals(0, p.chosen)
        assertEquals(listOf("hwaseo4"), p.spots.map { it.landmark.id })
        assertEquals(0, p.members[1].stop)
        assertFalse(p.members[1].aboard)
        assertEquals(listOf(Action.Speak("민지님은 이번에 함께 타지 않아요. 다음은 준호님, 화서역 4번 출구 앞이에요")), out.filterIsInstance<Action.Speak>())
        assertTrue(Action.SetNavi(p.spots[0]) in out)
    }

    @Test fun boardingTheLastStopIsMet() {
        val p = Chained.going()
        p.nextDest = "집"
        Pick.step(DEMO, p, Group.remote(0, boarded = true), 1_000)
        assertTrue(Action.Speak("모두 탑승 확인. 다음 목적지로 안내할게요") in Pick.step(DEMO, p, Group.remote(1, boarded = true), 2_000))
        assertEquals(Status.MET, p.status)
    }

    @Test fun aStopWhoseOnlyPersonBoardedEarlyIsSkipped() {
        val p = Chained.going()
        Pick.step(DEMO, p, Group.remote(1, boarded = true), 1_000) // 준호가 걸어와 첫 지점에서 먼저 탔다
        assertEquals(0, p.chosen)
        Pick.step(DEMO, p, Group.remote(0, boarded = true), 2_000)
        assertEquals(Status.MET, p.status) // 준호 지점으로 넘어가지 않는다
    }

    @Test fun someoneAlreadyAboardWhoStopsSharingIsStillInTheCar() {
        val p = Chained.going()
        Pick.step(DEMO, p, Group.remote(0, boarded = true), 1_000)
        val out = Pick.step(DEMO, p, Group.remote(0, status = "stopped"), 2_000) // 탄 뒤 페이지에서 공유 끄기
        assertTrue(out.none { it is Action.Speak })
        assertFalse(p.members[0].out)
        assertTrue(Action.Speak("민지님, 준호님이 탔어요") in Pick.step(DEMO, p, Group.remote(1, boarded = true), 3_000))
    }

    @Test fun secondStopCountsSeatsTakenSoFar() {
        val p = Chained.going()
        val rear = "ROW_2_RIGHT"
        val t = stoppedAtSeven(p)
        p.emptySeatsAtStart = setOf(seat, rear)
        listOf(Event.Door(seat, true), Event.Door(seat, false), Event.Seat(seat, true)).forEach { Pick.step(DEMO, p, it, t) }
        assertEquals(1, p.chosen)
        val stop = DEMO.landmarks.first { it.id == "hwaseo4" }.pos
        for (k in 0..6) Pick.step(DEMO, p, Event.CarPos(stop), 30_000L + k * 1_000)
        listOf(Event.Door(rear, true), Event.Door(rear, false), Event.Seat(rear, true)).forEach { Pick.step(DEMO, p, it, 36_100) }
        assertEquals(Status.MET, p.status) // 앉은 자리 2 ≥ 지금 지점까지 2명
    }

    @Test fun confirmingASpotCardWithAPlanDropsTheNearbyLine() {
        val p = Chained.spots()
        p.nearbySec = 1_000.0
        Pick.step(DEMO, p, Event.Confirm(0), 0)
        assertNull(p.nearbySec)
    }

    @Test fun leavingALaterStopJoinsItsLegs() {
        val p = Chained.going(Chained.minji, Chained.junho, Chained.seoyeon) // 차례: 민지(세븐) → 서연(3번 출구) → 준호(4번 출구)
        assertEquals(listOf(0, 2, 1), p.members.map { it.stop })
        val legs = p.chain!!.legs
        val out = Pick.step(DEMO, p, Event.Gone(2), 1_000) // 서연(지점 1, 지금 지점 아님)
        assertEquals(listOf("seven", "hwaseo4"), p.spots.map { it.landmark.id })
        assertEquals(listOf(legs[0], legs[1] + legs[2] - 60), p.chain!!.legs)
        assertEquals(1, p.members[1].stop)
        assertEquals(p.etaSec!! + legs[1] + legs[2] - 60, Pick.memberEta(p, 1)!!, 1e-6)
        assertTrue(Action.Speak("서연님은 이번에 함께 타지 않아요") in out)
        assertEquals(0, p.chosen)
    }

    @Test fun leavingTheLastStopJoinsItIntoTheDestinationLeg() {
        val p = Chained.spots()
        Pick.step(DEMO, p, Event.Dest(Chained.home), 0)
        Pick.step(DEMO, p, Event.ConfirmChain, 0)
        val c = p.chain!!
        Pick.step(DEMO, p, Event.Gone(1), 1_000)
        assertEquals(listOf(c.legs[0]), p.chain!!.legs)
        assertEquals(c.legs[1] + c.destSec!! - 60, p.chain!!.destSec!!, 1e-6)
    }

    @Test fun destinationRebuildsOnlyAGroupPlanBeforeConfirm() {
        val p = Chained.spots()
        assertNull(p.chain!!.destSec)
        val cards = p.spots
        Pick.step(DEMO, p, Event.Dest(Chained.home), 1_000)
        assertEquals(Chained.home, p.destPos)
        assertNotNull(p.chain!!.destSec)
        assertSame(cards, p.spots) // 카드 순서는 그대로, 목적지 구간만 다시 센다

        val one = Synth.minji()
        Pick.step(Synth.all, one, Event.Gear(true), 0)
        Pick.step(Synth.all, one, Event.CarPos(Synth.at(0.0, -2000.0)), 0)
        Pick.step(Synth.all, one, remoteOf(Synth.at(-10.0, 0.0), status = "link"), 0)
        val spots = one.spots
        Pick.step(Synth.all, one, Event.Dest(Synth.at(0.0, 3000.0)), 1_000)
        assertSame(spots, one.spots) // 한 명 픽업은 값만 저장한다
        assertNotNull(one.destPos)

        val g = Chained.spots()
        Pick.step(DEMO, g, Event.Dest(Chained.home), 0)
        Pick.step(DEMO, g, Event.ConfirmChain, 0)
        val plan = g.chain
        Pick.step(DEMO, g, Event.Dest(null), 1_000) // 목적지 방식: 확정 뒤 내비 목적지가 앱이 넣은 지점이 된다
        assertSame(plan, g.chain)
        assertEquals(Chained.home, g.destPos) // 확정 뒤에는 바꾸지 않는다
    }
}

/** data-model §4 사람별 남은 시간과 알림 */
class ChainEtaTest {
    @Test fun eachPersonHasTheirOwnEta() {
        val p = Chained.going()
        val eta = p.etaSec!!
        assertEquals(eta, Pick.memberEta(p, 0)!!, 1e-6)
        assertEquals(eta + p.chain!!.legs[1], Pick.memberEta(p, 1)!!, 1e-6)
        Pick.step(DEMO, p, Group.remote(0, boarded = true), 1_000)
        assertNull(Pick.memberEta(p, 0))
        assertEquals(p.etaSec!!, Pick.memberEta(p, 1)!!, 1e-6)
    }

    @Test fun onlyTheCurrentStopIsCalledOutAndAsked() {
        val p = Chained.going()
        val said = Scenes.route("group").withIndex().flatMap { (k, pt) -> Pick.step(DEMO, p, Event.CarPos(pt), 1_000L + k * 2_000) }.toMutableList()
        val end = Scenes.route("group").last()
        said += Pick.step(DEMO, p, Event.TurnSignal(true), 40_000)
        for (k in 1..5) said += Pick.step(DEMO, p, Event.CarPos(end), 40_000L + k * 1_000)
        assertTrue(p.members[0].outNow)
        assertFalse(p.members[1].outNow) // 준호 남은 시간 = 세븐일레븐까지 + 약 8분 > 문턱
        assertTrue(Action.Speak("민지님께 도착했다고 알릴까요?") in said)
        assertTrue(said.filterIsInstance<Action.Speak>().none { "준호" in it.text })

        Pick.step(DEMO, p, Group.remote(0, boarded = true), 50_000)
        assertEquals(1, p.chosen)
        Chained.route2.forEachIndexed { k, pt -> Pick.step(DEMO, p, Event.CarPos(pt), 60_000L + k * 5_000) }
        assertTrue(p.members[1].outNow)
    }
}

/** data-model §5 노드 자리 */
class ChainXsTest {
    @Test fun proportionalWithAMinimumGap() {
        assertEquals(listOf(0f, 120f, 600f, 900f), chainXs(listOf(120.0, 480.0, 300.0), 0f, 900f, 100f))
        assertEquals(listOf(0f, 100f, 200f, 900f), chainXs(listOf(10.0, 10.0, 880.0), 0f, 900f, 100f)) // 앞이 몰림: 오른쪽으로 민다
        assertEquals(listOf(0f, 700f, 800f, 900f), chainXs(listOf(880.0, 10.0, 10.0), 0f, 900f, 100f)) // 뒤가 몰림: 왼쪽으로 당긴다
        assertEquals(listOf(0f, 300f, 600f, 900f), chainXs(listOf(1.0, 1.0, 1.0), 0f, 900f, 400f)) // 둘 다 안 되면 같은 간격
        assertEquals(listOf(10f, 55f, 100f), chainXs(listOf(0.0, 0.0), 10f, 100f, 20f)) // 시간이 0이면 같은 간격
    }
}
