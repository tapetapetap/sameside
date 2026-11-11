#!/usr/bin/env python3
"""같은편 시연 스크립트(specs/001-sameside-pickup/quickstart.md). Python 3 표준 라이브러리만 쓴다.

  python3 scripts/demo.py reset
  python3 scripts/demo.py opposite|transit|transit-small|handsfree|fallback|reverse|wrongside|stop|cancel|decline|nospot|tour
  python3 scripts/demo.py ack|arrive|continue|continue-cancel|continue-full      # 2차(specs/002-arrive-and-continue/quickstart.md)
  python3 scripts/demo.py group|group-transit      # 3차 함께 태우기(specs/003-group-pickup/quickstart.md)
  python3 scripts/demo.py waiting   # 3차: 알림 뒤 길가에 세우고(P) 연락 줄 링·통화 물결 보기

먼저 할 것: 서버(python3 server/server.py), adb forward tcp:8088 tcp:8088, 브라우저의 Virtual Console 페이지 닫기.
장면 좌표는 scripts/scenes.txt다(PickTest와 같이 읽는다).
"""
import json
import math
import os
import re
import subprocess
import sys
import threading
import time
import urllib.request

ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
VC, SRV = "http://127.0.0.1:8088", "http://127.0.0.1:8090"
PKG, USER = "kr.skku.sameside", "10"
HERE = os.path.dirname(os.path.abspath(__file__))
# 3차 FR-071: 사람 다섯 명(번호는 가짜, 화면에는 가려서 보인다). 줄바꿈은 XML 문자 참조로 쓴다
PEOPLE = [("민지", "01012345678", 0), ("준호", "01023456789", 0), ("서연", "01034567890", 0), ("할머니", "0311234567", 1), ("도윤", "01045678901", 0)]
PEOPLE_XML = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
              "    <string name=\"people\">" + "&#10;".join(f"{n}\t{p}\t{c}" for n, p, c in PEOPLE) + "</string>\n"
              "    <string name=\"car\">그랜저\t흰색\t12가 3456\t01098765432</string>\n"
              "    <string name=\"navOwn\">시연대기</string>\n</map>\n")
WAIT_SPOT = dict(lat=37.3002852, lon=126.9711778, name="시연대기")  # 2차 reset의 내비 목적지(공백 없이: adb shell이 공백에서 자른다)


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def adb(*a):
    return subprocess.run([ADB, *a], capture_output=True, text=True).stdout


def http(method, url, body=None):
    req = urllib.request.Request(url, method=method, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            t = r.read().decode()
            return r.status, (json.loads(t) if t else None)
    except urllib.error.HTTPError as e:
        return e.code, None


# ---------- Virtual Console(하트비트가 있어야 쓰기를 받는다) ----------

def _beat():
    while True:
        try:
            http("POST", VC + "/browser/session?client=demo")
        except Exception as e:
            log("콘솔 하트비트 실패", e)
        time.sleep(1)


threading.Thread(target=_beat, daemon=True).start()
time.sleep(1.5)  # ponytail: 고정 대기. 세션이 생기기 전 쓰기는 콘솔 상태만 바뀌고 에뮬레이터로 가지 않는다(bridge_ready false)


def vc(path):
    return http("PUT", VC + "/vehicle/" + path)[0]


def gear(g):
    vc(f"gear?gear={g}")


def door(area, opened):
    vc(f"body?door_{area}={'true' if opened else 'false'}")


def seat(occupied):
    """앱이 조수석을 occupied로 보게 한다. research R5: 이 에뮬레이터는 콘솔 값의 반대가 앱에 오고,
    같은 값을 다시 쓰면 아무것도 오지 않는다. 그래서 반대 값을 먼저 써서 바뀜을 확실히 만든 뒤 반대로 쓴다"""
    for v in (occupied, not occupied):
        vc(f"seat?kind=occupied&area=passenger&value={'true' if v else 'false'}")
        time.sleep(1)


# ---------- 위치와 장면 ----------

def scenes():
    out = {}
    for line in open(os.path.join(HERE, "scenes.txt"), encoding="utf-8"):
        f = line.split("#")[0].split()
        if len(f) >= 3:
            out.setdefault(f[0], {}).setdefault(f[1], []).append(f[2:])
    return out


SC = scenes()


def pt(scene, key):
    v = SC[scene][key][0]
    return float(v[0]), float(v[1])


def route(scene):
    return [(float(a), float(b)) for a, b in SC[scene].get("route", [])]


def geo(lat, lon):
    adb("emu", "geo", "fix", f"{lon:.7f}", f"{lat:.7f}")


def meters(a, b):
    k = math.cos(math.radians(a[0])) * 111320
    return math.hypot((b[1] - a[1]) * k, (b[0] - a[0]) * 110540)


def drive(points, kmh=30, each=None):
    """points를 kmh로 1초마다 geo fix 한다. each(t초, 남은 m)가 True를 돌려주면 멈춘다"""
    v = kmh / 3.6
    total = sum(meters(a, b) for a, b in zip(points, points[1:]))
    t = 0
    while True:
        s = min(t * v, total)
        acc = 0
        for a, b in zip(points, points[1:]):
            L = meters(a, b)
            if acc + L >= s:
                k = (s - acc) / L if L else 0
                geo(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)
                break
            acc += L
        if each and each(t, total - s):
            return
        if s >= total:
            return
        time.sleep(1)
        t += 1


# ---------- 화면 ----------

def screen():
    xml = adb("exec-out", "uiautomator", "dump", "/dev/tty")  # 실패하면 XML이 없어 빈 목록이다(지난 파일을 읽지 않는다)
    return re.findall(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)


def texts():
    return [t for t, *_ in screen() if t]


def ours(got, app):
    """에뮬레이터 내비는 안내 중이면 10초쯤 가만있을 때 앞으로 나온다. 앱 화면을 찾는 중이면 앱을 다시 앞으로 가져온다"""
    if app and "데리러 갈 사람" not in got:
        front()


def tap(text, timeout=20, app=True):
    end = time.time() + timeout
    while time.time() < end:
        found = screen()
        for t, x1, y1, x2, y2 in found:
            if t == text:
                adb("shell", "input", "tap", str((int(x1) + int(x2)) // 2), str((int(y1) + int(y2)) // 2))
                return True
        ours([t for t, *_ in found], app)
        time.sleep(1)
    raise SystemExit(f"화면에서 '{text}'를 찾지 못했다")


def wait_text(pred, timeout=30, what=""):
    end = time.time() + timeout
    while time.time() < end:
        got = texts()
        if any(pred(t) for t in got):
            return got
        ours(got, True)
        time.sleep(1)
    raise SystemExit(f"화면에 {what}가 나오지 않았다")


def passenger(pid, **body):
    return http("PUT", f"{SRV}/api/pickups/{pid}/passenger", body)[0]


def server(pid):
    return http("GET", f"{SRV}/api/pickups/{pid}")[1]


def devtime():
    """에뮬레이터 시계는 맥과 어긋난다(18초 늦은 적 있음). 로그와 비교할 시각은 기기 시각으로 잡는다"""
    return float(adb("shell", "date", "+%s"))


def logtime(pattern, since, tag="PickupService:I"):
    """since 이후 로그에서 pattern이 처음 나온 기기 시각(없으면 None)"""
    for line in adb("logcat", "-d", "-v", "epoch", "-s", tag).splitlines():
        m = re.match(r"\s*(\d+\.\d+)", line)
        if m and float(m[1]) >= since and pattern in line:
            return float(m[1])
    return None


def navi(cmd, **extras):
    """디버그 전용 DemoReceiver로 내비를 다룬다(2차 research R15). state면 (목적지, 경유지)를 돌려준다"""
    args = ["shell", "am", "broadcast", "--user", USER, "-n", f"{PKG}/.DemoReceiver", "--es", "cmd", cmd]
    for k, v in extras.items():
        args += ["--ed" if isinstance(v, float) else "--es", k, str(v)]
    since = devtime()
    adb(*args)
    if cmd != "state":
        return None
    time.sleep(4)  # 수신기는 3초 뒤 끝난다
    got = {}
    for line in adb("logcat", "-d", "-v", "epoch", "-s", "DemoReceiver:I").splitlines():
        m = re.match(r"\s*(\d+\.\d+).*?(목적지|경유지) (.*)$", line)
        if m and float(m[1]) >= since:
            got[m[2]] = m[3].strip()
    return got.get("목적지"), got.get("경유지")


def spoken(since):
    """since 이후 운전자에게 한 말(서비스 로그)"""
    out = adb("logcat", "-d", "-v", "epoch", "-s", "PickupService:I")
    lines = []
    for line in out.splitlines():
        m = re.match(r"\s*(\d+\.\d+).*(말하기: .*|전화 걸기 요청.*)", line)
        if m and float(m[1]) >= since:
            lines.append(m[2])
    return lines


# ---------- 준비와 장면 공통 규칙 ----------

def wifi():
    """research R11: 콜드 부팅 뒤 끊긴 에뮬레이터 와이파이를 차 설정 화면에서 연결한다"""
    if "Active default network: none" not in adb("shell", "dumpsys", "connectivity"):
        return
    adb("shell", "am", "start", "--user", USER, "-a", "android.settings.WIFI_SETTINGS")
    tap("AndroidWifi", app=False)
    time.sleep(8)
    log("와이파이 연결:", "none" not in adb("shell", "dumpsys", "connectivity").split("Active default network:")[1][:8])


def reset():
    for other in ("kr.skku.unusedfeature", "kr.skku.drivedna"):
        adb("shell", "am", "force-stop", "--user", USER, other)
    wifi()
    gear("Park")
    for d in ("fl", "fr", "rl", "rr"):
        door(d, False)
    geo(*pt("opposite", "car"))
    vc("turn-signal?mode=Off")
    navi("route", **WAIT_SPOT)  # 2차: 일반 장면은 늘 목적지 방식으로 돈다(위임 결정 D3)
    time.sleep(5)
    adb("shell", "am", "force-stop", "--user", USER, PKG)
    # research R13: pm clear는 권한까지 되돌린다. 설정 파일만 지우고 다시 쓴다
    tmp = os.path.join(HERE, ".people.xml")
    open(tmp, "w", encoding="utf-8").write(PEOPLE_XML)
    adb("push", tmp, "/data/local/tmp/people.xml")
    os.remove(tmp)
    adb("shell", f"run-as {PKG} --user {USER} sh -c 'mkdir -p shared_prefs && rm -f shared_prefs/people.xml && cp /data/local/tmp/people.xml shared_prefs/people.xml'")
    adb("logcat", "-c")
    adb("shell", "am", "start", "--user", USER, "-n", f"{PKG}/.MainActivity")
    wait_text(lambda t: t == "민지", what="'민지'")
    seat(False)  # 구독 직후 첫 값은 엇갈려 와서(R5) 앱이 뜬 뒤에 바뀜으로 넣는다
    log("reset 끝: 앱이 열렸고 '민지'가 있다. 내비:", navi("state"))


def begin(scene, pick_from=None):
    """장면 시작: 차를 자기 자리(또는 pick_from 장면의 car)에 두고(P) 민지 줄을 눌러 링크를 만든다"""
    gear("Park")
    geo(*pt(pick_from or scene, "car"))
    adb("shell", "am", "start", "--user", USER, "-n", f"{PKG}/.MainActivity")
    time.sleep(2)
    tap("민지")
    got = wait_text(lambda t: "/p/" in t, what="링크")
    pid = next(t for t in got if "/p/" in t).rsplit("/", 1)[1]
    log(f"[{scene}] 태울 사람 화면: {SRV.replace('127.0.0.1', 'localhost')}/p/{pid}?sim=1")
    return pid


def begin_group(scene, names=("민지", "준호")):
    """3차: 차를 자리에 두고(P) "여러 명 함께" → 이름 → "n명 함께 데리러 가기". 사람마다 링크 id를 이름 순서로 돌려준다"""
    gear("Park")
    geo(*pt(scene, "car"))
    front()
    tap("여러 명 함께")
    for n in names:
        tap(n)
    tap(f"{len(names)}명 함께 데리러 가기")
    wait_text(lambda t: t == "함께 데리러 가요", what="함께 링크")
    found = {}
    w, h = (int(v) for v in re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1])
    for _ in range(5):  # 카드가 화면 아래로 넘치면 오른쪽 칸을 밀어 올려 다시 읽는다
        cur = None
        for t in texts():
            if t.endswith("님") and t[:-1] in names:
                cur = t[:-1]
            elif "/p/" in t and cur and cur not in found:
                found[cur] = t.rsplit("/", 1)[1]
        if len(found) == len(names):
            break
        adb("shell", "input", "swipe", str(w * 7 // 10), str(h * 3 // 4), str(w * 7 // 10), str(h // 4), "500")
        time.sleep(1)
    if len(found) != len(names):
        raise SystemExit(f"함께 링크를 다 읽지 못했다: {sorted(found)}")
    for n in names:
        log(f"[{scene}] {n} 화면: {SRV.replace('127.0.0.1', 'localhost')}/p/{found[n]}?sim=1")
    return [found[n] for n in names]


def snap(name):
    """SHOT_DIR 환경 변수가 있으면 차 화면을 그 폴더에 저장한다(문서 캡처용)"""
    d = os.environ.get("SHOT_DIR")
    if d:
        os.makedirs(d, exist_ok=True)  # 4차: 폴더가 없으면 장면이 멈추던 것
        with open(os.path.join(d, name), "wb") as f:
            f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)


def front():
    adb("shell", "am", "start", "--user", USER, "-n", f"{PKG}/.MainActivity")
    time.sleep(2)


def finish():
    """장면 끝: 통화 끊기, 확인 누르기, 착좌 되돌리기"""
    adb("shell", "input", "keyevent", "KEYCODE_ENDCALL")
    gear("Park")
    time.sleep(2)
    adb("shell", "am", "start", "--user", USER, "-n", f"{PKG}/.MainActivity")
    try:
        tap("확인", timeout=8)
    except SystemExit:
        pass
    seat(False)


def share(pid, scene, key="passenger"):
    lat, lon = pt(scene, key)
    assert passenger(pid, consent=True, lat=lat, lon=lon) == 204


def confirm_first():
    wait_text(lambda t: t == "이 자리로 갈게요", what="지점 카드")
    first = texts()
    snap("cards.png")  # 4차: 만날 곳 카드(두 지도) 캡처, SHOT_DIR이 있을 때만
    tap("이 자리로 갈게요")
    return first


def board():
    """지점에 서서 조수석 문을 열었다 닫고 착좌(true를 두 번)"""
    door("fr", True)
    time.sleep(2)
    door("fr", False)
    seat(True)


# ---------- 장면 ----------

def opposite():
    pid = begin("opposite")
    share(pid, "opposite")
    shown = confirm_first()
    log("만날 곳 카드:", [t for t in shown if "앞" in t or "차가 오는 쪽" in t])
    t0 = devtime()
    gear("Drive")
    drive(route("opposite"), 30)
    board()
    gear("Park")
    front()  # 주행 중에는 내비가 앞에 있다
    wait_text(lambda t: t == "만났어요", timeout=20, what="'만났어요'")
    log("운전자 음성:", spoken(t0))
    log("서버:", server(pid))
    finish()


def handsfree(scene="handsfree"):
    """2차: 문턱에서 알림만, 60초 안에 반응이 없으면 전화(또는 대체 알림). 시각은 모두 기기 로그로 잰다"""
    pid = begin(scene)
    share(pid, scene)
    confirm_first()
    walk = int(SC["handsfree"]["walk"][0][0])
    pts = route(scene)
    total = sum(meters(a, b) for a, b in zip(pts, pts[1:]))
    t_threshold = total / (30 / 3.6) - (walk + 60)
    gear("Drive")
    t0 = devtime()  # 출발 시각: 기어 Drive를 쓴 직후
    drive(pts, 30)
    board()
    gear("Park")
    front()  # 주행 중에는 내비가 앞에 있다
    wait_text(lambda t: t == "만났어요", timeout=20, what="'만났어요'")
    log("운전자 음성:", spoken(t0))
    line = "말하기: 민지님 폰으로 알렸어요" if scene == "fallback" else "말하기: 민지님께 전화 걸게요"
    t_note, t_call = logtime("알림 보냄", t0), logtime(line, t0)
    show = lambda t: "없음" if t is None else f"{t - t0:.0f}초"
    log(f"출발 기준: 실제 문턱 {t_threshold:.0f}초, 알림 {show(t_note)}, 전화 {show(t_call)}")
    if t_note and t_call:
        a, b = t_note - t0 - t_threshold, t_call - t_note
        log(f"알림 − 문턱 {a:.0f}초({'합격' if 0 <= a <= 30 else '불합격'} 0~30), 전화 − 알림 {b:.1f}초({'합격' if 60 <= b <= 62 else '불합격'} 60~62)")
    log("통화 상태:", [l.strip()[:60] for l in adb("shell", "dumpsys", "telecom").splitlines() if "state=" in l][:2])
    finish()


def ack():
    """2차: handsfree처럼 달리다 서버 outNow가 켜지면 바로 '지금 나가요'. 전화가 걸리지 않아야 한다"""
    pid = begin("ack", pick_from="handsfree")
    share(pid, "handsfree")
    confirm_first()
    acked = {}

    def each(t, left):
        if not acked and (server(pid) or {}).get("outNow"):
            acked["t"] = devtime()
            assert passenger(pid, ack=True) == 204
            log("알림을 받고 '지금 나가요'를 눌렀다")
        return False

    gear("Drive")
    t0 = devtime()
    drive(route("handsfree"), 30, each)
    board()
    gear("Park")
    front()
    wait_text(lambda t: t == "만났어요", timeout=20, what="'만났어요'")
    calls = [l for l in adb("logcat", "-d", "-v", "epoch", "-s", "PickupService:I").splitlines()
             if "전화 걸기 요청" in l and float(l.split()[0]) >= t0]
    log(f"전화 요청 {len(calls)}번(0이어야 함)")
    log("운전자 음성:", spoken(t0))
    finish()


def arrive():
    """2차 도착 확인: opposite처럼 달려 지점에 서고 오른쪽 깜빡이. 질문이 나오면 P에서 '보내기'"""
    pid = begin("arrive")
    share(pid, "arrive")
    confirm_first()
    st = server(pid) or {}
    log("페이지 서 있을 곳:", (st.get("spots") or [{}])[max(st.get("chosen", 0), 0)].get("standLine"))

    def each(t, left):  # 알림이 오면 바로 '지금 나가요'(전화가 장면을 가리지 않게)
        st = server(pid) or {}
        if st.get("outNow") and not (st.get("passenger") or {}).get("ack"):
            passenger(pid, ack=True)
        return False

    gear("Drive")
    t0 = devtime()
    drive(route("arrive"), 30, each)
    t_stop = devtime()  # 마지막 위치를 쓴 때
    vc("turn-signal?mode=BlinkRight")
    ask = "말하기: 민지님께 도착했다고 알릴까요?"
    end, t_ask = time.time() + 15, None
    while t_ask is None and time.time() < end:
        time.sleep(1)
        t_ask = logtime(ask, t_stop - 1)
    log(f"정차 → 질문 {t_ask - t_stop:.0f}초(8초 이하)" if t_ask else "질문이 15초 안에 오지 않았다")
    gear("Park")
    vc("turn-signal?mode=Off")  # 오른쪽 깜빡이를 켜 두면 에뮬레이터가 측방 카메라 창으로 화면 왼쪽을 가린다
    time.sleep(2)
    front()
    wait_text(lambda t: t == "보내기", timeout=10, what="도착 확인 카드")
    snap("arrive-card.png")
    t_send = time.time()
    tap("보내기")
    while time.time() - t_send < 10 and not (server(pid) or {}).get("arrived"):
        time.sleep(0.5)
    log(f"보내기 → 서버 arrived {time.time() - t_send:.1f}초(5초 이하):", (server(pid) or {}).get("arrived"))
    log("운전자 음성:", spoken(t0))
    board()
    wait_text(lambda t: t == "만났어요", timeout=20, what="'만났어요'")
    finish()


def cont(mode="continue"):
    """2차 원래 목적지로 이어 가기. reset 뒤에 내비 목적지 '집'(화서역)을 넣고 opposite처럼 픽업한다"""
    navi("route", lat=37.2839144, lon=126.9896135, name="집")
    time.sleep(8)
    if mode == "continue-full":  # 경유지 칸이 이미 차 있으면 경유지 방식을 쓸 수 없다(FR-037)
        navi("waypoint", lat=37.2884, lon=126.9862, name="약국")
        time.sleep(5)
    log("픽업 전 내비:", navi("state"))
    pid = begin(mode, pick_from="opposite")
    share(pid, "opposite")
    confirm_first()
    time.sleep(20)  # 내비는 경유지를 하나 넣을 때마다 경로를 다시 잡는다(약 6초씩). 그사이 목적지가 빈 값으로 읽힌다
    log("확인 20초 뒤 내비:", navi("state"))
    if mode == "continue-full":
        front()
        log("차 화면:", [t for t in texts() if "원래 목적지" in t])
        snap("continue-full-going.png")
        tap("픽업 취소")
        finish()
        return
    if mode == "continue-cancel":
        tap("픽업 취소")
        time.sleep(15)
        log("취소 15초 뒤 내비:", navi("state"))
        finish()
        return
    t0 = devtime()
    gear("Drive")
    drive(route("opposite"), 30)
    board()
    gear("Park")
    front()
    wait_text(lambda t: t == "만났어요", timeout=20, what="'만났어요'")
    log("운전자 음성:", spoken(t0))
    time.sleep(15)
    log("탑승 15초 뒤 내비:", navi("state"))
    finish()


PHONE = ("android.permission.CALL_PHONE", "android.permission.READ_PHONE_STATE")  # 같은 권한 묶음(전화)


def fallback():
    # 3차: READ_PHONE_STATE도 같은 묶음이라, 하나라도 허용돼 있으면 CALL_PHONE 요청이 창 없이 자동 허용된다. 둘 다 거둔다
    for perm in PHONE:
        adb("shell", "pm", "revoke", "--user", USER, PKG, perm)  # 앱 프로세스가 죽는다
    adb("shell", "am", "start", "--user", USER, "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    for t, *_ in screen():  # 다시 뜨는 권한 요청은 거절한다
        if t in ("허용 안함", "허용 안 함", "거부", "Don't allow", "Deny"):
            tap(t)
            break
    try:
        handsfree("fallback")
    finally:
        for perm in PHONE:
            adb("shell", "pm", "grant", "--user", USER, PKG, perm)


def reverse():
    # 북쪽 출발점에서 고르면 U턴 규칙이 세븐일레븐을 뺀다. 남쪽(opposite의 car)에서 고른 뒤 북쪽에서 내려온다
    pid = begin("reverse", pick_from="opposite")
    share(pid, "reverse")
    shown = confirm_first()
    log("만날 곳 카드:", [t for t in shown if "앞" in t])
    geo(*pt("reverse", "car"))
    time.sleep(2)
    t0 = devtime()
    gear("Drive")
    drive(route("reverse"), 30)
    gear("Park")
    log("운전자 음성:", spoken(t0))
    log("서버 crossOver:", (server(pid) or {}).get("crossOver"))
    adb("shell", "am", "start", "--user", USER, "-n", f"{PKG}/.MainActivity")
    tap("픽업 취소")
    finish()


def wrongside():
    pid = begin("wrongside")
    share(pid, "wrongside")
    confirm_first()
    passenger(pid, ready=True)  # 자동 전화 대신 "나와 있어요"로 끝나게 해서 경고와 섞이지 않게 한다
    lat, lon, at = (float(x) for x in SC["wrongside"]["passenger2"][0])
    moved = {}

    def each(t, left):
        if t >= at and not moved:
            moved["t"] = devtime()
            passenger(pid, lat=lat, lon=lon)
            log("민지가 길 건너편으로 갔다")
        if moved and (server(pid) or {}).get("wrongSide"):
            log("서버 wrongSide = true")
            return True
        return False

    gear("Drive")
    drive(route("wrongside"), 30, each)
    log("옮긴 뒤 새 운전자 음성:", spoken(moved.get("t", devtime())))
    gear("Park")
    adb("shell", "am", "start", "--user", USER, "-n", f"{PKG}/.MainActivity")
    tap("픽업 취소")
    finish()


def transit(scene="transit"):
    """1차 US4 + 4차 FR-080: 안 보냄 6초 → 받아들임 → '…타고 화서역으로 와요' 10초 → 가는 중 5초(맨 위 강조 카드) → 취소"""
    pid = begin(scene)
    share(pid, scene)
    wait_text(lambda t: t == "이 자리로 갈게요", what="지점 카드")
    log("카드 뜸")
    offer = (server(pid) or {}).get("offer")
    log("대중교통 제안:", offer)
    if offer:
        snap("transit-offer.png")
        time.sleep(6)
        passenger(pid, transit="accept")
        log("받아들임 보냄")
        wait_text(lambda t: "타고" in t and t.endswith("와요"), what="받아들임 문구")
        log("와요 뜸")
        snap("transit-accepted.png")
        time.sleep(10)
        log("받아들인 뒤 카드:", [t for t in texts() if "앞" in t or "정거장" in t])
        tap("이 자리로 갈게요")
        front()  # 내비가 앞에 와도 앱을 다시 앞으로
        wait_text(lambda t: t == "남은 시간", what="가는 중 화면")
        log("가는 중")
        snap("transit-going.png")
        time.sleep(5)
    log("취소")
    tap("픽업 취소")
    finish()


def stop():
    pid = begin("stop")
    share(pid, "opposite")
    confirm_first()
    at = int(SC["stop"]["stop_at"][0][0])
    gear("Drive")
    drive(route("stop"), 30, lambda t, left: t >= at and passenger(pid, stop=True) == 204)
    gear("Park")
    front()
    wait_text(lambda t: "위치 공유를 껐어요" in t, what="공유 끔 문구")
    log("서버:", server(pid))
    finish()


def cancel():
    pid = begin("cancel")
    share(pid, "opposite")
    wait_text(lambda t: t == "이 자리로 갈게요", what="지점 카드")
    time.sleep(int(SC["cancel"]["cancel_at"][0][0]))
    tap("픽업 취소")
    wait_text(lambda t: t == "픽업을 취소했어요", what="취소 문구")
    log("서버:", server(pid))
    finish()


def decline():
    pid = begin("decline")
    assert passenger(pid, consent=False) == 204
    wait_text(lambda t: "위치를 공유하지 않았어요" in t, what="거절 문구")
    log("차 화면: 민지님이 위치를 공유하지 않았어요")
    tap("픽업 취소")
    finish()


def nospot():
    pid = begin("nospot")
    share(pid, "nospot")
    wait_text(lambda t: t == "가까운 곳에 같은 편 자리가 없어요", what="같은 편 자리 없음")
    log("서버:", (server(pid) or {}).get("status"))
    tap("픽업 취소")
    finish()


def answer(pids, done, **reply):
    """서버 outNow가 켜진 사람은 바로 답한다(2차 ack 장면 방식). 전화가 걸리지 않아 음성 상한에 여유가 남는다"""
    for pid in pids:
        if pid not in done and (server(pid) or {}).get("outNow"):
            done.add(pid)
            assert passenger(pid, **reply) == 204
            log(f"알림을 받고 답했다: {reply}")


def group():
    """3차 US1: 두 사람이 걸어서 세븐일레븐 앞에 모인다. 지점에서 문 → 조수석(민지) → 두 사람 '탔어요'(research R21)"""
    pids = begin_group("group")
    for pid, k in zip(pids, ("p1", "p2")):
        share(pid, "group", k)
    shown = confirm_first()
    log("만날 곳 카드:", [t for t in shown if "앞" in t or "차가 오는 쪽" in t or "걸어서" in t or t.startswith("차 ") or "근처로" in t])
    done = set()
    gear("Drive")
    t0 = devtime()
    drive(route("group"), 30, lambda t, left: answer(pids, done, ack=True) or False)
    board()
    for pid in pids:
        assert passenger(pid, boarded=True) == 204
    gear("Park")
    front()
    wait_text(lambda t: t == "만났어요", timeout=20, what="'만났어요'")
    log("만남 화면:", [t for t in texts() if "확정부터" in t or "근처로" in t])
    log("운전자 음성:", spoken(t0))
    log("서버:", [server(pid) for pid in pids])
    finish()


def group_transit():
    """3차 US2: 두 사람 모두 1호선으로 화서역에 모여 약국 앞에서 탄다(data-model §6 움직임 네 단계)"""
    sc = "group-transit"
    pids = begin_group(sc)
    for pid, k in zip(pids, ("p1", "p2")):
        share(pid, sc, k)
    shown = confirm_first()
    log("만날 곳 카드:", [t for t in shown if "앞" in t or "차가 오는 쪽" in t or "1호선" in t or t.startswith("차 ") or "근처로" in t])
    rail = [(float(a), float(b)) for a, b in SC[sc]["rail"]]
    fast = [(float(a), float(b)) for a, b in SC[sc]["fast"]]

    def move(pid, at, **more):
        assert passenger(pid, lat=at[0], lon=at[1], **more) == 204

    for pid in pids:  # 1. 확정 뒤 "알겠어요"와 성균관대역
        move(pid, rail[0], transit="accept")
    done, stage = set(), [0]
    total = sum(meters(a, b) for a, b in zip(fast, fast[1:]))

    def each_fast(t, left):  # 2. 80km 구간 1/3·2/3에서 1호선 위 다음 점
        while stage[0] < 2 and total - left >= total * (stage[0] + 1) / 3:
            stage[0] += 1
            for pid in pids:
                move(pid, rail[stage[0]])
        answer(pids, done, ready=True)  # 4. 그 전이라도 알림을 받은 사람은 바로 "나와 있어요"
        return False

    gear("Drive")
    t0 = devtime()
    drive(fast, 80, each_fast)
    for pid, k in zip(pids, ("meet1", "meet2")):  # 3. 30km 구간 시작: 약국 앞 인도와 "나와 있어요"
        move(pid, pt(sc, k))
        if pid not in done:
            done.add(pid)
            assert passenger(pid, ready=True) == 204
    drive(route(sc), 30)
    board()
    for pid in pids:
        assert passenger(pid, boarded=True) == 204
    gear("Park")
    front()
    wait_text(lambda t: t == "만났어요", timeout=20, what="'만났어요'")
    log("만남 화면:", [t for t in texts() if "확정부터" in t or "근처로" in t])
    log("운전자 음성:", spoken(t0))
    log("서버:", [server(pid) for pid in pids])
    finish()


def cut(points, done):
    """points를 처음부터 done m 간 자리에서 자른 나머지 경로"""
    acc = 0
    for i, (a, b) in enumerate(zip(points, points[1:])):
        L = meters(a, b)
        if acc + L >= done:
            k = (done - acc) / L if L else 0
            return [(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)] + points[i + 1:]
        acc += L
    return points[-1:]


def contact():
    """연락 줄이 읽어 주는 상태(접근성 설명). 주행 중 앱이 가려져 있으면 빈 목록이다"""
    return re.findall(r'content-desc="(민지님 [^"]*)"', adb("exec-out", "uiautomator", "dump", "/dev/tty"))


def waiting():
    """3차: 알림이 나가면 길가에 세우고(P) 기다린다. 앱이 보이는 동안 연락 줄이
    호박색 링(남은 초) → 통화 중 초록 물결 → 나와 있어요로 바뀐다. 그 뒤 마저 가서 태운다"""
    pid = begin("waiting", pick_from="handsfree")
    share(pid, "handsfree")
    confirm_first()
    pts = route("handsfree")
    total = sum(meters(a, b) for a, b in zip(pts, pts[1:]))
    left = []

    def each(t, rest):  # 알림이 나가면 그 자리에서 멈춘다
        if (server(pid) or {}).get("outNow"):
            left.append(rest)
            return True
        return False

    gear("Drive")
    t0 = devtime()
    drive(pts, 30, each)
    gear("Park")  # 알림이 나갔다. 길가에 세운다
    front()
    time.sleep(5)
    log("세운 뒤 연락 줄:", contact())
    snap("25-going-notified.png")
    end = time.time() + 80
    while time.time() < end and logtime("통화 중 true", t0) is None:
        time.sleep(1)
    time.sleep(5)
    front()  # 통화 화면이 앞에 나와도 앱으로 돌아온다
    time.sleep(2)
    log("통화 중 연락 줄:", contact())
    snap("26-going-calling.png")
    time.sleep(8)
    assert passenger(pid, ready=True) == 204  # 전화를 받고 나왔다
    adb("shell", "input", "keyevent", "KEYCODE_ENDCALL")
    time.sleep(5)
    log("끊은 뒤 연락 줄:", contact())
    snap("27-going-ready.png")
    gear("Drive")
    drive(cut(pts, total - left[0]), 40)
    board()
    gear("Park")
    front()
    wait_text(lambda t: t == "만났어요", timeout=20, what="'만났어요'")
    t_note, t_say = logtime("알림 보냄", t0), logtime("말하기: 민지님께 전화 걸게요", t0)
    t_call, t_in = logtime("전화 걸기 요청", t0), logtime("통화 중 true", t0)
    show = lambda a, b: "없음" if a is None or b is None else f"{b - a:.1f}초"
    log(f"알림 → '전화 걸게요' {show(t_note, t_say)}(60~62, handsfree와 같은 기준), 걸기 요청 {show(t_say, t_call)} 뒤, 걸기 → 통화 중 {show(t_call, t_in)}")
    log("운전자 음성:", spoken(t0))
    log("서버:", server(pid))
    finish()


def tour():
    reset()
    for f in (opposite, transit, handsfree):
        log("──", f.__name__)
        f()
    log("── 끝")


if __name__ == "__main__":
    name = sys.argv[1] if len(sys.argv) > 1 else ""
    fn = {"reset": reset, "opposite": opposite, "transit": transit, "transit-small": lambda: transit("transit-small"),
          "handsfree": handsfree, "fallback": fallback, "reverse": reverse, "wrongside": wrongside, "stop": stop,
          "cancel": cancel, "decline": decline, "nospot": nospot, "tour": tour,
          "ack": ack, "arrive": arrive, "continue": cont, "continue-cancel": lambda: cont("continue-cancel"),
          "continue-full": lambda: cont("continue-full"), "group": group, "group-transit": group_transit, "waiting": waiting}.get(name)
    if fn is None:
        raise SystemExit(__doc__)
    fn()
