# Data Model: 같은편 2차

1차 `specs/001-sameside-pickup/data-model.md`에 더하거나 바꾸는 것만 적는다. 적지 않은 것은 1차 그대로다.

## 1. 수치 (`Rules`에 더함, research R19)

| 이름 | 값 | 뜻 |
|---|---|---|
| `CALL_WAIT_MS` | 60_000 | 알림 뒤 반응을 기다리는 시간 |
| `MOVED_M` | 20.0 | 알림 때 자리에서 이만큼 넘게 움직이면 반응 |
| `ARRIVE_STOP_MS` | 3_000 | 도착 확인: 이 시간 동안 5m(`STOP_M`) 미만으로 움직였으면 서 있음 |
| `UTURN_NEAR_M` | 600.0 | 유턴 교차로를 찾는 거리 |
| `UTURN_WAIT_SEC` | 90.0 | 유턴 신호 대기 |
| `PASS_MIN_M`, `PASS_MAX_M` | 20.0, 150.0 | 기준 랜드마크를 찾는 거리(차가 오는 쪽) |
| `DRIVER_DOOR` | `"ROW_1_LEFT"` | 탑승 판정에서 빼는 문 |

도착 확인 거리는 1차 `BOARD_M`(50m)을 같이 쓴다.

## 1-1. 랜드마크(`Landmark`)에 더하는 값

- `spot: Boolean = true`: 지점 후보가 되는가
  - `choose`(그리고 그것을 부르는 `transit`)는 `spot == true`인 랜드마크만 후보로 삼는다.
  - §3 기준 랜드마크는 `spot`과 상관없이 `area.landmarks` 전체에서 찾는다.
- 올리브영은 `spot = false`다.
  - 넣으면 `handsfree` 좌표에서 차 분이 1분 적어 1순위가 되기 때문이다(goldfish readiness #1). 그러면 `wrongside`·`fallback`·`ack`의 기대가 모두 바뀐다.
  - 기준 랜드마크로만 쓴다.
  - 길가 점은 `LatLon(37.2982708, 126.9715664)`다. OSM 점(37.2981858, 126.9717150)을 `seobu_n`에 투영한 발에서 진행 방향 오른쪽 4m다. 세븐일레븐보다 길을 따라 96.4m 앞이다.

## 2. 지점(`Spot`)에 더하는 값 — `choose`가 채운다

| 필드 | 형식 | 정하는 법 |
|---|---|---|
| `uturnSec` | `Double?` | 진행 방향과 상관없이 지점에서 가장 가까운 `CORNER` 정차 금지 점까지의 직선거리 d가 600m 이하면 `2 × d × 1.3 ÷ (25 / 3.6) + 90`. 없으면 null |
| `passing` | `Landmark?` | §3 기준 랜드마크 |
| `standLine` | `String` | §3 태울 사람 문장 |

**비교 줄을 보이는 조건**: `crossing == 1`이고 `uturnSec != null`이고 `minutes(uturnSec) > minutes(CROSS_SEC)`일 때다.
- 차 카드: "건너기 {minutes(CROSS_SEC)}분 · 유턴 {minutes(uturnSec)}분"
- 페이지: "{이름}님이 길을 건너는 게 차가 유턴하는 것보다 {minutes(uturnSec) − minutes(CROSS_SEC)}분 빨라요". 이 k는 차가 세어 `fasterMin`으로 보낸다(§8). 페이지가 같은 규칙을 따로 갖지 않는다(코드 리뷰 반영)
- `going`에서 태울 사람 위치가 오면 걷는 시간과 함께 `crossing`도 다시 계산해 고른 지점에 반영한다. 건넌 뒤에는 비교 줄이 내려간다(코드 리뷰 반영)
- 분은 1차처럼 올림이다.

## 3. 기준 랜드마크와 태울 사람 문장 (FR-043·045)

- `project(R, p)`가 돌려주는 `along`은 길 선 처음부터 p의 발까지 길 선을 따라 잰 거리(m)다(`Pick.kt`의 `Proj`).
- 지점 S(랜드마크 L, 길 R)에서 `prS = project(R, L.pos)`, `forward = prS.offset < 0`이다.
- forward면 차가 along이 커지는 쪽으로 온다. 그래서 "차가 오는 쪽"은 along이 작은 쪽이다. forward가 아니면 반대다.

1. **후보**: R 위의 다른 랜드마크 L'이다. `pr' = project(R, L'.pos)`일 때 아래를 모두 만족해야 한다.
   - `sign(pr'.offset) == sign(prS.offset)`(같은 길가)
   - `u = forward ? prS.along − pr'.along : pr'.along − prS.along`(차가 오는 쪽으로 지점까지 남은 거리)가 20 이상 150 이하
2. 후보 중 u가 가장 작은 것이 `passing`이다. 없으면 null이다. 고른 것의 투영을 `prB = project(R, passing.pos)`라 한다.
3. **태울 사람이 기준보다 차가 오는 쪽인가**: `prP = project(R, 태울 사람 위치)`일 때 `forward ? prP.along < prB.along : prP.along > prB.along`로 정한다. 지점을 고를 때의 위치로 한 번 정한다.
4. **`standLine`**
   - `passing`이 있고 3이 참이면 `"{passing.name}{을/를} 지나 {S.line} 인도에 서 계세요"`다.
   - 아니면 `"{S.line} 인도에 서 계세요"`다. `S.line`은 "○○ 앞"이다.
5. **을/를 `toObject(word)`**
   - 마지막 글자가 한글이면 받침이 있으면 "을", 없으면 "를"이다.
   - 숫자면 0·1·3·6·7·8이 "을", 나머지가 "를"이다(읽는 소리: 영·일·삼·육·칠·팔).
   - 그 밖의 글자는 "를"이다.

## 4. 픽업(`Pickup`)·태울 사람(`Passenger`)에 더하는 값

| 필드 | 형식 | 처음 값 | 뜻 |
|---|---|---|---|
| `notifiedAt` | `Long?` | null | "지금 나오세요"를 보낸 때 |
| `notifiedPos` | `LatLon?` | null | 그때 태울 사람 위치 |
| `signalRight` | Boolean | false | 마지막 깜빡이 값이 오른쪽인가 |
| `arriveAsked` | Boolean | false | 도착 확인을 물었는가(한 번만) |
| `arriveCard` | Boolean | false | 확인 카드가 떠 있는가 |
| `arrived` | Boolean | false | 도착 알림을 보냈는가(서버로 올림) |
| `nextDest` | `String?` | null | 경유지 방식일 때 원래 목적지 이름 |
| `lostDest` | `String?` | null | 경유지를 넣을 수 없어 대신 바꾼 원래 목적지 이름(FR-037) |
| `Passenger.ack` | Boolean | false | "지금 나가요"를 눌렀는가 |

## 5. 이벤트 (더함)

- `TurnSignal(right: Boolean)`: 깜빡이 값이 바뀔 때 들어온다. RIGHT면 true, 그 밖은 false다.
- `Arrive(send: Boolean)`: 확인 카드의 보내기(true)와 아니요(false)다.
- `Route(next: String?, lost: String?)`: 서비스가 내비 방식을 정한 뒤 넣는다(§7).
- `Remote`에 `ack: Boolean` 필드를 더한다.

## 6. 전이 (1차 §7 표에 더하거나 바꿈)

| 지금 | 일어난 일 | 바뀌는 것 / 할 일 |
|---|---|---|
| `going` | 자동 전화 문턱(1차 조건 그대로) | 나와 있으면 1차와 같다(`called = true`, "나와 있어요"). **나와 있지 않으면 바뀐다**: `notifiedAt = now`, `notifiedPos = passenger.pos`, `outNow = true`, `Push`. `CallOrNotify`는 여기서 내지 않는다. 문턱 판단은 `called == false && notifiedAt == null`일 때만 한다 |
| `going` | `CarPos`·`Tick`에서 `notifiedAt != null`, `!called`, `now − notifiedAt ≥ 60초` | `called = true`. 반응이 하나라도 있으면 끝이다. 반응은 `passenger.ack`, `passenger.ready`, `notifiedPos`와 지금 위치가 둘 다 있고 20m 넘게 떨어짐이다. 대중교통을 받아들였으면 이동은 반응으로 보지 않는다(차 안이라 늘 움직인다, 코드 리뷰 반영). 반응이 없으면 1차 규칙으로 `CallOrNotify(이름, 번호, speak)`와 `Push`를 낸다 |
| `going` | `CarPos`·`Tick`·`TurnSignal`(차 위치가 있을 때) | `!arriveAsked && signalRight && dist(car, spot) ≤ 50 && stopped(최근 3초 5m 미만)`이고, 차가 지점 길 위(25m 안)에 있고 진행 방향이 길과 나란하면(꺾는 중인 45°~135°가 아님, 방향을 모르면 통과) `arriveAsked = true`, `arriveCard = true`, `Speak("○○님께 도착했다고 알릴까요?")`(음성 상한 규칙). 반대 방향 도착(위임 결정 D1)도 묻는다. 길 조건은 옆길 신호 대기에서 한 번뿐인 질문을 써 버리지 않게 하려고 더했다(코드 리뷰 반영) |
| `going` | `Arrive(send)` | `park == true && arriveCard`일 때만 받는다. `arriveCard = false`. send면 `arrived = true`, `Push` |
| 끝이 아닌 상태 | `TurnSignal(right)` | `signalRight = right` |
| 끝이 아닌 상태 | `Route(next, lost)` | `nextDest = next`. `lost`가 null이 아니면 `lostDest = lost`(한 번 잃은 원래 목적지는 그 픽업 동안 남는다. 대중교통으로 다시 물으면 목적지가 앱이 넣은 지점이라 null이 오기 때문이다, 코드 리뷰 반영) |
| 끝이 아닌 상태 | `Remote` | 1차에 더해 `passenger.ack = e.ack` |
| `going`·`nospot` | `Door(area == DRIVER_DOOR)` | 무시한다. 열림 기록도 남기지 않는다 |
| 탑승 → `met` | — | 문장만 바뀐다. `nextDest != null`이면 `"○○님 탑승 확인. 다음 목적지로 안내할게요"`, 아니면 `"○○님이 탔어요"`. 나머지(`ClearNavi`, `Push`, `End`)는 1차와 같다 |
| `going`에서 끝(탑승·취소·공유 끄기·Gone·만료) | — | 어떤 끝이든 `ClearNavi`를 낸다. 경유지가 원래 길이나 다음 픽업에 남지 않게 한다(코드 리뷰 반영, 1차 data-model도 고침) |
| `going` | 150m 오른쪽 판정 | `spot.passing != null`이고 차가 아직 기준을 지나지 않았으면(`prC = project(R, 차 위치)`, `forward ? prC.along < prB.along : prC.along > prB.along`) `"오른쪽 {passing.name} 지나서 {landmark.name} 앞에 세우세요"`. 아니면 1차 문장(옆길에서 꺾어 들어와 이미 지난 경우) |
| 끝이 아닌 상태 | `Tick` | 1차(만료, 탑승 다시 보기)에 더해, `going`이면 위의 60초 마감과 도착 확인을 본다 |

- 대중교통을 받아들이면 지점이 바뀐다. 이때 `arriveAsked`·`arriveCard`는 그대로 둔다. 도착 확인은 바뀐 지점에서 처음 조건이 갖춰질 때 묻는다.
- 받아들이기 전에 물었다면 다시 묻지 않는다(한 픽업에 한 번).

## 7. 서비스의 내비 실행 (`SetNavi`, `ClearNavi`, research R15)

**공통 규칙**(goldfish readiness #3)
- **토큰**: SetNavi마다 새 토큰을 둔다. 내비 콜백(질의 답, `onWaypointChanged`, `onRouteStarted`)은 아래 셋이 모두 맞을 때만 다음 단계를 실행한다.
  - 그 토큰이 지금 토큰과 같다.
  - `pickup === 그때의 픽업`이다.
  - 픽업이 끝나지 않았다.
- **대기 중 `ClearNavi`**: 대기를 버린다(토큰을 지운다). 아직 아무것도 넣지 않았으므로 내비에 아무것도 하지 않는다. 이미 넣은 것이 있으면 아래 `ClearNavi` 규칙을 따른다.
- **두 답이 모이면** 3초를 기다리지 않고 바로 정한다. 3초가 지나도 안 온 목적지 답은 "없음"이다. **경유지 답이 안 왔으면 "경유지 있음"으로 본다**(경유지 방식으로 가지 않는다). 모르는 채 경유지 방식으로 가면 나중의 `removeWaypoint(ALL)`이 운전자가 넣은 경유지까지 지우기 때문이다(코드 리뷰 반영).

**`SetNavi(spot)`**
1. **이 픽업이 이미 경유지 방식이면**(대중교통을 받아들여 지점이 바뀐 경우)
   - `removeWaypoint(ALL)`을 부르고 `onWaypointChanged`(REMOVE)를 받거나 3초가 지나면 3의 순서로 다시 넣는다.
2. **아니면 내비에 묻는다**: `getDestinationInfo()`와 `getWaypointInfo()`를 함께 부른다.
   - 목적지 이름이 비어 있지 않고 `navOwn`과 다르면 "원래 목적지가 있다"다.
3. **경유지 방식**(원래 목적지가 있고 경유지가 비어 있음)
   - `addWaypoint(진입, FIRST)`를 부르고, `onWaypointChanged`(ADD)를 받으면 `addWaypoint(지점 이름, 지점 좌표, SECOND)`를 부른다. 10초 안에 오지 않으면 로그만 남긴다.
   - `Route(원래 목적지 이름, null)`을 넣는다.
4. **목적지 방식**(그 밖): 1차 순서(`cancelRoute` → `requestRoute` → `addWaypoint(진입)`)다.
   - SharedPreferences `navOwn`에 `spot.line`을 저장한다.
   - 원래 목적지가 있었는데 경유지가 있어 못 넣은 경우면 `Route(null, 원래 목적지 이름)`, 아니면 `Route(null, null)`을 넣는다.

**`ClearNavi`**: 경유지 방식이면 `removeWaypoint(ALL)`, 아니면 `cancelRoute()`(1차)다.

## 8. 서버에 올리는 값 (1차 §8에 더함)

| 필드 | 형식 |
|---|---|
| `arrived` | Boolean |
| `spots[].fasterMin` | 정수 분 또는 null. 비교 줄 조건(§2)이면 `minutes(uturnSec) − minutes(CROSS_SEC)`. 페이지는 이 값만 쓴다(처음에는 `crossing`·`uturnSec`을 보내 페이지가 다시 셌으나 규칙이 두 곳이 되어 바꿨다, 코드 리뷰 반영) |
| `spots[].standLine` | 문자열(§3) |

태울 사람 쪽 필드: `ack`(Boolean)
