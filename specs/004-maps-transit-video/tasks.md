# Tasks: 같은편 4차 — 두 지도 카드, 대중교통이 보이는 화면, 시연 영상 자막·전화 강조

**Input**: `specs/004-maps-transit-video/`의 spec.md, plan.md, research.md, data-model.md, contracts/ui.md, contracts/video.md, quickstart.md
**Prerequisites**: 3차 구현과 리뷰 완료(6752608, 단위 테스트 120개, 20장면 중 19 통과)

**Tests**: 원칙 VI에 따라 순수 계산은 테스트를 먼저 쓰고, 실패(컴파일 실패 포함)를 본 뒤 구현한다.
- 테스트는 새 파일 `app/src/test/java/kr/skku/sameside/CardMapTest.kt`(클래스 `CardMapTest`)에 쓴다. `GroupTest.kt` 272행에 이미 `class MapTest`가 있으니 그 이름은 쓰지 않는다.
- 1~3차 테스트(`PickTest.kt`, `GroupTest.kt`)는 고치지 않는다.

**경로 기준**
- 저장소 루트는 `/Users/jiwon/orca/workspaces/unused-feature-app/pickup`이다.
- 빌드는 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`으로 한다.
- 저장소 밖 영상 도구 폴더는 `$V` = `/Users/jiwon/Desktop/현대자동차 공모전/영상 도구`이고, 커밋하지 않는다.
  - 2026-11-11 사용자 결정으로 세션 폴더(/tmp)에서 이곳으로 복사했다. /tmp는 재부팅 때 지워질 수 있기 때문이다.
  - 경로에 빈칸이 있으니 명령에서는 `"$V/…"`처럼 따옴표로 감싼다.
- **시작 전 확인**: `$V`에 다음이 있는지 본다. 없으면 구현을 멈추고 사용자에게 알린다.
  - 스크립트: `run_rec_steps.sh`, `process_videos.sh`, `extract_voice.py`, `make_vtt.py`, `build_video_page.py`, `files_map.py`, `rec_pickup_fix2.sh`, `preview_overlay.py`
  - 3차 자료: `videos-pickup3/{page.json,verdicts.json,meta.json}`, `videos-pickup3/log/handsfree.*`, `videos-pickup3/web/videos/handsfree.{mp4,vtt,jpg}`

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 [P] 색 자원 data-model §9를 더한다. 둘 다 "4차 지도(specs/004 data-model §9)" 주석을 단다.
  - 밝은 값: `app/src/main/res/values/colors.xml`에 `line1`(`#FF0052A4`)과 `avatar1_line`~`avatar5_line`(`#FF445DBC`, `#FFB67338`, `#FF1D8348`, `#FFB04141`, `#FF743DA8`)
  - 다크 값: `app/src/main/res/values-night/colors.xml`에 같은 이름으로 `line1`(`#FF5C8FF0`)과 `avatar1_line`~`avatar5_line`(원색 `#FF5B7CFA`, `#FFF2994A`, `#FF27AE60`, `#FFEB5757`, `#FF9B51E0`)
- [X] T002 [P] `app/src/main/java/kr/skku/sameside/Ui.kt`의 `AVATARS` 옆에 공개 최상위 `val AVATAR_LINES = listOf(R.color.avatar1_line, …, R.color.avatar5_line)`를 둔다. `avatar()`는 바꾸지 않는다.

---

## Phase 2: Foundational (지도 도우미 나누기, 모든 이야기 전에)

- [X] T003 `app/src/test/java/kr/skku/sameside/CardMapTest.kt`(클래스 `CardMapTest`)를 새로 만들고 축척 테스트를 먼저 쓴다(data-model §6). 실패를 확인한다. 이 실패는 `scaleBar`가 없어서여야 하며, 클래스 이름이 겹친 것이 아니어야 한다.
  - `scaleBar`
    - k = 1.0px/m, 폭 400px이면 100이다.
    - k = 0.1, 폭 400이면 1000이다.
    - k = 4.0, 폭 330이면 20이다.
  - `scaleLabel`: 50 → "50m", 1000 → "1km", 2000 → "2km"
- [X] T004 `app/src/main/java/kr/skku/sameside/Views.kt`에 `scaleBar(k: Double, widthPx: Float): Int`와 `scaleLabel(m: Int): String`을 최상위 함수로 쓴다.
  - 후보는 20·50·100·200·500·1000·2000이다. m × k가 widthPx / 4에 가장 가까운 것을 고른다.
  - T003이 통과해야 한다.
- [X] T005 같은 파일 `Views.kt`에 `abstract class MapBase(context: Context, protected val area: Area, private val heightDp: Float) : View(context)`를 만들고 `SpotMap`의 도우미를 옮긴다.
  - 옮길 것: `stroke`, `fill`, `bold`, `small`, `clip`, `taken`, `onMeasure`, `color()`, `label()`, `box()`, `place()`, `around()`, `arrowHead()`
  - 새 공통 함수(`protected`)
    - `begin(c)`: 둥근 자르기(16dp), `map_bg` 칠하기, `taken` 비우기
    - `roads(c, paths, roadW)`: 테두리 `road_edge` (roadW + 3dp), 안쪽 `road`
    - `pin(c, x, y)`: 3차 핀 모양을 그리고 `taken`에 넣는다.
    - `reserveCorners(w, h, barPx, name: String?)`: 북쪽·축척·출처 칸과, name이 있으면 지도 이름표 칸을 `taken`에 넣는다. `begin` 바로 뒤, 이름표를 놓기 전에 부른다.
      - 칸 크기는 지금 `SpotMap`의 값(`Views.kt` 223~226행) 그대로다.
      - 지도 이름표 칸은 왼쪽 위의 글 폭 + 12dp × 26dp다.
    - `corners(c, barM, k)`: 북쪽 N(오른쪽 위), 축척 막대와 `scaleLabel(barM)`(왼쪽 아래), © OpenStreetMap(오른쪽 아래)을 그리기만 한다.
    - `mapName(c, s)`: 왼쪽 위 이름표를 그리기만 한다. 13sp 굵게 `text2`, 바탕은 `card` 85% 불투명 둥근 사각형이다.
  - `SpotMap`은 `MapBase`를 상속한다. 그림의 순서·크기·색·50m 막대와 칸 예약 시점(이름표보다 먼저)은 그대로다.
    - 222~226행의 예약은 `reserveCorners(w, h, 50f * k, null)`로 바꾼다.
    - 마지막 모서리 그림은 `corners(c, 50, k)`로 바꾼다.
- [X] T006 관문을 확인한다.
  - `./gradlew :app:testDebugUnitTest`: 120개 + T003이 모두 통과
  - `./gradlew :app:assembleDebug`: 성공

**Checkpoint**: 가는 중 지도는 그대로이고, 새 지도 둘이 같은 도우미를 쓸 자리가 생겼다.

---

## Phase 3: User Story 1 — 사람들이 어떻게 오는지와 차를 어디에 세울지를 따로 본다 (P1) 🎯 MVP

**Goal**: 모든 만날 곳 카드에 "사람과 이동 경로"와 "차 세울 곳" 두 지도, 그리고 사람 줄(색 아이콘 + 글)을 보인다(FR-072~076).

**Independent Test**: `opposite`, `group`, `group-transit`의 카드 캡처가 quickstart §2 표와 맞는다(SC-019).

- [X] T007 [US1] `app/src/test/java/kr/skku/sameside/CardMapTest.kt`에 테스트를 먼저 쓰고 실패를 확인한다.
  - `fit`(data-model §3)
    - 점 (0, 0)과 (600, −300)을 400 × 200px, 여백 20에 맞추면, 두 점의 화면 좌표가 모두 [20, 380] × [20, 180] 안에 든다.
    - 점 (0, 0) 하나면 min(w, h) / k = 100이다.
    - 점 (0, 0)과 (10, 0)이면 min(w, h) / k ≥ 100이다.
  - `route`(§2)
    - 걷기는 구간 1개이고, from = 사람, to = 지점이다.
    - 대중교통은 구간 3개이고, 이어지는 점이 같다. 가운데 구간의 leg가 그 노선이다.
  - `colorSlots`(§4)
    - `listOf("민지", "준호", "서연", "할머니", "도윤")` → `[2, 1, 3, 4, 0]`
    - 첫 후보가 같은 두 이름이면 뒤 이름은 다음 번호다. 예: 같은 이름 두 번 → `[a, (a + 1) % 5]`
    - 여섯 이름이면 여섯째는 첫 후보다.
  - `rightOf`(§5): `rightOf(0.0)` ≈ (1, 0), `rightOf(90.0)` ≈ (0, −1). 오차는 1e−9다.
  - `stopGeom`(§5): `Pick.side(DEMO, LatLon(37.2935193, 126.9700141), 세븐일레븐)`로 지점을 만든다. e = `rightOf(spot.heading)`일 때 다음을 확인한다.
    - (band − r)·e ≈ 3.5
    - (stand[0] − r)·e ≈ 8
    - n = 2면 두 서 있을 곳 사이 거리가 4m다.
  - `Pick.howLine`(§8)
    - `("민지", null, 170.0)` → "민지 걸어서 3분"
    - `("준호", 1호선 구간, 700.0)` → "준호 1호선 1정거장(성균관대역 → 화서역) · 12분"(구간은 `DEMO.transit[0]`)
- [X] T008 [US1] 순수 함수를 구현해 T007을 통과시킨다.
  - `app/src/main/java/kr/skku/sameside/Views.kt`
    - `data class Seg`, `route`
    - `data class Fit`, `fit`
    - `colorSlots`
    - `rightOf`
    - `data class StopGeom`, `stopGeom`: `project`, `pointAlong`, `xy`, `headingVec`를 쓴다.
  - `app/src/main/java/kr/skku/sameside/Pick.kt`
    - `Pick.howLine`
    - `headingVec`를 `private`에서 최상위 공개로 바꾼다.
- [X] T009 [US1] `MapPerson`과 `mapPeople`를 바꾼다(data-model §1).
  - `Views.kt`: `MapPerson`을 `class MapPerson(val name: String, val pos: LatLon?, val leg: TransitLeg? = null, val slot: Int = 0) { val line get() = leg?.line }`로 바꾼다. `SpotMap`은 `line`을 그대로 쓴다.
  - `Screens.kt` `mapPeople`가 `leg`와 `slot`을 채운다.
    - leg: 함께 태우기는 `s.plans.getOrNull(i)?.leg`, 한 명은 `m.offer?.leg.takeIf { m.transitAccepted }`
    - slot: `colorSlots(p.members.map { it.person.name })[i]`
- [X] T010 [US1] `Views.kt`에 `class PeopleMap(context, area, spot, people: List<MapPerson>, heightDp) : MapBase`를 쓴다(contracts/ui.md §2).
  - 범위: `fit`. 점은 지점, 위치 있는 사람, leg 있는 사람의 두 역이고, 여백은 40dp다.
  - 길: 폭 max(10m × k, 2.5dp). 지점 길(`spot.road`)은 `blue_soft`로 덧칠한다.
  - 경로: `route`. 걷기 점선은 사람 색(`AVATAR_LINES[slot]`)이고, 탐 실선은 `line1`이다. 같은 leg id는 한 번만 그린다.
  - 역: 네모와 이름, 노선 글 "${line} ${stops}정거장"
  - 핀과 `spot.line`, 사람 점과 이름
  - 이름표 차례: 지점, 사람, 역, 노선 글
  - 이름표 자리 규칙: contracts/ui.md §2 "이름표 자리"(경로 선 예약, 대각선 후보, 노선 글은 선과 수직 양쪽). Figma 그림을 만든 계산 `"$V/fig_geom.py"`가 같은 규칙의 참고 구현이다.
  - 모서리
    - `begin` 바로 뒤: `reserveCorners(w, h, scaleBar(k, w) * k, "사람과 이동 경로")`
    - 끝: `corners(c, scaleBar(k, w), k)`, `mapName(c, "사람과 이동 경로")`
  - `tag`와 `contentDescription`은 §2대로다.
- [X] T011 [US1] `Views.kt`에 `class StopMap(context, area, spot, people: List<MapPerson>, heightDp) : MapBase`를 쓴다(contracts/ui.md §3).
  - 범위: `stopGeom`의 r이 가운데이고 짧은 변의 반이 35m다.
  - 길: 폭 10m. 지점 길은 `blue_soft`로 덧칠한다.
  - 세울 자리 띠: 진행 방향 18m × 3m 회전 사각형. `blue_soft` 채움에 `blue` 2dp 점선 테두리다.
  - 화살표: `arrowFrom` → `arrowTo`, 5dp 선과 화살촉. 선을 따라 반지름 6dp 칸을 예약해 이름표가 피하게 한다.
  - 핀: 띠 가운데에 놓고 "여기 세워요"(`blue`)를 붙인다. 지점 이름(`spot.landmark.name`)도 쓴다.
  - 기준 랜드마크(`spot.passing`): 범위 안이면 그린다.
  - 서 있을 곳: 위치 있는 사람 수 n만큼 사람 색 점
  - 이름표 차례와 후보(contracts/ui.md §3, 사용자 결정)
    1. "여기 세워요": 핀의 `around`
    2. 지점 이름: 서 있을 곳 가운데에서 바깥(e 방향) → 위 → 아래
    3. 기준 랜드마크: 점의 `around`
  - 모서리
    - `begin` 바로 뒤: `reserveCorners(w, h, scaleBar(k, w) * k, "차 세울 곳")`
    - 끝: `corners(c, scaleBar(k, w), k)`, `mapName(c, "차 세울 곳")`
  - `tag`와 `contentDescription`은 §3대로다.
- [X] T012 [US1] `app/src/main/java/kr/skku/sameside/Screens.kt` `spotCard`를 contracts/ui.md §1·§4대로 바꾼다.
  - 두 지도 줄: 가로 `LinearLayout`. `PeopleMap`과 `StopMap`의 폭 가중치는 1, 칸 사이는 12dp다. 높이는 1순위 230f, 나머지 150f다.
  - 사람 줄: `row(10, icon(걷기|열차, 사람 색), small(Pick.howLine(…)))`
    - `icon`이 색 int를 받게 고친다. 색은 `c(AVATAR_LINES[slot])`이다.
    - 함께 태우기는 plan(leg, arriveSec)을 쓴다.
    - 한 명은 이 단계에서 `(null, s.walkSec)`다. 받아들인 뒤의 대중교통 줄은 T019에서 넣는다.
  - 그 아래 줄: `small("차 ${minutes(s.carSec)}분")`, 한 명이면 `Pick.compareLine`, 함께 태우기 1순위면 "근처로 데리러 가면 약 n분"
  - 지금 카드의 `map(s, …, 140.0)` 호출은 지운다. `Ui.map`은 가는 중이 계속 쓴다.
- [X] T013 [US1] `scripts/demo.py`를 고친다.
  - `confirm_first()`: `first = texts()` 바로 뒤에 `snap("cards.png")`를 더한다.
  - `snap()`(317~322행): 파일을 열기 전에 `os.makedirs(d, exist_ok=True)`를 부른다. 지금은 폴더가 없으면 FileNotFoundError로 장면이 멈춘다.
  - `SHOT_DIR`이 없으면 지금처럼 아무 일도 하지 않는다.
- [X] T014 [US1] 에뮬레이터에서 확인한다(quickstart 준비와 §1·§2).
  - 장면마다 `python3 scripts/demo.py reset` 뒤에 돌린다.
    - `SHOT_DIR=$V/shots-004/opposite python3 scripts/demo.py opposite`
    - `group`, `group-transit`도 같은 방식이다.
  - 다크 한 장: `adb shell cmd uimode night yes` → reset 뒤 `SHOT_DIR=$V/shots-004/dark python3 scripts/demo.py group-transit` → `adb shell cmd uimode night no`
    - `group-transit`인 까닭: 1호선 실선과 사람 선 둘 다 대비를 볼 수 있다.
  - 캡처를 quickstart §2 표와 맞춰 본다(SC-019). 다르면 T010~T012를 고친다.

**Checkpoint**: 피드백 1(두 지도, 여러 명)이 화면에서 끝났다.

---

## Phase 4: User Story 2 — 대중교통 장면에서 민지가 1호선을 타고 온다는 것이 차 화면에 보인다 (P1)

**Goal**: 한 명 픽업에서 안을 보냈거나 받아들였을 때 강조 카드를 보이고, 받아들인 카드의 지도·사람 줄이 대중교통이다. 시연 `transit`이 두 상태를 충분히 보인다(FR-077~080).

**Independent Test**: `transit` 캡처 두 장이 quickstart §2와 맞고, 로그 시각이 §3(6초, 10초)을 만족한다(SC-020).

- [X] T015 [US2] `CardMapTest.kt`에 테스트를 먼저 쓰고 실패를 확인한다. 공통 준비는 다음과 같다.
  - car = `LatLon(37.2570158, 127.0198273)`, passenger = `LatLon(37.2991996, 126.9727588)`
  - best = `Pick.choose(DEMO, car, passenger, false).first()`
  - offer = `Pick.transit(DEMO, car, passenger, false, best)!!`
  - 테스트
    - `offer.arriveSec` ≈ `dist(DEMO.center, passenger, leg.board.pos) × 1.3 / 67 × 60 + 240 + 180 + offer.spot.walkSec`(오차 0.01)
    - `Pick.transitNote`: `Pickup(…, Person("민지", "01012345678", false), now)`의 `lead.offer = offer`
      - 받아들이기 전: ("민지님께 1호선 안을 보냈어요", "성균관대역 → 화서역 1정거장 · ${offer.spot.line}에서 만나면 ${offer.gainMin}분 빨라요")
      - `transitAccepted = true`: ("민지님이 1호선 타고 화서역으로 와요", "성균관대역 → 화서역 1정거장")
      - offer가 null이면 null이다.
      - 함께 태우기(사람 2명)면 null이다.
      - `lead.transitDeclined = true`면 null이다.
    - 거절 기록(`Pick.step`, data-model §7)
      - 만들기: `PickTest.kt` 609~625행 `TransitTest`의 받아들임 시험과 같은 방식으로 안이 있는 SPOTS 픽업을 만든다.
      - 거절: 태울 사람 기록 `transit = "decline"`을 `Pick.step`에 주면 다음과 같다.
        - `lead.transitDeclined`가 참이다.
        - `transitAccepted`는 거짓이다.
        - 지점 목록은 그대로다.
        - 말하기와 내비 할 일이 없다.
      - 거절 뒤 받아들임: 그 뒤 `transit = "accept"`가 와도 받아들이지 않는다.
- [X] T016 [US2] `app/src/main/java/kr/skku/sameside/Pick.kt`을 고친다.
  - `Offer`에 `val arriveSec: Double`를 더하고, `Pick.transit`이 계산한 `arrive`를 넣는다.
  - `Pick.transitNote(p: Pickup): Pair<String, String>?`를 data-model §8대로 쓴다(`toParticle` 사용).
  - `Member`에 `var transitDeclined = false`를 더한다. `Pick.step`의 accept 처리(569~571행) 옆에 decline 처리를 둔다. 조건은 상태 SPOTS 또는 GOING, `offer != null`, 아직 받아들이지 않음이다.
  - accept 조건에 `!p.lead.transitDeclined`를 더한다.
  - T015가 통과하고 기존 120개도 통과해야 한다.
- [X] T017 [US2] `Screens.kt`에 `Ui.transitCard(title: String, detail: String): View`를 쓴다(contracts/ui.md §5).
  - 바탕 `blue_soft`, 테두리 2dp `blue`, 모서리 24
  - 48dp `line1` 원 안에 흰 `ic_train`
  - 글: 28sp 700 `text1` / 22sp `text2`
- [X] T018 [US2] `Screens.kt`의 두 화면에 강조 카드를 넣고 옛 한 줄을 지운다.
  - `situation`의 `Status.SPOTS`: `headline("만날 곳")` 다음에 `Pick.transitNote(p)`가 있으면 `transitCard`를 넣고, 카드들 아래 `transitLine` 줄은 지운다.
  - `going`: 도착 확인 카드 다음에 `transitCard`를 넣고, 맨 아래 `transitLine` 줄은 지운다.
  - `private fun transitLine`을 지운다.
- [X] T019 [US2] `Screens.kt` `spotCard`의 한 명 사람 줄을 받아들임에 따라 나눈다.
  - `p.lead.transitAccepted && p.lead.offer != null`이면 `Pick.howLine(이름, offer.leg, offer.arriveSec)`와 열차 아이콘이다.
  - 아니면 `(null, s.walkSec)`와 걷기 아이콘이다.
- [X] T020 [US2] `scripts/demo.py` `transit()`을 FR-080대로 바꾼다.
  1. 지점 카드가 뜨면 `log("카드 뜸")`, `snap("transit-offer.png")`, `time.sleep(6)`
  2. `passenger(pid, transit="accept")`, `log("받아들임 보냄")`
  3. `wait_text(lambda t: "타고" in t and t.endswith("와요"), what="받아들임 문구")`, `log("와요 뜸")`
  4. `snap("transit-accepted.png")`, `time.sleep(10)`, 받아들인 뒤 카드 로그를 남긴다.
  5. `tap("이 자리로 갈게요")` → `front()`(내비가 앞에 와도 앱을 다시 앞으로) → `wait_text(lambda t: t == "남은 시간", what="가는 중 화면")` → `log("가는 중")`, `snap("transit-going.png")`, `time.sleep(5)`
  6. `log("취소")`, `tap("픽업 취소")`
  - 5단계는 사용자 결정(2026-11-11)이다. 가는 중 화면 맨 위 강조 카드를 영상과 캡처로 확인하기 위한 것이다.
  - `log()`는 줄 앞에 시각을 붙인다. quickstart §3은 이 네 줄의 시각 차로 잰다.
  - 옛 조건 `"타고 와요" in t`는 새 문구("…타고 화서역으로 와요")와 맞지 않으므로 지운다.
- [X] T021 [US2] 에뮬레이터에서 확인한다.
  - 각각 reset 뒤에 돌린다. 같은 폴더를 쓰면 `transit-offer.png`가 덮이므로 폴더를 나눈다.
    - `SHOT_DIR=$V/shots-004/transit python3 scripts/demo.py transit`
    - `SHOT_DIR=$V/shots-004/transit-small python3 scripts/demo.py transit-small`
  - 캡처 세 장(`transit-offer.png`, `transit-accepted.png`, `transit-going.png`)을 quickstart §2와 맞춰 본다.
  - 장면 로그 시각으로 §3을 잰다.

**Checkpoint**: 피드백 5(대중교통이 보임)가 화면과 시연에서 끝났다.

---

## Phase 5: User Story 3 — 시연 영상에서 자막과 전화·마이크가 눈에 띈다 (P2)

**Goal**: 20장면을 다시 녹화하고 자막, 전화·마이크 강조, 내비 안내를 영상에 입혀 같은 주소의 주행 영상 페이지를 갱신한다(FR-081~084).

**Independent Test**: `handsfree`와 `decline` 영상의 프레임이 contracts/video.md와 맞는다(SC-021·022). 페이지는 20장면이고 data-cues가 없다.

- [X] T022 [P] [US3] `$V/make_vtt.py`에 내비 안내를 더한다(contracts/video.md §3).
  - 장면 logcat에서 t0 뒤 처음 나온 `PickupService: 기어 P 아님` 시각에 4초 자막을 넣는다: "※ 에뮬레이터 내비는 판교에 고정돼 움직이지 않아요 · 앱은 GPS로 차 위치를 따라가요"
  - 다른 자막 규칙은 그대로다.
- [X] T023 [P] [US3] `$V/burn.py`를 새로 쓴다(contracts/video.md §1·§2, research R37·R38). 사용법은 `python3 burn.py <폴더>`다.
  - 먼저 `$V/process_videos.sh`에 한 줄을 더한다. `NOSPLIT=1`이면 14MB가 넘어도 나누지 않는다(15~18행의 `if`에 조건 추가). burn.py는 나누지 않은 장면 전체 영상을 받아야 VTT 시각이 맞는다.
  - 입력
    - `<폴더>/web/videos/<id>.mp4`마다 처리한다. 나뉜 `<id>-N.mp4`는 받지 않는다. 있으면 "NOSPLIT=1로 다시 처리" 오류를 낸다.
    - 원본은 `<폴더>/web/plain/<id>.mp4`로 옮겨 둔다. 이미 있으면 그것을 입력으로 쓰므로 다시 돌려도 된다.
  - 자막
    - `<id>.vtt`를 읽어 자막마다 PIL PNG를 만든다(띠, 칩, 34px 글자, 1180px 줄바꿈).
    - 글꼴은 `/System/Library/Fonts/AppleSDGothicNeo.ttc` index 6이다.
    - 다음 자막이 시작하면 앞 자막은 그때 끝낸다(contracts/video.md §1).
  - 통화 구간: `log/<id>.logcat`과 `log/<id>.t0`에서 (a, b, c)를 뽑는다(contracts/video.md §2).
  - ffmpeg `filter_complex`
    - 자막: `overlay=0:H-h:enable='between(t,시작,끝)'`
    - 테두리: `drawbox(… color=0xFFD400 … t=5 … enable=…)`
      - 알약: a~c
      - 마이크: b+2~c(최종 녹화 뒤 고침, contracts/video.md §2)
    - 확대 창: `crop`, `scale=iw*3:ih*3`, `pad`(노란 6px), `overlay`
      - 알약: a~b+4. b가 없으면 a~a+4다.
      - 마이크: b+2~b+6
    - 이름표: PNG `overlay`
      - 알약: a~b "전화 거는 중", b~b+4 "통화 중"
      - 마이크: b+2~b+6 "마이크 켜짐"
  - 출력
    - libx264 crf 24, 15fps, yuv420p, `+faststart`로 `web/videos/<id>.mp4`를 바꾼다.
    - 14MB를 넘으면 process_videos.sh 16~17행과 같은 `-f segment` 명령으로 `<id>-N.mp4`로 나누고 `<id>.mp4`를 지운다.
  - 자막도 전화도 없는 장면은 원본을 그대로 복사한다.
- [X] T024 [US3] 다시 녹화하기 전에 burn.py를 3차 자료로 시험한다.
  - `$V/videos-pickup3`의 `log/handsfree.*`와 `web/videos/handsfree.{mp4,vtt}`를 `$V/burn-test/`에 같은 구조로 복사하고 burn.py를 돌린다.
  - a + 1.5초, b + 2초, 자막 한 순간의 프레임을 뽑는다(`ffmpeg -ss … -frames:v 1`).
  - 테두리·확대 창·띠가 contracts/video.md 위치에 있는지 본다. 어긋나면 좌표를 고친다.
- [X] T025 [P] [US3] `$V/build_video_page.py`를 고친다.
  - page.json에 `"burned": true`가 있으면 장면 `<video>`에 `data-cues`를 넣지 않는다.
  - 없으면 지금과 같다. 안 써본 기능과 DRIVE DNA 페이지도 이 도구를 쓰기 때문이다.
- [X] T026 [P] [US3] `$V/videos-pickup4/page.json`을 `$V/videos-pickup3/page.json`에서 복사해 만든다.
  - `data`·`out`을 `videos-pickup4`로 바꾼다(out은 `web/sameside-drive.html`).
  - `"burned": true`를 넣는다.
  - lead, footer 자막 문단, `transit` 설명을 contracts/video.md §4대로 바꾼다.
- [X] T027 [P] [US3] `$V/rec_pickup4.sh`를 새로 쓴다. `$V/rec_pickup_fix2.sh`와 같은 틀이다.
  - 빌드·테스트 → 설치 → `READ_PHONE_STATE`·`CALL_PHONE` 권한 → 다른 두 앱 force-stop
  - 20장면마다 `run:python3 scripts/demo.py reset`과 `rec:<id>:python3 scripts/demo.py <id>` 단계를 넣는다.
    - 장면: opposite transit transit-small handsfree fallback reverse wrongside stop cancel decline nospot ack arrive continue continue-cancel continue-full group group-transit waiting tour
  - `bash $V/run_rec_steps.sh <저장소> $V/videos-pickup4 kr.skku.sameside …`로 돌린다.
- [X] T028 [US3] 녹화한다(US1·US2 완료 뒤).
  - 준비
    - 에뮬레이터 Cold boot: `emulator -avd pleos -no-snapshot`
    - `adb forward tcp:8088 tcp:8088`
    - Virtual Console 페이지 닫기
    - 서버 켜기: `python3 server/server.py`
  - `bash $V/rec_pickup4.sh > $V/rec_pickup4.log 2>&1`를 돌린다. 장면마다 `log/<id>.rc`를 확인한다.
- [X] T029 [US3] 처리하고 판정한다(contracts/video.md §5 순서).
  - `NOSPLIT=1 VDIR=$V/videos-pickup4 POSTER=end bash $V/process_videos.sh opposite transit transit-small handsfree fallback reverse wrongside stop cancel decline nospot ack arrive continue continue-cancel continue-full group group-transit waiting tour`(장면 id를 꼭 넘긴다)
  - `python3 $V/extract_voice.py $V/videos-pickup4 kr.skku.sameside`(영상 아래 음성 목록)
  - `python3 $V/make_vtt.py $V/videos-pickup4 kr.skku.sameside`
  - `python3 $V/burn.py $V/videos-pickup4`
  - 전화가 있는 장면마다 a + 1.5초, b + 2초 프레임을 확인한다(SC-022). `log/<id>.logcat`에 "전화 걸기 요청"이 있는 장면이다.
  - `$V/videos-pickup4/verdicts.json`에 장면별 판정(`{"v": "pass"|"check"|"fail", "note": …}`)과 까닭을 적는다. 기준은 SC-024이고, 3차와 비교한다.
  - `$V/videos-pickup4/meta.json`에 `run` 한 줄을 쓴다(contracts/video.md §5의 6). 앱 크래시 수는 `rec_pickup4.log`의 마지막 crash 줄에서 읽는다.
- [X] T030 [US3] 페이지를 만들고 올린다.
  - `python3 $V/build_video_page.py $V/videos-pickup4/page.json`
  - 아티팩트 `https://claude.ai/artifact/LC2BoVmFeMjBhrbojPgF4P`를 먼저 읽는다(Artifact read).
  - 영상·포스터 파일 목록과 함께 같은 주소로 갱신한다. 파일 목록은 `$V/files_map.py` 방식이다.
- [X] T031 (미룸: 에뮬레이터와 중계 서버는 안 써본 기능·같은편 5차 확인에 계속 쓰고, 그 작업이 끝나면 끈다) [US3] 에뮬레이터를 정리한다.
  - `adb shell rm -rf /data/local/tmp/rec`
  - `adb emu kill`
  - 켠 서버를 끈다.

**Checkpoint**: 피드백 2·3·4가 영상과 페이지에서 끝났다.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T032 [P] `README.md`를 고친다.
  - 기능 표의 "3차 카드 안 지도" 줄을 4차 두 지도와 대중교통 강조 카드로 바꾸거나 4차 줄을 더한다.
  - 캡처 줄은 `docs/screenshots/`에 `$V/shots-004`의 카드 캡처를 넣어 바꾼다.
  - `transit` 시연 설명과 문서 목록(4차 설계·명세)도 고친다.
- [X] T033 ponytail-review로 바뀐 코드를 본다(`git diff -- app scripts`). 지적은 고치거나 까닭을 적는다.
- [X] T034 /code-review로 같은 diff를 본다. 맞는 지적은 고친다.
- [X] T035 /security-review를 저장소 안에서 돌린다. 새 권한·데이터 흐름이 없는지 확인한다.
- [X] T036 `specs/004-maps-transit-video/verification.md`를 쓴다.
  - 단위 테스트 수
  - SC-019~024 결과와 근거 경로: 캡처, 프레임, verdicts, 페이지 주소
  - 리뷰 결과
  - 남은 한계: 직선 경로, 상태 표시줄 위치 가정

---

## Phase 7: 차 세울 곳의 차선(FR-085)

**Goal**: "차 세울 곳" 지도의 길을 실제 도로처럼 그린다. 아스팔트, 흰 차선, 노란 중앙선 쪽 선, 횡단보도 줄무늬다(research R41).

**바뀐 점(2026-11-12 사용자 결정)**
- 처음 계획은 사용자가 국토정보플랫폼에서 받은 자료였다. 사용자가 메뉴를 찾지 못했고 작은 지도에서는 항공사진의 차선이 흐려서, OSM 차선 수로 그리기로 했다.
- 아래 T037~T039를 그 내용으로 바꿔 했다. 체크는 바뀐 내용 기준이다.

- [X] T037 [US1] (바뀜) OSM 차선 수와 두 방향 길 선 간격으로 길마다 차선 수를 정해 `app/src/main/java/kr/skku/sameside/Area.kt` `Road.lanes`에 넣고 research R41에 근거를 적는다. 원래 계획(받은 자료 확인)은 아래와 같았다.
  - 범위: 성균관대역 둘레(37.3003, 126.9712)와 화서역 둘레(37.2839, 126.9896)를 덮는지 본다.
  - 좌표계·형식: SHP·GeoTIFF·DXF 가운데 무엇인지, 좌표계(EPSG)가 무엇인지 본다.
  - 이용 조건: 사용자가 알려 준 공공누리 유형 등을 확인한다.
  - 결과를 `research.md` R41에 적고, 벡터로 할지 사진으로 할지 정한다. 공모전 앱에 넣을 수 없는 조건이면 사용자에게 알리고 멈춘다.
- [X] T038 [P] [US1] (바뀜) `Views.kt`에 `LANE_M`, `offsetLine`을 두고 `stopGeom`이 길 폭(차선 수 × LANE_M)을 따르게 한다. 테스트(`CardMapTest` stopGeom, offsetLine)를 먼저 고친다. 원래 계획(`make_ground.py`)은 아래와 같았다.
  - 시연 지점 10곳마다 `stopGeom`의 r을 가운데 두고, 지도 비율(439 × 230)보다 조금 넓게 자른다.
  - 벡터면 차선, 정지선, 횡단보도를 지점 원점 m 좌표의 선·다각형 목록으로 바꿔 `app/src/main/java/kr/skku/sameside/Ground.kt`(데이터만)에 쓴다.
  - 사진이면 `app/src/main/res/drawable-nodpi/ground_<랜드마크 id>.webp`와 그 범위(m)를 쓴다. 지점마다 100KB 안팎이다.
- [X] T039 [US1] (바뀜) `Views.kt` `StopMap`이 아스팔트·차선·횡단보도를 그리고(contracts/ui.md §3), 화살표에 흰 테두리를 깐다. 색 `asphalt`, `lane_white`, `lane_yellow`를 두 테마에 더한다. 원래 계획은 아래와 같았다.
  - 벡터: 차선은 흰 점선, 중앙선은 노랑, 횡단보도는 줄무늬로 그린다. 다크에서도 보이는 색을 쓴다.
  - 사진: 같은 m → px 변환으로 비트맵을 그린다. 다크 화면이면 30% 어둡게 한다.
  - 띠·화살표·핀·서 있을 곳·이름표는 그 위에 그대로 그린다.
  - 출처 글에 자료 출처를 더한다.
  - 자료가 없는 지점은 지금 그림이다.
- [X] T040 [US1] (바뀜) Figma ①~④·⑥의 "차 세울 곳" 지도 다섯 장을 OSM 차선 그림으로 바꾸고, 에뮬레이터 캡처(`영상 도구/shots-004/lanes`)와 맞춰 본다. ⑤ 가는 중 지도는 바뀌지 않는다.
  - 2026-11-12에 했다. 두 색 모음에 `asphalt`, `lane_white`, `lane_yellow` 변수를 더했다. 그림은 `영상 도구/fig_geom.py`가 Area.kt `lanes`를 읽어 만든다.
  - 이름표에 앱 `label()`과 같은 4px `map_bg` 테두리를 둘렀다. 아스팔트 위 "여기 세워요"가 읽히게 하려는 것이다.
  - 캡처 사본(`영상 도구/figma-004/s1~s4·s6.png`)을 새로 받았다.

---

## Dependencies & Execution Order

- **실제 노면(Phase 7)**: 자료가 오면 T011 뒤 언제든 한다. 녹화(T028) 전에 끝나면 영상에 들어간다. 자료가 녹화 때까지 오지 않으면 녹화를 먼저 하고, 나중에 다시 녹화할지 사용자에게 묻는다.

- **Phase 1 → Phase 2 → US1 → US2**
  - US2의 T019는 US1의 사람 줄(T012)을 쓴다.
  - US2의 받아들인 카드 지도는 US1의 `PeopleMap`(T010)을 쓴다.
- **US3**
  - 도구 작업(T022~T027)은 Phase 2 뒤 언제든 앱 작업과 나란히 할 수 있다.
  - 녹화(T028)부터는 US1·US2가 끝나야 한다.
- **Polish**
  - README(T032)는 캡처(T014, T021) 뒤에 한다.
  - 리뷰(T033~T035)는 코드 작업(T001~T021)이 끝난 뒤에 한다.
  - verification(T036)은 맨 끝이다.

### Parallel Opportunities

- T001과 T002
- T022, T023, T025, T026, T027: 모두 다른 파일이다. US1·US2 구현과도 나란히 할 수 있다.
- T032는 T033~T035와 나란히 할 수 있다.

### Parallel Example: US3 도구

```text
T022 make_vtt.py 안내 자막
T023 burn.py
T025 build_video_page.py burned 플래그
T026 videos-pickup4/page.json
T027 rec_pickup4.sh
```

## Implementation Strategy

- **MVP**: Phase 1·2와 US1이다. 카드 두 지도만으로 피드백 1이 끝나고 캡처로 보일 수 있다.
- **다음**: US2(대중교통 강조 카드와 시연 길이)를 더한다.
- **마지막**
  1. 앱이 고정되면 US3에서 한 번에 다시 녹화한다(20장면, 1시간 안팎).
  2. 리뷰한다.
  3. verification을 쓴다.
