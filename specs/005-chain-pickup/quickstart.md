# Quickstart: 같은편 5차 확인

작업 위치는 `/Users/jiwon/orca/workspaces/unused-feature-app/pickup`(브랜치 `pickup-chain`)다. 에뮬레이터·중계 서버·콘솔 준비는 README "시연"과 같다.

## 1. 단위 테스트와 빌드

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew -q :app:testDebugUnitTest :app:assembleDebug
```

- 기대: 1~4차 133개와 새 `ChainTest`가 모두 통과한다(SC-029).

## 2. 시연 장면 `chain`(SC-025~SC-028)

```sh
python3 scripts/demo.py reset
SHOT_DIR=<scratchpad>/chain python3 scripts/demo.py chain
```

| 때 | 기대 |
|---|---|
| 시작 | 내비에 원래 목적지 "집"을 넣고 기다린다(경유지 방식이 되게) |
| 두 사람 위치 공유 뒤 만날 곳 | 함께 모이는 곳 카드 아래 차례로 카드. "민지 → 준호 차례로 · 약 10분", 구간 그림에 노드 2개와 구간 시간 3개(목적지 포함), 지점 목록 두 줄(카드까지 밀어 올려 `chain-cards.png`) |
| "차례로 태울게요" | 가는 중, "차례로 태우는 중 · 1/2"와 구간 그림. 로그 "내비: 경유지 방식, 원래 목적지 집" |
| 확정 직후 서버 | 준호 기록 `chosen` = 1, `etaSec` > 민지 기록 `etaSec` |
| 첫 지점 탑승(P) | 음성 "민지님이 탔어요. 다음은 준호님, 화서역 4번 출구 앞이에요". 내비 경유지가 둘째 지점으로. 정차 중 화면에서 민지 노드 체크, 준호 노드 테두리, 첫 구간 시간 없음(`chain-next.png`) |
| 둘째 지점 탑승 | 만남, 탑승 15초 뒤 내비 ("집", 경유지 없음). 두 서버 기록 met |

## 3. 1~4차 장면

- `python3 scripts/demo.py`의 20장면 가운데 `opposite`, `group`, `group-transit`, `waiting`을 돌려 4차 판정과 같은지 본다. 시간이 되면 20장면 모두(SC-030). `group`·`group-transit`은 두 사람 1순위가 같거나 계획이 없으면 차례로 카드가 없다.
