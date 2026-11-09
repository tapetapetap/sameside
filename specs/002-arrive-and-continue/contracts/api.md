# Contract: 서버 API 추가 (`server/server.py`)

1차 `specs/001-sameside-pickup/contracts/api.md`에 더하는 것만 적는다. 엔드포인트·코드·끝 상태 처리·주기는 1차 그대로다.

## 필드 추가

| 쪽 | 필드 | 형식 | 처음 값 | 타입이 틀리면 |
|---|---|---|---|---|
| 차 `PUT …/car` | `arrived` | Boolean | false | 400 |
| 태울 사람 `PUT …/passenger` | `ack` | Boolean | false | 400 |

- `spots`는 1차처럼 목록인지만 본다. 각 지점에 더해지는 `fasterMin`(정수 분 또는 null), `standLine`은 그대로 저장해 돌려준다.
- `GET /api/pickups/{id}`의 `passenger`에 `ack`가 들어간다.
- 끝 상태면 1차처럼 `status`·`name`만 남는다. 새 필드도 지워진다.

## 자체 점검에 더하는 것

3번 항목 끝에 하나를 더한다: `PUT …/passenger {"ack": true}` → 204, 그 뒤 `GET`의 `passenger.ack`가 true다. `{"ack": "yes"}`는 400이다.
