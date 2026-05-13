# 代码审查任务报告 — TimetableMinimal

> **审查日期**: 2026-05-12
> **审查范围**: 全部 62 个 Kotlin 源文件、36 个测试文件、构建配置、Manifest、资源文件
> **项目路径**: `E:\vs1`
> **审查方法**: 逐文件静态审查 + 架构分析 + 安全扫描

---

## 一、项目概况

| 项目 | 内容 |
|------|------|
| 应用名称 | 课程表助手 (TimetableMinimal) |
| 技术栈 | Jetpack Compose + Material 3 + Room + MVVM |
| 语言 | Kotlin |
| 包名 | `com.example.timetable` |
| 源文件 | 62 个 Kotlin 源文件 |
| 测试文件 | 36 个 Kotlin 测试文件 |
| 版本 | v1.36 (versionCode 37) |
| 模块 | `app/` (主模块) |

### 模块结构

```
app/src/main/java/com/example/timetable/
├── MainActivity.kt                    # 入口 Activity
├── data/                              # 数据层 (18 files)
│   ├── TimetableModels.kt             # 实体 + 枚举 + 日期工具
│   ├── TimetableRepository.kt         # 单例 Repository
│   ├── EntryValidation.kt             # 验证逻辑
│   ├── TimetableConflicts.kt          # 冲突检测
│   ├── TimetableSnapshots.kt          # 快照 / 缓存
│   ├── WeekSchedulePlanner.kt         # 周时段规划
│   ├── IcsCalendar.kt / IcsExport.kt / IcsImport.kt / IcsShared.kt
│   ├── AppearanceStore.kt             # 外观持久化
│   ├── BackgroundImageManager.kt      # 背景图片管理
│   ├── BackgroundImageTransform.kt    # 背景变换
│   ├── SemesterStore.kt               # 学期配置
│   ├── AppCacheManager.kt             # 缓存清理
│   └── room/ (AppDatabase + TimetableDao)
├── ui/                                # UI 层 (24 files)
│   ├── ScheduleScreen.kt              # 主屏幕 (871 lines)
│   ├── ScheduleViewModel.kt           # ViewModel
│   ├── ScheduleLaunchers.kt           # ActivityResult 启动器
│   ├── ScheduleDialogOverlays.kt      # 对话框管理
│   ├── DayScheduleList.kt             # 日视图
│   ├── WeekViewContent.kt             # 周视图
│   ├── WeekScheduleBoard.kt           # 周视图面板
│   ├── WeekCalendarStrip.kt           # 周历条
│   ├── WeekOverviewHeader.kt          # 周视图头部
│   ├── WeekEntryLayout.kt             # 周视图布局计算
│   ├── TimetableCards.kt              # 课程卡片
│   ├── TimetableDialogs.kt            # 编辑对话框
│   ├── TimetableCalendar.kt           # 月历
│   ├── TimetableHero.kt               # Hero 区域
│   ├── SettingsScreen.kt              # 设置页
│   ├── BackgroundLayer.kt             # 背景渲染
│   ├── BackgroundImageAdjustDialog.kt # 背景调整
│   ├── CustomBackgroundImage.kt       # 自定义背景 Canvas
│   ├── AlphaConstants.kt              # 透明度常量
│   ├── Theme.kt                       # 主题
│   ├── AppDestination.kt / AppLaunchTarget.kt
│   ├── AccessibilityLabels.kt
│   └── DayListCallbacks.kt
├── jw/                                # 教务导入 (13 files)
│   ├── JwBridge.kt                    # JS-Native 桥
│   ├── JwWebView.kt                   # WebView 配置
│   ├── JwImportScreen.kt              # 导入界面
│   ├── JwWebMode.kt                   # 桌面/移动模式
│   ├── JwImportContract.kt            # URL 白名单 / 常量
│   └── hebau/ (8 files)
│       ├── HebauCourseParser.kt       # JSON 解析
│       ├── HebauCourseMapper.kt       # 数据映射
│       ├── HebauSectionTimes.kt       # 节次时间
│       ├── HebauStableId.kt           # 稳定 ID
│       └── HebauWeekFormatter.kt, AcademicImportPayload, PlainTextParser
├── notify/                            # 通知系统 (4 files)
│   ├── CourseReminderScheduler.kt     # 闹钟调度
│   ├── CourseReminderReceiver.kt      # 广播接收器
│   ├── CourseReminderRescheduleReceiver.kt
│   └── ReminderFallbackWorker.kt      # WorkManager 兜底
└── widget/                            # 小组件 (2 files)
    ├── TimetableWidgetProviders.kt
    └── TimetableWidgetUpdater.kt
```

---

## 二、问题汇总

### 🔴 P0 — 严重风险 (4 项)

| # | 问题 | 文件 | 风险说明 |
|---|------|------|----------|
| P0-1 | `MIXED_CONTENT_ALWAYS_ALLOW` | `JwWebView.kt:221` | 允许 HTTP 混合内容，MITM 攻击可注入恶意 JS |
| P0-2 | JwBridge 竞态条件 | `JwBridge.kt:43-52, 94-107` | `markImportRequested` 与 `consumeImportRequest` 非原子操作 |
| P0-3 | HebauSectionTimes 静默 fallback 0 | `HebauSectionTimes.kt:24` | 解析失败时默默使用 0，导致课程时间完全错误 |
| P0-4 | 周次范围无上限 | `HebauCourseParser.kt:88-101` | 可接受无限大的周次数字，潜在 OOM 风险 |

### 🟡 架构问题 (4 项)

| # | 问题 | 文件 | 说明 |
|---|------|------|------|
| A-1 | `bootstrapMutex` 作用域不足 | `TimetableRepository.kt:22-27` | 多入口并发初始化可能重复执行 |
| A-2 | 不存在的文件引用 | `CODE_REVIEW_PLAN.md`, `HANDOVER_DOCUMENT.md` | 声称已提取 `ScheduleAppState.kt`，但文件不存在 |
| A-3 | `OneTimeAction` 重复定义 | `CourseReminderReceiver.kt` + `TimetableWidgetProviders.kt` | 相同逻辑在两个文件中重复 |
| A-4 | `BackgroundTintOverlays` 冗余渲染 | `BackgroundLayer.kt:84-124` | 三个全屏 `Box` 覆盖层，性能浪费 |

### 🟠 代码质量问题 (5 项)

| # | 问题 | 文件 | 说明 |
|---|------|------|------|
| Q-1 | `parseEntryDate` null 处理不一致 | 多处 | 部分位置未处理 null 返回值 |
| Q-2 | `occursOnDate` 重复计算 | `TimetableConflicts.kt:91-128` | O(n²) 冲突检测中每对条目重复解析 |
| Q-3 | ICS 导出缺少 VALARM | `IcsExport.kt` | 导出的日历无提醒定义 |
| Q-4 | 两次打开 InputStream | `BackgroundImageManager.kt:61-95` | 远程 URI 场景下耗时翻倍 |
| Q-5 | `produceState` + `while(true)` | `ScheduleScreen.kt:191-206` | 协程管理正确但模式需标注 |

### 🔵 UI 问题 (4 项)

| # | 问题 | 文件 | 说明 |
|---|------|------|------|
| U-1 | `weekCardHue` 范围不一致 | `TimetableHero.kt:531` vs `SettingsScreen.kt:193` | Hero 弹窗 0-360°，设置页 -180° 到 180° |
| U-2 | `isWeekMode` 硬编码 false | `ScheduleScreen.kt:637` | 未使用的变量 |
| U-3 | `resolveBackgroundModeSelection` 重复 | `ScheduleScreen.kt:89-98` | 与 SettingsScreen 逻辑重复 |
| U-4 | 日历日期 tap target 过小 | `TimetableCalendar.kt` | 有课程的日期无额外点击区域 |

### 📝 测试覆盖缺口

| 模块 | 缺失测试 |
|------|----------|
| `IcsImport` | RRULE 解析、多事件解析 |
| `JwBridge` | 并发竞态条件 |
| `BackgroundImageManager` | 超大图片、无效 URI |
| `WeekSchedulePlanner` | `distributeProportionally` 浮点舍入 |
| `CourseReminderScheduler` | 多提醒时间的最优选择算法 |
| `AppCacheManager` | 符号链接攻击防护 |

---

## 三、安全审查结果

| 检查项 | 状态 | 位置 |
|--------|------|------|
| PendingIntent FLAG_IMMUTABLE | ✅ 全部使用 | `CourseReminderScheduler.kt`, `TimetableWidgetUpdater.kt` |
| WebView 文件访问禁用 | ✅ 全部关闭 | `JwWebView.kt:217-220` |
| 路径遍历防护 | ✅ 已检查符号链接 | `AppCacheManager.kt:57-90` |
| URL 白名单机制 | ✅ 严格限制 | `JwImportContract.kt` |
| 备份规则 | ✅ 已配置 | `xml/backup_rules.xml` |

---

## 四、依赖与构建

| 项目 | 值 |
|------|-----|
| AGP | 9.1.1 |
| Kotlin Compose BOM | 2026.03.00 |
| Room | 2.8.4 |
| WorkManager | 2.10.1 |
| Kotlin | 2.3.10 |
| KSP | 2.3.6 |
| Min SDK | 26 |
| Target SDK | 36 |

**注意**: Compose BOM `2026.03.00` 为未来版本，需确认实际可用性。

---

## 五、改进优先级

### 立即 (P0)
1. 统一 `weekCardHue` 范围至 `-180..180`，修改 `AlphaConstants.kt` 和 `Theme.kt`
2. 审查并修复所有 `parseEntryDate` 返回值的 null 处理路径
3. 修复 `HebauSectionTimes` 的 0 fallback 问题
4. 为 `JwBridge` 实现原子状态机，消除竞态条件

### 短期 (P1)
5. 提取 `OneTimeAction` 到公共工具类
6. 删除无效的 `ScheduleAppState.kt` 引用
7. 为 ICS 导出添加 `VALARM` 组件
8. 合并 `BackgroundTintOverlays` 的三个 `Box` 为单 Canvas

### 中期 (P2)
9. 优化 `BackgroundImageManager`：复用 InputStream
10. 预解析并缓存冲突检测中的 `RecurrenceType` 和 `LocalDate`
11. 为 `HebauSectionTimes.resolve()` 返回 `Result` 类型传播错误
12. 为 `parseWeeks` 增加上限检查

### 长期 (P3)
13. 考虑迁移到 `kotlinx.serialization` 替代 `org.json`
14. 为 WebView 添加 SSL Pinning（可选）
15. 补充关键模块的单元测试覆盖率

---

## 六、文件统计

| 类别 | 数量 |
|------|------|
| Kotlin 源文件 | 62 |
| 测试文件 | 36 |
| XML 资源 | ~30 |
| 图片/资产 | ~12 |
| 构建配置 | 7 |
| 文档 | 9 |
| **合计** | **~165** |

---

> **审查结论**: 代码整体架构清晰，MVVM 分层合理，安全性基本到位。主要风险集中在 JwBridge 的并发安全、Hebau 数据解析的容错性，以及部分 UI 常量的不一致。建议优先修复 P0 问题并补充测试覆盖。