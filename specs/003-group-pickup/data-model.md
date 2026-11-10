# Data Model: 같은편 3차

1·2차 data-model(§1~§9, 2차 §1~§8)에 더한다. 표기는 `Pick.kt` 그대로다. 시간은 `elapsedRealtime` ms, 거리는 m다.

## §1 Member와 Pickup

`Pickup`의 값 가운데 사람마다 다른 것을 `Member`로 옮긴다. 옮긴 값의 뜻은 1·2차와 같다.

```kotlin
class Member(val person: Person, val id: String, val key: String) {
    val passenger = Passenger()
    var offer: Offer? = null          // 한 명 픽업의 대중교통 안(1차)
    var transitAccepted = false       // 한 명: 안을 받아들임 / 함께: 안내에 "알겠어요"
    var walkSec: Double? = null
    var called = false
    var readySpoken = false
    var lateSpoken = false
    var notifiedAt: Long? = null
    var notifiedPos: LatLon? = null
    var outNow = false
    var wrongSide = false
    // 3차
    var callAt: Long? = null          // 전화(또는 대체 알림)를 정한 때. Pick이 정한다
    var phoned: Boolean? = null       // 서비스가 실제로 한 일: true = 전화, false = 폰으로 알림
    var aboard = false                // 페이지 "탔어요"
    var out = false                   // 빠짐(FR-059)
}

class Pickup(val members: List<Member>, now: Long) {
    constructor(id: String, key: String, person: Person, now: Long) : this(listOf(Member(person, id, key)), now)
    val group get() = members.size > 1
    val lead get() = members[0]
    val active get() = members.filter { !it.out }
    // 한 명 픽업 코드·화면이 쓰던 이름(첫 사람)
    val id get() = lead.id
    val key get() = lead.key
    val person get() = lead.person
    val passenger get() = lead.passenger
    // 차 쪽 값은 1·2차 그대로(status, expireAt, track, park, spots, chosen, etaSec, heading, passedWaypoint,
    // spoke150, spokenWhileDriving, crossOver, lastPushAt, emptySeatsAtStart, openDoors, doorCycledNearSpot,
    // takenSeats, reached, signalRight, arriveAsked, arriveCard, arrived, nextDest, lostDest)
    // 3차
    var lastCallAt: Long? = null      // 함께: 전화 간격(FR-058)
    var transitSpoken = false         // 함께: 대중교통 한 줄(FR-057)
    var confirmedAt: Long? = null     // 확정 시각(만남 요약)
    var startM: Double? = null        // 확정 때 남은 거리(막대)
    var metAt: Long? = null
    var nearbySec: Double? = null     // 고를 때 1순위 기준으로 센 근처 어림(비교 줄이 있을 때만, §3). 확정 때 1순위가 아니면 지운다
}
```

- 1·2차 테스트가 쓰는 `p.offer`, `p.called` 같은 사람 값은 `p.lead.…`로 이름만 고친다. 값과 판단은 같다.
- 사람 수는 1~3이다(`Rules.MAX_GROUP = 3`).

## §2 Plan과 함께 고르기

```kotlin
/** 한 사람이 한 지점까지 가는 방법. leg가 null이면 걷기 */
data class Plan(val leg: TransitLeg?, val walkM: Double, val arriveSec: Double, val crossing: Int, val standLine: String)
```

`Spot`에 `plans: List<Plan?> = emptyList()`를 더한다. 함께 태우기에서만 차고, `members`와 같은 순서다. 빠진 사람은 null이다.

### 2-1 같은 편 후보(`side`)

`Pick.choose`의 사람과 무관한 부분을 `side(area, car, l): Spot?`로 떼어 낸다. 한 명 고르기도 이것을 쓴다(결과는 1·2차와 같아야 한다, 기존 테스트).
- 애매한 길가(3m), 일방통행 반대편, 정차 금지, 유턴(50m 넘게 떨어졌고 방위 차 100° 초과)이면 null
- 아니면 들어오는 방향, 진입 경유지, 차 시간, 그림용 길, 유턴 시간, 기준 랜드마크를 채운 `Spot`이다. 사람 값(걷기·건너기·서 있을 곳)은 비어 있다.

### 2-2 사람별 가는 방법(`plan`)

`plan(area, s, pos, careful): Plan?`
1. 걷기: `dist(pos, s.pos) × 1.3 ≤ 한도`(300m, 가까이만 150m)
   - `walk(area, pos, s)`로 시간과 건너기를 구한다(1차 그대로).
   - 서 있을 곳 문장은 1·2차 규칙이다. pos가 기준 랜드마크보다 차가 오는 쪽이면 "○○을 지나 △△ 앞 인도에 서 계세요"다.
2. 대중교통: 걷기가 안 되면, 구간 t마다 아래가 둘 다 한도 안인 것을 본다.
   - `d1 = dist(pos, t.board.pos) × 1.3`
   - `d2 = dist(t.alight.pos, s.pos) × 1.3`
   - 도착 = `d1 / 67 × 60 + t.waitSec + t.rideSec + walk(area, t.alight.pos, s).first`
   - 건너기는 내린 정류장 기준이다. `walkM = d1 + d2`이고, 서 있을 곳 문장은 내린 정류장 기준이다.
   - 가장 빠른 구간을 쓴다.
3. 둘 다 안 되면 null

### 2-3 함께 고르기(`chooseGroup`)

`chooseGroup(area, car, people: List<Pair<LatLon, Boolean>?>): List<Spot>` (null = 빠진 사람, Boolean = 가까이만)
1. 지점 랜드마크마다 `side`가 null이 아니고, 빠지지 않은 모든 사람의 `plan`이 null이 아닌 것만 남긴다.
2. `arrive = max(plan.arriveSec)`, `meetSec = max(carSec, arrive)`
3. 결과 지점의 `walkSec = arrive`, `walkM = Σ plan.walkM`, `crossing = Σ plan.crossing`, `plans`를 채운다.
4. 이유 줄
   - 모두 걸으면 "차가 오는 쪽 · 모두 걸어서 · 세워도 되는 곳"
   - 누가 대중교통을 타면 "차가 오는 쪽 · ○호선으로 모여서 · 세워도 되는 곳"(구간이 여럿이면 "대중교통으로 모여서")
5. 정렬 순서(FR-052)
   1. `ceil((meetSec + (riders > 0 ? 300 : 0)) / 60)`
   2. `riders`
   3. `walkM`
   4. `crossing`
6. 3개까지 고른다.
- 시연 값(R20): `group-transit` 1순위 화서역 약국(둘 다 1호선), `group` 1순위 세븐일레븐(둘 다 걷기)

## §3 근처로 데리러 가기 어림(`nearby`)

`nearby(area, car, people): Double?` — FR-055
1. 빠지지 않은 사람마다 `choose(area, car, pos, careful).firstOrNull()`(한 명 고르기 1순위)을 구한다. 하나라도 없으면 null이다.
2. 같은 랜드마크는 한 번만 남긴다. 차에서 직선거리가 가까운 차례로 줄 세운다.
3. 차 → 첫 지점 → 다음 지점… 직선 × 1.3 / (25km/h)를 더하고, 들를 때마다 60초를 더한다.

link에서 고를 때(그리고 확정 전 다시 고를 때) 한 번 센다. `chooseGroup` 결과 1순위에 대중교통 탈 사람이 있고 `nearby − meetSec > 60`이면 `p.nearbySec = nearby`이고, 1순위 카드에 "근처로 데리러 가면 약 ${minutes(nearby)}분"을 보인다. 아니면 null이고 보이지 않는다. 시연 값: 20.6분 → "약 21분"(R20).

## §4 전이 바뀜(`Pick.step`)

한 명 픽업(`!p.group`)은 1·2차 그대로다. 아래는 함께 태우기에만 해당한다. `who`는 사람 순서다.

| 이벤트 | 함께 태우기 |
|---|---|
| `Remote(who)` 끝 상태 | 그 사람 `out = true`. 가는 중이면 "○○님은 이번에 함께 타지 않아요"(주행 중 상한 안). 남은 사람이 없으면 그 끝 상태로 픽업을 끝낸다. 남은 사람이 모두 탔으면 만남 |
| `Remote(who)` boarded | 그 사람 `aboard = true`, 탑승 판정 |
| `Remote(who)` 그 밖 | 그 사람 `passenger` 갱신. link면 고르기를 시도한다. 가는 중이면 걷기면 걷는 시간·건너기 갱신, 나와 있어요·늦어요 한 줄(사람 이름) |
| `Remote(who)` transit=accept | 가는 중이고 그 사람의 고른 지점 `plan.leg != null`이면 `transitAccepted = true`, Push. 대중교통 탈 사람(빠지지 않음)이 모두 받아들였고 `!transitSpoken`이면 한 줄(FR-057) |
| link에서 고르기 | 모든 사람이 응답했을 때만 고른다. 응답은 동의 + 위치, 또는 거절이다. 동의가 1명 이상이고 차 위치가 있어야 한다. 거절한 사람은 `out = true`, `EndMember(i)`. 결과가 없으면 NOSPOT(함께 태우기는 `nospotEta`를 쓰지 않는다) |
| `Confirm(i)` | 1·2차 조건 + 사람마다 `walkSec = plan.leg == null ? plan.arriveSec : null`. `confirmedAt`, `startM`(아래)을 적는다. i가 0이 아니면 `nearbySec = null` |
| `Gone(who)` | 그 사람 기록이 서버에 없다(404). 한 명 픽업은 1·2차 그대로 EXPIRED로 끝. 함께는 그 사람만 `out`(빠짐 문장은 끝 상태와 같다). 남은 사람이 없으면 EXPIRED로 끝 |
| 확정 전 빠짐 | spots·nospot에서 누가 빠지면 남은 사람으로 `chooseGroup`을 다시 한다(그때 `nearbySec`도 다시 센다). 남은 사람이 없으면 1차 거절 화면(link) |
| 탄 사람 빼기 | `aboard`인 사람은 알림, 전화, 건너편 경고, 도착 확인 질문 이름, 150m 건너오기 문장 이름에서 뺀다 |
| 가는 중 위치 | 1·2차 공용 판단(남은 시간, 도착, 150m, 도착 확인)에 더해, 사람마다 알림·전화·건너편 경고를 본다. 대중교통 탈 사람(`plan.leg != null` 또는 `transitAccepted`)의 문턱은 2분이고 이동을 반응으로 보지 않는다 |
| 전화 간격 | `callIfNoAnswer(m)`는 `p.lastCallAt`에서 60초가 안 지났으면 미룬다(다음 Tick·위치에서 다시 본다). 걸면 `p.lastCallAt = now`, `m.callAt = now`, 할 일은 `CallOrNotify(name, phone, speak, who)`(`who` 기본 0) |
| 탑승 판정 | `seats = (doorCycledNearSpot && stopped) ? takenSeats.size : 0`, `aboardN = max(seats, active.count { it.aboard })`. `aboardN >= active.size`면 만남 |
| 만남 | 남은 사람 이름을 묶어 "민지님, 준호님이 탔어요". 경유지 방식이면 "모두 탑승 확인. 다음 목적지로 안내할게요". `metAt = now` |
| 도착 확인 질문 | 남은 사람 이름을 묶어 "민지님, 준호님께 도착했다고 알릴까요?" |
| 150m 건너오기 문장 | 이름을 묶어 "민지님, 준호님이 길을 건너 오른쪽으로 와요" |

- 이름 묶기: `names(list) = list.joinToString(", ") { "${it.person.name}님" }`
- 한 명이면 "민지님"이다. 한 명 픽업의 문장과 같다.
- 한 명 픽업에도 3차 값을 적는다: `callAt`, `confirmedAt`, `startM`, `metAt`. 화면 표현에만 쓴다.
- `startM`: 확정 때 차 위치가 있으면 `remainingM = dist(car, s.waypoint) × 1.3 + 100`, 없으면 null
- 남은 거리 막대: `1 − remainingM(지금) / startM`을 0~1로 자른다. 경유지를 지난 뒤 `remainingM`은 `dist(car, s.pos) × 1.3`이다.

새 할 일: `Action.EndMember(index)`. 서비스가 그 사람 기록에 `{"status":"canceled"}`를 올린다(받을 때까지 끝 상태 재전송과 같은 방식).

## §5 올리는 값(사람마다)

`Relay.carJson(p, m, car, calling: Boolean)`. `calling`은 서비스가 §9로 정해 넘긴다(시각과 통화 상태는 서비스가 든다). 1·2차 필드는 그대로이고, 사람 값은 m의 것이다. 함께 태우기에서는 아래가 다르다.

| 필드 | 함께 태우기 |
|---|---|
| `name` | m의 이름 |
| `walkSec` | 가는 중이고 m이 걸을 때 m의 값 |
| `spots[].walkSec`·`meetSec`·`standLine` | 그 지점의 m의 `plan`(걷기 시간 또는 도착 시간, m 기준 서 있을 곳) |
| `spots[].fasterMin` | m의 plan이 걷기이고 `plan.crossing == 1`일 때만 2차 식(건너기 1분 대 유턴)으로 센 값. 아니면 null |
| `offer` | null |
| `plan`(새) | 가는 중 m의 고른 지점 plan. `{"mode":"walk"}` 또는 `{"mode":"transit","line":"1호선","stops":1,"alightName":"화서역","min":12}`. 그 밖에는 null |
| `group`(새) | m을 뺀 다른 사람: `[{"name":"준호","mode":"walk"|"1호선","aboard":false,"out":false}]`. 위치는 없다. `mode`는 확정 전이면 1순위 지점(`spots[0]`)의 plan, 확정 뒤면 고른 지점의 plan이다. 지점이 없으면 `""`. 한 명 픽업은 `[]` |
| `calling`(새) | 인자 그대로(§9) |
| `outNow`·`wrongSide` | m의 값 |

빠진 사람에게는 올리지 않는다(기록은 `EndMember`로 끝낸다).

## §6 시연 좌표(`scripts/scenes.txt`)

줄 형식은 1·2차와 같다: `장면 키 위도 경도`. 같은 키가 여러 줄이면 차례로 이은 점이다.

```text
group car 37.2935193 126.9700141
group route (opposite route와 같은 점 14개)
group p1 37.2998781 126.9722054
group p2 37.2991996 126.9727588

group-transit car 37.2570158 127.0198273
group-transit fast 37.2570158 127.0198273
group-transit fast 37.2814208 126.9918616
group-transit route 37.2814208 126.9918616
group-transit route 37.2816065 126.9917507
group-transit route 37.2843922 126.9901193
group-transit route 37.2844699 126.9900728
group-transit route 37.2846100 126.9899900
group-transit p1 37.2998781 126.9722054
group-transit p2 37.2991996 126.9727588
group-transit rail 37.3002852 126.9711778
group-transit rail 37.2921000 126.9804000
group-transit rail 37.2839144 126.9896135
group-transit meet1 37.2846600 126.9901500
group-transit meet2 37.2846100 126.9901600
```

- `meet1`·`meet2`는 약국 앞 동쪽 인도다. 지점(약국 길가 점)에서 15m 안이고, 지점과 같은 길가(오른쪽)다.
- `group-transit` 사람 움직임은 시연 스크립트가 아래 시각에 서버 API로 넣는다.
  1. 확정 뒤 두 사람이 "알겠어요"(`transit: accept`)를 누르고, 위치를 `rail` 첫 점(성균관대역)으로 옮긴다.
  2. 시속 80km 구간의 1/3 지점에서 `rail` 둘째 점, 2/3 지점에서 셋째 점(화서역)으로 옮긴다.
  3. 시속 30km 구간을 시작할 때 `meet1`·`meet2`로 옮기고 `ready`를 누른다.
  4. 그 전이라도 서버 `outNow`가 켜진 사람은 바로 `ready`를 누른다(2차 `ack` 장면과 같은 방식). 그래서 전화가 걸리지 않고 음성 상한에 여유가 남는다.

## §7 연락 줄 상태(`ContactRow`, FR-065)

가는 중 사람마다 위에서부터 처음 맞는 것이다.

| 순서 | 조건 | 글자 | 효과 |
|---|---|---|---|
| 1 | `out` | "함께 안 타요" | 없음, 회색 |
| 2 | `aboard` | "탔어요" | 체크, 초록 |
| 3 | 가장 최근에 전화함, `inCallWith`(phoned, 통화 중, 그 통화가 건 뒤 30초 안에 시작) | "○○님과 통화 중 · m:ss"(통화 시작부터) | 퍼지는 초록 물결 |
| 4 | `passenger.ready`, 또는 `called && notifiedAt == null && phoned == null`(문턱에서 위치로 나와 있다고 봄) | "나와 있어요" | 체크, 초록 |
| 5 | `passenger.ack` | "지금 나간대요" | 체크, 초록 |
| 5-1 | `notifiedAt != null && called && phoned == null`(알림 뒤 20m 넘게 움직여 전화하지 않음) | "나오고 있어요" | 걷기, 초록 |
| 6 | `phoned == true` | "전화를 걸었어요 · n초 전" | 전화, 초록 테두리 |
| 7 | `phoned == false` | "○○님 폰으로 알렸어요" | 종, 호박 |
| 8 | `notifiedAt != null` | "지금 나오세요를 보냈어요" + "n초 뒤 전화"(n = 60 − 경과, 0 아래면 "곧 전화") | 줄어드는 호박 링 |
| 9 | 그 밖 | "알림 전" | 회색 |

통화 중 여부와 시작 시각은 서비스가 `isInCall`로 갖는다(R22). 효과 위상은 `uptimeMillis` 기준이다(R25).

`Contact` 판정은 순수 함수다.

```kotlin
enum class ContactKind { OUT, ABOARD, CALLING, READY, ACK, MOVING, CALLED, PHONE_NOTIFIED, NOTIFIED, WAIT }
data class Contact(val kind: ContactKind, val sec: Int?) // sec: CALLING은 통화 경과, CALLED는 건 뒤 경과, NOTIFIED는 전화까지 남은 초(0 아래면 0)
fun contact(m: Member, inCall: Boolean?, callSince: Long?, latest: Boolean, now: Long): Contact
```

- `latest`: m이 픽업에서 가장 최근에 전화한 사람(`callAt`이 가장 큼)
- `inCall == null`: 통화 상태를 볼 수 없음(권한 없음) → CALLING은 나오지 않는다.

## §8 지도 계산(`SpotMap`, 순수 부분은 `Views.kt` 최상위 함수)

- 범위: 지점을 가운데에 두고 짧은 변의 반이 140m(카드), 200m(가는 중)다. 긴 변은 화면 비율대로 늘린다.
  - 그래서 들어오는 화살표(지점 뒤 70m)는 언제나 안에 있다.
- 지도 밖 점: 안쪽 여백 22dp의 사각형 위에, 지점에서 그 점으로 가는 선이 만나는 곳에 화살표와 글을 그린다.
  - `fun edge(dx: Double, dy: Double, halfW: Double, halfH: Double): Pair<Double, Double>?`
    - m 평면 좌표(지점 원점)를 받는다. 반폭·반높이는 여백을 뺀 m 값이다.
    - 안이면 null이다. 밖이면 사각형 위 점이다.
- 거리 글: `fun distLabel(m: Double): String`. 1000m 미만은 10m 단위 반올림("350m"), 이상은 소수 한 자리("1.9km")다. 대중교통 사람은 "민지 · 1호선"이다.

## §9 서비스 처리(PickupService, 사람마다 기록)

- 조회: poll 한 번에 io 작업 하나(`getting` 하나)가 빠지지 않은 사람 기록을 차례로 GET하고, 결과 묶음을 메인에 넘긴다.
  - 결과마다 `Remote(who)`를 넣는다. 404는 `Gone(who)`, 연결 실패는 서버 끊김 띠(1차)다.
  - 그다음 `Tick` 한 번이다.
- 올리기: `dirty`·`putting` 하나다. io 작업 하나가 사람마다 `carJson(p, m, car, calling(m))`을 차례로 PUT한다.
  - 409를 받으면 그 사람 기록만 다시 GET해 `Remote(who)`로 넣는다.
  - 404는 `Gone(who)`, 실패는 `dirty`(다음 poll에 다시)다.
- 끝: `End`는 빠지지 않은 모든 사람 기록에 끝 상태를 보낸다. `EndMember(i)`는 그 사람에게 `canceled`를 보낸다.
  - 재전송 큐는 `(id, key, json)` 항목이다. 204·409·404를 받으면 지운다.
- 만들기: `beginGroup`은 사람마다 POST한다. 성공한 사람은 그대로 두고, 실패한 사람만 2초 뒤 다시 보낸다. 모두 만들어지면 `Pickup(members)`다.
- `phoned`: `CallOrNotify(…, who)`를 실행할 때 정한다. CALL_PHONE 권한이 있으면 true(전화 시도), 없으면 false(대체 알림)다.
- `calling(m)`: `m.phoned == true && (now − m.callAt < 60s || (m이 latest && inCallWith(m)))`. `inCallWith`는 건 뒤 30초 안에 시작된 통화만 본다(구현 중 바뀜, approval-gate)
- 다시 올리는 때(Push + 화면 갱신)
  - `isInCall` 값이 바뀔 때
  - 가장 최근 `callAt` + 60초에 한 번(`main.postDelayed`)
