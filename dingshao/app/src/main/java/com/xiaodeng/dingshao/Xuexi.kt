package com.xiaodeng.dingshao

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * 和「学习」App 联动。
 * 学习把今天还剩几节、下一节、学完几节、在学习里待了多久、岛上用的判断题放在 content://com.xiaodeng.xuexi.status；
 * 这边把今天刷了多久、岛上答了哪些题放在 [StatusProvider] 给它读。学完一节它发 [LESSON_DONE] 过来。
 */
object Xuexi {
    const val PKG = "com.xiaodeng.xuexi"
    const val LESSON_DONE = "com.xiaodeng.dingshao.LESSON_DONE"
    private val URI = Uri.parse("content://$PKG.status")

    /** 每学完一节，「每天一共」多给 10 分钟 */
    const val BONUS_SEC = 600

    data class Quiz(val id: String, val cat: String, val q: String, val a: Boolean, val why: String)

    data class Status(
        val date: String,
        val left: Int,
        val min: Int,
        val done: Int,
        val studySec: Long,
        val nextCat: String?,
        val nextText: String?,
        val quiz: List<Quiz>,
    )

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        _status.value = Prefs.str(K_CACHE)?.let { parse(it) }
    }

    private var instAt = 0L
    private var inst = false

    /** 装没装学习；岛每秒重发都要问，半分钟查一次 */
    fun installed(): Boolean {
        if (!::app.isInitialized) return false
        val now = System.currentTimeMillis()
        if (now - instAt > 30_000L) {
            inst = runCatching { app.packageManager.getPackageInfo(PKG, 0); true }.getOrDefault(false)
            instAt = now
        }
        return inst
    }

    /** 今天的；昨天缓存下来的剩几节、学完几节不算 */
    fun today(): Status? = _status.value?.takeIf { it.date == LocalDate.now().toString() }

    /** 今天学完几节换来的额度（秒） */
    fun bonusSec(): Int = (today()?.done ?: 0) * BONUS_SEC

    /**
     * 去学习那边现读一次（后台线程），读到就缓存；读不到（没装、进程被冻住）留着上次的。
     * 读完在主线程回调今天的状态。
     */
    fun refresh(done: ((Status?) -> Unit)? = null) {
        if (!::app.isInitialized) return
        io.execute {
            val raw = runCatching {
                app.contentResolver.query(URI, null, null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull()
            val s = raw?.let { parse(it) }
            if (s != null) {
                _status.value = s
                Prefs.setStr(K_CACHE, raw)
            }
            if (done != null) main.post { done(today()) }
        }
    }

    private fun parse(raw: String): Status? = runCatching {
        val j = JSONObject(raw)
        val next = j.optJSONObject("next")
        val qs = j.optJSONArray("quiz") ?: JSONArray()
        Status(
            date = j.optString("date"),
            left = j.optInt("left"),
            min = j.optInt("min"),
            done = j.optInt("done"),
            studySec = j.optLong("studySec"),
            nextCat = next?.optString("cat")?.ifEmpty { null },
            nextText = next?.optString("text")?.ifEmpty { null },
            quiz = (0 until qs.length()).mapNotNull { i ->
                qs.optJSONObject(i)?.let { q ->
                    Quiz(q.optString("id"), q.optString("cat"), q.optString("q"), q.optBoolean("a"), q.optString("why"))
                        .takeIf { it.id.isNotEmpty() && it.q.isNotEmpty() }
                }
            },
        )
    }.getOrNull()

    /** 打开学习，直接开下一节；from 让学习那边的便利贴说对应的话（island 岛上点的 / kick 退了三回 / wrong 答错了） */
    fun intent(from: String): Intent = Intent()
        .setClassName(PKG, "$PKG.MainActivity")
        .putExtra("go", "next")
        .putExtra("from", from)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun pending(ctx: Context, from: String): PendingIntent = PendingIntent.getActivity(
        ctx, REQ[from] ?: 30, intent(from),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun open(ctx: Context, from: String): Boolean = runCatching {
        ctx.startActivity(intent(from))
        true
    }.getOrElse {
        Journal.error("打开学习", it)
        false
    }

    private val REQ = mapOf("island" to 31, "wrong" to 32, "kick" to 33)

    /** 最近问过的题，别连着问同一道 */
    private val asked = ArrayDeque<String>()

    /** 挑一道题：今天答对过的、最近问过的先不出；题库是学习那边学完一节出的，不分哪天 */
    fun pickQuiz(): Quiz? {
        val bank = _status.value?.quiz.orEmpty()
        if (bank.isEmpty()) return null
        val today = LocalDate.now().toString()
        val right = answers().let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .filter { it.optBoolean("ok") && Instant.ofEpochMilli(it.optLong("at")).atZone(ZoneId.systemDefault()).toLocalDate().toString() == today }
                .map { it.optString("id") }.toSet()
        }
        val pool = bank.filter { it.id !in right && it.id !in asked }.ifEmpty { bank.filter { it.id !in asked } }.ifEmpty { bank }
        val q = pool.random()
        asked.addLast(q.id)
        while (asked.size > 5) asked.removeFirst()
        return q
    }

    /**
     * 记一笔「刚把人往学习送」：vivo 拦一道「盯梢岛想要打开学习」再放行时会把打开时带的参数丢掉，
     * 学习回到前台时来问，20 秒内的照样当成从这边送过去的
     */
    fun markGo(from: String) = Prefs.setStr(K_GO, "$from|${System.currentTimeMillis()}")

    fun goJson(): JSONObject? = Prefs.str(K_GO)?.split("|")?.takeIf { it.size == 2 }?.let {
        JSONObject().put("from", it[0]).put("at", it[1].toLongOrNull() ?: 0L)
    }

    /** 岛上答了一道：记下来，学习那边读到答错的会把那节排进今天复习 */
    fun answer(q: Quiz, ok: Boolean) {
        val all = answers().put(JSONObject().put("id", q.id).put("ok", ok).put("at", System.currentTimeMillis()))
        val keep = JSONArray()
        for (i in maxOf(0, all.length() - 60) until all.length()) keep.put(all.get(i))
        Prefs.setStr(K_ANSWERS, keep.toString())
    }

    fun answers(): JSONArray = runCatching { JSONArray(Prefs.str(K_ANSWERS) ?: "[]") }.getOrDefault(JSONArray())

    private const val K_CACHE = "xuexi"
    private const val K_ANSWERS = "xuexi_answers"
    private const val K_GO = "xuexi_go"
}

/** 学习读 content://com.example.liveupdatetest.status：今天刷了多久、每天一共的额度、在不在盯、岛上答的题 */
class StatusProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor {
        val ctx = context!!
        Prefs.init(ctx)
        Xuexi.init(ctx)
        val j = JSONObject()
            .put("date", LocalDate.now().toString())
            .put("scrolledSec", Engine.todaySec())
            .put("dailySec", Engine.dailyLimit())
            .put("monitoring", Prefs.monitoring.value)
            .put("answers", Xuexi.answers())
            .put("go", Xuexi.goJson())
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(j.toString())) }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, v: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/** 学习那边学完一节：现读一次它的状态，岛上庆祝 */
class LinkReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Xuexi.LESSON_DONE) return
        Prefs.init(ctx)
        Journal.init(ctx)
        Engine.attach(ctx)
        val j = runCatching { JSONObject(intent.getStringExtra("json") ?: "{}") }.getOrDefault(JSONObject())
        val title = j.optString("title").ifEmpty { null }
        Journal.add(Journal.Kind.App, "学习那边学完一节：${j.optString("cat")} · ${title ?: "?"}（${j.optInt("score")} 分）")
        val pending = goAsync()
        Xuexi.refresh { s ->
            runCatching { Engine.onStudyStatus(s, title) }
            pending.finish()
        }
    }
}
