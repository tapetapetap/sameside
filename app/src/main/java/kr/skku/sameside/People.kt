package kr.skku.sameside

import android.content.Context

data class Person(val name: String, val phone: String, val careful: Boolean)

data class Car(val model: String, val color: String, val plate: String, val phone: String)

/** 저장한 사람과 차 정보(data-model §1). 형식·검증 함수는 순수하고, 아래 여섯만 SharedPreferences를 쓴다 */
object People {
    private fun clean(s: String) = s.replace(Regex("[\t\r\n]"), "").trim()

    /** 1~10자. 틀리면 null */
    fun name(s: String): String? = clean(s).takeIf { it.length in 1..10 }

    /** 숫자만 남겨 9~11자리. 틀리면 null */
    fun phone(s: String): String? = s.filter { it.isDigit() }.takeIf { it.length in 9..11 }

    /** 각 칸 0~20자, 운전자 번호는 비우거나 9~11자리. 틀리면 null */
    fun car(model: String, color: String, plate: String, phone: String): Car? {
        val f = listOf(model, color, plate).map(::clean)
        if (f.any { it.length > 20 }) return null
        val p = if (phone.isBlank()) "" else phone(phone) ?: return null
        return Car(f[0], f[1], f[2], p)
    }

    fun encode(list: List<Person>) = list.joinToString("\n") { "${it.name}\t${it.phone}\t${if (it.careful) 1 else 0}" }

    fun decode(text: String): List<Person> = text.lines().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != 3 || f[2] !in setOf("0", "1")) return@mapNotNull null
        val n = name(f[0]) ?: return@mapNotNull null
        val p = phone(f[1]) ?: return@mapNotNull null
        Person(n, p, f[2] == "1")
    }

    fun encodeCar(c: Car) = "${c.model}\t${c.color}\t${c.plate}\t${c.phone}"

    fun decodeCar(text: String): Car? = text.split('\t').takeIf { it.size == 4 }?.let { car(it[0], it[1], it[2], it[3]) }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("people", Context.MODE_PRIVATE)
    fun load(ctx: Context) = decode(prefs(ctx).getString("people", "").orEmpty())
    fun save(ctx: Context, list: List<Person>) = prefs(ctx).edit().putString("people", encode(list)).apply()
    fun loadCar(ctx: Context) = decodeCar(prefs(ctx).getString("car", "").orEmpty())
    fun saveCar(ctx: Context, c: Car) = prefs(ctx).edit().putString("car", encodeCar(c)).apply()

    /** 앱이 마지막으로 내비 목적지로 넣은 지점 이름(2차 research R15). 이것과 같은 목적지는 "원래 목적지"가 아니다 */
    fun loadNavOwn(ctx: Context): String? = prefs(ctx).getString("navOwn", null)
    fun saveNavOwn(ctx: Context, name: String) = prefs(ctx).edit().putString("navOwn", name).apply()
}
