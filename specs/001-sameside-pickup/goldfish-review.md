# Goldfish Review — 같은편 — 차가 오는 쪽 길가, 눈에 띄는 곳 앞에서 만나게 한다

- 날짜: 2026-11-09
- 리뷰어 상태: comprehension 완료 · readiness GO(첫 판정 NO-GO → 재리뷰 GO)

## Comprehension의 다시 쓰기

"같은편"은 Pleos 차량 앱과 태울 사람용 웹 페이지로 된 픽업 도우미다. 웹 페이지는 설치 없이 링크로 열고, 작은 Python 중계 서버가 내준다.
앱은 태울 사람이 걸어갈 만한 거리 안에서 랜드마크 앞 길가를 1~3곳 고른다. 고르는 자리는 차가 마지막으로 들어갈 때 오른쪽(같은 편)에 있고, U턴이 필요 없고, 세워도 되는 곳이다. 고른 자리는 기본 내비에 목적지와 진입 경유지로 넣는다.
주행 중에는 운전자 조작 없이 음성만 낸다. 도착 직전에 자동으로 전화를 걸고, 150m 앞에서 지점 방향을 말한다. 태울 사람에게는 나갈 시각, 건너편 경고, 한두 정거장 대중교통 제안을 보인다. 문·착좌 신호나 "탔어요"로 탑승을 알아채면 위치 공유를 끝낸다.
목적은 길 건너편에서 기다리다 U턴하거나 "어디야?" 전화를 주고받는 불편을 없애는 것이다. 1차(11/15)는 에뮬레이터와 성균관대역 주변 준비 데이터로 시연한다.

**기존 시스템**
- 이 저장소에는 아직 코드가 없다. spec-kit 설정, 헌법, 설계 초안, 11/7 회의 기록, 이 명세 폴더만 있다. 저장소에 `app/`, `server/`, `scripts/`가 없는 것도 확인했다.
- 지금은 태울 사람이 길 건너편에서 기다리면 운전자가 U턴하고, 둘이 "어디야?" 전화를 주고받는다. 문서가 "마중"이라 부르는 경쟁작에는 자동 전화가 없는 것으로 보인다.
- research가 에뮬레이터(Pleos Connect v2.1.0)에서 확인한 사실은 다음과 같다.
  - 기본 내비는 NaviHelper로 목적지와 경유지를 받는다. 와이파이가 연결돼 있어야 하는데, 콜드 부팅 직후에는 끊겨 있다.
  - 내비의 현재 위치와 남은 시간은 에뮬레이터 GPS를 따라오지 않는다.
  - FusedLocation은 약 1초마다 위치를 주지만 heading은 늘 0이다.
  - 문·착좌·기어는 Vehicle SDK로 받는다. 착좌 값은 한 번 늦게 오고, 뒷좌석은 늘 true다.
  - `ACTION_CALL`은 모의 SIM으로 통화가 된다. 블루투스 폰은 연결돼 있지 않다.
  - TTS는 온디바이스다. 주행 중에는 Pleos가 앱 화면을 가린다.
- 같은 공모전의 다른 앱 DRIVE DNA에서 다음을 복사해 쓴다: 빌드 뼈대, 색 토큰, 화면 도우미 `Ui`, 신호 구독 방식, 포그라운드 서비스·TTS.

## 지적과 처리

**리뷰어 이름 줄임**: C = comprehension, R = readiness

### 첫 리뷰: comprehension (#1~#13)

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 1 | C | Major | spec 용어 "남은 시간"·Assumptions | 남은 시간을 내비가 계산하는지, 앱이 추정하는지가 반대다. 정차 속도 25와 20이 다르다. SC-004가 자기 추정으로 잰다 | 수정 | 근거: research R3에서 정했고, 리뷰 전 대화에서 "남은 시간은 1차에서 앱이 직접 추정해요"라고 알렸다. 변경: spec 용어·Assumptions, R3 속도 문구(고를 때 25, 주행 중 max(속도, 20)), SC-004 "스크립트 경로·속도 기준" |
| 2 | C | Major | spec FR-005 | 1차에 링크가 어디서 열리는지, QR 주소가 없다 | 수정 | 근거: research R7에서 정했다(같은 맥 브라우저, HTTPS는 2차). 변경: FR-005, Assumptions, 범위 밖, ui.md QR 주소 `http://localhost:8090/p/<id>` |
| 3 | C | Major | data-model §4.2·§7·§8 walkSec | 걷는 시간을 다시 계산하는지가 없다. 대중교통을 받아들인 뒤 나갈 때 안내가 없다 | 수정(사용자 결정) | 2026-11-09 사용자 "추천대로 진행"으로 정했다. Q1 추천안: 받아들이면 걷는 시간을 다시 계산하지 않는다. 나갈 때 안내 대신 "○○ 타고 ○○에서 내려 ○○ 앞으로 와 주세요"를 보인다. 변경: data-model §7·§8, ui.md §3, FR-025, T030~T032 |
| 4 | C | Major | data-model §7 150m LEFT·건너편 경고 | crossOver와 wrongSide가 반대로 안내한다 | 수정 | 근거: FR-020(건너오게 안내)과 맞지 않는 모순이다. 변경: crossOver 뒤에는 wrongSide의 기준 길가를 반대쪽으로 바꿨고, T026 테스트를 더했다 |
| 5 | C | Major | spec FR-015·SC-003 | 음성 5번 상한이 앱 규칙인지 시연 목표인지 모호하다. 전화 줄 순서가 다르다 | 수정 | 근거: FR-015는 기능 요구사항이므로 앱 규칙이다. 변경: data-model §7 보충에 상한(5번 뒤로는 말하지 않음)과 T021 테스트. 전화 줄 순서는 #19(Q3)에서 정한다 |
| 6 | C | Major | contracts/api.md 끝 상태 | FR-028과 서버에 남기는 값이 다르다. 끝난 뒤 차 쓰기 규칙이 없다 | 수정 | 근거: plan Constitution Check와 api.md("상태 글자만 10분")에서 정했다. 변경: FR-028 문구, 끝 상태 쓰기 409, 차는 GET status로 끝을 안다 |
| 7 | C | Major | data-model §7 nospot | nospot에 탑승 경로가 없다. nospot 남은 시간 정의가 없다 | 수정(사용자 결정) | 2026-11-09 사용자 "추천대로 진행"으로 정했다. Q2 추천안: nospot에서도 "탔어요"와 문·착좌 판정을 받는다(기준점은 태울 사람 마지막 위치 50m). 남은 시간은 차 → 태울 사람 직선 × 1.3. 변경: data-model §7·§8, ui.md §3, FR-026, T018·T034·T037 |
| 8 | C | Major | spec Input·범위 밖 | F1~F5, R1~R6 번호가 정의되지 않았고 research R 번호와 겹친다 | 수정 | 근거: 사용자가 승인한 설계 초안의 번호다. 변경: spec에 대응표를 두고, 범위 밖에서는 번호를 뺐다 |
| 9 | C | Nit | spec Key Entities·FR-024 | 상태 목록과 대중교통 계산 시점이 data-model과 다르다 | 수정 | 사실 오류(data-model §7과 대조). 변경: 상태 목록과 FR-024 시점 |
| 10 | C | Nit | spec US3 #3 | 걷는 시간이 1분 미만이면 "0 = 전화 시각"이 틀린다. 분 처리 방식이 없다 | 수정 | 사실 오류(FR-016 최소 2분과 대조). 변경: 조건문과 분 올림 |
| 11 | C | Nit | spec SC-004 | "정확히 한 번"이 FR-017과 부딪힌다 | 수정 | 사실 오류. 변경: "나와 있지 않으면 정확히 한 번, 나와 있으면 0번" |
| 12 | C | Nit | spec US2 Why | "마중"이 경쟁 앱 이름인지 모른다 | 기록 | — |
| 13 | C | Nit | ui.md §1 링크 문구 | 거절 문구가 spec과 다르고, 언제 바뀌는지가 없다 | 수정 | 사실 오류(spec US3 #1과 대조). #26과 같이 고쳤다 |

### 첫 리뷰: readiness (#14~#37)

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 14 | R | Blocker | data-model §7, T012·T016·T020·T023 | choose에 넣을 차 위치를 언제 얻는지가 없다(CarPos는 going에서만, FusedLocation 연결은 US2) | 수정 | 근거: plan 흐름 2가 choose에 차 위치를 넣고, T012가 FusedLocation을 초기화한다. 빠진 것이다. 변경: CarPos를 모든 상태에서 받고, 셋이 갖춰지면 choose를 부르고, 없으면 link에 머문다. ui.md에 "차 위치를 확인하고 있어요", T012 구독, T014 테스트를 더했다 |
| 15 | R | Major | research R9, data-model §2·§5, quickstart | 반경 500m 한 곳으로는 이득 5분이 수식상 안 나온다. gainMin 내림이라 "n > 5"가 틀린다 | 수정 | 사실 오류(§4.2·§5 수식). 변경: 데이터 묶음 두 개(1.6km 넘게 떨어짐), 차 시작점, quickstart를 n ≥ 5로, §5 데이터 요건 |
| 16 | R | Major | data-model §7 | crossOver와 wrongSide가 반대로 안내한다(#4와 같은 문제) | 수정 | #4와 같이 고쳤다 |
| 17 | R | Major | api.md PUT car·끝 상태 | 끝 상태가 다시 살아날 수 있다. 지우는 범위가 두 문장에서 다르다 | 수정 | 근거: FR-028과 원칙 III. 변경: 끝 상태 쓰기는 모두 409, `status`·`name`만 남기기, check 6 |
| 18 | R | Major | data-model §7 끝 전이 | spots·link에 만료가 없다 | 수정 | 근거: 원칙 III "정해 둔 시간이 지나면 공유를 끝낸다". 변경: link·spots·nospot 60분(FR-027, T034) |
| 19 | R | Major | T023, T003, R4 | 주행 중 서비스의 ACTION_CALL은 백그라운드 시작 제한에 걸릴 수 있다. `<queries>`, 런타임 요청, 문장 순서가 다르다 | 수정(사용자 결정) · 재지적 | 2026-11-09 사용자 "추천대로 진행"으로 정했다. Q3 추천안: `TelecomManager.placeCall`로 건다. 판정은 CALL_PHONE 권한이다. 확인한 뒤 말하고, 2초 뒤 건다(안 되면 한 줄). T023 첫 단계에서 에뮬레이터로 확인한다. 변경: research R4, data-model §7 보충, T023. 재리뷰가 다시 지적했다(RR16) |
| 20 | R | Major | quickstart reset 4 | `pm clear`가 런타임 권한까지 되돌려 CALL_PHONE이 사라진다 | 수정 | 근거: DRIVE DNA `scripts/demo.py`의 reset은 `run-as rm`만 한다(코드). 변경: research R13, quickstart, T019 |
| 21 | R | Major | data-model §7 vs api.md 주기 | 위치를 올리는 Push가 §7에 없다 | 수정 | 근거: api.md "위치가 왔을 때 PUT, 1초에 한 번 이하"(리뷰 전 계약). 변경: §7 첫 행, T021 테스트 |
| 22 | R | Major | data-model §6·§7 Pickup | 규칙이 쓰는 상태 셋(passedWaypoint, park 모름, 올린 값)이 필드에 없다. 나와 있음 분기가 readySpoken을 안 본다 | 수정 | 근거: §6·§7 규칙이 이미 쓰는 값이고, "각각 한 번만" 규칙과 원칙 I(모르면 P 아님)에서 나온다. 변경: 필드 목록, remaining 서명, 분기 |
| 23 | R | Major | plan 흐름 2·4, data-model §2~§4 | 서명이 문서마다 다르다. 전역 Area로는 거르기를 합성 데이터로 테스트할 수 없다 | 수정 | 근거: 원칙 VI(거르기 규칙마다 실패하는 테스트). 변경: 지역·원점을 인자로 받기, Area는 data class와 DEMO, T008·T014 |
| 24 | R | Major | data-model §6, R3, spec 용어 | 정차 속도와 "서 있음" 기준이 없다. walkSec가 고정이다 | 수정 | 근거: spec 용어 "걷는 시간: 지금 위치에서"(리뷰 전 정의). 변경: §6 속도·서 있음 정의, §7 walkSec 다시 계산, T021 |
| 25 | R | Major | ui.md §3, FR-021, §8 | 페이지에 차 위치를 그리는 방법이 없다. nospot 남은 시간이 정의되지 않았다 | 수정(사용자 결정) · 재지적 | 2026-11-09 사용자 "추천대로 진행"으로 정했다. Q2 추천안: 차 점은 지점 300m 안일 때만 그림에 넣고, 밖이면 "차 n분 · n.n km"로 쓴다. nospot의 남은 시간을 정했다. 변경: FR-021, ui.md §3, data-model §8, T018. 재리뷰가 다시 지적했다(RR10·RR11) |
| 26 | R | Major | ui.md §1 link 문구, api PUT passenger | 세 문구를 언제 바꾸는지, consent:false를 보내는지, 한 요청 안의 처리 순서가 없다 | 수정 | 근거: spec US3 #1(거절하면 운전자에게 문구)과 quickstart("동의와 위치를 넣는다"). 변경: consent:false 보내기, 조건표, consent 먼저 처리 |
| 27 | R | Major | T011·T016 | 서버 실패 때의 동작(create 실패, put 실패, 404)이 없다 | 수정 | 근거: ui.md 띠 문구 "2초마다 다시 시도해요"와 SC-006(10초 안 종료). 변경: T011 실패 동작 세 가지 |
| 28 | R | Major | T016 SetNavi, R1 | addWaypoint를 부르는 때와 다시 설정하는 방법이 없다 | 수정(사용자 결정) · 재지적 | 2026-11-09 사용자 "추천대로 진행"으로 정했다. Q3 추천안: `onRouteStarted`를 받은 뒤 addWaypoint를 부른다(10초 안에 안 오면 로그). 다시 설정할 때는 cancel → request → add 순서다. 변경: research R1, T016. 재리뷰가 다시 지적했다(RR15) |
| 29 | R | Major | quickstart handsfree, T024 | sim 모드에서는 클릭이 없어 Web Audio 소리가 안 난다 | 수정 | 근거: 브라우저 자동 재생 정책상 클릭이 반드시 필요하다. quickstart 기대 결과("소리")를 지키는 길은 이것 하나다. 변경: ui.md 소리 준비, "소리 켜기", T018·T024 |
| 30 | R | Major | data-model §9 | 장면별 키가 없다. 주석 규칙과 예시가 어긋난다 | 수정 | 사실 오류(DRIVE DNA `DnaTest.kt`의 `substringBefore('#')`). 변경: 키 표, "# 뒤 버림" |
| 31 | R | Major | T009·T016 | 뒤 단계 코드에 기대는 task가 있다 | 수정 | 사실 오류(T031, T036 순서). 변경: 대중교통 점검은 T030으로, 착좌 구독은 T012로 |
| 32 | R | Major | T010~T012, api check | check가 계약의 검증을 다 덮지 않는다 | 수정 | 근거: 원칙 VI와 T010이 나열한 검증. 변경: check 8항목, 바꿔 끼우는 시계 |
| 33 | R | Major | ui.md 문구 | 분 반올림 방식, "(으)로", 번호 가리기, 20명 상한 동작이 없다 | 수정 | 근거: spec US3 #3(0 = 전화 시각)에서 올림이 나온다. "(으)로"는 표기다. ui.md 예시 "010-****-5678"과 data-model §1 최대 20명에서 나온다. 변경: ui.md 공통 규칙, §1, data-model §1·§7·§8 |
| 34 | R | Major | data-model §2 photo, api `/photo/`, T004 | 사진 출처와 경로가 다르다. 알림 아이콘이 없다 | 수정 | 근거: data-model §2 "없으면 null, 페이지는 숨긴다"(리뷰 전 결정), api.md `server/photo/`. 변경: 1차 기본 null, 경로 통일, T004 `ic_pin` |
| 35 | R | Nit | T009 | "2-1 게이트"가 정의되지 않았다 | 기록 · 재지적 | 재리뷰가 다시 지적했다(RR23) |
| 36 | R | Nit | Phase 3 Independent Test | "도착 전까지"와 "도착까지"가 다르다 | 수정 | 사실 오류. 변경: "도착까지(탑승은 US5)" |
| 37 | R | Nit | T036 | 좌석 수가 네 곳과 세 곳으로 다르다 | 수정 | 사실 오류. 변경: 문 네 곳, 좌석 세 곳 |

### 재리뷰: readiness 새 지적 (#38~#57)

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 38 | R(재) | Major | T011, data-model §7 Gone | 공유 끄기 직후 차 PUT이 409를 받으면 `expired`로 끝나 `stop` 장면이 흔들린다 | 수정 | 근거: quickstart `stop` 기대 화면(리뷰 전). 변경: 409면 GET을 다시 해 끝 상태를 Remote로 넣고, Gone은 404일 때만 쓴다 |
| 39 | R(재) | Major | T011 Relay | 반환형이 404·409와 연결 실패를 구분하지 못한다. End 뒤 다시 보낼 주체가 없다 | 수정 | 사실 오류(Boolean으로는 세 경우를 나눌 수 없다). 변경: HTTP 코드 반환, 재전송 2초 타이머, Phase 3 Checkpoint 실패 확인 두 개 |
| 40 | R(재) | Major | T012·T016 | 새 픽업의 park·track이 비어 P에서도 확인이 막힌다 | 수정 | 근거: data-model `park == true`와 구독 특성(바뀔 때만 옴). 변경: 서비스의 마지막 값으로 시작값을 채운다 |
| 41 | R(재) | Major | data-model §7 150m | 꺾어 들어오는 경로에서는 판정 시점의 방향이 틀려 맞는 쪽 사람을 건너게 한다 | 수정 | 근거: FR-019 "실제 진행 방향 기준"의 뜻과 원칙 II. 변경: 지점 길 위(15m)에서만 판정, angleDiff(heading, h) 기준, 50m 대체 문구, FR-019 문구, T021 합성 테스트 |
| 42 | R(재) | Major | R3, quickstart handsfree | 직선 × 1.3 추정이 경로에 따라 SC-004 30초 창을 넘는다 | 수정 | 사실 오류(수식 계산). 변경: data-model §2 장면 요건 4(비율 1.17~1.30), T021 장면 재생 테스트 |
| 43 | R(재) | Major | data-model §7 30m | 30m 판정이 길가 쪽을 안 봐서 건너편 사람도 "나와 있음"이 된다 | 수정 | 근거: FR-017 "지점에 나와 있음"의 뜻과 원칙 II. 변경: 같은 쪽일 때만, T021 테스트, wrongside 확인 기준 |
| 44 | R(재) | Major | data-model §7 탑승 | 세 조건을 따지는 이벤트와 순서가 없다 | 수정 | 근거: FR-026은 순서를 정하지 않으므로 어느 순서든 맞아야 한다. 변경: `seatTaken`, 문 닫힘·착좌 둘 다에서 검사, 거리는 닫힐 때, T034 테스트 |
| 45 | R(재) | Major | data-model §7 보충 상한 | 전화 줄이 상한에 세어지지 않는다 | 수정 | 근거: FR-015. 변경: CallOrNotify도 세고, `speak` 플래그를 두고, T021 |
| 46 | R(재) | Major | data-model §7·§8 대중교통 | 받아들인 지점의 표현(index), 대안, walkSec, 페이지 표시, spots에서 알리기가 없다 | 수정(사용자 결정) | 2026-11-09 사용자 "추천대로 진행"으로 정했다. Q1 추천안: spots를 정류장 근처 지점 하나로 바꾸고 대안은 지운다. chosen은 spots면 −1, going이면 0이다. 정차 중이든 주행 중이든 한 줄로 알린다. 변경: data-model §7, ui.md §1·§3, T030~T032 |
| 47 | R(재) | Major | api.md POST·GET | 만든 직후 값, `acc`, 모르는 필드, 타입 오류, Remote 형식이 없다 | 수정 | 근거: ui.md link 조건표가 consent null과 false를 나눈다. research R9(Pick.kt는 org.json 불가). 변경: 처음 값, 필드 규칙, data-model Remote 형식 |
| 48 | R(재) | Major | ui.md §1·§3 | consent true인데 태울 사람 위치가 없을 때, 위치 오류, consent:true를 보내는 때가 없다 | 수정 | 근거: data-model §7 link 전이(셋이 갖춰져야 고름)에 이미 있는 상태다. 변경: 조건표 줄과 순서, 페이지 위치 오류 화면, 누르는 즉시 consent:true |
| 49 | R(재) | Major | api.md 안전망·청소 | 안전망 30분이 link 60분보다 먼저 지운다. 청소 방식이 없다 | 수정 | 근거: FR-027(60분)과 FR-028·원칙 III. 변경: key가 맞는 GET도 차의 요청으로 세기, 60초 청소 스레드, check 7 |
| 50 | R(재) | Major | quickstart 시나리오 | 취소, 거절, nospot, 대체 알림, expired 장면이 없다 | 수정 | 근거: spec 인수 시나리오(리뷰 전). 변경: `cancel`·`decline`·`nospot`·`fallback` 장면, expired는 단위 테스트, T019·T025·T038 |
| 51 | R(재) | Major | tasks Phase 2 Checkpoint | Relay 확인을 Phase 2에서 할 수 없다 | 수정 | 사실 오류(픽업 시작은 T016). 변경: Phase 3 Checkpoint로 옮김 |
| 52 | R(재) | Major | quickstart tour·reset, T019·T039 | 장면을 이어 붙이는 방법이 없다 | 수정 | 근거: quickstart tour 정의(리뷰 전)를 돌리는 데 필요하다. 변경: 장면 공통 규칙, transit 끝내기, reset GPS |
| 53 | R(재) | Major | plan 흐름 1 vs ui.md §1 | "데리러 가기" 버튼과 줄 누르기가 다르다 | 수정 | 사실 오류(ui.md·quickstart와 대조). 변경: plan 흐름 1 |
| 54 | R(재) | Major | data-model §1, ui.md "내 차" | 차 정보 검증이 없다(번호는 `tel:`에 쓰임) | 수정 | 근거: §1 사람 검증 규칙과 같은 기준이다. 번호를 `tel:`에 넣으므로 숫자만 둔다. 변경: §1 차 정보 검증, ui.md, T007 |
| 55 | R(재) | Major | T003·T013 런타임 권한 | CALL_PHONE·POST_NOTIFICATIONS 요청이 없다 | 수정 | 근거: DRIVE DNA `MainActivity`의 `requestPermissions`(코드)와 FR-018 대체 경로. 변경: T013 |
| 56 | R(재) | Nit | data-model §9 | 상대 경로 기준이 헷갈린다 | 수정 | 사실 오류(DRIVE DNA `DnaTest.kt`의 `File("../scripts/…")`, 기준 `app/`). 변경: 문구 |
| 57 | R(재) | Nit | plan 가져오는 것 | 아이콘 "색만 바꾼다"가 T004와 다르다 | 수정 | 사실 오류. 변경: plan 표 |

## 재리뷰

- **대상**: readiness. 첫 VERDICT NO-GO → 재리뷰 VERDICT GO
- **새 지적**: #38~#57(20개)
- **`재지적` 행**: #19(RR16), #25(RR10·RR11), #28(RR15), #35(RR23)

## 집계

지적 57개 중 문서를 바꾼 것 55개
- 수정 49개, 수정(사용자 결정) 6개: #3, #7, #19, #25, #28, #46(2026-11-09 사용자 "추천대로 진행")
- 기록 2개: #12, #35
