package com.kakario.householdbook

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 화면 색감 (설정에서 4가지 중 고름) */
data class Palette(val id: String, val name: String, val primary: Long, val card: Long)

val PALETTES = listOf(
    Palette("green", "초록", 0xFF2E7D5B, 0xFFF1F8F4),
    Palette("blue", "파랑", 0xFF2F6FB3, 0xFFEEF4FB),
    Palette("pink", "분홍", 0xFFC2506F, 0xFFFCF0F3),
    Palette("orange", "주황", 0xFFC7792A, 0xFFFBF4EA),
)

object ThemeState {
    var palette by mutableStateOf(PALETTES[0])

    fun load(ctx: Context): Palette {
        val id = ctx.getSharedPreferences("book", Context.MODE_PRIVATE).getString("theme", null)
        return PALETTES.firstOrNull { it.id == id } ?: PALETTES[0]
    }

    fun set(ctx: Context, p: Palette) {
        palette = p
        ctx.getSharedPreferences("book", Context.MODE_PRIVATE).edit().putString("theme", p.id).apply()
    }
}

/** 지금 고른 색 (화면에서 읽으면 색감 바꿀 때 바로 다시 그려짐) */
val Green: Color get() = Color(ThemeState.palette.primary)
val GreenSoft: Color get() = Green.copy(alpha = 0.14f)
val CardBg: Color get() = Color(ThemeState.palette.card)
