package com.xiaodeng.dingshao.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaodeng.dingshao.BuildConfig
import com.xiaodeng.dingshao.Copy
import com.xiaodeng.dingshao.Engine
import com.xiaodeng.dingshao.Perms
import com.xiaodeng.dingshao.Prefs
import com.xiaodeng.dingshao.Xuexi

/** 各项时间的常用档，最后一格都是「自定义」 */
object Rules {
    val SINGLE = buildList {
        if (BuildConfig.DEBUG) add(Triple(10, "10", "秒"))
        add(Triple(60, "1", "分钟"))
        add(Triple(180, "3", "分钟"))
        add(Triple(300, "5", "分钟"))
        if (!BuildConfig.DEBUG) add(Triple(600, "10", "分钟"))
    }

    val DAILY = buildList {
        add(Triple(Prefs.OFF, "不限", ""))
        if (BuildConfig.DEBUG) add(Triple(30, "30", "秒"))
        add(Triple(1800, "30", "分钟"))
        add(Triple(3600, "1", "小时"))
        if (!BuildConfig.DEBUG) add(Triple(7200, "2", "小时"))
    }

    val EXPAND = buildList {
        add(Triple(Prefs.OFF, "不点", ""))
        add(Triple(0, "马上", ""))
        if (BuildConfig.DEBUG) add(Triple(10, "10", "秒"))
        add(Triple(60, "1", "分钟"))
        if (!BuildConfig.DEBUG) add(Triple(180, "3", "分钟"))
    }

    val CLOSE = buildList {
        add(Triple(Prefs.OFF, "不退", ""))
        if (BuildConfig.DEBUG) add(Triple(25, "25", "秒"))
        add(Triple(300, "5", "分钟"))
        add(Triple(600, "10", "分钟"))
        if (!BuildConfig.DEBUG) add(Triple(900, "15", "分钟"))
    }

    /** 主页规则卡片上的两行字 */
    fun summary(single: Int, daily: Int, expand: Int, close: Int): Pair<String, String> {
        val a = "单次 ${Copy.duration(single.toLong())}" + if (daily > 0) " · 每天 ${Copy.duration(daily.toLong())}" else " · 每天不限"
        val b = listOf(
            when {
                expand == Prefs.OFF -> "岛亮后不自动点开"
                expand == 0 -> "岛亮后马上点开"
                else -> "岛亮 ${Copy.duration(expand.toLong())}后点开"
            },
            if (close == Prefs.OFF) "不退回桌面" else "${Copy.duration(close.toLong())}后退回桌面",
        ).joinToString(" · ")
        return a to b
    }

    /** 调试版的秒级选项到了正式版就不认了，还原成默认 */
    fun sanitize() {
        if (BuildConfig.DEBUG) return
        if (Prefs.thresholdSec.value < 60) Prefs.setThreshold(180)
        if (Prefs.expandSec.value in 1..59) Prefs.setExpand(180)
        if (Prefs.closeSec.value in 1..59) Prefs.setClose(Prefs.OFF)
        if (Prefs.dailySec.value in 1..59) Prefs.setDaily(3600)
    }
}

@Composable
fun SettingsScreen(perms: Perms.State, onBack: () -> Unit, onOpenSetup: () -> Unit, onOpenCheck: () -> Unit) {
    val single by Prefs.thresholdSec.collectAsState()
    val daily by Prefs.dailySec.collectAsState()
    val expand by Prefs.expandSec.collectAsState()
    val close by Prefs.closeSec.collectAsState()
    var custom by remember { mutableStateOf<TimeTarget?>(null) }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                T("←", 24, Palette.Ink, modifier = Modifier.pressable(onClick = onBack).padding(8.dp))
            }
            T("设置", 30, weight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 18.dp))

            Panel {
                Label("单次待多久就喊我")
                Spacer(Modifier.height(12.dp))
                TimePicker(Rules.SINGLE, single, { Prefs.setThreshold(it); Engine.onRulesChanged() }) { custom = TimeTarget.Threshold }
                Spacer(Modifier.height(22.dp))
                Label("每天一共待多久就喊我")
                Spacer(Modifier.height(12.dp))
                TimePicker(Rules.DAILY, daily, { Prefs.setDaily(it); Engine.onRulesChanged() }) { custom = TimeTarget.Daily }
                T("几个 App 加起来算，零点清零", 13, Palette.Sub, modifier = Modifier.padding(top = 10.dp))
                if (Xuexi.installed()) T("在学习里每学完一节，多给 10 分钟", 13, Palette.Good, modifier = Modifier.padding(top = 2.dp))
            }

            Spacer(Modifier.height(12.dp))
            Panel {
                Label("岛亮以后，多久自动点开")
                Spacer(Modifier.height(12.dp))
                TimePicker(Rules.EXPAND, expand, { Prefs.setExpand(it); Engine.onRulesChanged() }) { custom = TimeTarget.Expand }
                Spacer(Modifier.height(22.dp))
                Label("多久直接退回桌面")
                Spacer(Modifier.height(12.dp))
                TimePicker(Rules.CLOSE, close, { Prefs.setClose(it); Engine.onRulesChanged() }) { custom = TimeTarget.Close }
                AnimatedVisibility(close != Prefs.OFF, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    T("1 分钟内再打开，会再退一次", 13, Palette.Sub, modifier = Modifier.padding(top = 10.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Panel {
                NavRow("权限", dot = (!perms.ready || perms.a11yStuck) && !perms.a11yRecovering, onClick = onOpenSetup)
                NavRow("自检", hint = "看这台手机的岛能不能亮", onClick = onOpenCheck)
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Palette.Bg, Palette.Bg, Palette.Bg.copy(alpha = 0f))))
                .windowInsetsTopHeight(WindowInsets.statusBars)
        )
        CustomTimeSheet(
            target = custom,
            initialSec = when (custom) {
                TimeTarget.Threshold -> single
                TimeTarget.Daily -> daily
                TimeTarget.Expand -> expand
                TimeTarget.Close -> close
                null -> 0
            },
            onDone = { sec ->
                when (custom) {
                    TimeTarget.Threshold -> Prefs.setThreshold(sec)
                    TimeTarget.Daily -> Prefs.setDaily(sec)
                    TimeTarget.Expand -> Prefs.setExpand(sec)
                    TimeTarget.Close -> Prefs.setClose(sec)
                    null -> {}
                }
                Engine.onRulesChanged()
                custom = null
            },
            onDismiss = { custom = null },
        )
    }
}

@Composable
private fun NavRow(title: String, hint: String? = null, dot: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(scaleTo = 0.98f, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(title, 16, Palette.Ink, FontWeight.Medium)
        if (dot) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Accent))
        }
        Spacer(Modifier.weight(1f))
        hint?.let { T(it, 13, Palette.Sub, modifier = Modifier.padding(end = 8.dp)) }
        T("›", 20, Palette.Sub)
    }
}
