package com.xiaodeng.dingshao.ui

import com.xiaodeng.dingshao.Rom

/** 每个权限「跳过去以后点哪」，按厂商写；vivo 是照着 OriginOS 7 真机界面画的 */
object Guides {

    fun notify(): Guide = Guide(
        title = "允许通知",
        steps = listOf(
            GuideStep(
                "把「允许通知」打开",
                rows = listOf(MockRow("允许通知", trailing = MockTrailing.Switch(false), highlight = true)),
            ),
        ),
        toast = "把「允许通知」打开",
    )

    fun live(): Guide = when (Rom.vendor) {
        Rom.Vendor.Vivo -> Guide(
            title = "让提醒进原子岛",
            steps = listOf(
                GuideStep(
                    "打开第二个开关「显示实时活动」",
                    rows = listOf(
                        MockRow("允许通知", trailing = MockTrailing.Switch(true)),
                        MockRow("显示实时活动", "以原子通知形式显示实时活动。", trailing = MockTrailing.Switch(false), highlight = true),
                        MockRow("静默通知", "通知静音，仅在通知中心显示。", trailing = MockTrailing.Switch(false)),
                    ),
                ),
            ),
            tip = "更新盯梢岛后要重新打开",
            toast = "打开「显示实时活动」",
        )
        Rom.Vendor.Oppo -> Guide(
            title = "让提醒进流体云",
            steps = listOf(
                GuideStep(
                    "打开流体云或实况相关的开关",
                    rows = listOf(
                        MockRow("允许通知", trailing = MockTrailing.Switch(true)),
                        MockRow(Rom.liveSwitchName, trailing = MockTrailing.Switch(false), highlight = true),
                    ),
                ),
            ),
            tip = "这个开关默认关着",
            toast = "打开流体云 / 实况开关",
        )
        else -> Guide(
            title = "让提醒进${Rom.islandName}",
            steps = listOf(
                GuideStep(
                    "打开带「实时活动」或「实况」字样的开关，没有就不用管",
                    rows = listOf(
                        MockRow("允许通知", trailing = MockTrailing.Switch(true)),
                        MockRow("实时活动 / 实况通知", trailing = MockTrailing.Switch(false), highlight = true),
                    ),
                ),
            ),
            toast = "打开实时活动 / 实况开关",
        )
    }

    fun accessibility(hasOldApp: Boolean): Guide {
        val section = when (Rom.vendor) {
            Rom.Vendor.Vivo -> "已下载的服务"
            Rom.Vendor.Xiaomi -> "已下载的应用"
            else -> "已安装的服务"
        }
        val rows = buildList {
            add(MockRow("抖音", icon = MockIcon.Plain, trailing = MockTrailing.Status("已关闭")))
            if (hasOldApp) add(MockRow("盯梢岛", icon = MockIcon.Orange, trailing = MockTrailing.Status("已关闭")))
            add(MockRow("盯梢岛", icon = MockIcon.Eyes, trailing = MockTrailing.Status("已关闭"), highlight = true))
            add(MockRow("讯飞输入法", icon = MockIcon.Plain, trailing = MockTrailing.Status("已关闭")))
        }
        return Guide(
            title = "打开无障碍",
            steps = listOf(
                GuideStep(
                    "在「$section」里点黑底眼睛的盯梢岛" + if (hasOldApp) "（不是橙色旧版）" else "",
                    header = section,
                    rows = rows,
                ),
                GuideStep(
                    "打开最上面的开关",
                    rows = listOf(
                        MockRow("盯梢岛", trailing = MockTrailing.Switch(false), highlight = true),
                        MockRow("“盯梢岛”快捷方式", trailing = MockTrailing.Switch(false)),
                    ),
                ),
                GuideStep(
                    if (Rom.vendor == Rom.Vendor.Vivo) "点「允许」，然后直接回盯梢岛" else "点「允许」",
                    dialog = (if (Rom.vendor == Rom.Vendor.Vivo) "盯梢岛请求监控和控制设备" else "要允许“盯梢岛”完全控制您的设备吗？") to
                        (if (Rom.vendor == Rom.Vendor.Vivo) listOf("允许", "卸载", "禁止") else listOf("允许", "拒绝")),
                ),
            ),
            tip = if (Rom.vendor == Rom.Vendor.Vivo) "回到列表时显示「已关闭」是正常的，离开设置几秒后会恢复" else null,
            toast = "黑底眼睛的盯梢岛 → 打开 → 允许",
        )
    }

    fun restricted(): Guide = Guide(
        title = "允许受限制的设置",
        steps = listOf(
            GuideStep(
                "点右上角 ⋮",
                rows = listOf(MockRow("盯梢岛", "版本 1.1", icon = MockIcon.Eyes, trailing = MockTrailing.Status("⋮"), highlight = true)),
            ),
            GuideStep(
                "选「允许受限制的设置」，再回去开无障碍",
                rows = listOf(
                    MockRow("卸载更新", trailing = MockTrailing.Chevron),
                    MockRow("允许受限制的设置", trailing = MockTrailing.Chevron, highlight = true),
                ),
            ),
        ),
        toast = "右上角 ⋮ →「允许受限制的设置」",
    )

    fun background(): Guide = when (Rom.vendor) {
        Rom.Vendor.Vivo -> Guide(
            title = "允许后台耗电",
            steps = listOf(
                GuideStep(
                    "点「后台耗电管理」",
                    header = "电池管理",
                    rows = listOf(
                        MockRow("省电管理"),
                        MockRow("电池健康与充电"),
                        MockRow("后台耗电管理", highlight = true),
                    ),
                ),
                GuideStep(
                    "点黑底眼睛的盯梢岛",
                    rows = listOf(
                        MockRow("钉钉", icon = MockIcon.Plain, trailing = MockTrailing.Status("智能控制")),
                        MockRow("盯梢岛", icon = MockIcon.Orange, trailing = MockTrailing.Status("智能控制")),
                        MockRow("盯梢岛", icon = MockIcon.Eyes, trailing = MockTrailing.Status("智能控制"), highlight = true),
                    ),
                ),
                GuideStep(
                    "选「允许后台耗电」",
                    rows = listOf(
                        MockRow("智能控制后台耗电", "智能控制不同类型应用的后台耗电量", trailing = MockTrailing.Status("○")),
                        MockRow("允许后台耗电", "不限制应用后台耗电量", trailing = MockTrailing.Status("◉"), highlight = true),
                        MockRow("限制后台耗电", trailing = MockTrailing.Status("○")),
                    ),
                ),
            ),
            toast = "后台耗电管理 → 黑底眼睛的盯梢岛 → 允许后台耗电",
        )
        else -> Guide(
            title = "别让系统省电停掉它",
            steps = listOf(
                GuideStep(
                    "找到盯梢岛，选「不限制」",
                    rows = listOf(MockRow("盯梢岛", icon = MockIcon.Eyes, trailing = MockTrailing.Status("智能限制"), highlight = true)),
                ),
            ),
            toast = "找到「盯梢岛」→ 不限制",
        )
    }

    fun autostart(): Guide = Guide(
        title = "允许自启动",
        steps = listOf(
            GuideStep(
                "按拼音找到黑底眼睛的盯梢岛，打开开关",
                header = "自启动",
                rows = listOf(
                    MockRow("钉钉", "后台启动", icon = MockIcon.Plain, trailing = MockTrailing.Switch(false)),
                    MockRow("盯梢岛", "开机启动  后台启动", icon = MockIcon.Orange, trailing = MockTrailing.Switch(false)),
                    MockRow("盯梢岛", "开机启动  后台启动", icon = MockIcon.Eyes, trailing = MockTrailing.Switch(false), highlight = true),
                ),
            ),
        ),
        tip = "跳到的是应用信息页的话，找「自启动」",
        toast = "找到盯梢岛 → 打开",
    )
}
