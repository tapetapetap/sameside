# UI 계약: 같은편 6차 — 차 화면 개선과 주행 중 한 줄 카드

크기 단위는 sp(글자)·dp(칸)다. 에뮬레이터에서 1dp = 1px이다. "그대로"는 지금 값을 바꾸지 않는다는 뜻이다.

## §1 주행 중 한 줄 카드

### 언제

`MainActivity.render`에서 `PickupService.park != true`(D·N·R 또는 아직 모름)이면 한 줄 카드, 아니면 지금 정차 화면이다.

### 화면

- 사람 칸(바깥 ScrollView)과 구분선은 `GONE`. 상황 칸(`R.id.screen`)이 앱 칸 폭을 다 쓴다. 정차로 돌아오면 `VISIBLE`.
- 상황 칸 내용: (서버 배너가 있으면 그 배너) + 카드 하나.
- 카드: `card` 바탕(카드색, 1.5dp 선, 반지름 24), 여백 40, 위 여백(화면 안) 0. 안은 가로 줄 간격 32:
  - 남은 분(있을 때만): "<n>분", 72sp, 800, text1.
  - 한 줄 글: 32sp, 700, text1, 남은 폭을 쓰고 넘치면 줄바꿈.
- 움직임·누를 것·아이콘이 없다. 접근성 설명은 글자 그대로.

### 내용 `glance(p, creating, contactOf): Glance` (Pick.kt, 순수)

`Glance(minutes: Int?, line: String)`. 위에서부터 처음 맞는 줄:

| 조건 | minutes | line |
|---|---|---|
| `p == null`, `creating` | null | "링크를 만들고 있어요" |
| `p == null` | null | "운전 중이에요 · 픽업은 차를 세우고 시작해요" |
| MET | null | "만났어요" |
| CANCELED | null | "픽업을 취소했어요" |
| STOPPED | null | "<마지막으로 끈 사람 또는 첫 사람>님이 위치 공유를 껐어요"(정차 화면과 같은 이름 규칙) |
| EXPIRED | null | "시간이 지나 위치 공유를 끝냈어요" |
| LINK, 한 명, 공유 거절(`consent == false`) | null | "<이름>님이 위치를 공유하지 않았어요" |
| LINK, 한 명, 동의했고 위치 아직 | null | "<이름>님 위치를 기다리고 있어요" |
| LINK, 한 명, 동의·위치 있음, 차 위치 아직 | null | "차 위치를 확인하고 있어요" |
| LINK, 한 명, 그 밖 | null | "<이름>님이 링크를 열면 만날 곳을 골라요" |
| LINK, 여러 명 | null | "함께 데리러 가요 · 위치를 기다려요" |
| SPOTS, 후보(`p.spots.size`) 1곳 이상 | null | "만날 곳 <p.spots.size>곳 · 차를 세우면 골라요" |
| SPOTS, 후보 0곳, 차례 계획 있음 | null | "차례로 태우기 · 차를 세우면 골라요" |
| NOSPOT, 한 명 | null | "가까운 곳에 같은 편 자리가 없어요" |
| NOSPOT, 여러 명 | null | "함께 만날 곳이 없어요" |
| GOING, 빠지지 않은 사람이 모두 탐 | null | "모두 탔어요" |
| GOING | `minutes(p.etaSec ?: 0.0)` | "<다음 사람 이름>님 · <짧은 상태>" |

- 다음 사람: 차례로 태우기(`p.chained`)면 `p.active` 중 `stop == p.chosen`이고 안 탄 첫 사람, 없으면 `p.active` 중 안 탄 첫 사람. 차례가 아니면 `p.active` 중 안 탄 첫 사람.
- 짧은 상태(`shortStatus(kind)`): ABOARD "탔어요", CALLING "통화 중", READY "나와 있어요", ACK "지금 나간대요", MOVING "나오고 있어요", CALLED "전화를 걸었어요", PHONE_NOTIFIED "폰으로 알렸어요", NOTIFIED "나오라고 알렸어요", WAIT "가까워지면 알려요", OUT "함께 안 타요".
- `contactOf`는 정차 화면의 연락 줄과 같은 함수(`contact(m, inCall, callSince, latest, now)`)다.

### 입력 창

`Screens.kt`의 `show`가 연 `AlertDialog`를 파일 안 변수에 두고, 창이 닫히면(`setOnDismissListener`) 변수를 비운다. `closeDialog()`는 열려 있으면 닫고 비운다. `render`가 한 줄 카드로 갈 때, 그리고 `MainActivity.onDestroy`가 부른다(액티비티가 다시 만들어질 때 옛 창이 남지 않게).

## §2 정차 화면 요소별 값

| 도우미·화면 | 요소 | 지금 | 바뀜 |
|---|---|---|---|
| `Ui.small` | 보조 글 | 22sp 600 text3 | **24sp** |
| `Ui.chip` | 상태 칩("가까이만") | 20sp 600, 여백 18·9·20·9, 아이콘 22 | **24sp**, 여백 20·10·22·10, 아이콘 26. 누르지 않는 칩 전용 |
| `Ui.banner` | 서버 배너 | 22sp | **24sp** |
| `Ui.button` | 모든 버튼 | 24sp 700, 여백 28·18(높이 약 64) | 최소 높이·폭 **76dp**, 여백 가로 28·세로 0. 보조 버튼 글자 `blue_text`(지금 blue) |
| 첫 화면 | "여러 명 함께" | 20sp, 135×45dp, 여백 20·10 | **24sp**, 최소 높이·폭 **76dp**, 여백 가로 24·세로 0, `gravity = CENTER`. 글자색 꺼짐 `blue_text`·켜짐 흰색, 바탕 그대로 |
| 첫 화면 | 제목 줄 간격(제목 ↔ "여러 명 함께") | 16dp | **24dp** |
| 첫 화면 | "가까이만" 칩 | blue / blue_soft | **blue_text** / blue_soft |
| 첫 화면 | 사람 줄 첫 글자 원(`Ui.avatar`) | 원 48dp, 글자 원 × 0.42(약 20sp) | 원 **58dp**(글자 약 24sp). 색은 그대로(spec 용어의 장식 예외) |
| 첫 화면 | 사람 줄 | 높이 약 80dp, 줄 사이 18dp | 최소 높이 **76dp**, 줄 사이 **24dp**. 전화번호 22sp → **24sp** |
| 링크(한 명·여러 명) | 링크 글 | 22sp text3 | **24sp** text3 |
| 가는 중 | 도착 확인 버튼 줄 간격 | 16dp | **24dp** |
| 가는 중 | 차례 카드 제목 | 26sp 700 | **28sp** 700, 문구 §4 |
| 대중교통 강조 카드 | 둘째 줄 | 22sp | **24sp** |
| 차례로 태우기 카드 | 지점 목록 줄 | 22sp text3 | **24sp** text3 |
| 입력 창 | 오류 글 | 20sp | **24sp** |
| 연락 줄(`ContactRow`) | 상태 글 / 초읽기 숫자 | 22sp / 15sp 주황 | **24sp** / **20sp** `amber_text` |
| 연락 줄 | 아이콘 색 | green / amber | **green_text / amber_text**(물결·링 선은 green / amber 그대로) |
| 지도 3종 | 지도 이름표("사람과 이동 경로" 등) | 13sp | **20sp**(이름표 상자도 글자에 맞춤) |
| 지도 3종 | N / 축척 글 / 출처 | 19 / 15 / 12sp | **20 / 20 / 14sp** |
| `SpotMap`(가는 중) | 이름표 | 17·19sp, 사람 이름 green | **20sp**, 사람 이름 `green_text` |
| `PeopleMap` | 지점·사람 / 역 / 노선 글 | 17 / 14 / 15sp, 사람 이름표 글자 사람 색 | **20 / 20 / 20sp**, 사람 이름표 글자 **text1**(점·점선은 사람 색 그대로). 역·노선 글은 `optional` |
| `StopMap` | "여기 세워요" / 랜드마크 / 지나는 곳 | 17(blue) / 15 / 14sp | **20**(`blue_text`) **/ 20 / 20sp**. 지나는 곳은 `optional` |
| 지도 3종 | 빈 자리가 없을 때 | 첫 후보 자리에 겹쳐 그림 | `optional` 이름표는 그리지 않음(자리도 안 잡음), 나머지는 그대로 첫 자리 |
| `ChainStrip` | 노드 안 첫 글자 | 14sp 흰 글자 | **지운다**(원만 남김, 탄 사람은 체크 그대로) |

누르는 칸 목록(FR-104): "여러 명 함께", 사람 줄, "n명 함께 데리러 가기", "+ 사람 추가", 내 차 줄, "이 자리로 갈게요", "차례로 태울게요", "보내기", "아니요", "확인", "픽업 취소". 모두 76×76dp 이상.

## §3 색(`values/colors.xml`, `values-night/colors.xml`)

| 토큰 | 낮 | 밤 |
|---|---|---|
| `text3` | #6B6E76 → **#62656D** | 그대로 |
| `green_text` (새) | **#017A40** | **#00E074** |
| `amber_text` (새) | **#A35A00** | **#F2A33A** |
| `blue_text` (새) | **#0050D8** | **#6CB4FF** |

글자에 `text4`를 쓰는 곳은 `text3`으로 바꾼다(있으면). 대비는 research R7 표. 3:1은 spec 용어의 큰 글자 예외(40sp 이상 제목, 파랑 바탕 위 24sp 이상 흰 굵은 버튼 글자)에만 쓰고, 흐린 비활성 요소(alpha 0.4·0.5)는 기준에서 뺀다.

## §4 문구

| 자리 | 지금 | 바뀜 |
|---|---|---|
| 가는 중 안내 줄 | "내비에 넣었어요. 출발하세요" | "내비에 만날 곳을 넣었어요" |
| 차례 카드 제목 | "차례로 태우는 중 · <k>/<n>" | "차례로 태우는 중 · <n>곳 중 <k>번째" |
| 연락 줄 WAIT | "알림 전" | "가까워지면 알려요" |
| 취소 확인 중 버튼 | — | "한 번 더 누르면 취소해요"(`red_text`) |
| 한 줄 카드 | — | §1 표 |

## §5 배치, 뒤로, 보존, 취소

- **만날 곳 배치**(SPOTS): 제목 → 대중교통 강조 카드(있으면) → **차례로 태우기 카드(있으면)** → 만날 곳 카드들 → 취소 버튼.
- **두 번째부터 만날 곳 카드**(`i > 0`): 지도 줄과 이유 줄을 빼고 이름(30sp) → 사람 줄 → 차 줄·비교 줄 → 버튼. 1순위 카드는 지금과 같다(지도 230dp, 이름 40sp, 이유 26sp).
- **뒤로**(`onBackPressed`): `together`면 `together = false`, `picked.clear()`, `tooMany = false`, 다시 그림. 아니면 `super.onBackPressed()`.
- **보존**: `onSaveInstanceState`에 `together`(Boolean), `picked`(사람 목록 안 위치 `IntArray`, 차례대로, 전화번호 없음). `onCreate`에서 `People.load` 목록의 그 위치에 있는 사람을 차례대로 되살린다(목록 밖 위치는 뺀다).
- **취소 두 번 누르기**: `MainActivity.cancelUntil`(uptime). `situation(..., cancelArmed = now < cancelUntil, onCancel)`. 첫 누름: `cancelUntil = now + 4000`, 다시 그림, 4초 뒤 다시 그림. 두 번째 누름(확인 중): `cancelUntil = 0`, `PickupService.send(Event.Cancel)`. `render`가 한 줄 카드로 가거나 픽업이 없거나 끝난 상태(`p == null || p.status.ended`)를 그리면 `cancelUntil = 0`.
