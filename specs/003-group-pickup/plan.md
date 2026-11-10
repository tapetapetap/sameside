# Implementation Plan: 같은편 3차 — 여러 명 함께 태우기, 지도 카드, 눈에 띄는 연락 상태

**Branch**: `003-group-pickup` (git 브랜치 `pickup`) | **Date**: 2026-11-11 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/003-group-pickup/spec.md`, 설계 `docs/superpowers/specs/2026-11-11-pickup-group-design.md`

## Summary

1·2차 구조를 그대로 잇는다.
- 판단은 `Pick.kt`의 순수 함수와 `Pick.step` 전이에 더하고 JUnit으로 지킨다.
- 핵심 변경은 `Pickup`이 함께 타는 사람 목록(`members`)을 갖게 하는 것이다. 사람마다 다른 값(링크, 태울 사람 상태, 걷는 시간, 알림·전화, 건너편 경고, 탑승)은 `Member`로 옮긴다. 한 명 픽업은 목록이 한 명이고, 1·2차 동작과 단위 테스트 95개를 그대로 지킨다.
- 서비스는 사람마다 서버 기록을 읽고 올린다(서버 API는 그대로, 필드 셋 추가).
- 화면은 `Screens.kt`에 더하고, 새 그리기 View 두 개(지도, 연락 상태)를 `Views.kt` 한 파일에 둔다.

| 기능 | 판단(Pick.kt) | 실행·화면 |
|---|---|---|
| 함께 태우기 | 함께 고르기(사람별 걷기·대중교통), 5분 가산 순위, 근처로 데리러 가기 어림, 빠짐, 전화 간격, 탑승 추론, 묶은 문장 | 사람 목록 고르기, 사람별 링크·QR, 카드 사람별 줄, 서비스가 사람마다 읽고 올림 |
| 카드 지도 | 없음(지점·사람·차 값은 이미 있음) | `SpotMap`(Canvas, `Area` 길 선) |
| 연락 상태 | 사람마다 알림·전화 시각(`callAt`) | `ContactRow`(Canvas 링·물결), 통화 여부(`isInCall`), 페이지 맥박 띠(헤드업 알림은 R23 측정 뒤 뺐다) |
| 화면 디테일 | 확정 때 남은 거리·시각, 만남 시각 | 아바타, 진행 점, 남은 분·막대, 만남 요약, 시연 다섯 명 |

## 현재 상태 (구현 전)

- 1·2차 구현과 리뷰가 끝났고 `sameside` main(47314c5)에 올라가 있다. 단위 테스트 95개, 서버 점검 아홉 항목, 장면 16개(1차 11 + 2차 5)가 통과한다.
- 11/10 밤에 그 16개와 `tour`(1차 장면 셋을 잇는 장면)를 녹화해 "같은편 주행 영상" 아티팩트로 올렸다(17장면 통과). SC-015의 기준은 이 17장면이다.

## Technical Context

**Language/Version**: Kotlin 2.0.21(JDK 17), Python 3(서버·시연, 표준 라이브러리), HTML/JS(페이지). 1·2차와 같다
**Primary Dependencies**: 1·2차와 같다. 새 의존성 없음(그리기는 `android.graphics`, 아이콘은 벡터 드로어블)
**Storage**: 1·2차와 같다. 시연 `reset`이 사람 다섯 명을 SharedPreferences `people`에 쓴다
**Testing**: JUnit(`PickTest.kt`, 새 `GroupTest.kt`), `python3 server/server.py check`(열 항목으로), 에뮬레이터 장면
**Target Platform**: Pleos Connect 에뮬레이터(API 34)
**Project Type**: 차량 앱 + 중계 서버 + 웹 페이지(1·2차와 같음)
**Performance Goals**: 연락 줄 초읽기 1초 단위, 전화 뒤 3초 안에 통화 표시(SC-017). 지도는 위치가 올 때마다 다시 그린다(길 점 150개 안팎)
**Constraints**: 주행 중 음성 5줄 이하, 조작은 P에서만(1차). 다른 사람의 위치는 페이지에 보내지 않는다(원칙 III)
**Scale/Scope**: 픽업 하나에 사람 1~3명, 시연 지역 하나

## Constitution Check

| 원칙 | 판정 |
|---|---|
| I. 운전 중에는 듣기만 | 통과. 고르기·함께 데리러 가기는 P에서만 된다. 함께 태우기 문장은 이름을 묶어 한 줄이고 5줄 상한을 함께 쓴다. 연락 줄·지도는 보는 것뿐이다 |
| II. 같은 편이 아니면 내지 않는다 | 통과. 함께 고르기 후보도 1차 같은 편 규칙을 그대로 지난다(FR-050) |
| III. 위치는 픽업하는 동안만 | 통과. 사람 위치는 1·2차처럼 그 사람 기록에만 있다. 함께 타는 사람에게는 이름·가는 방법·탑승 여부만 보낸다 |
| IV. 길 안내는 기본 내비에 | 통과. 목적지는 여전히 하나다(2차 경유지 방식 그대로) |
| V. 최소 구현 | 통과(Complexity Tracking 한 줄). 새 파일은 `Views.kt`와 아이콘 드로어블 다섯 개다. 차례로 태우기, 대중교통 다시 고르기는 넣지 않는다 |
| VI. 판단 로직은 테스트로 | 통과. 함께 고르기, 근처 어림, 빠짐, 전화 간격, 탑승 추론, 묶은 문장은 `GroupTest`를 먼저 쓴다. 1·2차 테스트는 `Member`로 옮긴 이름만 고친다 |

## Project Structure

### Documentation (this feature)

```text
specs/003-group-pickup/
├── spec.md
├── plan.md              # 이 문서
├── research.md          # R20~R27
├── data-model.md        # §1 Member·Pickup, §2 Plan·함께 고르기, §3 근처 어림, §4 전이, §5 올리는 값, §6 시연 좌표
├── contracts/
│   ├── api.md           # 차가 올리는 새 필드(group, plan, calling)
│   └── ui.md            # 사람 목록, 링크, 카드 지도, 가는 중 연락 줄, 만남, 페이지
├── quickstart.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/kr/skku/sameside/
├── Pick.kt            # Member, Plan, chooseGroup, nearby, 전이(사람별)
├── PickupService.kt   # 사람마다 GET/PUT, isInCall, beginGroup
├── Relay.kt           # carJson(p, m), remote(j, who)
├── Screens.kt         # 고르기, 사람별 링크, 카드(지도·사람별 줄), 가는 중(연락 줄·막대), 만남
├── Views.kt           # 새: SpotMap, ContactRow
├── Ui.kt              # avatar
└── MainActivity.kt    # 고르기 상태, sig에 그리기 View 키
app/src/main/res/drawable/ic_call.xml, ic_bell.xml, ic_check.xml, ic_walk.xml, ic_train.xml   # 새(Material Icons, Apache 2.0)
app/src/main/AndroidManifest.xml   # READ_PHONE_STATE
app/src/test/java/kr/skku/sameside/GroupTest.kt   # 새
app/src/test/java/kr/skku/sameside/PickTest.kt    # Member로 옮긴 이름만
server/server.py, server/page.html
scripts/demo.py, scripts/scenes.txt
```

**Structure Decision**: 1·2차와 같은 한 모듈. 판단은 `Pick.kt`에 남기고, 그리기만 `Views.kt`로 나눈다(`Screens.kt`가 이미 대화상자와 카드를 들고 있어서).

## Phase 0: Research

research.md R20~R27.
- R20: 함께 고르기 수치(시험 계산)
- R21: 탑승 판정과 에뮬레이터 좌석
- R22: 통화 여부
- R23: 주행 중 헤드업
- R24: 지도 그리기
- R25: 연락 줄 효과와 화면 다시 붙이기
- R26: 서버·페이지 필드
- R27: 시연 장면

## Phase 1: Design

data-model.md, contracts/api.md, contracts/ui.md, quickstart.md

## Complexity Tracking

| 위반 | 필요한 이유 | 버린 더 단순한 안 |
|---|---|---|
| `Pickup`을 `Pickup` + `Member`로 나눔(1·2차 코드·테스트 이름 수정) | 여러 명이 한 차·한 지점을 함께 쓰려면 차 쪽 값과 사람 쪽 값이 갈라져야 한다 | 한 명짜리 픽업 여러 개를 묶는 조정자: 음성·내비·도착 질문이 겹쳐 하나만 남기는 규칙이 따로 든다(설계 D2) |
