# Quickstart: 같은편 7차 확인

작업 위치는 `/Users/jiwon/orca/workspaces/unused-feature-app/car-sameside`(브랜치 `car-sameside`)다. 아래 명령은 이 폴더에서 돌린다.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
OUT="/Users/jiwon/Desktop/현대자동차 공모전/화면 점검 1115/같은편"; mkdir -p "$OUT"
```

## 1. 빌드와 점검 (SC-043)

```sh
./gradlew -q :app:testDebugUnitTest :app:assembleDebug
python3 server/server.py check
```

- 기대: 단위 테스트 160개 통과, "check: 열 항목 통과". 서버 점검은 페이지 내용을 보지 않는다(research R6).

## 2. 에뮬레이터 준비 — 세 앱이 하나를 함께 쓴다

- 안 써본 기능·DRIVE DNA 작업이 에뮬레이터를 쓰는 중이면 끝날 때까지 기다린다. `demo.py reset`은 다른 두 앱을 강제로 멈추므로 그 사이에 돌리지 않는다.
- 꺼져 있으면 `$HOME/Library/Android/sdk/emulator/emulator -avd pleos -no-snapshot &`로 켜고, 이 작업이 켰다고 적어 둔다.
- 켤 때마다 `$ADB forward tcp:8088 tcp:8088`. 남은 1:1 분할은 `$ADB shell input swipe 1706 720 2550 720 800`으로 푼다.
- `python3 server/server.py &`, `$ADB install -r -g app/build/outputs/apk/debug/app-debug.apk`, `python3 scripts/demo.py reset`(다섯 명·내 차, P, 와이파이). 브라우저의 Virtual Console 페이지(127.0.0.1:8088)는 닫는다.

## 3. 첫 화면 (SC-038, SC-039)

1. 사람 0명(처음 깐 앱과 같음, research R6):

   ```sh
   $ADB shell am force-stop --user 10 kr.skku.sameside
   $ADB shell "run-as kr.skku.sameside --user 10 sh -c 'rm -f shared_prefs/people.xml'"
   $ADB shell am start --user 10 -n kr.skku.sameside/.MainActivity; sleep 3
   $ADB exec-out screencap -p > "$OUT/01 사람 0명 첫 화면.png"
   $ADB shell input swipe 2547 720 1500 720 800; sleep 3      # 1:1 분할(앱 왼쪽 세로 칸)
   $ADB exec-out screencap -p > "$OUT/02 사람 0명 첫 화면 - 1대1 세로.png"
   $ADB shell input swipe 1706 720 2550 720 800; sleep 2      # 분할 풀기
   ```

   기대: "같은편", 제목 "먼저 데리러 갈 사람을 추가해 주세요", 1단계 "사람 추가하기"와 `"+ 사람 추가"로 이름과 번호를 넣어요`. 세로에서도 "왼쪽"이 없다.
2. 사람 5명: `python3 scripts/demo.py reset` 뒤 위 캡처 줄들(`am start`부터)을 이름만 바꿔 `03 사람 5명 첫 화면.png`, `04 사람 5명 첫 화면 - 1대1 세로.png`. 기대: 제목 "누구를 데리러 갈까요?", 1단계 "사람 고르기".
3. 서버 끔과 링크 만드는 중: 서버를 끄고(`pkill -f "server/server.py"`) 민지를 누른다.

   ```sh
   python3 -c "import sys; sys.path.insert(0, 'scripts'); import demo; demo.tap('민지')"; sleep 3
   $ADB exec-out screencap -p > "$OUT/05 서버 끔 - 링크 만드는 중.png"
   ```

   기대: 맨 위 빨간 띠 "서버에 연결하지 못했어요 · 2초마다 다시 시도해요", 제목 "링크를 만들고 있어요", 단계 카드 셋. 서버를 다시 켜고(`python3 server/server.py &`) `python3 scripts/demo.py reset`.

## 4. `chain` 장면 — 지도 둘과 폰 페이지 (SC-040~042)

```sh
python3 scripts/demo.py reset
SHOT_DIR="$OUT" python3 scripts/demo.py chain
```

- `chain-cards.png`(만날 곳): 1순위 카드의 "차 세울 곳"과 "사람과 이동 경로" 오른쪽 아래 "© OpenStreetMap"이 둥근 바탕 위에서 끝까지 읽힌다. 대비는 research R3 표.
- `chain-next.png`(첫 탑승 뒤 가는 중, P): 지도의 준호 점·점선이 바로 위 차례 그림의 준호 노드와 같은 색(낮 갈색)이고 이름은 text1(낮 검정)이다.
- `transit-going.png`(`SHOT_DIR="$OUT" python3 scripts/demo.py transit`, 한 명 픽업, P): 민지가 1호선 위라 지도 밖이다. 가장자리 화살표가 민지 색(낮 avatar3_line #1D8348)이고 이름표는 text1이다. 묶음 화살표 색은 보여 줄 장면이 없어 확인 범위 밖이다.
- 폰 페이지: 장면 시작 때 로그에 "[chain] 민지 화면: …", "[chain] 준호 화면: …" 주소가 찍힌다. "넘어간 뒤 화면:" 줄이 나오면(그 뒤 차가 약 12초 서 있고, 두 페이지는 만남까지 그림이 남는다) 두 주소를 찍는다.

  ```sh
  CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
  "$CHROME" --headless --user-data-dir="$(mktemp -d)" --window-size=500,900 --force-device-scale-factor=2 \
    --virtual-time-budget=4000 --screenshot="$OUT/06 폰 페이지 - 민지(차 옆).png" "<민지 주소>"
  "$CHROME" --headless --user-data-dir="$(mktemp -d)" --window-size=500,900 --force-device-scale-factor=2 \
    --virtual-time-budget=4000 --screenshot="$OUT/07 폰 페이지 - 준호(북쪽에서 들어옴).png" "<준호 주소>"
  ```

  기대: ★이 만날 곳 점 가운데에 보이고 진입 화살표는 그 앞에서 끝난다. 머리는 ★보다 작다. 민지 페이지의 "차"와 두 페이지의 "나"가 선·네모 위에서도 읽힌다. 그림 오른쪽 아래에 "© OpenStreetMap"이 있다. 고치기 전 모습은 5차 캡처 3(`같은편 5차 캡처/3 태울 사람 페이지 …png`)이다.

## 5. 마무리

- 분할이 남았으면 풀고 `python3 scripts/demo.py reset`으로 다섯 명을 되돌린다. 띄운 서버는 끈다.
- 이 작업이 에뮬레이터를 켰고 다른 앱이 쓰지 않으면 `$ADB emu kill`. 쓰는 중이면 두고 알린다.
- 캡처 01~07과 `chain-cards.png`·`chain-next.png`로 `/impeccable audit`을 돌린다.
