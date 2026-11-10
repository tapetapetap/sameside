package kr.skku.sameside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3차 함께 태우기 준비(specs/003-group-pickup data-model §1~§4). 합성 지역은 PickTest의 Synth다 */
object Group {
    private fun at(x: Double, y: Double) = Synth.at(x, y)
    val minji = Person("민지", "01012345678", false)
    val junho = Person("준호", "01023456789", false)

    fun pickup(vararg people: Person) = Pickup(people.toList().ifEmpty { listOf(minji, junho) }.mapIndexed { i, p -> Member(p, "id$i", "key$i") }, 0L)

    fun remote(who: Int, pos: LatLon? = null, status: String = "going", consent: Boolean? = true, ready: Boolean = false,
               boarded: Boolean = false, transit: String? = null, ack: Boolean = false) =
        remoteOf(pos, ready = ready, boarded = boarded, transit = transit, status = status, consent = consent, ack = ack).copy(who = who)

    /** 민지는 길 서쪽(-10, 0), 준호는 동쪽 인도(20, 40). 차는 남쪽 2km에서 북쪽으로 온다. 지점은 동쪽 가게 */
    fun going(): Pickup {
        val p = pickup()
        val a = Synth.all
        Pick.step(a, p, Event.Gear(true), 0)
        Pick.step(a, p, Event.CarPos(at(0.0, -2000.0)), 0)
        Pick.step(a, p, remote(0, at(-10.0, 0.0), status = "link"), 0)
        Pick.step(a, p, remote(1, at(20.0, 40.0), status = "link"), 0)
        Pick.step(a, p, Event.Confirm(0), 0)
        Pick.step(a, p, Event.Gear(false), 0)
        assertEquals(Status.GOING, p.status)
        assertEquals("east", p.spot?.landmark?.id)
        return p
    }

    /** DEMO 장면 좌표로 고르고(P) 1순위를 확정한 뒤 D로 둔다 */
    fun demo(scene: String): Pickup {
        val p = pickup()
        Pick.step(DEMO, p, Event.Gear(true), 0)
        Pick.step(DEMO, p, Event.CarPos(Scenes.point(scene, "car")), 0)
        Pick.step(DEMO, p, remote(0, Scenes.point(scene, "p1"), status = "link"), 0)
        Pick.step(DEMO, p, remote(1, Scenes.point(scene, "p2"), status = "link"), 0)
        assertEquals(Status.SPOTS, p.status)
        return p
    }
}

/** data-model §2-2 사람별 가는 방법 */
class PlanTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)
    private val leg = TransitLeg("bus", "7번", Stop("먼 정류장", at(-2000.0, 50.0)), Stop("가게 정류장", at(-20.0, 10.0)), 3, 180, 120)
    private val area = Synth.area(listOf(Synth.east), transit = listOf(leg))
    private val spot = Pick.side(area, at(0.0, -2000.0), Synth.east)!!

    @Test fun walkWithinTheLimitElseTransitElseNothing() {
        val walk = Pick.plan(area, spot, at(-10.0, 0.0), false)!!
        assertNull(walk.leg)
        assertEquals(1, walk.crossing)
        val ride = Pick.plan(area, spot, at(-2000.0, 0.0), false)!!
        assertEquals("bus", ride.leg?.id)
        assertEquals(50 * 1.3 / 67 * 60 + 120 + 180 + Pick.walk(area, leg.alight.pos, spot).first, ride.arriveSec, 0.5)
        assertNull(Pick.plan(area, spot, at(-4000.0, 0.0), false))
    }

    @Test fun carefulPeopleWalkOnly150m() {
        assertNull(Pick.plan(area, spot, at(-10.0, 120.0), false)?.leg) // 157m: 걷는다
        assertNull(Pick.plan(Synth.area(listOf(Synth.east)), spot, at(-10.0, 120.0), true)) // 가까이만은 150m까지
    }
}

/** data-model §2-3 함께 고르기(시연 좌표, research R20) */
class GroupChooseTest {
    private fun people(scene: String) = listOf(Scenes.point(scene, "p1") to false, Scenes.point(scene, "p2") to false)

    @Test fun walkingGroupMeetsAtSevenEleven() {
        val first = Pick.chooseGroup(DEMO, Scenes.point("group", "car"), people("group")).first()
        assertEquals("seven", first.landmark.id)
        assertTrue(first.plans.all { it!!.leg == null })
        assertEquals(listOf(3, 2), first.plans.map { minutes(it!!.arriveSec) })
        assertEquals(2, minutes(first.carSec))
        assertEquals("차가 오는 쪽 · 모두 걸어서 · 세워도 되는 곳", first.reason)
    }

    @Test fun ridersGatherAtTheStationWhenItIsFasterByMoreThanFiveMinutes() {
        val spots = Pick.chooseGroup(DEMO, Scenes.point("group-transit", "car"), people("group-transit"))
        assertEquals("pharmacy", spots[0].landmark.id)
        assertTrue(spots[0].plans.all { it!!.leg?.line == "1호선" })
        assertEquals("차가 오는 쪽 · 1호선으로 모여서 · 세워도 되는 곳", spots[0].reason)
        assertEquals("seven", spots[1].landmark.id) // 걸어서 모이면 19.7분: 순위 분 20 > 19(13.3분 + 5분)
    }

    @Test fun personWhoLeftIsLeftOut() {
        val car = Scenes.point("group", "car")
        val p1 = Scenes.point("group", "p1")
        val withOut = Pick.chooseGroup(DEMO, car, listOf(p1 to false, null))
        assertEquals(Pick.chooseGroup(DEMO, car, listOf(p1 to false)).map { it.landmark.id }, withOut.map { it.landmark.id })
        assertNull(withOut.first().plans[1])
    }
}

/** data-model §3 근처로 데리러 가기 어림 */
class NearbyTest {
    private fun people(scene: String) = listOf(Scenes.point(scene, "p1") to false, Scenes.point(scene, "p2") to false)

    @Test fun sameSpotIsVisitedOnce() {
        // 두 사람의 한 명 픽업 1순위가 모두 세븐일레븐이라 한 번 들른다(20.6분)
        assertEquals(21, minutes(Pick.nearby(DEMO, Scenes.point("group-transit", "car"), people("group-transit"))!!))
    }

    @Test fun shownOnlyWhenTheFirstSpotHasRiders() {
        assertEquals(21, minutes(Group.demo("group-transit").nearbySec!!))
        assertNull(Group.demo("group").nearbySec)
    }
}

/** data-model §4 함께 태우기 전이 */
class GroupStepTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)
    private val a = Synth.all
    private val seat = "ROW_1_RIGHT"

    private fun linked(): Pickup = Group.pickup().also { p ->
        Pick.step(a, p, Event.Gear(true), 0)
        Pick.step(a, p, Event.CarPos(at(0.0, -2000.0)), 0)
    }

    @Test fun choosesOnlyAfterEveryoneAnswered() {
        val p = linked()
        Pick.step(a, p, Group.remote(0, at(-10.0, 0.0), status = "link"), 0)
        assertEquals(Status.LINK, p.status)
        Pick.step(a, p, Group.remote(1, at(20.0, 40.0), status = "link"), 0)
        assertEquals(Status.SPOTS, p.status)
        assertEquals("east", p.spots.first().landmark.id)
        assertEquals(2, p.spots.first().plans.size)
    }

    @Test fun declinerLeavesAndTheOthersAreChosen() {
        val p = linked()
        val out = Pick.step(a, p, Group.remote(1, status = "link", consent = false), 0)
        assertTrue(Action.EndMember(1) in out)
        assertTrue(p.members[1].out)
        Pick.step(a, p, Group.remote(0, at(-10.0, 0.0), status = "link"), 0)
        assertEquals(Status.SPOTS, p.status)
        assertNull(p.spots.first().plans[1])
    }

    @Test fun everyoneDeclinedStaysOnTheLink() {
        val p = linked()
        Pick.step(a, p, Group.remote(0, status = "link", consent = false), 0)
        Pick.step(a, p, Group.remote(1, status = "link", consent = false), 0)
        assertEquals(Status.LINK, p.status)
        assertTrue(p.spots.isEmpty())
    }

    /** 지점 앞 (0, -5)에 와서 6초 서 있다. 조수석은 처음에 비어 있었다 */
    private fun stopped(p: Pickup): Long {
        p.emptySeatsAtStart = setOf(seat)
        drive(a, p, 10.0, XY(0.0, -305.0), XY(0.0, -5.0), t0 = 100_000)
        val t = 130_000L
        for (k in 1..6) Pick.step(a, p, Event.CarPos(at(0.0, -5.0)), t + k * 1_000)
        return t + 6_000
    }

    @Test fun boardingCountsTheLargerOfSeatsAndPages() {
        val p = Group.going()
        val t = stopped(p)
        listOf(Event.Door(seat, true), Event.Door(seat, false), Event.Seat(seat, true)).forEach { Pick.step(a, p, it, t + 100) }
        assertEquals(Status.GOING, p.status) // 좌석 1 < 2명
        Pick.step(a, p, Group.remote(0, boarded = true), t + 200)
        assertEquals(Status.GOING, p.status) // 앉은 사람과 누른 사람이 같을 수 있다: 큰 쪽 1
        assertEquals(listOf(Action.Speak("민지님, 준호님이 탔어요"), Action.ClearNavi, Action.Push, Action.End),
            Pick.step(a, p, Group.remote(1, boarded = true), t + 300))
        assertEquals(Status.MET, p.status)
        assertNotNull(p.metAt)
    }

    @Test fun waypointModeSaysEveryoneIsAboard() {
        val p = Group.going()
        p.nextDest = "집"
        Pick.step(a, p, Group.remote(0, boarded = true), 1_000)
        assertTrue(Action.Speak("모두 탑승 확인. 다음 목적지로 안내할게요") in Pick.step(a, p, Group.remote(1, boarded = true), 2_000))
    }

    @Test fun leavingWhileGoingIsSaidAndTheLastOneEndsThePickup() {
        val p = Group.going()
        assertEquals(listOf(Action.Speak("준호님은 이번에 함께 타지 않아요"), Action.Push), Pick.step(a, p, Group.remote(1, status = "stopped"), 1_000))
        assertEquals(Status.GOING, p.status)
        val last = Pick.step(a, p, Group.remote(0, status = "stopped"), 2_000)
        assertEquals(Status.STOPPED, p.status)
        assertTrue(Action.ClearNavi in last && Action.End in last)
    }

    @Test fun goneRecordLeavesOnlyThatPerson() {
        val p = Group.going()
        assertTrue(Action.Speak("준호님은 이번에 함께 타지 않아요") in Pick.step(a, p, Event.Gone(1), 1_000))
        assertEquals(Status.GOING, p.status)
        assertFalse(p.members[0].out)
    }

    @Test fun arriveQuestionNamesEveryoneLeft() {
        val p = Group.going()
        drive(a, p, 10.0, XY(0.0, -330.0), XY(0.0, -30.0), t0 = 100_000)
        Pick.step(a, p, Event.TurnSignal(true), 131_000)
        val said = (1..4).flatMap { k -> Pick.step(a, p, Event.CarPos(at(0.0, -30.0)), 131_000L + k * 1_000) }
        assertTrue(Action.Speak("민지님, 준호님께 도착했다고 알릴까요?") in said)
    }

    @Test fun secondCallWaitsSixtySecondsAfterTheFirst() {
        val p = Group.going()
        val acts = drive(a, p, 10.0, XY(0.0, -2000.0), XY(0.0, -20.0)) +
            (200..260).flatMap { s -> Pick.step(a, p, Event.Tick, s * 1_000L).map { s * 1_000L to it } }
        val calls = acts.filter { it.second is Action.CallOrNotify }
        assertEquals(listOf(0, 1), calls.map { (it.second as Action.CallOrNotify).who })
        assertTrue(calls[1].first - calls[0].first >= 60_000)
        assertEquals(calls[1].first, p.lastCallAt)
    }
}

/** data-model §4 대중교통으로 모이기(FR-056·057) */
class GroupTransitTest {
    private fun transitGoing(): Pickup = Group.demo("group-transit").also { p ->
        Pick.step(DEMO, p, Event.Confirm(0), 0)
        Pick.step(DEMO, p, Event.Gear(false), 0)
    }

    @Test fun bothAcceptedSaysOneLine() {
        val p = transitGoing()
        assertTrue(Pick.step(DEMO, p, Group.remote(0, transit = "accept"), 1_000).none { it is Action.Speak })
        assertTrue(p.members[0].transitAccepted)
        assertEquals(listOf(Action.Speak("민지님, 준호님이 1호선 타고 화서역으로 와요")),
            Pick.step(DEMO, p, Group.remote(1, transit = "accept"), 2_000).filterIsInstance<Action.Speak>())
        assertTrue(Pick.step(DEMO, p, Group.remote(1, transit = "accept"), 3_000).none { it is Action.Speak })
    }

    @Test fun ridersWaitForTwoMinutesAndMovingIsNoAnswer() {
        val p = transitGoing()
        Pick.step(DEMO, p, Event.CarPos(Scenes.point("group-transit", "car")), 1_000)
        assertTrue(p.members.all { it.notifiedAt == null }) // 12분 남음. 걷는 사람 문턱(13분 + 1분)이면 벌써 알렸다
        Pick.step(DEMO, p, Event.CarPos(Scenes.route("group-transit").last()), 10_000)
        assertTrue(p.members.all { it.notifiedAt == 10_000L })
        Pick.step(DEMO, p, Group.remote(0, Scenes.values("group-transit", "rail")[1].let { LatLon(it[0].toDouble(), it[1].toDouble()) }), 20_000)
        assertTrue(Pick.step(DEMO, p, Event.Tick, 70_000).any { it is Action.CallOrNotify && it.who == 0 })
    }
}

/** data-model §7 연락 줄 상태(FR-065) */
class ContactTest {
    private fun m() = Member(Person("민지", "01012345678", false), "id", "key")
    private val now = 100_000L

    @Test fun firstMatchingRowWins() {
        assertEquals(Contact(ContactKind.WAIT, null), contact(m(), null, null, false, now))
        assertEquals(Contact(ContactKind.NOTIFIED, 30), contact(m().apply { notifiedAt = 70_000 }, null, null, false, now))
        assertEquals(Contact(ContactKind.NOTIFIED, 0), contact(m().apply { notifiedAt = 10_000 }, null, null, false, now))
        val rung = m().apply { notifiedAt = 10_000; called = true; callAt = 70_000; phoned = true }
        assertEquals(Contact(ContactKind.CALLED, 30), contact(rung, null, null, true, now))
        assertEquals(Contact(ContactKind.CALLING, 5), contact(rung, true, 95_000, true, now))
        assertEquals(ContactKind.CALLED, contact(rung, true, 95_000, false, now).kind) // 통화는 다른 사람과
        assertEquals(ContactKind.CALLED, contact(rung, true, 100_000 + 600_000, true, now + 600_000).kind) // 10분 뒤 걸려 온 다른 전화
        assertEquals(ContactKind.PHONE_NOTIFIED, contact(m().apply { notifiedAt = 1; called = true; phoned = false }, null, null, true, now).kind)
        assertEquals(ContactKind.MOVING, contact(m().apply { notifiedAt = 1; called = true }, null, null, false, now).kind)
        assertEquals(ContactKind.READY, contact(m().apply { called = true }, null, null, false, now).kind) // 문턱에서 위치로 나와 있음
        assertEquals(ContactKind.READY, contact(m().apply { notifiedAt = 1; passenger.ready = true }, null, null, false, now).kind)
        assertEquals(ContactKind.ACK, contact(m().apply { notifiedAt = 1; called = true; passenger.ack = true }, null, null, false, now).kind)
        assertEquals(ContactKind.ABOARD, contact(m().apply { aboard = true; passenger.ready = true }, null, null, false, now).kind)
        assertEquals(ContactKind.OUT, contact(m().apply { out = true; aboard = true }, null, null, false, now).kind)
    }
}

/** data-model §8 지도 순수 계산(SC-016) */
class MapTest {
    @Test fun edgeKeepsTheDirection() {
        assertNull(edge(10.0, 10.0, 100.0, 50.0))
        assertEquals(100.0 to 0.0, edge(300.0, 0.0, 100.0, 50.0))
        assertEquals(50.0 to 50.0, edge(200.0, 200.0, 100.0, 50.0))
        assertEquals(-25.0 to -50.0, edge(-100.0, -200.0, 100.0, 50.0))
    }

    @Test fun labelsTakeTheFirstFreeSpot() {
        val pinLabel = Box(40f, 0f, 160f, 20f)
        val above = Box(90f, 5f, 130f, 25f) // 지점 이름표와 겹친다
        val below = Box(90f, 60f, 130f, 80f)
        assertEquals(1, firstFree(listOf(above, below), listOf(pinLabel), 200f, 100f))
        assertEquals(0, firstFree(listOf(below, above), listOf(pinLabel), 200f, 100f))
        assertEquals(1, firstFree(listOf(Box(180f, 60f, 220f, 80f), below), emptyList(), 200f, 100f)) // 지도 밖은 건너뛴다
        assertEquals(0, firstFree(listOf(above), listOf(pinLabel), 200f, 100f)) // 빈 자리가 없으면 첫 자리
        assertFalse(Box(0f, 0f, 10f, 10f).hits(Box(10f, 0f, 20f, 10f))) // 맞닿기만 하면 겹침이 아니다
    }

    @Test fun distanceLabels() {
        assertEquals("350m", distLabel(349.0))
        assertEquals("1.9km", distLabel(1940.0))
        assertEquals("1.0km", distLabel(999.0))
    }

    @Test fun everyDemoSpotIsWithin25mOfItsRoad() {
        val spots = DEMO.landmarks.filter { it.spot }
        assertEquals(10, spots.size)
        spots.forEach { assertTrue(it.id, project(DEMO.center, DEMO.road(it.roadId), it.pos).distance <= 25.0) }
    }
}

/** data-model §4 끝: 남은 거리 막대와 만남 요약(FR-069·070) */
class ProgressTest {
    private fun at(x: Double, y: Double) = Synth.at(x, y)

    @Test fun barGrowsFromTheConfirmedDistance() {
        val p = Synth.going()
        assertEquals(1900 * 1.3 + 100, p.startM!!, 1.0)
        assertEquals(0L, p.confirmedAt)
        assertEquals(0.0, Pick.progress(Synth.o, p)!!, 1e-3)
        Pick.step(Synth.all, p, Event.CarPos(at(0.0, -1050.0)), 10_000)
        assertEquals(1 - (950 * 1.3 + 100) / (1900 * 1.3 + 100), Pick.progress(Synth.o, p)!!, 0.01)
        Pick.step(Synth.all, p, Event.CarPos(at(0.0, -90.0)), 20_000) // 경유지 지남
        Pick.step(Synth.all, p, Event.CarPos(at(0.0, -50.0)), 25_000)
        assertEquals(1 - dist(Synth.o, at(0.0, -50.0), Synth.east.pos) * 1.3 / (1900 * 1.3 + 100), Pick.progress(Synth.o, p)!!, 0.01)
    }

    @Test fun noBarWithoutACarPositionAtConfirm() {
        val p = Synth.minji()
        Pick.step(Synth.all, p, Event.Gear(true), 0)
        assertNull(Pick.progress(Synth.o, p))
    }
}
