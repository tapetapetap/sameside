# Research: 같은편 2차

1차 연구 R1~R13은 `specs/001-sameside-pickup/research.md`에 있다. 여기서는 R14부터 잇는다.

## R14. 비상등은 읽을 수 없다 → 오른쪽 깜빡이

- **Decision**: 도착 확인(FR-038)의 조건은 오른쪽 깜빡이와 정차다.
- **Rationale**:
  - Vehicle SDK 2.3.2의 `TurnSignalValue`는 `OFF`, `LEFT`, `RIGHT` 세 값뿐이다.
  - 라이브러리(`classes.jar`) 어디에도 `HAZARD` 문자열이 없다.
  - 계기판 표시등(telltales)으로 읽는 길은 첫 앱에서 비공개 권한(`PRIVATE_CAR_SAFETY`)이 필요하다고 확인됐다.
  - Virtual Console에는 비상등 쓰기(`/vehicle/lights?feature=hazard`)가 있지만, 앱이 받을 API가 없다.
- **Alternatives**: 사용자 문서의 2순위(오른쪽 깜빡이 + 정차)를 그대로 쓴다. SDK에 비상등 값이 생기면 조건에 OR로 더한다.

## R15. 에뮬레이터 내비의 안내 상태 (2026-11-10 측정)

디버그 전용 `DemoReceiver`로 쟀다.

| 동작 | 결과 |
|---|---|
| `getRouteStateInfo().isRouting` | 화면에 안내가 떠 있어도 늘 `false`였다. 쓰지 않는다 |
| `getDestinationInfo()` | `onDestinationInfo`로 목적지 이름이 온다(예: "세븐일레븐 앞", "집"). 1초 안 |
| `getWaypointInfo()` | `onWayPointInfo`로 경유지 이름 목록이 온다(예: `[진입]`) |
| 안내 중 `cancelRoute()` | 내비가 요청은 받았지만(`onCancelRouteRequest`) 안내를 끝내지 않았다. 목적지·경유지가 남았다. 두 번 재현됐다 |
| `requestRoute(집)` | 이전 안내가 취소되고(`onRouteCancelled`) 목적지가 "집", 경유지가 `[]`가 됐다 |
| 경유지가 없을 때 `removeWaypoint(ALL)` | 안내가 한 번 취소됐다가 같은 목적지로 다시 잡혔다(`onRouteEnded` 뒤 목적지 "집") |
| 내비 앱의 `android.intent.action.NAVIGATE` + `geo:` | 새 경로를 잡지 않았다. 기존 작업만 앞으로 가져왔다 |

- **Decision**
  - **원래 목적지 판단**: 확인할 때 `getDestinationInfo`와 `getWaypointInfo`를 함께 묻는다. 3초 안에 답이 없으면 없는 것으로 본다.
    - 목적지 이름이 비어 있지 않고 `navOwn`(앱이 마지막으로 목적지로 넣은 지점 이름)과 다르면 "원래 목적지가 있다"로 본다.
  - **경유지 방식**: 원래 목적지가 있고 경유지가 비어 있으면 `addWaypoint(진입, FIRST)`, `addWaypoint(지점, SECOND)` 순서로 넣는다. 탑승·취소 때 `removeWaypoint(ALL)`이다.
  - **경유지가 이미 있으면** 경유지 두 칸을 쓸 수 없어, 1차처럼 지점을 목적지로 넣고 FR-037의 한 줄을 보인다.
  - **목적지 방식**(1차): 넣은 지점 이름을 `navOwn`에 저장한다.
  - **시연 상태 맞추기**(위임 결정 D3, goldfish readiness #2):
    - `reset`이 앱을 멈추기 전에 `DemoReceiver route`로 고정 목적지 "시연대기"(성균관대역 앞 서부로)를 넣는다. 이름에 공백이 없어야 `adb shell`로 그대로 넘어간다.
    - 다시 쓰는 `people.xml`의 `navOwn`도 "시연대기"로 둔다. 그래서 일반 장면은 늘 목적지 방식이다.
    - `continue` 계열 장면만 `reset` 뒤에 "집"을 새로 넣어 경유지 방식이 된다.
    - 장면은 늘 `reset` 뒤에 돈다.
- **Rationale**: `isRouting`을 믿을 수 없고, 취소가 목적지를 지우지 않는 에뮬레이터에서도 1차 픽업의 잔여 목적지를 원래 목적지로 오인하지 않게 한다.
- **에뮬레이터에서 볼 수 없는 것**: 내비 자기 위치가 판교 고정이라 "경유지를 지나 원래 목적지로 이어 간다"는 화면 이동을 볼 수 없다. 확인은 탑승 뒤 `state`의 목적지·경유지 값으로 한다.

### R15 보충 (구현 중 측정, 2026-11-10 03:1x, 디버그 `DemoReceiver`)

| 동작 | 결과 |
|---|---|
| 목적지 "집"에 A(FIRST) → B(SECOND) | `[A, B]` |
| A(FIRST) → B(FIRST) | `[A, B]` (앞에 끼우지 않는다) |
| 빈 목록에 B(SECOND) → A(FIRST) | `[B, A]` |
| 경유지를 하나 넣을 때마다 | 약 6초 경로를 다시 잡고, 그사이 목적지가 빈 값으로 읽힌다 |

- 이 에뮬레이터 내비는 `WaypointIndex`를 무시하고 부른 순서대로 뒤에 붙인다. 앱은 진입 → (ADD 콜백) → 지점 순서로 부르므로 순서는 맞다.
- 장면에서 확인 20초 뒤 경유지가 `[진입, 세븐일레븐 앞]`으로 2번, `[세븐일레븐 앞]`만 1번 나왔다. 경로를 다시 잡는 중에 둘째를 넣으면 진입이 빠지는 것으로 보인다. 지점은 늘 남았고, 탑승·취소 뒤에는 늘 목적지 "집", 경유지 `[]`였다(SC-009).
- 실차 내비가 칸 번호를 지키는지는 확인하지 못했다.

## R16. 깜빡이 신호

- **Decision**: DRIVE DNA `CarWatcher`와 같이 `vehicle.getTurnSignal().registerTurnSignalState(TurnSignalStateListener)`를 쓴다. 값이 `RIGHT`면 켜짐으로 본다.
- **권한**: `pleos.car.permission.CAR_LIGHTS`다. DRIVE DNA가 이 권한으로 방향지시등을 받는다(`drive-dna/app/src/main/AndroidManifest.xml`). 설치 권한이라 런타임 요청은 없다.
- **시연 쓰기**: Virtual Console `PUT /vehicle/turn-signal?mode=BlinkRight`(끄기 `Off`)다. DRIVE DNA 측정에서 깜빡임 없이 LEFT/RIGHT 값으로 왔다.

## R17. 시연 데이터: 기준 랜드마크와 교차로

- **올리브영**: OSM(2026-11-09 Overpass, ODbL)에서 찾았다.
  - 세븐일레븐 길가 점에서 약 98m 떨어져 있고, 서부로(북쪽 방향 줄)의 오른쪽, 즉 같은 길가다.
  - 다른 랜드마크와 같은 방식으로 넣는다. 길 선에서 오른쪽 4m 길가 점이다.
  - **지점 후보로 넣지 않는다**(`Landmark.spot = false`, data-model §1-1). 후보로 넣으면 `handsfree` 좌표에서 차 분이 1분 적어 1순위가 바뀐다(goldfish readiness #1).
  - 길가 점은 `LatLon(37.2982708, 126.9715664)`, 세븐일레븐보다 길을 따라 96.4m 앞이다.
- **교차로**: 정차 금지 데이터의 `CORNER` 점(교차로 모퉁이 5m)을 교차로 위치로 쓴다. 지점에서 가장 가까운 점까지의 직선거리로 유턴 시간을 잰다(FR-040). `// ponytail: 직선 근사. 진행 방향 앞쪽 교차로만 고르면 더 맞다`

## R18. 넣지 않는 것의 근거

- **음성 입력**:
  - Pleos Maven 저장소에 `ai.pleos.playground:SpeechToText` 3.3.1이 있다(2026-11-10 `maven-metadata.xml` 확인).
  - 하지만 주행 중 앱이 듣기를 시작할 계기가 없다. 운전대 음성 버튼은 시스템 비서 몫이다.
  - 에뮬레이터 마이크 시험은 밤에 소리를 내야 해서 하지 않았다. 다음 단계에서 F6 질문 직후 5초만 듣는 방식부터 붙인다.
- **로드뷰**: Naver Maps JS `Panorama`는 Naver Cloud 키가 필요하다. 계정·키 발급은 대신 할 수 없고, 사용 조건·요금도 확인하지 못했다.

## R19. 잠정 수치 (R12에 더함)

| 값 | 쓰는 곳 |
|---|---|
| 알림 뒤 반응 대기 60초 | FR-030 |
| 반응으로 보는 움직임 20m | FR-030 |
| 도착 확인 정차 3초(3초 동안 5m 미만), 지점 50m | FR-038 |
| 유턴 교차로 600m, 신호 대기 90초, 왕복 × 1.3 ÷ 시속 25km | FR-040 |
| 기준 랜드마크 20~150m | FR-043 |
| 원래 목적지 질의 답 대기 3초 | R15 |
