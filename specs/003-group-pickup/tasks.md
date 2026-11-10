# Tasks: 같은편 3차 — 여러 명 함께 태우기, 지도 카드, 눈에 띄는 연락 상태

**Input**: `specs/003-group-pickup/`의 spec.md, plan.md, research.md, data-model.md, contracts/, quickstart.md
**Prerequisites**: 1·2차 구현과 리뷰 완료(47314c5, 단위 테스트 95개, 장면 16개)

**Tests**: 원칙 VI에 따라 판단(`Pick.kt`)과 순수 계산(`Views.kt`의 지도 가장자리 계산)은 JUnit을 먼저 쓰고 실패를 본 뒤 구현한다.
- 새 테스트는 `app/src/test/java/kr/skku/sameside/GroupTest.kt`에 쓴다.
- 1·2차 `PickTest.kt`는 `Member`로 옮긴 이름만 고친다.
- 서버는 `python3 server/server.py check`의 열째 항목을 먼저 쓴다.

**경로 기준**: 저장소 루트 `/Users/jiwon/orca/workspaces/unused-feature-app/pickup`. 빌드는 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 [P] 아이콘 다섯 개를 벡터 드로어블로 더한다. Material Icons(Apache 2.0)이고, 파일 머리 주석에 출처를 적는다.
  - `app/src/main/res/drawable/ic_call.xml`(call), `ic_bell.xml`(notifications), `ic_check.xml`(check), `ic_walk.xml`(directions_walk), `ic_train.xml`(train)
  - 24dp, tint는 쓰는 곳에서 준다.
- [X] T002 [P] `app/src/main/AndroidManifest.xml`에 `android.permission.READ_PHONE_STATE`를 더한다(research R22).
- [X] T003 [P] 지도·연락 줄 색을 contracts/ui.md §6 표대로 더한다. 밝은 값은 `app/src/main/res/values/colors.xml`, 다크 값은 `values-night/colors.xml`이다.
  - `green`은 다크 파일에 이미 있으므로 밝은 파일에만 더한다(중복 자원이면 빌드가 깨진다).
  - `avatar1~5`는 밝은 파일에만 둔다.
  - 이미 있는 이름(`blue`, `blue_soft`, `line`, `text1~3`)은 그대로 쓴다.

---

## Phase 2: Foundational (Member 나누기, 모든 이야기 전에)

- [X] T004 `app/src/main/java/kr/skku/sameside/Pick.kt`에서 `Pickup`을 `Pickup` + `Member`로 나눈다(data-model §1).
  - 사람 값(passenger, offer, transitAccepted, walkSec, called, readySpoken, lateSpoken, notifiedAt, notifiedPos, outNow, wrongSide)은 `Member`로 옮긴다.
  - `Pickup(id, key, person, now)` 보조 생성자, `group`, `lead`, `active`, 그리고 `id`·`key`·`person`·`passenger`(lead) 접근자를 둔다.
  - `Pick` 안의 사용처는 `p.lead.…`로 바꾼다. 이 작업에서 판단은 바꾸지 않는다.
- [X] T005 `app/src/main/java/kr/skku/sameside/{Relay,Screens,PickupService}.kt`의 사람 값 사용처를 `p.lead.…`로 바꾼다(컴파일만).
- [X] T006 `app/src/test/java/kr/skku/sameside/PickTest.kt`의 사람 값 이름을 `p.lead.…`로 바꾼다. `./gradlew :app:testDebugUnitTest`가 95개 통과해야 한다(관문).
- [X] T007 `Pick.choose`에서 사람과 무관한 부분을 `side(area, car, l): Spot?`로 떼어 낸다(data-model §2-1). 한 명 `choose`는 `side`를 쓰고 결과가 같아야 한다. 95개 통과(관문).

**Checkpoint**: 1·2차 동작이 그대로이고 함께 태우기를 얹을 자리가 생겼다.

---

## Phase 3: User Story 1 - 두 사람을 한곳에서 함께 태운다 (Priority: P1) 🎯 MVP

**Goal**: 사람 칸에서 2~3명을 골라 한 지점에서 함께 태운다(걷기).

**Independent Test**: 장면 `group`

### Tests for User Story 1 (먼저 쓰고 실패를 본다)

- [X] T008 [P] [US1] `app/src/test/java/kr/skku/sameside/GroupTest.kt`에 함께 고르기 테스트를 쓴다(data-model §2).
  - `plan`: 걷기 한도 안이면 걷기, 아니면 대중교통(타는 정류장·내린 정류장 모두 한도 안), 둘 다 아니면 null, 가까이만 150m
  - `chooseGroup`: `group` 좌표(research R20)에서 1순위 "세븐일레븐", 두 plan 모두 걷기, `minutes`가 민지 3·준호 2·차 2
  - 이유 줄 "차가 오는 쪽 · 모두 걸어서 · 세워도 되는 곳"
  - 순위: 대중교통 안 5분 가산, 같은 분이면 탈 사람 수 → 걷는 거리 → 건너기
  - 빠진 사람(null)은 고르기에서 뺀다.
- [X] T009 [P] [US1] `GroupTest.kt`에 함께 태우기 전이 테스트를 쓴다(data-model §4).
  - 모두 응답해야 고른다(한 명이 응답 전이면 link 그대로).
  - 거절한 사람은 `out`이고 `EndMember(i)`가 나온다. 모두 거절이면 고르지 않는다.
  - 탑승은 `max(좌석, 페이지)`다: 좌석 1 + 페이지 1(같은 사람일 수 있음) → 만남 아님, 페이지 2 → 만남
  - 만남 문장 "민지님, 준호님이 탔어요", 경유지 방식 "모두 탑승 확인. 다음 목적지로 안내할게요"
  - 가는 중 빠짐 → "준호님은 이번에 함께 타지 않아요", 남은 사람 없으면 그 끝 상태로 끝
  - 도착 확인 문장 "민지님, 준호님께 도착했다고 알릴까요?"
  - 전화 간격: 두 사람 모두 무반응이면 두 번째 전화는 첫 전화 60초 뒤 이후에만
  - 한 명 픽업에서 `Pickup(id, key, person, now)`가 1·2차와 같은 문장을 낸다(회귀).

### Implementation for User Story 1

- [X] T010 [US1] `app/src/main/java/kr/skku/sameside/Pick.kt`에 `Plan`, `Spot.plans`, `plan()`, `chooseGroup()`, `names()`, `Rules.MAX_GROUP = 3`을 더한다(data-model §2). T008이 통과해야 한다.
- [X] T011 [US1] `Pick.kt`의 `Pick.step`에 함께 태우기 전이를 더한다(data-model §4 표).
  - `Event.Remote`에 `who: Int = 0`, `Action.EndMember(index)`, `Member.aboard·out·callAt·phoned`, `Pickup.lastCallAt`
  - 한 명 픽업 경로는 그대로 둔다. T009와 95개가 통과해야 한다.
- [X] T012 [P] [US1] `server/server.py`를 고친다(contracts/api.md).
  - `CAR_FIELDS`에 `"group": list`, `"plan": (dict, type(None))`, `"calling": bool`을 더한다.
  - `check()`에 열째 항목을 먼저 쓰고 통과시킨다. "check: 열 항목 통과"
- [X] T013 [US1] `app/src/main/java/kr/skku/sameside/Relay.kt`를 고친다.
  - `carJson(p, m, car)`: 사람별 값, `group`, `plan`, `calling`(data-model §5). `calling` 판단에 쓸 통화 상태는 인자로 받는다.
  - `remote(j, who)`
- [X] T014 [US1] `app/src/main/java/kr/skku/sameside/PickupService.kt`를 data-model §9대로 고친다.
  - `beginGroup(persons)`: 사람마다 POST하고, 실패한 사람만 2초 뒤 다시 보낸다. 다 만들면 `Pickup(members)`다.
  - 조회는 io 작업 하나가 사람 기록을 차례로 GET한다(`getting` 하나). 404는 `Gone(who)`(Pick에 `Event.Gone`의 `who` 기본 0을 더함), 결과는 `Remote(who)`다.
  - 올리기는 io 작업 하나가 사람마다 차례로 PUT한다(`putting`·`dirty` 하나). 409는 그 사람만 다시 GET이다.
  - `End`는 빠지지 않은 모든 사람에게, `EndMember(i)`는 그 사람에게 보낸다. 재전송 큐는 `(id, key, json)`이다.
- [X] T015 [P] [US1] `app/src/main/java/kr/skku/sameside/Ui.kt`에 `avatar(name)`을 더한다. 첫 글자 원이고, 이름 해시로 `avatar1~5`를 고른다.
- [X] T016 [US1] 사람 칸과 화면을 고친다(contracts/ui.md §1, §2). 대상은 `app/src/main/java/kr/skku/sameside/Screens.kt`와 `MainActivity.kt`다.
  - 사람 줄 아바타
  - "여러 명 함께" 칩, 체크, "n명 함께 데리러 가기", 3명 한도 문구. 고르기 상태는 `MainActivity`에 있다.
  - 함께 링크 카드: "○○님", 진행 점 셋, QR 200px, 링크
  - 함께 카드의 사람별 시간 줄(아이콘)
  - 함께 만날 곳 없음
  - "n명 중 k명 탔어요"
- [X] T017 [P] [US1] `server/page.html`에 "함께 타요" 칩과 함께 만날 곳 없음 문구를 더한다(contracts/ui.md §5).
- [X] T018 [US1] 시연을 고친다. 대상은 `scripts/demo.py`, `scripts/scenes.txt`다.
  - `PEOPLE_XML`을 다섯 명으로(FR-071)
  - `begin_group(names)`: "여러 명 함께" → 이름 누르기 → 버튼 → "○○님" 다음 링크 읽기
  - 장면 `group`(data-model §6). 탑승은 문 → 조수석 → 두 사람 `boarded`

**Checkpoint**: `group`이 "만났어요"로 끝난다.

---

## Phase 4: User Story 2 - 둘 다 대중교통으로 한곳에 모아 태운다 (Priority: P1)

**Goal**: 대중교통으로 모으는 안이 1순위로 뜨고, "근처로 데리러 가면" 비교와 안내가 이어진다.

**Independent Test**: 장면 `group-transit`

### Tests for User Story 2

- [X] T019 [P] [US2] `GroupTest.kt`에 대중교통 함께 테스트를 쓴다.
  - `group-transit` 좌표에서 1순위 "화서역 약국", 두 plan 모두 1호선, 이유 줄 "…1호선으로 모여서…"
  - `nearby`: 같은 지점은 한 번(세븐일레븐 한 번), `minutes` 21. 1순위에 탈 사람이 없으면 비교 줄 없음
  - 두 사람 모두 `transit: accept` → "민지님, 준호님이 1호선 타고 화서역으로 와요" 한 번
  - 대중교통 탈 사람의 문턱은 2분이고, 이동은 반응이 아니다.

### Implementation for User Story 2

- [X] T020 [US2] `Pick.kt`를 고친다. T019가 통과해야 한다.
  - `nearby()`
  - 확정 때 `nearbySec`
  - 함께 태우기 대중교통 받아들임과 한 줄
  - 탈 사람 판정(`plan.leg != null || transitAccepted`)을 알림·전화·`moved`에 쓴다.
- [X] T021 [US2] `Screens.kt`의 함께 1순위 카드에 "근처로 데리러 가면 약 n분"을 보인다. `Relay.kt` `plan` 값(대중교통)도 확인한다.
- [X] T022 [P] [US2] `server/page.html`에 대중교통 안내 카드와 "알겠어요"를 더한다(`transit: accept`, contracts/ui.md §5).
- [X] T023 [US2] `scripts/demo.py`와 `scripts/scenes.txt`에 장면 `group-transit`을 더한다. 좌표 줄과 사람 움직임 시각은 data-model §6 그대로다.
  - `fast`는 시속 80km, `route`는 시속 30km
  - 확정 뒤 두 사람 "알겠어요" + `rail` 첫 점
  - 80km 구간 1/3·2/3에서 `rail` 둘째·셋째 점
  - 30km 구간 시작에 `meet1`·`meet2`와 `ready`. 그 전에 `outNow`가 켜진 사람은 바로 `ready`
  - 탑승: 문 → 조수석 → 두 사람 `boarded`

**Checkpoint**: `group-transit`이 SC-013·SC-014를 만족한다.

---

## Phase 5: User Story 3 - 만날 곳을 카드 안 지도로 본다 (Priority: P2)

**Goal**: 카드와 가는 중 화면에 지점 둘레 지도가 보인다.

**Independent Test**: `opposite`·`group` 카드와 가는 중 캡처

- [X] T024 [P] [US3] `GroupTest.kt`(또는 `MapTest.kt`)에 지도 순수 계산 테스트를 쓴다(data-model §8).
  - `edge(dx, dy, halfW, halfH)`: 안이면 null, 밖이면 사각형 위 점(방향 보존)
  - `distLabel`: 349 → "350m", 1940 → "1.9km", 999 → "1000m"가 아니라 "1.0km"
  - 시연 지점 10곳이 지점 길 선 25m 안이다(SC-016)
- [X] T025 [US3] `app/src/main/java/kr/skku/sameside/Views.kt`(새)에 `SpotMap`과 순수 계산 함수를 쓴다(contracts/ui.md §3, research R24). T024가 통과해야 한다.
- [X] T026 [US3] `Screens.kt` 카드에 지도를 넣고(1순위 230dp, 나머지 150dp), 가는 중에 300dp 지도(차 표시)를 넣는다. `MainActivity.sig`는 지도의 `key`(지점, 사람 점, 차를 5m 단위로 반올림)를 쓴다.

---

## Phase 6: User Story 4 - 알림과 전화가 켜졌는지 한눈에 안다 (Priority: P2)

**Goal**: 차 화면 연락 줄과 페이지 전화 띠가 눈에 띈다.

**Independent Test**: `handsfree`를 P에서 지켜보거나 캡처

- [X] T027 [P] [US4] `GroupTest.kt`에 연락 상태 판정 테스트를 쓴다. data-model §7 표의 열한 행 순서(움직이는 중, 위치로 나와 있음 포함)와 `sec` 계산(NOTIFIED는 0 아래면 0)이다.
- [X] T028 [US4] `Pick.kt`에 `ContactKind`, `Contact`, `contact(m, inCall, callSince, latest, now)`(data-model §7)를 쓴다. T027이 통과해야 한다.
- [X] T029 [US4] `Views.kt`에 `ContactRow`를 쓴다(research R25).
  - 링, 물결, 아이콘, 두 줄 글
  - `uptimeMillis` 위상, `areAnimatorsEnabled`, `key`
- [X] T030 [US4] `PickupService.kt`를 고친다(data-model §9).
  - poll마다 `isInCall`을 읽고(READ_PHONE_STATE, 실패하면 null) 바뀐 때를 통화 시작 시각으로 적는다. 바뀌면 Push + 화면 갱신이다.
  - `CallOrNotify(…, who)`를 실행할 때 `members[who].phoned`(권한 있으면 true, 대체 알림이면 false)를 정한다.
  - 가장 최근 `callAt` + 60초에 한 번 Push + 화면 갱신이다.
  - `carJson(p, m, car, calling(m))`
  - 전화 때 채널 `call`(HIGH, 소리 없음)에 "○○님께 전화 중"을 60초 띄운다(research R23).
  - 첫 실행 권한 요청에 READ_PHONE_STATE를 넣는다(`MainActivity.kt`).
- [X] T031 [US4] `Screens.kt` 가는 중에 사람마다 `ContactRow`를 넣는다. `MainActivity.sig`에 그리기 View `key`를 쓴다.
- [X] T032 [P] [US4] `server/page.html`을 고친다.
  - 전화 띠(`calling`)를 더한다.
  - 전화 띠와 "지금 나오세요" 띠에 맥박 CSS를 넣고, `prefers-reduced-motion`이면 끈다.

---

## Phase 7: User Story 5 - 사람 여럿과 다듬은 화면 (Priority: P3)

**Goal**: 다섯 명, 남은 분·막대, 만남 요약

- [X] T033 [P] [US5] `GroupTest.kt`에 확정 때 `startM`, 막대 비율(경유지 전·후), 만남 걸린 분 테스트를 쓴다.
- [X] T034 [US5] `Pick.kt`에서 확정·만남 때 `confirmedAt`, `startM`, `metAt`을 적고, `progress(p)` 순수 함수를 쓴다. T033이 통과해야 한다.
- [X] T035 [US5] `Screens.kt`를 고친다.
  - 가는 중: 큰 "n분", "○○ 앞 · 이름들", 막대
  - 만남: "이름들 · 확정부터 n분", "근처로 데리러 갔다면 약 n분"

---

## Phase 8: Polish & 검증

- [X] T036 단위 테스트(95 + 3차), `python3 server/server.py check`(열 항목), `assembleDebug`, 설치, READ_PHONE_STATE grant
- [X] T037 에뮬레이터 장면 `reset`(다섯 명 캡처), `group`, `group-transit`. 캡처는 카드 지도, 가는 중, 만남, 링크다. `docs/screenshots/`에 3차 번호로 둔다.
- [X] T038 `handsfree`로 연락 줄(P에서 캡처), `isInCall`(R22), 주행 중 헤드업(R23)을 잰다. 헤드업이 안 보이면 FR-068 코드를 지우고 spec·research에 적는다.
- [X] T039 1·2차 장면 16개와 `tour`를 다시 돌린다(SC-015). 결과는 `specs/003-group-pickup/verification.md`에 적는다.
- [X] T040 ponytail-review로 과설계를 점검하고 반영한다.
- [X] T041 /code-review(high)로 정확성을 점검하고 반영한다.
- [X] T042 보안을 직접 점검한다.
  - 새 서버 필드 타입 검사
  - 페이지 `esc()`
  - 다른 사람 위치를 보내지 않는지
  - 새 권한의 쓰임
- [X] T043 문서를 고친다.
  - `README.md`: 3차 기능, 장면, 화면, 한계(뒷좌석, 헤드업, 상태 표시줄)
  - `specs/003-group-pickup/verification.md`
  - `specs/003-group-pickup/approval-gate.md`(2-1 게이트 때 새로 만든다)의 "승인 뒤 구현 중 바뀐 것"
- [X] T044 저장소 밖: 같은편 전 장면(+ `group`, `group-transit`)을 다시 녹화한다. 자막(음성·전화 시각)과 함께 "같은편 주행 영상" 아티팩트를 갱신한다.

---

## Dependencies & Execution Order

- Phase 1 → Phase 2(관문: 95개) → US1 → US2(US1의 함께 고르기·전이 위) → US3·US4·US5(서로 독립, US1 화면 위) → Phase 8
- US3(지도)와 US4(연락 줄)는 `Views.kt`를 함께 쓰므로 순서대로 한다(T025 → T029).
- 같은 파일(`Pick.kt`, `Screens.kt`)을 고치는 작업은 순서대로 한다.

## Parallel Example: User Story 1

```text
T008 GroupTest 함께 고르기 ┐
T009 GroupTest 전이       ├ 함께(같은 파일이면 한 번에)
T012 server.py check      ┘
T015 Ui.avatar, T017 page.html 칩
```

## Implementation Strategy

- MVP는 Phase 2 + US1이다. 함께 태우기(걷기)만으로 요청 2의 기본이 선다.
- 이어서 US2(사용자 아이디어), US3·US4(요청 3·4), US5(요청 1·5) 차례로 더한다.
- 장면 검증과 녹화는 마지막에 한 번에 한다(에뮬레이터 한 번 켜기).
