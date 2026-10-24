package com.xiaodeng.dingshao.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFFF6F5F1)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF121212)
    val Sub = Color(0xFF8C8A84)
    val Faint = Color(0xFFEFEDE7)
    val Line = Color(0xFFE6E4DE)
    val Accent = Color(0xFFFF5A1F)
    val Good = Color(0xFF1F9D55)
    val IslandSub = Color(0xFF9A9893)
    val IslandButton = Color(0xFF2B2B2B)
}

/** 手指在屏幕上的位置（根坐标），眼睛跟着看；没按着时为 null */
val LocalFinger = staticCompositionLocalOf<MutableState<Offset?>> { mutableStateOf(null) }
