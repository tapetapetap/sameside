package kr.skku.sameside

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 디버그 빌드 전용. 시연 스크립트(scripts/demo.py)가 adb로 부른다.
 *   am broadcast -n kr.skku.sameside/.DemoReceiver --es cmd route --ed lat 37.28 --ed lon 126.99 --es name 집
 *   cmd: route(원래 목적지 넣기) · waypoint(경유지 하나, --es index SECOND면 둘째 칸) · state(안내 상태·목적지·경유지 로그) · remove(경유지 모두 지우기) · cancel
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = goAsync()
        val n = NaviHelper(context.applicationContext)
        val main = Handler(Looper.getMainLooper())
        fun finish() = main.postDelayed({ runCatching { n.release() }; done.finish() }, 3_000)
        n.initialize({
            n.addListener(object : NaviHelperEventListener {
                override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) { Log.i(TAG, "상태 routing=${routeStateInfo.isRouting}") }
                override fun onDestinationInfo(destinationInfo: DestinationInfo) { Log.i(TAG, "목적지 ${destinationInfo.name}") }
                override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) { Log.i(TAG, "경유지 ${waypointInfoList.map { it.name }}") }
                override fun onRouteCancelled() { Log.i(TAG, "안내 취소됨") }
                override fun onRouteEnded() { Log.i(TAG, "안내 끝") }
                override fun onError(errorCode: NaviErrorCode) { Log.w(TAG, "오류 $errorCode") }
            })
            when (intent.getStringExtra("cmd")) {
                "route" -> n.requestRoute(RouteInfo(latitude = intent.getDoubleExtra("lat", 0.0), longitude = intent.getDoubleExtra("lon", 0.0),
                    displayLatitude = null, displayLongitude = null, poiName = intent.getStringExtra("name") ?: "목적지", poiId = "", address = "", poiSubId = "0"))
                "waypoint" -> n.addWaypoint(RequestWaypointInfo(latitude = intent.getDoubleExtra("lat", 0.0), longitude = intent.getDoubleExtra("lon", 0.0),
                    poiName = intent.getStringExtra("name") ?: "경유지", waypointIndex = if (intent.getStringExtra("index") == "SECOND") WaypointIndex.SECOND else WaypointIndex.FIRST,
                    poiId = "", address = "", poiSubId = "0"))
                "state" -> { n.getRouteStateInfo(); n.getDestinationInfo(); n.getWaypointInfo() }
                "remove" -> n.removeWaypoint(WaypointIndex.ALL)
                "cancel" -> n.cancelRoute()
            }
            Log.i(TAG, "명령 ${intent.getStringExtra("cmd")}")
            finish()
        }, { e -> Log.w(TAG, "내비 연결 실패", e); done.finish() })
    }

    private companion object { const val TAG = "DemoReceiver" }
}
