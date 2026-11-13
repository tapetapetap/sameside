package kr.skku.sameside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 6차 주행 중 한 줄 카드(specs/006-car-screen contracts §1): 상태마다 남은 분과 한 줄 */
class GlanceTest {
    private val ready: (Member) -> Contact = { Contact(ContactKind.READY, null) }
    private fun one() = Group.pickup(Group.minji)
    private fun line(p: Pickup?, contact: (Member) -> Contact = ready, creating: Boolean = false) = glance(p, creating, contact).line

    @Test fun noPickup() {
        assertEquals(Glance(null, "운전 중이에요 · 픽업은 차를 세우고 시작해요"), glance(null, false, ready))
        assertEquals("링크를 만들고 있어요", line(null, creating = true))
    }

    @Test fun linkLinesForOnePerson() {
        val p = one()
        assertEquals("민지님이 링크를 열면 만날 곳을 골라요", line(p))
        p.passenger.consent = false
        assertEquals("민지님이 위치를 공유하지 않았어요", line(p))
        p.passenger.consent = true
        assertEquals("민지님 위치를 기다리고 있어요", line(p))
        p.passenger.pos = LatLon(37.29, 126.97)
        assertEquals("차 위치를 확인하고 있어요", line(p))
        assertEquals("함께 데리러 가요 · 위치를 기다려요", line(Group.pickup()))
    }

    @Test fun spotsAndNoSpot() {
        val p = Chained.spots()
        assertEquals("만날 곳 ${p.spots.size}곳 · 차를 세우면 골라요", line(p))
        p.spots = emptyList() // 함께 만날 곳은 없고 차례 계획만(ChainTest planWithoutAMeetingPlaceIsStillSpots)
        assertEquals("차례로 태우기 · 차를 세우면 골라요", line(p))
        assertEquals("가까운 곳에 같은 편 자리가 없어요", line(one().apply { status = Status.NOSPOT }))
        assertEquals("함께 만날 곳이 없어요", line(Group.pickup().apply { status = Status.NOSPOT }))
    }

    @Test fun goingShowsMinutesAndNextPersonWithoutSeconds() {
        val p = one().apply { status = Status.GOING; etaSec = 230.0 }
        assertEquals(Glance(4, "민지님 · 나와 있어요"), glance(p, false, ready))
        assertEquals("민지님 · 통화 중", line(p, { Contact(ContactKind.CALLING, 12) }))
        assertEquals("민지님 · 나오라고 알렸어요", line(p, { Contact(ContactKind.NOTIFIED, 37) }))
        assertEquals("민지님 · 전화를 걸었어요", line(p, { Contact(ContactKind.CALLED, 5) }))
        assertEquals("민지님 · 가까워지면 알려요", line(p, { Contact(ContactKind.WAIT, null) }))
        p.etaSec = null
        assertNull(glance(p, false, ready).minutes) // 남은 시간을 아직 모르면 "0분"이 아니라 숨김
    }

    @Test fun chainShowsPersonAtCurrentStop() {
        val p = Chained.going()
        val first = p.active.first { it.stop == p.chosen }
        first.aboard = true
        p.chosen = p.active.first { !it.aboard }.stop // 다음 지점으로 넘어간 뒤
        assertEquals("${p.active.first { !it.aboard }.person.name}님 · 나와 있어요", line(p))
    }

    @Test fun allAboardHasNoMinutes() {
        val p = Group.pickup().apply { status = Status.GOING; etaSec = 60.0; members.forEach { it.aboard = true } }
        assertEquals(Glance(null, "모두 탔어요"), glance(p, false, ready))
    }

    @Test fun endedStates() {
        assertEquals("만났어요", line(one().apply { status = Status.MET }))
        assertEquals("픽업을 취소했어요", line(one().apply { status = Status.CANCELED }))
        assertEquals("시간이 지나 위치 공유를 끝냈어요", line(one().apply { status = Status.EXPIRED }))
        assertEquals("민지님이 위치 공유를 껐어요", line(one().apply { status = Status.STOPPED }))
        val g = Group.pickup().apply { status = Status.STOPPED; lastLeft = members[1] }
        assertEquals("준호님이 위치 공유를 껐어요", line(g))
        assertNull(glance(g, false, ready).minutes)
    }
}
