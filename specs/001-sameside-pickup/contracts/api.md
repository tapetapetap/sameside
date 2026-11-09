# Contract: 중계 서버 HTTP API (`server/server.py`)

## 기본

- **주소**
  - 서버는 `127.0.0.1:8090`에서만 연다.
  - 차 앱(에뮬레이터)은 `http://10.0.2.2:8090`을 쓴다.
  - 페이지는 같은 서버에서 열리므로 상대 경로를 쓴다.
  - 1차 링크는 `http://localhost:8090/p/<id>`이고, 같은 맥의 브라우저에서 연다(spec FR-005).
- **형식**: 요청과 응답은 JSON(UTF-8)이다. 요청 본문은 32KB를 넘으면 413이다.
- **식별자**
  - `id`: `secrets.token_urlsafe(9)`. 링크와 조회에 쓴다.
  - `key`: `secrets.token_urlsafe(18)`. 차만 알고, 차 쪽 쓰기에 쓴다.
- **저장**
  - 메모리 dict 하나와 lock 하나로 둔다. 디스크에는 쓰지 않는다.
  - 로그에는 위치를 남기지 않는다. 경로와 상태 코드만 남긴다.
- **시계**: 시간은 `now()` 함수 하나로 읽는다. 자체 점검에서 이 함수를 바꿔 끼워 10분·30분 규칙을 확인한다.
- **끝 상태 처리**
  - 끝 상태는 `met`, `canceled`, `stopped`, `expired`다.
  - 끝 상태가 되면 그 픽업에서 `status`와 `name`만 남기고 나머지는 지운다. 위치, 지점, 차 정보, passenger 값이 모두 지워진다(spec FR-028).
  - 10분 뒤에는 통째로 지운다.
  - 끝 상태의 픽업에 오는 쓰기(`PUT …/car`, `PUT …/passenger`)는 모두 409다. 그래서 끝난 상태가 되돌아가지 않는다.
- **안전망**: 차의 요청이 30분 넘게 없으면 픽업을 통째로 지운다.
  - 차는 `GET`에도 `X-Car-Key`를 붙인다. 서버는 key가 맞는 `GET`·`PUT`을 모두 차의 요청으로 센다.
  - 그래서 차가 2초마다 조회하는 동안에는 링크 대기(최대 60분, FR-027)도 지워지지 않는다.
- **청소**: 10분·30분 규칙은 요청이 올 때도 검사하고, 60초마다 도는 청소 스레드도 검사한다. 아무도 다시 조회하지 않는 픽업도 남지 않게 하기 위해서다.

## 엔드포인트

### `POST /api/pickups` (차)

```json
{"name": "민지", "car": {"model": "그랜저", "color": "흰색", "plate": "12가 3456", "phone": "01098765432"}}
```
→ `201 {"id": "...", "key": "..."}`
- 만든 직후 값: `status: "link"`, passenger는 `consent: null`, `lat`·`lon`: null, `ready`·`late`·`boarded`: false, `transit`: null
- `name`은 1~10자다.
- 차 정보는 비어 있어도 된다.
- 이것을 만족하지 않으면 400이다.

### `PUT /api/pickups/{id}/car` (차)

- 헤더 `X-Car-Key: <key>`가 필요하다.
- 응답 코드

  | 경우 | 코드 |
  |---|---|
  | key가 틀림 | 403 |
  | id가 없음 | 404 |
  | 이미 끝 상태 | 409 |
  | `status`가 data-model §7의 여덟 개 중 하나가 아님 | 400 |
  | 성공 | 204 |

- 본문은 data-model §8의 필드다. 주는 필드만 바꾸고, 빠진 필드는 그대로 둔다.
- 끝 상태 값을 받으면 위의 "끝 상태 처리"를 한다.
- 차는 409를 받으면 바로 `GET`을 한 번 더 해서 끝 상태(예: `stopped`)를 읽는다. `GET`이 404면 끝난 것으로 본다(data-model §7 `Gone`).

### `PUT /api/pickups/{id}/passenger` (태울 사람 페이지)

```json
{"consent": true, "lat": 37.3001, "lon": 126.9712, "acc": 12, "ready": false, "late": false, "boarded": false, "stop": false, "transit": "accept"}
```
- 모든 필드는 선택이다. 주는 필드만 바꾼다.
- `acc`(위치 정확도)는 받고 버린다. 모르는 필드는 무시한다. 아는 필드의 타입이 틀리면 400이다(car 쪽도 같다).
- **처리 순서**: 한 요청 안에서는 `consent`를 먼저 처리한 뒤 나머지를 처리한다. 그래서 `consent: true`와 위치가 같이 오면 위치를 받는다.
- **동의**
  - `consent: false`도 받는다. 페이지는 "괜찮아요"를 누르면 이것을 보낸다.
  - consent가 false일 때 오는 `lat`·`lon`은 무시하고, 이미 있던 위치도 지운다.
- **위치 범위**: 위도 −90~90, 경도 −180~180이다. 벗어나면 400이다.
- `transit`은 `"accept"` 또는 `"decline"`이다.
- `stop: true`면 상태를 `stopped`로 바꾸고 끝 상태 처리를 한다.
- → `204`
- 이미 끝 상태면 409, id가 없으면 404다.

### `GET /api/pickups/{id}` (차, 페이지)

→ `200 {"status": ..., "name": ..., "car": {...}, "pos": {...}, "etaSec": ..., "walkSec": ..., "spots": [...], "chosen": ..., "offer": ..., "outNow": ..., "crossOver": ..., "wrongSide": ..., "passenger": {"consent", "lat", "lon", "ready", "late", "boarded", "transit"}}`

- 페이지와 차가 같은 응답을 본다. `key`는 응답에 넣지 않는다.
- 태울 사람이 공유를 끈 것은 `status: "stopped"`로 알 수 있다. passenger에 `stop` 필드는 없다.
- 끝 상태면 `{"status": ..., "name": ...}`만 준다.
- 없으면 404다.

### `GET /p/{id}` (페이지)

- `server/page.html`을 그대로 준다(200).
- 페이지가 `GET /api/pickups/{id}`로 상태를 읽고, 404면 "끝난 픽업이에요"를 보인다.
- `?sim=1`이 붙으면 페이지는 브라우저 위치를 쓰지 않는다. 이때 위치는 시연 스크립트가 같은 `PUT …/passenger`로 넣는다.

### `GET /photo/{file}`

- `server/photo/`의 시연 지역 사진을 준다.
- 파일 이름은 `[a-z0-9_-]+\.jpg`만 허용하고, 아니면 404다. 경로를 거슬러 올라가는 이름(`../` 등)도 404다.

## 주기

- 차와 페이지는 2초마다 `GET`한다.
- 차는 두 경우에 `PUT`한다.
  - 상태가 바뀌었을 때
  - 위치가 왔을 때(`spots`·`nospot`·`going`, 1초에 한 번 이하)
- 차의 `PUT`은 한 스레드에서 순서대로 보낸다.
- 실패한 `PUT`은 다음 주기에 그때의 최신 상태로 다시 보낸다. 끝 상태는 받아들여지거나(204), 409·404가 올 때까지 보낸다(tasks T011).

## 자체 점검 `python3 server/server.py check`

assert로 확인하고, 실패하면 0이 아닌 값으로 끝난다. 확인하는 것은 아래와 같다.

1. 만들기 → `id`, `key`가 나온다. 이름이 비면 400이다. 만든 직후 `GET`이 `status: "link"`, `consent: null`이다.
2. 틀린 key로 `PUT …/car` → 403. 잘못된 `status` → 400. 32KB 넘는 본문 → 413
3. 동의 전 위치는 무시된다. `consent: true`와 위치가 한 요청에 오면 저장된다. 범위 밖 위도 → 400. `consent: false`면 위치가 지워진다.
4. `status: "met"` → `GET`에 `status`·`name`만 있다.
5. 끝난 픽업에 `PUT …/passenger`·`PUT …/car` → 둘 다 409
6. `stop: true` → `GET`의 status가 `stopped`이고, 그 뒤 차 `PUT` → 409
7. 시계를 10분 넘게 돌리고 청소 함수를 직접 부르면 끝난 픽업이 404다. 30분 넘게 돌리면 차 요청이 없던 진행 중 픽업이 404다. 같은 시간 동안 key를 붙인 `GET`이 있었던 픽업은 남아 있다.
8. `/photo/../server.py`, `/photo/a.png` → 404
9. `Content-Length`가 음수면 413, 숫자가 아니면 400, 32KB 안의 너무 깊은 JSON → 400(코드 리뷰 반영). 본문을 보내지 않고 붙잡는 연결은 10초 뒤 끊는다
