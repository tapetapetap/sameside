# API 계약 바뀜: 같은편 3차

1·2차 `contracts/api.md`에 더한다. 엔드포인트, 인증(`X-Car-Key`), 끝 처리는 그대로다. 함께 태우기는 사람마다 기록을 하나씩 만든다(POST를 사람 수만큼).

## 차 → 서버 `PUT /api/pickups/{id}/car`의 새 필드

| 필드 | 타입 | 뜻 |
|---|---|---|
| `group` | list | 함께 타는 다른 사람. 원소는 `{"name": str, "mode": str, "aboard": bool, "out": bool}`이다. `mode`는 `"walk"`이거나 노선 이름이다. 한 명 픽업은 `[]`. 위치는 담지 않는다(원칙 III) |
| `plan` | dict 또는 null | 이 사람이 고른 지점까지 가는 방법. `{"mode":"walk"}` 또는 `{"mode":"transit","line":str,"stops":int,"alightName":str,"min":int}`. 가는 중에만 값이 있다 |
| `calling` | bool | 운전자가 이 사람에게 전화하고 있다(data-model §5) |

- 서버는 아는 필드의 타입만 검사하고(1차 `typed`), 모르는 필드는 버린다. `group`·`plan`의 안쪽 값은 그대로 저장하고, 페이지가 `esc()`로 넣는다.
- 끝 상태로 바뀌면 1차처럼 `status`·`name`만 남는다(FR-028). 새 필드도 지워진다.
- 자체 점검 열째 항목
  - 새 필드 셋을 올리면 204
  - 타입이 틀리면 400(`group`이 문자열, `calling`이 문자열)
  - 끝 상태 뒤 GET에 새 필드가 없다

## 태울 사람 → 서버

바뀌지 않는다. 대중교통 안내의 "알겠어요"는 1차 `transit: "accept"`를 쓴다. 페이지 "탔어요"는 1차 `boarded: true`다.
