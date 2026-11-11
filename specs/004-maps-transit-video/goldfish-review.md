# Goldfish Review — 같은편 4차 — 두 지도 카드, 대중교통이 보이는 화면, 시연 영상 자막·전화 강조

- 날짜: 2026-11-11
- 리뷰어 상태: comprehension 완료 · readiness GO

## Comprehension의 다시 쓰기

## 다시 쓰기
같은편은 Pleos 차량 픽업 앱이다. 이번 4차는 주행 영상 피드백을 반영하되 판단 로직(지점 고르기, 대중교통 안, 자동 전화, 탑승, 음성)은 그대로 두고 차 화면 표현과 시연 영상만 고친다.
- **만날 곳 카드(1·2·3순위)**: 지도 하나를 두 개로 나눈다.
  - "사람과 이동 경로": 모든 사람, 역, 지점이 들어오도록 범위를 자동으로 맞춘다. 사람마다 색이 다르고, 걷기는 점선, 탐은 노선 색 실선이다.
  - "차 세울 곳": 지점을 70m로 확대해 세울 자리 띠, 들어오는 방향 화살표, 오른쪽 인도의 서 있을 곳을 보인다.
  - 지도 아래에는 사람마다 색 아이콘이 붙은 "걸어서 n분" 또는 "○호선 n정거장(탈 역 → 내릴 역) · n분" 줄을 둔다.
- **대중교통 안**: 한 명 픽업의 안을 카드 밑 한 줄 대신 큰 강조 카드로 보인다. 자리는 제목 아래이고, 가는 중 화면에서는 맨 위다. 받아들이면 문구, 지도, 사람 줄이 대중교통으로 바뀐다.
- **시연과 영상**: transit 장면을 6초·10초 기다리게 늘린다. 20장면을 다시 녹화해 큰 자막을 영상 안에 넣는다. 앱이 건 전화의 통화 알약과 마이크를 노란 테두리와 3배 확대 창으로 강조하고, 내비가 판교에 고정된다는 안내를 넣는다.
- **이유**: 멀리 있는 사람, 세울 자리, 대중교통 안, 통화 표시, 자막이 지금 화면과 영상에서 잘 보이지 않는다.

## 기존 시스템
- **구성**: 차량 앱(Pleos Connect, AAOS), 중계 서버, 태울 사람이 링크로 여는 웹 페이지.
  - 운전자는 P에서 왼쪽 목록의 사람을 고르거나 2~3명을 함께 골라 링크를 보낸다. 목록의 사람마다 이름 해시로 정한 다섯 색 가운데 하나의 첫 글자 원이 있다.
  - 동의와 위치가 오면 앱이 차가 들어오는 방향 오른쪽 길가의 지점을 최대 3곳 고른다.
  - 주행 중에는 화면을 쓰지 않고 음성만 낸다.
- **만날 곳 카드(3차)**
  - 지도: 카드마다 가로로 긴 지도 하나가 지점 둘레 140m를 보인다. 길, 지점 핀, 들어오는 방향 화살표, 기준 랜드마크, 사람 점과 걷는 점선을 그린다. 범위 밖 사람은 가장자리 화살표와 거리(대중교통이면 노선 이름)로만 보이고, 축척은 50m 고정이다.
  - 글(한 명 픽업): "○○님 걸어서 n분 · 차 n분"과 건너기·유턴 비교 줄.
  - 글(함께 태우기): 사람마다 "이름 걸어서 n분" 또는 "이름 ○호선 n분", "차 n분", 1순위의 "근처로 데리러 가면 약 n분".
- **대중교통 안(1차)**
  - 처음 고를 때 1~2정거장을 타서 5분 넘게 빨라지면 태울 사람 화면에 제안한다.
  - 받아들이면 지점을 내릴 역 근처 하나로 바꾸고 음성 한 줄을 낸다.
  - 차 화면에는 카드들 아래(가는 중에는 맨 아래)에 "○○님께 1호선 1정거장 안을 보냈어요" 또는 "○○님이 1호선 타고 와요" 한 줄만 있다. 받아들인 뒤에도 카드 줄은 "걸어서 n분"이다.
- **가는 중 화면**: 맨 위에 2차 도착 확인 카드, 그 아래 남은 분, 막대, 차를 그리는 지도, 사람마다 연락 줄이 있다.
- **시연과 영상**
  - `scripts/demo.py` 20장면을 1280×720으로 녹화하고, 저장소 밖 도구로 처리해 "같은편 주행 영상" 아티팩트 페이지에 올린다.
  - 자막은 페이지 VTT 트랙(0.95rem)이라 작다.
  - transit 장면은 받아들이자마자 취소한다.
  - 에뮬레이터 내비는 판교에 고정돼 움직이지 않는다.
  - 통화와 마이크는 Pleos 상태 표시줄에 작게만 보인다.

## 지적과 처리

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 1 | comprehension | Major | spec.md › FR-077, Edge Cases | 태울 사람이 안을 거절한 뒤 강조 카드가 어떻게 되는지 없다. 글자대로면 "안을 보냈어요"가 끝까지 남는다. | 수정(사용자 결정) | 코드 확인: 차 앱은 거절(`transit: "decline"`)을 받아도 처리하지 않는다. `Pick.kt` 570행은 accept만 보고, `server/page.html` 208행에 거절 버튼이 있다.<br>사용자 결정: 거절하면 카드를 지운다.<br>고친 곳: spec 관계·FR-077·US2 AS6·Edge·Key Entities, data-model §7·§8(`Member.transitDeclined`, step의 decline), R35, ui.md §5, plan, T015·T016 |
| 2 | comprehension | Major | spec.md › FR-074 ↔ data-model.md §4, research.md R32 | 겹친 색을 다시 주는 규칙이 명세(팔레트 처음부터)와 설계(그 색 다음 칸부터)에서 다르다. | 수정 | 리뷰 전 plan 단계에서 정한 data-model §4·R32 규칙에 FR-074를 맞췄다("그 색 다음 칸부터 팔레트를 돌며 아직 안 쓴 첫 색"). |
| 3 | comprehension | Major | spec.md › FR-073·FR-075 ↔ data-model.md §5·§6 | 축척 후보(명세엔 20m·2km 없음)와 들어오는 화살표 거리(지점 기준 30m ↔ 띠 뒤 끝 기준 30m)가 다르다. | 수정 | R34·data-model §6(20~2000m), data-model §5(띠 뒤 끝 기준)에 맞췄다. FR-073 후보 목록과 FR-075 "띠 뒤 끝보다 30m 뒤에서 띠 뒤 끝까지"로 고쳤다. |
| 4 | comprehension | Major | spec.md › SC-024 (quickstart.md §4) | 다시 녹화한 장면의 "통과"와 "확인 필요"가 무엇인지 없다. | 수정 | 3차 영상 판정과 같은 정의를 SC-024에 적었다. 근거는 `verdicts.json`의 pass/check와 `specs/003-group-pickup/verification.md` 57행 `continue-cancel` 까닭이다. 장면별 기대 문서(1·2·3차 quickstart, 3차 verification, 이번 quickstart §2·FR-080)도 가리켰다. |
| 5 | comprehension | Major | plan.md › 저장소 밖 (quickstart 머리, R37·R38·R40) | 영상 도구가 기대는 t0, 지금 자막 규칙, 도구 위치가 문서에 없다. | 수정 | t0(run_rec_steps.sh 33·42행)와 make_vtt.py의 지금 규칙(음성 6초, 사건 5초, 1초 규칙)을 contracts/video.md §1·§5에 적었다. 도구 위치(고정 폴더 또는 저장소)는 #18 사용자 판단으로 묶었다. |
| 6 | comprehension | Nit | quickstart.md › §2 다크 화면 | `opposite`에는 1호선 경로가 없어 다크 대비를 볼 수 없다. | 수정 | 사실 오류다(같은 절 표의 `opposite` 기대). quickstart §2와 T014를 `group-transit`으로 바꿨다. |
| 7 | comprehension | Nit | spec.md › FR-080 | "'타고 와요' 문구"는 새 문구 "…타고 화서역으로 와요"에 이어진 글자로 없다. | 수정 | FR-078 문구로 FR-080 3단계를 고쳤다. plan의 demo.py 줄에 새 조건(`"타고" in t and t.endswith("와요")`)을 적었다. |
| 8 | comprehension | Nit | research.md › R32 ↔ data-model.md §4 | R32의 "민지 3"은 1부터 센 값이라 0부터 세는 번호와 헷갈린다. | 수정 | 사실 오류다(R32의 번호 정의 `floorMod`로 민지는 2). "민지 avatar3(번호 2)" 꼴로 고쳤다. |
| 9 | comprehension | Nit | spec.md › FR-073 "대중교통으로 그리는 경우" | "고른 지점까지"가 확정한 지점으로 읽힌다. | 기록 | |
| 10 | comprehension | Nit | spec.md › FR-082 (research.md R38) | "통화 중 true"는 거는 중·울리는 중을 포함한다(3차 FR-066). 그래서 "전화 연결" 이름표가 틀리고, 두 알약 창이 곧 겹친다. | 수정 | 3차 FR-066으로 확인했다. 다음을 정했다.<br>- b를 "통화가 생긴 때"로 정의한다.<br>- 알약 창은 a~b+4 하나이고 이름표는 "전화 거는 중" → "통화 중"이다.<br>- 마이크 창은 b~b+4다.<br>고친 곳: spec FR-082·US3·SC-022, R38, video.md §2, data-model §10, T023 |
| 11 | readiness | Major | tasks.md › T005·T010·T011 / research.md › R30 | 모서리 칸을 이름표보다 늦게 예약해서 이름표가 그 칸을 피하지 못한다. | 수정 | `Views.kt` 222~226행(이름표 전에 예약)으로 확인했다. `reserveCorners`를 `begin` 직후로 나눴다. 고친 곳: R30 순서, ui.md §2, T005·T010·T011 |
| 12 | readiness | Major | tasks.md › T029·T030 / quickstart.md §4 / R40 | process_videos.sh에 장면 id를 넘기지 않는다. extract_voice.py와 meta.json 단계도 빠졌다. | 수정 | process_videos.sh 6행, build_video_page.py 63·106행으로 확인했다. 명령과 단계를 video.md §5, R40, T029, quickstart §4에 넣었다. |
| 13 | readiness | Major | tasks.md › T003·T007·T015 / plan.md › VI | 새 테스트 클래스 이름 `MapTest`가 같은 패키지에 이미 있다. | 수정 | `GroupTest.kt` 272행으로 확인했다. 새 테스트를 `CardMapTest`로 바꿨다(plan, data-model, tasks, quickstart). |
| 14 | readiness | Major | tasks.md › T018·T021 / quickstart.md §2 | 가는 중 화면 맨 위의 강조 카드를 확인할 장면이 없다. | 수정(사용자 결정) | 사용자 결정: `transit` 장면을 가는 중까지 늘린다(받아들임 10초 → "이 자리로 갈게요" → 가는 중 5초 → 취소).<br>고친 곳: spec FR-080·US2 AS5·SC-020, R40, video.md §4, T020·T021, quickstart §2·§3 |
| 15 | readiness | Major | tasks.md › T023 / contracts/video.md §1 / spec Edge | 나뉜 영상, 입힌 뒤 14MB 초과, 겹치는 자막을 어떻게 할지 없다. | 수정 | process_videos.sh 15~18행, make_vtt.py 39행으로 확인했다. 다음을 정했다.<br>- 순서: `NOSPLIT=1` 처리 → burn → 14MB가 넘으면 나누기<br>- 겹치는 자막: burn.py가 다음 자막 시작에서 앞 자막을 끝낸다(spec Edge의 규칙).<br>고친 곳: video.md §1·§5, R37·R40, T023·T029 |
| 16 | readiness | Major | tasks.md › T011 / contracts/ui.md §3 | "차 세울 곳" 지도의 이름표 후보 자리와 놓는 차례가 없다. | 수정(사용자 결정) | 사용자 결정: 리뷰어 제안대로 한다.<br>- 차례: 여기 세워요 → 지점 이름 → 기준 랜드마크<br>- 후보: 핀 둘레, 서 있을 곳 바깥 → 위 → 아래, 점 둘레<br>고친 곳: ui.md §3, T011 |
| 17 | readiness | Major | tasks.md › T014·T020·T021 / quickstart.md §3 | `SHOT_DIR` 폴더가 없으면 `snap()`이 멈춘다. `transit-small`이 캡처를 덮는다. 시각을 잴 로그가 없다. | 수정 | demo.py 317~322행으로 확인했다. 다음을 고쳤다.<br>- T013: `snap()`에 `os.makedirs`<br>- T021: 폴더를 나눈다.<br>- T020: 로그 네 줄을 넣는다.<br>- quickstart §3: 잼 기준을 적는다. |
| 18 | readiness | Major | tasks.md › 경로 기준 `$S` | 영상 도구와 3차 자료가 /tmp 아래 세션 폴더에만 있어 지워질 수 있다. | 수정(사용자 결정) | 사용자 결정: `현대자동차 공모전/영상 도구/`에 복사한다(저장소에는 넣지 않음).<br>2026-11-11에 스크립트 8개와 3차 `handsfree` 시험 자료(8.6MB)를 복사했다.<br>고친 곳: tasks 머리(`$V`, 시작 전 확인), quickstart, plan, R37·R40, video.md, spec Assumptions |

## 집계

지적 18개 중 문서를 바꾼 것 17개
