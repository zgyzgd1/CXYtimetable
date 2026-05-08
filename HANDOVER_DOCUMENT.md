# 交接文档

更新时间：2026-05-07

---

## 一、项目概况

| 指标 | 值 |
|------|-----|
| 项目名 | CXYtimetable（课程表助手） |
| 技术栈 | Kotlin + Jetpack Compose BOM 2026.03.00 + Material 3 + Room |
| 版本 | v1.33（code 34） |
| 架构 | MVVM：ViewModel + Room + Repository |
| 最低 SDK | 26（Android 8.0） |
| 目标 SDK | 36 |
| 编译 SDK | 36 |
| 构建系统 | Gradle Kotlin DSL + KSP |
| 包名 | `com.example.timetable` |

### 核心功能

- 日视图 / 周视图 课程展示
- 课程增删改查 + 冲突检测
- 重复课程规则（单双周 / 自定义周次 / 跳周）
- ICS 导入导出
- 课程提醒（AlarmManager + WorkManager + BootReceiver 三层弹性设计）
- 桌面 Widget
- 自定义背景图 / 渐变背景
- 外观设置（卡片透明度 / 色调偏移）

---

## 二、本次会话修改总览

本次基于 `UI_CODE_REVIEW_REPORT.md` 发现的 7 个问题进行全面修复，同时完成了 Lint 零错误目标。共修改 **11 个源文件** + **1 个配置文件**。

### 文件变更清单

| # | 文件 | 修改类型 | 说明 |
|---|------|----------|------|
| 1 | `app/build.gradle.kts` | 功能 | 启用 `buildConfig = true`，生成 `BuildConfig` 类 |
| 2 | `app/.../data/AppearanceStore.kt` | Bug 修复 | 色调范围 `0f..360f` → `-180f..180f`（匹配 UI 滑块） |
| 3 | `app/.../data/AppCacheManager.kt` | Lint 修复 | `String.format` 添加 `Locale.US` 避免 DefaultLocale 警告 |
| 4 | `app/.../ui/BackgroundLayer.kt` | Bug 修复（P1） | `produceState` 添加 `value =` 赋值 + `key2 = revision` |
| 5 | `app/.../ui/WeekEntryLayout.kt` | Bug 修复 | `colorWithHueShift` 负数取模归一化 |
| 6 | `app/.../ui/WeekViewContent.kt` | Bug 修复 + Lint | `AnimatedContent` 数据解析移入 lambda + `stringResource` 预读 |
| 7 | `app/.../ui/ScheduleScreen.kt` | Bug 修复 + Lint | 通知检查改用封装方法 + 全部 `context.getString` 替换（~20 处） |
| 8 | `app/.../ui/DayScheduleList.kt` | Lint 修复 | 5 处 `context.getString` → `stringResource` 预读 |
| 9 | `app/.../ui/ScheduleDialogOverlays.kt` | Lint 修复 | 13 处 `context.getString` → `stringResource` 预读 |
| 10 | `app/.../ui/TimetableDialogs.kt` | Lint 修复 | `validationErrorMessages` 从 `remember+context.getString` 改为直接 `stringResource` |
| 11 | `app/.../ui/TimetableCalendar.kt` | 性能优化（P3） | 无限动画仅在 `isToday && hasCourse` 时创建 |
| 12 | `app/.../ui/SettingsScreen.kt` | 多项 | 版本号改用 `BuildConfig.VERSION_NAME` + 清理 4 个未使用 import + 删除 2 个未使用变量 |
| 13 | `local.properties` | Lint 修复 | 驱动器分隔符 `C:` → `C\:` 转义 |

---

## 三、逐项修复详情

### P1 — 自定义背景图不显示（功能缺陷）

**文件**：`BackgroundLayer.kt`

**根因**：`produceState` 的 producer lambda 中计算了 bitmap 但未赋值给 `value`，导致 `customBackground` 永远为 `null`。同时 key 未包含 `revision`，替换图片后不会重新解码。

**修复**：
```kotlin
// 修复前
) {
    if (backgroundAppearance.mode == AppBackgroundMode.CUSTOM_IMAGE) {
        withContext(Dispatchers.IO) { ... }
    } else { null }
}

// 修复后
) {
    value = if (backgroundAppearance.mode == AppBackgroundMode.CUSTOM_IMAGE) {
        withContext(Dispatchers.IO) { ... }
    } else { null }
}
```
同时在 `key1` 基础上添加 `key2 = backgroundAppearance.revision`。

---

### P2 — 色调范围不一致（逻辑错误）

**文件**：`AppearanceStore.kt`

**根因**：UI 滑块范围为 `-180f..180f`（相对偏移），但 `getWeekCardHue()` / `setWeekCardHue()` 持久化时 coerceIn 范围为 `0f..360f`（绝对色相），导致重启后值漂移。

**修复**：统一为 `-180f..180f`：
```kotlin
fun getWeekCardHue(context: Context): Float {
    return prefs(context).getFloat(KEY_WEEK_CARD_HUE, DEFAULT_WEEK_CARD_HUE)
        .coerceIn(-180f, 180f)  // 原为 0f, 360f
}
```

---

### P2 — 负数色相取模错误（逻辑错误）

**文件**：`WeekEntryLayout.kt`

**根因**：Kotlin `%` 运算符对负数可能返回负值（如 `-30f % 360f == -30f`），传给 `HSVToColor` 导致未定义行为。

**修复**：
```kotlin
// 修复前
hsv[0] = (hsv[0] + hueShift) % 360f

// 修复后
hsv[0] = ((hsv[0] + hueShift) % 360f + 360f) % 360f
```

---

### P2 — 周视图切周动画数据错配（逻辑错误）

**文件**：`WeekViewContent.kt`

**根因**：`visibleEntriesByDate` 在 `AnimatedContent` 外部使用 `config.selectedWeekStart` 解析，但动画期间 lambda 的 `weekStart` 参数可能对应旧/新周，导致短暂数据错配。

**修复**：将 `data.dateRangeEntriesCache.resolve()` 移入 `AnimatedContent` lambda 内部，使用 lambda 参数 `weekStart` 计算 `weekEnd = weekStart.plusDays(6)`。

---

### P2 — 通知权限检查不准确（逻辑错误）

**文件**：`ScheduleScreen.kt`

**根因**：直接使用 `ContextCompat.checkSelfPermission()` 检查通知权限，未处理 Android 13 以下不需要运行时权限的场景。

**修复**：替换为 `CourseReminderScheduler.notificationsEnabled(context)`，该封装已处理 API 版本兼容。

---

### P3 — 日历无限动画性能问题

**文件**：`TimetableCalendar.kt`

**根因**：`rememberInfiniteTransition` + `animateFloat` 在每个日期 cell 中创建，即使非今日无课程也持有动画对象。

**修复**：仅在 `shouldPulse = isToday && hasCourse` 时创建无限动画，其余日期使用固定 `6.dp`。

---

### Lint 修复 — `context.getString()` 替换

**涉及文件**：`ScheduleScreen.kt`、`DayScheduleList.kt`、`ScheduleDialogOverlays.kt`、`WeekViewContent.kt`、`TimetableDialogs.kt`

**问题**：`LocalContext.current.getString()` 在 Composable 中使用会触发 `LocalContextGetResourceValueCall` lint error，且在 Configuration 变化后可能不会正确失效。

**统一修复模式**：
```kotlin
// 在 Composable 函数顶层预读字符串
val msgExportSuccess = stringResource(R.string.msg_export_success)
val msgExportFailedTemplate = stringResource(R.string.msg_export_failed, "%s")

// 在回调中使用预读值
onClick = {
    snackbarHostState.showSnackbar(msgExportFailedTemplate.format(error.message))
}
```

对于模板字符串（含 `%s` / `%d`），使用 `stringResource(R.string.xxx, "%s")` 预读后再 `.format(actualArg)`。

---

### Lint 修复 — 其他

| 文件 | 问题 | 修复 |
|------|------|------|
| `AppCacheManager.kt` | `DefaultLocale` 警告 | `String.format(Locale.US, ...)` |
| `build.gradle.kts` | `BuildConfig` 未生成 | 添加 `buildConfig = true` |
| `SettingsScreen.kt` | 版本号硬编码 "1.33" | 改用 `BuildConfig.VERSION_NAME` |
| `SettingsScreen.kt` | 未使用 import/变量 | 删除 `ActivityResultLauncher`、`DropdownMenu`、`DropdownMenuItem`、`CircleShape`、`mutableStateOf`、`setValue` + 删除 `backgroundMenuExpanded`、`bgModeLabel` |
| `local.properties` | `:` 未转义 | `C\:\\Users\\...` |

---

## 四、验证结果

### 构建状态

```
.\gradlew.bat clean lintDebug
→ BUILD SUCCESSFUL in 1m 41s
→ 0 Error / 106 Warning / 5 Hint
```

### 单元测试

```
.\gradlew.bat testDebugUnitTest
→ 全部通过（修复前已通过，修复后无回归）
```

### 剩余 Warning 说明

106 个 Warning 均为非阻塞项，分类如下：

| 类型 | 数量 | 说明 |
|------|------|------|
| `GradleDependency` | ~5 | 依赖有新版本可升级（Compose BOM 2026.05.00、WorkManager 2.11.2 等） |
| `NewerVersionAvailable` | ~2 | org.json、robolectric 有新版 |
| `InlinedApi` | 2 | `POST_NOTIFICATIONS` 需 API 33，已用 `notificationsEnabled()` 封装保护 |
| `ObsoleteSdkInt` | 3 | `minSdk=26` 使部分 `SDK_INT < 26` 判断冗余（`CourseReminderScheduler` 中） |
| `PrivateResource` | ~4 | 覆盖 Material 内部资源（动画/drawable），属刻意行为 |
| `DataExtractionRules` | 1 | `AndroidManifest.xml` 需补充 `fullBackupContent` |
| `ConfigurationScreenWidthHeight` | 2 | `BackgroundImageAdjustDialog` 使用 `Configuration.screenWidthDp`，建议改用 `LocalWindowInfo` |

---

## 五、架构概览

```
app/src/main/java/com/example/timetable/
├── data/                          # 数据层
│   ├── AppearanceStore.kt         # SharedPreferences 外观持久化
│   ├── AppCacheManager.kt         # 缓存管理
│   ├── EntryValidation.kt         # 课程验证逻辑（统一验证器）
│   ├── TimetableModels.kt         # 数据模型（TimetableEntry, RecurrenceType 等）
│   ├── TimetableRepository.kt     # Room 仓库（单例）
│   ├── TimetableSnapshots.kt      # 快照计算 + DateRangeEntriesCache
│   ├── TimetableConflicts.kt      # 冲突检测
│   ├── WeekSchedulePlanner.kt     # 周时段规划
│   ├── IcsImport.kt / IcsExport.kt# ICS 导入导出
│   └── ...
├── ui/                            # UI 层
│   ├── ScheduleScreen.kt          # 主入口（日/周/设置导航 + ScheduleApp）
│   ├── ScheduleViewModel.kt       # ViewModel
│   ├── DayScheduleList.kt         # 日视图课程列表
│   ├── WeekViewContent.kt         # 周视图（含 AnimatedContent 切周）
│   ├── WeekScheduleBoard.kt       # 周视图面板（课程网格）
│   ├── WeekEntryLayout.kt         # 周视图布局计算
│   ├── WeekOverviewHeader.kt      # 周视图概览头部
│   ├── TimetableCalendar.kt       # 永久日历
│   ├── TimetableCards.kt          # 课程卡片组件
│   ├── TimetableHero.kt           # Hero 区域
│   ├── TimetableDialogs.kt        # 课程编辑对话框
│   ├── ScheduleDialogOverlays.kt  # 所有对话框覆盖层
│   ├── BackgroundLayer.kt         # 背景图层
│   ├── SettingsScreen.kt          # 设置页
│   ├── AlphaConstants.kt          # 透明度常量 + 语义扩展函数
│   ├── AppShape.kt                # 统一 Shape 体系
│   └── Theme.kt                   # Material 3 主题
├── notify/                        # 提醒调度
│   ├── CourseReminderScheduler.kt # AlarmManager 精确闹钟调度
│   ├── CourseReminderReceiver.kt  # BroadcastReceiver
│   └── ReminderFallbackWorker.kt  # WorkManager 兜底
└── widget/                        # 桌面小组件
```

---

## 六、构建与发布

### 常用命令

| 命令 | 用途 |
|------|------|
| `.\gradlew.bat assembleDebug` | 编译 Debug APK |
| `.\gradlew.bat lintDebug` | 运行 Lint 检查 |
| `.\gradlew.bat testDebugUnitTest` | 运行单元测试 |
| `.\scripts\publish-release.ps1 -Version "1.34"` | 自动化发布（版本号+tag+APK归档） |

### 发布流程

1. 修改 `gradle.properties` 中 `APP_VERSION_NAME` / `APP_VERSION_CODE`
2. 运行 `.\scripts\publish-release.ps1 -Version "x.xx"`
3. 脚本自动：编译 Release APK → 签名 → 归档到 `apk-archive-repo/releases/vx.xx/` → Git commit + tag + push

> 注意：`publish-release.ps1` 已更新为自动将 APK 归档到 `apk-archive-repo/releases/` 目录。

---

## 七、已知待处理事项

### 来自历史审查报告的遗留项

| # | 优先级 | 问题 | 文件 |
|---|--------|------|------|
| 1 | P0 | `DateRangeEntriesCache` TOCTOU 竞态 | `TimetableSnapshots.kt`（已在二轮审查中修复） |
| 2 | P0 | `icsUnescapeText` 转义顺序 | `IcsShared.kt`（已在二轮审查中修复） |
| 3 | P1 | `ScheduleApp` 上帝函数 ~370 行 | `ScheduleScreen.kt` |
| 4 | P1 | `DayScheduleList` 参数过多 | `DayScheduleList.kt`（已封装回调，仍有 14 参数） |
| 5 | P2 | `recurrenceType`/`weekRule` 使用字符串存储 | `TimetableModels.kt` |
| 6 | P2 | `WeekdayOptions` 英文硬编码 | `TimetableModels.kt` |
| 7 | P2 | 同步令牌使用 JSON 序列化做变更检测 | `ScheduleViewModel.kt` |
| 8 | P2 | `AppearanceStore` 读操作中有副作用 | `AppearanceStore.kt` |

### Lint 遗留 Warning（非阻塞）

- `CourseReminderScheduler.kt` 中 3 处 `ObsoleteSdkInt`（`minSdk=26` 使 `SDK_INT < 26` 判断冗余）
- `BackgroundImageAdjustDialog.kt` 使用 `Configuration.screenWidthDp`（建议改用 `LocalWindowInfo`）
- `AndroidManifest.xml` 缺少 `android:fullBackupContent` 属性
- 依赖版本可升级（Compose BOM → 2026.05.00、WorkManager → 2.11.2、Gradle → 9.5.0）

---

## 八、UI 优化状态

`UI_OPTIMIZATION_PLAN.md` 中规划的 5 个阶段已全部完成（22/25 任务，3 项合理跳过）：

| 阶段 | 内容 | 状态 |
|------|------|------|
| 1 | 卡片阴影提升（1dp → 3~4dp） | ✅ |
| 2 | 动画系统（页面切换/课程入场/周切换/背景过渡） | ✅ |
| 3 | AlphaConstants 精简（30+ → 19 语义函数） | ✅ |
| 4 | 日历自适应 + 空状态专业化 | ✅ |
| 5 | 设置页重建 + 触觉反馈 | ✅ |

---

## 九、Git 状态

当前工作区有大量未提交改动（UI 优化 + 本次 Lint 修复）。建议提交前确认：

```bash
git status --short
```

提交建议分组：
1. **UI 优化**：动画、阴影、日历、空状态、设置页重构、AlphaConstants 精简
2. **Bug 修复**：BackgroundLayer produceState、AppearanceStore 色调范围、WeekEntryLayout 负数取模、WeekViewContent 数据错配、ScheduleScreen 通知检查
3. **Lint 修复**：全部 `context.getString` 替换、BuildConfig 启用、DefaultLocale、版本号、local.properties

---

*文档结束*
