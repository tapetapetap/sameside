# Goldfish Review — 같은편 6차 — 차 화면 개선과 주행 중 한 줄 카드

- 날짜: 2026-11-13
- 리뷰어 상태: comprehension 완료 · readiness GO(첫 판정 NO-GO, 재리뷰 GO)

## Comprehension의 다시 쓰기

같은편은 Pleos(AAOS) 차에서 쓰는 픽업 앱이다. 태울 사람에게 링크를 보내 위치를 받고, 만날 곳을 골라 내비에 넣는다. 가까워지면 알림이나 전화로 그 사람을 나오게 한다.
지금은 주행 최적화 선언이 없어서 기어가 P가 아니면 Pleos가 앱을 가린다. 그래서 운전자는 데리러 가는 동안 아무것도 볼 수 없다. 6차는 이 선언을 넣는다. 주행 중(P가 아니거나 기어를 아직 모를 때)에는 사람 칸을 숨기고, 움직임과 버튼이 없는 한 줄 카드 하나만 보인다. 가는 중이면 "남은 분 + 다음 사람 이름 · 초 숫자 없는 짧은 연락 상태"이고, 그 밖의 상태는 고정 문구다.
impeccable 점검의 P1·P2도 함께 고친다. 정차 화면을 차 기준(화면 글자 24sp, 그림 글자 20sp, 누르는 칸 76dp, 대비 4.5:1)에 맞춘다. 취소는 4초 안에 두 번 눌러야 된다. 뒤로 키는 "여러 명 함께"를 끈다. 낮·밤이 바뀌어도 고르던 사람이 남는다. 만날 곳 화면을 정리하고 문구 세 곳을 고친다.
픽업 판단, 음성, 서버, 저장 형식은 그대로다.

### 기존 시스템

- Pleos는 주행 최적화를 선언하지 않은 앱을 기어가 P가 아닐 때 "주행 중에는 사용할 수 없습니다"로 가린다. 같은편은 선언이 없어서 데리러 가는 내내 가려진다. 기어를 모르면 주행 중으로 보고(1차부터), 조작은 정차 중에만 받는다.
- 정차 화면은 두 칸이다.
  - 사람 칸: 사람 줄, "여러 명 함께", "+ 사람 추가", 내 차 줄.
  - 상황 칸: 픽업 상태별 화면(LINK·SPOTS·NOSPOT·GOING·MET·CANCELED·STOPPED·EXPIRED).
  - 가는 중 화면에는 큰 남은 분, 진행 막대, 300dp 지도, 사람별 연락 줄, "내비에 넣었어요. 출발하세요"가 있다. 연락 줄에는 초 숫자("m:ss", "n초 전", "n초 뒤 전화")와 물결·링 움직임이 있다.
- `render`는 위치가 올 때마다(약 1초) 불린다. 글자와 켜짐 상태가 같으면 `replace`가 뷰를 그대로 둔다.
- 크기는 폰 기준이다. 보조 글 22sp, 칩 20sp, 지도 글자 12~19sp, 버튼 높이 약 64dp, "여러 명 함께" 135×45dp다. 초록(#00A86B)·주황(#E08A12) 글자의 대비는 약 2.7:1이다. 지도 이름표가 자리를 못 찾으면 덜 중요한 것(역·노선·지나는 곳)부터 그리지 않는다.
- 만날 곳 화면은 모든 후보 카드에 지도(1순위 230dp, 나머지 150dp)와 이유 줄이 있다. "차례로 태우기" 카드는 맨 아래에 있다. 문구는 "차례로 태우는 중 · k/n", "알림 전"이다.
- "픽업 취소"는 한 번 누르면 바로 취소된다. 뒤로 키는 앱을 나간다. "여러 명 함께" 고르기는 액티비티에만 있어서 낮·밤이 바뀌면 사라진다. 픽업 상태는 서비스에 있어서 남는다.

## 지적과 처리

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 1 | comprehension | Major | spec 무엇이 바뀌나·FR-111 | "링크"와 "링크 화면"이 정의 없이 두 뜻 | 수정 | 용어에 링크·링크 페이지·링크 상태 정의, FR-111을 "태울 사람이 보는 링크 페이지"로 |
| 2 | comprehension | Major | spec US5-2 ↔ contracts §5 | 두 번째 카드에 비교 줄이 남는지 다름, 비교 줄 정의 없음 | 수정 | Screens.kt `spotCard`의 `after`(차 줄·비교 줄)를 남기는 contracts가 리뷰 전 뜻. spec US5-2에 "비교 줄(한 명 픽업일 때)", 용어에 정의 |
| 3 | comprehension | Major | contracts §1 LINK 한 명 | 문구 없이 `linkLine(p)`만 | 수정 | Screens.kt `linkLine`의 네 문장과 조건을 표에 |
| 4 | comprehension | Major | spec US2-3·FR-102, contracts §2 | 입력 창이 차 기준 범위인지 두 가지로 읽힘 | 수정 | 리뷰 전 결정(범위 = 점검 P1·P2 목록, 입력 창은 목록에 없음)대로 Assumptions에 범위 밖, US2-3에 "입력 창 제외" |
| 5 | comprehension | Major | spec 용어 | 용어를 다른 앱 spec에 맡김, "안 써본 기능" 설명 없음 | 수정 | 화면 글자·그림 안 글자·누르는 칸·큰 글자 예외를 이 spec에 정의, 안 써본 기능과 DO 확인을 한 줄로 |
| 6 | comprehension | Major | spec Edge Cases(취소 확인) ↔ contracts §5 | 확인 상태를 지우는 단계가 없음 | 수정 | spec Edge의 리뷰 전 뜻("D가 되면 확인도 끝난다")대로 contracts §5, T009·T016, quickstart §4에 지우기 |
| 7 | comprehension | Nit | spec 용어 | 23dp와 24dp, 글자 단위 dp | 수정 | 사실 불일치. "23dp(이 명세는 24dp)", 글자는 sp |
| 8 | comprehension | Nit | spec US1-1·FR-098 | 사람 칸·상황 칸 정의 없음, "왼쪽"은 세로 배치와 다름 | 수정 | layout-port/activity_main.xml 확인. 용어에 두 칸을 정의, US1-1의 "왼쪽" 뺌 |
| 9 | comprehension | Nit | spec US1-5·FR-099·FR-100, plan Summary | contracts §4 참조(표는 §1), 함수 모양 다름, `creating` 줄이 spec에 없음 | 수정 | 사실 오류. 참조를 §1로, plan을 `glance(p, creating, contactOf)`로, US1-6에 "링크를 만들고 있어요" |
| 10 | comprehension | Nit | spec 머리말 | 앱 설명과 상태 흐름이 없음 | 수정 | 용어 첫 줄에 앱 설명과 상태 흐름 |
| 11 | comprehension | Nit | research R6 | 이름이 겹칠 수 있는지 모순 | 수정 | #17과 같은 변경 |
| 12 | readiness | Blocker | research R4, spec US2-4·Edge Cases | "지금 규칙"이 코드와 반대: `place`는 빈 자리가 없으면 겹쳐 그림 | 수정 | 리뷰 전 결정(설계 S2 "겹치는 이름표는 그리지 않는다")을 코드 사실(Views.kt `firstFree`의 `coerceAtLeast(0)`, GroupTest "빈 자리가 없으면 첫 자리")에 맞게 구현 방법까지 적음: `freeIndex`(−1), `place(…, optional)`, 역·노선 글·지나는 곳만 `optional`, 테스트 1줄 |
| 13 | readiness | Major | contracts §3, research R7 | "가까이만" 칩(4.34·3.94)과 사람과 이동 경로 지도의 사람 이름표(3.37 등)가 FR-105 미달 | 수정 | 리뷰 전 기준(FR-105)대로 칩 `blue_text`, 이름표 글자 `text1`(점·점선은 사람 색), R7 표 두 줄 |
| 14 | readiness | Major | tasks T007 | 파일 수준 창 변수가 다시 만든 액티비티에서 남아 D에서 앱이 죽음 | 수정 | `setOnDismissListener { dialog = null }`, `onDestroy`에서 `closeDialog()`, quickstart §2-7 |
| 15 | readiness | Major | tasks T016·T009 | `cancelUntil`을 비우는 곳이 없음 | 수정, 재지적 | #6과 같은 변경. 재리뷰에서 선언 차례(T009가 먼저 씀)와 T016의 `p == null || p.status.ended` 줄이 빠진 것을 재지적 → T009에 선언, T016에 줄 추가 |
| 16 | readiness | Major | contracts §1 SPOTS | 후보 수 뜻, 후보 0곳 + 차례 계획이면 "0곳" | 수정 | Pick.kt 1066행, ChainTest `planWithoutAMeetingPlaceIsStillSpots` 확인. `p.spots.size`, 0곳 + 차례 줄과 T003 테스트 |
| 17 | readiness | Major | research R6, tasks T018 | 같은 이름 저장을 막지 않아 이름으로 되살리면 중복 | 수정 | People.kt `name()`, MainActivity.kt 71행 확인. `People.encode`로 저장, `People.load`에 있는 사람만 `distinct()` |
| 18 | readiness | Major | quickstart §3-3 | grep이 괄호 있는 호출을 놓침, `body` 빠짐 | 수정 | 사실 오류. 후보를 모두 뽑고 글자 크기 인자만 보는 절차 |
| 19 | readiness | Major | tasks T003 | 테스트 목록이 SC-032·contracts §1과 다름 | 수정 | CALLED, STOPPED(`lastLeft` 있음·없음), 자리 없음 여러 명, 한 명 링크 네 줄, 0곳 + 차례 추가 |
| 20 | readiness | Major | tasks T024, quickstart §6 | 캡처 폴더가 상대 경로 | 수정 | 절대 경로 |
| 21 | readiness | Nit | plan Summary | `glance` 모양이 다름 | 수정 | #9와 같은 변경 |
| 22 | readiness | Nit | spec US1-5·FR-099·FR-100 | §4 참조 | 수정 | #9와 같은 변경 |
| 23 | readiness | Nit | spec 용어 | 23dp, 단위 | 수정 | #7과 같은 변경 |
| 24 | readiness | Nit | tasks T010 | 매니페스트의 옛 주석("DO를 선언하지 않는다")이 남음 | 수정 | AndroidManifest.xml 32행 확인. T010에서 주석을 바꿈 |
| 25 | readiness | Nit | README.md | 옛 문구·주행 중 설명이 남음 | 수정 | README 35·53·63·124·165행 확인. T022a 추가 |
| 26 | readiness(재) | Major | contracts §2·§3, tasks T005·T011 | 사람 줄 첫 글자 원 약 20sp, 흰 글자 대비 2.2~3.7 | 수정 | Ui.kt 83행 확인. 장식 글자(접근성 읽기에서 빠짐)라 대비 예외를 용어에 적고, 원 58dp로 글자 24sp(T005) |
| 27 | readiness(재) | Major | quickstart §6, tasks T025 | 회귀 기준 "5차 로그"가 21장면 분량이 아님 | 수정 | `/Users/jiwon/Desktop/현대자동차 공모전/영상 도구/videos-pickup4/log/<장면>.log` 21개(`chain.log` 포함)가 있음을 확인. 기준으로 적음 |
| 28 | readiness(재) | Major | tasks T019 | 두 번째 누름이 4초를 넘길 수 있음 | 수정 | demo.py `tap()` 확인. 좌표를 돌려받아 곧바로 두 번째 `input tap` |
| 29 | readiness(재) | Major | quickstart §2-4 | `waiting`은 알림 뒤 바로 P라 D에서 알림·전화 상태가 안 보임 | 수정 | demo.py `waiting`·`handsfree` 확인. 알림·전화는 `handsfree` D 구간에서, `waiting`은 "나와 있어요"만 |
| 30 | readiness(재) | Major | tasks T011 | 토글 높이만 키우면 글자가 위로 붙음 | 수정 | Screens.kt 59~61행 확인. 세로 여백 0, `gravity = CENTER` |
| 31 | readiness(재) | Nit | tasks Phase 2 Checkpoint | "T003만 실패"는 실제(컴파일 실패)와 다름 | 수정 | 사실 오류. 컴파일 실패로 고침 |
| 32 | readiness(재) | Nit | tasks T002 | 고친 원칙 I의 제목이 그대로 | 수정 | 제목을 "운전 중에는 듣고 한 줄만 본다"로 |
| 33 | readiness(재) | Nit | tasks T013·T014 | 둘 다 [P]인데 같은 파일 | 수정 | 둘끼리는 차례로 |
| 34 | readiness(재) | Nit | plan Scale/Scope | 앱 코드 파일 수 6 → 5 | 수정 | 사실 오류 |

## 재리뷰

- 대상: readiness. 첫 판정 NO-GO → 재리뷰 GO.
- 새 지적: #26~#34. 재지적: #15.

## 집계

지적 34개 중 문서를 바꾼 것 34개
