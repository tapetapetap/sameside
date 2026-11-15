# Implementation Plan: 같은편 7차 — 시연용 그림 바로잡기와 제출 전 화면 수정

**Branch**: `007-demo-pictures` (git 브랜치 `car-sameside`, 되돌리기 태그 `backup/before-pictures-sameside` = bd18e8c) | **Date**: 2026-11-14 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/007-demo-pictures/spec.md`, 설계 `docs/superpowers/specs/2026-11-14-demo-pictures-design.md`(같은편 S1~S4)

## Summary

네 곳 모두 그리기와 문구만 고친다. 첫 화면은 `situation`의 픽업 없음 갈래를 단계 카드로 바꾸고, 사람이 없는지는 참/거짓 하나로 받는다. 지도는 공용 `corners()`와 `SpotMap.onDraw()`만, 페이지는 `drawing()`만 고친다.

| 바꿀 것 | 파일 · 함수 | 요구 |
|---|---|---|
| 새 `private fun Ui.intro(waiting, noPeople)`(단계 카드), `situation`에 `noPeople` 인자, `null ->` 갈래(105행)가 `intro` | `app/src/main/java/kr/skku/sameside/Screens.kt` | FR-112~115 |
| `situation(…, noPeople = list.isEmpty(), …)` | `MainActivity.kt` `render` | FR-113 |
| 출처 뒤 카드색 85% 둥근 바탕, 글자색 text2 | `Views.kt` `MapBase.corners`(284~286행) | FR-116 |
| 점·걷는 점선·가장자리 화살표를 `AVATAR_LINES[slot]`, 이름 text1 | `Views.kt` `SpotMap.onDraw`(320·321·330·365·376·394·403행) | FR-117 |
| 화살표 머리 12단위 고정, 선은 만날 곳 22단위 앞에서 끝, ★은 점 가운데·맨 뒤, 글자 테두리, 아래 끝이면 "나"·"차"를 점 위로, 출처 | `server/page.html` `drawing`(113~119행) | FR-118 |

## Technical Context

**Language/Version**: Kotlin(JDK 17), Android View. 페이지는 HTML·SVG·JS. 서버(Python 3)는 바뀌지 않는다

**Primary Dependencies**: 새 의존성 없음

**Storage**: 바뀌지 않음

**Testing**: 기존 단위 테스트 160개(`PickTest` 95, `GroupTest` 25, `ChainTest` 20, `CardMapTest` 13, `GlanceTest` 7), `python3 server/server.py check`. 새 테스트 없음(research R6). 화면은 에뮬레이터와 헤드리스 크롬 캡처

**Target Platform**: Pleos Connect 에뮬레이터(AAOS API 34, 2560×1440, density 160 = 1dp 1px). 페이지는 폰 브라우저

**Project Type**: 차량용 안드로이드 앱 + 중계 서버가 보내는 웹 페이지

**Performance Goals**: 해당 없음(그리기 몇 줄)

**Constraints**: 6차 규칙(화면 글자 24sp, 그림 안 글자 20sp 이상·출처 14sp 예외, 대비 4.5:1, 그림 선·점 3:1). 판단 로직·음성·서버 API 불변(FR-119)

**Scale/Scope**: 앱 코드 3개 파일(Screens·MainActivity·Views), 페이지 1개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판단 |
|---|---|
| I. 운전 중에는 듣고 한 줄만 본다 | 통과. 바뀌는 것은 정차 화면과 태울 사람 페이지다. 주행 중 한 줄 카드는 그대로다 |
| II. 같은 편이 아니면 내지 않는다 | 해당 없음(지점 판단 그대로) |
| III. 위치는 픽업하는 동안만 | 통과. 저장·전송이 그대로이고, 페이지 그림은 이미 받은 값만 그린다 |
| IV. 길 안내는 기본 내비에 | 해당 없음 |
| V. 최소 구현 | 통과. 새 파일·의존성·추상화가 없다. 단계 카드는 다른 두 앱과 같은 몇 줄(저장소가 달라 공유 모듈이 없음), 출처 바탕은 `mapName`과 같은 방식이다 |
| VI. 판단 로직은 테스트로 | 통과. 판단 로직을 바꾸지 않는다. 새 코드는 그리기·문구라 캡처로 본다(research R6) |

Phase 1 뒤에도 같다. Complexity Tracking: 없음.

## Project Structure

### Documentation (this feature)

```text
specs/007-demo-pictures/
├── spec.md, plan.md, research.md, quickstart.md
├── checklists/requirements.md
├── goldfish-review.md
├── verification.md      # T015(구현 뒤 결과)
└── tasks.md
```

data-model.md와 contracts/는 만들지 않는다. 저장 데이터, 서버 API, 페이지가 받는 값이 바뀌지 않고, 화면 값(문구·크기·색)은 spec FR-112~118과 research에 있다.

### Source Code (repository root)

```text
app/src/main/java/kr/skku/sameside/
├── Screens.kt        # situation(noPeople 인자), intro(새, 단계 카드)
├── MainActivity.kt   # render: noPeople = list.isEmpty()
└── Views.kt          # MapBase.corners(출처 바탕), SpotMap.onDraw(사람 색)
server/page.html      # drawing(화살표·★·테두리·출처)
```

**Structure Decision**: 1~6차 구조 그대로. 새 파일 없음. 기술 스택이 그대로라 스택 그림은 만들지 않는다.

## Phase 0: Research

[research.md](./research.md): R1 사람이 없는지 넘기기, R2 단계 카드 틀과 문구, R3 출처 바탕, R4 사람 색, R5 페이지 그림, R6 확인 방법.

## Phase 1: Design

- [quickstart.md](./quickstart.md): 빌드·점검, 에뮬레이터 준비(세 앱이 함께 씀), 첫 화면 캡처, `chain` 장면과 폰 페이지 캡처, 마무리.
- Figma(2-0.5, 설계·리뷰·승인을 맡은 대화 세션이 그림): 첫 화면(사람 0명·5명), 출처 바탕, 가는 중 사람 색, 페이지 그림. 링크는 tasks.md 머리에 있고, 기준은 spec FR이다.
- 에이전트 컨텍스트: 저장소의 `CLAUDE.md`는 인수인계용이라 바꾸지 않는다(6차와 같음).
