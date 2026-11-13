# Quickstart: 같은편 6차 확인

작업 위치는 `/Users/jiwon/orca/workspaces/unused-feature-app/car-sameside`(브랜치 `car-sameside`)다. 에뮬레이터 준비, 서버, 콘솔 연결은 README "시연"과 같다. 에뮬레이터를 켤 때마다 `adb forward tcp:8088 tcp:8088`을 하고, 안 써본 기능 시험이 남긴 1:1 분할이 있으면 먼저 푼다(`adb shell input swipe 1706 720 2550 720 800`).

## 1. 단위 테스트와 빌드 (SC-032, SC-036)

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew -q :app:testDebugUnitTest :app:assembleDebug
python3 server/server.py check
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- 기대: 기존 153개와 새 `GlanceTest`가 통과하고, 서버 점검이 통과한다.

## 2. 주행 중 한 줄 카드 (SC-031)

1. 서버를 켜고(`python3 server/server.py &`) `python3 scripts/demo.py reset`.
2. `SHOT_DIR=<캡처 폴더> python3 scripts/demo.py opposite`를 돌리며 D 구간에서 앱 칸을 캡처한다(장면이 가는 중에 내비를 앞으로 낼 수 있으니 캡처 전에 앱을 앞으로).
3. 기대: "주행 중에는 사용할 수 없습니다"가 아니라 한 줄 카드("<n>분" + "민지님 · <상태>"). 지도·버튼·연락 줄이 없다. logcat에 `hideTask.*sameside`가 없다.
4. `handsfree` 장면의 D 구간(알림 "나오라고 알렸어요" → 60초 뒤 "전화를 걸었어요"/"통화 중")과 `chain` 장면의 D 구간(둘째 지점 사람)을 캡처해 한 줄이 상태대로 바뀌는지 본다. `waiting` 장면은 알림 뒤 바로 P로 세우므로 마지막 D 구간의 "나와 있어요"만 본다.
5. 1:1 분할(`adb shell input swipe 2547 720 1500 720 800`)에서 D 한 줄 카드를 캡처하고 분할을 푼다.
6. P에서 "+ 사람 추가" 창을 열어 둔 채 콘솔로 기어를 D로 → 창이 닫히고 한 줄 카드.
7. P에서 "+ 사람 추가" 창을 열어 둔 채 `adb shell cmd uimode night yes` → 기어를 D로 → 앱이 멈추지 않고 한 줄 카드(`adb logcat -d | grep -c FATAL`이 0).

## 3. 정차 화면 글자와 누르는 칸 (SC-033, SC-034)

1. 첫 화면, "여러 명 함께" 켬, 만날 곳(`group`·`chain` 장면의 P), 가는 중(P에서 기다림), 만남 화면을 낮·밤으로 캡처한다.
2. 각 화면 `adb shell uiautomator dump`의 `clickable="true"` bounds가 contracts §2의 누르는 칸 목록에서 모두 76×76 이상인지 본다.
3. 코드 검사: `grep -nE '\b(1[0-9]|2[0-3])f\b' app/src/main/java/kr/skku/sameside/Screens.kt app/src/main/java/kr/skku/sameside/Ui.kt`로 후보를 모두 뽑고 글자 크기 인자인 것만 본다(반지름·간격 인자는 무시). 남는 글자 크기가 없어야 한다. `Views.kt`의 `sp(..f)`는 14(출처)와 20 이상만 있다.
4. 지도 이름표: `group`·`chain` 장면의 만날 곳 지도에서 이름표가 겹쳐 그려지지 않는다(역·노선 글이 빠질 수 있다).

## 4. 취소, 뒤로, 낮·밤 (SC-035)

| 시작 | 동작 | 기대 |
|---|---|---|
| 링크 상태(P) | "픽업 취소" 한 번 | 버튼이 "한 번 더 누르면 취소해요", 픽업 그대로 |
| 그 상태 | 4초 안에 다시 누름 | "픽업을 취소했어요" |
| 링크 상태(P) | 한 번 누르고 5초 기다림 | 버튼이 "픽업 취소"로 돌아옴 |
| 링크 상태(P) | 한 번 누르고 바로 기어 D → 2초 안에 P | 버튼이 "픽업 취소"(확인이 지워짐) |
| "여러 명 함께" 켬 + 두 명 고름 | `adb shell input keyevent 4` | 고르기 꺼짐, 앱 남음 |
| 고르기 꺼짐 | 뒤로 | 앱을 나감 |
| "여러 명 함께" 켬 + 두 명 고름 | `adb shell cmd uimode night yes` | 고르기 켜짐, 같은 두 명(차례 그대로) |

## 5. 배치와 문구 (US5)

- `chain` 장면의 만날 곳 화면: 차례로 태우기 카드가 제목 바로 아래, 둘째부터 만날 곳 카드에 지도·이유가 없다.
- 가는 중(P): "내비에 만날 곳을 넣었어요", 차례 카드 "차례로 태우는 중 · 2곳 중 1번째", 알림 전 연락 줄 "가까워지면 알려요".

## 6. 회귀와 점검

- 기준 로그: 21장면 모두 `/Users/jiwon/Desktop/현대자동차 공모전/영상 도구/videos-pickup4/log/<장면>.log`(4·5차 녹화 로그, `chain.log` 포함)다. 지점, 알림·전화 시각(장면 시작 기준), 탑승 줄을 견준다.
- 시연 스크립트의 "픽업 취소"를 두 번 누르기로 고친 뒤, 1~5차 장면 21개(`opposite`, `transit`, `transit-small`, `handsfree`, `fallback`, `reverse`, `wrongside`, `stop`, `cancel`, `decline`, `nospot`, `tour`, `ack`, `arrive`, `continue`, `continue-cancel`, `continue-full`, `group`, `group-transit`, `waiting`, `chain`)가 5차 로그와 같은 판단(지점, 알림·전화 시각, 탑승)으로 끝난다.
- `/impeccable critique`와 `audit`를 다시 돌려 점수를 적는다(SC-037).
- 캡처는 `/Users/jiwon/Desktop/현대자동차 공모전/화면 점검 1113/고친 뒤/같은편/`에 둔다.
