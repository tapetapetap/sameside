# Tasks: 같은편 6차 — 차 화면 개선과 주행 중 한 줄 카드

**Input**: `specs/006-car-screen/`의 spec.md, plan.md, research.md, contracts/ui.md, quickstart.md
**작업 위치**: `/Users/jiwon/orca/workspaces/unused-feature-app/car-sameside`(브랜치 `car-sameside`). 코드 폴더는 `app/src/main/java/kr/skku/sameside/`(아래 `src/`로 줄임).
**Tests**: 한 줄 글 고르기만 새 단위 테스트(헌법 VI). 화면은 에뮬레이터로 확인한다.

## Phase 1: Setup

- [X] T001 브랜치 `car-sameside`, 태그 `backup/before-car-sameside`(= pickup cd40c13)를 확인하고, 바꾸기 전 `./gradlew -q :app:testDebugUnitTest`(153개)와 `python3 server/server.py check`가 통과하는지 본다
- [X] T002 `.specify/memory/constitution.md`를 1.1.0으로: 원칙 I 끝에 "주행 중 화면은 움직이지 않는 한 줄 카드 하나(남은 시간과 다음 사람 상태)이고 누르기를 받지 않는다. (1.1.0: 6차 주행 중 한 줄 카드. 1.0.0은 '운전자가 화면을 보게 하지 않는다')"를 더하고 첫 문장 "주행 중(기어가 P가 아닐 때)에는 운전자가 화면을 보게 하지 않는다"를 "주행 중(기어가 P가 아닐 때)에는 한 줄 카드 말고는 화면을 보게 하지 않는다"로 고친다. 제목은 "### I. 운전 중에는 듣고 한 줄만 본다"로 바꾼다. Version 줄 `1.1.0 | … | Last Amended: 2026-11-13`

## Phase 2: Foundational

- [X] T003 한 줄 글 테스트를 먼저 쓴다: `app/src/test/java/kr/skku/sameside/GlanceTest.kt` — contracts §1 표의 줄마다 하나(픽업 없음, 만드는 중, 한 명 링크 네 줄, 여러 명 링크, 만날 곳 3곳, 만날 곳 0곳 + 차례 계획, 자리 없음 한 명·여러 명, 가는 중 READY "민지님 · 나와 있어요"와 분, CALLING "통화 중"(초 없음), NOTIFIED "나오라고 알렸어요", CALLED "전화를 걸었어요", 차례 둘째 지점 사람, 모두 탐, MET·CANCELED·EXPIRED, STOPPED(`lastLeft`가 있을 때 그 사람, 없을 때 첫 사람)). 픽업은 기존 테스트(`ChainTest.kt`, `GroupTest.kt`)의 만들기 방식을 따르고, `contactOf`는 고정 `Contact`를 돌려주는 람다로. 돌려서 `glance`가 없어 실패하는 것을 확인한다
- [X] T004 [P] 색 토큰(contracts §3): `app/src/main/res/values/colors.xml`에 `text3` #FF62656D, 새 `green_text` #FF017A40, `amber_text` #FFA35A00, `blue_text` #FF0050D8. `values-night/colors.xml`에 새 `green_text` #FF00E074, `amber_text` #FFF2A33A, `blue_text` #FF6CB4FF
- [X] T005 `src/Ui.kt`: `small` 24sp, `chip` 24sp·여백 20·10·22·10·아이콘 26, `banner` 24sp, `avatar` 기본 크기 48 → 58. `src/Screens.kt` `button`: `minimumHeight`·`minHeight`·`minimumWidth`·`minWidth` 76dp, 여백 가로 28·세로 0, 보조 버튼 글자 `R.color.blue_text`

**Checkpoint**: `assembleDebug`는 되고, `testDebugUnitTest`는 GlanceTest가 없는 `glance`를 불러 테스트 컴파일에서 실패한다(T003 확인).

## Phase 3: User Story 1 - 운전 중에도 남은 시간과 그 사람 상태를 본다 (P1) 🎯 MVP

**Goal**: FR-098~101.
**Independent Test**: quickstart §2.

- [X] T006 [US1] `src/Pick.kt`: `data class Glance(val minutes: Int?, val line: String)`, `fun shortStatus(k: ContactKind): String`(contracts §1), `fun glance(p: Pickup?, creating: Boolean, contactOf: (Member) -> Contact): Glance`를 더해 T003을 통과시킨다. 한 명 링크는 지금 `linkLine(p)`를 그대로 쓴다. STOPPED 이름은 정차 화면과 같은 `p.lastLeft?.person?.name ?: p.person.name`
- [X] T007 [US1] `src/Screens.kt`: `fun Ui.glanceCard(g: Glance): View` — `card`(여백 40)에 가로 줄 간격 32로 남은 분(72sp 800 text1, 있으면)과 한 줄(32sp 700 text1, 남은 폭). 파일 안 `private var dialog: AlertDialog?`를 `show`가 채우고 `d.setOnDismissListener { dialog = null }`, `fun closeDialog()`가 열려 있으면 닫고 비운다
- [X] T008 [P] [US1] `app/src/main/res/layout/activity_main.xml`과 `layout-port/activity_main.xml`: 사람 칸 바깥 ScrollView에 `android:id="@+id/peopleScroll"`, 구분선 View에 `android:id="@+id/divider"`
- [X] T009 [US1] `src/MainActivity.kt`: `private var cancelUntil = 0L`을 선언한다(T016이 쓴다). `render`: `park`가 아니면 `closeDialog()`, `cancelUntil = 0`, `peopleScroll`·`divider`를 `GONE`, `replace(R.id.screen, column(배너(서버 끊김이면) + ui.glanceCard(glance(p, PickupService.creating, contactOf)))`)로 하고 끝낸다. `park`면 둘을 `VISIBLE`로 하고 지금 정차 화면. `contactOf`는 지금 `situation`에 넘기는 람다와 같다. `onDestroy()`를 더해 `closeDialog()`를 부른다
- [X] T010 [P] [US1] `app/src/main/AndroidManifest.xml` MainActivity에 `<meta-data android:name="distractionOptimized" android:value="true" />`를 넣고, 위의 옛 주석("정차 중에만 조작하는 화면이라 DO(주행 중 사용 허용)를 선언하지 않는다(원칙 I)")을 "6차: 주행 중에는 움직이지 않고 누를 것 없는 한 줄 카드만 보이므로 DO를 선언한다(원칙 I 1.1.0)"로 바꾼다

**Checkpoint**: quickstart §2의 3·6이 통과한다(D에서 한 줄 카드, 입력 창 닫힘).

## Phase 4: User Story 2 - 차에서 읽고 누를 수 있다 (P1)

**Goal**: FR-102~104.
**Independent Test**: quickstart §3.

- [X] T011 [US2] `src/Screens.kt` `people`: "여러 명 함께" 24sp·최소 높이·폭 76dp·여백 가로 24·세로 0·`gravity = Gravity.CENTER`·꺼짐 글자 `blue_text`, 제목 줄 간격 24, 사람 줄 `minimumHeight` 76dp·전화번호 24sp, 바깥 `column` 간격 18 → 24, "가까이만" 칩 글자 `blue_text`
- [X] T012 [US2] `src/Screens.kt` 나머지 고정 글자(contracts §2): 링크 글 24sp(한 명·여러 명), 도착 확인 버튼 줄 간격 24, 차례 카드 제목 28sp, 대중교통 둘째 줄 24sp, 차례로 카드 지점 목록 24sp, `error` 24sp
- [X] T013 [P] [US2] `src/Views.kt` 빈 자리 규칙: `fun freeIndex(candidates, taken, w, h): Int`(지금 `firstFree`의 `indexOfFirst`, 없으면 −1)를 더하고 `firstFree = freeIndex(…).coerceAtLeast(0)`로 바꾼다. `place(…, optional: Boolean = false)`는 `optional`이고 −1이면 그리지 않고 끝낸다. 역 이름(`PeopleMap` stations), 노선 글(legs), 지나는 곳(`SpotMap`·`StopMap` passing)을 `optional = true`로 부른다. `app/src/test/java/kr/skku/sameside/GroupTest.kt`의 "빈 자리가 없으면 첫 자리" 옆에 `assertEquals(-1, freeIndex(…))` 한 줄. 지도 글자(contracts §2): `reserveCorners`·`mapName` 13 → 20sp(상자 높이·너비를 글자에 맞춰 `dp(34f)` → `dp(40f)` 등), `corners` N 20sp·축척 20sp·출처 14sp, `SpotMap` 17·19 → 20sp, `PeopleMap` 17·14·15 → 20sp, `StopMap` 17·15·14 → 20sp
- [X] T014 [P] [US2] `src/Views.kt` `ContactRow` 상태 글 24sp·초읽기 숫자 20sp, `ChainStrip` 노드 안 첫 글자 그리기와 `initial` 페인트를 지운다

**Checkpoint**: quickstart §3이 통과한다.

## Phase 5: User Story 3 - 낮·밤 모두 또렷하다 (P1)

**Goal**: FR-105.
**Independent Test**: quickstart §3의 낮·밤 캡처와 대비 표.

- [X] T015 [US3] `src/Views.kt`: `PeopleMap` 사람 이름표 글자색 `AVATAR_LINES[m.slot]` → `R.color.text1`(점·점선은 그대로), `SpotMap` 사람 이름표 글자색 `green` → `R.color.green_text`, `StopMap` "여기 세워요" 글자색 `blue` → `R.color.blue_text`, `ContactRow` 아이콘 색과 초읽기 숫자 green/amber → `green_text`/`amber_text`(물결·링 선은 그대로). 글자에 `R.color.text4`를 쓰는 곳이 있으면 `text3`으로(`grep -n text4 src/*.kt`)

## Phase 6: User Story 4 - 실수로 취소하지 않고, 보던 것을 잃지 않는다 (P2)

**Goal**: FR-106~108.
**Independent Test**: quickstart §4.

- [X] T016 [US4] `src/Screens.kt` `situation`에 `cancelArmed: Boolean` 인자: 참이면 취소 버튼 글자 "한 번 더 누르면 취소해요", 글자색 `R.color.red_text`. `src/MainActivity.kt`(T009에서 선언한 `cancelUntil`): `render`가 정차 화면에서 `p == null || p.status.ended`이면 `cancelUntil = 0`, `onCancel`이 확인 중이면 `cancelUntil = 0`과 `PickupService.send(Event.Cancel)`, 아니면 `cancelUntil = SystemClock.uptimeMillis() + 4000`, `render()`, `window.decorView.postDelayed({ render() }, 4000)`
- [X] T017 [US4] `src/MainActivity.kt`: `@Deprecated("Deprecated in Java") override fun onBackPressed()` — `together`면 `together = false; picked.clear(); tooMany = false; render()`, 아니면 `super.onBackPressed()`
- [X] T018 [US4] `src/MainActivity.kt`: `onSaveInstanceState`에 `together`와 `People.encode(picked)`, `onCreate`에서 `People.decode(…)` 중 `People.load(this)`에 있는 사람만 차례대로 `distinct()`로 되살린다
- [X] T019 [P] [US4] `scripts/demo.py`: `tap()`이 누른 좌표 `(x, y)`를 돌려주게 하고(지금은 `True`), `def cancel_pickup(): x, y = tap("픽업 취소"); adb("shell", "input", "tap", str(x), str(y))`(버튼 자리가 같으므로 dump 없이 곧바로 두 번째 누름)를 더해 `tap("픽업 취소")`를 모두(지금 8곳) 바꾼다. 결과는 각 장면의 기존 확인(`wait_text` 등)으로 본다

## Phase 7: User Story 5 - 덜 빽빽하고, 문구가 맞는다 (P2)

**Goal**: FR-109·110.
**Independent Test**: quickstart §5.

- [X] T020 [US5] `src/Screens.kt` `situation` SPOTS: 차례로 태우기 카드를 대중교통 강조 카드 바로 뒤로 옮긴다. `spotCard`의 `i > 0`이면 지도 줄과 이유 줄을 넣지 않는다(이름 30sp 그대로)
- [X] T021 [US5] `src/Screens.kt` `going`: 안내 줄 "내비에 만날 곳을 넣었어요", 차례 카드 제목 "차례로 태우는 중 · ${p.spots.size}곳 중 ${p.chosen + 1}번째". `src/Views.kt` `ContactRow.status`의 WAIT "가까워지면 알려요"
- [X] T022 [US5] 바뀐 문구에 기대는 테스트·스크립트를 고친다: `grep -rn '알림 전\|출발하세요\|차례로 태우는 중 ·' app/src/test scripts`
- [X] T022a [US5] `README.md`의 옛 문구와 주행 중 설명을 고친다: 35행 F3("운전자는 주행 중 화면을 보지 않고 음성만"), 53행 연락 줄("알림 전"), 63행("차례로 태우는 중 · 1/2"), 124행("주행 중에는 … 앱은 음성만"), 165행("주행(D) 중에는 연락 줄이 가려집니다")을 6차 동작(주행 중 한 줄 카드)에 맞춘다

## Phase 8: Polish & 검증

- [X] T023 빌드·테스트·서버 점검·설치(quickstart §1)
- [X] T024 에뮬레이터 확인 quickstart §2~5. 캡처를 `/Users/jiwon/Desktop/현대자동차 공모전/화면 점검 1113/고친 뒤/같은편/`에, UI 덤프 크기표를 verification.md에
- [X] T025 장면 회귀 quickstart §6(21개, 기준은 `영상 도구/videos-pickup4/log/<장면>.log`)
- [X] T026 `/impeccable audit`·`critique` 다시, 점수를 verification.md에. 남은 P1은 `/impeccable polish`(contracts 범위 안)
- [X] T027 ponytail-review(`git diff backup/before-car-sameside -- app scripts`) 반영
- [X] T028 `/code-review`(high) 반영, 보안 직접 점검(주행 최적화 선언으로 새로 보이는 정보가 이름과 상태뿐인지, 전화번호가 한 줄 카드·로그에 없는지)
- [X] T029 `specs/006-car-screen/verification.md`: SC-031~037, 캡처 경로, 점수 전후, 리뷰 반영
- [X] T030 로컬 커밋(브랜치 `car-sameside`, 푸시하지 않음)

## Dependencies & Execution Order

- T001·T002 → T003~T005 → 이야기들. T006은 T003 뒤. T009는 T006·T007·T008 뒤.
- US2·US3·US5는 Screens.kt·Views.kt를 같이 고쳐 차례로 한다. US4의 T016은 Screens.kt `situation`과 MainActivity를 고쳐 US1(T009) 뒤에 한다.
- Phase 8은 모두 뒤.

### Parallel Opportunities

- T004(색 XML), T008(레이아웃), T010(매니페스트), T019(demo.py)는 다른 파일이다.
- T013·T014(Views.kt)는 T011·T012(Screens.kt)와 동시에 할 수 있다. T013과 T014끼리는 같은 파일이라 차례로 한다.

## Implementation Strategy

- MVP는 US1(주행 중 한 줄 카드)이다. 사용자 결정이고 가장 크게 바뀐다.
- 글자·칸·색은 안 써본 기능 6차와 같은 도우미 값이다.
