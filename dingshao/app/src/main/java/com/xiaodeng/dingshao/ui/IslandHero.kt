package com.xiaodeng.dingshao.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.xiaodeng.dingshao.AppCatalog
import com.xiaodeng.dingshao.R
import kotlinx.coroutines.delay

enum class HeroMode { Idle, Watching, Expanded }

/**
 * 主页正中间那颗「小岛」：和摄像头那颗原子岛一个样子。
 * 没开始时缩成一颗，开始盯梢后横着撑开，点一下展开成到点时的提醒卡片。
 */
@Composable
fun IslandHero(
    monitoring: Boolean,
    since: Long,
    expanded: Boolean,
    onTap: () -> Unit,
    sample: AppCatalog.Entry?,
    title: String,
    subtitle: String,
) {
    val mode = when {
        expanded -> HeroMode.Expanded
        monitoring -> HeroMode.Watching
        else -> HeroMode.Idle
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val full = maxWidth
        val t = updateTransition(mode, label = "hero")
        val w by t.animateDp({ spring(0.74f, 330f) }, label = "w") {
            when (it) {
                HeroMode.Idle -> 180.dp
                HeroMode.Watching -> min(full, 322.dp)
                HeroMode.Expanded -> full
            }
        }
        val h by t.animateDp({ spring(0.74f, 330f) }, label = "h") { if (it == HeroMode.Expanded) 212.dp else 70.dp }
        val r by t.animateDp({ spring(0.8f, 330f) }, label = "r") { if (it == HeroMode.Expanded) 44.dp else 35.dp }
        val ew by t.animateDp({ spring(0.7f, 300f) }, label = "ew") {
            when (it) {
                HeroMode.Idle -> 108.dp
                HeroMode.Watching -> 94.dp
                HeroMode.Expanded -> 62.dp
            }
        }
        val ex by t.animateDp({ spring(0.7f, 300f) }, label = "ex") {
            when (it) {
                HeroMode.Idle -> (180.dp - 108.dp) / 2
                HeroMode.Watching -> 16.dp
                HeroMode.Expanded -> 24.dp
            }
        }
        val ey by t.animateDp({ spring(0.7f, 300f) }, label = "ey") {
            when (it) {
                HeroMode.Expanded -> 26.dp
                HeroMode.Idle -> (70.dp - 108.dp * 0.48f) / 2
                HeroMode.Watching -> (70.dp - 94.dp * 0.48f) / 2
            }
        }
        val watchAlpha by t.animateFloat(
            { if (targetState == HeroMode.Watching) tween(260, delayMillis = 140) else tween(90) },
            label = "wa",
        ) { if (it == HeroMode.Watching) 1f else 0f }
        val expAlpha by t.animateFloat(
            { if (targetState == HeroMode.Expanded) tween(260, delayMillis = 150) else tween(90) },
            label = "xa",
        ) { if (it == HeroMode.Expanded) 1f else 0f }

        Box(
            Modifier
                .width(w)
                .height(h)
                .pressable(scaleTo = 0.97f, onClick = onTap)
                .shadow(22.dp, RoundedCornerShape(r), ambientColor = Color(0x33000000), spotColor = Color(0x55000000))
                .clip(RoundedCornerShape(r))
                .background(Color.Black)
        ) {
            Eyes(
                Modifier
                    .offset(ex, ey)
                    .width(ew)
                    .height(ew * 0.48f),
                when (mode) {
                    HeroMode.Idle -> EyeMood.Sleepy
                    HeroMode.Watching -> EyeMood.Watching
                    HeroMode.Expanded -> EyeMood.Curious
                },
            )
            if (watchAlpha > 0.01f) {
                WatchingClock(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .alpha(watchAlpha),
                    since,
                )
            }
            if (expAlpha > 0.01f) {
                ExpandedCard(Modifier.fillMaxSize().alpha(expAlpha), sample, title, subtitle, expanded)
            }
        }
    }
}

@Composable
private fun WatchingClock(modifier: Modifier, since: Long) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000L - value % 1000L)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(
        1f, 0.25f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .alpha(a)
                .clip(CircleShape)
                .background(Palette.Accent)
        )
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            T("盯梢中", 11, Palette.IslandSub, FontWeight.Medium)
            // 跨天的话只从今天零点算，不显示几十个小时
            val from = maxOf(since, startOfDay(now))
            T(clock(now - from), 20, Color.White, FontWeight.SemiBold, tabular = true, lineHeight = 22)
        }
    }
}

/** 到点时岛上展开的样子：标题、正文、进度条、两个按钮。这里只是预览，按钮不会真的跳走 */
@Composable
private fun ExpandedCard(
    modifier: Modifier,
    sample: AppCatalog.Entry?,
    title: String,
    subtitle: String,
    expanded: Boolean,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            progress.snapTo(0.04f)
            delay(260)
            progress.animateTo(0.42f, tween(1500, easing = FastOutSlowInEasing))
        }
    }
    Box(modifier) {
        Column(
            Modifier
                .padding(start = 100.dp, top = 22.dp, end = if (sample != null) 66.dp else 22.dp)
        ) {
            T(title, 16, Color.White, FontWeight.SemiBold, maxLines = 1)
            T(subtitle, 13, Palette.IslandSub, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        sample?.icon?.let { bmp ->
            val img = remember(bmp) { bmp.asImageBitmap() }
            Image(
                img, null,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 22.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
        ProgressMock(
            Modifier
                .padding(start = 22.dp, end = 22.dp, top = 88.dp)
                .fillMaxWidth()
                .height(24.dp),
            progress.value,
            sample,
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 22.dp, end = 22.dp, bottom = 22.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(if (com.xiaodeng.dingshao.Xuexi.installed()) "去学一节" else "回去专注", "再刷 5 分钟").forEach {
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Palette.IslandButton),
                    contentAlignment = Alignment.Center,
                ) { T(it, 15, Color.White, FontWeight.SemiBold) }
            }
        }
    }
}

/** 和岛上那条一样：一根导火索，走过的亮、前面的暗，眼睛骑在线上，线头是💢 */
@Composable
private fun ProgressMock(modifier: Modifier, p: Float, sample: AppCatalog.Entry?) {
    val anger = painterResource(R.drawable.anger_mark)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            val full = maxWidth
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                val line = Color(0xFFF2F1EC)
                drawRoundRect(line.copy(alpha = 0.22f), cornerRadius = r)
                drawRoundRect(line, size = androidx.compose.ui.geometry.Size(size.width * p, size.height), cornerRadius = r)
            }
            Box(
                Modifier
                    .offset(x = (full - 30.dp) * p)
                    .size(width = 30.dp, height = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .padding(horizontal = 3.dp, vertical = 4.dp)
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawEyes(0.7f, 0.1f, 0f, 1f) }
            }
        }
        Spacer(Modifier.width(10.dp))
        Image(anger, null, Modifier.size(18.dp))
    }
}

private fun startOfDay(t: Long): Long {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = t
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
