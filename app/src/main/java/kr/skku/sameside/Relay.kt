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

    fun get(id: String, key: String): Pair<Int, Event.Remote?> {
        val (code, text) = http("GET", "/api/pickups/$id", null, key)
        if (code != 200 || text == null) return code to null
        return code to runCatching { remote(JSONObject(text)) }.getOrNull()
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

        /** 서버 GET → 순수 이벤트(data-model §7 Remote). 끝 상태면 passenger가 없다 */
        fun remote(j: JSONObject): Event.Remote {
            val p = j.optJSONObject("passenger")
            fun bool(k: String) = p?.optBoolean(k, false) ?: false
            val consent = if (p == null || p.isNull("consent")) null else p.optBoolean("consent")
            val pos = if (p == null || p.isNull("lat") || p.isNull("lon")) null else LatLon(p.getDouble("lat"), p.getDouble("lon"))
            val transit = if (p == null || p.isNull("transit")) null else p.optString("transit")
            return Event.Remote(j.optString("status"), consent, pos, bool("ready"), bool("late"), bool("boarded"), transit, bool("ack"))
        }

        /** 픽업 → 서버에 올리는 값(data-model §8) */
        fun carJson(p: Pickup, car: Car?): JSONObject {
            val j = JSONObject().put("status", p.status.wire)
            if (p.status.ended) return j
            j.put("name", p.person.name)
            j.put("car", carObj(car))
            val last = p.track.lastOrNull()
            j.put("pos", if (last == null || p.status == Status.LINK) JSONObject.NULL
            else JSONObject().put("lat", last.pos.lat).put("lon", last.pos.lon).put("heading", p.heading ?: JSONObject.NULL))
            j.put("etaSec", p.etaSec?.roundToInt() ?: JSONObject.NULL)
            j.put("walkSec", if (p.status == Status.GOING && !p.transitAccepted) p.walkSec?.roundToInt() ?: JSONObject.NULL else JSONObject.NULL)
            j.put("spots", JSONArray().apply {
                p.spots.forEach { s ->
                    put(JSONObject().put("name", s.line).put("reason", s.reason).put("lat", s.pos.lat).put("lon", s.pos.lon)
                        .put("heading", s.heading).put("walkSec", s.walkSec.roundToInt()).put("meetSec", s.meetSec.roundToInt())
                        .put("photo", s.landmark.photo ?: JSONObject.NULL)
                        // 페이지 "k분 빨라요"는 차 카드와 같은 값이어야 해서 차가 센 k를 보낸다(코드 리뷰 반영)
                        .put("fasterMin", Pick.fasterMin(s) ?: JSONObject.NULL)
                        .put("standLine", s.standLine)
                        .put("road", JSONArray().apply { s.road.forEach { put(JSONArray().put(it.lat).put(it.lon)) } }))
                }
            })
            j.put("chosen", p.chosen)
            j.put("offer", p.offer?.let {
                JSONObject().put("line", it.leg.line).put("stops", it.leg.stops).put("alightName", it.leg.alight.name)
                    .put("gainMin", it.gainMin)
            } ?: JSONObject.NULL)
            j.put("outNow", p.outNow).put("crossOver", p.crossOver).put("wrongSide", p.wrongSide).put("arrived", p.arrived)
            return j
        }
    }
}
