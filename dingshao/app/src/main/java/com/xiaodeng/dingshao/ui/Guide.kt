package com.xiaodeng.dingshao.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 系统设置页里的一行，用来画「跳过去以后点哪」的示意图 */
data class MockRow(
    val title: String,
    val sub: String? = null,
    val icon: MockIcon? = null,
    val trailing: MockTrailing = MockTrailing.Chevron,
    val highlight: Boolean = false,
)

enum class MockIcon { Eyes, Orange, Plain }

sealed class MockTrailing {
    data object Chevron : MockTrailing()
    data class Status(val text: String) : MockTrailing()
    data class Switch(val on: Boolean) : MockTrailing()
}

/** 一步：一句话 + 一张示意图（列表或弹窗） */
data class GuideStep(
    val text: String,
    val header: String? = null,
    val rows: List<MockRow> = emptyList(),
    val dialog: Pair<String, List<String>>? = null,
)

data class Guide(
    val title: String,
    val steps: List<GuideStep>,
    val tip: String? = null,
    val toast: String,
    val go: String = "去设置",
)

/** 跳去系统设置之前，先告诉你过去以后点哪 */
@Composable
fun GuideSheet(guide: Guide?, onGo: () -> Unit, onDismiss: () -> Unit) {
    var last by remember { mutableStateOf(guide) }
    if (guide != null) last = guide
    val g = last
    BackHandler(enabled = guide != null) { onDismiss() }

    AnimatedVisibility(guide != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
        )
    }
    AnimatedVisibility(
        guide != null,
        enter = slideInVertically(spring(0.86f, 400f)) { it },
        exit = slideOutVertically(tween(220)) { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Palette.Bg)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(40.dp, 5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD6D4CE))
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    T(g?.title ?: "", 24, weight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
                    T("跳过去以后这么点", 14, Palette.Sub, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                    g?.steps?.forEachIndexed { i, step ->
                        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Palette.Ink),
                                contentAlignment = Alignment.Center,
                            ) { T("${i + 1}", 13, Color.White, FontWeight.Bold) }
                            T(step.text, 16, Palette.Ink, FontWeight.Medium, Modifier.padding(start = 10.dp, top = 1.dp), lineHeight = 22)
                        }
                        if (step.rows.isNotEmpty()) MockSettings(step.header, step.rows, Modifier.padding(start = 34.dp, top = 10.dp))
                        step.dialog?.let { (title, buttons) -> MockDialog(title, buttons, Modifier.padding(start = 34.dp, top = 10.dp)) }
                    }
                    g?.tip?.let {
                        Column(
                            Modifier
                                .padding(top = 20.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFFFEDE4))
                                .padding(14.dp)
                        ) {
                            T(it, 14, Color(0xFF8A3B1F), lineHeight = 20)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Pill(
                    g?.go ?: "",
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    height = 56.dp,
                    onClick = onGo,
                )
            }
        }
    }
}

/** 一块设置页的样子：可选的小标题 + 几行，要点的那行用会呼吸的橙框圈出来 */
@Composable
fun MockSettings(header: String?, rows: List<MockRow>, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "hl")
    val a by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "hla")
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF0EFEC))
            .padding(10.dp)
    ) {
        header?.let { T(it, 12, Palette.Sub, FontWeight.Medium, Modifier.padding(start = 6.dp, bottom = 6.dp)) }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
        ) {
            rows.forEach { r ->
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        r.icon?.let { MockAppIcon(it) ; Spacer(Modifier.width(10.dp)) }
                        Column(Modifier.weight(1f)) {
                            T(r.title, 14, Palette.Ink, if (r.highlight) FontWeight.SemiBold else FontWeight.Normal)
                            r.sub?.let { T(it, 11, Palette.Sub) }
                        }
                        when (val t = r.trailing) {
                            MockTrailing.Chevron -> T("›", 18, Palette.Sub)
                            is MockTrailing.Status -> T("${t.text}  ›", 12, Palette.Sub)
                            is MockTrailing.Switch -> MockSwitch(t.on)
                        }
                    }
                    if (r.highlight) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .padding(2.dp)
                                .alpha(a)
                                .border(2.dp, Palette.Accent, RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MockAppIcon(icon: MockIcon) {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when (icon) {
                    MockIcon.Eyes -> Color(0xFF111111)
                    MockIcon.Orange -> Color(0xFFFFE9DE)
                    MockIcon.Plain -> Color(0xFFDDDBD5)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != MockIcon.Plain) {
            Canvas(Modifier.size(18.dp, 9.dp)) {
                if (icon == MockIcon.Orange) {
                    drawCircle(Color(0xFFF26B3A), size.height * 1.1f, center)
                }
                drawEyes(0.5f, 0.2f, 0f, if (icon == MockIcon.Orange) 0.7f else 1f)
            }
        }
    }
}

@Composable
private fun MockSwitch(on: Boolean) {
    Box(
        Modifier
            .size(36.dp, 20.dp)
            .clip(CircleShape)
            .background(if (on) Color(0xFF3B6FF5) else Color(0xFFD9D7D1))
            .padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** 系统弹窗的样子，第一个按钮是要点的那个 */
@Composable
fun MockDialog(title: String, buttons: List<String>, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "dl")
    val a by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dla")
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF0EFEC))
            .padding(10.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T(title, 14, Palette.Ink, FontWeight.SemiBold)
            buttons.forEachIndexed { i, b ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF0EFEC)),
                    contentAlignment = Alignment.Center,
                ) {
                    T(b, 13, if (i == 0) Color(0xFFE5483B) else Palette.Ink, FontWeight.SemiBold)
                    if (i == 0) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .alpha(a)
                                .border(2.dp, Palette.Accent, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
