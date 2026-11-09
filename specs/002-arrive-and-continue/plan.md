# Implementation Plan: 같은편 2차 — 알리고 나서 전화, 도착 알림, 원래 길로 이어 가기

**Branch**: `002-arrive-and-continue` (git 브랜치 `pickup`) | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/002-arrive-and-continue/spec.md`, 설계 `docs/superpowers/specs/2026-11-10-pickup-additions-design.md`

## Summary

1차(`specs/001-sameside-pickup/`)의 구조를 그대로 잇는다.
- 판단은 `Pick.kt`의 순수 함수와 `Pick.step` 전이로 더하고 JUnit으로 지킨다.
- `PickupService`는 새 신호(깜빡이)를 이벤트로 넣고, 새 할 일(내비 경유지 방식)을 실행한다.
- 서버와 페이지는 필드 몇 개와 띠·문구를 더한다.
- 시연 스크립트에 장면 세 개(`ack`, `arrive`, `continue`)를 더한다.

| 기능 | 판단(Pick.kt) | 실행·화면 |
|---|---|---|
| 알림 → 60초 → 전화 | 문턱에서 `notifiedAt`, 마감에서 반응 보기 | 페이지 "지금 나가요" 버튼(`ack`) |
| 도착 알림 확인 | 깜빡이·50m·3초 정차 → 카드 | 차 화면 카드(P), 페이지 도착 띠 |
| 원래 목적지로 이어 가기 | 탑승 문장 고르기, 운전석 문 빼기 | 서비스가 내비에 경유지로 넣고 탑승 때 경유지만 지움 |
| 건너기·유턴 비교 | 지점마다 `uturnSec` | 차 카드 한 줄, 페이지 한 줄 |
| 지나서 문구 | 지점마다 기준 랜드마크, 150m 문장, 동승자 문장 | 음성, 페이지 한 줄 |

## 현재 상태 (구현 전)

- 1차 구현은 끝났다. 작업 41개, 단위 테스트 63개, 에뮬레이터 장면 11개와 `tour`가 통과했다(`specs/001-sameside-pickup/verification.md`).
  - 1차 /code-review는 돌고 있다. /security-review는 그 뒤다.
  - 커밋은 없다.
- 1차 뒤에 더해 둔 것: 디버그 빌드 전용 `app/src/debug/.../DemoReceiver.kt`
  - adb broadcast로 내비에 목적지를 넣고(`route`), 상태를 로그로 남긴다(`state`). 경유지 지우기(`remove`)와 `cancel`도 받는다.
  - research R15를 재는 데 썼고, `continue` 장면이 원래 목적지를 넣는 데 쓴다.

## Technical Context

**Language/Version**: Kotlin 2.0.21(JDK 17), Python 3(서버·시연, 표준 라이브러리), HTML/JS(페이지). 1차와 같다
**Primary Dependencies**: Pleos Vehicle 2.3.2(새로 `TurnSignal`), NaviHelper 3.0.5(새로 `getDestinationInfo`·`getWaypointInfo`·`removeWaypoint`), 나머지 1차와 같음
**Storage**: 1차와 같음 + SharedPreferences `people`에 `navOwn`(앱이 마지막으로 목적지로 넣은 지점 이름) 한 칸
**Testing**: JUnit(`PickTest.kt`), `python3 server/server.py check`, 에뮬레이터 장면
**Target Platform**: Pleos Connect 에뮬레이터(API 34)
**Project Type**: 차량 앱 + 중계 서버 + 웹 페이지(1차와 같음)
**Performance Goals**: 도착 확인 질문은 조건이 갖춰진 뒤 5초 안(Tick 2초, 위치 1초)
**Constraints**: 주행 중 음성 5줄 이하(1차), 조작은 P에서만(1차)
**Scale/Scope**: 픽업 하나, 시연 지역 하나(올리브영 랜드마크 하나 추가)

## Constitution Check

| 원칙 | 판정 |
|---|---|
| I. 운전 중에는 듣기만 | 통과. 도착 확인 카드는 P에서만 눌리고, 주행 중에는 음성 질문만이다. 알림 단계는 운전자 음성이 없어 5줄 상한에 여유가 생긴다 |
| II. 같은 편이 아니면 내지 않는다 | 통과. 유턴 비교는 표시만 하고 제안을 바꾸지 않는다(FR-042) |
| III. 위치는 픽업하는 동안만 | 통과. 새로 남기는 위치는 알림 때 태울 사람 위치 하나로, 픽업 객체 안에만 둔다. 서버에 새 위치 필드는 없다 |
| IV. 길 안내는 기본 내비에 | 통과. 원래 목적지를 지우지 않고 경유지로 끼워 넣는다. 오히려 내비에 더 맡긴다 |
| V. 최소 구현 | 통과. 새 파일은 디버그 전용 `DemoReceiver.kt` 하나다. 판단은 `Pick.kt`, 실행은 `PickupService.kt`에 더한다 |
| VI. 판단 로직은 테스트로 | 통과. 새 판단(두 단계 전화, 도착 확인, 탑승 문장, 운전석 문, 유턴 시간, 기준 랜드마크, 을/를)은 모두 JUnit을 먼저 쓴다 |

## Project Structure

### Documentation (this feature)

```text
specs/002-arrive-and-continue/
├── spec.md
├── plan.md            # 이 문서
├── research.md        # R14~R19
├── data-model.md      # 1차 data-model에 더하는 것
├── contracts/
│   ├── api.md         # 서버 필드 추가
│   └── ui.md          # 차 화면·음성·페이지 문구 추가
├── quickstart.md      # 새 장면과 바뀐 기대값
├── checklists/requirements.md
└── tasks.md           # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/kr/skku/sameside/
├── Pick.kt            # Rules 추가, Spot·Pickup 필드, step 전이(알림·마감·도착·Arrive·Route·운전석 문), choose(유턴·기준 랜드마크·동승자 문장), toObject(을/를)
├── PickupService.kt   # 깜빡이 구독, setNavi 경유지 방식, ClearNavi 갈래, navOwn 저장
├── Relay.kt           # carJson(arrived, spots[].crossing·uturnSec·standLine), remote(ack)
├── Screens.kt         # 지점 카드 비교 줄, going 도착 확인 카드·원래 목적지 대신 줄
├── People.kt          # loadNavOwn·saveNavOwn
└── Area.kt            # Landmark.spot, 올리브영 랜드마크(기준 전용)
app/src/main/AndroidManifest.xml  # CAR_LIGHTS(깜빡이)
app/src/debug/
├── AndroidManifest.xml
└── java/kr/skku/sameside/DemoReceiver.kt   # 시연·측정용(디버그 빌드만)
app/src/test/java/kr/skku/sameside/PickTest.kt  # 새 테스트 묶음
server/server.py       # ack, arrived 필드와 자체 점검 한 줄
server/page.html       # "지금 나가요", 도착 띠, 비교 줄, 서 있을 곳 문장
scripts/demo.py        # ack·arrive·continue 장면, handsfree·fallback 기대 시각
```

**Structure Decision**: 1차 파일에 더한다. 새 클래스·파일은 디버그 전용 수신기 하나뿐이다.

## Complexity Tracking

| 위반 | 왜 필요한가 | 더 단순한 대안을 버린 이유 |
|---|---|---|
| 디버그 전용 `DemoReceiver` | 에뮬레이터 내비에 "원래 목적지"를 넣을 방법이 이것뿐이다. 내비 앱의 `NAVIGATE` 인텐트는 기존 작업만 앞으로 가져왔다(R15) | 내비 화면 검색으로 넣기는 스크립트가 길고 깨지기 쉽다. 디버그 빌드에만 들어가 출시 APK에는 없다 |
| `navOwn` 한 칸 저장 | 에뮬레이터 내비는 안내 중 `cancelRoute`를 무시해, 1차 픽업의 지점이 목적지로 남는다(R15). 그것을 "원래 목적지"로 잘못 보면 다음 픽업이 경유지 방식이 된다 | 실차에서는 취소하면 목적지가 지워져 필요 없을 수 있다. 하지만 취소가 실패해도 잘못 판단하지 않게 하는 방어가 한 줄이라 둔다 |
