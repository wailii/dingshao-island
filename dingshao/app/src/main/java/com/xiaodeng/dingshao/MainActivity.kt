package com.xiaodeng.dingshao

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.xiaodeng.dingshao.ui.HomeScreen
import com.xiaodeng.dingshao.ui.LogScreen
import com.xiaodeng.dingshao.ui.SettingsScreen
import com.xiaodeng.dingshao.ui.SelfCheckScreen
import com.xiaodeng.dingshao.ui.LocalFinger
import com.xiaodeng.dingshao.ui.SetupScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContent { Root() }
    }

    @androidx.compose.runtime.Composable
    private fun Root() {
        var perms by remember { mutableStateOf(Perms.read(this)) }
        val onboarded by Prefs.onboarded.collectAsState()
        var setup by remember { mutableStateOf(false) }
        val finger = remember { mutableStateOf<Offset?>(null) }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            Perms.onAppResumed()
            perms = Perms.read(this@MainActivity)
            Prefs.rollStats()
            // 学习那边今天学了几节、还剩几节；学完的节数涨了顺手庆祝
            Xuexi.refresh { Engine.onStudyStatus(it) }
        }
        // 从系统设置页回来时，开关状态有时晚一点才生效，在前台时每秒复查一次
        val owner = LocalLifecycleOwner.current
        LaunchedEffect(owner) {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(1000)
                    perms = Perms.read(this@MainActivity)
                }
            }
        }

        // 一层层进、一层层退：设置 → 自检，返回回到设置
        val stack = remember { mutableStateListOf(Page.Home) }
        val page = stack.last()
        val go: (Page) -> Unit = { stack.add(it) }
        val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
        val screen = if (!onboarded && page == Page.Home) Page.Setup else if (setup) Page.Setup else page
        BackHandler(enabled = screen != Page.Home && (onboarded || screen != Page.Setup)) {
            when {
                setup -> setup = false
                else -> back()
            }
        }

        CompositionLocalProvider(LocalFinger provides finger) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val c = ev.changes.firstOrNull() ?: continue
                                finger.value = if (c.pressed) c.position else null
                            }
                        }
                    }
            ) {
                AnimatedContent(
                    screen,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith
                            (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 1.02f))
                    },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Page.Setup -> SetupScreen(
                            perms = perms,
                            canClose = onboarded,
                            onClose = { setup = false },
                            onDone = {
                                Prefs.setOnboarded()
                                setup = false
                            },
                            onCheck = {
                                setup = false
                                go(Page.Check)
                            },
                        )
                        Page.Log -> LogScreen(onBack = back, onCheck = { go(Page.Check) })
                        Page.Settings -> SettingsScreen(
                            perms = perms,
                            onBack = back,
                            onOpenSetup = { setup = true },
                            onOpenCheck = { go(Page.Check) },
                        )
                        Page.Check -> SelfCheckScreen(perms, onBack = back, onTest = { realPreview() })
                        Page.Home -> HomeScreen(
                            perms = perms,
                            onOpenSetup = { setup = true },
                            onOpenLog = { go(Page.Log) },
                            onOpenSettings = { go(Page.Settings) },
                            onRealPreview = { realPreview() },
                        )
                    }
                }
            }
        }
    }

    private enum class Page { Home, Setup, Settings, Log, Check }

    /** 拿真的岛演示一遍：先退到后台，岛才会亮。「进岛」开关没开也照发，自检要看系统怎么处理 */
    private fun realPreview() {
        Journal.add(Journal.Kind.Island, "发了一个测试岛")
        val pkg = Prefs.watched.value.firstOrNull() ?: "com.xingin.xhs"
        val th = Prefs.thresholdSec.value
        moveTaskToBack(true)
        window.decorView.postDelayed({
            val start = System.currentTimeMillis() - th * 1000L
            IslandNotifier.showCatch(
                IslandNotifier.Catch(pkg, start, th, snoozed = false, snoozedJustNow = false, closeAt = 0L, seed = 0L),
                alert = true, preview = true,
            )
        }, 700)
    }
}
