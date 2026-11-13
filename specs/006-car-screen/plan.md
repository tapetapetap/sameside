# Implementation Plan: 같은편 6차 — 차 화면 개선과 주행 중 한 줄 카드

**Branch**: `006-car-screen` (git 브랜치 `car-sameside`, 되돌리기 태그 `backup/before-car-sameside` = pickup cd40c13) | **Date**: 2026-11-13 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/006-car-screen/spec.md`, 설계 `docs/superpowers/specs/2026-11-13-car-screen-design.md`(C1~C5, S1~S7)

## Summary

한 줄 카드의 글 고르기는 순수 함수(`Pick.kt`의 `glance`)로 두고 테스트한다. 화면은 `MainActivity.render`가 주행 중이면 사람 칸을 숨기고 상황 칸에 한 줄 카드만 붙인다. 글자·칸·색은 `Ui` 도우미와 색 토큰부터 바꾼다.

| 바꿀 것 | 파일 | 요구 |
|---|---|---|
| 주행 최적화 선언 | `AndroidManifest.xml` | FR-098 |
| 한 줄 글 고르기 `glance(p, creating, contactOf)` + `Glance` + 짧은 연락 상태 글 | `Pick.kt`(순수), 새 `GlanceTest.kt` | FR-099·100 |
| 한 줄 카드 화면 `Ui.glanceCard` | `Screens.kt` | FR-098 |
| 주행 중 분기: 사람 칸 숨김, 한 줄 카드, 입력 창 닫기 | `MainActivity.kt`, `activity_main.xml`·`layout-port/activity_main.xml`(사람 칸 바깥에 id) | FR-098·101 |
| 입력 창 추적·닫기(닫힐 때 비우기, 액티비티가 끝날 때 닫기) | `Screens.kt`(`show`, 새 `closeDialog`), `MainActivity.onDestroy` | FR-101 |
| `small`·`chip` 24sp, 버튼·누르는 칩 76dp, 20·22sp 고정 글자 → 24sp | `Ui.kt`, `Screens.kt` | FR-102·104 |
| 지도·구간 그림·연락 줄 글자 20sp 이상, 출처 14sp, 구간 그림 노드 첫 글자 삭제 | `Views.kt` | FR-103 |
| 빈 자리가 없으면 덜 중요한 이름표를 그리지 않음: 새 `freeIndex`(−1 가능), `place(…, optional)` | `Views.kt`, `GroupTest.kt`에 테스트 1줄 | FR-103 |
| "가까이만" 칩 글자 `blue_text`, 사람과 이동 경로 지도의 사람 이름표 글자 `text1` | `Screens.kt`, `Views.kt` | FR-105 |
| `text3` 낮 #62656D, 새 `green_text`·`amber_text`·`blue_text` | `values/colors.xml`, `values-night/colors.xml` | FR-105 |
| 지도 사람 이름·연락 줄 아이콘·숫자 → 진한 글자색 | `Views.kt` | FR-105 |
| 취소 두 번 누르기, D·취소 버튼 없는 화면에서 확인 지우기 | `MainActivity.kt`, `Screens.kt`(`situation`) | FR-106 |
| 뒤로 버튼, 설정 변화 때 고르기 보존 | `MainActivity.kt` | FR-107·108 |
| 만날 곳 배치(차례 카드 위로, 두 번째부터 짧게) | `Screens.kt`(`situation`, `spotCard`) | FR-109 |
| 문구 세 곳 | `Screens.kt`(`going`), `Views.kt`(`ContactRow.status`) | FR-110 |
| 시연 스크립트 취소 두 번 누르기 | `scripts/demo.py` | spec Assumptions |
| 매니페스트의 옛 주석("DO를 선언하지 않는다") 바꾸기, README의 옛 문구·주행 중 설명 | `AndroidManifest.xml`, `README.md` | FR-098·110 |
| 헌법 1.1.0(원칙 I) | `.specify/memory/constitution.md` | spec Assumptions |

## Technical Context

**Language/Version**: Kotlin(JDK 17), Android View, Python 3(시연·서버). 1~5차와 같다

**Primary Dependencies**: 새 의존성 없음

**Storage**: 바뀌지 않음. 고르기 상태는 설정 변화 동안만 `Bundle`에 둔다

**Testing**: 기존 단위 테스트 153개(`PickTest` 95, `GroupTest` 25, `ChainTest` 20, `CardMapTest` 13) + 새 `GlanceTest.kt` + `GroupTest`에 `freeIndex` 1개. 서버 `python3 server/server.py check`

**Target Platform**: Pleos Connect 에뮬레이터(AAOS API 34, 2560×1440, density 160)

**Project Type**: 차량용 안드로이드 앱 + 시연 서버(`server/`, 바뀌지 않음)

**Performance Goals**: 한 줄 카드는 위치가 올 때마다(약 1초) `render`가 부르지만, `replace`가 글자가 같으면 화면을 그대로 둔다(지금 규칙). 그래서 다시 그리기는 분이나 상태 글이 바뀔 때만이다

**Constraints**: 앱 칸 가로 약 1,700dp(전체), 1:1 세로 칸 약 850dp. 차 기준(spec 용어). Pleos 주의분산 기준: 주행 중 화면은 움직임과 누를 것이 없다

**Scale/Scope**: 앱 코드 5개 파일(Pick·Ui·Screens·Views·MainActivity), 테스트 2개(새 GlanceTest, GroupTest), 레이아웃 2개, 색 2개, 매니페스트, README, 시연 스크립트 1개, 헌법

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판단 |
|---|---|
| I. 운전 중에는 듣기만 한다 | **고침 필요(사용자 결정)**. 지금 원칙은 "주행 중 운전자가 화면을 보게 하지 않는다"다. 사용자가 주행 중 한 줄 카드를 골랐다(2026-11-13). 헌법을 1.1.0으로 고친다: 주행 중 화면은 움직이지 않는 한 줄 카드 하나이고 누르기를 받지 않는다. 알림이 음성 한 줄인 것, 조작은 정차 중에만인 것은 그대로다. 고친 뒤 통과 |
| II. 같은 편이 아니면 내지 않는다 | 해당 없음(지점 판단 그대로) |
| III. 위치는 픽업하는 동안만 | 해당 없음(저장·전송 그대로) |
| IV. 길 안내는 기본 내비에 | 통과. 한 줄 카드는 길을 안내하지 않는다(지점 이름도 넣지 않음) |
| V. 최소 구현 | 통과. 새 의존성·추상화가 없다. 한 줄 카드는 기존 `replace` 비교로 다시 그리기를 줄인다 |
| VI. 판단 로직은 테스트로 | 통과. 한 줄 글 고르기를 순수 함수와 테스트로 둔다 |

Phase 1 뒤에도 같다. Complexity Tracking: 원칙 I 개정(위 표), 별도 복잡도 없음.

## Project Structure

### Documentation (this feature)

```text
specs/006-car-screen/
├── spec.md, plan.md, research.md
├── contracts/ui.md      # 한 줄 카드, 요소별 값, 색, 문구, 뒤로·보존·취소
├── quickstart.md
├── checklists/requirements.md
└── tasks.md
```

data-model.md는 만들지 않는다. 저장 데이터와 서버 형식이 바뀌지 않는다. 한 줄 카드 내용(`Glance`)은 contracts §1에 적는다.

### Source Code (repository root)

```text
app/src/main/
├── AndroidManifest.xml
├── java/kr/skku/sameside/
│   ├── Pick.kt           # glance(새, 순수), Glance, 짧은 연락 상태 글
│   ├── Ui.kt             # small·chip·action
│   ├── Screens.kt        # button, people, situation, going, spotCard, chainCard, glanceCard(새), show/closeDialog
│   ├── Views.kt          # 지도 글자, ContactRow, ChainStrip
│   └── MainActivity.kt   # 주행 중 분기, 취소 확인, 뒤로, 상태 보존
├── res/layout/activity_main.xml, res/layout-port/activity_main.xml
└── res/values/colors.xml, res/values-night/colors.xml
app/src/test/java/kr/skku/sameside/GlanceTest.kt   # 새
scripts/demo.py
.specify/memory/constitution.md
```

**Structure Decision**: 1~5차 구조 그대로. 새 파일은 테스트 하나다.

## Phase 0: Research

[research.md](./research.md): R1 차 기준 값(안 써본 기능 6차 R1과 같음), R2 주행 최적화와 한 줄 카드 갱신, R3 짧은 연락 상태 글, R4 지도 이름표 키우기, R5 취소 두 번 누르기, R6 설정 변화 보존, R7 색 값.

## Phase 1: Design

- [contracts/ui.md](./contracts/ui.md): §1 한 줄 카드, §2 요소별 값, §3 색, §4 문구, §5 뒤로·보존·취소.
- [quickstart.md](./quickstart.md): 빌드·테스트, 주행 중 한 줄 카드, 정차 화면 크기, 취소·뒤로·낮밤, 장면 회귀.
- 에이전트 컨텍스트: 저장소의 `CLAUDE.md`는 인수인계용이라 바꾸지 않는다.
