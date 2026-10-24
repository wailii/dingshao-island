package com.xiaodeng.dingshao

/**
 * 岛上的文字。按「超了几个阈值」分档，越往后越凶；每档几句轮换，同一档里不变。
 * {时长}{App}{任务} 现填。
 */
object Copy {

    fun duration(sec: Long): String = when {
        sec < 60 -> "${sec}秒"
        sec < 3600 -> "${sec / 60}分钟"
        sec % 3600 < 60 -> "${sec / 3600}小时"
        else -> "${sec / 3600}小时${(sec % 3600) / 60}分"
    }

    /** 超了几个阈值：刚到阈值是 1，两倍是 2…… 每加一档岛就跳一次 */
    fun level(elapsedSec: Long, thresholdSec: Int): Int =
        (elapsedSec / thresholdSec.coerceAtLeast(1)).toInt().coerceAtLeast(1)

    /** 眼神：0 平静地盯，1 不耐烦，2 生气 */
    fun mood(elapsedSec: Long, thresholdSec: Int, snoozed: Boolean): Int {
        val m = (level(elapsedSec, thresholdSec) - 1).coerceIn(0, 2)
        return if (snoozed) maxOf(m, 1) else m
    }

    fun catchTitle(
        elapsedSec: Long,
        thresholdSec: Int,
        snoozedJustNow: Boolean,
        app: String,
        task: String?,
        seed: Long,
        overDaily: Boolean = false,
        todaySec: Long = 0,
        /** 学习里今天还剩几节；0 = 没装或学完了 */
        studyLeft: Int = 0,
    ): String {
        val d = duration(elapsedSec)
        val lv = level(elapsedSec, thresholdSec)
        if (overDaily && lv == 1 && !snoozedJustNow) {
            val today = duration(todaySec)
            val pool = listOf("今天已经刷了$today", "今天刷够了，歇会儿", "今天的份用完了")
            return pool[Math.floorMod(seed, pool.size.toLong()).toInt()]
        }
        val pool: List<String> = when {
            snoozedJustNow -> listOf("说好的 5 分钟到了", "5 分钟到，该走了", "时间到，别赖了")
            lv == 1 -> listOfNotNull(
                "${d}了，就我还在这守着",
                "${d}了，我一直看着呢",
                "${app}刷了${d}，差不多啦",
                task?.let { "${d}了，该回去$it" },
                if (studyLeft > 0) "${d}了，去学一节？" else null,
            )
            lv == 2 -> listOfNotNull(
                task?.let { "${d}了，${it}还在等你" } ?: "${d}了，该撤了",
                "又过了${duration(thresholdSec.toLong())}，还不走？",
                "${d}了，我眼睛都盯酸了",
                if (studyLeft > 0) "${d}了，还有${studyLeft}节没学" else null,
            )
            lv == 3 -> listOf("${d}了，放下${app}吧", "${d}了，我要生气了", "${d}了，再刷就过分了")
            lv == 4 -> listOf("${d}了！还刷？", "${d}了，你是不是忘了我", "${d}了，${app}有这么好看？")
            else -> listOfNotNull(
                "${d}了，我真的生气了",
                task?.let { "${d}了，今天还${it}吗" },
                "${d}了，我不想说话了",
            )
        }
        return pool[Math.floorMod(seed * 31 + lv, pool.size.toLong()).toInt()]
    }

    /** 胶囊里的短字，给不认秒表的岛用：「45秒」「3分钟」「1小时」 */
    fun chip(sec: Long): String = when {
        sec < 60 -> "${sec}秒"
        sec < 3600 -> "${sec / 60}分钟"
        else -> "${sec / 3600}小时"
    }

    fun catchText(app: String, task: String?, todaySec: Long? = null, studyLeft: Int = 0, studyMin: Int = 0): String {
        val study = if (studyLeft > 0) "还剩${studyLeft}节，约${studyMin}分钟" else null
        return when {
            todaySec != null -> "$app · 今天一共${duration(todaySec)}" + (if (studyLeft > 0) " · 还剩${studyLeft}节" else "")
            study != null -> "$app · $study"
            task != null -> "$app · 专注：$task"
            else -> "$app · 盯梢中"
        }
    }

    /** 连着被退了几回还打开 */
    fun toStudyTitle(n: Int, seed: Long): String {
        val pool = listOf("退了${n}回了，学会儿习", "又来？那就学会儿习", "刷不动了吧，学会儿习")
        return pool[Math.floorMod(seed, pool.size.toLong()).toInt()]
    }

    fun returnedTitle(): String = "回来了，好样的"

    fun returnedText(app: String, elapsedSec: Long, task: String?): String {
        val tail = if (task != null) "，继续$task" else ""
        return "这次在${app}待了${duration(elapsedSec)}$tail"
    }

    fun kickedTitle(app: String): String = "到点了，帮你关了$app"

    fun kickedText(task: String?): String =
        if (task != null) "冷静一下，${task}还在等你" else "冷静一下，一分钟内别再打开"

    fun cooldownTitle(app: String): String = "冷静期，先别开$app"
}
