# Implementation Plan: 같은편 픽업

**Branch**: `001-sameside-pickup` (git 브랜치 `pickup`) | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/001-sameside-pickup/spec.md`, 설계 `docs/superpowers/specs/2026-11-09-pickup-app-design.md`

## Summary

같은편은 세 부분으로 나뉜다.

| 부분 | 할 일 |
|---|---|
| 차 앱 | 지점을 고르고, 기본 내비에 넘기고, 운전 중에는 음성만 낸다. |
| 중계 서버 | 차와 태울 사람 화면 사이에서 현재 상태만 메모리에 들고 있다. |
| 태울 사람 화면 | 설치 없이 링크로 열리는 웹 페이지다. |

**판단은 모두 순수 Kotlin(`Pick.kt`)에 두고 JUnit으로 지킨다.** 판단 함수는 지역을 인자로 받는다. 그래서 테스트는 합성 지역으로 거르기 규칙을 하나씩 확인한다. 판단에는 같은 편 판정, 세우면 안 되는 자리, 순위, 걷는 시간, 남은 시간 추정, 자동 전화 시점, 150m 안내 방향, 건너편 경고, 대중교통 이득, 탑승 판정이 들어간다. 서비스와 화면은 신호를 넘기고 결과를 말하거나 보이기만 한다.

**에뮬레이터 확인 결과**(research)
- 위치는 FusedLocation으로 받는다. 진행 방향은 앱이 계산한다.
- 기본 내비에는 목적지와 진입 경유지만 넘긴다.
- 남은 시간은 1차에서 앱이 추정한다. 에뮬레이터 내비는 GPS를 따라오지 않기 때문이다.
- 자동 전화는 `ACTION_CALL`로 건다. 에뮬레이터 모의 SIM으로 통화가 됐다.
- 문·착좌는 Vehicle SDK로 받는다.

**시연**
- 1차는 성균관대역 주변 한 지역의 준비 데이터(`Area.kt`)로 한다.
- 시연 스크립트(`scripts/demo.py`)가 세 가지를 움직인다.
  - 에뮬레이터 GPS(`adb emu geo fix`)
  - Virtual Console(기어, 문, 착좌)
  - 태울 사람 위치(서버 API)

## 현재 상태 (구현 전)

- 워크트리 `/Users/jiwon/orca/workspaces/unused-feature-app/pickup`
  - 브랜치 `pickup`은 첫 앱과 기록을 나눈 orphan 브랜치다. 아직 커밋이 없다.
  - 원격 `sameside`는 GitHub 비공개 `tapetapetap/sameside`이고 비어 있다.
- 있는 것: spec-kit 설정(`.specify/`, `.claude/skills/`), 헌법, `CLAUDE.md`, 설계 초안, 11/7 회의 대본·정리, 이 명세 폴더
- 코드는 아직 없다.
- 에뮬레이터 와이파이(`AndroidWifi`)는 2026-11-09에 연결해 저장해 두었다(research R11).

## DRIVE DNA에서 가져오는 것

DRIVE DNA는 같은 공모전에 낸 두 번째 앱이다.
- 경로: `/Users/jiwon/orca/workspaces/unused-feature-app/drive-dna`
- 패키지: `kr.skku.drivedna`

DRIVE DNA는 읽기만 한다. 필요한 파일은 복사한 뒤 패키지를 `kr.skku.sameside`로 바꾼다.

| 가져오는 것 | DRIVE DNA 위치 | 하는 일 |
|---|---|---|
| 빌드 뼈대 | `settings.gradle.kts`(Pleos 저장소), `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/`(`libs.versions.toml`, `wrapper/`) | `rootProject.name = "sameside"`로 바꾼다 |
| 색 토큰 | `app/src/main/res/values/colors.xml`, `values-night/colors.xml` | Pleos 라이트·다크 색 |
| 화면 도우미 `class Ui` | `app/src/main/java/kr/skku/drivedna/Ui.kt` | 글자, 둥근 바탕, 배치, 카드, 칩, 경고 띠를 코드로 조립한다 |
| 신호 받기 방식 | `CarWatcher.kt` | SDK 초기화가 실패하면 10초마다 다시 시도한다. 구독마다 try/catch를 두고, 콜백은 메인 스레드로 옮긴다 |
| 서비스와 음성 | `WatchService.kt` | 포그라운드 서비스(`specialUse`), 온디바이스 TTS, 알림 채널 |
| 앱 아이콘 | `res/mipmap-*`, `res/drawable/ic_launcher*` | 바탕 색과 앞면(흰 위치 핀)을 바꾼다(tasks T004) |

## Technical Context

**Language/Version**
- Kotlin 2.0.21, JDK 17, AGP 8.8.2, Gradle 8.11.1(DRIVE DNA와 같음)
- 서버와 시연 스크립트는 Python 3. 표준 라이브러리만 쓴다.

**Primary Dependencies**
- Pleos SDK: Vehicle 2.3.2, TextToSpeech 3.3.1, NaviHelper 3.0.5, FusedLocation 1.1.3
- ZXing core 3.5.3(QR)
- androidx core-ktx
- 통신은 플랫폼 기본 `HttpURLConnection`과 `org.json`을 쓴다.

**Storage**
- 차: SharedPreferences `people`. 저장한 사람과 차 정보를 한 줄씩 둔다(data-model §1).
- 서버: 메모리만 쓴다.

**Testing**
- JUnit 4: `app/src/test/java/kr/skku/sameside/PickTest.kt`
- 서버 자체 점검: `python3 server/server.py check`
- 에뮬레이터 시나리오: quickstart

**Target Platform**: Pleos Connect Emulator v2.1.0(API 34), 실차 Pleos Connect. 태울 사람 화면은 최신 데스크톱·모바일 브라우저다.

**Project Type**: mobile-app과 작은 웹 서비스
- Android 단일 모듈 `app`, 패키지 `kr.skku.sameside`, 앱 이름 "같은편"
- `server/`(서버와 페이지)

**Performance Goals**
- 링크를 연 뒤 지점이 나오기까지 5초 안
- 상태가 상대 화면에 반영되기까지 4초 안(2초 주기 × 2)
- 탑승 뒤 공유 종료까지 10초 안(SC-006)

**Constraints**
- 주행 중 조작 금지(원칙 I)
- 같은 편이 아니면 내지 않는다(원칙 II)
- 위치는 픽업 동안만, 서버는 메모리만(원칙 III)
- 길 안내는 기본 내비(원칙 IV)

**Scale/Scope**: 차 한 대, 진행 중인 픽업 하나, 시연 지역 하나. 지역마다 후보 자리는 수십 개 이하다.

**Agent context 갱신**: 이 spec-kit 버전에는 `update-agent-context` 스크립트가 없어 건너뛴다. 프로젝트 안내는 `CLAUDE.md`에 손으로 둔다.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 운전 중에는 듣기만 | 통과 | 조작은 기어 P일 때만 받는다(FR-015). 주행 중 알림은 서비스가 음성 한 줄씩 낸다. 주행 중 화면은 Pleos가 가린다(research R6). |
| II. 같은 편이 아니면 내지 않는다 | 통과 | `Pick.kt`가 길가 쪽, U턴, 세우면 안 되는 자리, 애매한 자리를 걸러 낸다(research R10). 직선 방위 규칙의 한계는 150m 확인(FR-019·020)이 막는다. |
| III. 위치는 픽업 동안만 | 통과 | 동의 뒤에만 위치를 보낸다. 끝나는 조건 네 가지(FR-027)가 있다. 서버는 메모리에 현재 값만 둔다. 끝나면 위치를 지우고 상태 글자만 10분 둔다(contracts/api.md). |
| IV. 길 안내는 기본 내비에 | 통과 | 목적지·경유지는 NaviHelper로 넘긴다. 쓰는 신호(위치, 문, 착좌, 기어, 전화)는 모두 에뮬레이터에서 확인했다(research). |
| V. 최소 구현 | 통과(설명 2건) | 서버는 표준 라이브러리 파일 하나다. 통신은 플랫폼 기본이다. 지역 데이터는 Kotlin 코드다. 새 실행 단위(서버)와 새 의존성(ZXing)은 Complexity Tracking에 이유를 적었다. |
| VI. 판단 로직 테스트 | 통과 | 판단은 `Pick.kt` 순수 코드이고 `PickTest.kt`가 지킨다. 서버에는 assert 자체 점검이 있다. |

**Phase 1 뒤 다시 점검**: data-model·contracts를 쓴 뒤에도 위 판정은 그대로다. 서버 저장 항목에 이동 경로가 없고(contracts/api.md), 화면 계약상 주행 중 입력이 없다(contracts/ui.md).

## Project Structure

### Documentation (this feature)

```text
specs/001-sameside-pickup/
├── spec.md
├── plan.md              # 이 문서
├── research.md          # 에뮬레이터 확인과 결정 R1~R12
├── data-model.md        # 저장 형식, 판단 입력·출력, 상태, 지역 데이터
├── contracts/
│   ├── api.md           # 중계 서버 HTTP API
│   └── ui.md            # 차 화면, 음성 문장, 태울 사람 화면
├── quickstart.md        # 빌드·시연·검증
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks가 만든다
```

### Source Code (repository root)

```text
settings.gradle.kts  build.gradle.kts  gradle.properties  gradlew  gradlew.bat  gradle/   # DRIVE DNA에서 복사
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── res/xml/network_security_config.xml   # 10.0.2.2·localhost만 평문 HTTP 허용
    │   ├── res/values(-night)/colors.xml, res/mipmap-*            # DRIVE DNA에서 복사
    │   └── java/kr/skku/sameside/
    │       ├── Pick.kt          # 순수 판단 로직(안드로이드 import 없음)
    │       ├── Area.kt          # 지역 데이터 형식과 시연 지역 DEMO(순수 Kotlin)
    │       ├── People.kt        # 저장한 사람·차 정보 읽기/쓰기(형식 함수는 순수)
    │       ├── Relay.kt         # 서버와 주고받기(HttpURLConnection + org.json)
    │       ├── PickupService.kt # 포그라운드 서비스: 신호·위치·내비·음성·전화·중계를 잇는다
    │       ├── Ui.kt            # DRIVE DNA에서 복사
    │       ├── Screens.kt       # 정차 중 화면(contracts/ui.md)
    │       └── MainActivity.kt
    └── test/java/kr/skku/sameside/PickTest.kt
server/
├── server.py            # 중계 서버 + 자체 점검(check)
└── page.html            # 태울 사람 화면
scripts/
├── demo.py              # 시연 시나리오(reset, opposite, transit, transit-small, handsfree, reverse, wrongside, stop, tour)
└── scenes.txt           # 장면 좌표. demo.py와 PickTest.kt가 같이 읽는다(data-model §9)
README.md                # 실행 방법(Polish 단계)
```

**Structure Decision**: Android 단일 모듈과 `server/`, `scripts/`를 둔다.
- 판단 코드는 `Pick.kt`·`Area.kt`에만 둔다. 둘 다 안드로이드 없이 컴파일된다.
- 서비스는 상태를 하나(`Pick.Pickup`)로 들고, 신호가 올 때마다 순수 함수에 넣어 나온 "할 일"(말하기, 전화, 서버 보내기, 내비 설정)을 실행한다.

## 흐름 (한 번의 픽업)

1. **정차 중 시작**: 왼쪽 칸의 사람 줄을 누르면 그 사람으로 픽업을 시작한다(contracts/ui.md §1).
   - 서비스가 서버에 픽업을 만든다(`POST /api/pickups`). 받은 링크는 QR로 보인다.
2. **링크 열기**: 태울 사람이 링크를 열고 동의한다.
   - 페이지가 위치를 보낸다(`PUT …/passenger`).
   - 서비스는 2초마다 상태를 읽는다(`GET …`). 차 위치는 서비스가 처음부터 FusedLocation으로 받는다.
   - 동의, 태울 사람 위치, 차 위치가 처음 모두 갖춰지면 `Pick.choose(area, car, passenger, careful)`로 지점 1~3개를 계산한다. 같은 때 `Pick.transit`으로 대중교통 안도 계산해 서버에 올린다(`PUT …/car`, data-model §7).
3. **운전자 확인**(P): 지점을 확인하면 `requestRoute(지점)`과 `addWaypoint(진입 경유지, FIRST)`를 부른다.
4. **주행**: 위치가 올 때마다 아래를 한다.
   - `Pick.heading`, `Pick.remaining`을 갱신한다.
   - `Pick.step(area, pickup, event, now)`가 돌려준 할 일을 실행한다.
     - 자동 전화 시점: 전화 + 페이지 알림
     - 150m: 음성 한 줄. 왼쪽이면 FR-020 안내
     - 건너편 경고 표시
     - 나와 있어요·조금 늦어요 음성
5. **탑승**: 지점 50m 안에 서 있을 때 문이 열렸다 닫히고 빈 좌석 착좌가 켜지면 `met`이 된다. 공유를 끝낸다.
6. **끝**: 서버는 위치를 지우고 상태 글자만 10분 남긴다. 그 뒤에는 지운다.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| 차 앱 밖에 실행 단위가 하나 더 있다(중계 서버) | 설치 없는 링크 화면(FR-005)에 차 위치·남은 시간을 갱신하고, 태울 사람 위치·동의·버튼을 받아야 한다 | 정적 지도 링크를 문자로 보내면 갱신도 받기도 못 한다. 외부 BaaS는 계정·키가 필요하고 위치가 제3자에 남는다(원칙 III) |
| 새 의존성 ZXing core | 차 화면에 QR을 그린다(research R8) | QR 인코더를 손으로 쓰는 것은 수백 줄이다. 링크 글자만 보이면 폰으로 옮기기 어렵다 |
