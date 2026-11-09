package kr.skku.sameside

/* 지역 데이터 형식(data-model §2)과 시연 지역 DEMO. 순수 Kotlin이다. */

data class LatLon(val lat: Double, val lon: Double)

/** oneway면 points 순서가 허용 방향이다 */
data class Road(val id: String, val name: String, val points: List<LatLon>, val oneway: Boolean)

enum class NoStopKind(val meters: Double) { CORNER(5.0), BUS(10.0), CROSSWALK(10.0) }

data class NoStop(val kind: NoStopKind, val pos: LatLon)

/** pos는 랜드마크 바로 앞 길가 점이다. 화면과 음성은 "$name 앞"을 쓴다. spot이 false면 지점 후보가 아니고 기준으로만 쓴다 */
data class Landmark(val id: String, val name: String, val pos: LatLon, val roadId: String, val photo: String? = null, val spot: Boolean = true)

data class Stop(val name: String, val pos: LatLon)

/** 시연용 준비값이다. 실시간이 아니다 */
data class TransitLeg(
    val id: String, val line: String, val board: Stop, val alight: Stop,
    val stops: Int, val rideSec: Int, val waitSec: Int,
)

data class Area(
    val center: LatLon,
    val roads: List<Road>,
    val noStops: List<NoStop>,
    val landmarks: List<Landmark>,
    val transit: List<TransitLeg>,
) {
    fun road(id: String) = roads.first { it.id == id }
}

// 시연 지역(1차). © OpenStreetMap contributors(ODbL). 2026-11-09 Overpass로 받아 scratchpad make_area.py로 만들었다.
// 묶음 ① 성균관대역 주변, 묶음 ② 화서역 주변. 대중교통 시간은 시연용 준비값이다(실시간 아님).
val DEMO = Area(
    center = LatLon(37.3002852, 126.9711778),
    roads = listOf(
        Road("seobu_n", "서부로(북쪽 방향)", listOf(LatLon(37.2933715, 126.9700040), LatLon(37.2945944, 126.9700879), LatLon(37.2951426, 126.9701262), LatLon(37.2957055, 126.9702241), LatLon(37.2963487, 126.9704068), LatLon(37.2966331, 126.9705018), LatLon(37.2969612, 126.9706338), LatLon(37.2973005, 126.9708180), LatLon(37.2976045, 126.9709977), LatLon(37.2978476, 126.9711654), LatLon(37.2981370, 126.9713917), LatLon(37.2984852, 126.9717019), LatLon(37.2988495, 126.9720459), LatLon(37.2991405, 126.9722890), LatLon(37.2993896, 126.9724318), LatLon(37.2997904, 126.9726208), LatLon(37.2999234, 126.9726545), LatLon(37.3001022, 126.9726943), LatLon(37.3003153, 126.9727673), LatLon(37.3003498, 126.9727724), LatLon(37.3004211, 126.9727828), LatLon(37.3004644, 126.9727892), LatLon(37.3006432, 126.9728153), LatLon(37.3007456, 126.9728302), LatLon(37.3008184, 126.9728410), LatLon(37.3011758, 126.9728987), LatLon(37.3026500, 126.9731053), LatLon(37.3027820, 126.9731030), LatLon(37.3030300, 126.9730962), LatLon(37.3032151, 126.9730907), LatLon(37.3041441, 126.9730104), LatLon(37.3053787, 126.9728056), LatLon(37.3056953, 126.9727583), LatLon(37.3058304, 126.9727537), LatLon(37.3060975, 126.9727677), LatLon(37.3063247, 126.9727945), LatLon(37.3070139, 126.9730399), LatLon(37.3073628, 126.9732696), LatLon(37.3076913, 126.9735690), LatLon(37.3108669, 126.9773913)), oneway = true),
        Road("seobu_s", "서부로(남쪽 방향)", listOf(LatLon(37.3030227, 126.9729457), LatLon(37.3027842, 126.9729412), LatLon(37.3024289, 126.9729043), LatLon(37.3023403, 126.9728950), LatLon(37.3018008, 126.9728217), LatLon(37.3012676, 126.9727386), LatLon(37.3010702, 126.9727078), LatLon(37.3008336, 126.9726718), LatLon(37.3007621, 126.9726602), LatLon(37.3006613, 126.9726440), LatLon(37.3006089, 126.9726359), LatLon(37.3005066, 126.9726199), LatLon(37.3004382, 126.9726090), LatLon(37.3003651, 126.9725979), LatLon(37.3003302, 126.9725924), LatLon(37.3001168, 126.9725989), LatLon(37.2999323, 126.9725551), LatLon(37.2998048, 126.9725253), LatLon(37.2994144, 126.9723429), LatLon(37.2992361, 126.9722265), LatLon(37.2991835, 126.9721922), LatLon(37.2988991, 126.9719687), LatLon(37.2985364, 126.9716120), LatLon(37.2981507, 126.9712871), LatLon(37.2978834, 126.9710862), LatLon(37.2976326, 126.9709186), LatLon(37.2973228, 126.9707343), LatLon(37.2969797, 126.9705609), LatLon(37.2966456, 126.9704121), LatLon(37.2963651, 126.9703214), LatLon(37.2957134, 126.9701420), LatLon(37.2951560, 126.9700429), LatLon(37.2945853, 126.9699988), LatLon(37.2933727, 126.9699168)), oneway = true),
        Road("deok_n", "덕영대로(북쪽 방향)", listOf(LatLon(37.2814208, 126.9918616), LatLon(37.2816065, 126.9917507), LatLon(37.2843922, 126.9901193), LatLon(37.2844699, 126.9900728), LatLon(37.2847917, 126.9898883), LatLon(37.2849225, 126.9898111), LatLon(37.2865154, 126.9888242), LatLon(37.2873817, 126.9882875), LatLon(37.2890612, 126.9868786), LatLon(37.2890967, 126.9868448)), oneway = true),
        Road("deok_s", "덕영대로(남쪽 방향)", listOf(LatLon(37.2890036, 126.9867147), LatLon(37.2873087, 126.9881254), LatLon(37.2864192, 126.9887353), LatLon(37.2857011, 126.9891743), LatLon(37.2846387, 126.9897801), LatLon(37.2844105, 126.9899102), LatLon(37.2843259, 126.9899480), LatLon(37.2815516, 126.9915857), LatLon(37.2813613, 126.9916981)), oneway = true),
    ),
    noStops = listOf(
        NoStop(NoStopKind.CORNER, LatLon(37.3004644, 126.9723439)),
        NoStop(NoStopKind.CORNER, LatLon(37.3006193, 126.9730410)),
        NoStop(NoStopKind.CROSSWALK, LatLon(37.2844699, 126.9900728)),
        NoStop(NoStopKind.CROSSWALK, LatLon(37.2816065, 126.9917507)),
        NoStop(NoStopKind.CROSSWALK, LatLon(37.2814703, 126.9919975)),
        NoStop(NoStopKind.BUS, LatLon(37.2886307, 126.9869612)),
        NoStop(NoStopKind.BUS, LatLon(37.2884173, 126.9875539)),
        NoStop(NoStopKind.CROSSWALK, LatLon(37.2844105, 126.9899102)),
        NoStop(NoStopKind.CROSSWALK, LatLon(37.2815516, 126.9915857)),
        NoStop(NoStopKind.CORNER, LatLon(37.3008336, 126.9726718)),
        NoStop(NoStopKind.CORNER, LatLon(37.3003153, 126.9727673)),
        NoStop(NoStopKind.BUS, LatLon(37.3017262, 126.9731627)),
        NoStop(NoStopKind.BUS, LatLon(37.3015559, 126.9731316)),
        NoStop(NoStopKind.BUS, LatLon(37.3012016, 126.9726709)),
        NoStop(NoStopKind.BUS, LatLon(37.2837270, 126.9901402)),
        NoStop(NoStopKind.BUS, LatLon(37.2839945, 126.9904835)),
        NoStop(NoStopKind.BUS, LatLon(37.2815672, 126.9913860)),
        NoStop(NoStopKind.BUS, LatLon(37.2818178, 126.9917789)),
        NoStop(NoStopKind.BUS, LatLon(37.3026130, 126.9728391)),
        NoStop(NoStopKind.BUS, LatLon(37.2997884, 126.9728832)),
        NoStop(NoStopKind.BUS, LatLon(37.2992373, 126.9720582)),
        NoStop(NoStopKind.BUS, LatLon(37.2984571, 126.9713471)),
        NoStop(NoStopKind.BUS, LatLon(37.2981363, 126.9717195)),
        NoStop(NoStopKind.BUS, LatLon(37.2972740, 126.9709630)),
        NoStop(NoStopKind.BUS, LatLon(37.2969923, 126.9704507)),
        NoStop(NoStopKind.BUS, LatLon(37.2942460, 126.9698155)),
        NoStop(NoStopKind.BUS, LatLon(37.3050161, 126.9729902)),
        NoStop(NoStopKind.BUS, LatLon(37.3049137, 126.9725986)),
    ),
    landmarks = listOf(
        Landmark("skku1", "성균관대역 1번 출구", LatLon(37.3001516, 126.9725527), "seobu_s"),
        Landmark("skku2", "성균관대역 2번 출구", LatLon(37.2998365, 126.9724868), "seobu_s"),
        Landmark("seven", "세븐일레븐", LatLon(37.2989767, 126.9722065), "seobu_n"),
        Landmark("starbucks", "스타벅스", LatLon(37.2989097, 126.9719216), "seobu_s"),
        Landmark("lotte", "롯데슈퍼", LatLon(37.3011715, 126.9726781), "seobu_s"),
        Landmark("kb", "KB국민은행", LatLon(37.3013125, 126.9727001), "seobu_s"),
        Landmark("hotdog", "뉴욕 핫도그", LatLon(37.3006922, 126.9726034), "seobu_s"),
        Landmark("hwaseo3", "화서역 3번 출구", LatLon(37.2848334, 126.9896194), "deok_s"),
        Landmark("hwaseo4", "화서역 4번 출구", LatLon(37.2846351, 126.9897325), "deok_s"),
        Landmark("pharmacy", "화서역 약국", LatLon(37.2846337, 126.9900286), "deok_n"),
        // 2차: 세븐일레븐보다 길을 따라 96m 앞(차가 오는 쪽), 같은 길가. 후보로 넣으면 handsfree 1순위가 바뀌어 기준 전용이다(research R17)
        Landmark("oliveyoung", "올리브영", LatLon(37.2982708, 126.9715664), "seobu_n", spot = false),
    ),
    transit = listOf(
        TransitLeg("line1", "1호선", Stop("성균관대역", LatLon(37.3002852, 126.9711778)), Stop("화서역", LatLon(37.2839144, 126.9896135)), stops = 1, rideSec = 180, waitSec = 240),
    ),
)
