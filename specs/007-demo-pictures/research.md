# Research: 같은편 7차 — 시연용 그림 바로잡기와 제출 전 화면 수정

## R1. 첫 화면이 사람 수를 아는 법(S1)

- **Decision**: `Ui.situation`(Screens.kt 97행)에 `noPeople: Boolean` 인자 하나를 `waiting` 뒤에 더한다. `MainActivity.render`가 이미 읽은 `val list = People.load(this)`의 `list.isEmpty()`를 넘긴다.
- **Rationale**: render는 정차 화면마다 사람 칸을 그리려고 목록을 이미 읽는다. 화면 도우미는 저장소를 읽지 않는다(People.kt: SharedPreferences는 그 파일의 여섯 함수만). 제목과 1단계만 갈리므로 수가 아니라 참/거짓이면 된다.
- **Alternatives considered**: 목록을 통째로 넘기기(쓰지 않는 값), `situation` 안에서 `People.load(ctx)`(저장 읽기가 화면 도우미로 샘).

## R2. 단계 카드 틀과 문구(S1)

- **Decision**: Screens.kt에 `private fun Ui.intro(waiting: Boolean, noPeople: Boolean): List<View>`를 두고 `situation`의 `null ->` 갈래(105행)가 부른다. 안의 `step(n, bg, fg, title, sub)`는 안 써본 기능 `firstRun()`(car-unused Screens.kt 202행)·DNA `first()`(car-dna Screens.kt 19행)와 같다: `row(24, 숫자 text(24sp, 700, 가운데, 52dp 원 round(bg, 100)), column(4, text(제목, 28sp, text1, 600), text(설명, 24sp, text3)), grow = 1)`, 바탕 `round(card, 22, line)`, 여백 가로 28·세로 24. 순서는 `small("같은편")`, `headline(제목)`, 1·2단계(`blue`/`on_accent`), 3단계(`green_soft`/`green_text`). 같은편 Ui에는 `hero`·`tile`이 없어 제목은 `headline`(48sp)이고, 사이 간격은 `situation`의 `column(28)`이다.
- **Rationale(문구)**: 1단계: 사람 줄을 누르면 `PickupService.begin`이 링크를 만든다(MainActivity `onPick`). 2단계: 링크 화면은 QR과 링크를 보이고 태울 사람은 앱 없이 연다(README 첫 줄). 3단계: 같은 편 만날 곳을 고르고(README F1), "걷는 시간 + 1분"에 "지금 나오세요"를 보내고 60초 안에 반응이 없으면 한 번 전화한다(README F5, 6차 verification `handsfree` 전화 − 알림 60.2초).
- **방향 말**: 1:1 세로(`layout-port/activity_main.xml`)는 사람 칸이 아래다. 그래서 "왼쪽"을 쓰지 않고 버튼 이름으로 가리킨다. 지금 문구 "왼쪽에서 사람을 고르면…"도 세로에서는 틀렸다.
- **대비**: 번호 원 흰 숫자/파랑 낮 4.92, 밤 3.73(24sp 굵게라 큰 글자 3:1, 다른 두 앱과 같음). text3/카드 5.83·6.14, green_text/green_soft 4.84·8.77.
- **Alternatives considered**: 다른 두 앱처럼 아이콘 타일(같은편에 `tile`과 그림이 없어 새로 만들어야 함), 사람이 없을 때만 카드(2-1에서 고르지 않은 다른 길).

## R3. 지도 출처 바탕(S2)

- **Decision**: `MapBase.corners()`(Views.kt 272행)에서 285행이 글자 크기를 14sp로 정한 뒤, 286행 `drawText` 바로 앞에서 `tw = small.measureText("© OpenStreetMap")`를 잰다(그 전에는 `small`이 축척 글 크기 20sp라 폭이 약 163dp로 재진다). 순서는 textAlign RIGHT → textSize 14sp → `tw` → 바탕 → alpha 255 → 글자색 text2 → drawText다. 바탕은 `fill.color = color(R.color.card); fill.alpha = 217`, `c.drawRoundRect(w - dp(16f) - tw, h - dp(24f), w - dp(4f), h - dp(2f), dp(6f), dp(6f), fill)`, `fill.alpha = 255`. 글자는 14sp 그대로, 색 `R.color.text2`, 자리 그대로(오른쪽 끝 `w - dp(10f)`, 기준선 `h - dp(8f)`).
- **Rationale**: `mapName()`(259행)과 같은 바탕이다(카드색 85%, 모서리 6dp, 글자 좌우 6dp 여유). 6차 캡처 03에서 출처 글자는 폭 114px(density 160이라 dp와 같음), 위아래 h−18~h−6이다. 그래서 바탕 칸(w−130~w−4, h−24~h−2)은 `reserveCorners()`의 출처 칸 `Box(w − 140, h − 24, w, h)`(251행) 안이고, 이름표 자리 규칙은 그대로다. 세 지도 모두 `corners()`를 맨 끝에 부르므로 한 곳만 고친다.
- **대비**(text2 / 카드색 85% + 그 밑 색):

| | 낮 | 밤 |
|---|---|---|
| 지도 색 위 가장 낮은 값 | 8.62(1호선 선 위) | 7.11(흰 차선 위) |
| 검정·흰색 위(이론 최저) | 7.81 | 6.39 |
| 지금(text3, 바탕 없음) | 1.21(아스팔트 위) | 1.82(흰 차선 위) |

- **Alternatives considered**: `label()`처럼 지도 바탕색 테두리만(차선 줄무늬 위에서 얼룩짐), 출처를 지도 밖 줄로(세 지도 배치가 바뀜).

## R4. 가는 중 지도 사람 색(S3)

- **Decision**: `SpotMap.onDraw()`에서 `val green`·`val greenText`(Views.kt 320·321행)를 지운다. 걷는 점선(330행)과 지도 안 점(365행)은 `color(AVATAR_LINES[m.slot])`, 가장자리 화살표(376행)는 묶음 첫 사람 `AVATAR_LINES[group[0].slot]`, 이름표 글자(394·403행)는 `color(R.color.text1)`이다. 점 둘레 흰 테두리는 그대로다.
- **Rationale**: `mapPeople()`(Screens.kt 156행)이 이미 `colorSlots(이름 목록)` 번호를 `MapPerson.slot`으로 넘긴다. 차례 그림(`ChainStrip`), `PeopleMap`, `StopMap`이 같은 번호를 쓴다. 이름을 text1로 둔 것은 6차 R7과 같다(사람 색 글자는 지도 바탕 대비 3.37까지 떨어짐). 점·선 대비(그림 3:1): 낮 3.37~6.23, 밤 3.74~7.59.
- **tag**: `SpotMap` tag에 색 번호를 넣지 않는다. 한 픽업 안에서 번호는 이름 목록으로만 정해져 바뀌지 않고, 다른 픽업이면 화면 글자가 달라 어차피 다시 붙는다(MainActivity `replace`·`sig`). 정차 화면은 갱신마다 View를 새로 만들지만, 글자와 그리기 View의 tag가 같으면 옛 View를 그대로 둔다(MainActivity.kt 136~145행, Views.kt 293행). 색 번호는 한 픽업 안에서 바뀌지 않으므로 tag에 없어도 옛 색이 남지 않는다.
- **Alternatives considered**: 이름 글자도 사람 색(낮 갈색 3.37:1로 4.5:1 미달).

## R5. 태울 사람 페이지 그림(S4)

- **Decision**(`server/page.html` `drawing()`, 113~119행):
  - 머리: `<marker id="m" markerUnits="userSpaceOnUse" markerWidth="12" markerHeight="12" refX="6" refY="6" orient="auto">`, 길 `M0,0 L12,6 L0,12 z`. 지금은 markerUnits 기본값(strokeWidth)이라 8×8이 선 굵기 4배인 32단위다(그림 폭 340단위).
  - 선: 들어오는 방향의 화면 단위 벡터 `ux = sin(h)`, `uy = −cos(h)`로 만날 곳 점(`sx, sy`)에서 58단위 뒤부터 22단위 앞까지(길이 36). 머리 끝은 만날 곳 16단위 앞이다. `tail`(60m 뒤)은 그림 범위 맞추기에만 남긴다. `P()`가 글자를 돌려주므로 `+sx`처럼 숫자로 바꿔 셈한다.
  - ★: `x = sx`, `y = sy`, `dominant-baseline="central"`로 만날 곳 점 가운데에 두고 맨 마지막에 그린다.
  - 테두리: ★·"나"·"차"·출처에 `stroke="var(--card)" stroke-width="3" stroke-linejoin="round" paint-order="stroke"`.
  - 글자 자리: "나"는 `my + 24`, "차"는 `cy + 22`가 `H - 4`를 넘으면 점 위(`my - 14`, `cy - 12`)에 적는다. 지금은 그림 범위 여유가 20m(몇 단위)뿐이라 태울 사람이 그림 맨 아래 점이면 "나"가 잘린다(README `docs/screenshots/13-page-going.png`, 설계에 없던 세부).
  - 출처: `<text x="W − 8" y="H − 8" text-anchor="end" font-size="12" fill="var(--sub)">© OpenStreetMap</text>`.
  - 그림 맞추기(2-1 사용자 결정 "들어오는 쪽 여유"): 가장자리마다 화면 여유 16단위를 두고, 들어오는 쪽은 그 방향 성분만큼 64×|성분|까지 늘린다. 화살표 시작점(만날 곳에서 58단위 뒤)은 `(sx, sy) − 58 × (ux, uy)`이므로, 그 x 성분이 음수면 왼쪽, 양수면 오른쪽 여유가 커지고, y 성분이 양수면 아래, 음수면 위 여유가 커진다. `sc = min((W − 왼 − 오) ÷ (maxX − minX), (H − 위 − 아래) ÷ (maxY − minY))`, `P = [왼 + (x − minX) × sc, H − 아래 − (y − minY) × sc]`. 그래서 선 시작점, 머리, ★이 늘 그림 안이다. 지금의 20m 여유(108~110행)는 그대로 둔다.
- **Rationale**: 그림은 길(만날 곳 ±150m), 60m 꼬리, 내 위치, 300m 안 차를 모두 담게 줄어든다. 태울 사람 위치에는 한도가 없어(대중교통이면 1km 넘음) 60m가 몇 단위까지 준다. 그래서 꼬리→만날 곳 선의 끝만 당기면 선이 뒤집힐 수 있어 화살표 길이를 축척에서 뗐다. ★ 자리는 spec Assumptions. 대비 --sub/--card 낮 6.13, 밤 7.13.
- **확인**: 스크래치패드 시제품(지금 그림과 고친 그림을 같은 값으로 헤드리스 크롬에 그림)에서 북북동·남남동 진입, 차가 옆, 사람이 만날 곳에 선 경우 모두 ★·"차"·"나"·출처가 보였다. 한 문서에 SVG가 둘이면 같은 marker id 중 첫 것이 쓰여 시제품만 id를 나눴다(페이지는 그림 하나).
- **Alternatives considered**: 기본 markerUnits로 숫자만 줄이기(선 굵기를 바꾸면 다시 커짐), ★은 두고 선만 당기기(북쪽 진입에서 여전히 가림).

## R6. 확인 방법

- **Decision**: 새 단위 테스트를 두지 않는다. 바뀌는 것이 그리기와 문구뿐이고 판단 로직이 없다(헌법 VI 대상 아님). 제목 고르기도 세 갈래 `if`라 캡처로 본다. 기존 160개와 서버 점검은 회귀로 돌린다.
- **캡처**: 차 화면은 `adb exec-out screencap -p`(demo.py `snap`과 같음). 폰 페이지는 1·2차처럼 헤드리스 크롬으로 `?sim=1` 주소를 찍는다(001·002 verification). 실제 `server.py`를 빈 포트로 띄워 시험했고, `--virtual-time-budget=4000`이면 페이지가 상태를 받아 그림까지 그린 뒤 찍힌다. 크롬은 창 폭을 500px 밑으로 줄이지 않아 페이지는 최대 폭 480px로 그려진다.
- **사람 0명**: `demo.py reset`은 앱을 멈추고 `run-as`로 `shared_prefs/people.xml`을 지운 뒤 다시 쓴다. 지우기만 하면 처음 깐 앱과 같다(사람·내 차 없음).
- **서버 점검**: `python3 server/server.py check`는 API 열 항목만 보고 페이지 내용은 보지 않는다. 페이지는 캡처로 본다.
