# Goldfish Review — 같은편 2차 — 알리고 나서 전화, 도착 알림, 원래 길로 이어 가기

- 날짜: 2026-11-10
- 리뷰어 상태: comprehension 완료 · readiness GO(재리뷰, 첫 판정 NO-GO)

## Comprehension의 다시 쓰기

1차 '같은편'에 다섯 가지를 더한다. 같은편은 Pleos 차량 앱과 태울 사람용 링크 페이지로, 차가 실제로 들어가는 쪽 길가의 랜드마크 앞에서 만나게 한다.
① 문턱(남은 시간 = 걷는 시간 + 1분, 최소 2분)에서 곧장 전화하지 않는다. 먼저 페이지에 "지금 나오세요" 띠와 "지금 나가요" 버튼을 띄운다. 60초 안에 반응이 없을 때만 한 번 건다. 반응은 두 버튼 중 하나를 누르거나 20m 넘게 움직인 것이다. 전화가 양쪽에 부담이어서다.
② 내비에 원래 목적지가 있으면 지우지 않고, 진입 경유지와 지점을 경유지로 끼운다. 태우면 그 경유지만 지워 원래 목적지 안내가 이어진다. 운전석 문은 탑승 판정에서 뺀다.
③ 지점 50m 안에서 오른쪽 깜빡이를 켜고 3초 서면 음성으로 묻는다. P에서 "보내기"를 누르면 페이지에 도착 띠가 뜬다. 전화 없이 도착을 알리는 수단이다. 앱이 비상등을 읽을 수 없어 깜빡이로 대신한다.
④ 건너야 하는 지점에는 건너기와 유턴 시간을 나란히 보여 준다. 같은 편을 고른 이유를 보이려는 것이고, 제안 자체는 바꾸지 않는다. ⑤ 150m 음성과 페이지 문구에 "X 지나서 Y 앞" 기준 랜드마크를 붙인다.

**기존 시스템**

1차는 구현이 끝났고 커밋은 아직 없다. Kotlin 차량 앱, Python 중계 서버, 브라우저 페이지로 이루어진다.
- **시작과 지점 고르기**: 운전자가 P에서 저장한 사람을 고르면 링크가 생긴다. 태울 사람이 동의하고 위치를 보내면 `Pick.choose`가 지점 1~3개를 낸다. 조건은 걸어서 300m 안의 랜드마크 앞 길가이고, 차가 마지막으로 들어갈 때 오른쪽이며, 정차 금지 거리 밖인 곳이다.
- **지점 확인**: 운전자가 P에서 지점을 확인하면 `cancelRoute → requestRoute(지점) → addWaypoint(진입 경유지, 지점 100m 앞)` 순서로 내비에 넣는다. 지점이 목적지가 되므로 원래 목적지는 사라진다.
- **주행 중**: 음성만 쓰고, 5줄까지다.
  - 남은 시간이 max(걷는 시간 + 60초, 120초)에 닿으면 "○○님께 전화 걸게요"라고 말하고 곧장 한 번 자동 전화한다.
  - 걸 수 없으면 페이지에 "지금 나오세요" 띠를 띄우고 운전자에게 "○○님 폰으로 알렸어요"라고 말한다.
  - 태울 사람이 이미 나와 있으면 "○○님이 나와 있어요"라고만 말한다.
  - 지점 길 150m 안에서는 "오른쪽 △△ 앞이에요"라고 한 번 말한다. 반대 방향으로 들어오면 대신 건너오라는 문장을 말하고 페이지에 `crossOver` 띠를 띄운다.
- **탑승**: 세 조건이 모두 맞으면 탑승으로 본다. 차가 서 있고, 지점 50m 안에서 아무 문이나 열렸다 닫히고, 빈 좌석에 착좌가 켜지는 것이다. 태울 사람이 "탔어요"를 눌러도 같다. 탑승하면 "○○님이 탔어요"라고 말하고 `ClearNavi`(`cancelRoute`)를 부른 뒤 공유를 끝낸다.
- **구조**: 판단은 순수 상태기계 `Pick.step`(link → spots/nospot → going → met 등)이 한다. 실행(Speak, CallOrNotify, SetNavi, ClearNavi, Push)은 `PickupService`가 한다.
- **에뮬레이터 내비**: 안내 중 `cancelRoute`를 무시해서 지난 지점이 목적지로 남는다. `isRouting`은 늘 false다. 디버그 전용 `DemoReceiver`로 목적지를 넣고 상태를 읽는다.

## 지적과 처리

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 1 | comprehension | Major | spec › 1차와의 관계 | 바뀌는 1차 요구 목록에 FR-014·FR-025가 없고, FR-027의 무엇이 바뀌는지 모호하다 | 수정 | 1차 FR-014·FR-025와 2차 FR-033이 충돌함을 문서로 확인. 목록을 FR별로 다시 적고 FR-027은 탑승·취소 때 내비 처리만 바뀐다고 적음 |
| 2 | comprehension | Major | spec › Edge Cases | 경유지 방식을 포기하는 조건이 문서마다 다르다(두 개 vs 하나라도) | 수정 | research R15·data-model §7의 "경유지가 비어 있을 때만"이 리뷰 전 결정. Edge Case를 "하나라도 있으면"으로, 내비 2칸 근거(1차 research R1)를 함께 적음 |
| 3 | comprehension | Major | spec › US3·FR-039 | crossOver로 건너편에 섰을 때 도착 띠 문구가 crossOver 띠와 반대 | 수정(사용자 결정) | 위임 결정 D1(approval-gate.md). crossOver면 "차가 길 건너편에 도착했어요. 가까운 횡단보도로 건너 주세요"(spec Edge Case, contracts/ui.md §3) |
| 4 | comprehension | Major | spec › US4·SC-011 | 페이지에 두 시간이 보인다는 서술과 k 문장 서술이 섞임 | 수정 | 설계 문서 §2 F2와 FR-041·contracts/ui.md가 리뷰 전에 k 문장으로 정함. US4·Independent Test·SC-011을 k 문장(k = m − n)으로 맞춤 |
| 5 | comprehension | Major | spec › SC-012 | 화면 조작 0번과 도착 확인(P에서 보내기)이 부딪힘 | 수정(사용자 결정) | 위임 결정 D2. SC-012는 P가 아닐 때의 조작만 세고 측정 장면을 `opposite`·`handsfree`로 한정 |
| 6 | comprehension | Major | quickstart › 준비 | reset을 그대로 쓰면 navOwn 방어가 깨짐 | 수정(사용자 결정) | 위임 결정 D3(#15와 같은 해결). quickstart에 "바뀐 reset" 절과 "장면은 늘 reset 뒤" |
| 7 | comprehension | Major | data-model §3 | `along`이 정의되지 않았고 `pr'` 기호를 두 뜻으로 씀 | 수정 | `Pick.kt` 82행 `Proj` 주석에서 정의 확인. §3 첫머리에 정의와 forward 방향을 적고, 고른 기준은 `prB`로 씀 |
| 8 | comprehension | Major | spec › SC-008 | 전화 시각 구간의 기준점이 모호 | 수정 | 설계 문서 §2 F5 "알림 뒤 60초에 반응을 본다"가 리뷰 전 결정. "알림 뒤 60~62초(실제 문턱 뒤 60~92초)"로 적음 |
| 9 | comprehension | Nit | spec › FR-038·US3 | "켠 채 3초"는 순서가 정해진 것처럼 읽힘(data-model은 순서 무관) | 수정 | data-model §6·tasks T017과의 불일치(사실 오류). "3초 넘게 서 있고 오른쪽 깜빡이가 켜져 있으면(순서 무관)" |
| 10 | comprehension | Nit | spec › FR-030·US1 | 20m를 60초 안 언제든 넘으면 되는 것처럼 읽힘(data-model은 마감 때만 봄) | 수정 | data-model §6과의 불일치. "60초가 됐을 때 알림 때 자리에서 20m 넘게 떨어져 있음" |
| 11 | comprehension | Nit | spec › FR-040 | "지점을 지나 가장 가까운 교차로" vs 방향 무관 직선 근사 | 수정 | data-model §2·research R17과의 불일치. Assumptions에 근사임을 적음 |
| 12 | comprehension | Nit | spec › FR-033·US2 | "진입점" 정의 없음 | 수정 | 1차 용어 "진입 경유지"(지점 100m 앞)와 다른 이름. 모두 고침 |
| 13 | comprehension | Nit | spec › FR-036 | 운전석 문을 빼는 이유가 없음 | 기록 | — |
| 14 | readiness | Blocker | tasks › T002 | 올리브영을 넣으면 handsfree 계열 1순위가 바뀌는데 후보에서 빼는 방법·좌표가 없음 | 수정 | T002의 리뷰 전 결정("바뀌면 지점 후보에서 빼는 쪽으로")을 구체화. data-model §1-1 `Landmark.spot`(올리브영 false), 확정 좌표 37.2982708, 126.9715664, 장면 1순위 테스트를 T002에 넣음 |
| 15 | readiness | Major | tasks › T015·T030 | navOwn 맞추기가 장면 순서에 따라 깨짐 | 수정(사용자 결정) | 위임 결정 D3. reset이 앱을 멈추기 전에 "시연 대기" 목적지를 넣고 같은 이름을 navOwn으로 씀, 장면마다 reset(research R15, quickstart, T015·T030) |
| 16 | readiness | Major | data-model §7·T013 | 내비 답을 기다리는 동안 상태가 바뀔 때의 처리 없음 | 수정(사용자 결정) | 위임 결정 D4. 토큰, 대기 중 ClearNavi는 대기 버림, REMOVE 콜백 또는 3초 뒤 다시 넣기, 두 답이 모이면 바로 정함(data-model §7 공통 규칙, T013) |
| 17 | readiness | Major | quickstart › 새 장면 | FR-035·FR-037을 확인하는 장면이 없음 | 수정(사용자 결정) | 위임 결정 D5. `continue-cancel`, `continue-full` 장면과 `DemoReceiver waypoint` 명령(quickstart, T015·T016·T030) |
| 18 | readiness | Major | tasks › T009·T010·T023 | 측정 방법·합격 기준이 SC-008·SC-010과 맞지 않음 | 수정 | spec SC-008·SC-010의 수치(리뷰 전)에 측정 절차를 맞춤. 알림 시각은 서버 outNow, fallback은 대체 음성 로그, arrive는 정차→질문 8초 이하(quickstart, T009·T022·T023). **재지적**: 전화 로그가 2초 늦고 폴링 시각과 기기 시각이 섞임 → 세 시각 모두 기기 로그("알림 보냄" 로그 추가, 판단 순간의 말하기 로그)로 고침 |
| 19 | readiness | Major | tasks › T005 | uturnSec 반올림이면 경계에서 두 화면의 분이 다름 | 수정 | 수식으로 확인(180.3초 → 차 4분, 페이지 3분). 올림(ceil) 정수 초로 올림(data-model §8, T005) |
| 20 | readiness | Major | data-model §6 150m | 이미 지난 기준 랜드마크를 "지나서"로 말할 수 있음 | 수정(사용자 결정) | 위임 결정 D6. 차가 기준을 지나지 않았을 때만 지나서 문장(spec FR-044·US5, data-model §6, T027·T028) |
| 21 | readiness | Major | quickstart › 바뀐 기대값 | standLine 기대값이 없고 규칙대로면 두 장면 모두 "지나"가 빠짐 | 수정(사용자 결정) | 위임 결정 D7. 규칙 유지, opposite·handsfree는 "세븐일레븐 앞 인도에 서 계세요", "지나" 문장은 민지가 올리브영 남쪽에 있는 `arrive` 장면에서 확인(spec US5, quickstart, T022·T023) |
| 22 | readiness | Nit | tasks › T032 | README의 F2·F3과 2차 F2·F3이 다른 기능 | 수정 | README 18–19행에서 확인. T032에 붙일 이름을 적음. **재지적**: F5도 겹치고 1차 F5 설명이 2차와 어긋남 → "2차 ·" 머리와 1차 F5 설명 고침 |
| 23 | readiness | Nit | spec › Edge Cases | "두 개"와 "비어 있을 때만"이 다름 | 수정 | #2와 같은 변경 |
| 24 | readiness | Nit | tasks › T006 | 시각을 보지 않는 두 테스트에 "+60초로 고칠" 기대값이 없음 | 수정 | `PickTest.kt`의 두 테스트가 목록만 비교함을 확인. "그대로 통과하는지 본다"로 바꿈 |
| 25 | readiness | Nit | tasks › T026 | [P]인데 T025의 `compareLine`을 씀 | 수정 | [P]를 빼고 T025를 쓴다고 적음 |
| 26 | readiness | Major | research R16·T019 | 깜빡이 구독 권한이 정해지지 않음 | 수정 | DRIVE DNA 매니페스트에서 방향지시등을 `CAR_LIGHTS`로 받음을 확인. R16·T019에 권한과 "깜빡이 = 값" 로그 |
| 27 | readiness | Major | tasks › T015 | "시연 대기"의 공백이 adb에서 잘림 | 수정 | `adb shell`이 인자를 공백으로 이어 붙임(demo.py `adb()`). "시연대기"로 바꾸고 reset 끝에 state로 확인(T015, research R15, quickstart) |
| 28 | readiness | Major | tasks › T003 | `Event.Remote.ack`의 위치·기본값이 없고 고칠 곳이 모자람 | 수정 | `PickTest.kt`의 직접 생성 두 곳 확인. "마지막 인자, 기본값 false"로 정해 두 곳은 그대로 둠(T003) |
| 29 | readiness | Major | tasks › T013·plan | navOwn 읽기·쓰기 함수가 없고 People.kt가 목록에 없음 | 수정 | `People.kt` 41행 `prefs`가 private임을 확인. `loadNavOwn`·`saveNavOwn`을 T013과 plan 목록에 더함 |
| 30 | readiness | Major | tasks › T021·T023 | 반대 방향 도착 띠 문구(D1)가 작업에 없고 확인 장면이 없음 | 수정 | spec Edge·contracts/ui.md의 문구(D1)를 T021에 조건과 함께 적고, T030에 서버 API로 페이지 문구 확인을 더함 |
| 31 | readiness | Nit | spec › FR-037 | 화면 문구가 계약·T014와 다름 | 수정 | contracts/ui.md §1의 "원래 목적지(○○) 대신…"으로 맞춤 |

## 재리뷰

- 대상: readiness. 첫 VERDICT NO-GO → 재리뷰 VERDICT **GO**
- 새 지적: #26~#31(Major 5, Nit 1, 모두 수정)
- `재지적`: #18(측정 방법), #22(README 번호)

## 집계

지적 31개 중 문서를 바꾼 것 30개
