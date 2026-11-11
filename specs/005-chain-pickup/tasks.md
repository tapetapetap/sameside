# Tasks: 같은편 5차 — 차례로 태우기와 구간 줄

**Input**: `specs/005-chain-pickup/` (spec, plan, research R42~R49, data-model, contracts/ui.md, quickstart)
**Prerequisites**: 에뮬레이터(Pleos Connect, 2560×1440), 중계 서버 `python3 server/server.py`(8090), `adb forward tcp:8088 tcp:8088`, JDK 17
**작업 위치**: `/Users/jiwon/orca/workspaces/unused-feature-app/pickup` (브랜치 `pickup-chain`). 아래 경로는 이 폴더 기준이다. 코드 경로 앞부분 `app/src/main/java/kr/skku/sameside/`는 `…/`로 줄인다.

**Tests**: 헌법 VI에 따라 판단 로직 테스트를 먼저 쓰고 실패를 본 뒤 구현한다.

## Phase 1: Setup

- [X] T001 `…/Pick.kt` `Rules`에 `const val ROAD_KMH = 40.0`(research R43)을, `Pick` 안에 `private const val ROAD_MPS = Rules.ROAD_KMH / 3.6`을 더한다.

## Phase 2: Foundational (테스트 먼저)

- [X] T002 새 `app/src/test/java/kr/skku/sameside/ChainTest.kt`를 쓴다. 시연 지역(`DEMO`)과 research R42 좌표(group 차, 민지 group p1, 준호 group-transit meet2)를 쓴다.
  - `Pick.chain`
    - 민지·준호 → 지점 2곳, 차례 `seven` → `hwaseo4`(id로 확인)
    - 두 사람이 모두 세븐일레븐 근처(group p1·p2) → null(지점 1곳)
    - 한 사람이 먼 곳(1순위 없음) → null
    - `legs[1]` = dist × 1.3 ÷ (25/3.6) + 60, `destSec` = dist × 1.3 ÷ (40/3.6), dest가 null이면 `destSec` null
  - 전이(`Pick.step`). 함께 태우기 Pickup을 만들어 두 사람 동의·위치와 차 위치를 넣는 방법은 `GroupTest.kt`와 같다.
    - `ConfirmChain`: spots = 차례 지점, chosen 0, `chained`, 사람별 `stop`, `nearbySec` null, `SetNavi(첫 지점)`
    - 첫 지점 사람이 "탔어요" → chosen 1, 민지 `aboard`, `SetNavi(둘째 지점)`, `Speak("민지님이 탔어요. 다음은 준호님, 화서역 4번 출구 앞이에요")`, `etaSec`·`startM`이 둘째 지점 기준으로 바뀜, 지점 값 8개(`passedWaypoint` 등)와 `spokenWhileDriving`이 처음 값
    - 첫 지점에서 착좌로 탐(문 열고 닫음 + 빈자리) → 넘어가고 민지 `aboard`
    - 착좌로 넘어간 뒤 둘째 사람이 빠짐(Gone) → MET
    - 첫 지점 사람이 빠짐 → 바로 둘째 지점으로, `Speak` 한 줄("민지님은 이번에 함께 타지 않아요. 다음은 준호님, …")
    - 둘째 지점 사람도 탐 → MET
  - `memberEta`: 확정 직후 준호 = etaSec + legs[1], 민지가 탄 뒤 민지 = null
  - 알림: chosen 0일 때 준호에게는 "지금 나오세요"가 켜지지 않는다(남은 시간이 문턱보다 크다). 넘어간 뒤(chosen 1) 둘째 지점 가까운 차 위치가 오면 준호 `outNow`가 켜진다
  - 확정 뒤 둘째 지점 `plans`에 준호의 걷기 plan이 있다(서 있을 곳 문장이 준호 것)
  - `Event.Dest`: 함께 태우기 SPOTS면 계획을 다시 만들고(목적지 구간 생김), 한 명 픽업이나 `chained`면 `destPos`만 바뀐다
  - 함께 모이는 곳이 없고 계획만 있으면 SPOTS(NOSPOT 아님)
  - 지금이 아닌 지점의 빠짐: 셋째 지점 사람이 빠지면 그 지점이 지워지고 `legs` 합치기·`stop` 당기기·`memberEta`가 맞다(3명 시험 데이터: 민지·준호에 서연을 더한다. 서연 위치는 `chain` 계산으로 지점 3곳이 나오게 정해 테스트에 적는다)
  - 대상 제한(T011): chosen 0에서 첫 지점 150m 안에 들면 150m 안내 문장에 민지만 있고 준호는 없다. 도착 확인 카드도 지금 지점 사람 기준이다
  - `chainXs`: 첫 값 x0, 끝 값 x1, 증가, 시간이 한쪽으로 몰린 경우 최소 간격
- [X] T003 테스트를 돌려 새 테스트가 컴파일 실패 또는 실패인지 본다.

## Phase 3: User Story 1 - 차례로 태우기를 고른다 (P1) 🎯 MVP

**Goal**: 만날 곳에 차례로 카드와 확정
**Independent Test**: SC-025

- [X] T004 [US1] `…/Pick.kt`에 data-model §1·§2를 더한다: `ChainStop`, `Chain`, `Pick.chain`, `Pickup.chain·chained·destPos`, `Member.stop`, `Event.ConfirmChain`, `Event.Dest`.
- [X] T005 [US1] `…/Pick.kt`
  - private `chooseGroup`에서 지금 계산 뒤 `p.chain = chain(area, car, people, p.destPos)`. `p.spots`가 비어도 `p.chain`이 있으면 `SPOTS`
  - `step`에 `Event.Dest`(data-model §3)와 `Event.ConfirmChain`(§3, `nearbySec = null` 포함)
- [X] T006 [P] [US1] `…/Views.kt`에 최상위 순수 함수 `chainXs`(data-model §5)와 View `ChainStrip`(contracts/ui.md §3, research R48)을 더한다.
  - 입력: 노드별(색 번호, 첫 글자, 이름, 탔는지), 계획 구간 분(그리기용), 표시 구간 분(지난 구간은 null), 목적지 유무, 지금 지점 번호(고를 때 −1)
  - `tag`는 research R48 형식이다.
- [X] T007 [US1] `…/Screens.kt` `situation`의 `Status.SPOTS` 분기를 contracts/ui.md §1대로 고친다.
  - 차례로 카드 `chainCard(p, park, onChain)`
  - `spotCard`의 비교 글은 `p.chain == null`일 때만
  - `situation`에 `onChain: () -> Unit` 인자를 더한다.
- [X] T008 [US1] `…/MainActivity.kt`에서 `onChain = { PickupService.send(Event.ConfirmChain) }`를 넘긴다.
- [X] T009 [US1] `…/PickupService.kt`를 고친다(research R44).
  - `onDestinationInfo`에서 위도·경도가 0이 아니고 이름이 비지 않았으며 `People.loadNavOwn`과 다르면 `handle(Event.Dest(LatLon(lat, lon)))`, 아니면 `Event.Dest(null)`
  - 픽업이 시작될 때(링크 상태가 된 뒤) `navi?.getDestinationInfo()`를 한 번 부른다.

**Checkpoint**: 만날 곳에 차례로 카드가 보이고 누르면 가는 중이 된다.

## Phase 4: User Story 3 - 다음 지점으로 이어 간다 (P1)

**Goal**: 넘어가기, 빠짐, 마지막 만남
**Independent Test**: SC-026, T002 전이 테스트

- [X] T010 [US3] `…/Pick.kt` `board`·`leave`에 차례 분기, `advance`, `dropStop`을 더한다(data-model §3, research R45·R46). 넘어가기 전에(`board`에서만) 지금 지점 사람을 `aboard`로, 넘어갈 때 `etaSec`·`startM`·`expireAt`을 다시 정하고 주행 중 음성 셈을 0으로 돌린다. 음성은 contracts/ui.md §4다.
- [X] T011 [US3] `…/Pick.kt`에 `Pick.memberEta`(data-model §4)를 더하고, `going`의 알림·전화 문턱, 나와 있음 판정, `remoteGroup`의 걷는 시간 갱신을 사람마다 그 사람 지점 기준으로 고친다. 150m 안내 문장, 건너편 경고, 도착 확인은 `chained`이면 지금 지점 사람만이다.

## Phase 5: User Story 2 - 가는 중 구간 그림 (P1)

**Goal**: 가는 중 차례 카드
**Independent Test**: SC-027

- [X] T012 [US2] `…/Screens.kt` `going`을 contracts/ui.md §2대로 고친다: 차례 카드 자리(도착 확인 카드 아래), 요소마다 대상(지금 지점 사람만인 것: 남은 시간 옆 이름, 지도).

## Phase 6: User Story 4 - 태울 사람 페이지 (P2)

**Goal**: 사람별 지점과 시간
**Independent Test**: SC-028

- [X] T013 [US4] `…/Relay.kt` `carJson`을 research R47대로 고친다(T011 뒤): `chained`이면 `chosen` = `m.stop`, `etaSec` = `Pick.memberEta(p, i)`, `arrived`·`crossOver`는 `m.stop == p.chosen`일 때만, `outNow`는 탔으면 false.

## Phase 7: 시연과 확인

- [X] T014 `scripts/scenes.txt`에 `chain` 장면을 더한다(research R49): `car`, `route`(= route1), `p1`, `p2`, `route2`.
- [X] T015 `scripts/demo.py`에 `chain()`을 더한다(`group()`과 같은 틀).
  - 시작: `cont()`처럼 내비에 원래 목적지 "집"을 넣고 기다린다(research R44).
  - 민지는 알림에 `ack`("지금 나가요")로 답한다("나와 있어요" 음성이 늘지 않게).
  - 두 사람 공유 → 만날 곳에서 차례로 카드까지 밀어 올린다(`begin_group`의 스크롤 방식) → 캡처 `chain-cards.png` → "차례로 태울게요" 누름
  - 확정 직후 두 서버 기록의 `chosen`·`etaSec`을 로그에 남긴다(SC-028). 첫 탑승 뒤 민지 기록의 `outNow`가 false인지도 남긴다(FR-095).
  - D → `route` 30km → 첫 지점 `board()` + 민지 "탔어요" → P → `front()` → 3초 → 캡처 `chain-next.png` → D
  - `route2` 40km(준호에게 "나와 있어요") → `board()` + 준호 "탔어요" → P → 만남 확인
  - 로그: 운전자 음성, 서버 두 기록, 내비 목적지·경유지(넘어간 뒤 15초, 만남 뒤 15초)
  - 장면을 `fn` 사전과 맨 위 사용법(docstring)에 넣는다.
- [X] T016 단위 테스트와 빌드(1~4차 133개 + ChainTest), 설치, `reset` 뒤 `chain`을 돌려 SC-025~SC-028을 확인한다. 캡처와 로그는 scratchpad에 둔다.
- [X] T017 1~4차 회귀: `opposite`, `group`, `group-transit`, `waiting`을 돌려 4차 판정과 같은지 본다. 시간이 되면 20장면 전체(SC-030).

## Phase 8: Polish

- [X] T018 [P] `README.md`에 "5차: 차례로 태우기" 절(무엇이 바뀌는지, `chain` 장면, 되돌리기 태그)과 기능 표 한 줄을 더한다.
- [X] T019 ponytail-review로 `git diff backup/sameside-4 -- app scripts`를 본다.
- [X] T020 /code-review로 같은 diff를 본다. 맞는 지적은 고친다.
- [X] T021 보안 직접 점검: 서버·페이지·권한이 바뀌지 않았는지, 새 저장이 없는지 본다.
- [X] T022 `specs/005-chain-pickup/verification.md`를 쓴다(테스트 수, SC-025~030, 캡처 경로, 리뷰, 한계).
- [X] T023 `pickup-chain` 브랜치에 로컬 커밋한다(푸시하지 않음, 사용자 위임 2026-11-12).

## Dependencies & Execution Order

- T001 → T002 → T003 → T004 → T005 → T010 → T011 → T013
- T006(Views.kt)은 T004 뒤 언제든(다른 파일)
- T007·T008·T009는 T005·T006 뒤, T012는 T006·T010 뒤
- T014 → T015 → T016 → T017 → T018~T023

## Implementation Strategy

- MVP는 US1·US3(고르기와 끝까지 이어 가기)이다. 구간 그림(US2)과 페이지(US4)는 그 위에 얹는다.
