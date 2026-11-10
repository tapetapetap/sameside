# Quickstart: 같은편 3차 검증

1·2차 quickstart의 준비(에뮬레이터 Cold boot, `adb forward tcp:8088 tcp:8088`, Virtual Console 페이지 닫기, 서버 켜기)를 그대로 한다.

## 1. 빌드·설치·권한

```sh
./gradlew :app:testDebugUnitTest          # 1·2차 95개 + 3차 GroupTest
python3 server/server.py check            # 열 항목
./gradlew :app:assembleDebug && adb install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant --user 10 kr.skku.sameside android.permission.READ_PHONE_STATE
```

## 2. 장면

모든 장면은 `python3 scripts/demo.py reset` 뒤에 돌린다. 태울 사람 페이지는 장면이 찍는 `?sim=1` 주소로 연다.

| 장면 | 기대 |
|---|---|
| `reset` | 사람 칸에 다섯 명(민지, 준호, 서연, 할머니 "가까이만", 도윤)과 첫 글자 원 |
| `group` | 1순위 카드 "세븐일레븐 앞". 사람 줄 "민지 걸어서 3분", "준호 걸어서 2분", "차 2분". 건너기·유턴 줄과 "근처로 데리러 가면" 줄 모두 없음. 민지 페이지에만 "…빨라요"(민지만 건넘, 유턴이 더 길 때). 확정 뒤 두 페이지에 "함께 타요" 칩. 지점에서 문 → 조수석 → 두 사람 "탔어요" → "민지님, 준호님이 탔어요", 두 기록 met |
| `group-transit` | 1순위 카드 "화서역 약국 앞", 두 사람 모두 기차 아이콘 "1호선", "근처로 데리러 가면 약 21분". 확정 뒤 두 페이지에 대중교통 안내. 둘 다 "알겠어요" → 운전자 "민지님, 준호님이 1호선 타고 화서역으로 와요". 주행 끝에 "민지님, 준호님이 탔어요" |
| `handsfree` | 판단 결과는 2차와 같다. 주행 중이라 앱(연락 줄)은 가려진다. 페이지 전화 띠가 맥박친다 |
| `waiting` | 알림이 나가면 길가에 세운다(P). 연락 줄이 알림 → 줄어드는 호박색 링과 남은 초 → 60초 뒤 전화하면 통화 중 초록 물결과 m:ss → 페이지 "나와 있어요"와 통화 끊기 뒤 "나와 있어요". 그 뒤 마저 가서 탄다. `SHOT_DIR`을 주면 세 순간을 캡처한다 |
| 1·2차 16장면 + `tour` | 2차 verification과 같은 결과(SC-015). 카드에 지도가 보인다 |

`group`·`group-transit`의 주행 중 음성은 장면 출력의 "운전자 음성"으로 센다(5줄 이하).

## 3. 화면 확인(캡처)

- 카드 지도: `opposite` 1순위 카드에서 핀이 세븐일레븐 동쪽 길가에 있다. 화살표가 북쪽(서부로 북쪽 방향)이다. 민지 점과 점선이 있다.
- 가는 중: 큰 남은 분, 막대, 지도의 차, 연락 줄
- 만남: 이름과 걸린 분. `group-transit`이면 "근처로 데리러 갔다면 약 21분"
- 헤드업: `handsfree` 녹화에서 전화 시점에 "민지님께 전화 중" 알림이 보이는지 본다(R23). 안 보이면 FR-068을 빼고 코드에서 지운다. → 11/11 녹화에서 보이지 않아 뺐다(verification 3절).
