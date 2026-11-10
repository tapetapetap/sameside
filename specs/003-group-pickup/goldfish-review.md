# Goldfish Review — 같은편 3차 — 여러 명 함께 태우기, 지도 카드, 눈에 띄는 연락 상태

- 날짜: 2026-11-11
- 리뷰어 상태: comprehension 완료 · readiness GO

## Comprehension의 다시 쓰기

같은편은 차가 오는 쪽 길가에서만 만날 곳을 골라 주는 픽업 서비스입니다. Pleos 차 앱, 중계 서버, 태울 사람이 여는 웹 페이지로 이루어집니다. 3차는 운전자가 2~3명을 한 지점에서 함께 태우게 합니다.
사람마다 걸어오거나(한도 안) 대중교통 한 구간을 타고 오게 합니다. 차와 모든 사람 가운데 가장 늦게 닿는 시간을 만나는 시간으로 보고 지점 순위를 매기며, 대중교통 안에는 5분을 더해 불리하게 둡니다. 대중교통을 시킬 때는 "근처로 데리러 가면 약 n분" 어림을 함께 보여 이득을 드러냅니다.
그 밖에 빠진 사람 제외, 전화 사이 60초, 탑승 인원(새로 앉은 좌석 수와 "탔어요" 수 중 큰 쪽), 이름을 묶은 음성 한 줄을 정합니다.
덧붙여 카드 안 지도로 어느 길가인지 보여 줍니다. 알림·전화 상태는 운전자 화면(초읽기 링, 통화 물결)과 페이지(맥박 띠)에 눈에 띄게 그리고, 시연 화면과 다섯 명 데이터를 다듬습니다.
이유는 세 가지 요청입니다. 여럿을 한 번에 태우고 싶다, 지점 이름만으로는 어느 길가인지 모르겠다, 전화가 걸렸는지 알 수 없다.

### 기존 시스템

- 구성: Pleos Connect(API 34) 위 Kotlin 차 앱, Python 중계 서버, 웹 페이지.
  - 판단은 `Pick.kt` 순수 함수와 `Pick.step` 전이가 맡고, 단위 테스트가 95개 있습니다.
  - 실행은 `PickupService`가 맡습니다.
  - 서버 자체 점검 9항목, `scripts/demo.py` 시연 장면 16개가 있습니다. 1·2차 구현과 리뷰는 끝났습니다.
- 시작: 기어가 P이고 진행 중인 픽업이 없을 때 사람 줄(가린 번호)을 누르면 한 명 픽업과 링크·QR이 생깁니다.
  - 태울 사람이 페이지에서 동의하고 위치를 보내면, 차 위치와 함께 같은 편 지점을 최대 3개 고릅니다.
  - 같은 편 지점은 차가 오는 쪽 길가이고, 정차 금지가 아니며, 유턴이 필요 없는 곳입니다. 걷기 한도는 300m(가까이만 150m)이고, 직선거리에 1.3을 곱하며, 길을 건너면 1분을 더합니다.
  - 대중교통이 5분 넘게 빠를 때만 대중교통 안을 따로 냅니다.
- 가는 중: 고른 지점을 내비에 넣습니다. 목적지는 하나이고, 경유지 방식이면 탑승 뒤 원래 목적지로 이어 갑니다.
  - 문서상 "문턱"이 되면 페이지에 "지금 나오세요"를 보냅니다. 반응(지금 나가요, 나와 있어요, 이동)이 없으면 한 번 자동 전화하거나 대체 알림을 보냅니다. 대중교통으로 오는 사람은 이동을 반응으로 보지 않습니다.
  - 150m 안내 문장, 도착 확인 질문과 페이지 도착 띠, 건너편 경고가 있습니다.
  - 주행 중 음성은 5줄까지이고, 조작은 P에서만 됩니다. 주행 중에는 Pleos가 앱을 가립니다. 통화 표시는 Pleos 상태 표시줄의 작은 아이콘뿐입니다.
- 끝: 지점 50m 안에서 서서 문이 열렸다 닫히고, 시작 때 비어 있던 좌석이 새로 차면 탑승입니다. 페이지 "탔어요"로도 탑승입니다.
  - 끝나면 위치 공유가 끝나고 서버에는 상태와 이름만 남습니다.
  - 에뮬레이터는 뒷좌석 착좌를 늘 true로 보내서, 새로 앉음을 셀 수 있는 자리는 조수석뿐입니다.

## 지적과 처리

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 1 | comprehension | Major | spec US4, FR-064·065·069, plan 원칙 I | 가는 중 화면을 언제 보는지, 주행 중 운전자가 기대는 수단이 없다 | 수정 | 설계 초안 D6과 리뷰 전 대화에서 정함(주행 중 가림 → 음성·헤드업·페이지 띠, 영상 자막). spec US4에 "언제 보이는가" 단락 |
| 2 | comprehension | Major | spec FR-056 | "전화 문턱 2분"이 무엇을 재는지 정의가 없다 | 수정 | 2차 동작(Pick.kt:542-544, 2차 FR-029~032). FR-056에 알림 → 60초 → 전화 흐름과 문턱 값, 반응 정의 |
| 3 | comprehension | Major | spec FR-056, Edge Cases | 2분 문턱·이동 무시 대상이 "알겠어요" 누른 사람인지 대중교통인 사람 모두인지 | 수정 | data-model §4(리뷰 전)의 "`plan.leg != null` 또는 `transitAccepted`". FR-056·Edge Cases를 맞추고 "알겠어요"는 FR-057에만 |
| 4 | comprehension | Major | spec Assumptions | 시연 탑승 문구가 FR-060 큰 쪽 규칙과 어긋난다 | 수정 | research R21(리뷰 전 고침). Assumption을 R21대로 고치고, 에뮬레이터에서 끝나지 않는 조합을 한계로 적음 |
| 5 | comprehension | Major | spec US5 AS3 | "따로 태웠다면"이 버린 계산의 이름이다 | 수정 | FR-070 문구로 맞춤 |
| 6 | comprehension | Major | spec US1, FR-054, quickstart | 분 반올림 규칙이 없고 기대값이 서로 다르다 | 수정 | Pick.kt:221 `minutes`(올림). FR-054에 "분은 올림", US1 "준호 걸어서 2분" |
| 7 | comprehension | Major | spec US4 AS2, FR-066, research R22 | "통화 중"이 거는 중을 포함하는지, m:ss 시작점이 두 가지로 읽힌다 | 수정 | R22(리뷰 전) "true로 바뀐 때를 통화 시작". `isInCall`은 거는 중·울리는 중을 포함한다(플랫폼 정의). FR-066·US4 AS2에 적음 |
| 8 | comprehension | Nit | spec US1 AS3, US2 AS1·2 | 인수 문장이 FR-052 경계와 다르다 | 수정 | 문서 사이 사실 불일치. 인수 문장이 FR-052 순서를 가리키게 고침(경계 예 포함) |
| 9 | comprehension | Nit | spec FR-069 | "사람별 상태 칩"이 계약에 없다 | 수정 | contracts/ui.md §2에 칩이 없음을 확인. FR-069를 연락 줄(FR-065)로 고침 |
| 10 | comprehension | Nit | data-model §2-1·§4, spec FR-060 | "경유지"가 두 뜻이고 +100이 무엇인지 없다 | 기록 | |
| 11 | comprehension | Nit | plan 현재 상태 | 장면 16개와 17장면이 함께 있다 | 수정 | 17번째는 `tour`. plan에 적고 SC-015 기준을 17장면으로 |
| 12 | comprehension | Nit | spec Assumptions | 2~3명의 근거(좌석 수)가 없다 | 기록 | |
| 13 | readiness | Major | tasks T014, data-model §4 | 사람마다 서버 기록의 404·409·끝·POST 실패·한 번에 하나 규칙이 없다 | 수정(사용자 결정) | 위임 결정 G1. data-model §4 `Gone(who)`, §9 서비스 처리, T014 |
| 14 | readiness | Major | data-model §1·§5, T013·T030 | 연락 값 사슬(`phoned` 사람 순서, `carJson` 시그니처, `calling` 다시 올리는 때)이 끊겼다 | 수정(사용자 결정) | 위임 결정 G2. `CallOrNotify(…, who = 0)`, `carJson(p, m, car, calling)`, §9 `phoned`·`calling`·다시 올리는 때, T030 |
| 15 | readiness | Major | data-model §7, T027·T028 | 연락 상태 표가 두 상태를 빠뜨렸고 `Contact` 형이 없다 | 수정(사용자 결정) | 위임 결정 G3. §7에 "나와 있어요(위치)"·"나오고 있어요" 행, `ContactKind`·`Contact`·`contact()` |
| 16 | readiness | Major | data-model §2-3·§3·§5, ui §2 | 함께 카드의 2차 비교 줄·페이지 k분과 근처 어림 계산 시점이 없다 | 수정(사용자 결정) | 위임 결정 G4. 함께 카드는 2차 줄 없음, 페이지 k분은 그 사람 plan 기준(§5 `fasterMin`), 근처 어림은 고를 때 `p.nearbySec`(§3) |
| 17 | readiness | Major | data-model §4·§5 | 확정 전 빠짐, 탄 사람의 알림 제외, 확정 전 `group[].mode`가 비었다 | 수정(사용자 결정) | 위임 결정 G5. FR-059·FR-060, §4 두 행, §5 `mode` 규칙 |
| 18 | readiness | Major | research R24, ui §3, T024·T025 | 지도 범위·가장자리·거리 글·순수 함수가 정해지지 않았다 | 수정(사용자 결정) | 위임 결정 G6. §8(짧은 변 반 140m/200m, 여백 22dp, `edge`, `distLabel`), R24, ui §3, T024 |
| 19 | readiness | Major | tasks T003, ui §5 | 색 값이 없고 `green`이 다크에 이미 있어 중복이 된다. 맥박 배율이 없다 | 수정(사용자 결정) | 위임 결정 G7. `values-night/colors.xml`의 `green` 확인. ui §6 색 표(밝은·다크), `green`은 밝은 파일에만, 맥박 1.03배·그림자 10px |
| 20 | readiness | Major | data-model §6, T023 | `group-transit` 좌표와 사람 움직임 시각이 없다 | 수정(사용자 결정) | 위임 결정 G8. §6 줄 형식 그대로의 좌표, 움직임 네 단계(`outNow`면 바로 `ready`), T023 |
| 21 | readiness | Major | spec US1·US5·FR-069 | 수용 문구가 작업·계약과 다르다 | 수정 | #5·#6·#9와 같은 고침 |
| 22 | readiness | Major | tasks T043 | `approval-gate.md`가 3차 폴더에 없다 | 수정 | T043에 "2-1 게이트 때 새로 만든다"(이 리뷰 뒤 만듦) |
| 23 | readiness | Nit | data-model §2-2 | `t.board`·`t.alight`는 `Stop`이다 | 수정 | Area.kt `val board: Stop, val alight: Stop`. `.pos`로 고침 |
| 24 | readiness | Nit | tasks T012 | `(dict, None)` 표기 | 수정 | server.py `(dict, type(None))`로 맞춤 |
| 25 | readiness | Nit | research R24 | 길 점 150개가 아니라 93개 | 수정 | Area.kt 점 수(40 + 34 + 10 + 9) |

## 집계

지적 25개 중 문서를 바꾼 것 23개
