# Data Model: 같은편 픽업

- 판단에 쓰는 형식과 함수는 모두 `Pick.kt`와 `Area.kt`(순수 Kotlin)에 있다. 안드로이드 import는 없다.
- 수치는 잠정값이다(research R12). `Pick.kt` 맨 위 `object Rules` 한 곳에 둔다.
- 판단 함수는 지역을 인자로 받는다. 그래서 테스트는 일부러 만든 작은 지역(합성 지역)으로 거르기 규칙을 하나씩 확인한다. 시연 지역은 `Area.kt`의 `DEMO` 하나다.

## 1. 저장 (차 안, SharedPreferences `people`)

| 키 | 형식 | 예 |
|---|---|---|
| `people` | 한 사람이 한 줄이다. `이름\t전화번호\t표시`이고, 표시는 `1`이면 아이·어르신, `0`이면 아니다 | `민지\t01012345678\t0` |
| `car` | 한 줄. `차종\t색\t번호판\t운전자 전화번호`. 비어 있어도 된다 | `그랜저\t흰색\t12가 3456\t01098765432` |

- **형식 함수**: `People.encode(list)`과 `People.decode(text)`은 순수 함수이고 왕복 테스트가 있다. 깨진 줄은 건너뛴다.
- **검증**(저장할 때)
  - 이름은 앞뒤 공백을 빼고 1~10자
  - 전화번호는 숫자만 남긴 뒤 9~11자리
  - 탭과 줄바꿈은 지운다.
  - 사람은 최대 20명이다. 20명이면 "+ 사람 추가"는 대화상자를 열지 않고 "최대 20명까지 저장해요"를 보인다(contracts/ui.md §1).
- **차 정보 검증**: 차종·색·번호판은 각 0~20자이고 탭과 줄바꿈을 지운다. 운전자 전화번호는 비우거나, 숫자만 남긴 뒤 9~11자리다. 틀리면 그 칸 아래에 "전화번호를 확인해 주세요"가 보인다. 이 번호는 페이지의 `tel:` 링크에 그대로 쓰이므로 숫자만 둔다.
- 시연 스크립트는 이 값을 `run-as`로 `shared_prefs/people.xml`에 직접 쓴다(quickstart).

## 2. 지역 데이터 (`Area.kt`, 순수 Kotlin)

```kotlin
data class LatLon(val lat: Double, val lon: Double)
data class Road(val id: String, val name: String, val points: List<LatLon>, val oneway: Boolean) // oneway면 points 순서가 허용 방향
enum class NoStopKind(val meters: Double) { CORNER(5.0), BUS(10.0), CROSSWALK(10.0) }
data class NoStop(val kind: NoStopKind, val pos: LatLon)
data class Landmark(val id: String, val name: String, val pos: LatLon, val roadId: String, val photo: String?)
data class Stop(val name: String, val pos: LatLon)
data class TransitLeg(val id: String, val line: String, val board: Stop, val alight: Stop, val stops: Int, val rideSec: Int, val waitSec: Int)
data class Area(val center: LatLon, val roads: List<Road>, val noStops: List<NoStop>, val landmarks: List<Landmark>, val transit: List<TransitLeg>)
val DEMO = Area(...)   // 시연 지역 하나
```

**필드 규칙**

| 필드 | 규칙 |
|---|---|
| `Landmark.name` | "앞"을 붙여 읽을 수 있는 이름이다(예: "성균관대역 1번 출구", "스타벅스 성균관대역점"). 화면·음성은 `"${name} 앞"`을 쓴다 |
| `Landmark.pos` | 랜드마크 바로 앞 **길가** 점이다(건물 중심이 아님). 길 선분에서 3m 넘게 떨어져야 길가 쪽이 정해진다 |
| `Landmark.photo` | `server/photo/` 아래 파일 이름이다. 1차 기본값은 null이고, 이때 페이지는 사진 칸을 숨긴다. 팀이 직접 찍은 사진을 주면 그 파일을 둔다 |
| `TransitLeg` | 시연용 준비값이다. 실시간이 아니다. `stops`는 1 또는 2다 |

**시연 장면이 요구하는 데이터**(research R9)
1. **반대편 함정**: 한 길의 양쪽 길가에 랜드마크가 하나씩 있다. 태울 사람은 차가 오는 쪽의 반대편 가까이에 서 있다.
2. **두 정거장**: 데이터 묶음이 두 개 있어야 한다.
   - 묶음 ①: 성균관대역 주변(태울 사람과 탈 정거장)
   - 묶음 ②: 내릴 정거장 주변(길과 랜드마크). 묶음 ①에서 1.6km 넘게 떨어져 있어야 한다. 이득이 5분을 넘으려면 내린 쪽 지점이 차에 그만큼 가까워야 하기 때문이다(§5).
   - 차 시작점은 묶음 ②를 지나 ①로 가는 길 위, 수 km 밖에 둔다.
   - `transit-small` 장면에는 이득이 5분 이하인 다른 `TransitLeg`나 차 시작점을 쓴다.
3. **걸러지는 자리**: 하나 이상 있다. 예: 버스정류장 10m 안의 랜드마크. SC-001과 단위 테스트가 확인한다.
4. **자동 전화 장면(`handsfree`)의 경로**: 앱은 남은 거리를 직선거리 × 1.3으로 본다(§6). 그래서 전화를 거는 무렵부터 진입 경유지까지의 실제 경로 길이 ÷ 직선거리가 1.17~1.30이어야 한다. 그래야 앱의 전화가 실제 문턱 뒤 30초 안에 나온다(SC-004). 단위 테스트가 이 장면 경로를 1초 간격으로 다시 돌려 확인한다(tasks T021).

## 3. 기하 (Pick.kt)

모든 기하 함수는 원점 `o`(보통 `area.center`)를 받는다.

- **평면 변환** `xy(o, p)`
  - `x = (lon − o.lon) × cos(o.lat) × 111_320`
  - `y = (lat − o.lat) × 110_540`
  - 단위는 m이고, x는 동쪽, y는 북쪽이다.
- `dist(o, a, b)`: 평면 거리(m)
- `bearing(o, a, b)`: 0°가 북쪽이고 시계 방향이다.
- `angleDiff(a, b)`: 0~180°
- `project(o, road, p)`: 가장 가까운 선분과 그 선분 위의 발, 부호 있는 거리 `offset`을 돌려준다.
  - `offset`은 외적 `seg.x·(p.y−a.y) − seg.y·(p.x−a.x)`를 선분 길이로 나눈 값이다.
  - 양수면 선분 진행 방향의 왼쪽, 음수면 오른쪽이다.
- `side(o, heading, from, to)`: `from`에서 `heading`으로 갈 때 `to`가 오른쪽이면 RIGHT, 왼쪽이면 LEFT다.

## 4. 지점 고르기 `Pick.choose(area, car, passenger, careful): List<Spot>`

### 4.1 후보 거르기

`area.landmarks`의 랜드마크 L마다 아래를 순서대로 따진다. 하나라도 걸리면 뺀다.

1. **걷는 거리** `walkM = dist(passenger, L.pos) × 1.3`. `careful`이면 150m, 아니면 300m를 넘을 때 뺀다.
2. **길가 쪽**: `project(road(L.roadId), L.pos)`를 구한다.
   - `|offset| < 3`이면 뺀다(애매).
   - `offset < 0`(오른쪽)이면 들어오는 방향 `h`는 선분 방향이다.
   - `offset > 0`(왼쪽)이면 `h`는 그 반대다. 이때 `oneway`면 뺀다.
3. **세우면 안 되는 자리**: 어떤 `NoStop`이든 `dist(L.pos, ns.pos) < ns.kind.meters`면 뺀다.
4. **U턴**: `angleDiff(h, bearing(car, L.pos)) > 100`이면 뺀다.
   - 차가 지점에서 50m 안에 있으면 이 검사는 하지 않는다.
   - `// ponytail: 직선 방위 근사. 길이 복잡하면 틀린다. 2차에 경로 API`

### 4.2 남은 후보로 계산하기

- **진입 경유지 `waypoint`**: 길 선분의 발에서 `−h` 방향으로 길 선을 따라 100m 거슬러 간 점이다. 길 선이 먼저 끝나면 그 끝점이다.
- **차 시간**: `carSec = (dist(car, waypoint) × 1.3 + 100) / (25 km/h)`. 고를 때는 차가 아직 출발 전이라 시속 25km로 본다.
- **걷는 시간** `Pick.walkSec(area, passenger, spot)`. 지점을 정한 뒤에도 태울 사람 위치가 올 때마다 이 함수로 다시 계산한다(§7).
  - **길 건너기**: 태울 사람이 지점 길에서 80m 안에 있고 `offset` 부호가 지점과 다르면 `crossing = 1`, 아니면 0이다.
  - **식**: `walkSec = walkM / 67 × 60 + crossing × 60`
- **만나는 시각**: `meetSec = max(carSec, walkSec)`

### 4.3 순위와 결과

- **순위**(FR-011): 아래 순서로 오름차순 정렬하고 앞에서 3개를 쓴다.
  1. `ceil(meetSec / 60)`(분 단위로 올림)
  2. `walkM`
  3. `crossing`
- **결과 `Spot`**: landmark, pos, heading `h`, waypoint, walkSec, carSec, meetSec, crossing, reason, road
  - `road`: 지점 앞뒤 150m 길 선이다. 페이지 그림에 쓴다.
- **이유 한 줄**: `"차가 오는 쪽 · " + (crossing == 0 ? "길 안 건너도 됨" : "횡단보도 한 번") + " · 세워도 되는 곳"`
- 남은 후보가 없으면 빈 목록이다(→ `nospot`).

## 5. 대중교통 안 `Pick.transit(area, car, passenger, careful, best: Spot): Offer?`

`area.transit`의 T마다 계산한다.

1. **타러 가기**: `d1 = dist(passenger, T.board.pos) × 1.3`. 걷는 거리 한도(§4.1의 1)를 넘으면 건너뛴다.
2. **내린 뒤 지점**: `S = choose(area, car, T.alight.pos, careful).firstOrNull()`. 없으면 건너뛴다.
3. **태울 사람 도착**: `arrive = d1 / 67 × 60 + T.waitSec + T.rideSec + S.walkSec`
4. **만나는 시각**: `meet = max(S.carSec, arrive)`
5. **이득**: `gain = best.meetSec − meet`

- `gain > 300`(5분)인 T 중에서 gain이 가장 큰 것을 `Offer`로 돌려준다.
  - `Offer`: legId, line, stops, alightName, gainMin = floor(gain / 60), spot S
  - gain이 300을 넘으므로 gainMin은 5 이상이다.
- 걷는 지점이 없으면(`best` 없음) 대중교통 안도 내지 않는다.
- **데이터 요건**: 걷는 거리가 300m 안이라 `best.walkSec`는 약 330초 이하다. 그래서 이득 5분은 주로 차 시간 차이(`best.carSec − S.carSec`)에서 나온다. 데이터 묶음 ②가 묶음 ①에서 1.6km 넘게 떨어져 있어야 하는 이유다(§2).

## 6. 진행 방향, 속도, 남은 시간

- **진행 방향** `Pick.heading(o, track)`: 최근 위치 `p_n`과, 거기서 8m 넘게 떨어진 가장 최근 위치 `p_k`의 `bearing(p_k, p_n)`이다. 그런 `p_k`가 없으면 null이다.
- **속도** `Pick.speed(o, track, now)`: 최근 30초 위치들의 이동 거리 합 ÷ 경과 시간(m/s)이다.
- **서 있음** `Pick.stopped(o, track, now)`: 최근 5초 동안 5m 미만으로 움직였다.
- **남은 시간** `Pick.remaining(o, track, spot, passedWaypoint, now)`
  - 거리: `passedWaypoint`가 false면 `dist(car, waypoint) × 1.3 + 100`, true면 `dist(car, spot) × 1.3`
  - 속도: `max(speed, 20 km/h)`. 신호 대기처럼 주행 중에 잠깐 서 있어도 시속 20km로 본다.
  - `// ponytail: 직선 근사. 2차에 내비 DrivingInfo.duration`
- **passedWaypoint**: 차가 진입 경유지에서 30m 안에 한 번이라도 들어가면 true가 되고, 다시 false가 되지 않는다.
- **track**: `(LatLon, millis)` 목록이다. 최근 60초만 둔다.

## 7. 픽업 상태 `Pick.Pickup`과 전이

**상태**

| 상태 | 뜻 | 끝 상태인가 |
|---|---|---|
| `link` | 링크 열기, 또는 차 위치를 기다린다 | |
| `spots` | 지점을 냈고, 운전자 확인을 기다린다 | |
| `nospot` | 같은 편 자리가 없다. 위치 공유만 한다 | |
| `going` | 지점으로 가는 중이다 | |
| `met` | 탔다 | 예 |
| `canceled` | 운전자가 취소했다 | 예 |
| `stopped` | 태울 사람이 공유를 껐다 | 예 |
| `expired` | 시간이 지났거나, 서버가 픽업을 모른다 | 예 |

**`Pickup`이 들고 있는 것**
- 기본: id, key, person, status, statusAt(상태가 된 때), expireAt
- 차: track, park
  - `park`는 `Boolean?`이다. 아직 모르면 null이고, null은 P가 아닌 것으로 본다(원칙 I).
- 태울 사람: passenger(최근 위치, consent, ready, late, boarded)
- 지점: spots, chosen, offer, transitAccepted, walkSec(지금 값), passedWaypoint
- 말한 기록: called, spoke150, readySpoken, lateSpoken, spokenWhileDriving(주행 중에 말한 수)
- 서버에 올린 값: crossOver, wrongSide, outNow(마지막으로 올린 값), lastPushAt
- 탑승: emptySeatsAtStart, doorCycledNearSpot, seatTaken

**함수**: `Pick.step(area, p: Pickup, e: Event, now: Long): List<Action>`. `p`를 바꾸고 할 일을 돌려준다.

**Event**
- `CarPos(LatLon)`
- `Gear(park: Boolean)`
- `Door(area, open: Boolean)`
- `Seat(area, occupied: Boolean)`
- `Remote(status: String, consent: Boolean?, pos: LatLon?, ready: Boolean, late: Boolean, boarded: Boolean, transit: String?)`: 서버 `GET`의 값이다. JSON에서 이 형식으로 바꾸는 일은 `Relay.kt`가 한다. `Pick.kt`는 org.json을 쓰지 않는다. consent는 아직 아무 값도 오지 않았으면 null이다.
- `Gone`: 서버 `GET`이 404였다. 차 `PUT`이 409이면 서비스가 바로 `GET`을 한 번 더 해서, 그 결과를 `Remote`로 넣거나(끝 상태가 남아 있을 때) `Gone`으로 넣는다(404일 때).
- `Confirm(index)`
- `Cancel`
- `Tick`: 2초마다 들어온다. 만료를 본다.

**Action**
- `Speak(text)`
- `CallOrNotify(name, phone)`
- `SetNavi(spot)`: 목적지 + 진입 경유지
- `ClearNavi`
- `Push`: 지금 상태를 서버에 올린다.
- `End`: 폴링을 멈춘다.

**전이 규칙**

| 지금 | 일어난 일 | 바뀌는 것 / 할 일 |
|---|---|---|
| 끝이 아닌 상태 | `CarPos` | track을 갱신한다. `spots`·`nospot`·`going`에서는 직전 `Push`에서 1초 넘게 지났으면 `Push`(위치·남은 시간 갱신, contracts/api.md 주기) |
| `link` | `CarPos` 또는 `Remote` | 아래 셋이 처음으로 모두 갖춰지는 이벤트에서 `choose`·`transit`을 계산한다. ① consent true ② 태울 사람 위치 ③ 차 위치(track이 비어 있지 않음). 지점이 있으면 `spots`, 없으면 `nospot`이 되고 `Push`한다. 셋 중 하나라도 없으면 `link`에 머문다 |
| `link` | `Remote.consent = false` | 화면 문구만 바꾼다(contracts/ui.md §1) |
| `spots` | `Confirm(i)` | `park == true`일 때만 받는다. `chosen = spots[i]`, `walkSec = spots[i].walkSec`, `etaSec = 남은 시간(§6)`, `SetNavi`, `going`, `expireAt = now + carSec + 30분`, `Push`. 남은 시간을 여기서도 셈하는 것은 서 있는 동안 위치가 오지 않아도 화면·페이지에 "0분"이 나오지 않게 하기 위해서다(구현 중 추가) |
| `spots`·`going` | `Remote.transit = accept`(처음) | `transitAccepted = true`, `spots = [offer.spot]`(대안은 지운다). `chosen`은 `spots`면 −1 그대로(운전자가 확인), `going`이면 0(지점이 바뀌므로 `passedWaypoint`·`spoke150`은 false로 되돌린다). `Speak("○○님이 ${line} 타고 ${alightName}${으로/로} 와요")`(정차 중이든 주행 중이든). `going`이면 `SetNavi`. `Push` |
| `going` | `Remote`에 태울 사람 위치 | `walkSec = Pick.walkSec(area, 위치, chosen)`으로 다시 계산. 단, `transitAccepted`면 다시 계산하지 않는다(버스 안의 위치로 걷는 시간을 셀 수 없다) |
| `going` | `CarPos` | heading·남은 시간·passedWaypoint를 갱신하고 `expireAt = now + 남은 시간 + 30분`. 단, 지점 50m 안에 처음 들어온 뒤로는 expireAt을 더 미루지 않는다(`reached`). 그때가 "마지막 도착 예정 시각"이고, 지점에서 기다리는 동안 만료가 밀리지 않게 하기 위해서다(코드 리뷰 반영). 대중교통으로 지점이 바뀌면 `reached`를 되돌린다. 아래 규칙을 차례로 본다 |
| `going` | 자동 전화: `!called`, 남은 시간 ≤ max(walkSec + 60, 120) | `called = true` 뒤 아래 둘 중 하나. 대중교통을 받아들였으면 walkSec + 60 대신 120초 기준이다 |
| | 태울 사람이 "나와 있어요"를 눌렀거나, 지점 30m 안이고 건너편 경고의 기준 길가와 같은 쪽 | `readySpoken`이 false면 `Speak("○○님이 나와 있어요")`, `readySpoken = true`. 이미 true면 말하지 않는다 |
| | 그 밖 | `CallOrNotify`, `outNow = true`, `Push` |
| `going` | 150m: `!spoke150`, 차가 **지점 길 위**(지점 길의 가장 가까운 선분에서 25m 안)에 있고 dist(car, spot) ≤ 150 | `spoke150 = true`. 지점 길에 들어선 뒤에야 판정한다. 교차하는 길에서 꺾어 들어오기 전에 판정하면 방향이 틀리기 때문이다. 셋 중 하나를 말한다. 25m는 상·하행 선이 17m 떨어진 서부로에서 맞은편 줄을 달리는 차도 같은 길로 보기 위해서다(구현 중 조정, 2026-11-09). 교차로 근처에서 25m 안에 들어와도 아래 각도 사이(45°~135°)면 기다린다 |
| | heading이 있고 `angleDiff(heading, h) ≤ 45` | `Speak("오른쪽 ${name} 앞이에요")` |
| | heading이 있고 `angleDiff(heading, h) ≥ 135`(반대 방향으로 들어옴) | `Speak("○○님이 길을 건너 오른쪽으로 와요")`, `crossOver = true`, `Push` |
| | heading이 있고 45°~135° 사이(꺾는 중) | 아직 말하지 않는다. 다음 위치에서 다시 본다. 그사이 50m 안이 되면 아래 "곧" 줄이다 |
| | heading 없음 | `Speak("곧 ${name} 앞이에요")` |
| `going` | `!spoke150`인데 지점 길 위에 오지 않은 채 dist(car, spot) ≤ 50 | `spoke150 = true`, `Speak("곧 ${name} 앞이에요")`. 방향을 모르는 채로 건너라고 하지 않는다 |
| `going` | 건너편 경고 | 기준 길가는 지점 쪽이다. 단, `crossOver`가 true면 그 반대쪽(차가 서는 쪽)이다. 남은 시간 ≤ 180이고, 태울 사람이 지점 길에서 80m 안이며 `offset` 부호가 기준 길가와 다르면 `wrongSide = true`, 아니면 false. 올린 값과 다를 때만 `Push` |
| `going` | `Remote.ready`(처음, `!readySpoken`) | `Speak("○○님이 나와 있어요")`, `readySpoken = true` |
| `going` | `Remote.late`(처음) | `Speak("○○님이 조금 늦는대요")`, `lateSpoken = true` |
| `going` | 탑승 | `Remote.boarded`이거나, 문 닫힘(`Door` open=false)이나 착좌 true(`Seat`) 이벤트가 올 때, 그리고 `Tick`마다 아래 셋이 모두 맞으면 `met`, `Speak("○○님이 탔어요")`, `ClearNavi`, `Push`, `End`. `Tick`에서도 보는 것은 막 선 직후 문·좌석이 오면 최근 5초에 도착 움직임이 남아 ①이 아직 거짓이기 때문이다. `ClearNavi`는 만날 곳까지의 안내가 끝났기 때문이다(에뮬레이터 내비는 자기 위치가 고정이라 도착하지 않고, 남은 안내가 10초쯤 뒤 앞으로 나온다)(둘 다 구현 중 추가). ① `stopped`(§6) ② `doorCycledNearSpot`: 문이 열렸다가 닫힌 순간 dist(car, spot) ≤ 50이었다(거리는 닫힐 때만 본다) ③ `takenSeats`가 비어 있지 않다: `going`·`nospot`에서 `emptySeatsAtStart` 중 착좌 true가 된 자리를 넣고, false가 되면 뺀다(그 전 상태의 착좌, 다시 빈 자리는 세지 않는다). 앉은 뒤 문을 닫든, 문을 닫은 뒤 앉든 같다. 운전석 문(`ROW_1_LEFT`)은 ②에 세지 않는다(코드 리뷰 반영, 2차 FR-036과 같음) |
| `nospot` | 탑승 | `going`의 탑승 규칙과 같다. 다만 기준점은 지점 대신 태울 사람의 마지막 위치다(문이 닫힐 때 그 위치에서 50m 안). 맞으면 `met`, `Speak("○○님이 탔어요")`, `Push`, `End`(앱이 내비에 넣은 것이 없어 `ClearNavi`는 없다) |
| 끝이 아닌 상태 | `Remote.boarded` | `met`, `Speak("○○님이 탔어요")`, `Push`, `End`(지점이 있든 없든). `ClearNavi`는 `going`이었을 때만이다. 앱이 내비에 넣은 것만 지운다(코드 리뷰 반영) |
| 끝이 아닌 상태 | `Remote.status`가 끝 상태(`stopped`·`met`·`canceled`·`expired`) | 그 상태가 되고 `End`(서버가 이미 끝 상태다. 태울 사람이 공유를 끈 경우가 여기다). `going`이었으면 `ClearNavi`도(2차 코드 리뷰 반영: 어떤 끝이든 앱이 넣은 안내를 지운다) |
| 끝이 아닌 상태 | `Gone` | `expired`, `End`. `going`이었으면 `ClearNavi`도 |
| 끝이 아닌 상태 | `Cancel`(`park == true`일 때만) | `canceled`, `Push`, `End`. `going`이었으면 `ClearNavi`도 |
| 끝이 아닌 상태 | `Tick`, now > expireAt | `expired`, `Push`, `End`. `going`이었으면 `ClearNavi`도 |

**보충**
- **expireAt**: `link`·`spots`·`nospot`은 그 상태가 된 때 + 60분이다. `going`은 위 표를 따른다.
- **시작값**: 새 `Pickup`은 서비스가 들고 있던 마지막 값으로 시작한다. `park`는 마지막 기어 값이다. `track`은 마지막 위치 한 점이다. `emptySeatsAtStart`는 착좌가 false인 좌석이다(ROW_1_RIGHT, ROW_2_LEFT, ROW_2_RIGHT). 기어와 착좌는 구독 직후 한 번 오고 그 뒤에는 바뀔 때만 오므로, 서비스가 처음부터 받아 들고 있어야 한다(tasks T012·T016).
- **CallOrNotify 실행**(서비스, research R4)
  - 먼저 걸 수 있는지 본다. 조건은 `CALL_PHONE` 권한이 있는 것이다.
  - 걸 수 있으면 `speak`일 때 "○○님께 전화 걸게요"를 말하고, 2초 뒤 `TelecomManager.placeCall(Uri.fromParts("tel", phone, null), Bundle())`로 건다. 화면을 띄우지 않는 API라 앱이 가려진 주행 중에도 쓸 수 있다.
  - 걸 수 없거나 `placeCall`이 예외를 던지면, `speak`일 때 "○○님 폰으로 알렸어요"만 말한다. 한 줄이다.
  - 페이지 알림(`outNow`)은 어느 쪽이든 간다.
- **주행 중 음성 상한**(FR-015)
  - `park != true`일 때 `Speak`나 `CallOrNotify`를 낼 때마다 `spokenWhileDriving`을 1 늘린다. 전화 줄도 한 줄로 센다.
  - `CallOrNotify(name, phone, speak)`의 `speak`는 그때까지 센 수가 5보다 작은지다. false면 서비스는 전화(또는 대체 알림)는 하되 말은 하지 않는다.
  - 5가 된 뒤로는 주행 중 `Speak`를 내지 않는다. 화면과 페이지 표시는 그대로 바뀐다.
  - "나와 있어요"·"조금 늦어요"는 각각 한 번만 나온다.
- **`(으)로`**: `alightName`의 마지막 글자에 받침이 없거나 받침이 ㄹ이면 "로", 아니면 "으로"다(contracts/ui.md §2).

## 8. 서버에 올리는 상태 (contracts/api.md의 `car` 부분)

| 필드 | 형식 |
|---|---|
| `status` | §7 상태 문자열 |
| `name` | 태울 사람 이름 |
| `car` | `{model, color, plate, phone}`. phone은 비어 있을 수 있다 |
| `pos` | `{lat, lon, heading}`. `spots`·`nospot`·`going`에서 올린다. 끝 상태에서는 없음 |
| `etaSec` | 남은 시간(정수 초). `going`이면 §6, `nospot`이면 차 → 태울 사람 마지막 위치의 직선거리 × 1.3 ÷ max(속도, 20km/h). `nospot`이 되는 순간에도 한 번 센다(서 있어 위치가 더 오지 않아도 페이지에 차 분이 나온다, 코드 리뷰 반영) |
| `walkSec` | 태울 사람의 지금 걷는 시간(정수 초). `going`일 때만. 태울 사람 위치가 올 때마다 다시 계산한 값이다. `transitAccepted`면 보내지 않는다(null) |
| `spots` | `[{name, reason, lat, lon, heading, walkSec, meetSec, photo, road: [[lat, lon], …]}]` |
| `chosen` | spots의 index. 없으면 −1 |
| `offer` | `{line, stops, alightName, gainMin}` 또는 null |
| `outNow`, `crossOver`, `wrongSide` | Boolean |

**분 표시**: 화면과 페이지의 "n분"은 모두 초를 60으로 나눠 올림한다. 나갈 때 안내는 `ceil((etaSec − walkSec − 60) / 60)`이고, 0 이하면 "지금 나오세요"다.

## 9. 장면 좌표 파일 (`scripts/scenes.txt`)

시연 스크립트(`demo.py`)와 단위 테스트(`PickTest.kt`)가 같이 읽는다. 그래야 두 쪽의 좌표가 어긋나지 않는다.

**규칙**
- 한 줄은 `장면 키 값…`이다. 칸은 공백으로 나눈다.
- `#` 뒤는 모두 버린다. 줄 끝 주석도 마찬가지다(DRIVE DNA `DnaTest.kt`의 `substringBefore('#')`와 같음).
- 빈 줄은 건너뛴다.
- 장면 이름은 quickstart 시나리오 이름과 같다.
- 테스트는 Gradle 테스트 작업 디렉터리인 `app/` 기준으로 `../scripts/scenes.txt`를 읽는다(DRIVE DNA `DnaTest.kt`의 `File("../scripts/…")`와 같음).

**키**(장면마다 필요한 것만 쓴다)

| 키 | 값 | 쓰는 장면 |
|---|---|---|
| `car` | 위도 경도 | 모든 장면. 차 시작점 |
| `route` | 위도 경도 | 주행이 있는 장면. 여러 줄이고 순서대로 달린다 |
| `speed` | km/h | 주행이 있는 장면. 없으면 30 |
| `passenger` | 위도 경도 | 모든 장면. 태울 사람 처음 위치 |
| `passenger2` | 위도 경도 초 | `wrongside`. 주행 시작 몇 초 뒤 옮길 위치 |
| `expect` | 랜드마크 id | 기대하는 1순위 |
| `offer` | yes·no | `transit`, `transit-small`. 대중교통 제안이 떠야 하는가 |
| `walk` | 초 | `handsfree`. 걷는 시간 목표(확인용) |
| `stop_at` | 초 | `stop`. 주행 시작 몇 초 뒤 `stop: true`를 넣을지 |
| `cancel_at` | 초 | `cancel`. 지점을 낸 뒤 몇 초 뒤 P에서 "픽업 취소"를 누를지 |

```text
opposite car 37.29xxxx 126.97xxxx        # 차 시작점
opposite route 37.29xxxx 126.97xxxx      # 여러 줄
opposite passenger 37.30xxxx 126.97xxxx
opposite expect exit1
```
