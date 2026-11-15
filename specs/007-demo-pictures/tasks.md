# Tasks: 같은편 7차 — 시연용 그림 바로잡기와 제출 전 화면 수정

**Input**: `specs/007-demo-pictures/`의 spec.md, plan.md, research.md, quickstart.md
**작업 위치**: `/Users/jiwon/orca/workspaces/unused-feature-app/car-sameside`(브랜치 `car-sameside`). 코드 폴더 `app/src/main/java/kr/skku/sameside/`는 아래 `src/`로 줄인다.
**Tests**: 새 단위 테스트는 두지 않는다. 바뀌는 것이 그리기와 문구뿐이라 순수 판단 로직이 없다(헌법 VI 대상 아님, research R6). 확인은 Phase 7 캡처와 기존 160개 회귀다.

## Phase 1: Setup

- [X] T001 브랜치 `car-sameside`, 태그 `backup/before-pictures-sameside`(= bd18e8c)를 확인하고, 바꾸기 전 `./gradlew -q :app:testDebugUnitTest`(160개)와 `python3 server/server.py check`가 통과하는지 본다

Foundational 단계는 없다(이야기마다 다른 함수를 고친다). **구현 기준은 spec FR-112~118과 research다.** 2-0.5(구현 전에 바뀌는 화면을 Figma에 그려 2-1 승인의 근거로 쓰는 단계)에서 그린 프레임은 FR과 같은 문구·모양이다. 프레임은 Figma 페이지 "시연용 그림 7차 (1114)"에 있다. ⑰ 첫 화면·사람 있음(https://www.figma.com/design/mme1NBmcgJhFEvmRzMh4IB/?node-id=31-70), ⑱ 첫 화면·사람 0명(https://www.figma.com/design/mme1NBmcgJhFEvmRzMh4IB/?node-id=31-114), ⑲ 지도 출처 바탕·가는 중 사람 색(https://www.figma.com/design/mme1NBmcgJhFEvmRzMh4IB/?node-id=32-73), ⑳ 폰 페이지 지도(https://www.figma.com/design/mme1NBmcgJhFEvmRzMh4IB/?node-id=32-217). 1:1 세로 첫 화면은 같은 상황 칸이 위에 놓일 뿐이라 따로 그리지 않았다.

## Phase 2: User Story 1 - 처음 연 사람도 할 일을 안다 (P1) 🎯 MVP

**Goal**: FR-112~115. **Independent Test**: quickstart §3.

- [X] T002 [P] [US1] `src/Screens.kt`: 새 `private fun Ui.intro(waiting: Boolean, noPeople: Boolean): List<View>`. 안의 `step(n, bg, fg, title, sub)`은 research R2 값(car-unused `Screens.kt` `firstRun()`에서 옮김). `listOf(small("같은편"), headline(제목), step(1, R.color.blue, R.color.on_accent, …), step(2, R.color.blue, R.color.on_accent, …), step(3, R.color.green_soft, R.color.green_text, …))`. 제목·문구는 spec FR-113·FR-114 그대로
- [X] T003 [US1] `src/Screens.kt` `situation`: `waiting` 뒤에 `noPeople: Boolean` 인자, `null ->` 갈래(105행)를 `intro(waiting, noPeople)`로. `src/MainActivity.kt` `render`의 `ui.situation(…)` 호출에 `noPeople = list.isEmpty()`(같은 함수의 `val list = People.load(this)`)

## Phase 3: User Story 2 - 지도 출처가 어느 바탕에서도 읽힌다 (P1)

**Goal**: FR-116. **Independent Test**: quickstart §4 `chain-cards.png`.

- [X] T004 [P] [US2] `src/Views.kt` `MapBase.corners()`(284~286행): research R3 순서대로: textAlign RIGHT → 285행 textSize 14sp → `tw = small.measureText("© OpenStreetMap")` → `fill`을 카드색 alpha 217로 `c.drawRoundRect(w - dp(16f) - tw, h - dp(24f), w - dp(4f), h - dp(2f), dp(6f), dp(6f), fill)`, 다시 alpha 255. 글자색 `R.color.text2`(14sp와 자리는 그대로)

## Phase 4: User Story 3 - 가는 중 지도에서도 사람 색이 같다 (P2)

**Goal**: FR-117. **Independent Test**: quickstart §4 `chain-next.png`.

- [X] T005 [US3] `src/Views.kt` `SpotMap.onDraw()`: `val green`·`val greenText`(320·321행)를 지우고 걷는 점선(330행)·지도 안 점(365행)은 `color(AVATAR_LINES[m.slot])`, 가장자리 화살표(374·376행)는 `for ((group, at, q) in outside)`로 받아 `color(AVATAR_LINES[group[0].slot])`, 이름표(394·403행)는 `color(R.color.text1)`. T004 뒤(같은 파일)

## Phase 5: User Story 4 - 태울 사람 페이지 그림이 가리지 않는다 (P2)

**Goal**: FR-118. **Independent Test**: quickstart §4 폰 페이지.

- [X] T006 [P] [US4] `server/page.html` `drawing()`(113~119행), research R5대로: marker `markerUnits="userSpaceOnUse"` 12×12·`refX="6" refY="6"`·길 `M0,0 L12,6 L0,12 z`. 선은 `ux = Math.sin(h)`, `uy = -Math.cos(h)`로 `(sx − 58ux, sy − 58uy)`부터 `(sx − 22ux, sy − 22uy)`까지(`+sx`·`+sy`로 숫자 셈, `toFixed(1)`), `tail`은 `pts`에 남긴다. "나"·"차" text는 테두리 속성을 달고, 기준선 `my + 24`·`cy + 22`가 `H - 4`를 넘으면 `my - 14`·`cy - 12`에 둔다. 맨 끝에 ★(`x=sx y=sy dominant-baseline="central"` + 테두리)과 출처(`x=W-8 y=H-8 text-anchor="end" font-size="12" fill="var(--sub)"` + 테두리) 그림 맞추기는 research R5 "그림 맞추기"대로 들어오는 쪽 화면 여유를 둔다(2-1 사용자 결정).

## Phase 6: 빌드와 점검

- [X] T007 `./gradlew :app:testDebugUnitTest :app:assembleDebug`(160개 통과)와 `python3 server/server.py check`(quickstart §1)

## Phase 7: 에뮬레이터 확인 — 세 앱이 에뮬레이터 하나를 함께 쓴다. 다른 앱 확인과 차례로 쓴다

- [X] T008 준비(quickstart §2): 다른 앱이 쓰는 중이면 기다린다(`demo.py reset`은 다른 두 앱을 멈춘다). Pleos 2560×1440·density 160(1dp=1px), adb `$HOME/Library/Android/sdk/platform-tools/adb`, 앱 사용자 `--user 10`, 켤 때마다 `adb forward tcp:8088 tcp:8088`, 서버 `python3 server/server.py`, 설치, `python3 scripts/demo.py reset`
- [X] T009 첫 화면 캡처 01~05(quickstart §3): 사람 0명(people.xml 지우기, 가로·1:1 세로), 5명(가로·1:1 세로), 서버 끔 + 링크 만드는 중. 화면 나누기 `adb shell input swipe 2547 720 1500 720 800`, 되돌리기 `adb shell input swipe 1706 720 2550 720 800`
- [X] T010 `SHOT_DIR=… python3 scripts/demo.py chain`(quickstart §4): `chain-cards.png`(차 세울 곳 출처), `chain-next.png`(준호 갈색), 로그 "넘어간 뒤 화면:" 뒤 헤드리스 크롬으로 폰 페이지 06(민지)·07(준호). 이어서 `SHOT_DIR=… python3 scripts/demo.py transit`의 `transit-going.png`(한 명 픽업, 민지가 1호선 위라 지도 밖): 가장자리 화살표가 민지 색(낮 avatar3_line #1D8348)이고 이름표는 text1이다. 묶음 화살표 색은 보여 줄 장면이 없어 확인 범위 밖이다. 끝으로 `SHOT_DIR=… python3 scripts/demo.py group-transit`의 만날 곳 확정 직후(두 사람이 성균관대역 쪽이라 그림이 크게 줄어듦) 폰 페이지 한 장 `08 폰 페이지 - 멀리서(group-transit).png`: 진입 화살표·머리·★이 그림 안에 다 보인다(2-1 결정)
- [X] T011 마무리(quickstart §5): 분할 풀기, `reset`, 서버 끄기, 에뮬레이터는 메인 세션이 세 앱 확인을 다 마친 뒤 한 번 끈다(2-1 결정). 캡처 폴더는 `/Users/jiwon/Desktop/현대자동차 공모전/화면 점검 1115/같은편/`

## Phase 8: 점검·리뷰·기록

- [X] T012 `/impeccable audit`(바뀐 화면만: 캡처 01~07, `chain-cards.png`, `chain-next.png`). P1·P2는 `/impeccable polish`로 Figma 승인 프레임과 어긋나지 않는 범위에서 고치고 T007과 해당 캡처를 다시 한다
- [X] T013 ponytail-review(`git diff backup/before-pictures-sameside -- app server`) 반영
- [X] T014 `/code-review` 반영
- [X] T015 `/security-review`: 페이지 SVG에 새로 들어간 값이 상수 글자와 숫자 좌표뿐인지(이름은 지금처럼 `esc`), 새 권한·네트워크·로그·저장이 없는지, 첫 화면에 전화번호가 새로 나오지 않는지
- [X] T016 `specs/007-demo-pictures/verification.md`: SC-038~043 결과, 캡처 경로, 출처 대비 표(research R3), 리뷰 반영, Figma·spec과 달라진 점
- [X] T016b README 그림(2-1 결정): `docs/screenshots/13-page-going.png`(폰 페이지)와 `34-chain-going.png`(차례 가는 중)를 7차 캡처(07 폰 페이지, `chain-next.png`)로 덮어쓰고, README 51행 "가는 중 큰 지도는 3차 그대로"를 사람 색 그림에 맞게 고친다
- [X] T017 로컬 커밋(브랜치 `car-sameside`, 푸시하지 않음): 코드, `specs/007-demo-pictures/`, `docs/superpowers/specs/2026-11-14-demo-pictures-design.md`, `.specify/feature.json`

## Dependencies & Execution Order

- T001 → 구현. T002 → T003(같은 Screens.kt). T004 → T005(같은 Views.kt). T006은 따로.
- 구현 뒤 T007 → T008~T011(차례로) → T012~T017(차례로).
- **Parallel**: T002(Screens.kt), T004(Views.kt), T006(page.html)은 파일이 달라 동시에 할 수 있다.

## Implementation Strategy

- MVP는 US1(첫 화면)이다. 모든 장면의 첫 장면이고 출시 APK의 첫 실행 화면이다.
- 나머지 셋은 각각 함수 하나만 고친다. 캡처는 에뮬레이터를 한 번 잡아 T009·T010으로 몰아 찍는다.
