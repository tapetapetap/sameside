# Tasks: 같은편 픽업

**Input**: `specs/001-sameside-pickup/` 아래 문서 전부. spec, plan, research, data-model, contracts/api·ui, quickstart다.

**Tests**: 헌법 원칙 VI에 따라 순서를 지킨다.
- `Pick.kt`·`People.kt`의 JUnit 테스트(`PickTest.kt`)를 먼저 쓰고, 실패를 확인한 뒤 구현한다.
- 서버도 `python3 server/server.py check`를 먼저 쓴다.

## Format: `[ID] [P?] [Story] Description`

- 경로는 저장소 루트 기준이다. 저장소 루트는 `pickup` 워크트리 `/Users/jiwon/orca/workspaces/unused-feature-app/pickup`이다.
- 소스와 테스트 위치
  - 소스: `app/src/main/java/kr/skku/sameside/`
  - 테스트: `app/src/test/java/kr/skku/sameside/`
- **DRIVE DNA**는 `/Users/jiwon/orca/workspaces/unused-feature-app/drive-dna`다. 읽기만 하고, 필요한 파일은 복사한 뒤 패키지를 `kr.skku.sameside`로 바꾼다.
- 수치는 `Pick.kt`의 `object Rules` 한 곳에 둔다(research R12).

## Phase 1: Setup

- [X] T001 빌드 뼈대를 DRIVE DNA에서 복사한다.
  - 복사할 것: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/`, `gradle/libs.versions.toml`
  - `local.properties`도 복사한다. git에서는 제외된다.
  - `settings.gradle.kts`의 `rootProject.name`을 `"sameside"`로 바꾼다. Pleos 저장소 설정은 그대로 둔다.
  - `libs.versions.toml`에 세 줄을 더한다.
    - `pleos-navi`(`ai.pleos.playground:NaviHelper:3.0.5`)
    - `pleos-location`(`ai.pleos.playground:FusedLocation:1.1.3`)
    - `zxing-core`(`com.google.zxing:core:3.5.3`)
- [X] T002 `app/build.gradle.kts`를 만든다.
  - DRIVE DNA 것을 본보기로 쓴다. namespace·applicationId는 `kr.skku.sameside`, compileSdk 35, minSdk 28, targetSdk 34, JVM 17이다.
  - 의존성: pleos vehicle·tts·navi·location, zxing-core, core-ktx, 테스트는 junit
- [X] T003 [P] `app/src/main/AndroidManifest.xml`과 `app/src/main/res/xml/network_security_config.xml`을 만든다.
  - 권한
    - `pleos.car.permission.CAR_DRIVE`·`CAR_DOORS`·`CAR_SEATS`·`FUSED_LOCATION`·`NAVI_ROUTE`·`NAVI_ROUTE_SEARCH`·`TTS_SERVICE`
    - `android.permission.CALL_PHONE`·`INTERNET`·`FOREGROUND_SERVICE`·`FOREGROUND_SERVICE_SPECIAL_USE`·`POST_NOTIFICATIONS`
  - `MainActivity`(런처)와 `PickupService`(foregroundServiceType `specialUse`)를 선언한다. 서비스 property는 DRIVE DNA `WatchService`와 같은 방식이다.
  - 평문 HTTP는 `10.0.2.2`와 `localhost`만 허용한다.
- [X] T004 [P] DRIVE DNA의 화면 자원을 복사한다.
  - 색: `res/values/colors.xml`, `res/values-night/colors.xml`
  - 앱 아이콘: `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/drawable/ic_launcher_bg.xml`, `ic_launcher_fg.xml`. 바탕은 초록→청록 그라데이션, 앞은 흰 위치 핀으로 바꾼다.
  - `res/values/strings.xml`에 앱 이름 "같은편"을 둔다.
  - 알림 작은 아이콘 `res/drawable/ic_pin.xml`(24dp 흰 위치 핀)을 만든다. `PickupService` 알림이 쓴다(DRIVE DNA는 `ic_dna`를 썼다).
- [X] T005 [P] DRIVE DNA의 `Ui.kt`를 `app/src/main/java/kr/skku/sameside/Ui.kt`로 복사하고 패키지만 바꾼다. 쓰지 않는 도우미는 ponytail-review에서 지운다.
- [X] T006 [P] 화면 배치 파일 두 개를 만든다. DRIVE DNA 배치를 복사하고 `dnaPane`을 `peoplePane`으로 이름만 바꾼다.
  - `app/src/main/res/layout/activity_main.xml`: 가로. 왼쪽 ScrollView 안 `peoplePane` 3 : 오른쪽 `scroll`/`screen` 5
  - `app/src/main/res/layout-port/activity_main.xml`: 세로

**Checkpoint**: `./gradlew :app:assembleDebug` 성공(빈 `MainActivity` 하나로 빌드만 확인)

---

## Phase 2: Foundational (판단 뼈대, 지역 데이터, 서버, 서비스 틀)

- [X] T007 `PickTest.kt`를 먼저 쓰고 실패를 확인한다.
  - **기하**(data-model §3)
    - 위경도 0.001° 차이의 `dist`가 위도 방향 110.5m, 경도 방향 `cos(lat)×111.3m`에 1% 안으로 맞는다.
    - `bearing`: 북쪽 0°, 동쪽 90°
    - `angleDiff`: 350°와 10°의 차이는 20°
    - `project`: 북쪽으로 가는 선분의 동쪽 점은 offset이 음수(오른쪽), 서쪽 점은 양수
    - `side`: 북쪽으로 갈 때 동쪽 점은 RIGHT
  - **사람 형식**(data-model §1)
    - `People.encode/decode` 왕복
    - 깨진 줄은 건너뛴다.
    - 이름 1~10자, 전화번호 9~11자리 검증과 탭·줄바꿈 제거
    - 차 정보: 각 칸 0~20자, 운전자 번호는 비우거나 9~11자리(data-model §1)
- [X] T008 세 파일을 만들어 T007을 통과시킨다.
  - `Area.kt`: data-model §2의 데이터 형식(data class들). 시연 지역 `DEMO`는 T009에서 채운다.
  - `Pick.kt`: object `Rules`와 기하 함수. 모든 기하 함수는 원점 `o`를 인자로 받는다(data-model §3).
  - `People.kt`: 순수 형식·검증 함수와 SharedPreferences 읽기/쓰기
- [X] T009 `Area.kt`의 `DEMO`와 `scripts/scenes.txt`를 만든다(data-model §2·§9, research R9).
  - `DEMO`의 데이터 묶음은 두 개다.
    - 묶음 ①: 성균관대역 주변
    - 묶음 ②: 대중교통 장면의 내릴 정거장 주변. 묶음 ①에서 1.6km 넘게 떨어진 곳
  - 사진(`photo`)은 1차 기본값이 null이다. 팀이 직접 찍은 사진을 주면 `server/photo/`에 둔다.
  - 좌표는 OpenStreetMap에서 Overpass API로 두 지역의 길·횡단보도·버스정류장·가게를 받아 다듬는다(2026-11-09 사용자 허락, research R9).
    - 받은 JSON은 스크래치 폴더에 둔다.
    - 출처 "© OpenStreetMap contributors"를 `Area.kt` 머리와 README에 적는다.
  - 데이터는 data-model §2의 장면 요구를 모두 만족해야 한다.
  - `scripts/scenes.txt`에는 장면마다 다음을 적는다.
    - 차 시작점과 주행 경로
    - 태울 사람 위치
    - 기대하는 1순위 랜드마크 id
  - `scripts/scenes.txt`의 키와 주석 규칙은 data-model §9다.
  - `PickTest.kt`에 데이터 점검을 더한다.
    - 모든 `roadId`가 있다.
    - 길 점이 2개 이상이다.
    - 장면 요구 중 두 가지(한 길의 양쪽 길가 랜드마크, 정류장 10m 안 랜드마크)가 있다.
    - 대중교통 장면 점검은 `transit()`이 생기는 T030에서 한다.
- [X] T010 `server/server.py`를 만든다. `check`를 먼저 쓰고, 실패를 확인한 뒤 구현한다.
  - contracts/api.md 전부다.
    - 엔드포인트 6개
    - 검증(본문 32KB, key, 상태 값, 위경도 범위, 동의 전 위치 무시)
    - 끝 상태 처리(위치 지움, 10분 뒤 삭제)
    - 30분 안전망
    - `/photo/` 파일 이름 제한
  - 표준 라이브러리만 쓴다(`http.server.ThreadingHTTPServer`, `json`, `secrets`, `threading`).
  - 인자 없이 실행하면 `127.0.0.1:8090`에서 연다.
  - `check`는 빈 포트에 띄우고 `urllib`로 contracts/api.md 자체 점검 여덟 항목을 assert한다.
  - 시간 규칙(10분 삭제, 30분 안전망)은 `now()`를 바꿔 끼우고 청소 함수를 직접 불러 확인한다. 실제 서버는 청소 스레드가 60초마다 같은 함수를 부른다.
  - key가 맞는 `GET`도 차의 요청으로 센다(contracts/api.md 안전망).
- [X] T011 [P] `Relay.kt`를 만든다. 기준 주소는 `http://10.0.2.2:8090`이다.
  - 함수. 돌려주는 값은 HTTP 코드이고, 연결 실패는 −1이다.
    - `create(name, car): Triple<code, id, key>`
    - `putCar(id, key, json): Int`
    - `get(id, key): Pair<code, Remote?>`. `X-Car-Key`를 붙이고, JSON을 data-model §7의 `Remote`로 바꾼다.
  - `HttpURLConnection`(연결·읽기 각 3초)과 `org.json`을 쓴다. 호출은 백그라운드 스레드에서만 한다.
  - 실패하면 null 또는 false를 돌려주고, 서비스가 연결 실패 띠를 켠다(contracts/ui.md §1).
  - 실패 때의 동작
    - `create` 실패: 픽업을 만들지 않고 "픽업 없음"에 띠를 켠다. 2초마다 다시 만든다. 운전자가 다른 사람을 고르면 그 사람으로 바꾼다.
    - `putCar` 실패: 다음 주기에 그때의 최신 상태로 다시 보낸다. 끝 상태는 204, 409, 404 중 하나가 올 때까지 보낸다(SC-006).
    - `putCar`가 409면 바로 `get`을 한 번 더 한다. 끝 상태가 오면 `Remote`로, 404면 `Gone`으로 넣는다(data-model §7). `get`의 404도 `Gone`이다.
    - 끝 상태 다시 보내기: `End`로 폴링이 멈춘 뒤에도, 끝 상태 `PUT`이 204·409·404를 받을 때까지 2초 타이머가 따로 돈다.
  - 차 `PUT`은 한 스레드에서 순서대로 보낸다.
- [X] T012 `PickupService.kt` 틀을 만든다.
  - 포그라운드 서비스(`specialUse`)와 알림이다. DRIVE DNA `WatchService.kt`를 본보기로 쓴다.
  - SDK 초기화: Vehicle(기어), FusedLocation, NaviHelper, TTS(`Mode.ON_DEVICE`)
    - 영역마다 try/catch를 둔다.
    - 실패하면 10초마다 다시 시도한다(DRIVE DNA `CarWatcher.kt` 방식).
    - 콜백은 메인 스레드로 옮긴다.
  - `Pick.Pickup` 하나를 들고, 이벤트가 오면 `Pick.step`을 부르고 돌려받은 할 일을 실행한다.
    - 1차 틀이 실행하는 할 일: `Speak`(TTS 대기열), `Push`(Relay.putCar), `End`(폴링 멈춤)
    - `SetNavi`, `CallOrNotify` 등 나머지는 각 이야기에서 더한다.
  - 진행 중인 픽업이 있으면 2초마다 `Relay.get` → `Remote` 이벤트와 `Tick`을 넣는다.
  - 신호는 처음부터 구독해 이벤트로 넣는다(상태와 관계없이). 마지막 기어·위치·좌석 값은 서비스가 따로 들고 있다가 새 픽업의 시작값으로 쓴다(data-model §7 보충).
    - FusedLocation 위치 → `CarPos`. 차 위치를 기다리는 `link`에서도 필요하다(data-model §7).
    - 기어 → `Gear`. 아직 값이 없으면 `park`는 null이고, P가 아닌 것으로 본다.
    - 문 네 곳(DoorValue ROW_1_LEFT·ROW_1_RIGHT·ROW_2_LEFT·ROW_2_RIGHT)과 좌석 세 곳(SeatValue ROW_1_RIGHT·ROW_2_LEFT·ROW_2_RIGHT) → `Door`·`Seat`. 좌석의 최근 값은 픽업을 시작할 때 `emptySeatsAtStart`에 쓴다(research R5).
  - 화면에는 상태와 기어(P인지)를 리스너로 알린다.
- [X] T013 `MainActivity.kt`와 `Screens.kt` 틀을 만든다(contracts/ui.md §1).
  - 서비스를 시작하고 상태를 받아 그린다.
  - 왼쪽 칸
    - 사람 줄(가린 번호, 아이·어르신 칩)
    - "+ 사람 추가" 대화상자(검증 문구 포함)
    - "내 차" 대화상자
  - 오른쪽 칸: "픽업 없음" 상태
  - 가린 번호는 앞 3자리와 끝 4자리만 보인다. 20명이면 "+ 사람 추가"가 "최대 20명까지 저장해요"를 보인다(contracts/ui.md §1).
  - 버튼은 기어가 P일 때만 눌린다.
  - 처음 열릴 때 `CALL_PHONE`과 `POST_NOTIFICATIONS`를 요청한다(DRIVE DNA `MainActivity`의 `requestPermissions`와 같은 방식). `CALL_PHONE`이 거부되면 자동 전화는 대체 알림으로 간다(FR-018).

**Checkpoint**
- `./gradlew :app:testDebugUnitTest` 통과
- `python3 server/server.py check` 통과
- 에뮬레이터에서 사람을 저장하고 다시 열어도 남아 있다.

---

## Phase 3: User Story 1 - 차가 오는 쪽 길가의 지점을 받는다 (P1) 🎯 MVP

**Goal**: 링크 → 동의 → 같은 편 지점 1~3개 → 정차 중 확인 → 기본 내비에 목적지와 진입 경유지
**Independent Test**: quickstart `opposite`의 도착까지(탑승은 US5)

- [X] T014 [US1] `PickTest.kt`에 `choose`와 `step`(시작부)의 테스트를 먼저 쓴다(data-model §4·§7).
  - **choose**
    - `opposite` 장면(scenes.txt)의 1순위가 기대 랜드마크다.
    - 반대쪽 길가 랜드마크는 U턴 규칙으로 빠진다.
    - offset 3m 안은 빠진다.
    - 일방통행의 왼쪽 길가는 빠진다.
    - 버스정류장 10m 안은 빠진다.
    - `careful`이면 150m 밖은 빠진다.
    - 순위는 분 올림 → 걷는 거리 → 길 건너기 순이다.
    - 진입 경유지는 길 선 위에서 지점으로부터 100m(±1m) 거슬러 간 점이다.
    - 이유 한 줄 두 가지(crossing 0과 1)
    - 후보가 없으면 빈 목록
  - **거르기 테스트는 합성 지역을 쓴다.** 길 하나와 랜드마크 몇 개로 만든 작은 `Area`다. 시연 데이터에 일부러 애매한 자리를 넣지 않는다.
  - **step**
    - `link`에서 동의, 태울 사람 위치, 차 위치가 모두 갖춰지면 → `spots`(또는 `nospot`)와 `Push`
    - 차 위치가 없으면 동의와 위치가 와도 `link`에 머문다. 나중에 첫 `CarPos`가 오면 그때 고른다.
    - `consent: false`는 `link`에 머문다.
    - 기어 P가 아니면 `Confirm`을 무시한다.
    - P에서 `Confirm(0)` → `going`, `SetNavi`, `expireAt`
- [X] T015 [US1] `Pick.kt`에 `choose`, `Spot`, 상태·이벤트·할 일 타입, `step`의 시작부 전이를 만들어 T014를 통과시킨다.
  - 시작부 전이: `link`→`spots`/`nospot`, `spots`→`going`
  - 진입 경유지 계산과 서버에 올릴 `road` 선(지점 앞뒤 150m)을 함께 만든다(data-model §8).
- [X] T016 [US1] `PickupService.kt`에 픽업 시작과 `SetNavi` 실행을 더한다.
  - **시작**: `Relay.create`. 새 `Pickup`의 `park`·`track`·`emptySeatsAtStart`는 T012가 들고 있던 마지막 값으로 채운다. 그 뒤 `link`, `Push`
  - **SetNavi**
    - 먼저 `requestRoute(RouteInfo(latitude, longitude, displayLatitude=null, displayLongitude=null, poiName="${name} 앞", poiId="", address="", poiSubId="0"))`
    - `onRouteStarted`를 받으면 `addWaypoint(RequestWaypointInfo(latitude, longitude, poiName="진입", waypointIndex=WaypointIndex.FIRST, poiId="", address="", poiSubId="0"))`를 부른다. 10초 안에 오지 않으면 로그만 남긴다(research R1).
    - 다시 설정할 때는 `cancelRoute` → `requestRoute` → `addWaypoint` 순서다.
    - 실패(`onError`)는 로그만 남긴다.
  - 권한은 research R1이다. `NAVI_CUSTOM_MAP`이 없어 `SecurityException`이 나면 그때 더한다.
- [X] T017 [US1] `Screens.kt` 오른쪽 칸에 네 상태를 만든다. 문구는 contracts/ui.md §1이다.
  - `link`: ZXing `QRCodeWriter`로 링크 `http://localhost:8090/p/<id>`를 300dp 비트맵으로 그리고, 링크 글자를 둔다. 안내 한 줄은 contracts/ui.md §1 표의 조건으로 고른다.
  - `spots`: 1순위 카드와 대안 2장, "이 자리로 갈게요"(P에서만)
  - `nospot`
  - `going` 요약
- [X] T018 [P] [US1] `server/page.html`을 만든다. 문구는 contracts/ui.md §3이다.
  - 처음(동의)·거절·`link`·`spots`·`nospot`·`going`(기본)·404 상태
  - 동의
    - "위치 공유하고 시작"을 누르는 즉시 `consent: true`를 `PUT`한다. "괜찮아요"를 누르면 `consent: false`를 `PUT`한다.
    - 위치 오류(`watchPosition` 오류 콜백)면 "위치를 받지 못했어요…"와 "다시 시도"를 보인다(contracts/ui.md §3).
    - "위치 공유하고 시작"을 누를 때 소리를 준비한다(AudioContext). `?sim=1`이면 "소리 켜기" 버튼을 보인다(contracts/ui.md §3).
  - 위치 보내기
    - 동의 뒤 `navigator.geolocation.watchPosition`으로 위치를 `PUT …/passenger`한다.
    - `?sim=1`이면 위치 API를 부르지 않는다.
  - 2초마다 `GET`한다.
  - `going` 기본: 큰 "○○ 앞", 사진(`/photo/…`, 없으면 숨김), SVG 그림
    - 그림은 `road` 선, 들어오는 방향 화살표, 지점 별, 내 위치 점이다. 차 점은 차가 지점 300m 안일 때만 넣고, 밖이면 그림 아래 "차 n분 · n.n km"를 쓴다.
  - `nospot`: 문구와 "차 n분"(`etaSec`)
    - 그림 범위는 그리는 점들의 경계 상자에 맞춘다.
- [X] T019 [US1] `scripts/demo.py`를 만든다. Python 표준 라이브러리만 쓴다.
  - 도우미
    - 콘솔 하트비트(1초)와 `PUT`
    - `adb emu geo fix` 경로 재생(시속 지정, 1초 간격)
    - uiautomator 덤프로 글자 찾기·누르기
    - 화면에서 링크 id 읽기
    - 서버 `PUT …/passenger`
  - `reset`: quickstart 준비 1~6. 와이파이 연결 확인, 앱 멈춤, `run-as`로 `people.xml` 지우고 다시 쓰기가 들어간다. `pm clear`는 쓰지 않는다(research R13).
  - 장면 파일 읽기: data-model §9 규칙(`#` 뒤 버림)을 PickTest와 똑같이 따른다.
  - 장면 공통 규칙(quickstart)을 함수 하나로 만든다. 시작할 때 GPS를 자기 `car`로 옮기고 Park로 둔다. 끝날 때 통화를 끊고, "확인"을 누르고, 조수석을 빈 좌석으로 넣는다(research R5).
  - `decline`, `nospot` 장면도 여기서 만든다.
  - `opposite`: 도착까지
- [X] T020 [US1] 에뮬레이터에서 `opposite`를 도착까지 확인한다.
  - 1순위가 기대 랜드마크다.
  - 내비 로그에 `onRouteStarted`와 `onWaypointChanged(ADD)`가 있다.
  - 결과는 T040의 verification에 적는다.

**Checkpoint**
- US1만으로 "반대편 함정" 장면의 앞부분(지점 고르기와 내비)이 된다.
- 에뮬레이터 로그에 `Relay.create` → `get` 성공이 찍힌다.
- 서버를 끈 채 픽업을 시작하면 연결 실패 띠가 뜬다. 서버를 다시 띄우면 픽업이 생긴다.
- 진행 중에 서버를 다시 띄우면 `GET` 404 → `expired`가 된다.

---

## Phase 4: User Story 2 - 운전 중에는 듣기만, 도착 전 자동 전화 (P1)

**Goal**: 주행 중 조작 0번, 자동 전화 한 번, 150m 한 줄
**Independent Test**: quickstart `handsfree`, `reverse`

- [X] T021 [US2] `PickTest.kt`에 테스트를 먼저 쓴다(data-model §6·§7).
  - `heading`: 8m 규칙, 부족하면 null
  - `speed`: 30초 창
  - `remaining`: 경유지 전·후 거리식, 최저 시속 20km
  - 자동 전화
    - 문턱 max(걷는 시간 + 60, 120)
    - 한 번만
    - "나와 있어요" 또는 30m 안이면 전화 대신 "○○님이 나와 있어요"
  - 150m 세 문장(RIGHT·LEFT·heading 없음). LEFT이면 `crossOver`
  - `CarPos`의 `Push`는 직전 `Push`에서 1초 넘게 지났을 때만 나온다(`spots`·`nospot`·`going`).
  - 속도: 고를 때는 시속 25km, 주행 중에는 max(속도, 20km/h)
  - `stopped`(5초 5m), `passedWaypoint`(30m, 한 번 true면 그대로)
  - `going`에서 태울 사람 위치가 오면 `walkSec`를 다시 계산하고, 자동 전화 문턱이 그 값을 쓴다.
  - 자동 전화의 나와 있음 분기는 `readySpoken`이 true면 말하지 않는다.
  - 주행 중 음성은 5번에서 멈춘다(`spokenWhileDriving`). `CallOrNotify`도 한 줄로 세고, 상한에 닿았으면 `speak = false`다.
  - 150m 판정(합성 지역): 교차하는 길에서 꺾어 들어오는 경로에서는 꺾기 전에 판정하지 않는다. 지점 길에 들어선 뒤 RIGHT로 말한다. 지점 길에 오지 않은 채 50m 안이면 "곧 ○○ 앞이에요"다.
  - 나와 있음 30m 판정은 기준 길가와 같은 쪽일 때만이다. 길 건너편 25m는 나와 있음이 아니다.
  - `handsfree` 장면 재생: scenes.txt 경로를 1초 간격으로 넣었을 때 ① 자동 전화가 실제 문턱 뒤 0~30초 안에 나온다 ② `walkSec`가 `walk` 값과 ±20초 안에서 맞는다(data-model §2의 4).
- [X] T022 [US2] `Pick.kt`에 `heading`·`speed`·`remaining`과 `going`의 자동 전화·150m 규칙(FR-016·017·019·020)을 만든다. 문장은 contracts/ui.md §2를 그대로 쓴다.
- [X] T023 [US2] `PickupService.kt`에 두 가지를 더한다.
  - FusedLocation 위치를 `CarPos` 이벤트로 넣는다.
  - **먼저 확인**: 기어 D·앱이 가려진 상태에서 서비스가 `TelecomManager.placeCall`을 부르면 통화가 생기는지 에뮬레이터로 보고 research R4에 적는다.
  - `CallOrNotify`를 실행한다(data-model §7 보충).
    - 걸 수 있는 조건: `CALL_PHONE` 권한
    - 걸 수 있으면 `speak`일 때 "○○님께 전화 걸게요"를 말하고, 2초 뒤 `placeCall`(`// ponytail: 고정 지연, TTS 끝 콜백으로 바꿀 수 있음`)
    - 걸 수 없거나 예외가 나면 `speak`일 때 "○○님 폰으로 알렸어요"만 말한다.
- [X] T024 [P] [US2] `server/page.html`에 알림 띠 두 개를 더한다.
  - `outNow`: 삐 소리 3번(Web Audio, T018에서 준비한 AudioContext), `navigator.vibrate`, "운전자에게 전화" `tel:` 링크(운전자 번호가 있을 때)
  - `crossOver`: 소리 한 번
- [X] T025 [US2] `scripts/demo.py`에 두 장면을 더한다.
  - `handsfree`: 끝나면 `KEYCODE_ENDCALL`로 통화를 끊는다.
  - `reverse`
  - `fallback`: `pm revoke`로 `CALL_PHONE`을 거둔 뒤 `handsfree`를 돌리고, 끝나면 `pm grant`로 되돌린다.
  - 확인: 전화 한 번(`dumpsys telecom`), 음성 줄 수 5 이하
  - `handsfree`는 경로와 속도로 실제 남은 시간을 계산해, 그것이 "걷는 시간 + 1분"에 닿는 시각을 출력한다(SC-004의 기준).

**Checkpoint**: `handsfree`에서 문턱 30초 안에 전화 한 번, 150m 한 줄, 주행 중 화면 조작 0번

---

## Phase 5: User Story 3 - 태울 사람은 링크 하나로 어디로, 언제 (P1)

**Goal**: 나갈 때 안내, 차 알아보기, 버튼, 건너편 경고
**Independent Test**: quickstart `wrongside`, 처음 보는 사람 3명 30초 테스트(SC-005)

- [X] T026 [US3] `PickTest.kt`에 테스트를 먼저 쓴다.
  - `wrongSide`
    - 남은 3분 이하이고, 길에서 80m 안이며, offset 부호가 다르면 true. 아니면 false다.
    - 바뀔 때만 `Push`한다.
    - `crossOver`가 켜진 뒤에는 기준 길가가 반대쪽이다. 그래서 안내대로 건넌 사람에게는 `wrongSide`가 뜨지 않는다.
  - "나와 있어요"·"조금 늦어요" 음성은 각각 한 번만 나온다.
- [X] T027 [US3] `Pick.kt`에 `wrongSide`와 `Remote.ready`·`late` 규칙을 만든다(data-model §7).
- [X] T028 [P] [US3] `server/page.html`의 `going` 화면을 마친다.
  - 나갈 때 안내(`etaSec − walkSec − 60`)
  - "차 n분 · 색 차종 번호판"
  - 버튼 네 개: "나와 있어요"·"조금 늦어요" → `PUT`. "탔어요"·"공유 끄기"는 T037에서 잇는다.
  - `wrongSide` 띠
  - 거절 뒤 "다시 공유하기"
- [X] T029 [US3] `scripts/demo.py`에 `wrongside`를 더한다. 확인 기준은 `passenger2`로 옮긴 뒤 새 운전자 음성 0줄과 페이지 띠다. 30초 이해 테스트(SC-005)는 사람이 하고 T040에 적는다.

---

## Phase 6: User Story 4 - 대중교통 역마중 (P2)

**Goal**: 5분 넘게 빠를 때만 제안하고, 받아들이면 지점을 바꾼다.
**Independent Test**: quickstart `transit`, `transit-small`

- [X] T030 [US4] `PickTest.kt`에 테스트를 먼저 쓴다(data-model §5·§7).
  - `transit`
    - 이득이 300초를 넘으면 제안하고, 300초 이하면 null이다.
    - 걷는 거리 한도 밖 정류장은 건너뛴다.
    - 걷는 지점이 없으면 null이다.
    - 이득이 가장 큰 안을 고른다.
  - `step`
    - `spots`에서 받아들이면 `spots`가 정류장 근처 지점 하나가 되고(대안 지움), 음성 한 줄이 나온다.
    - `going`에서 받아들이면 `SetNavi`와 음성 한 줄이 나온다.
    - 받아들인 뒤에는 태울 사람 위치가 와도 `walkSec`를 다시 계산하지 않고 올리지 않는다.
    - 받아들인 뒤 자동 전화 문턱은 120초다.
  - **시연 데이터 점검**(T009에서 옮김): scenes.txt의 `transit` 장면은 `offer yes`대로 제안이 뜨고 이득이 300초를 넘는다. `transit-small` 장면은 `offer no`대로 제안이 없다.
- [X] T031 [US4] `Pick.kt`에 `transit`, `Offer`, 받아들임 전이를 만든다.
- [X] T032 [P] [US4] 화면 두 곳에 대중교통을 붙인다.
  - `server/page.html`: 대중교통 카드(문구, 두 버튼 → `PUT transit`, "시연용 준비 데이터예요"). 받아들인 뒤에는 나갈 때 안내 대신 "○○ 타고 ○○에서 내려 ○○ 앞으로 와 주세요"를 계속 보인다.
  - `Screens.kt`: `spots`의 "○○님께 버스 n정거장 안을 보냈어요", 받아들인 뒤 "○○님이 ○○ 타고 와요"
- [X] T033 [US4] `scripts/demo.py`에 `transit`, `transit-small`을 더한다.

---

## Phase 7: User Story 5 - 탑승 확인과 공유 종료 (P2)

**Goal**: 문·착좌로 "만났어요", 끝나는 조건 네 가지, 서버에 위치가 남지 않음
**Independent Test**: quickstart `opposite`(끝까지), `stop`

- [X] T034 [US5] `PickTest.kt`에 테스트를 먼저 쓴다(data-model §7).
  - **탑승**
    - 서 있고, 50m 안이고, 문이 열렸다 닫히고, 비어 있던 좌석에 착좌 → `met`
    - 멀거나 좌석 변화가 없으면 아니다.
    - 착좌가 먼저 오고 문이 나중에 닫혀도 `met`이다(`seatTaken`). 문이 50m 밖에서 닫혔으면 아니다.
    - `Remote.boarded` → `met`(끝이 아닌 모든 상태)
    - `nospot`에서도 문·착좌로 `met`. 기준점은 태울 사람의 마지막 위치 50m다.
  - **끝**
    - `Remote.stop` → `stopped`
    - `Cancel`은 P에서만 → `canceled`와 `ClearNavi`
    - `Tick`에서 `expireAt`가 지나면 `expired`. `link`·`spots`·`nospot`은 상태가 된 때 + 60분
    - `Remote.status`가 끝 상태면 그 상태와 `End`. 공유 끄기 직후 차 `PUT`이 409를 받아도, 다시 읽은 `GET`의 `stopped`가 들어가 `stopped`로 끝난다(`expired`가 아님).
    - `Gone`이면 `expired`와 `End`
- [X] T035 [US5] `Pick.kt`에 탑승과 끝 전이를 만든다.
- [X] T036 [US5] `PickupService.kt`와 `Screens.kt`에 끝 처리를 더한다.
  - T012가 구독한 문 네 곳·좌석 세 곳의 이벤트로 탑승 판정을 잇는다(research R5).
  - `ClearNavi` → `cancelRoute()`
  - `End` → 폴링을 멈추고, 끝 상태 화면과 "확인"을 보인다.
  - `link`·`spots`·`nospot`·`going`의 "픽업 취소" 버튼(P에서만)
- [X] T037 [P] [US5] `server/page.html`에 끝 처리를 더한다.
  - "탔어요"(`boarded`)와 "공유 끄기"(`stop`, 위치 감시 해제). "탔어요"는 `nospot`에서도 보인다.
  - 끝 상태 네 개의 문구
- [X] T038 [US5] `scripts/demo.py`에 끝 처리를 더한다.
  - `opposite`의 탑승: 문 `door_fr` 열기·닫기, 조수석에 앉은 것으로 넣기(research R5: 에뮬레이터는 반대로 온다)
  - `stop`, `cancel`
  - 확인: 끝난 뒤 `GET`에 `status`·`name`만 있다.

---

## Phase 8: Polish & 검증

- [X] T039 에뮬레이터에서 quickstart의 모든 장면과 `tour`를 돌린다.
  - `tour`는 `scripts/demo.py`에 더한다. 장면 공통 규칙으로 이어 붙이고, `transit`은 받아들인 것을 확인한 뒤 P에서 "픽업 취소"로 끝낸다.
  - 화면(차 화면·페이지)을 `docs/screenshots/`에 저장한다.
  - `logcat -b crash`를 확인한다.
- [X] T040 [P] `specs/001-sameside-pickup/verification.md`를 쓴다.
  - 테스트 결과와 장면별 결과(SC-001~007 대응)
  - SC-005 사람 테스트
  - 확인하지 못한 것: 실차 핸즈프리 전화, 실차 내비 남은 시간·위치, 실제 폰 HTTPS 화면
- [X] T041 [P] `README.md`를 쓴다.
  - 공모전 README 필수 항목: 앱 이름과 한 줄 소개, 실행 방법(서버 → 앱 → 시연), 테스트 환경·설정, APK 위치
  - 시연용 준비 데이터와 출처(OSM을 썼다면 ODbL 표기)
  - 한계

---

## Dependencies & Execution Order

- **단계 순서**: Setup(T001~T006) → Foundational(T007~T013) → US1(T014~T020) → US2(T021~T025) → US3(T026~T029) → US4(T030~T033)·US5(T034~T038) → Polish
- **이야기 사이 의존**
  - US2는 US1의 `going`이 있어야 한다.
  - US3 페이지는 US1 페이지를 잇는다.
  - US5의 "서 있음" 판정은 US2의 위치 기록(track)을 쓴다.
  - US4와 US5는 서로 독립이다.
- **이야기 안 순서**: 테스트(실패 확인) → `Pick.kt` → 서비스·화면 → 시연 스크립트

## Parallel Opportunities

- Setup: T003·T004·T005·T006
- Foundational: T011은 T010과 동시에 할 수 있다(서로 다른 파일).
- 이야기마다 `server/page.html` 작업([P])은 같은 이야기의 Kotlin 작업과 동시에 할 수 있다(T018, T024, T028, T032, T037).

## Implementation Strategy

1. **MVP**: Setup → Foundational → US1. "반대편 함정"의 지점 고르기와 내비 넘기기까지 보인다.
2. **P1 완성**: US2(자동 전화, 150m)와 US3(태울 사람 화면)를 붙인다. 1차 시연 장면 ①·③의 주행 부분이 된다.
3. **P2**: US4(장면 ②)와 US5(장면 ③의 끝 "만났어요")를 붙인다.
4. **Polish**: `tour`, 검증 기록, README
5. **워크플로**: 구현 뒤 ponytail-review → /code-review → /security-review(서버와 위치를 다루므로)를 한다.
