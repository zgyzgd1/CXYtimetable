# 课程表助手当前状态统一总结

更新时间：2026-05-10

本文是当前仓库 `e:\vs1` 的统一状态入口。它综合了现有 Markdown 文档和当前源码事实；旧文档保留为历史材料，当前判断以本文为准。

## 总体结论

当前项目是一个 Android 原生课程表应用，项目名为 `TimetableMinimal`，应用包名为 `com.example.timetable`，版本为 `1.33`，`versionCode` 为 `34`。

当前代码已经包含课程表核心功能、Room 数据库、多课表分组、日视图/周视图/设置页、ICS 导入导出、课程提醒、桌面 Widget，以及河北农业大学教务系统导入模块。当前构建链路可用，单元测试和 Debug 打包均通过。

需要注意的是，教务导入模块虽然已经合入并可编译，但原审查文档中指出的几个高优先级风险在当前源码中仍然存在，发布前应优先处理。

## 当前验证状态

已在当前工作区执行：

| 命令 | 结果 |
|---|---|
| `.\gradlew.bat test assembleDebug` | 通过 |
| `.\gradlew.bat test` | 通过 |
| `.\gradlew.bat assembleDebug` | 通过 |

当前未重新运行 `lintDebug`。历史交接文档中曾记录 lint 通过，但当前代码已经经过后续合并和修复，发布前建议重新运行。

## 工作区状态

当前工作区存在未提交改动和未跟踪文件，这是本轮合并/修复后的实际状态，不是干净提交点。

主要状态：

| 类型 | 内容 |
|---|---|
| 已修改 | Gradle 配置、Manifest、数据层、提醒、Widget、UI、测试等 |
| 已删除 | `ScheduleImportSupport.kt`、`ScheduleSyncTokens.kt` |
| 新增未跟踪 | `app/src/main/java/com/example/timetable/jw/`、`app/src/test/java/com/example/timetable/jw/`、`app/schemas/.../4.json`、`network_security_config.xml`、多份历史 Markdown |
| 当前统一文档 | `PROJECT_SUMMARY.md` |

建议在下一次提交前先整理文档入口，并决定哪些历史 Markdown 需要归档、删除或保留。

## 构建与依赖

| 项目 | 当前值 |
|---|---|
| Android Gradle Plugin | `9.1.1` |
| Kotlin Compose Plugin | `2.3.10` |
| KSP | `2.3.6` |
| compileSdk | `36` |
| targetSdk | `36` |
| minSdk | `26` |
| Java target | `17` |
| buildTools | `37.0.0` |
| Compose BOM | `2026.03.00` |
| Room | `2.8.4` |
| WorkManager | `2.10.1` |
| Calendar Compose | `2.10.1` |

当前 `app/build.gradle.kts` 已启用：

- `compose = true`
- `buildConfig = true`
- Room schema 导出到 `app/schemas`
- 单元测试包含 Android resources

注意：`HANDOVER_DOCUMENT.md` 中提到 Compose BOM `2026.05.00`、WorkManager `2.11.2`、AndroidX test ext junit `1.3.0`，但当前 Gradle 文件实际仍是 `2026.03.00`、`2.10.1`、`1.1.5`。

## 代码结构

主要源码入口：

| 路径 | 说明 |
|---|---|
| `MainActivity.kt` | 应用入口，解析启动日期、目标页和 Widget 添加课程入口 |
| `data/` | 数据模型、Repository、ICS、冲突检测、周次规划、背景和缓存管理 |
| `data/room/` | Room 数据库与 DAO |
| `ui/` | Compose UI、ViewModel、日视图、周视图、设置页、弹窗 |
| `jw/` | 教务系统 WebView、JS Bridge、导入屏幕和导入协议 |
| `jw/hebau/` | 河北农业大学教务数据解析、映射、节次时间和稳定 ID |
| `notify/` | 课程提醒调度、通知接收、重启恢复和 WorkManager 兜底 |
| `widget/` | 今日课表 Widget 和下一节课 Widget |
| `app/src/test/` | 数据、UI、提醒、Widget、教务导入相关单元测试 |

## 数据层状态

Room 数据库当前为版本 4，包含两张表：

| 表 | 说明 |
|---|---|
| `timetable_entries` | 课程条目，包含日期、星期、开始/结束时间、重复规则、周次规则、所属课表组 |
| `timetable_groups` | 多课表分组，默认分组 ID 为 `default` |

当前迁移：

| 迁移 | 说明 |
|---|---|
| `1 -> 2` | 新增周期课程字段 |
| `2 -> 3` | 新增日期和星期索引 |
| `3 -> 4` | 新增课表组，已有课程迁移到默认课表 |

当前 `AppDatabase.MIGRATIONS` 已作为 `internal` 数组暴露，便于迁移测试使用。Repository 仍保留旧 JSON 存储迁移逻辑，会在 Room 初始化时尝试迁移旧文件。

## 已实现功能

### 课程表管理

- 创建、编辑、删除课程。
- 支持多课表组。
- 支持单次课程和按周重复课程。
- 支持全部周、单双周、自定义周次、跳过周次。
- 保存前进行日期、时间、重复规则和周次规则校验。
- 支持课程时间冲突检测。

### 日视图、周视图和设置页

- `ScheduleScreen.kt` 当前仍是主 UI 组合和状态入口。
- 日视图展示选中日期课程、下一节课、课表组、导入导出、提醒和外观入口。
- 周视图通过 `WeekViewContent` 和 `WeekScheduleBoard` 展示周课表。
- 设置页包含外观、提醒、数据、关于等区域。

注意：`ScheduleAppState.kt` 和 `ScheduleLaunchers.kt` 仍存在，但当前 `ScheduleScreen.kt` 没有接入 `rememberScheduleAppState`。历史文档中“主界面状态已完全拆到 ScheduleAppState”的描述与当前源码不一致。

### ICS 导入导出

- ICS 文件读取上限为 `1 MB`。
- 导入前构建 `ImportPreview`。
- 预览会统计有效课程、无效数量、内部冲突数量和与现有课程冲突数量。
- 用户确认时可以覆盖当前课表，也可以新建课表组。
- 导出使用当前课表数据生成 ICS。

当前限制：导入预览只保留无效数量，没有保留每个失败条目的具体原因。

### 河北农业大学教务导入

当前教务导入模块包含：

| 文件 | 说明 |
|---|---|
| `JwImportScreen.kt` | 教务导入界面 |
| `JwWebView.kt` | WebView 加载和配置 |
| `JwBridge.kt` | JavaScript 到 Native 的桥接 |
| `JwImportContract.kt` | 入口 URL、白名单、Bridge JSON 限制 |
| `JwWebMode.kt` | 桌面/移动 Web 模式 |
| `HebauCourseParser.kt` | 教务 JSON 解析、去重和错误收集 |
| `HebauCourseMapper.kt` | 教务课程映射为 `TimetableEntry` |
| `HebauSectionTimes.kt` | 节次时间解析 |
| `HebauStableId.kt` | 稳定课程 ID |
| `HebauWeekFormatter.kt` | 周次列表格式化 |

Bridge JSON 大小限制为 `512 KB`。网络安全配置允许 `hebau.edu.cn` 明文流量，当前入口 URL 使用 `http://urp.hebau.edu.cn:1009/...` 和 `http://urp1.hebau.edu.cn:1010/...`。

### 课程提醒

- 使用 `AlarmManager` 调度精确提醒。
- 使用 `WorkManager` 作为兜底扫描。
- Android 13+ 通知权限由 UI 引导请求。
- 支持自定义提前提醒分钟数。
- 系统重启、应用更新、时间变化后会触发重新同步。
- 当前已修复重启后因为签名相同而跳过重排的问题：`CourseReminderRescheduleReceiver` 会以 `forceReschedule = true` 调用同步。

### 桌面 Widget

- 支持今日课表 Widget。
- 支持下一节课 Widget。
- Widget 可打开应用查看对应日期。
- Widget 添加入口会带 `EXTRA_WIDGET_ADD_COURSE`，当前 `MainActivity` 会转换为 `openAddCourse`，进入应用后自动打开新增课程弹窗。

## 当前已修复的关键问题

| 问题 | 当前状态 |
|---|---|
| 缺失字符串资源导致资源链接失败 | 已修复 |
| `SettingsScreen` 调用参数与实现不匹配 | 已修复 |
| `WeekViewContent` 调用参数与实现不匹配 | 已修复 |
| 重复的 `ImportPreview`、导入 helper、同步 token helper | 已清理 |
| `BuildConfig.VERSION_NAME` 未生成 | 已启用 `buildConfig` |
| `dayLabel(dayOfWeek, context)` 缺失 | 已补齐 |
| 迁移测试无法访问迁移数组 | 已补齐 `AppDatabase.MIGRATIONS` |
| Widget 添加课程入口没有被主 Activity 消费 | 已修复 |
| 重启后提醒可能不重新注册 | 已修复 |

## 当前确认存在的风险

这些风险来自旧审查文档，并已用当前源码重新核对。

### P0：WebView 混合内容与明文流量风险

当前 `JwWebView.kt` 中仍有：

```kotlin
mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
```

同时 `JwImportContract.kt` 中的教务入口为 HTTP，`network_security_config.xml` 对 `hebau.edu.cn` 允许 cleartext。若发布给真实用户，需要重新评估是否必须使用 HTTP；如果只是兼容学校系统，也应把允许范围和风险写清楚。

### P0：`JwBridge` 导入请求标记存在竞态

当前 `consumeImportRequest()` 使用 `synchronized(this)`，但 `markImportRequested()` 直接写入 `waitingForImportResult`。这会让“请求导入”和“消费导入结果”不是同一个原子协议。建议改为 `AtomicBoolean` 或统一使用同一个锁。

### P0：`HebauSectionTimes` 默认节次解析失败时回退到 0

当前默认节次解析仍使用：

```kotlin
startMinutes = parseMinutes(time.start) ?: 0
endMinutes = parseMinutes(time.end) ?: 0
```

如果默认时间配置或未来修改出错，可能生成 00:00 或零长度时间段。建议让解析失败显式报错，而不是静默回退。

### P1：周次范围解析缺少上限

`HebauCourseParser.parseWeekText()` 对 `1-999999` 这类范围会展开成列表。虽然 Bridge JSON 有大小限制，但仍建议设置合理周次上限，例如 1..60 或 1..100。

### P1：导入错误反馈不够细

当前 `ImportPreview` 只记录 `invalidCount`，没有保留每条失败课程的原因。对用户来说，“导入失败几条”仍不够可操作。

### P1：导入流程缺少超时和进度状态

教务导入和 ICS 导入当前以结果提示为主，缺少明确的处理中状态、超时控制和取消策略。

### 技术债：`ScheduleAppState` 当前未接入

`ScheduleAppState.kt` 和 `ScheduleLaunchers.kt` 看起来是此前拆分方案留下的状态层，但当前 `ScheduleScreen.kt` 继续本地持有大量状态和 launcher。建议下一轮选择一种方向：要么重新接入状态层，要么删除未使用的状态层，减少维护混淆。

## 现有 Markdown 的定位

| 文件 | 当前定位 |
|---|---|
| `PROJECT_SUMMARY.md` | 当前统一状态入口，也就是本文 |
| `HEBAU_IMPORT_IMPROVEMENT_GUIDE.md` | 教务导入专项审查和修复建议，仍有参考价值，但里面的状态应按源码复核 |
| `MERGE_COMPLETION_REPORT.md` | 合并过程记录，适合作历史追踪 |
| `MERGE_SUCCESS_SUMMARY.md` | 合并成功摘要，适合作历史追踪 |
| `README_DOCUMENTATION.md` | 旧文档导航，已不再是最新入口 |
| `HANDOVER_DOCUMENT.md` | 2026-05-08 UI/lint 交接记录，部分结论已过期 |
| `UI_OPTIMIZATION_PLAN.md` | UI 优化计划与完成记录，适合作历史追踪 |
| `UI_CODE_REVIEW_REPORT.md` | UI 审查归档入口 |

建议后续保留 `PROJECT_SUMMARY.md` 和 `HEBAU_IMPORT_IMPROVEMENT_GUIDE.md` 作为主要文档，其余合并过程类文档可移动到 `docs/archive/` 或删除。

## 建议的下一步

1. 先修复三个 P0：WebView 混合内容策略、`JwBridge` 竞态、`HebauSectionTimes` 解析失败回退。
2. 处理 `ScheduleAppState` 未接入带来的状态层混乱，减少重复代码。
3. 给教务导入增加周次上限、导入错误明细、处理中状态和超时策略。
4. 重新运行 `.\gradlew.bat lintDebug`，并把结果写入本文。
5. 整理根目录 Markdown，只保留一个当前入口和必要专题文档。
6. 提交前检查 `git diff --check`，然后把代码、测试、schema 和文档一起作为一次明确提交。

## 快速命令

```powershell
.\gradlew.bat test assembleDebug
.\gradlew.bat lintDebug
git status --short
git diff --check
```

## 当前发布判断

当前状态适合作为“可继续开发的集成基线”：代码能编译，单元测试能通过，Debug APK 能打包。

当前状态不建议直接作为生产发布：教务 WebView 和导入解析仍有明确 P0/P1 风险，且当前工作区尚未整理成干净提交。
