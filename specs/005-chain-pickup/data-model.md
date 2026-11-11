# Data Model: 같은편 5차

1~4차 data-model 위에 더한다. 순수 계산은 `Pick` 객체(Pick.kt)와 Views.kt 최상위 함수에 두고, 새 `ChainTest.kt`가 지킨다(원칙 VI). 좌표 계산은 1~4차 `xy`, `dist(o, a, b)`(원점 `o` 기준 m 평면)와 같다. `o`는 `area.center`다.

## §1 차례 계획

```kotlin
data class ChainStop(val spot: Spot, val who: List<Int>) // who = p.members 번호(이 지점에서 탈 사람). Area.kt의 Stop(정류장)과 다른 이름
data class Chain(val stops: List<ChainStop>, val legs: List<Double>, val destSec: Double?)
// legs[0] = 차 → stops[0](초), legs[k] = stops[k-1] → stops[k](초, 들르는 60초 포함). destSec = 마지막 지점 → 목적지(초) 또는 null
object Pick { fun chain(area: Area, car: LatLon, people: List<Pair<LatLon, Boolean>?>, dest: LatLon?): Chain? }
```

- `people[i]`는 3차 `chooseGroup`과 같은 형식이다(위치, 가까이만). null이면 빠진 사람이다.
- 사람마다 `choose(area, car, pos, careful).firstOrNull()`. 하나라도 null이면 null이다.
- 같은 랜드마크 id끼리 한 `ChainStop`으로 합친다. 합친 뒤 지점이 2곳 미만이면 null이다.
- 차례(research R42): `at = car`에서 시작해 남은 지점 가운데 `dist(o, at, stop.spot.pos)`가 가장 작은 것을 고르고 `at`을 그 지점 `spot.pos`로 옮긴다.
- 시간(research R43). `PICK_MPS`는 지금 `Pick` 안 상수, 새 `ROAD_MPS = Rules.ROAD_KMH / 3.6`도 `Pick` 안 상수다.
  - `legs[0] = stops[0].spot.carSec`
  - `legs[k] = dist(o, stops[k-1].spot.pos, stops[k].spot.pos) × DETOUR / PICK_MPS + STOP_SEC`
  - `destSec = dest?.let { dist(o, stops.last().spot.pos, it) × DETOUR / ROAD_MPS }`
- 화면의 분: 구간마다 `minutes(초)`(올림). 카드의 "약 n분" = `minutes(legs.sum())`(목적지 제외, 한 번 올림).

## §2 상태

- `Pickup`
  - `var chain: Chain? = null`: 고를 때 만든 계획(함께 태우기에서만). 확정 뒤에는 빈 지점을 지울 때만 바뀐다(§3).
  - `var chained = false`: 차례로 확정함
  - `var destPos: LatLon? = null`: 내비 원래 목적지(research R44)
- `Member.stop: Int = -1`: 차례에서 그 사람 지점 번호(`p.spots`와 `p.chain.stops`의 같은 번호)
- 새 이벤트
  - `Event.ConfirmChain`: 만날 곳에서 "차례로 태울게요"
  - `Event.Dest(pos: LatLon?)`: 내비 원래 목적지 좌표

## §3 전이

- **고르기**(private `chooseGroup`, 3차): 지금 계산 뒤에 `p.chain = chain(area, car, people, p.destPos)`.
  - `p.spots`가 비어도 `p.chain`이 있으면 `SPOTS`다(`NOSPOT` 아님).
  - 3차 근처 어림(`nearbySec`)은 그대로 계산한다. 화면이 계획이 있을 때 쓰지 않는다(contracts §1).
- **Dest**: 확정 전(`chained`가 아님)에만 `p.destPos = pos`. 계획이 있으면 목적지 구간(`destSec`)만 다시 센다(카드 순서·만료는 그대로, 코드 리뷰 반영). 한 명 픽업은 값만 저장한다(1~4차 지점 고르기를 건드리지 않는다). 확정 뒤에는 무시한다(research R44).
- **ConfirmChain**: `status == SPOTS && park == true && chain != null`일 때
  - `p.spots` = 차례 지점마다 `spot.copy(plans = 사람마다 그 지점 사람이면 걷기 plan, 아니면 null)`. 걷기 plan은 3차 `plan`의 걷기 경우와 같고 한도를 보지 않는다(고른 뒤 사람이 움직여도 null이나 대중교통이 되지 않게, 코드 리뷰 반영). 합친 지점에서도 페이지·알림이 그 사람 값을 쓴다.
  - `p.chosen = 0`, `p.chained = true`, `p.nearbySec = null`(만남 화면의 3차 비교 줄을 끔)
  - 사람마다 `stop` = 자기 지점 번호, `walkSec` = 그 사람 plan의 `arriveSec`
  - 나머지는 지금 `Confirm`과 같다: `etaSec = remaining(…)`, `status = GOING`, `expireAt`, `confirmedAt`, `startM`, `Action.SetNavi(spots[0])`, `Action.Push`
- **탑승**(`board`, research R46): `chained`이면
  - `need = active.count { it.stop <= chosen }`, 탄 수 = max(빈자리 수, `active.count { it.stop <= chosen && it.aboard }`)
  - 탄 수 ≥ need이면 지금 지점 사람을 모두 `aboard = true`로 두고(aboard 표시는 여기서만 한다), 다음 지점이 있으면 `advance("${names(그 지점 사람)}이 탔어요. ", 다음 지점)`, 없으면 3차 `met`
  - 다음 지점(`nextStop`): 지금 다음부터 아직 안 탄 사람이 있는 첫 지점. 먼저 와서 탄 사람의 지점은 건너뛴다(코드 리뷰 반영). 빠짐으로 지점이 비었을 때도 같다
- **advance(spoken: String, next: Int)**(research R45)
  - `chosen = next`
  - 지점 값 초기화: `passedWaypoint`, `spoke150`, `reached`, `doorCycledNearSpot`, `arriveAsked`, `arriveCard`, `arrived`, `crossOver`를 처음 값으로. `takenSeats`는 그대로(누적).
  - 다시 정함(Confirm과 같은 식): `etaSec = remaining(o, track, spots[chosen], false, now)`, `startM`, `expireAt`
  - 주행 중 음성 셈(`spokenWhileDriving`)을 0으로 돌린다. 주행 중 음성 상한(`VOICE_CAP` 5줄)은 지점마다 센다.
  - 음성: `spoken + "다음은 ${names(다음 지점 사람)}, ${spots[chosen].line}이에요"` 한 줄(contracts §4)
  - `Action.SetNavi(spots[chosen])`, `Action.Push`
- **빠짐**(`leave`, 3차): `chained`이고 이미 탄 사람이면 무시한다(탄 뒤 공유를 꺼도 차에 있다. 남은 지점까지 길어서 빠짐 음성을 내지 않는다, 코드 리뷰 반영). `chained`이고 빠진 사람이 아직 안 탔으면
  - 그 사람 지점에 남은(빠지지 않은) 사람이 없으면 `dropStop(k)`(탄 사람이 남아 있으면 지점을 두고 3차처럼 탑승으로 넘어간다): `spots`·`chain.stops`·`chain.legs`에서 그 지점을 지우고 구간을 잇는다. 뒤 사람들의 `stop`을 1씩 줄인다.
    - 가운데 지점: `legs[k] + legs[k+1] − STOP_SEC`
    - 마지막 지점: 앞 지점 → 목적지로 잇는다. `destSec = legs[k] + destSec − STOP_SEC`(목적지 구간이 없으면 null 그대로)
  - 지운 지점이 지금 지점이었으면: 남은 지점이 있으면 `advance("${이름}님은 이번에 함께 타지 않아요. ", chosen)`(번호는 그대로이고 당겨진 다음 지점이 지금 지점이 된다. aboard 표시는 하지 않는다), 없으면 3차 `met`. 3차 빠짐 음성은 이 경우 따로 말하지 않는다.
  - 그 밖에는 3차 그대로(빠짐 음성, `board`로 남은 사람이 모두 탔는지 확인)

## §4 사람별 남은 시간과 알림(`Pick.memberEta`)

```kotlin
fun Pick.memberEta(p: Pickup, i: Int): Double?
```

- 차례가 아니면 `p.etaSec`(지금과 같다).
- `m.aboard`이거나 `m.stop < chosen`: null
- `m.stop == chosen`: `p.etaSec`
- `m.stop > chosen`: `p.etaSec + Σ chain.legs[chosen+1 .. m.stop]`
- `going`(가는 중 차 위치마다)
  - 알림·전화 문턱: 사람마다 `memberEta`. null이면 그 사람은 건너뛴다.
  - 나와 있음 판정(`onCurb`, 거리)과 걷는 시간·건너기 갱신(`remoteGroup`, 3차 plans 경로): 그 사람 지점 `p.spots[m.stop]` 기준. `onCurb`가 읽는 `p.crossOver`는 `m.stop == chosen`일 때만 쓰고, 다른 지점 사람은 false로 본다.
  - 150m 안내 문장, 건너편 경고, 도착 확인: 지금 지점 사람만

## §5 노드 자리(`chainXs`, Views.kt 최상위)

```kotlin
fun chainXs(times: List<Double>, x0: Float, x1: Float, minGap: Float): List<Float>
```

- `times`: 계획한 구간 시간(출발 → 첫 노드, …, 마지막 노드 → 목적지). 가는 중에도 계획 값으로 그린다(노드가 움직이지 않게). 노드 수 = `times.size + 1`
- 시간 비례 자리 `x0 + (x1 − x0) × 누적 / 합`. 합이 0이면 같은 간격이다.
- 왼쪽부터 보며 앞 노드와 간격이 `minGap`보다 작으면 `minGap`으로 민다. 끝이 `x1`을 넘으면 오른쪽부터 같은 방식으로 당긴다. 둘 다 안 되면 같은 간격이다.
- 지킬 성질(테스트): 첫 값 `x0`, 끝 값 `x1`, 증가, 이웃 간격 ≥ `minGap`(가능할 때)
