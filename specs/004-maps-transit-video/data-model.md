# Data Model: 같은편 4차

1·2·3차 data-model 위에 더한다. 판단 값(`Pickup`, `Member`, `Spot`, `Plan`)은 바뀌지 않는다. 좌표는 3차처럼 원점 기준 m 평면(`xy`, x 동쪽, y 북쪽)으로 계산한다. 순수 계산은 `Views.kt` 최상위 함수와 `Pick` 객체에 두고, 새 테스트 파일 `CardMapTest.kt`(클래스 `CardMapTest`)가 지킨다(원칙 VI). `GroupTest.kt`에 이미 `class MapTest`가 있어 이름을 피했다.

## §1 지도에 그릴 사람(`MapPerson`, 바뀜)

```kotlin
class MapPerson(val name: String, val pos: LatLon?, val leg: TransitLeg? = null, val slot: Int = 0) {
    val line get() = leg?.line // 가는 중 SpotMap의 가장자리 글("민지 · 1호선")은 이 값을 그대로 쓴다
}
```

- 그릴 사람(`mapPeople`, 지금 함수를 고친다)
  - 빠지지 않은 사람이다. 가는 중에는 아직 안 탄 사람만이다(지금과 같다).
  - `pos`가 null이면 두 지도 모두 그 사람을 그리지 않는다.
- `leg`(대중교통이면 노선 구간, 아니면 null)
  - 함께 태우기: 그 지점의 `plans[i]?.leg`
  - 한 명 픽업: `transitAccepted`이면 `offer?.leg`, 아니면 null
- `slot`: 사람 색 번호(§4). `p.members` 전체 이름 순서로 정하므로, 사람이 빠져도 남은 사람의 색은 바뀌지 않는다.

## §2 이동 경로 구간

```kotlin
data class Seg(val from: LatLon, val to: LatLon, val leg: TransitLeg?) // leg가 null이면 걷기
fun route(pos: LatLon, leg: TransitLeg?, spot: LatLon): List<Seg>
```

- 걷기(`leg == null`): `[Seg(pos, spot, null)]`
- 대중교통: `[Seg(pos, leg.board.pos, null), Seg(leg.board.pos, leg.alight.pos, leg), Seg(leg.alight.pos, spot, null)]`
- 앞 구간의 `to`와 다음 구간의 `from`은 같은 점이다.

## §3 사람 지도 범위(`fit`, research R28)

```kotlin
data class Fit(val cx: Double, val cy: Double, val k: Double) // 가운데(m), 축척(px/m)
fun fit(points: List<XY>, w: Float, h: Float, pad: Float, minShortM: Double = 100.0): Fit
```

- 입력
  - `points`: 지점 원점 m 좌표의 그릴 점들이다. 지점 (0, 0)은 늘 들어 있다.
  - 대중교통 사람이 있으면 역 두 개도 들어 있다.
- 계산
  - 상자 [minX, maxX] × [minY, maxY]의 가운데가 (cx, cy)다.
  - k = min((w − 2·pad) / (maxX − minX), (h − 2·pad) / (maxY − minY), min(w, h) / minShortM)
    - 폭이나 높이가 0이면 그 항은 무한대다. 점이 하나뿐이면 셋째 항이 k다.
  - 화면 좌표: x_px = w/2 + (x − cx)·k, y_px = h/2 − (y − cy)·k
- 지킬 성질(테스트)
  - 모든 점이 여백 안에 들어온다.
  - 짧은 변이 덮는 거리 min(w, h) / k는 100m 이상이다.
  - 점 하나면 짧은 변이 100m다.

## §4 사람 색 번호(`colorSlots`, research R32)

```kotlin
fun colorSlots(names: List<String>, n: Int = 5): List<Int>
```

- i번째 이름의 첫 후보는 `Math.floorMod(names[i].hashCode(), n)`다. 지금 첫 글자 원과 같다.
- 앞사람들이 쓴 번호면 (후보 + 1) mod n, (후보 + 2) mod n … 가운데 안 쓴 첫 번호를 쓴다.
- 이름이 n개를 넘으면 겹침을 허용하고 첫 후보를 쓴다. 함께 태우기는 최대 3명이라 생기지 않는다.
- 색 자원
  - 첫 글자 원(왼쪽 목록): `avatar{번호+1}`, 지금 그대로다.
  - 지도의 선·점과 사람 줄 아이콘: `avatar{번호+1}_line`(§9)

## §5 차 세울 곳 기하(`stopGeom`, research R33)

```kotlin
fun rightOf(headingDeg: Double): XY = XY(cos(h), −sin(h)) // h = 라디안. 북 0°, 시계 방향의 오른쪽 단위 벡터
data class StopGeom(val r: XY, val band: XY, val arrowFrom: XY, val arrowTo: XY, val stand: List<XY>)
fun stopGeom(area: Area, spot: Spot, n: Int): StopGeom // 좌표는 지점 원점(spot.pos) m
```

- 기본값
  - `pr = project(area.center, area.road(spot.landmark.roadId), spot.pos)`
  - r = `xy(spot.pos, pointAlong(area.center, road, pr.along))`
  - d = `headingVec(spot.heading)`(진행 방향 단위 벡터), e = `rightOf(spot.heading)`
- 위치(4차 FR-085: 길 반폭 `half = road.lanes × LANE_M / 2`, LANE_M = 3.1m)
  - 띠 가운데: `band = r + e × (half − 1.5)`. 띠는 진행 방향 18m, 폭 3m인 사각형으로 회전해 그린다(맨 오른쪽 차로 안).
  - 화살표: `arrowTo = r + e × (half − LANE_M / 2) − d × 9`(맨 오른쪽 차로 가운데, 띠 뒤 끝), `arrowFrom = arrowTo − d × 30`
  - 서 있을 곳: `stand[k] = r + e × (half + 3) + d × ((k − (n − 1) / 2.0) × 4)`, k = 0..n−1
- 차선 선: `offsetLine(pts, d)`는 길 선을 왼쪽(+)·오른쪽(−)으로 d(m) 나란히 옮긴 선이다. 꼭짓점은 양옆 선분 법선의 평균 방향으로 옮긴다.
- 범위: 가운데 r, 짧은 변의 반 35m. 축척 k = (min(w, h) / 2) / 35이다.
- 지킬 성질(테스트)
  - `rightOf(0) = (1, 0)`(북쪽으로 오면 오른쪽은 동쪽), `rightOf(90) = (0, −1)`(동쪽으로 오면 남쪽)
  - 시연 `opposite`의 세븐일레븐 지점(서부로 3차로)에서 `band`, `stand`, `arrowTo`는 지점 길 선의 오른쪽에 있다. 길 선에서의 거리는 각각 half − 1.5, half + 3, half − LANE_M / 2다.
  - `offsetLine`: 동쪽으로 가는 선을 +2 옮기면 북쪽 2m, −2면 남쪽 2m

## §6 축척 막대(`scaleBar`, research R34)

```kotlin
fun scaleBar(k: Double, widthPx: Float): Int  // 후보 20·50·100·200·500·1000·2000m 가운데 m·k가 widthPx/4에 가장 가까운 것
fun scaleLabel(m: Int): String                // 1000 미만 "50m", 이상 "1km"·"2km"
```

## §7 대중교통 안(`Offer`, 바뀜, research R36)

```kotlin
data class Offer(val leg: TransitLeg, val gainMin: Int, val spot: Spot, val arriveSec: Double)
```

- `arriveSec`는 `Pick.transit`의 `arrive`다. 탈 역까지 걷기, 기다림, 탐, 내려서 지점까지 걷기의 합이다.
- 서버와 페이지로 보내는 값(`Relay`의 offer JSON)은 바뀌지 않는다.
- 거절(사용자 결정 2026-11-11): `Member`에 `var transitDeclined = false`를 둔다.
  - `Pick.step`이 태울 사람 기록의 `transit == "decline"`을 받으면 참으로 바꾼다. 조건: 상태가 SPOTS 또는 GOING, `offer != null`, 아직 받아들이지 않음. accept 처리(`Pick.kt` 569~571행) 옆에 둔다.
  - 거절 뒤에는 accept가 와도 받아들이지 않는다.
  - 지점, 내비, 음성은 바꾸지 않는다.
  - 함께 태우기(사람마다 plan)에는 쓰지 않는다.

## §8 카드 글(`Pick`의 순수 함수)

```kotlin
fun howLine(name: String, leg: TransitLeg?, sec: Double): String
fun transitNote(p: Pickup): Pair<String, String>?  // (굵은 줄, 작은 줄). 없으면 null
```

- `howLine`(FR-076)
  - 걷기: `"$name 걸어서 ${minutes(sec)}분"`
  - 대중교통: `"$name ${leg.line} ${leg.stops}정거장(${leg.board.name} → ${leg.alight.name}) · ${minutes(sec)}분"`
  - 쓰는 곳
    - 한 명 픽업: 받아들였으면 `(offer.leg, offer.arriveSec)`, 아니면 `(null, s.walkSec)`
    - 함께 태우기: `(plan.leg, plan.arriveSec)`
- `transitNote`(FR-077·078)
  - `p.group`이거나, `p.lead.offer == null`이거나, `p.lead.transitDeclined`이면 null이다.
  - o = offer, n = 이름이다.
    - 보냄: `("${n}님께 ${o.leg.line} 안을 보냈어요", "${board} → ${alight} ${stops}정거장 · ${o.spot.line}에서 만나면 ${o.gainMin}분 빨라요")`
    - 받아들임: `("${n}님이 ${o.leg.line} 타고 ${alight}${toParticle(alight)} 와요", "${board} → ${alight} ${stops}정거장")`

## §9 색 자원(`colors.xml`, `values-night/colors.xml`)

| 이름 | 밝은 | 다크 | 쓰는 곳 |
|---|---|---|---|
| `line1` | `#FF0052A4` | `#FF5C8FF0` | 탐 실선, 역 테두리, 노선 글, 강조 카드 아이콘 원 |
| `avatar1_line` | `#FF445DBC` | `#FF5B7CFA` | 사람 선·점·아이콘(번호 0) |
| `avatar2_line` | `#FFB67338` | `#FFF2994A` | 번호 1 |
| `avatar3_line` | `#FF1D8348` | `#FF27AE60` | 번호 2 |
| `avatar4_line` | `#FFB04141` | `#FFEB5757` | 번호 3 |
| `avatar5_line` | `#FF743DA8` | `#FF9B51E0` | 번호 4 |

- 지도 바탕(`map_bg`) 대비가 두 테마 모두 3:1 이상이다(research R31·R32).

## §10 시연 영상 강조(저장소 밖, contracts/video.md)

- 자막 줄: `(시작 초, 끝 초, 종류, 문장)`
  - 종류는 음성, 전화, 알림, 함께, 안내 가운데 하나다.
  - VTT 문장 앞 글자로 정한다: 🔊, 📞, 🔔, 👥, ※
- 통화 구간: `(a, b, c)`. 전화 걸기 요청, 통화 중 true, 통화 중 false 시각이다. b와 c는 없을 수 있다.
  - b는 기기에 통화가 생긴 때(거는 중 포함, 3차 FR-066)다. 연결된 때가 아니다.
  - 시각은 t0(녹화 시작 기기 시각, `log/<id>.t0`) 기준 초다.
- 안내: 처음 "기어 P 아님" 시각 g. 없으면 안내 자막이 없다.
