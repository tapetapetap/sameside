# Research: 같은편 픽업

에뮬레이터 확인은 모두 2026-11-09에 했다.
- 에뮬레이터: Pleos Connect Emulator v2.1.0, AVD `pleos`, 콜드 부팅
- 방법: 받은 값을 로그로만 남기는 시험판 `kr.skku.probe`을 설치하고, Virtual Console·`adb emu`로 값을 바꿔 확인했다.
- 시험판은 스크래치 폴더에 있고 저장소에는 넣지 않는다.

## R1. 기본 내비 (NaviHelper 3.0.5)

- **Decision**: 기본 내비는 고른 지점을 목적지로, 진입 경유지를 첫 경유지로 넘기는 데만 쓴다. 남은 시간과 차 위치는 내비에서 받지 않는다(R2, R3).
- **API**: `ai.pleos.playground:NaviHelper:3.0.5`
  - 순서: `NaviHelper(context)` → `initialize(onSuccess, onError)` → `addListener(NaviHelperEventListener)`
  - 목적지: `requestRoute(RouteInfo(latitude=…, longitude=…, displayLatitude=null, displayLongitude=null, poiName=…, poiId="", address="", poiSubId="0"))`
  - 경유지: `addWaypoint(RequestWaypointInfo(latitude=…, longitude=…, poiName=…, waypointIndex=WaypointIndex.FIRST, poiId="", address="", poiSubId="0"))`. 최대 2개다.
  - 취소: `cancelRoute()`
  - **부르는 순서**(2026-11-09 사용자 승인, goldfish #28): `requestRoute` → `onRouteStarted`를 받으면 `addWaypoint(FIRST)`. `onRouteStarted`가 10초 안에 오지 않으면 로그만 남긴다. 다시 설정할 때(대중교통을 받아들임)는 `cancelRoute` → `requestRoute` → `addWaypoint` 순서다.
  - 권한: `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`. 문서 예시에는 `NAVI_CUSTOM_MAP`도 있지만 쓰는 API에 필요한지 구현 때 확인하고, 필요 없으면 넣지 않는다.
- **에뮬레이터에서 본 것**
  - 에뮬레이터 와이파이가 끊겨 있으면 내비 엔진이 뜨지 않는다. 요청은 "navi not ready"로 버려지고, 콜백은 오지 않는다(R11).
  - 와이파이를 연결한 뒤에는 동작했다.
    - `requestRoute` → `onRouteStarted(duration=1800, distance=25000, …)`이 왔다. 초와 m다.
    - 이어서 `onNavigationStatus(ROUTE_DRIVE)`, `onDrivingInfo(DrivingState(name, duration, distance, …))`, `onTBTInfo`가 왔다.
  - `addWaypoint`는 경로를 다시 찾는다.
    - `onRouteCancelled` → `onWaypointChanged(index=FIRST, cmdType=ADD, …)` → `onDrivingInfo`(경유지 몫 포함) 순서로 왔다.
  - **내비의 현재 위치는 에뮬레이터 GPS를 따라오지 않았다.**
    - 내비는 마지막 위치(37.4127, 127.0947, 성남시 수정구 시흥동)에 머물렀다.
    - `adb emu geo fix`로 15초 동안 위치를 옮겨도 `onCurrentLocationInfo`가 같은 값이었고, `onDrivingInfo`는 새로 오지 않았다.
    - 내비 엔진 안에 경로 모의 주행 기능(`route_simul`)은 있지만, 화면이나 설정에서 켜는 곳을 찾지 못했다.
- **Rationale**: Pleos 단일 경로 정책상 길 안내는 기본 내비가 한다(원칙 IV). 목적지·경유지 넘기기는 에뮬레이터에서 확인됐다.
- **Alternatives considered**
  - 내비의 남은 시간(`DrivingInfo.destination.duration`)을 쓰기: 실차에서는 가장 정확할 것이다. 하지만 에뮬레이터에서 주행에 따라 바뀌는 것을 확인할 수 없어 1차에서는 쓰지 않는다. 2차에 실차 확인 뒤 붙인다.
  - 내비의 현재 위치·진행 방향(`CurrentLocationInfo.heading`): 같은 이유로 쓰지 않는다.

## R2. 차 위치와 진행 방향 (FusedLocation 1.1.3)

- **Decision**: 차 위치는 FusedLocation으로 받고, 진행 방향은 앱이 연속된 위치로 계산한다. 계산할 때는 지금 위치와, 8m 넘게 떨어진 가장 최근 위치를 쓴다.
- **API**: `ai.pleos.playground:FusedLocation:1.1.3`
  - 순서: `FusedLocation(context)` → `isSupported()` → `initialize()` → `registerFusedLocationCallback(FusedLocationListener)`
  - 콜백: `onChanged(LocationData?)`. 받는 값은 latitude, longitude, altitude, heading, confidence, timestampMillis다.
  - 권한: `pleos.car.permission.FUSED_LOCATION`
- **에뮬레이터에서 본 것**
  - `isSupported()`는 true였다.
  - 위치는 약 1초마다 왔고, `adb emu geo fix`로 넣은 값을 그대로 따라왔다.
  - `heading`은 차가 움직여도 늘 0.0이었다. `geo fix`에는 방향 값이 없다.
- **Alternatives considered**: Android `LocationManager`도 같은 위치를 받는다(`dumpsys location` 확인). Pleos가 차량 앱에 권하는 쪽이 FusedLocation이고, 실차에서는 차 센서로 보정된 값이 온다고 해서 FusedLocation을 고른다.

## R3. 남은 시간

- **Decision**: 1차는 앱이 직접 추정한다.
  - 거리: 차에서 진입 경유지까지의 직선거리 × 1.3에, 진입 경유지에서 지점까지 거리(100m)를 더한다.
  - 속도
    - 지점을 고를 때는 차가 아직 출발 전이라 시속 25km로 본다(data-model §4.2).
    - 주행 중에는 최근 30초 평균 속도를 쓴다. 시속 20km보다 느리면 신호 대기처럼 잠깐 선 것으로 보고 시속 20km로 본다(data-model §6).
- **Rationale**: 에뮬레이터에서는 내비 남은 시간이 주행에 따라 바뀌지 않는다(R1). 마지막 몇 분 구간에서는 이 추정으로 "도착 몇 분 전" 판단에 충분하다. 시연 스크립트는 일정한 속도로 달려 추정이 맞는다.
- **천장**: 길이 많이 굽거나 막히면 틀린다. 코드에 `ponytail:` 주석으로 남기고, 2차에 내비 남은 시간(R1)으로 바꾼다.

## R4. 자동 전화

- **Decision**(2026-11-09 사용자 승인, goldfish #19): `TelecomManager.placeCall(tel 주소, Bundle())`로 건다. 권한은 `android.permission.CALL_PHONE`이다.
  - **이유**: 주행 중에는 Pleos가 앱 화면을 가린다(R6). 서비스에서 화면을 띄우는 `ACTION_CALL`은 Android의 백그라운드 액티비티 시작 제한에 걸리면 예외 없이 무시될 수 있다. `placeCall`은 화면을 띄우지 않는다.
  - **확인**(T023, 2026-11-10 `handsfree`): 기어 D이고 내비가 앞에 있어 앱이 가려진 상태에서 서비스가 `placeCall`을 불렀다. `dumpsys telecom`에 통화가 ACTIVE로 생겼다. 출발 78초 뒤였고, 실제 문턱(출발 60초 뒤)보다 18초 늦었다.
  - **순서**: 권한을 먼저 확인한다. 되면 말하고 2초 뒤 건다. 안 되면 "폰으로 알렸어요" 한 줄만 말한다(data-model §7 보충).
  - 걸 때마다 태울 사람 화면에도 "지금 나오세요" 알림을 함께 보낸다. 전화가 막혀도 알림은 간다.
  - 운전자에게 하는 말은 `startActivity`가 성공했는지에 따라 고른다. 성공하면 "○○님께 전화 걸게요", 예외가 나면 "○○님 폰으로 알렸어요"다.
- **에뮬레이터에서 본 것**
  - `CALL` 인텐트는 시스템 telecom(`UserCallActivity`)이 받는다.
  - 에뮬레이터의 모의 SIM 계정(`TelephonyConnectionService`)이 기본 발신 계정이라 통화가 바로 ACTIVE가 됐다.
  - 화면 위에 초록 통화 칩만 뜨고 앱 화면은 그대로였다.
  - 끊기: `adb shell input keyevent KEYCODE_ENDCALL`
  - 블루투스 폰은 연결돼 있지 않았다(`isConnected: false`).
- **실차**: 연결된 폰(블루투스 핸즈프리)으로 건다. Pleos FAQ는 차량에서 Telephony가 제한될 수 있다고 한다. 그래서 대체 알림을 항상 함께 보낸다(FR-018).

## R5. 문과 착좌 (Vehicle SDK 2.3.2)

- **Decision**
  - 문: `vehicle.getDoor().registerDoorPosition(DoorArea(DoorValue.ROW_1_LEFT … ROW_2_RIGHT), DoorPositionListener)`. 값은 `onPositionUpdated(Int?)`로 오고, 0이면 닫힘, 0보다 크면 열림이다.
  - 착좌: `vehicle.getSeat().registerSeatOccupancy(SeatArea(SeatValue.ROW_1_RIGHT …), SeatOccupancyListener)`. 값은 `onSeatOccupancyUpdated(Boolean?)`로 온다.
  - 기어: DRIVE DNA와 같은 `GearSelectionListener`
  - 권한: `pleos.car.permission.CAR_DOORS`, `CAR_SEATS`, `CAR_DRIVE`
- **에뮬레이터에서 본 것**
  - 문: 콘솔 `PUT /vehicle/body?door_fr=true|false`로 바꾸면 0.5초 안에 1 또는 0이 왔다.
  - 착좌: 콘솔 `PUT /vehicle/seat?kind=occupied&area=passenger&value=…`로 바꾸면 **앱에는 콘솔 값의 반대가 온다**(구현 중 다시 확인, 2026-11-09).
    - 원래 값은 맞게 바뀐다. 콘솔 false → VHAL 1(VACANT), true → 2(OCCUPIED)이다. SDK가 1을 true, 2를 false로 넘긴다.
    - 같은 값을 다시 쓰면 바뀜이 없어 아무것도 오지 않는다.
    - 처음 기록한 "쓸 때마다 직전 값이 온다"는 번갈아 쓸 때 반대로 오는 것과 구별되지 않아 생긴 오해였다.
    - 구독 직후 첫 값은 두 번 오고 서로 엇갈렸다(조수석이 false 다음 true).
    - **앱은 SDK 값을 그대로 믿는다.** 실차에서 어느 쪽이 맞는지 모르기 때문이다. 에뮬레이터에 맞추는 일은 시연 스크립트가 한다.
      - `seat(occupied)`는 원하는 값을 콘솔에 먼저 쓰고, 그 반대를 쓴다. 두 번째 쓰기가 늘 바뀜을 만들고, 앱은 원하는 값을 받는다.
      - `reset`은 앱이 뜬 뒤에 좌석을 쓴다.
  - 뒷좌석 착좌는 늘 true로 왔다. 콘솔 값 false(1)가 반대로 온 것이다. 그래서 시연에서는 조수석으로 탑승을 보인다.
  - 기어: 콘솔 하트비트 세션이 생기기 전에 쓴 값은 콘솔 상태만 바뀌고 에뮬레이터로 가지 않는다(응답 `bridge_ready: false`). 시연 스크립트는 하트비트를 시작하고 1.5초 기다린 뒤 쓴다.

## R6. 음성

- **Decision**: DRIVE DNA와 같다. `TextToSpeech(applicationContext, Mode.ON_DEVICE)` → `initialize`. 권한은 `TTS_SERVICE`다.
- 말한 것은 `adb logcat -d | grep "doSpeak(kr.skku.sameside"`로 확인한다.
- 주행 중에는 화면이 가려지므로(DO 선언 없음) 말하기는 포그라운드 서비스에서 한다.

## R7. 태울 사람 화면과 중계 서버

- **Decision**
  - 서버: Python 3 표준 라이브러리 `http.server.ThreadingHTTPServer` 파일 하나(`server/server.py`)
  - 화면: 같은 서버가 내주는 정적 페이지 하나(`server/page.html`, 바닐라 JS, 지도는 SVG 그림)
  - 상태: 메모리에만 두고, 차와 페이지가 2초마다 주고받는다(contracts/api.md).
  - 주소: 서버는 `127.0.0.1:8090`에서만 연다.
    - 에뮬레이터 앱은 `http://10.0.2.2:8090`으로 닿는다. 에뮬레이터에서 `nc 10.0.2.2 8090`이 `HTTP/1.0 200 OK`를 받는 것으로 확인했다.
    - 페이지는 맥 브라우저 `http://localhost:8090/p/<id>`로 연다. 차 화면의 QR과 링크 글자도 1차에는 이 주소다(spec FR-005).
- **Rationale**
  - 설치 없는 링크 화면(FR-005)에는 위치를 중계할 곳이 필요하다.
  - 표준 라이브러리만으로 충분하고, 의존성이 0이다(원칙 V).
  - 메모리만 쓰면 끝난 픽업이 남지 않는다(원칙 III).
- **천장**
  - 실제 폰 브라우저의 위치 API는 HTTPS에서만 동작한다. 1차 시연은 맥 브라우저(localhost는 예외로 허용됨)와 시연 스크립트가 넣는 위치로 한다.
  - 공개 HTTPS 서버는 2차에 올린다.
- **Alternatives considered**
  - Firebase 같은 외부 서비스: 계정과 키가 필요하고, 위치가 제3자 서비스에 남는다.
  - 문자로 정적 지도 링크 보내기: 차 위치·남은 시간을 갱신할 수 없고, 태울 사람 위치와 동의를 받을 수 없다.

## R8. 링크 전달

- **Decision**: 차 화면에 QR과 짧은 링크 글자를 보인다.
  - 운전자는 정차 중에 자기 폰으로 QR을 찍어 메신저로 보낸다.
  - QR은 ZXing core(`com.google.zxing:core:3.5.3`)로 만든다. 새 의존성 하나다.
- **Rationale**: Pleos 차량 앱은 SMS·Telephony가 제한될 수 있다. 서버에서 문자를 보내려면 유료 문자 API와 계정이 필요하다.
- **Alternatives considered**: 링크 글자만 보이기. 폰에 옮겨 치기 어렵다.

## R9. 시연 지역 데이터

- **Decision**: 1차 시연 데이터는 Kotlin 코드(`Area.kt`의 `DEMO`)로 둔다. 데이터 묶음은 두 개다.
  - 묶음 ①: 성균관대역 주변(반경 약 500m). 반대편 함정, 자동 전화, 건너편 경고 장면에 쓴다.
  - 묶음 ②: 대중교통 장면의 내릴 정거장 주변. 묶음 ①에서 1.6km 넘게 떨어진 곳이다.
    - 걷는 거리가 300m 안이라 걷는 지점의 걷는 시간은 약 330초 이하다.
    - 그래서 이득 5분은 차 시간 차이에서 나와야 하고, 내린 쪽 지점이 차에 그만큼 가까워야 한다(data-model §5).
    - 대중교통 장면의 차 시작점은 묶음 ②를 지나 ①로 가는 길 위, 수 km 밖에 둔다.
  - 넣는 것: 길(선, 일방통행 여부), 랜드마크 자리, 세우면 안 되는 점(교차로 모퉁이, 버스정류장, 횡단보도), 대중교통 안 1~2개
  - 시연 장면이 요구하는 것
    - 반대편 함정: 역 출구가 길(또는 철로) 양쪽에 있어서, 차가 오는 쪽과 반대쪽에 출구가 하나씩 있어야 한다.
    - 두 정거장: 한두 정거장 타면 5분 넘게 빨라지는 대중교통 안이 하나 있어야 한다.
- **출처**(2026-11-09 사용자 승인)
  - 좌표는 OpenStreetMap(ODbL, 출처 "© OpenStreetMap contributors")에서 두 지역을 Overpass API로 한 번 받아 다듬는다. 받은 JSON은 스크래치 폴더에 두고 저장소에는 넣지 않는다.
  - 대중교통 시간은 시연용 준비값이고, 화면과 README에 그렇게 밝힌다.
- **사진**: 1차 기본값은 사진 없음(null)이다. 거리 사진(로드뷰 등)은 저작권 때문에 쓰지 않는다. 팀이 직접 찍은 사진을 주면 `server/photo/`에 둔다.
- **Rationale**
  - 순수 코드 테스트(JUnit)에서 JSON 파서 없이 바로 쓸 수 있다. Android `org.json`은 단위 테스트에서 동작하지 않는다.
  - 실시간 장소·경로 API는 키와 비용이 들어 2차로 미룬다.

## R10. 같은 편 판정 규칙

- **좌표**: 지역 중심을 기준으로 위경도를 m 단위 평면(x 동쪽, y 북쪽)으로 바꿔 계산한다. 반경 수 km에서 오차는 무시할 만하다.
- **길가 쪽**: 랜드마크 자리가 가장 가까운 길 선분의 오른쪽인지 왼쪽인지를 외적의 부호로 정한다.
  - 진행 방향 오른쪽 길가가 되는 방향이 그 자리의 "들어오는 방향"이다.
  - 선분에서 3m 안이라 편이 애매한 자리는 뺀다(FR-010).
  - 일방통행 길에서는 허용된 방향이 들어오는 방향일 때만 쓴다.
- **진입 경유지**: 들어오는 방향의 반대쪽으로 길을 따라 100m 거슬러 올라간 점이다. 기본 내비가 그 방향으로 들어오게 만든다(FR-014).
- **U턴이 필요한 자리 빼기(FR-008)**: 들어오는 방향과 "차 → 지점" 방위의 차이가 100°를 넘으면 뺀다. 차가 지점을 지나쳐 되돌아와야 하는 자리이기 때문이다.
  - 천장: 길이 복잡하면 직선 방위가 실제 진입 방향과 다를 수 있다. 코드에 `ponytail:` 주석으로 남기고, 2차에 경로 API로 바꾼다.
  - 이 규칙이 틀리는 경우는 150m 앞 확인(FR-019·020)이 태울 사람을 건너오게 해서 막는다.
- **걷는 시간**: 직선거리 × 1.3 ÷ 분속 67m(시속 4km)로 계산한다. 태울 사람이 지점과 반대쪽 길가에 있으면 1분을 더한다(횡단보도 대기).

## R11. 에뮬레이터 네트워크

- 콜드 부팅 직후에는 와이파이가 저장된 망 없이 끊겨 있다.
  - 안드로이드 기본 네트워크가 없다(`Active default network: none`).
  - 내비가 "네트워크 없음"으로 엔진을 띄우지 않는다.
  - 앱의 서버 접속도 이 상태에 영향을 받는다.
- 이 상태에서 할 수 없는 것
  - 셸에서 `cmd wifi connect-network`는 권한이 없다(uid 2000).
  - 상용(user) 빌드라 `adb root`도 안 된다.
- **대응**: 시연 준비(`demo.py reset`)는 차 설정 화면(`android.settings.WIFI_SETTINGS`)을 열어 `AndroidWifi`를 누른다. 한 번 연결하면 저장된다.
- 연결 뒤 내비 상태 변화: NetworkComplete → AuthComplete → NaviEngineComplete → MapComplete. 약 7초 걸렸다.

## R12. 기준 수치 (잠정값)

| 값 | 쓰는 곳 |
|---|---|
| 걷는 거리 300m, 아이·어르신 150m | FR-007 |
| 세우면 안 되는 거리: 교차로 모퉁이 5m, 버스정류장·횡단보도 10m | FR-009, 도로교통법 제32조 |
| 편이 애매한 거리 3m | FR-010 |
| 만나는 시각 동점 범위 1분 | FR-011 |
| 진입 경유지 100m | FR-014 |
| U턴 판정 100° | FR-008 |
| 걷기 분속 67m, 우회 1.3배, 길 건너기 +1분 | 걷는 시간 |
| 차 기본 시속 25km, 최저 시속 20km, 평균 구간 30초 | R3 |
| 자동 전화: 걷는 시간 + 1분, 최소 2분 | FR-016 |
| 나와 있음으로 보는 거리 30m | FR-017 |
| 도착 직전 안내 150m. 지점 길 위 = 지점 길 선에서 25m 안(상·하행 선 간격 17m를 품는다). 방향: 45° 안이면 오른쪽, 135° 넘으면 반대 방향, 사이는 기다림(구현 중 조정) | FR-019·020 |
| 건너편 경고: 남은 시간 3분, 길에서 80m 안 | FR-023 |
| 대중교통 이득 5분 | FR-024 |
| 탑승 판정 거리 50m | FR-026 |
| 공유 자동 종료: 도착 예정 + 30분 | FR-027 |
| 진행 방향 계산 거리 8m | R2 |
| 서 있음: 최근 5초 이동 5m 미만 | 탑승 판정(data-model §6) |
| 대기 상태 만료: 링크 대기·지점 고름·같은 편 자리 없음 60분 | FR-027 |
| 주행 중 음성 상한 5번 | FR-015 |
| 차 위치 올리기 1초에 한 번 이하 | contracts/api.md 주기 |

## R13. 시연 전 앱 데이터 지우기

- **Decision**: 시연 준비(`demo.py reset`)는 앱을 멈추고(`am force-stop`), `run-as`로 `shared_prefs/people.xml`만 지운 뒤 다시 쓴다(DRIVE DNA `scripts/demo.py`의 `reset`과 같은 방식).
- **Rationale**: `pm clear`는 앱 데이터와 함께 런타임 권한 허용도 되돌린다(AOSP `clearApplicationUserData`). 그러면 설치 때 `-g`로 준 `CALL_PHONE`이 사라져 자동 전화 장면이 대체 알림으로 바뀐다.

