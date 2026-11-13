# Research: 같은편 6차 — 차 화면 개선과 주행 중 한 줄 카드

## R1. 차 기준 값

- **Decision**: 누르는 칸 76×76dp 이상, 이웃한 누르는 칸 사이 24dp 이상, 화면 글자 24sp 이상, 카드 제목 28sp 이상, 그림 안 글자 20sp 이상(지도 출처 14sp), 글자 대비 4.5:1 이상(파랑 바탕 흰 굵은 버튼 글자는 큰 글자 3:1).
- **Rationale**: Android for Cars(https://developers.google.com/cars/design/design-foundations/visual-principles, https://developers.google.com/cars/design/automotive-os/design-system/sizing). 세 앱이 같은 값을 쓴다(안 써본 기능 6차 research R1). 지도 그림 글자를 24가 아닌 20으로 둔 것은 지도 높이(230·300dp) 안에 이름표 자리가 모자라서다(R4).
- **Alternatives considered**: 지도 이름표도 24sp(이름표 대부분이 자리를 못 찾아 사라짐).

## R2. 주행 최적화와 한 줄 카드 갱신

- **Decision**: 매니페스트의 MainActivity에 `<meta-data android:name="distractionOptimized" android:value="true" />`를 넣는다. 주행 중에는 `render`가 사람 칸(바깥 ScrollView와 구분선)을 `GONE`으로 하고 상황 칸에 `Ui.glanceCard(glance(...))` 하나만 붙인다.
- **Rationale**: Pleos 에뮬레이터는 adb로 설치한 앱의 이 선언을 받아 D에서도 가리지 않는다(안 써본 기능 5차 verification, 2026-11-12). `render`는 위치가 올 때마다 불리지만, 지금의 `replace`가 글자·켜짐 서명이 같으면 뷰를 바꾸지 않으므로 한 줄 카드는 분이나 글이 바뀔 때만 다시 붙는다(research R25 규칙 그대로). 움직이는 그림(연락 줄 물결, 지도)은 한 줄 카드에 없다.
- **Alternatives considered**: 주행 중용 별도 액티비티(상태 공유가 늘어남), 정차 화면을 그대로 두고 버튼만 숨기기(지도·연락 줄 움직임이 남아 주의분산 기준과 사용자 결정에 어긋남).

## R3. 짧은 연락 상태 글

- **Decision**: 한 줄 카드는 연락 상태(`ContactKind`)마다 초 숫자 없는 글을 쓴다.

| 상태 | 정차 화면 연락 줄(지금) | 한 줄 카드 |
|---|---|---|
| ABOARD | 탔어요 | 탔어요 |
| CALLING | ○○님과 통화 중 · m:ss | 통화 중 |
| READY | 나와 있어요 | 나와 있어요 |
| ACK | 지금 나간대요 | 지금 나간대요 |
| MOVING | 나오고 있어요 | 나오고 있어요 |
| CALLED | 전화를 걸었어요 · n초 전 | 전화를 걸었어요 |
| PHONE_NOTIFIED | ○○님 폰으로 알렸어요 | 폰으로 알렸어요 |
| NOTIFIED | 지금 나오세요를 보냈어요 · n초 뒤 전화 | 나오라고 알렸어요 |
| WAIT | 알림 전 → **가까워지면 알려요** | 가까워지면 알려요 |
| OUT | 함께 안 타요 | (다음 사람에서 빠짐) |

  한 줄은 "<이름>님 · <글>"이다. 이름 뒤 조사를 고르지 않아도 되게 가운뎃점으로 나눈다.
- **Rationale**: 초가 1초마다 바뀌면 주행 중 화면이 계속 움직인다(주의분산). 상태가 바뀌는 순간에만 한 줄이 바뀐다.
- **Alternatives considered**: 정차 화면 문장을 그대로(초 숫자가 1초마다 바뀜).

## R4. 지도 이름표 키우기

- **Decision**: 지도(`SpotMap`, `PeopleMap`, `StopMap`)의 이름표·N·축척 글자를 20sp로, 출처 "© OpenStreetMap"은 14sp로 올린다. 연락 줄 상태 글 24sp, 초읽기 숫자 20sp. 구간 그림(`ChainStrip`) 노드 안 첫 글자(14sp)는 지운다(바로 아래에 이름표 22sp가 있다). 빈 자리 찾기를 둘로 나눈다: 새 `freeIndex`는 빈 자리가 없으면 −1이고, 지금의 `firstFree`는 `freeIndex(…).coerceAtLeast(0)`로 남는다. `place`에 `optional` 인자를 더해, 역 이름·노선 글·지나는 곳은 `optional = true`로 불러 −1이면 그리지도 자리를 잡지도 않는다.
- **Rationale**: 지금 `place`는 빈 자리가 없으면 첫 후보 자리에 겹쳐서라도 그린다(`firstFree`의 `coerceAtLeast(0)`, Views.kt; GroupTest "빈 자리가 없으면 첫 자리"). 그래서 글자만 키우면 이름표가 서로 겹친다. 설계(S2)의 뜻은 "겹치는 이름표는 그리지 않는다"였으므로 덜 중요한 이름표만 빠지게 한다. 지점·사람·"여기 세워요"·랜드마크는 지금처럼 늘 그린다(4차 차례: 지점 → 사람 → 역 → 노선 글). 두 번째부터의 만날 곳 카드에서 150dp 지도가 빠지므로(FR-109) 남는 지도는 230dp(1순위)와 300dp(가는 중)다. 노드 첫 글자를 20sp로 키우려면 원을 키워야 하는데 같은 정보가 이름표에 있으므로 지우는 쪽이 작다.
- **Alternatives considered**: 지도 높이를 키우기(카드가 더 길어져 S7과 어긋남).

## R5. 취소 두 번 누르기

- **Decision**: 첫 누름은 `cancelUntil = uptime + 4초`로 버튼을 "한 번 더 누르면 취소해요"(빨간 글자 `red_text`, 바탕 그대로)로 바꾸고 4초 뒤 다시 그린다. 그 안의 두 번째 누름이 `Event.Cancel`을 보낸다.
- **Rationale**: 차 화면에서 모달 대화상자는 주의를 끈다. 되돌리기(undo)는 서버와 링크 상태를 되살려야 해 크다. 같은 자리 두 번 누르기가 가장 작다.
- **Alternatives considered**: 확인 대화상자, 취소 뒤 되돌리기.

## R6. 설정 변화 때 고르기 보존

- **Decision**: `onSaveInstanceState`에 `together`와 고른 사람을 `People.encode(picked)`(이름·전화번호·가까이만 한 줄씩, 차례대로)로 넣고, `onCreate`에서 `People.decode`한 사람 가운데 `People.load` 목록에 있는 사람만 차례대로 되살린다(`distinct()`). 취소 확인 상태는 보존하지 않는다.
- **Rationale**: 낮·밤 전환은 액티비티를 새로 만든다. 픽업 상태는 서비스에 있어 남지만 "여러 명 함께" 고르기는 액티비티에만 있어 사라진다. 사람 저장은 같은 이름을 막지 않으므로(`People.name()`은 길이만 본다) 이름만으로는 사람을 가릴 수 없다. `Person`은 data class라 세 값이 같으면 같은 사람이다.
- **Alternatives considered**: `configChanges="uiMode"`(레이아웃 바탕 색을 손으로 다시 칠해야 함).

## R7. 색 값

- **Decision**: 낮 `text3` #62656D. 새 토큰 `green_text`(낮 #017A40, 밤 #00E074), `amber_text`(낮 #A35A00, 밤 #F2A33A), `blue_text`(낮 #0050D8, 밤 #6CB4FF). 글자에 쓰던 `text4`는 `text3`으로.
- **Rationale**: WCAG 상대 휘도로 계산했다.

| 글자 / 바탕 | 낮 | 밤 |
|---|---|---|
| green_text / green_soft · 카드 | 4.84 · 5.44 | 8.77 · 9.46 |
| amber_text / amber_soft · 카드 | 4.69 · 5.22 | 7.39 · 8.00 |
| blue_text / blue_soft · 카드 | 5.90 · 6.69 | 6.72 · 7.63 |
| text3 / 카드 · 바탕 · 회색 칩 | 5.83 · 5.49 · 5.03 | 6.14 · 6.79 · 5.15 |
| red_text / blue_soft(취소 확인 버튼) | 5.46 | 6.38 |

| blue_text / blue_soft("가까이만" 칩) | 5.90 | 6.72 |
| text1 / 지도 바탕(사람과 이동 경로의 사람 이름표) | 15 이상 | 13 이상 |

  지금 값: 낮 초록 #00A86B / green_soft 2.74, 주황 #E08A12 / 카드 2.69, "가까이만" blue / blue_soft 4.34(밤 3.94), 사람 이름표 avatar2_line #B67338 / 지도 바탕 약 3.37, avatar3_line 약 4.22, 밤 avatar5 약 3.74. 사람 이름표는 글자를 text1로 하고, 사람 색은 점과 점선에 남긴다.
- **Alternatives considered**: `green`·`amber` 자체를 진하게(지도 선·링 색까지 어두워짐).
