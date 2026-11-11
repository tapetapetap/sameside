# Goldfish Review — 같은편 5차 — 차례로 태우기와 구간 줄

- 날짜: 2026-11-12
- 리뷰어 상태: comprehension 완료 · readiness GO(첫 실행 NO-GO → 재리뷰 GO)

## Comprehension의 다시 쓰기

## 다시 쓰기
같은편은 Pleos 차량에서 사람을 데리러 갈 때, 차가 들어오는 쪽 길가(같은 편)에서 만날 곳을 골라 기본 내비에 넣어 주는 앱이다. 사용자는 "출발 → 민지 → 하니 → 목적지"를 가로선에 놓고 구간마다 분을 쓴 그림을 원했다. 그런데 내비 경로 카드에는 앱이 그릴 수 없고, 지금의 함께 태우기(3차)는 모두를 한 곳에 모으기 때문에 "들르는 차례"라는 것이 없다.
그래서 함께 태우기(2~3명)에 "차례로 태우기"를 더한다. 사람마다 한 명 픽업 1순위 지점을 쓰고, 차에서 가까운 곳부터 욕심 방식으로 한 곳씩 들른다. 만날 곳 화면에는 차례 카드와 구간 줄을 둔다. 가는 중 화면 맨 위에는 진행이 반영된 줄을 둔다(탄 사람은 체크, 지금 지점은 테두리, 첫 구간은 남은 시간).
탑승이 확인되면 내비와 음성을 다음 지점으로 넘긴다. "지금 나오세요", 전화, 페이지 값은 태울 사람마다 자기 지점과 남은 시간 기준으로 맞춘다. 4차로 되돌릴 수 있도록 별도 브랜치(pickup-chain)에서 작업한다.
## 기존 시스템
- **한 명 픽업(1·2차)**: 태울 사람이 링크 페이지(중계 서버)에서 위치를 공유하면 앱이 같은 편 지점 후보를 순위대로 고른다(1순위 지점). 운전자가 P에서 확정하면 지점을 내비에 한 곳 넣는다. 에뮬레이터 내비는 경유지를 하나만 유지한다. 넣는 방법은 "경유지 방식/목적지 방식"이라는 이름으로만 나온다.
- **가는 중**: 남은 시간을 직선 × 우회 어림으로 센다. 남은 시간이 걸음 + 1분 문턱에 닿으면 페이지에 "지금 나오세요"를 보내고, 60초 뒤에는 전화한다. 150m 앞에서 음성 한 줄, 도착 확인도 있다. 탑승은 페이지 "탔어요" 또는 지점 근처에서 문 열고 닫음 + 빈자리 착좌로 판단한다. 탑승하면 만남으로 끝나고 원래 목적지로 이어 간다(2차).
- **함께 태우기(3차, 최대 3명)**:
  - 모두가 갈 수 있는 한 지점을 "함께 모이는 곳" 카드로 고른다.
  - 사람별 상태(Member)가 있다. 탄 수는 좌석 수와 "탔어요" 수 가운데 큰 쪽이다. 빠진 사람(취소, 만료)은 빼고 판단한다.
  - 각자 1순위 지점을 차에서 가까운 순으로 도는 어림을 "근처로 데리러 가면 약 n분" 비교 글로 보인다.
  - 공통 지점이 없으면 NOSPOT이다.
- **4차**: 대중교통 안을 받아들이면 가는 중에도 지점을 바꾼다. 지점별 값을 되돌리고 내비를 다시 넣는다. 사람 색 규칙도 4차에 있다.
- **페이지·내비·주행 중**:
  - 페이지는 `spots[chosen]`, `etaSec` 같은 값으로 그린다. 이번에 서버와 페이지는 바꾸지 않는다.
  - 내비 목적지는 지금 이름만 받고 좌표는 쓰지 않는다.
  - 같은편은 주행 최적화 앱이 아니어서 주행 중에는 Pleos가 화면을 가린다.

## 지적과 처리

위임: 사용자가 2026-11-12 "추천안으로 끝까지 진행"(게이트 내용은 문서로 남기고 구현까지)을 골랐다. 새 결정이 필요한 지적은 기본값으로 정해 `수정(사용자 결정)`으로 적는다.

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 1 | comprehension | Major | spec US1·FR-088 | 카드 "약 n분"에 목적지 구간이 드는지, 올림 시점이 문서마다 다름 | 수정 | contracts §1·data-model §1이 이미 목적지 제외로 정함. spec 예시 "약 10분(—2분— 민지 —8분— 준호)", data-model §1 "구간 초 합을 한 번 올림", `totalSec` 뺌 |
| 2 | comprehension | Major | spec FR-086·Edge | 1순위 지점이 같을 때 노드 하나 카드인지 계획 없음인지, 계획 없을 때 비교 글 규칙이 다름 | 수정 | data-model §1 "합친 뒤 2곳 미만이면 null"에 맞춤. FR-086 "합친 뒤 2곳 이상", Edge 문장, FR-088 "계획이 없으면 3차 규칙 그대로"(contracts §1과 같게) |
| 3 | comprehension | Major | spec FR-093 | 빠진 사람·빈 지점의 노드, 이름, 구간 시간, 넘어가기 규칙이 문서마다 다름 | 수정(사용자 결정) | 위임 결정: 빈 지점은 계획에서 지우고 구간을 잇는다(`dropStop`, data-model §3, FR-093), 노드 이름에서 빠진 사람을 뺀다(R48), R45를 data-model에 맞춤 |
| 4 | comprehension | Major | research R47·FR-095 | 이미 탄 사람의 페이지 모습이 없음, `outNow` 띠가 남음 | 수정(사용자 결정) | 위임 결정: 자기 지점 화면이 만남까지 남고 탄 사람에게는 `outNow` false(R47 "탄 사람의 페이지", FR-095) |
| 5 | comprehension | Major | R44·R45, US3·SC-026 | 경유지/목적지 방식 정의 없음, chain 장면이 어느 방식인지 없음 | 수정 | demo.py 32·258행(reset이 목적지 방식) 확인. spec 용어에 두 방식 정의, US3·SC-026에 조건, FR-096·R44·T015에 "집"을 먼저 넣음 |
| 6 | comprehension | Nit | contracts §3 | 출발 노드 아래 글자 | 기록 | 문서에도 반영: "아래 글자 없음" |
| 7 | comprehension | Nit | spec 용어·FR-092 | "줄"을 여러 뜻으로 씀 | 기록 | 문서에도 반영: "150m 안내 문장", "지점 목록" |
| 8 | comprehension | Nit | spec FR-092 | 탑승 기준 문장이 읽히지 않음 | 기록 | 문서에도 반영: R46 문장을 FR-092로 |
| 9 | comprehension | Nit | contracts §2 | 차례 카드와 도착 확인 카드 순서 | 기록 | 문서에도 반영: 도착 확인 카드 바로 아래 |
| 10 | comprehension | Nit | spec Edge·Assumptions, data-model §1 | 대중교통 안, 안 써본 기능 5차, `careful` 설명 없음 | 기록 | 문서에도 반영: 용어 "가까이만" |
| 11 | readiness | Blocker | data-model §1, T004 | `Stop`이 이미 있어 재선언 오류 | 수정 | Area.kt 17행 `data class Stop` 확인. 새 이름 `ChainStop`, 식은 `.spot.pos` |
| 12 | readiness | Blocker | data-model §3, R45·R46 | 착좌로 탄 사람의 `aboard` 규칙이 없어 멈추거나 음성이 두 줄 | 수정(사용자 결정) | 위임 결정: `board`에서 넘어가기 전에 지금 지점 사람을 `aboard`로, 빠짐은 `board`를 거치지 않고 바로 넘어감(음성 한 줄). T002에 두 경우. **재지적**(재리뷰): `advance(spoken, next)` 하나로 정하고 aboard 표시는 `board`에서만(data-model §3, R45, T010) |
| 13 | readiness | Blocker | T014, R42, 예시 | 유턴 규칙으로 약국이 빠져 준호 1순위는 4번 출구, route2가 반대 방향 | 수정 | 임시 테스트로 확인(R42 시연 확인): 준호 → `hwaseo4`(155°). R49 route2를 `deok_s`로, 예시 "화서역 4번 출구 앞", T002가 id 확인 |
| 14 | readiness | Major | T015, quickstart §2 | reset 뒤 목적지가 "시연대기"라 목적지 구간·이어 가기 불가 | 수정 | demo.py 32·258·484행 확인. T015 시작에 `cont()`처럼 "집", R44 |
| 15 | readiness | Major | T015 | 카드가 첫 화면 밖, D에서 캡처, SCENES 목록 없음 | 수정 | ScrollView, demo.py 299·377·819~823행 확인. 밀어 올린 뒤 캡처·누름, P → `front()` → 캡처 → D, `fn` 사전과 docstring |
| 16 | readiness | Major | data-model §3 advance, R45 | 넘어갈 때 `etaSec`·`startM` 다시 세기 없음 | 수정 | Confirm(Pick.kt 613·617행)과 같은 식으로 다시 정함(R45, data-model §3), T002 확인 |
| 17 | readiness | Major | contracts §3, T006 | `ChainStrip` `tag` 없음 | 수정 | 4차 지도 View의 tag 방식(Views.kt)을 따름: R48 tag 형식, T006 |
| 18 | readiness | Major | contracts §3, R48, data-model §5 | 최소 간격, 끝 여백, 출발 글자, 지난 구간 x, 빈 지점, 목적지 출처 | 수정(사용자 결정) | 위임 결정: `minGap` = 가장 넓은 이름 + 16dp, 끝 여백 = 이름 반폭 + 8dp 이상, 출발 글자 없음, x는 계획 시간, 빈 지점은 지움, 목적지는 `chain.destSec`(R48, contracts §3) |
| 19 | readiness | Major | contracts §2, T010 | 가는 중 요소마다 대상, 카드 자리 | 수정(사용자 결정) | 위임 결정: contracts §2 요소별 대상(지금 지점 사람만: 남은 시간 옆 이름, 지도), 도착 확인 카드 아래 |
| 20 | readiness | Major | contracts §5 | MET 비교 줄을 빼는 task 없음 | 수정 | Pick.kt 618행, Screens.kt 103행 확인. ConfirmChain에 `nearbySec = null`(data-model §3, T005) |
| 21 | readiness | Major | T012·T013, data-model §4 | 사람별 판단 기준 지점과 검증 없음 | 수정(사용자 결정) | 위임 결정: 기준은 `p.spots[m.stop]`, `memberEta` null이면 건너뜀(data-model §4). 알림 테스트(T002), 서버 `etaSec` 확인(T015, SC-028) |
| 22 | readiness | Major | spec US1 ↔ contracts §1 | 카드 합계 값이 다름 | 수정 | #1과 같음 |
| 23 | readiness | Major | plan 구조, T006·T012·T013 | `chainXs` 위치, `memberEta` task 없음 | 수정 | `chainXs`는 Views.kt, `memberEta`는 T011, T013은 T011 뒤 |
| 24 | readiness | Nit | data-model §1 | `dist` 인자 수, `ROAD_MPS` 위치 | 수정 | 사실 오류(Pick.kt `dist(o, a, b)`): 3인자로, `ROAD_MPS`는 `Pick` 안(T001) |
| 25 | readiness | Nit | data-model §1 | `fun Pick.chain` 확장 함수 표기 | 수정 | private 상수 접근 불가: `object Pick` 멤버로 표기 |
| 26 | readiness | Nit | data-model §1 | `totalSec` 쓰는 곳 없음 | 기록 | 문서에서도 뺌 |
| 27 | readiness | Nit | tasks 머리 | R42~R48 ↔ R42~R47 | 수정 | 둘 다 R42~R49 |
| 28 | readiness(재리뷰) | Major | data-model §3 Dest | 한 명 픽업에서도 다시 고르기가 돌아 1~4차 회귀 | 수정 | Pick.kt 933~945행 `chooseGroup`은 함께 태우기 전용. "`p.group`이고 SPOTS이고 `chained`가 아닐 때만", 한 명 픽업은 값만 저장. T002 Dest 테스트 |
| 29 | readiness(재리뷰) | Major | T002 알림 테스트 | chosen 0에서 준호 알림은 문턱(약 164초)상 불가 | 수정 | Pick.kt 734~736행 문턱, R42 `legs[1]` 약 475초. "chosen 0에서는 꺼짐, 넘어간 뒤 켜짐"으로 |
| 30 | readiness(재리뷰) | Major | data-model §1 합친 지점 | 둘째 사람 페이지에 첫 사람의 서 있을 곳·건너기 값 | 수정(사용자 결정) | 위임 결정: 확정 때 지점마다 사람별 걷기 plan(3차 `Pick.plan`)으로 `plans`를 채움(data-model §3 ConfirmChain). T002 확인 |
| 31 | readiness(재리뷰) | Major | data-model §4, T011 | 다음 지점 사람 판정에 지금 지점 `crossOver`가 섞임, 건너기 갱신 미정 | 수정(사용자 결정) | 위임 결정: `crossOver`는 `m.stop == chosen`일 때만, 건너기 갱신은 3차 plans 경로(#30으로 plans가 생김) |
| 32 | readiness(재리뷰) | Major | spec Edge, R47 | 계획만 있는 SPOTS에서 페이지 안내 없음 | 수정(사용자 결정) | 위임 결정: 한계로 적음(R47 한계, spec Edge). 확정하면 자기 지점이 보인다 |
| 33 | readiness(재리뷰) | Major | plan 원칙 I, data-model §3 | 음성 상한 5줄이 픽업 전체에 한 번, 시연이 이미 5줄 | 수정(사용자 결정) | 위임 결정: 넘어갈 때 셈을 0으로(지점마다 5줄, R45·data-model §3·spec Edge), T015 민지는 `ack` |
| 34 | readiness(재리뷰) | Major | FR-093 ↔ data-model §3 | 마지막 지점을 지울 때 목적지 구간 값이 다름 | 수정 | FR-093 기준으로 맞춤: `destSec = legs[k] + destSec − STOP_SEC`(data-model §3, FR-093에 식) |
| 35 | readiness(재리뷰) | Major | T002, SC-029 | Dest, 빈 spots, 지금이 아닌 지점 빠짐, 처음 값, 대상 제한, Relay 값 테스트 없음 | 수정 | 헌법 VI·SC-029: T002에 항목마다 기대값. Relay(org.json)는 시연 로그로 확인(T015 `outNow`) |
| 36 | readiness(재리뷰) | Nit | plan Constraints | "경유지를 하나만 유지"는 2차와 다름 | 수정 | 사실 오류(002 spec 123행, PickupService.kt 480·541행): "경유지 두 칸(진입, 지점)을 한 지점에" |

## 재리뷰

- 대상: readiness. 첫 VERDICT NO-GO(Blocker #11·#13 수정, #12 수정(사용자 결정)) → 재리뷰 VERDICT GO
- 새 지적: #28~#36(Blocker 없음). `재지적` 행: #12

## 집계

지적 36개 중 문서를 바꾼 것 30개
