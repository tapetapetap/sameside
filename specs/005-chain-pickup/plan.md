# Implementation Plan: 같은편 5차 — 차례로 태우기와 구간 줄

**Branch**: `005-chain-pickup` (git 브랜치 `pickup-chain`, 되돌리기 태그 `backup/sameside-4`) | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/005-chain-pickup/spec.md`, 설계 `docs/superpowers/specs/2026-11-12-pickup-chain-design.md`

## Summary

함께 태우기(3차)의 사람별 상태(`Member`)와 대중교통 받아들임(4차 `accept`)의 "가는 중에 지점 바꾸기"를 그대로 써서, 사람마다 자기 지점을 차례로 들르는 모드를 더한다. 계산은 `Pick.kt` 순수 코드와 JUnit, 그림은 `Views.kt`의 새 View 하나, 화면은 `Screens.kt` 두 자리(만날 곳 카드, 가는 중 맨 위)다.

| 기능 | 순수 코드(테스트) | 실행·화면 |
|---|---|---|
| 차례 계획 | `Pick.chain(area, car, people, dest)` → `Chain`(`ChainStop` 목록) | `chooseGroup`(확정 전 다시 고르기 포함)에서 계산해 `Pickup.chain`에 둔다 |
| 확정 | `Event.ConfirmChain` 처리(`Pick.step`) | 만날 곳 카드 버튼 "차례로 태울게요" |
| 넘어가기 | `board`·`leave`의 차례 분기, `advance`, `dropStop` | `Action.SetNavi`(다음 지점), `Action.Speak` |
| 사람별 남은 시간 | `Pick.memberEta(p, i)` | `going`의 알림·전화 문턱, `Relay.carJson`의 `etaSec` |
| 줄 노드 자리 | `chainXs(times, x0, x1, minGap)`(Views.kt 최상위) | 새 `ChainStrip`(Views.kt) |
| 목적지 | 없음(`Pickup.destPos`) | `PickupService`가 내비 목적지 정보(위도·경도)를 받아 `Event.Dest`로 넣는다 |
| 페이지 | 없음 | `Relay.carJson`: 차례면 `chosen` = 그 사람 지점 번호, `etaSec`·`arrived`는 그 사람 것 |
| 시연 | 없음 | `scripts/demo.py chain`, 장면 데이터 `scripts/scenes.txt`에 `chain` 줄들 |

## Technical Context

**Language/Version**: Kotlin(JDK 17), Python 3(시연). 1~4차와 같다
**Primary Dependencies**: 새 의존성 없음. Pleos NaviHelper `DestinationInfo`(이미 받음)의 위도·경도를 더 쓴다
**Storage**: 없음(픽업 상태는 메모리, 1~4차와 같다)
**Testing**: JUnit 133개 + 새 `ChainTest.kt`
**Target Platform**: Pleos Connect 에뮬레이터(API 34, 2560×1440), 디버그 빌드, 중계 서버 `server/server.py`
**Project Type**: 차량 앱 + 중계 서버 + 태울 사람 웹 페이지(서버·페이지는 바꾸지 않음)
**Performance Goals**: 차례 계획은 사람 3명까지, 순서 고르기는 욕심 방식이라 계산량을 무시할 만하다
**Constraints**
- 헌법 I(운전 중에는 듣기만 한다): 줄은 정차 중 화면에만 있다. 같은편은 주행 최적화 앱이 아니어서 주행 중에는 Pleos가 가린다. 넘어갈 때 음성은 한 줄이다.
- 헌법 IV(길 안내는 기본 내비에): 지점마다 지금처럼 내비에 넣는다(한 곳씩).
- 경유지 방식은 내비 경유지 두 칸(진입, 지점)을 한 지점에 쓴다(2차). 그래서 한 번에 한 지점만 넣는다.
**Scale/Scope**: 수정 6개(Pick.kt, Screens.kt, Views.kt, MainActivity.kt, PickupService.kt, Relay.kt), demo.py와 장면 데이터, 새 테스트 1개

## Constitution Check

| 원칙 | 판정 |
|---|---|
| I. 운전 중에는 듣기만 한다 | 통과. 새 화면(차례로 카드, 줄)은 정차 중에만 보인다. 확정 버튼은 정차(P)에서만 받는다(지금 확정과 같은 조건). 넘어갈 때 음성은 한 줄이고, 탑승은 정차 중에 일어난다 |
| II. 같은 편이 아니면 내지 않는다 | 통과. 지점은 사람마다 한 명 픽업 1순위(같은 편 판단 그대로)다 |
| III. 위치는 픽업하는 동안만 | 통과. 새로 저장하는 위치가 없다. 내비 목적지 좌표는 이번 픽업 메모리에만 둔다 |
| IV. 길 안내는 기본 내비에 맡긴다 | 통과. 지점마다 내비에 넣는다. 구간 시간은 화면 표시용 어림이다 |
| V. 최소 구현 | 통과. 사람별 상태·지점 바꾸기·내비 다시 넣기는 3·4차 것을 쓴다. 새 View는 줄 하나다. 순서는 욕심 방식이다 |
| VI. 판단 로직은 테스트로 | 통과. `chain`, `memberEta`, `chainXs`, 넘어가기·빠짐 전이를 순수 코드로 두고 테스트를 먼저 쓴다 |

**설계 뒤 다시 봄**: 위와 같다.

## Project Structure

```text
specs/005-chain-pickup/  spec, plan, research(R42~R49), data-model, contracts/ui.md, quickstart, tasks
app/src/main/java/kr/skku/sameside/
├── Pick.kt          # ChainStop·Chain, chain(), memberEta(), Pickup.chain·chained·destPos, Member.stop, Event.ConfirmChain·Dest, step 분기
├── Views.kt         # chainXs, ChainStrip
├── Screens.kt       # 만날 곳 차례로 카드, 가는 중 맨 위 줄
├── MainActivity.kt  # onChain → Event.ConfirmChain
├── PickupService.kt # 목적지 위도·경도 → Event.Dest, 픽업 시작 때 목적지 묻기
└── Relay.kt         # 차례면 사람별 chosen·etaSec·arrived
app/src/test/java/kr/skku/sameside/ChainTest.kt   # 새
scripts/demo.py, scripts/scenes.txt   # chain
```

## Complexity Tracking

위반 없음.
