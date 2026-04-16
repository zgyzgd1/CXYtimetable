# 课程表助手 (Timetable Minimal) 代码评估报告

## 整体架构与技术栈评估 🌟

本项目整体完成度极高，代码结构清晰，使用了非常多现代化的 Android 开发实践。

- **技术栈先进**：全面使用纯 Kotlin + Jetpack Compose 构建 UI，结合了 Material 3 提供了现代化的视觉体验。
- **状态管理规范**：在 `ScheduleViewModel` 中，很好地运用了 `StateFlow` (核心数据) 和 `SharedFlow` (一次性事件响应)。在 UI 层使用了生命周期安全的 `collectAsStateWithLifecycle()`。
- **职责划分清晰**：UI 逻辑保留在 `ui` 包内的 Composable 中，业务逻辑和存储封装在 ViewModel 中，数据模型与格式转换工具放在 `data` 包中，未出现越界包调用。
- **自研 ICS 解析器极大亮点**：`IcsCalendar.kt` 令人印象深刻。它在没有引入庞大的三方日历框架（如 iCal4j）的前提下，手工实现了一个严谨的轻量级日历解析器，成功解析 `RRULE` 周期性规则以及 `EXDATE`（并设限制 `MAX_EXPANDED_OCCURRENCES = 512` 以防死循环或者极其巨大的列表）。

---

## 核心劣势与潜在风险警告 ⚠️ (不足)

尽管整体底子很好，如果作为一款**商业级**应用，存在以下由于架构选型引出的严重设计缺陷：

### 1. 致命的闹铃机制失效 (针对 Android 14+ 适配)
在 `CourseReminderScheduler.kt` 中：
- **缺少权限声明**：在 `AndroidManifest.xml` 中，**并未声明** `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>`。
- **降级后引发的失效误差**：代码内部使用 `try-catch` 捕获了缺少精准闹钟权限导致的 `SecurityException`，并在 Catch 块里降级使用了 `setAndAllowWhileIdle()`（非精确闹钟）。但在 Android 系统 Doze（打盹）机制下，非精确闹钟可能会推迟 10~30 分钟执行。这意味着**“提前十分钟提醒上课”的功能在现代 Android 系统上极有可能“迟到”**。

### 2. 本地存储严重的并发“竞态条件” (Race Condition)
在 `ScheduleViewModel.kt` 的磁盘存储逻辑中：
- 每当进行新增、修改、导入操作时，都会在 `Dispatchers.IO` 下执行 `writeEntries()` 全量复写本地 `.json` 文件。
- 如果应用处在高并发状态或者用户的快速连点操作，会引发**多个协程并行读写同一个文件**。这将产生文件写入截断情况，直接导致用户的整个课表 JSON 损坏清空（这也是为什么代码中不得不加上大量读取时的 `backupCorruptedStorage()` 的原因，这是在掩盖症状而不是根治）。
- **建议改进**：引入 `Mutex` 互斥锁确保读写单线程化执行，或者接入更为稳妥的官方推荐方案 `Room Database` 或 `DataStore`。

### 3. 系统级高耗电操作与 CPU 开销
`syncReminders()` 闹钟策略过于暴力：
- **牵一发而动全身**：不管用户是仅仅改了一个备注单词，还是修改了一节微不足道的课，都会触发闹铃全量校验。具体为将 `SharedPreferences` 内所有的过去闹铃 `RequestCode` 全部提取，对 `AlarmManager` 挨个下达取消指令，然后重新再次循环下发数百个新建闹铃 IPC 指令。
- 这引发极大的跨进程通讯消耗（IPC overloads）。如果是装在 ColorOS 或 MIUI 这样的高度定制化 ROM 上，此操作将会带来极高的耗电警告以至于应用被强杀。
- **建议改进**：将课程独立赋予唯一可哈希标识（持久态），做到闹钟增量计算；或者转接使用 `WorkManager` 处理延时较长的触发工作。

### 4. 暴力硬编码与时区灾难
- **多语言化不可用 (Hardcoded Texts)**：UI 文件如 `ScheduleScreen.kt` 存在极其巨量的中文硬编码，根本没有通过 `strings.xml` 抽出。产品一旦流出海外毫无本地化可能。
- **时区写死 (Hardcoded Timezone)**：在 `IcsCalendar.kt` 与调度中：`private val beijingZone: ZoneId = ZoneId.of("Asia/Shanghai")`。这意味着当有国外留学生尝试以此解析自己学校发放的时区制 ICS 文件时，由于强制转化为北京时间计算，会导致事件落入错误时间发生混乱现象。

### 5. Compose 组件“巨型类” (God Object) 问题
- `ScheduleScreen.kt` 源代码多达将近 1000 行，容纳了页面脚手架、Hero 组件、长列表组件、弹窗表单、万年历组件等。
- 这违背了 Compose 开发中鼓励的小组件分离解耦原则，随着复杂度上升，重组调试性能降低，难以二次维护。应该抽离进多个零散的文件统一组合调用。
