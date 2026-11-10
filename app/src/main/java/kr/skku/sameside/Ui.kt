package kr.skku.sameside

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

// 첫 앱 "안 써본 기능" Screens.kt·Views.kt에서 가져온 화면 도우미(plan "첫 앱에서 가져오는 것")

private val AVATARS = listOf(R.color.avatar1, R.color.avatar2, R.color.avatar3, R.color.avatar4, R.color.avatar5)

/** 화면을 코드로 조립하는 작은 도구들. 글자 크기는 contracts 머리 */
class Ui(val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density
    fun px(v: Number) = (v.toFloat() * density).toInt()
    fun c(id: Int) = ctx.getColor(id)

    fun text(s: CharSequence, sp: Float, color: Int = R.color.text1, weight: Int = 400): TextView = TextView(ctx).apply {
        text = s
        textSize = sp
        setTextColor(c(color))
        typeface = Typeface.create(Typeface.SANS_SERIF, weight, false)
        setLineSpacing(0f, 1.18f)
    }

    fun small(s: String) = text(s, 22f, R.color.text3, 600)
    fun headline(s: String, sp: Float = 48f) = text(s, sp, R.color.text1, 700)
    fun body(s: String, sp: Float = 26f) = text(s, sp, R.color.text2)

    fun round(fill: Int, radius: Float, line: Int = 0, width: Float = 1.5f) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius * density
        if (line != 0) setStroke(px(width), line)
    }

    fun dot(color: Int, size: Int = 12) = View(ctx).apply {
        background = round(c(color), 100f)
        layoutParams = LinearLayout.LayoutParams(px(size), px(size))
    }

    fun column(gap: Int, vararg views: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        views.forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { if (i > 0) topMargin = px(gap) }) }
    }

    /** 가로 줄. [grow] 번째 칸이 남은 폭을 차지한다(글자가 줄바꿈되게) */
    fun row(gap: Int, vararg views: View, grow: Int = -1) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { i, v ->
            val lp = if (i == grow) LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            else (v.layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            addView(v, lp.apply { if (i > 0) marginStart = px(gap) })
        }
    }

    fun card(vararg views: View, gap: Int = 16) = column(gap, *views).apply {
        background = round(c(R.color.card), 24f, c(R.color.line))
        setPadding(px(32), px(28), px(32), px(28))
    }

    fun chip(label: String, fg: Int, bg: Int, iconRes: Int = 0) = text(label, 20f, fg, 600).apply {
        background = round(c(bg), 100f)
        setPadding(px(18), px(9), px(20), px(9))
        if (iconRes != 0) {
            val d = ctx.getDrawable(iconRes)!!.mutate()
            d.setTint(c(fg))
            d.setBounds(0, 0, px(22), px(22))
            setCompoundDrawablesRelative(d, null, null, null)
            compoundDrawablePadding = px(8)
        }
    }

    /** 이름 첫 글자 원(3차 FR-046). 색은 이름마다 다섯 가지 중 하나다 */
    fun avatar(name: String, size: Int = 48) = text(name.take(1), size * 0.42f, R.color.on_accent, 700).apply {
        gravity = Gravity.CENTER
        background = round(c(AVATARS[Math.floorMod(name.hashCode(), AVATARS.size)]), 100f)
        layoutParams = LinearLayout.LayoutParams(px(size), px(size))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO // 이름은 옆 글자가 읽는다
    }

    fun banner(text: String) = row(14, dot(R.color.red), text(text, 22f, R.color.red_text, 600), grow = 1).apply {
        background = round(c(R.color.red_soft), 18f, c(R.color.red))
        setPadding(px(24), px(18), px(24), px(18))
    }
}
