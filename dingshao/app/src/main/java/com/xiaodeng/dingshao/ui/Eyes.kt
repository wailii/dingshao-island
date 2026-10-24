package com.xiaodeng.dingshao.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class EyeMood {
    /** 没开始盯：眼皮耷拉一点，慢悠悠地瞟 */
    Sleepy,

    /** 盯梢中：眼睛睁圆，左右来回扫 */
    Watching,

    /** 展开预览：往右边的文字看 */
    Curious,

    /** 阴阳你：眼皮压下来一半，斜着眼看你 */
    Judging,
}

/**
 * 一对会眨、会看你手指的眼睛。左小右大，学专注狗那只。
 */
@Composable
fun Eyes(modifier: Modifier, mood: EyeMood) {
    val finger = LocalFinger.current.value
    var center by remember { mutableStateOf(Offset.Unspecified) }

    var auto by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(mood) {
        when (mood) {
            EyeMood.Sleepy -> while (true) {
                auto = Offset(rnd(-0.4f, 0.4f), rnd(0.1f, 0.5f))
                delay(Random.nextLong(1600, 3600))
            }

            EyeMood.Watching -> {
                val path = listOf(
                    Offset(-0.95f, 0.05f) to 1000L,
                    Offset(0.95f, 0.05f) to 1100L,
                    Offset(-0.95f, 0.1f) to 900L,
                    Offset(0.3f, -0.6f) to 600L,
                    Offset(0.95f, 0.35f) to 800L,
                    Offset(0f, 0f) to 1200L,
                )
                while (true) for ((o, d) in path) {
                    auto = o
                    delay(d)
                }
            }

            EyeMood.Judging -> while (true) {
                auto = Offset(0.8f, 0.45f)
                delay(2200)
                auto = Offset(0.55f, 0.5f)
                delay(900)
            }

            EyeMood.Curious -> while (true) {
                auto = Offset(rnd(0.45f, 0.9f), rnd(-0.25f, 0.3f))
                delay(Random.nextLong(1100, 2400))
            }
        }
    }

    val goal = if (finger != null && center.isSpecified) {
        val d = finger - center
        val dist = d.getDistance()
        if (dist < 1f) Offset.Zero else d / dist * (dist / 380f).coerceIn(0f, 1f)
    } else {
        auto
    }
    val follow = finger != null
    val lx by animateFloatAsState(goal.x, spring(0.62f, if (follow) 700f else 140f), label = "lx")
    val ly by animateFloatAsState(goal.y, spring(0.62f, if (follow) 700f else 140f), label = "ly")
    val lid by animateFloatAsState(
        when {
            mood == EyeMood.Judging -> 0.5f
            mood == EyeMood.Sleepy && !follow -> 0.34f
            else -> 0f
        },
        tween(420), label = "lid",
    )

    val blink = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2000, 5200))
            blink.animateTo(1f, tween(70))
            blink.animateTo(0f, tween(130))
            if (Random.nextFloat() < 0.22f) {
                delay(80)
                blink.animateTo(1f, tween(70))
                blink.animateTo(0f, tween(130))
            }
        }
    }

    // 开始盯的那一下，眼睛猛地睁大再弹回来
    val pop = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        if (mood == EyeMood.Watching) {
            blink.snapTo(0f)
            pop.animateTo(1.2f, tween(110))
            pop.animateTo(1f, spring(0.36f, 260f))
        }
    }

    Canvas(
        modifier.onGloballyPositioned {
            center = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
        }
    ) {
        drawEyes(lx, ly, maxOf(lid, blink.value), pop.value)
    }
}

/** 画两只眼睛。look 范围 -1..1，close 0 睁开 1 闭上 */
fun DrawScope.drawEyes(lookX: Float, lookY: Float, close: Float, pop: Float) {
    val w = size.width
    val h = size.height
    val eyes = listOf(
        Triple(w * 0.27f, h * 0.50f, h * 0.38f),
        Triple(w * 0.735f, h * 0.53f, h * 0.44f),
    )
    for ((cx, cy, r0) in eyes) {
        val r = r0 * pop
        val ry = (r * (1f - close)).coerceAtLeast(r * 0.07f)
        val rect = Rect(cx - r, cy - ry, cx + r, cy + ry)
        drawOval(Color.White, rect.topLeft, Size(rect.width, rect.height))
        val pr = r * 0.45f
        val px = cx + lookX.coerceIn(-1f, 1f) * (r - pr) * 0.92f
        val py = cy + lookY.coerceIn(-1f, 1f) * (r - pr) * 0.92f
        clipPath(Path().apply { addOval(rect) }) {
            drawCircle(Color(0xFF111111), pr, Offset(px, py))
            drawCircle(Color.White.copy(alpha = 0.92f), pr * 0.26f, Offset(px - pr * 0.36f, py - pr * 0.38f))
        }
    }
}

private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
