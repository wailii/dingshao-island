package com.xiaodeng.dingshao.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaodeng.dingshao.BuildConfig
import com.xiaodeng.dingshao.Copy
import kotlinx.coroutines.delay
import kotlin.random.Random

/** 哪个时间在自定义 */
enum class TimeTarget(val title: String, val hint: String) {
    Threshold("单次待多久就喊我", "从打开 App 开始算"),
    Daily("每天一共待多久就喊我", "几个 App 加起来算，零点清零"),
    Expand("岛亮以后，多久自动点开", "从岛亮起开始算"),
    Close("多久直接退回桌面", "从岛亮起开始算"),
}

private const val TWO_HOURS = 2 * 3600

/**
 * 自己填时间：一个大数字 + 单位（分钟 / 小时）。
 * 超过两小时先弹个框阴阳你一下，坚持的话照样给你设。
 */
@Composable
fun CustomTimeSheet(target: TimeTarget?, initialSec: Int, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val units = buildList {
        if (BuildConfig.DEBUG) add(Triple(1, "秒", ""))
        add(Triple(60, "分钟", ""))
        add(Triple(3600, "小时", ""))
    }
    var text by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(60) }
    var roast by remember { mutableStateOf<Pair<String, String>?>(null) }
    val focus = remember { FocusRequester() }
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(target) {
        if (target != null) {
            roast = null
            val s = initialSec.coerceAtLeast(0)
            when {
                s > 0 && s % 3600 == 0 -> { unit = 3600; text = "${s / 3600}" }
                s > 0 && s % 60 == 0 -> { unit = 60; text = "${s / 60}" }
                s > 0 && BuildConfig.DEBUG -> { unit = 1; text = "$s" }
                else -> { unit = 60; text = "" }
            }
            delay(280)
            runCatching { focus.requestFocus() }
        }
    }

    val n = text.toIntOrNull() ?: 0
    val sec = n * unit
    val valid = sec >= (if (BuildConfig.DEBUG) 5 else 60) && sec <= 24 * 3600
    val visible = target != null

    BackHandler(enabled = visible) { if (roast != null) roast = null else onDismiss() }

    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically(spring(0.86f, 400f)) { it },
        exit = slideOutVertically(tween(220)) { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Palette.Bg)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 20.dp)
            ) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(40.dp, 5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD6D4CE))
                )
                T(target?.title ?: "", 22, weight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
                T(target?.hint ?: "", 14, Palette.Sub, modifier = Modifier.padding(top = 4.dp))

                Row(
                    Modifier
                        .padding(top = 22.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Palette.Card)
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() }.take(4).trimStart('0') },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
                            fontFeatureSettings = "tnum",
                        ),
                        cursorBrush = SolidColor(Palette.Accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus),
                        decorationBox = { inner ->
                            if (text.isEmpty()) T("0", 52, Color(0xFFD0CEC7), FontWeight.Bold)
                            inner()
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        units.forEach { (u, label, _) ->
                            val on = unit == u
                            Box(
                                Modifier
                                    .pressable {
                                        unit = u
                                    }
                                    .clip(RoundedCornerShape(50))
                                    .background(if (on) Palette.Ink else Palette.Faint)
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            ) { T(label, 15, if (on) Color.White else Palette.Sub, FontWeight.SemiBold) }
                        }
                    }
                }
                T(
                    when {
                        n == 0 -> "填个数字"
                        !valid && sec < 60 -> "至少 1 分钟"
                        !valid -> "最多 24 小时"
                        else -> "= ${Copy.duration(sec.toLong())}"
                    },
                    14, if (n > 0 && !valid) Palette.Accent else Palette.Sub,
                    modifier = Modifier.padding(start = 6.dp, top = 10.dp),
                )
                Pill(
                    "好",
                    Modifier
                        .padding(top = 18.dp, bottom = 14.dp)
                        .fillMaxWidth(),
                    enabled = valid,
                    height = 56.dp,
                ) {
                    if (sec > TWO_HOURS && target != null) {
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        keyboard?.hide()
                        focusManager.clearFocus()
                        roast = roastFor(target, sec)
                    } else {
                        onDone(sec)
                    }
                }
            }
        }
    }

    Roast(
        roast,
        onKeep = {
            roast = null
            onDone(sec)
        },
        onShorter = {
            roast = null
            runCatching { focus.requestFocus() }
            keyboard?.show()
        },
    )
}

private fun roastFor(target: TimeTarget, sec: Int): Pair<String, String> {
    val d = Copy.duration(sec.toLong())
    val pool = when (target) {
        TimeTarget.Threshold -> listOf(
            "待满${d}才喊你？" to "行，那我先睡会儿。你刷完了记得叫醒我。",
            "${d}？" to "这不叫盯梢，叫陪刷。",
            "好家伙，${d}" to "你这是给自己批了半天假。",
        )
        TimeTarget.Daily -> listOf(
            "每天${d}？" to "那今天基本不用管了。",
            "一天刷${d}才喊你？" to "这不叫盯梢，叫陪刷。",
        )
        TimeTarget.Expand -> listOf(
            "${d}后才点开？" to "那会儿你都刷完一部剧了。",
            "${d}？" to "设这么长，我盯着都替你心虚。",
        )
        TimeTarget.Close -> listOf(
            "${d}后才退？" to "等我退的时候，今天都快过完了。",
            "${d}？" to "这么长，不如直接把我卸了。",
        )
    }
    return pool[Random.nextInt(pool.size)]
}

/** 阴阳弹窗：小岛半眯着眼斜着看你 */
@Composable
private fun Roast(content: Pair<String, String>?, onKeep: () -> Unit, onShorter: () -> Unit) {
    var last by remember { mutableStateOf(content) }
    if (content != null) last = content
    AnimatedVisibility(content != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x80000000))
                .clickable(remember { MutableInteractionSource() }, null) { onShorter() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                content != null,
                enter = scaleIn(spring(0.55f, 420f), initialScale = 0.82f) + fadeIn(),
                exit = scaleOut(tween(140), targetScale = 0.92f) + fadeOut(tween(140)),
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 28.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Palette.Card)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(132.dp, 56.dp)
                            .shadow(14.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x33000000), spotColor = Color(0x44000000))
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        Eyes(Modifier.size(84.dp, 40.dp), EyeMood.Judging)
                    }
                    Spacer(Modifier.height(20.dp))
                    T(last?.first ?: "", 22, weight = FontWeight.Bold, align = TextAlign.Center)
                    T(
                        last?.second ?: "", 15, Palette.Sub, align = TextAlign.Center, lineHeight = 22,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(22.dp))
                    Pill("改短点", Modifier.fillMaxWidth(), height = 52.dp, onClick = onShorter)
                    Spacer(Modifier.height(10.dp))
                    Pill("就这么定", Modifier.fillMaxWidth(), dark = false, height = 52.dp, onClick = onKeep)
                }
            }
        }
    }
}
