# Implementation Plan: 같은편 4차 — 두 지도 카드, 대중교통이 보이는 화면, 시연 영상 자막·전화 강조

**Branch**: `004-maps-transit-video` (git 브랜치 `pickup`) | **Date**: 2026-11-11 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/004-maps-transit-video/spec.md`, 설계 `docs/superpowers/specs/2026-11-11-pickup-feedback-design.md`(D1~D10, 사용자 승인)

## Summary

1·2·3차 구조를 그대로 잇는다. 판단(지점 고르기, 대중교통 안, 전화, 탑승, 음성)은 바꾸지 않는다. 하나만 더한다: 태울 사람의 대중교통 안 거절을 기록한다(`Member.transitDeclined`, 사용자 결정 2026-11-11).

- **지도**
  - 만날 곳 카드의 지도 하나(`SpotMap`)를 새 그리기 View 둘로 바꾼다: `PeopleMap`(사람과 이동 경로)과 `StopMap`(차 세울 곳).
  - 세 지도가 함께 쓰는 그리기 도우미는 `MapBase`로 옮긴다. 이름표 자리, 핀, 길, 모서리 표시다.
  - 가는 중 `SpotMap`은 그대로 그린다.
- **순수 계산**: `Views.kt` 최상위 함수와 `Pick`에 두고, 새 `CardMapTest.kt`로 먼저 지킨다(원칙 VI). `GroupTest.kt`에 이미 `class MapTest`가 있어 이름을 피했다.
  - 범위 맞추기, 경로 구간, 사람 색 번호, 차 세울 곳 기하, 축척, 카드 글
- **대중교통**: `Offer`에 도착 시간(`arriveSec`)을 담는다. 차 화면의 한 줄을 강조 카드로 바꾼다.
- **시연**: `scripts/demo.py transit`에 두 번 기다림(6초, 10초)을 넣는다.
- **영상**: 저장소 밖 `현대자동차 공모전/영상 도구/`의 도구로 만든다.
  - 자막 그림(PIL)과 테두리·확대 창(ffmpeg drawbox·crop·overlay)을 영상에 입힌다.
  - 20장면을 다시 녹화하고, 주행 영상 페이지를 같은 주소로 갱신한다.

| 기능 | 순수 계산(테스트) | 그리기·화면 |
|---|---|---|
| 사람과 이동 경로 지도 | `fit`, `route`, `colorSlots`, `scaleBar` | `PeopleMap`(`MapBase`) |
| 차 세울 곳 지도 | `rightOf`, `stopGeom`, `scaleBar` | `StopMap`(`MapBase`) |
| 사람 줄 | `Pick.howLine` | `spotCard`(색 아이콘 + 글) |
| 대중교통 강조 카드 | `Pick.transitNote`, `Offer.arriveSec`, `Pick.step`의 거절 기록 | `situation`(만날 곳), `going` |
| 시연·영상 | (없음) | `demo.py transit`(가는 중까지), `영상 도구/` `make_vtt.py`·`burn.py`·`build_video_page.py` |

## 현재 상태 (구현 전)

- 3차 구현과 리뷰가 끝났고 `sameside` main(6752608)에 올라가 있다. 단위 테스트는 120개가 통과한다.
- 3차 녹화
  - 11/11 새벽에 20장면을 녹화했다. 19장면 통과, 1장면(`continue-cancel`) 확인 필요다.
  - "같은편 주행 영상" 아티팩트(`LC2BoVmFeMjBhrbojPgF4P`)로 올렸고, 팀원과 링크로 공유한다. SC-024의 기준은 이 결과다.
- 피드백 대상은 이 영상들이다.
  - `transit` 장면은 받아들이자마자 취소한다(40초 영상의 24초쯤).
  - 자막은 페이지 VTT 트랙(0.95rem)이다.

## Technical Context

**Language/Version**: Kotlin 2.0.21(JDK 17), Python 3(시연 `scripts/demo.py`, 영상 도구). 1~3차와 같다.

**Primary Dependencies**
- 앱: 새 의존성 없다. 그리기는 `android.graphics`이고, 아이콘은 3차 `ic_walk`·`ic_train`을 쓴다.
- 영상 도구(저장소 밖): ffmpeg(overlay·drawbox·crop만, drawtext 없음), Python PIL 12.3. 둘 다 이 컴퓨터에 이미 있다.

**Storage**: 바뀌지 않는다.

**Testing**
- JUnit: 기존 120개 + 새 `CardMapTest.kt`
- `python3 server/server.py check`
- 에뮬레이터 20장면 다시 녹화와 프레임 확인

**Target Platform**: Pleos Connect 에뮬레이터(API 34). 녹화는 1280×720, 15fps다.

**Project Type**: 차량 앱 + 중계 서버 + 웹 페이지(1~3차와 같다). 서버와 페이지는 바뀌지 않는다.

**Performance Goals**: 지도는 위치가 올 때마다 다시 그린다. 그릴 것은 길 점 93개, 사람 3명, 역 2개 안팎이라 가볍다. 다시 붙일지는 3차처럼 tag로 정한다.

**Constraints**
- 카드는 P에서만 보인다(비DO, 원칙 I).
- 선·점은 지도 바탕 대비 3:1 이상이다(R31·R32).
- 지도 타일과 망을 쓰지 않는다.
- 다른 사람의 위치는 페이지에 보내지 않는다(원칙 III, 바뀌지 않음).

**Scale/Scope**: 픽업 하나에 사람 1~3명, 시연 지역 하나, 노선 하나

## Constitution Check

| 원칙 | 판정 |
|---|---|
| I. 운전 중에는 듣기만 | 통과. 두 지도와 강조 카드는 정차 중 화면에만 있다. 주행 중에는 Pleos가 앱을 가린다(비DO). 음성 문장과 횟수는 바뀌지 않는다. 영상 강조는 영상 편집이라 앱 동작과 무관하다. |
| II. 같은 편이 아니면 내지 않는다 | 통과. 지점 고르기는 그대로다. 서 있을 곳을 들어오는 방향 오른쪽에 그리는 것은 이 원칙을 그림으로 보이는 것이다(R33). |
| III. 위치는 픽업하는 동안만 | 통과. 새로 받거나 보내는 위치가 없다. 차 화면 지도는 3차부터 이미 그 픽업 사람들의 위치를 보였다. |
| IV. 길 안내는 기본 내비에 | 통과. 내비 호출은 바뀌지 않는다. |
| V. 최소 구현 | 통과. 새 의존성이 없고, 새 파일은 `CardMapTest.kt` 하나다. `MapBase`는 지도 셋이 함께 쓰는 도우미를 옮긴 것이다(아래 Complexity Tracking). 노선 색은 노선이 하나라 색 자원 하나다(`ponytail:` 주석). 영상 도구는 저장소 밖이다. |
| VI. 판단 로직은 테스트로 | 통과. `fit`, `route`, `colorSlots`, `rightOf`, `stopGeom`, `scaleBar`, `howLine`, `transitNote`, `Offer.arriveSec`, 거절 기록(`Pick.step`)은 `CardMapTest`를 먼저 쓴다. 1~3차 테스트는 고치지 않는다. |

## Project Structure

### Documentation (this feature)

```text
specs/004-maps-transit-video/
├── spec.md
├── plan.md              # 이 문서
├── research.md          # R28~R40
├── data-model.md        # §1 MapPerson, §2 경로 구간, §3 범위, §4 색 번호, §5 차 세울 곳, §6 축척, §7 Offer, §8 카드 글, §9 색 자원, §10 영상 강조
├── contracts/
│   ├── ui.md            # 카드, 두 지도, 사람 줄, 강조 카드, 색
│   └── video.md         # 자막 띠·칩, 전화·마이크 강조, 내비 안내, 페이지
├── quickstart.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/kr/skku/sameside/
├── Pick.kt        # Offer.arriveSec(transit에서 채움), Member.transitDeclined와 step의 decline, Pick.howLine, Pick.transitNote, headingVec 공개
├── Views.kt       # MapBase(SpotMap 도우미를 옮김), SpotMap(가는 중, 그림 그대로), 새 PeopleMap·StopMap,
│                  # 순수: Seg/route, Fit/fit, colorSlots, rightOf, StopGeom/stopGeom, scaleBar/scaleLabel, MapPerson(leg, slot)
├── Screens.kt     # spotCard(두 지도 줄, 사람 줄, 차 줄), situation(만날 곳 강조 카드), going(맨 위 강조 카드),
│                  # mapPeople(leg·slot), transitCard 뷰. transitLine 지움
└── Ui.kt          # 사람 선 색(avatarN_line) 목록
app/src/main/res/values/colors.xml, values-night/colors.xml   # line1, avatar1_line~avatar5_line
app/src/test/java/kr/skku/sameside/CardMapTest.kt             # 새
scripts/demo.py                                               # transit: 6초·10초 기다림, 받아들임 조건 "타고" in t and t.endswith("와요"), 시각 로그
                                                              # snap(): SHOT_DIR 폴더가 없으면 만든다. confirm_first(): 카드 캡처
```

저장소 밖(`/Users/jiwon/Desktop/현대자동차 공모전/영상 도구/`, 커밋하지 않음)

```text
process_videos.sh      # NOSPLIT=1이면 나누지 않음(장면 id를 인자로 받음)
extract_voice.py       # 그대로: log/<id>.voice(영상 아래 음성 목록)
make_vtt.py            # 첫 "기어 P 아님"에 ※ 안내 자막
burn.py                # 새: 자막 그림(PIL) + 테두리(drawbox) + 확대 창(crop·scale·overlay), 14MB 넘으면 나누기
build_video_page.py    # page.json "burned": true면 data-cues 넣지 않음
rec_pickup4.sh         # 새: 20장면 녹화(run_rec_steps.sh 단계)
videos-pickup4/page.json, videos-pickup4/meta.json
```

처리 순서는 contracts/video.md §5다.

**Structure Decision**
- 1~3차와 같은 한 모듈이다. 그리기는 `Views.kt`, 화면 조립은 `Screens.kt`, 판단·문구는 `Pick.kt`에 둔다.
- 영상 도구는 저장소에 넣지 않고 `현대자동차 공모전/영상 도구/`에 둔다. 시연 영상을 만드는 도구이지 앱이 아니기 때문이다.
  - 3차까지는 세션 폴더(/tmp)에 있었다. 재부팅 때 지워질 수 있어 2026-11-11 사용자 결정으로 옮겼다.

## Phase 0: Research

research.md R28~R40

- R28 사람 지도 범위
- R29 길 굵기
- R30 경로·역 그리기
- R31 노선 색
- R32 사람 색
- R33 차 세울 곳 기하
- R34 축척
- R35 강조 카드
- R36 `Offer.arriveSec`
- R37 자막 입히기
- R38 전화·마이크 강조
- R39 내비 안내
- R40 페이지와 다시 녹화

NEEDS CLARIFICATION은 없다.

## Phase 1: Design

- data-model.md, contracts/ui.md, contracts/video.md, quickstart.md
- 에이전트 문맥 갱신 스크립트는 이 저장소의 `.specify/scripts/bash/`에 없다(3차와 같음). 건너뛴다.

**설계 뒤 Constitution Check**
- 다시 봐도 위 표와 같다.
- 새 화면은 모두 P 화면이고, 새 데이터 흐름은 없다(`Offer.arriveSec`는 앱 안에만 있다).

## Complexity Tracking

| 위반 | 필요한 이유 | 버린 더 단순한 안 |
|---|---|---|
| 추상 클래스 `MapBase`(구현 셋: `SpotMap`, `PeopleMap`, `StopMap`) | 세 지도가 같은 도우미를 쓴다: 이름표 자리 규칙, 글 테두리, 핀, 길 그리기, 북쪽·축척·출처, 측정 | 도우미를 세 클래스에 복사: 같은 코드 세 벌. `SpotMap` 하나에 그림 종류 분기: `onDraw` 하나가 세 그림으로 갈라져 읽기 어렵다. |
