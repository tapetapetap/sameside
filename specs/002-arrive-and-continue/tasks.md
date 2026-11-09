# Tasks: 같은편 2차 — 알리고 나서 전화, 도착 알림, 원래 길로 이어 가기

**Input**: `specs/002-arrive-and-continue/`의 spec.md, plan.md, research.md, data-model.md, contracts/, quickstart.md
**Prerequisites**: 1차 구현(`specs/001-sameside-pickup/`, 작업 41개 완료)

**Tests**: 원칙 VI에 따라 `Pick.kt` 판단은 JUnit(`app/src/test/java/kr/skku/sameside/PickTest.kt`)을 먼저 쓰고 실패를 본 뒤 구현한다. 서버는 `python3 server/server.py check`를 먼저 고친다.

**경로 기준**: 저장소 루트 `/Users/jiwon/orca/workspaces/unused-feature-app/pickup`

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 1차 상태를 확인한다.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug`가 통과해야 한다(63개).
  - 디버그 전용 `app/src/debug/AndroidManifest.xml`과 `app/src/debug/java/kr/skku/sameside/DemoReceiver.kt`가 디버그 APK에 들어 있어야 한다. `adb shell am broadcast --user 10 -n kr.skku.sameside/.DemoReceiver --es cmd state`로 로그 "명령 state"를 본다.

---

## Phase 2: Foundational (모든 이야기 전에)

- [X] T002 올리브영을 기준 전용 랜드마크로 더한다(data-model §1-1, research R17).
  - `app/src/main/java/kr/skku/sameside/Area.kt`
    - `Landmark`에 `spot: Boolean = true`를 더한다.
    - `DEMO.landmarks`에 `Landmark("oliveyoung", "올리브영", LatLon(37.2982708, 126.9715664), "seobu_n", spot = false)`를 더한다.
  - `app/src/main/java/kr/skku/sameside/Pick.kt`: `choose`는 `spot == true`인 랜드마크만 후보로 삼는다.
  - `PickTest.kt`에 먼저 테스트를 쓴다.
    - `handsfree`·`wrongside`·`opposite`·`transit`의 `choose` 1순위가 여전히 `seven`이다.
    - 어느 장면의 `choose` 결과에도 `oliveyoung`이 없다.
    - 합성 지역에서 `spot = false` 랜드마크는 후보가 아니다.
- [X] T003 `app/src/main/java/kr/skku/sameside/Pick.kt`에 공통 형식을 더한다(data-model §1·§2·§4·§5).
  - `Rules`의 새 상수
  - `Spot`의 `uturnSec: Double? = null`, `passing: Landmark? = null`, `standLine: String`
    - `standLine`의 기본값은 `"$line 인도에 서 계세요"`가 되게, `choose`에서 채운다.
  - `Pickup`의 새 필드, `Passenger.ack`
  - `Event.TurnSignal`, `Event.Arrive`, `Event.Route`, `Event.Remote`의 `ack: Boolean = false`(마지막 인자, 기본값). 기본값이라 테스트의 직접 생성(`StepStartTest`의 `Event.Remote("link", …)`, `EndTest`의 `Event.Remote("stopped", …)`)은 그대로 둔다
  - `Remote` 생성자가 바뀌므로 테스트의 `remoteOf`와 `Relay.remote`도 같이 고친다. 동작은 아직 바꾸지 않는다. 1차 테스트가 그대로 통과해야 한다.
- [X] T004 [P] `server/server.py`를 고친다(contracts/api.md).
  - 자체 점검 3번 끝에 `ack` 두 줄을 먼저 더한다(true → 204·`GET`에 true, `"yes"` → 400). 실패를 확인한다.
  - 그다음 `PASSENGER_FIELDS`에 `"ack": bool`, `CAR_FIELDS`에 `"arrived": bool`을 더한다.
  - `fresh()`의 처음 값을 `arrived: False`, `passenger.ack: False`로 둔다.
  - `put_passenger`가 저장하는 키에 `"ack"`를 더한다.
  - `python3 server/server.py check`가 통과해야 한다.
- [X] T005 `app/src/main/java/kr/skku/sameside/Relay.kt`를 고친다(data-model §8).
  - `remote()`가 `passenger.ack`를 읽는다.
  - `carJson`에 `arrived`를 더하고, `spots[]`에 `crossing`, `uturnSec`(정수 **올림** `ceil` 또는 null), `standLine`을 더한다. 올림이어야 페이지의 분이 차 카드와 같다(data-model §8).

---

## Phase 3: User Story 1 - 알림 먼저, 반응 없을 때만 전화 (P1) 🎯 MVP

**Goal**: 문턱에서 "지금 나오세요" 알림만 보내고, 60초 안에 반응이 없으면 한 번 전화한다.
**Independent Test**: `handsfree`(알림 1번, 60~90초 뒤 전화 1번), `ack`(전화 0번)

- [X] T006 [US1] `PickTest.kt`에 `NotifyTest`를 먼저 쓴다(data-model §6).
  - 문턱에서 `CallOrNotify`가 없고 `outNow`·`Push`·`notifiedAt`이 생긴다.
  - 60초 뒤 반응이 없으면 `CallOrNotify` 한 번(그 시각), 그 뒤로는 없다.
  - `ack`, `ready`, 20m 넘는 이동은 각각 전화를 막는다. 19m 이동은 막지 못한다.
  - 알림 때 위치가 없으면 이동으로는 판단하지 않는다.
  - 나와 있음(버튼·같은 길가 30m)은 알림 없이 "나와 있어요"다(1차 테스트 유지).
  - `Tick`만 와도 60초 마감이 처리된다.
  - 1차 GoingTest 중 시각을 보는 `callsOnceAtWalkPlusOneMinute`, `thresholdIsAtLeastTwoMinutes…`의 전화 시각 기대값을 +60초로 고친다. `twentyFiveMetersAcross…`, `voiceStopsAtFive…`는 시각을 보지 않으므로 그대로 통과하는지만 본다(주행 경로가 60초 넘게 이어지는지 확인).
  - 대중교통 `acceptingWhileGoing…`도 +60초로 고친다.
  - `handsfree` 재생 테스트는 "실제 문턱 + 60초 뒤 0~30초"로 바꾼다.
- [X] T007 [US1] `Pick.kt`에 두 단계 전화를 만든다. `going()`의 전화 갈래를 바꾸고, `callIfNoAnswer`(`CarPos`·`Tick`에서 부름)를 둔다. `Remote` 처리에서 `passenger.ack`를 저장한다. T006을 통과시킨다.
- [X] T008 [P] [US1] `server/page.html`의 `outNow` 띠를 고친다(contracts/ui.md §3).
  - "지금 나가요" 버튼이 `put({ack:true})`를 보낸다.
  - `ack`가 true면 버튼 대신 "운전자에게 나간다고 알렸어요"를 보인다.
  - "운전자에게 전화"는 1차 그대로 둔다.
- [X] T009 [US1] 시각을 모두 기기 로그로 잰다(quickstart "재는 값", goldfish 재리뷰).
  - `PickupService.kt`: `run(actions)` 앞뒤로 `outNow`가 false → true로 바뀌면 `Log.i(TAG, "알림 보냄")`
  - `scripts/demo.py` `handsfree()`
    - 출발 시각: 기어 Drive를 쓴 직후의 `devtime()`
    - 알림 시각: "알림 보냄" 로그
    - 전화 시각: 판단 순간에 찍히는 "말하기: 민지님께 전화 걸게요"(`handsfree`) 또는 "말하기: 민지님 폰으로 알렸어요"(`fallback`) 로그. "전화 걸기 요청"은 2초 늦게 찍혀 쓰지 않는다.
  - 출력은 출발 기준 초로 세 가지다: 실제 문턱, 알림 시각, 전화 시각.
  - 합격 표시: 알림 − 실제 문턱이 0~30초, 전화 − 알림이 60~62초
  - `ack` 장면을 더한다. `handsfree` 좌표를 쓰고, 달리는 중 서버 `outNow`가 true가 되면 `passenger(pid, ack=True)`를 보낸다. 끝까지 달려 탑승한다.
    - 출력: 전화 요청 로그 수(0이어야 함), 운전자 음성
- [X] T010 [US1] 에뮬레이터에서 `handsfree`·`fallback`·`ack`를 돌리고 결과를 메모한다(T030에서 verification에 적는다).

---

## Phase 4: User Story 2 - 원래 목적지로 이어 가기 (P1)

**Goal**: 원래 목적지가 있으면 경유지로 끼워 넣고, 탑승하면 경유지만 지운다. 운전석 문은 탑승 판정에서 뺀다.
**Independent Test**: `continue`(탑승 뒤 내비 목적지 "집", 경유지 없음, 음성 "탑승 확인. 다음 목적지로…")

- [X] T011 [US2] `PickTest.kt`에 `ContinueTest`를 먼저 쓴다.
  - `Route("집", null)` 뒤 탑승하면 문장이 "민지님 탑승 확인. 다음 목적지로 안내할게요"이고 `ClearNavi`가 있다.
  - `Route(null, null)`(또는 없음)이면 1차 문장이다.
  - `Route(null, "집")`이면 `lostDest`가 "집"이다.
  - 운전석 문(`ROW_1_LEFT`)이 열렸다 닫히고 좌석이 차도 탑승이 아니다. 조수석 문이면 탑승이다.
- [X] T012 [US2] `Pick.kt`에 `Route` 전이, 탑승 문장, 운전석 문 무시를 만든다. T011을 통과시킨다.
- [X] T013 [US2] `app/src/main/java/kr/skku/sameside/PickupService.kt`에 내비 방식을 만든다(data-model §7의 공통 규칙과 SetNavi·ClearNavi, research R15).
  - SetNavi마다 토큰을 두고, 콜백은 토큰·픽업이 같고 끝나지 않았을 때만 다음 단계를 실행한다.
  - 대기 중 ClearNavi는 대기를 버리고 내비를 건드리지 않는다.
  - 경유지 방식이면 `removeWaypoint(ALL)` → `onWaypointChanged`(REMOVE) 또는 3초 → 다시 넣기다.
  - 아니면 `getDestinationInfo`·`getWaypointInfo`를 함께 묻고, 두 답이 모이면 바로, 아니면 3초에 정한다.
  - 경유지 방식은 `addWaypoint(진입 경유지, FIRST)` → `onWaypointChanged`(ADD) → `addWaypoint(지점, SECOND)`다.
  - 목적지 방식은 1차 순서이고, `navOwn`을 저장한다. `app/src/main/java/kr/skku/sameside/People.kt`에 `loadNavOwn(ctx): String?`와 `saveNavOwn(ctx, name)`을 더한다(같은 SharedPreferences "people", 키 `navOwn`).
  - 정한 뒤 `Event.Route`를 넣는다.
  - 리스너에 `onDestinationInfo`, `onWayPointInfo`를 더한다.
  - `ClearNavi`는 경유지 방식이면 `removeWaypoint(ALL)`, 아니면 `cancelRoute()`다.
  - 경유지 방식인지는 서비스가 픽업마다 들고 있다. 새 픽업이 시작되면 false다.
- [X] T014 [P] [US2] `app/src/main/java/kr/skku/sameside/Screens.kt`의 `going`에 `lostDest`가 있으면 작은 글자 "원래 목적지({lostDest}) 대신 만날 곳으로 안내해요"를 더한다.
- [X] T015 [US2] 시연 도구를 고친다(quickstart "바뀐 reset", 새 장면, 위임 결정 D3·D5).
  - `app/src/debug/java/kr/skku/sameside/DemoReceiver.kt`에 `waypoint` 명령을 더한다. `--ed lat --ed lon --es name`으로 `addWaypoint(…, FIRST)`다.
  - `scripts/demo.py`
    - `navi(cmd, **extras)` 도우미를 더한다. `DemoReceiver` broadcast이고, `state`는 `DemoReceiver` 로그에서 목적지·경유지를 읽어 돌려준다.
    - `reset`: 앱을 멈추기 전에 `navi("route", lat=37.3002852, lon=126.9711778, name="시연대기")`를 부르고 5초 기다린다.
      - 이름에 공백을 쓰지 않는다. `adb shell`은 인자를 공백으로 이어 붙여 기기 셸에 넘기므로 "시연 대기"는 둘로 잘린다.
      - 다시 쓰는 `people.xml`에 `<string name="navOwn">시연대기</string>`을 넣는다.
      - 끝에 `navi("state")`로 내비 목적지가 "시연대기"인지 확인해 출력한다.
      - 콘솔 깜빡이를 `Off`로 둔다.
    - `continue`: `reset`이 끝난 뒤 `navi("route", lat=37.2839144, lon=126.9896135, name="집")`, 8초 대기. 그다음은 `opposite`와 같다.
      - 확인 20초 뒤와 탑승 15초 뒤에 `navi("state")`를 출력한다(구현 중 조정: 내비가 경유지를 하나 넣을 때마다 약 6초씩 경로를 다시 잡고, 그사이 목적지가 빈 값으로 읽혔다).
    - `continue-cancel`: `continue`처럼 확인한 뒤 10초 기다렸다가 P에서 "픽업 취소"를 누르고, 10초 뒤 `state`를 출력한다.
    - `continue-full`: "집"을 넣은 뒤 `navi("waypoint", lat=37.2884, lon=126.9862, name="약국")`, 5초 대기. 그다음 확인하고 10초 뒤 `state`와 차 화면 문구를 출력하고, P에서 취소한다.
- [X] T016 [US2] 에뮬레이터에서 `continue`, `continue-cancel`, `continue-full`을 각각 `reset` 뒤에 돌린다. quickstart의 기대(목적지·경유지 값, 차 화면 줄)와 맞는지 확인하고 메모한다.

---

## Phase 5: User Story 3 - 도착 알림 확인 (P2)

**Goal**: 지점 50m 안에서 오른쪽 깜빡이를 켜고 3초 서면 묻고, P에서 보내기를 누르면 페이지에 도착 띠
**Independent Test**: `arrive`

- [X] T017 [US3] `PickTest.kt`에 `ArriveTest`를 먼저 쓴다.
  - 50m·오른쪽·3초면 질문 한 줄과 `arriveCard`가 생긴다. 한 번만이다.
  - 왼쪽·꺼짐·50m 밖·2초는 묻지 않는다.
  - 순서가 바뀌어도 묻는다. 서 있다가 깜빡이를 켜면 `TurnSignal` 이벤트에서, 깜빡이를 켠 채 서면 `Tick`/`CarPos`에서다.
  - P에서 `Arrive(true)`면 `arrived`·`Push`와 카드 닫힘이다. D에서는 무시한다. `Arrive(false)`면 보내지 않고 카드를 닫는다. 카드 없이 온 `Arrive`는 무시한다.
- [X] T018 [US3] `Pick.kt`에 도착 확인과 `Arrive`, `TurnSignal` 전이를 만든다. `stopped`에 창 길이 인자를 더한다(기본 5초, 도착 확인은 3초). T017을 통과시킨다.
- [X] T019 [US3] `PickupService.kt`에서 깜빡이를 구독한다(research R16, DRIVE DNA `CarWatcher.watchTurnSignal`과 같은 방식).
  - `TurnSignalValue.RIGHT`면 `Event.TurnSignal(true)`, 그 밖은 false다.
  - 시작 실패 처리는 문·좌석과 같다. 값이 올 때 `Log.i(TAG, "깜빡이 = $v")`를 남긴다.
  - `app/src/main/AndroidManifest.xml`에 `pleos.car.permission.CAR_LIGHTS`를 더한다.
- [X] T020 [P] [US3] `Screens.kt`의 `going`에 확인 카드를 만든다(contracts/ui.md §1). 보내기·아니요는 `PickupService.send(Event.Arrive(true|false))`이고, P에서만 눌린다.
- [X] T021 [P] [US3] `server/page.html`에 `arrived` 띠를 더한다. 켜질 때 소리 2번이고, 띠 순서는 contracts/ui.md §3이다.
  - 문구: `state.arrived && state.crossOver`면 "차가 길 건너편에 도착했어요. 가까운 횡단보도로 건너 주세요"(위임 결정 D1), 아니면 "차가 도착했어요. ○○ 앞으로 오세요"
- [X] T022 [US3] `scripts/scenes.txt`에 `arrive car`(= `opposite car`), `arrive route`(= `opposite route`), `arrive passenger 37.2979552 126.9713407`을 더하고, `scripts/demo.py`에 `arrive` 장면을 더한다.
  1. `opposite`처럼 달린다.
  2. 끝에서 `vc("turn-signal?mode=BlinkRight")` 한 뒤 질문 로그를 기다린다(15초). 마지막 위치를 쓴 때부터 질문 로그까지의 초를 출력한다(합격 8초 이하, SC-010).
  3. `gear("Park")`, `vc("turn-signal?mode=Off")`, `front()`, `tap("보내기")` 한다(구현 중 조정: 깜빡이를 켜 두면 에뮬레이터의 측방 카메라 창이 버튼을 가린다).
  4. 서버 `arrived`를 확인한다.
  5. 탑승하고 `finish`한다.
  - `reset`에 `turn-signal?mode=Off`를 더한다.
- [X] T023 [US3] 에뮬레이터에서 `arrive`를 돌리고 메모한다. 정차부터 질문까지 8초 이하, 보내기부터 서버 `arrived`까지 5초 이하, 페이지 "올리브영을 지나…" 문장이 기준이다.

---

## Phase 6: User Story 4 - 누가 건널지 비교 (P2)

**Goal**: 길을 건너야 하는 지점에 "건너기 1분 · 유턴 m분"과 페이지 한 줄
**Independent Test**: `opposite`의 카드와 페이지

- [X] T024 [US4] `PickTest.kt`에 `CompareTest`를 먼저 쓴다(합성 지역에 `CORNER` 점을 둔다).
  - 600m 안 가장 가까운 모퉁이로 계산한 `uturnSec`
  - 601m면 null
  - 길을 건너지 않는 지점도 `uturnSec`은 계산되지만, 카드 문구를 정하는 순수 함수 `compareLine(spot)`이 null을 준다.
  - `compareLine` 문구 "건너기 1분 · 유턴 4분"
  - 시연: `opposite`의 1순위 세븐일레븐에 `uturnSec`이 있다.
- [X] T025 [US4] `Pick.kt`의 `choose`에서 `uturnSec`을 채우고 `compareLine`을 만든다. T024를 통과시킨다.
- [X] T026 [US4] 화면 두 곳에 비교 줄을 붙인다(T025가 만든 `compareLine`을 쓴다).
  - `Screens.kt` 지점 카드: `compareLine`이 있으면 작은 글자
  - `server/page.html`의 `spots`·`going` 지점 이름 아래: `crossing === 1 && uturnSec != null && mins(uturnSec) > 1`이면 "{이름}님이 길을 건너는 게 차가 유턴하는 것보다 {mins(uturnSec) − 1}분 빨라요"

---

## Phase 7: User Story 5 - 지나서 문구 (P3)

**Goal**: 150m 음성 "오른쪽 ○○ 지나서 △△ 앞에 세우세요", 페이지 "○○을 지나 △△ 앞 인도에 서 계세요"
**Independent Test**: `opposite`·`handsfree`의 150m 음성과 페이지

- [X] T027 [US5] `PickTest.kt`에 `PassingTest`를 먼저 쓴다.
  - 합성 지역에서 같은 길·같은 길가·20~150m의 가장 가까운 것을 고른다.
  - 19m·151m·반대 길가·다른 길은 아니다.
  - 길 선 반대 방향으로 오는 지점(왼쪽 길가)은 "앞쪽"이 반대다.
  - `toObject`: 올리브영 → 을, 세븐일레븐 → 을, GS25 → 를, CU → 를, 이마트24 → 를, 7번가 → 를
  - 태울 사람이 기준보다 차가 오는 쪽이면 `standLine`에 "지나"가 들어가고, 아니면 빠진다.
  - 150m 오른쪽 판정 문장: 기준 있음, 기준 없음, 기준이 있지만 차가 이미 지남(옆길에서 기준과 지점 사이로 꺾어 들어옴 → 1차 문장)
  - 시연: 세븐일레븐의 `passing`이 올리브영이다.
- [X] T028 [US5] `Pick.kt`의 `choose`에서 `passing`·`standLine`을 채우고, `going()` 150m 문장(차가 기준을 지났는지 포함)을 고친다. T027을 통과시킨다.
- [X] T029 [P] [US5] `server/page.html`의 `going`에서 "차는 이쪽 길로 와서 여기 서요" 다음 줄에 지점의 `standLine`을 보인다.

---

## Phase 8: Polish & 검증

- [X] T030 에뮬레이터에서 1차 장면 전부와 새 장면을 **장면마다 `reset` 뒤에** 돌린다.
  - 1차 장면: `opposite`, `handsfree`, `fallback`, `reverse`, `wrongside`, `transit`, `transit-small`, `stop`, `cancel`, `decline`, `nospot`
  - 새 장면: `ack`, `arrive`, `continue`, `continue-cancel`, `continue-full`
  - `logcat -b crash`를 확인한다.
  - 새 화면(지점 카드 비교 줄, 도착 확인 카드, 페이지의 지금 나가요·도착 띠·비교 줄·서 있을 곳 문장)을 `docs/screenshots/`에 더한다.
  - 반대 방향 도착 띠는 1차 verification처럼 서버 API로 상태(`crossOver: true`, `arrived: true`)를 넣어 페이지 문구만 확인한다.
- [X] T031 [P] `specs/002-arrive-and-continue/verification.md`를 쓴다.
  - 테스트 수, 장면 결과, SC-008~012, 1차 SC 유지
  - 확인하지 못한 것: 실차 내비 경유지 동작, 실차 깜빡이·비상등, 음성 입력, 로드뷰
- [X] T032 [P] `README.md`를 고친다.
  - 기능 표에 2차 줄을 더한다. 1차 번호(F2 랜드마크, F3 위치, F5 자동 전화)와 겹치지 않게 "2차" 머리를 붙인다: "2차 · 누가 건널지 비교", "2차 · 지나서 문구", "2차 · 도착 알림 확인", "2차 · 원래 목적지로 이어 가기".
  - 1차 F5 줄의 설명은 2차 동작(알림 먼저, 60초 반응이 없을 때만 전화)으로 고친다.
  - 장면 목록에 새 장면을 더하고 한계를 고친다.

---

## Dependencies & Execution Order

- Setup(T001) → Foundational(T002~T005) → US1(T006~T010) → US2(T011~T016) → US3(T017~T023) → US4(T024~T026) → US5(T027~T029) → Polish
- `Pick.kt`·`PickTest.kt`를 모든 이야기가 고치므로, 이야기는 순서대로 한다. 같은 이야기 안의 `page.html`·`Screens.kt` 작업([P])은 Kotlin 판단 작업과 동시에 할 수 있다.
- US4·US5의 `choose` 변경은 `Spot` 기본값(T003) 위에서 한다.

## Parallel Opportunities

- T004(server.py)는 T003·T005와 동시에 할 수 있다.
- 이야기마다 페이지·화면 작업([P])

## Implementation Strategy

1. **MVP**: Foundational + US1(두 단계 전화). 기존 자동 전화의 동작이 바뀌므로 먼저 고정한다.
2. **P1 완성**: US2(원래 목적지)
3. **P2**: US3(도착 확인), US4(비교)
4. **P3**: US5(지나서 문구)
5. **워크플로**: 구현 뒤 ponytail-review → /code-review → /security-review(서버 필드가 바뀜)를 한다.
