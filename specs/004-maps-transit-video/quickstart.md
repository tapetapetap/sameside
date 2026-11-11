# Quickstart: 같은편 4차 검증

1·2·3차 quickstart의 준비를 그대로 한다.
- 에뮬레이터 Cold boot
- `adb forward tcp:8088 tcp:8088`
- Virtual Console 페이지 닫기
- 서버 켜기

영상 도구와 결과물은 저장소 밖 `V="/Users/jiwon/Desktop/현대자동차 공모전/영상 도구"`에 둔다. 경로에 빈칸이 있어 명령에서는 `"$V/…"`처럼 따옴표로 감싼다.

## 1. 빌드·테스트·설치

```sh
./gradlew :app:testDebugUnitTest        # 3차까지 120개 + 4차 CardMapTest가 모두 통과
python3 server/server.py check          # 열 항목(서버는 바뀌지 않음)
./gradlew :app:assembleDebug && adb install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant --user 10 kr.skku.sameside android.permission.READ_PHONE_STATE
adb shell pm grant --user 10 kr.skku.sameside android.permission.CALL_PHONE
```

## 2. 화면 확인(장면과 캡처)

모든 장면은 `python3 scripts/demo.py reset` 뒤에 돌린다. 캡처는 다시 녹화한 영상(§4)에서 그 순간의 프레임을 뽑아도 된다.

| 장면 | 기대(contracts/ui.md) |
|---|---|
| `opposite` | 1순위 카드: 왼쪽 지도에 민지 점·이름(초록)·지점까지 점선과 "세븐일레븐 앞" 핀이 모두 안에 있다(가장자리 화살표 없음). 오른쪽 지도에 북쪽으로 올라오는 화살표, 동쪽 길가 띠, "여기 세워요", 띠 오른쪽(동쪽) 인도에 초록 점. 사람 줄 "민지 걸어서 n분"(걷기 아이콘 초록), 다음 줄 "차 n분", "건너기 1분 · 유턴 3분" |
| `transit` | ① 카드가 뜨면 제목 아래 강조 카드 "민지님께 1호선 안을 보냈어요" / "성균관대역 → 화서역 1정거장 · 화서역 약국 앞에서 만나면 6분 빨라요". 카드들 아래 한 줄 없음. ② 받아들인 뒤 "민지님이 1호선 타고 화서역으로 와요" / "성균관대역 → 화서역 1정거장", 카드 "화서역 약국 앞"의 왼쪽 지도에 민지 → 성균관대역 점선, 1호선 실선(역 두 개, "1호선 1정거장"), 화서역 → 지점 점선. 사람 줄 "민지 1호선 1정거장(성균관대역 → 화서역) · n분"(열차 아이콘). ③ "이 자리로 갈게요" 뒤 가는 중 화면 맨 위에 같은 강조 카드("…타고 화서역으로 와요"), 맨 아래 대중교통 한 줄 없음. 거절은 이 장면에 없고 단위 테스트(T015)로 본다 |
| `transit-small` | 강조 카드 없음 |
| `group` | 1순위 "세븐일레븐 앞": 왼쪽 지도에 민지(초록)·준호(주황) 점선, 오른쪽 지도에 서 있을 곳 점 둘(진행 방향으로 나란히). 사람 줄 둘과 "차 n분" |
| `group-transit` | 1순위 "화서역 약국 앞": 왼쪽 지도가 성균관대역 양쪽의 두 사람부터 화서역 약국까지 모두 담는다. 1호선 실선과 역은 한 번만. 사람 줄 "민지 1호선 1정거장(성균관대역 → 화서역) · n분", "준호 …", "근처로 데리러 가면 약 n분". 강조 카드 없음 |
| 가는 중(어느 장면이든 P에서 보일 때) | 지도는 3차 그대로(차 표시). 한 명 대중교통이면 맨 위 강조 카드 |

- **다크 화면 한 장**
  1. `adb shell cmd uimode night yes`
  2. reset 뒤 `SHOT_DIR=$V/shots-004/dark python3 scripts/demo.py group-transit`(카드 캡처 `cards.png`)
  3. `adb shell cmd uimode night no`
  - 1호선 실선과 두 사람의 선이 다크 바탕에서 보이는지 본다.

## 3. `transit` 시간(SC-020)

- 장면 출력의 로그 시각으로 잰다(`log/transit.log`의 "카드 뜸", "받아들임 보냄", "와요 뜸", "가는 중", "취소" 줄).
  - "카드 뜸"부터 "받아들임 보냄"까지 6초 이상
  - "와요 뜸"부터 "가는 중"까지 10초 이상
  - "가는 중"부터 "취소"까지 5초 이상
- 영상에서도 같은 길이로 보이는지 본다.

## 4. 영상 다시 만들기(FR-081~084, contracts/video.md)

```sh
# 4-1 녹화: 20장면, 장면마다 reset 뒤 rec(3차와 같은 run_rec_steps.sh)
bash $V/rec_pickup4.sh            # → $V/videos-pickup4/raw, log
# 4-2 잇기·줄이기(나누지 않음), 음성 목록, 자막 파일, 자막·강조 입히기(contracts/video.md §5)
IDS="opposite transit transit-small handsfree fallback reverse wrongside stop cancel decline nospot ack arrive continue continue-cancel continue-full group group-transit waiting tour"
NOSPLIT=1 VDIR=$V/videos-pickup4 POSTER=end bash $V/process_videos.sh $IDS
python3 $V/extract_voice.py $V/videos-pickup4 kr.skku.sameside   # log/<id>.voice(영상 아래 음성 목록)
python3 $V/make_vtt.py $V/videos-pickup4 kr.skku.sameside        # 4차: 첫 "기어 P 아님"에 ※ 안내
python3 $V/burn.py $V/videos-pickup4                             # 새: 자막 띠·칩, 테두리, 확대 창, 14MB 넘으면 나누기
# 4-3 실행 정보와 페이지
#   $V/videos-pickup4/meta.json에 {"run": "…"} 한 줄(contracts/video.md §5의 6)
python3 $V/build_video_page.py $V/videos-pickup4/page.json       # "burned": true라 data-cues 없음
```

- **확인**
  - 장면마다 `log/<id>.rc`가 0이다. 판정은 `verdicts.json`에 새로 적는다(SC-024: 19 통과 이상).
  - 전화 장면(`handsfree` 등)은 a + 1.5초, b + 2초 프레임을 뽑는다(`ffmpeg -ss … -frames:v 1`). 노란 테두리 안에 초록 알약·마이크가 있고, 확대 창이 보이는지 본다(SC-022).
  - 아무 장면 한 프레임에서 자막 글자 높이가 34px(영상 높이의 4.7%) 안팎인지 본다(SC-021).
  - 주행 장면은 처음 출발 때 "안내" 칩 자막이 4초 보인다.
- **올리기**
  - 같은편 주행 영상 아티팩트(`LC2BoVmFeMjBhrbojPgF4P`)를 같은 주소로 갱신한다.
  - 올리기 전 그 아티팩트를 한 번 읽는다.

## 5. 끝내기

- 에뮬레이터의 `/data/local/tmp/rec`를 지운다.
- 내가 켠 에뮬레이터는 `adb emu kill`로 끈다.
