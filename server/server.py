#!/usr/bin/env python3
"""같은편 중계 서버(contracts/api.md). 표준 라이브러리만 쓰고, 메모리에만 둔다.

  python3 server/server.py         # 127.0.0.1:8090
  python3 server/server.py check   # 자체 점검
"""
import http.client
import json
import re
import secrets
import sys
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
STATUSES = {"link", "spots", "nospot", "going", "met", "canceled", "stopped", "expired"}
ENDED = {"met", "canceled", "stopped", "expired"}
MAX_BODY = 32 * 1024
KEEP_ENDED_SEC = 10 * 60
CAR_SILENCE_SEC = 30 * 60
NUM = (int, float)

_offset = [0.0]  # 자체 점검이 시계를 앞으로 돌릴 때 쓴다


def now():
    return time.monotonic() + _offset[0]


def advance(sec):
    _offset[0] += sec


lock = threading.Lock()
pickups = {}  # id -> {"key", "carSeen", "endedAt", "state"}

# 차가 올리는 필드와 타입(data-model §8). None은 "없음"이다
CAR_FIELDS = {"status": str, "name": str, "car": dict, "pos": (dict, type(None)), "etaSec": (int, type(None)),
              "walkSec": (int, type(None)), "spots": list, "chosen": int, "offer": (dict, type(None)),
              "outNow": bool, "crossOver": bool, "wrongSide": bool, "arrived": bool}
PASSENGER_FIELDS = {"consent": bool, "lat": NUM, "lon": NUM, "acc": NUM, "ready": bool, "late": bool,
                    "boarded": bool, "stop": bool, "transit": str, "ack": bool}


def typed(body, fields):
    """모르는 필드는 버리고, 아는 필드의 타입이 틀리면 None(→ 400)"""
    out = {}
    for k, v in body.items():
        if k not in fields:
            continue
        t = fields[k]
        if isinstance(v, bool) and t in (NUM, int, (int, type(None))):
            return None  # JSON true가 숫자로 들어오는 것을 막는다
        if not isinstance(v, t):
            return None
        out[k] = v
    return out


def fresh(name, car):
    return {"status": "link", "name": name, "car": car, "pos": None, "etaSec": None, "walkSec": None, "spots": [],
            "chosen": -1, "offer": None, "outNow": False, "crossOver": False, "wrongSide": False, "arrived": False,
            "passenger": {"consent": None, "lat": None, "lon": None, "ready": False, "late": False,
                          "boarded": False, "transit": None, "ack": False}}


def end(rec, status):
    rec["state"] = {"status": status, "name": rec["state"]["name"]}  # 위치·지점·차 정보는 바로 지운다(FR-028)
    rec["endedAt"] = now()


def sweep():
    """끝난 지 10분, 차 요청이 30분 넘게 없으면 통째로 지운다"""
    t = now()
    with lock:
        for pid in [k for k, r in pickups.items()
                    if (r["endedAt"] is not None and t - r["endedAt"] > KEEP_ENDED_SEC)
                    or (r["endedAt"] is None and t - r["carSeen"] > CAR_SILENCE_SEC)]:
            del pickups[pid]


def sweeper():
    while True:
        time.sleep(60)
        sweep()


def clean_car(car):
    if not isinstance(car, dict):
        return None
    out = {}
    for k in ("model", "color", "plate"):
        v = car.get(k, "")
        if not isinstance(v, str) or len(v) > 20:
            return None
        out[k] = v
    phone = car.get("phone", "")
    if not isinstance(phone, str) or not (phone == "" or re.fullmatch(r"\d{9,11}", phone)):
        return None  # 페이지의 tel: 링크에 쓰이므로 숫자만
    out["phone"] = phone
    return out


class Handler(BaseHTTPRequestHandler):
    timeout = 10  # 본문을 다 보내지 않고 붙잡는 연결을 끊는다

    def log_message(self, fmt, *args):  # 위치는 본문에만 있으므로 경로와 코드만 남는다
        sys.stderr.write(f"{self.command} {self.path.split('?')[0]} {args[1] if len(args) > 1 else ''}\n")

    def reply(self, code, body=None, ctype="application/json; charset=utf-8"):
        data = b"" if body is None else (body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode())
        self.send_response(code)
        if data:
            self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def body(self):
        try:
            n = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            return None
        if not 0 <= n <= MAX_BODY:
            return "big"
        try:
            v = json.loads(self.rfile.read(n) or b"{}")
            return v if isinstance(v, dict) else None
        except (ValueError, RecursionError, OSError):  # 깨진 JSON, 너무 깊은 JSON, 시간 초과
            return None

    def do_GET(self):
        path = self.path.split("?")[0]
        if m := re.fullmatch(r"/api/pickups/([\w-]+)", path):
            with lock:
                rec = pickups.get(m[1])
                if rec is None:
                    return self.reply(404)
                if self.headers.get("X-Car-Key") == rec["key"]:
                    rec["carSeen"] = now()
                return self.reply(200, rec["state"])
        if re.fullmatch(r"/p/[\w-]+", path):
            return self.reply(200, (HERE / "page.html").read_bytes(), "text/html; charset=utf-8")
        if m := re.fullmatch(r"/photo/([a-z0-9_-]+\.jpg)", path):
            f = HERE / "photo" / m[1]
            return self.reply(200, f.read_bytes(), "image/jpeg") if f.is_file() else self.reply(404)
        self.reply(404)

    def do_POST(self):
        if self.path != "/api/pickups":
            return self.reply(404)
        b = self.body()
        if b == "big":
            return self.reply(413)
        name = b.get("name", "").strip() if isinstance(b, dict) and isinstance(b.get("name"), str) else ""
        car = clean_car(b.get("car", {}) if isinstance(b, dict) else None)
        if not (1 <= len(name) <= 10) or car is None:
            return self.reply(400)
        pid, key = secrets.token_urlsafe(9), secrets.token_urlsafe(18)
        with lock:
            pickups[pid] = {"key": key, "carSeen": now(), "endedAt": None, "state": fresh(name, car)}
        self.reply(201, {"id": pid, "key": key})

    def do_PUT(self):
        m = re.fullmatch(r"/api/pickups/([\w-]+)/(car|passenger)", self.path)
        if not m:
            return self.reply(404)
        b = self.body()
        if b == "big":
            return self.reply(413)
        if b is None:
            return self.reply(400)
        with lock:
            rec = pickups.get(m[1])
            if rec is None:
                return self.reply(404)
            if m[2] == "car" and self.headers.get("X-Car-Key") != rec["key"]:
                return self.reply(403)
            if rec["endedAt"] is not None:
                return self.reply(409)
            return self.put_car(rec, b) if m[2] == "car" else self.put_passenger(rec, b)

    def put_car(self, rec, b):
        v = typed(b, CAR_FIELDS)
        if v is None or ("status" in v and v["status"] not in STATUSES):
            return self.reply(400)
        if "car" in v:
            v["car"] = clean_car(v["car"])
            if v["car"] is None:
                return self.reply(400)
        rec["carSeen"] = now()
        rec["state"].update(v)
        if rec["state"]["status"] in ENDED:
            end(rec, rec["state"]["status"])
        self.reply(204)

    def put_passenger(self, rec, b):
        v = typed(b, PASSENGER_FIELDS)
        if v is None or v.get("transit", "accept") not in ("accept", "decline"):
            return self.reply(400)
        if ("lat" in v and not -90 <= v["lat"] <= 90) or ("lon" in v and not -180 <= v["lon"] <= 180):
            return self.reply(400)
        p = rec["state"]["passenger"]
        if "consent" in v:  # 동의를 먼저 처리한다
            p["consent"] = v["consent"]
            if not v["consent"]:
                p["lat"] = p["lon"] = None
        if p["consent"] and "lat" in v and "lon" in v:
            p["lat"], p["lon"] = v["lat"], v["lon"]
        for k in ("ready", "late", "boarded", "transit", "ack"):
            if k in v:
                p[k] = v[k]
        if v.get("stop"):
            end(rec, "stopped")
        self.reply(204)


def check():
    """contracts/api.md 자체 점검 아홉 항목"""
    srv = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    base = f"http://127.0.0.1:{srv.server_port}"

    def call(method, path, body=None, key=None, raw=None):
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        req = urllib.request.Request(base + path, data=data, method=method, headers={"Content-Type": "application/json"})
        if key:
            req.add_header("X-Car-Key", key)
        try:
            with urllib.request.urlopen(req, timeout=5) as r:
                text = r.read().decode()
                return r.status, (json.loads(text) if text else None)
        except urllib.error.HTTPError as e:
            return e.code, None

    def make(name="민지"):
        code, got = call("POST", "/api/pickups", {"name": name, "car": {"model": "그랜저", "color": "흰색", "plate": "12가 3456", "phone": "01098765432"}})
        assert code == 201 and got["id"] and got["key"], (code, got)
        return got["id"], got["key"]

    # 1. 만들기, 처음 값
    pid, key = make()
    assert call("POST", "/api/pickups", {"name": " "})[0] == 400
    code, got = call("GET", f"/api/pickups/{pid}")
    assert code == 200 and got["status"] == "link" and got["passenger"]["consent"] is None and "key" not in got, got

    # 2. key·status·크기
    assert call("PUT", f"/api/pickups/{pid}/car", {"status": "going"}, key="wrong-key")[0] == 403
    assert call("PUT", f"/api/pickups/{pid}/car", {"status": "flying"}, key=key)[0] == 400
    assert call("PUT", f"/api/pickups/{pid}/car", raw=b"{" + b" " * (33 * 1024) + b"}", key=key)[0] == 413

    # 3. 동의와 위치
    assert call("PUT", f"/api/pickups/{pid}/passenger", {"lat": 37.3, "lon": 126.97})[0] == 204
    assert call("GET", f"/api/pickups/{pid}")[1]["passenger"]["lat"] is None
    assert call("PUT", f"/api/pickups/{pid}/passenger", {"consent": True, "lat": 37.3, "lon": 126.97, "acc": 12})[0] == 204
    p = call("GET", f"/api/pickups/{pid}")[1]["passenger"]
    assert p["consent"] is True and p["lat"] == 37.3 and "acc" not in p, p
    assert call("PUT", f"/api/pickups/{pid}/passenger", {"lat": 137.3, "lon": 126.97})[0] == 400
    assert call("PUT", f"/api/pickups/{pid}/passenger", {"ready": "yes"})[0] == 400
    assert call("PUT", f"/api/pickups/{pid}/passenger", {"ack": True})[0] == 204  # 2차: 지금 나가요
    assert call("GET", f"/api/pickups/{pid}")[1]["passenger"]["ack"] is True
    assert call("PUT", f"/api/pickups/{pid}/passenger", {"ack": "yes"})[0] == 400
    assert call("PUT", f"/api/pickups/{pid}/passenger", {"consent": False})[0] == 204
    p = call("GET", f"/api/pickups/{pid}")[1]["passenger"]
    assert p["consent"] is False and p["lat"] is None, p

    # 4. 끝 상태는 status·name만
    assert call("PUT", f"/api/pickups/{pid}/car", {"status": "going", "pos": {"lat": 37.29, "lon": 126.97, "heading": 10}}, key=key)[0] == 204
    assert call("PUT", f"/api/pickups/{pid}/car", {"status": "met"}, key=key)[0] == 204
    assert call("GET", f"/api/pickups/{pid}")[1] == {"status": "met", "name": "민지"}

    # 5. 끝난 픽업 쓰기는 409
    assert call("PUT", f"/api/pickups/{pid}/passenger", {"ready": True})[0] == 409
    assert call("PUT", f"/api/pickups/{pid}/car", {"status": "going"}, key=key)[0] == 409

    # 6. 공유 끄기
    pid2, key2 = make()
    assert call("PUT", f"/api/pickups/{pid2}/passenger", {"consent": True, "lat": 37.3, "lon": 126.97, "stop": True})[0] == 204
    assert call("GET", f"/api/pickups/{pid2}")[1] == {"status": "stopped", "name": "민지"}
    assert call("PUT", f"/api/pickups/{pid2}/car", {"status": "going"}, key=key2)[0] == 409

    # 7. 10분 삭제, 30분 안전망(시계를 바꿔 끼우고 청소 함수를 직접 부른다)
    pid3, key3 = make()  # 진행 중, 차 요청 없음
    pid4, key4 = make()  # 진행 중, key 붙은 GET이 있음
    advance(20 * 60)
    assert call("GET", f"/api/pickups/{pid4}", key=key4)[0] == 200
    advance(11 * 60)
    sweep()
    assert call("GET", f"/api/pickups/{pid}")[0] == 404, "끝난 지 10분 넘은 픽업"
    assert call("GET", f"/api/pickups/{pid3}")[0] == 404, "차 요청이 30분 넘게 없던 픽업"
    assert call("GET", f"/api/pickups/{pid4}")[0] == 200, "key 붙은 GET이 있던 픽업"

    # 8. 사진 이름 제한
    assert call("GET", "/photo/../server.py")[0] == 404
    assert call("GET", "/photo/a.png")[0] == 404

    # 9. 이상한 본문 길이와 너무 깊은 JSON
    def raw_put(length, data=b""):
        c = http.client.HTTPConnection("127.0.0.1", srv.server_port, timeout=5)
        c.putrequest("PUT", f"/api/pickups/{pid4}/passenger")
        c.putheader("Content-Length", length)
        c.endheaders(data)
        code = c.getresponse().status
        c.close()
        return code
    assert raw_put("-1") == 413
    assert raw_put("abc") == 400
    deep = b"[" * 16000 + b"]" * 16000
    assert raw_put(str(len(deep)), deep) == 400

    srv.shutdown()
    print("check: 아홉 항목 통과")


if __name__ == "__main__":
    if sys.argv[1:] == ["check"]:
        check()
    else:
        threading.Thread(target=sweeper, daemon=True).start()
        print("같은편 중계 서버: http://127.0.0.1:8090 (에뮬레이터에서는 http://10.0.2.2:8090)")
        ThreadingHTTPServer(("127.0.0.1", 8090), Handler).serve_forever()
