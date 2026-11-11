package kr.skku.sameside

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * 중계 서버와 주고받기(contracts/api.md). 돌려주는 값은 HTTP 코드이고, 연결 실패는 −1이다.
 * 메인 스레드에서 부르지 않는다(서비스의 한 스레드에서 순서대로 부른다).
 */
class Relay(private val base: String = "http://10.0.2.2:8090") {

    data class Created(val code: Int, val id: String?, val key: String?)

    fun create(name: String, car: Car?): Created {
        val (code, text) = http("POST", "/api/pickups", JSONObject().put("name", name).put("car", carObj(car)).toString(), null)
        val j = text?.let { runCatching { JSONObject(it) }.getOrNull() }
        return Created(code, j?.optString("id"), j?.optString("key"))
    }

    fun putCar(id: String, key: String, json: JSONObject): Int = http("PUT", "/api/pickups/$id/car", json.toString(), key).first

    fun get(id: String, key: String, who: Int = 0): Pair<Int, Event.Remote?> {
        val (code, text) = http("GET", "/api/pickups/$id", null, key)
        if (code != 200 || text == null) return code to null
        return code to runCatching { remote(JSONObject(text), who) }.getOrNull()
    }

    private fun http(method: String, path: String, body: String?, key: String?): Pair<Int, String?> = try {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 3000
        c.readTimeout = 3000
        key?.let { c.setRequestProperty("X-Car-Key", it) }
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = if (code in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
        c.disconnect()
        code to text
    } catch (e: Exception) {
        -1 to null
    }

    companion object {
        private fun carObj(c: Car?) = JSONObject().put("model", c?.model.orEmpty()).put("color", c?.color.orEmpty())
            .put("plate", c?.plate.orEmpty()).put("phone", c?.phone.orEmpty())

        /** 서버 GET → 순수 이벤트(data-model §7 Remote). 끝 상태면 passenger가 없다. who는 함께 태우기의 사람 순서(3차) */
        fun remote(j: JSONObject, who: Int = 0): Event.Remote {
            val p = j.optJSONObject("passenger")
            fun bool(k: String) = p?.optBoolean(k, false) ?: false
            val consent = if (p == null || p.isNull("consent")) null else p.optBoolean("consent")
            val pos = if (p == null || p.isNull("lat") || p.isNull("lon")) null else LatLon(p.getDouble("lat"), p.getDouble("lon"))
            val transit = if (p == null || p.isNull("transit")) null else p.optString("transit")
            return Event.Remote(j.optString("status"), consent, pos, bool("ready"), bool("late"), bool("boarded"), transit, bool("ack"), who)
        }

        /** 이 사람이 고른 지점(확정 전이면 1순위)까지 가는 방법: "walk" 또는 노선 이름, 지점이 없으면 "" */
        private fun mode(p: Pickup, i: Int) = (if (p.chained) p.spots.getOrNull(p.members[i].stop) else p.spot ?: p.spots.firstOrNull())
            ?.plans?.getOrNull(i)?.let { it.leg?.line ?: "walk" }.orEmpty()

        /**
         * 픽업 → 그 사람 기록에 올리는 값(1·2차 data-model §8, 3차 §5). 사람 값은 m의 것이다.
         * 함께 태우기는 지점 시간·서 있을 곳·"k분 빨라요"를 m의 가는 방법으로 바꾸고, 다른 사람은 이름·방법·탑승만 보낸다(위치 없음)
         */
        fun carJson(p: Pickup, m: Member, car: Car?, calling: Boolean = false): JSONObject {
            val j = JSONObject().put("status", p.status.wire)
            if (p.status.ended) return j
            val i = p.members.indexOf(m)
            val mine = (if (p.chained) p.spots.getOrNull(m.stop) else p.spot)?.plans?.getOrNull(i) // 가는 중 m의 가는 방법(함께 태우기, 5차 차례면 자기 지점)
            val here = !p.chained || m.stop == p.chosen // 5차: 지금 가는 지점이 이 사람 지점
            j.put("name", m.person.name)
            j.put("car", carObj(car))
            val last = p.track.lastOrNull()
            j.put("pos", if (last == null || p.status == Status.LINK) JSONObject.NULL
            else JSONObject().put("lat", last.pos.lat).put("lon", last.pos.lon).put("heading", p.heading ?: JSONObject.NULL))
            j.put("etaSec", Pick.memberEta(p, i)?.roundToInt() ?: JSONObject.NULL) // 5차 차례면 이 사람까지(research R47)
            val walking = p.status == Status.GOING && !m.transitAccepted && mine?.leg == null
            j.put("walkSec", if (walking) m.walkSec?.roundToInt() ?: JSONObject.NULL else JSONObject.NULL)
            j.put("spots", JSONArray().apply {
                p.spots.forEach { s ->
                    val pl = s.plans.getOrNull(i)
                    // 페이지 "k분 빨라요"는 차 카드와 같은 값이어야 해서 차가 센 k를 보낸다(코드 리뷰 반영). 함께 태우기는 걸어서 건너야 하는 사람만
                    val faster = if (pl == null) Pick.fasterMin(s) else if (pl.leg == null) Pick.fasterMin(s.copy(crossing = pl.crossing)) else null
                    put(JSONObject().put("name", s.line).put("reason", s.reason).put("lat", s.pos.lat).put("lon", s.pos.lon)
                        .put("heading", s.heading).put("walkSec", (pl?.arriveSec ?: s.walkSec).roundToInt()).put("meetSec", s.meetSec.roundToInt())
                        .put("photo", s.landmark.photo ?: JSONObject.NULL)
                        .put("fasterMin", faster ?: JSONObject.NULL)
                        .put("standLine", pl?.standLine ?: s.standLine)
                        .put("road", JSONArray().apply { s.road.forEach { put(JSONArray().put(it.lat).put(it.lon)) } }))
                }
            })
            j.put("chosen", if (p.chained) m.stop else p.chosen)
            j.put("offer", m.offer?.let {
                JSONObject().put("line", it.leg.line).put("stops", it.leg.stops).put("alightName", it.leg.alight.name)
                    .put("gainMin", it.gainMin)
            } ?: JSONObject.NULL)
            j.put("plan", if (p.status != Status.GOING || mine == null) JSONObject.NULL
            else mine.leg?.let { leg ->
                JSONObject().put("mode", "transit").put("line", leg.line).put("stops", leg.stops).put("alightName", leg.alight.name)
                    .put("min", minutes(mine.arriveSec))
            } ?: JSONObject().put("mode", "walk"))
            j.put("group", JSONArray().apply {
                p.members.forEachIndexed { k, o ->
                    if (o !== m) put(JSONObject().put("name", o.person.name).put("mode", mode(p, k)).put("aboard", o.aboard).put("out", o.out))
                }
            })
            j.put("calling", calling)
            val gone = p.chained && m.aboard // 5차: 앞 지점에서 이미 탔다
            j.put("outNow", m.outNow && !gone).put("crossOver", p.crossOver && here).put("wrongSide", m.wrongSide && here && !gone)
                .put("arrived", p.arrived && here)
            return j
        }
    }
}
