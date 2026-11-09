# Quickstart: 같은편 검증

## 준비

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew :app:testDebugUnitTest :app:assembleDebug
python3 server/server.py check            # 서버 자체 점검
python3 server/server.py &                 # 127.0.0.1:8090, 다른 터미널에 띄워 둔다
adb install -r -g app/build/outputs/apk/debug/app-debug.apk   # -g: CALL_PHONE 등 권한 허용
adb forward tcp:8088 tcp:8088              # Virtual Console
python3 scripts/demo.py reset
```

**`reset`이 하는 일**
1. 다른 두 앱(`kr.skku.unusedfeature`, `kr.skku.drivedna`)을 멈춘다(`am force-stop`). 시연 중에 그 앱들이 음성으로 끼어들지 않게 한다.
2. 에뮬레이터 와이파이가 끊겨 있으면 차 설정 화면을 열어 `AndroidWifi`를 누른다(research R11).
3. 콘솔 상태를 맞춘다.
   - 기어 Park, 문 4개 닫힘
   - 앱이 뜬 뒤 조수석을 빈 좌석으로 넣는다. 에뮬레이터는 콘솔 값의 반대가 앱에 오므로 스크립트가 맞춰 쓴다(research R5).
   - GPS를 `opposite` 장면의 `car` 좌표에 둔다.
4. 앱을 멈추고(`am force-stop --user 10 kr.skku.sameside`), `run-as`로 `shared_prefs/people.xml`만 지운다. `pm clear`는 쓰지 않는다. 런타임 권한까지 되돌려 `CALL_PHONE`이 사라지기 때문이다(research R13).
5. `run-as`로 `shared_prefs/people.xml`에 아래 값을 쓴다.
   - 사람: "민지"(01012345678)
   - 차: "그랜저 · 흰색 · 12가 3456 · 01098765432"
6. 앱을 연다.

**함께 알아둘 것**
- 브라우저의 Virtual Console 페이지는 닫는다. 열려 있으면 스크립트가 연결할 때 페이지 상태가 다시 들어간다(DRIVE DNA와 같음).
- 태울 사람 화면은 스크립트가 출력하는 `http://localhost:8090/p/<id>?sim=1`을 맥 브라우저로 열어 본다.
  - 모바일 크기로 보려면 개발자 도구의 기기 모드를 쓴다.
  - 열면 위의 "소리 켜기"를 한 번 누른다. 브라우저는 누르기 전에는 소리를 내지 않는다(contracts/ui.md §3).
- 내비는 에뮬레이터 GPS를 따라오지 않는다(research R1). 내비 화면의 남은 시간은 시연 판단과 관계없다. 앱은 자기 추정을 쓴다.

**확인 명령**
- 음성: `adb logcat -d | grep "doSpeak(kr.skku.sameside"`
- 전화: `adb shell dumpsys telecom | grep "state="`. 끊을 때는 `adb shell input keyevent KEYCODE_ENDCALL`
- 서버 상태: `curl -s localhost:8090/api/pickups/<id>`
- 화면 글자: `adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"'`

## 시나리오 (`python3 scripts/demo.py <이름>`)

**장면 공통 규칙**(혼자 돌릴 때와 `tour`로 이어 돌릴 때 같다)
- 시작할 때 자기 `car` 좌표로 GPS를 옮기고, 기어를 Park로 둔다.
- 끝날 때 아래를 한다.
  - 걸린 통화가 있으면 끊는다(`KEYCODE_ENDCALL`).
  - 끝 화면의 "확인"을 눌러 "픽업 없음"으로 돌린다.
  - 조수석을 다시 빈 좌석으로 넣는다(research R5).

스크립트가 하는 일
- 화면 조작: 덤프에서 글자를 찾아 누른다. 사람 줄 "민지", "이 자리로 갈게요".
- 링크 id: 화면 글자에서 읽는다.
- 태울 사람 조작: 위치와 버튼을 `PUT /api/pickups/<id>/passenger`로 넣는다.
- 주행: 기어 Drive를 넣고, 정해 둔 길을 1초마다 `adb emu geo fix`로 시속 30km에 맞춰 움직인다.

| 이름 | 하는 일 | 기대 결과 |
|---|---|---|
| `opposite` (반대편 함정) | ① 민지는 차가 오는 쪽의 **반대편** 출구 앞에 서 있다. ② 픽업을 시작하고 동의와 위치를 넣는다. ③ 1순위를 확인하고 달린다. ④ 지점에 서서 조수석 문을 열었다 닫고, 조수석에 앉은 것으로 넣는다(research R5) | 1순위가 차가 오는 쪽 출구이고, 이유에 "횡단보도 한 번"이 있다. 걸러진 후보(정류장 10m 안)는 목록에 없다. 주행 중 화면 조작 0번. 음성은 차례로 자동 전화 줄 → "오른쪽 ○○ 앞이에요" → "민지님이 탔어요". 두 화면 모두 "만났어요" |
| `transit` (두 정거장) | 민지는 멀리, 대중교통 안이 이득인 자리에 있다. 동의 뒤 페이지에 제안이 뜨면 `transit: accept`를 넣는다 | 페이지에 "…n분 빨리 만나요"(n ≥ 5)가 보인다. 받아들이면 차 화면의 1순위가 정류장 근처 지점으로 바뀐다. 주행 중이면 음성 "민지님이 ○○ 타고 ○○로 와요"가 나온다 |
| `transit-small` | 대중교통 이득이 5분 이하인 자리 | 제안이 뜨지 않는다(SC-007) |
| `handsfree` (손 안 대는 도착) | 민지가 걷는 시간이 2분인 자리에 있다. 버튼은 누르지 않는다 | 실제 남은 시간이 3분(2 + 1)에 닿은 뒤 30초 안에. 실제 남은 시간은 스크립트가 경로와 속도로 계산해 그 시각을 출력한다(SC-004) "민지님께 전화 걸게요"와 통화(telecom ACTIVE)가 있다. 페이지에 "지금 나오세요" 띠와 소리가 있다. 전화는 한 번만 있다 |
| `reverse` (반대 방향 진입) | 1순위로 정한 뒤, 일부러 반대 방향에서 지점에 들어간다 | 150m에서 "민지님이 길을 건너 오른쪽으로 와요"가 나오고, 페이지에 "차가 길 건너편으로 와요…" 띠가 뜬다. U턴 안내는 없다 |
| `wrongside` | 남은 3분 안에 민지가 지점 길의 반대쪽 길가로 간다(`passenger2`) | 페이지에 "앞 횡단보도를 건너 ○○ 앞으로 와 주세요"가 뜬다. `passenger2`로 옮긴 뒤 새로 나온 운전자 음성은 0줄이다 |
| `stop` | 진행 중 페이지에서 `stop: true`를 넣는다 | 차 화면 "민지님이 위치 공유를 껐어요". 서버 `GET`에 위치가 없다 |
| `cancel` | 지점이 나온 뒤 P에서 "픽업 취소"를 누른다(`cancel_at`) | 페이지 "픽업이 취소됐어요". 서버 `GET`에 `status`·`name`만 있다(SC-006) |
| `decline` | 페이지에 `consent: false`를 넣는다 | 차 화면 "민지님이 위치를 공유하지 않았어요". 지점을 고르지 않는다 |
| `nospot` | 조건에 맞는 자리가 걷는 거리 안에 없는 위치에 민지가 있다 | 두 화면 모두 "가까운 곳에 같은 편 자리가 없어요". 위치 공유는 이어진다 |
| `fallback` | `pm revoke --user 10 kr.skku.sameside android.permission.CALL_PHONE` 뒤 `handsfree`와 같게 달린다 | 운전자 음성 "민지님 폰으로 알렸어요"와 페이지 "지금 나오세요" 띠. 통화는 없다. 끝나면 `pm grant`로 권한을 되돌린다 |
| `tour` | `reset` → `opposite` → `transit` → `handsfree` | 각 장면이 위와 같다. `transit`은 받아들인 것을 확인한 뒤 P에서 "픽업 취소"로 끝낸다. 끝에 음성 로그를 출력한다 |

- `expired`(60분·도착 예정 + 30분)는 시간이 오래 걸려 장면으로 돌리지 않는다. 단위 테스트(tasks T034)로 확인한다.

## 통과 기준

- `./gradlew :app:testDebugUnitTest` 실패 0
- `python3 server/server.py check` 통과
- 위 시나리오의 기대 결과
  - 주행 중 음성은 장면마다 5번 이하(SC-003)
  - `logcat -b crash`에 이 앱 0건
