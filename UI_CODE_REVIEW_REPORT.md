# UI 与代码审查报告

更新时间：2026-05-07

## 审查范围

本次审查覆盖当前工作区代码，重点关注 Jetpack Compose UI、设置页新增能力、周视图/日视图交互、背景图逻辑、提醒权限接入，以及现有验证命令的结果。

当前工作区存在未提交改动和若干已删除历史报告文件，本报告基于当前文件状态生成，不回滚或恢复任何已有改动。

## 验证结果

| 命令 | 结果 | 说明 |
|---|---|---|
| `.\gradlew.bat testDebugUnitTest` | 通过 | 现有单元测试全部通过 |
| `git diff --check` | 通过 | 仅提示 `AlphaConstants.kt` 未来会 CRLF -> LF |
| `.\gradlew.bat lintDebug` | 失败 | 38 errors / 111 warnings / 5 hints |

Lint 完整报告：

`app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`

## 关键发现

### P1 自定义背景图不会显示

位置：

- `app/src/main/java/com/example/timetable/ui/BackgroundLayer.kt:34`

问题：

`produceState` 的 producer lambda 中计算了 bitmap，但没有把结果赋给 `value`，因此 `customBackground` 会一直保持初始值 `null`。当前 `CUSTOM_IMAGE` 模式下不会渲染自定义背景图。

另外，`produceState` 的 key 只包含 `backgroundAppearance.mode`，没有包含 `backgroundAppearance.revision`。即使补上 `value = ...`，用户在同一背景模式下替换图片后，也可能不会触发重新解码。

建议：

将 producer 改成显式赋值，并把 `revision` 加入 key：

```kotlin
val customBackground by produceState<ImageBitmap?>(
    initialValue = null,
    key1 = backgroundAppearance.mode,
    key2 = backgroundAppearance.revision,
) {
    value = if (backgroundAppearance.mode == AppBackgroundMode.CUSTOM_IMAGE) {
        withContext(Dispatchers.IO) {
            BackgroundImageManager.customBackgroundFile(context)
                .takeIf { it.isFile }
                ?.let { file -> BitmapFactory.decodeFile(file.absolutePath) }
                ?.asImageBitmap()
        }
    } else {
        null
    }
}
```

### P1 Lint 当前无法通过

位置示例：

- `local.properties:1`
- `app/src/main/java/com/example/timetable/ui/DayScheduleList.kt:69`
- `app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt:152`
- `app/src/main/java/com/example/timetable/ui/WeekViewContent.kt:239`
- `app/src/main/java/com/example/timetable/ui/BackgroundLayer.kt:34`

问题：

`lintDebug` 当前失败。第一处失败是本机 `local.properties` 的 Windows 路径转义问题，但代码中还有大量 Compose lint 错误，尤其是 `LocalContext.current.getString()` 读取资源。

这类调用在语言、字体缩放、区域设置等 Configuration 变化后可能不会正确失效，导致 UI 文案 stale。Compose 推荐使用 `stringResource()`，或在需要回调中使用字符串时提前在组合阶段读取并传入。

建议：

优先处理真正会进仓库的 lint errors：

- 把 Composable 内直接用于 UI/回调的 `context.getString(...)` 替换为 `stringResource(...)` 预读值。
- 修复 `BackgroundLayer.kt` 的 `produceState` 赋值问题。
- `local.properties` 是本机文件，通常不提交；可以本地修正路径格式，或避免把它纳入 lint 输入。

### P2 设置页色调范围与持久化范围不一致

位置：

- `app/src/main/java/com/example/timetable/ui/SettingsScreen.kt:201`
- `app/src/main/java/com/example/timetable/data/AppearanceStore.kt:217`
- `app/src/main/java/com/example/timetable/ui/WeekEntryLayout.kt:93`

问题：

设置页新增的“周视图卡片色调”滑杆范围是 `-180f..180f`，但 `AppearanceStore` 持久化时会把值限制到 `0f..360f`。这会造成运行时状态与重启后状态不一致。

另外，`colorWithHueShift()` 使用 `(hsv[0] + hueShift) % 360f`。Kotlin 对负数取模仍可能得到负数，传给 HSV 颜色转换前最好归一化。

建议：

选择一种语义并统一：

- 如果是绝对色相：设置页也使用 `0f..360f`。
- 如果是相对偏移：持久化范围改为 `-180f..180f`，并在 `colorWithHueShift()` 中做正模归一化。

推荐归一化写法：

```kotlin
hsv[0] = ((hsv[0] + hueShift) % 360f + 360f) % 360f
```

### P2 周视图切周动画期间可能显示错周数据

位置：

- `app/src/main/java/com/example/timetable/ui/WeekViewContent.kt:199`

问题：

`visibleEntriesByDate` 在 `AnimatedContent` 外部根据当前 `config.selectedWeekStart` 解析；但 `AnimatedContent` 的内容 lambda 中 `weekStart` 可能对应 outgoing 或 incoming 状态。

切周动画期间，旧周画面可能拿到新周数据，出现短暂错配。

建议：

把数据解析移入 `AnimatedContent` lambda：

```kotlin
AnimatedContent(targetState = config.selectedWeekStart) { weekStart ->
    val weekEnd = weekStart.plusDays(6)
    val visibleEntriesByDate = data.dateRangeEntriesCache.resolve(weekStart, weekEnd)

    WeekScheduleBoard(
        weekStart = weekStart,
        weekEnd = weekEnd,
        entriesByDay = visibleEntriesByDate,
        ...
    )
}
```

### P2 通知权限逻辑绕开了已有兼容封装

位置：

- `app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt:519`
- `app/src/main/java/com/example/timetable/notify/CourseReminderScheduler.kt:461`

问题：

设置页新增逻辑直接检查和请求 `Manifest.permission.POST_NOTIFICATIONS`，lint 报 API 33 warning。项目已有 `CourseReminderScheduler.notificationsEnabled(context)` 封装，可以处理 Android 13 以下无需运行时通知权限的场景。

建议：

- 显示授权状态时统一使用 `CourseReminderScheduler.notificationsEnabled(context)`。
- 请求权限前判断 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`。
- Android 13 以下点击“开启权限”应提示无需额外授权，或隐藏该按钮。

### P3 日历为每个可见日期启动无限动画

位置：

- `app/src/main/java/com/example/timetable/ui/TimetableCalendar.kt:245`

问题：

`rememberInfiniteTransition` 在每个日期 cell 中都会创建，即使 `shouldPulse == false`。这会产生不必要的动画对象和重组开销。

建议：

只在 `shouldPulse` 为 true 时创建无限动画。其他日期直接使用固定 `6.dp`。

### P3 设置页版本号硬编码

位置：

- `app/src/main/java/com/example/timetable/ui/SettingsScreen.kt:383`

问题：

设置页显示版本号时写死 `"1.33"`。当前刚好与 `gradle.properties` 一致，但发布脚本或 Gradle 配置更新版本后，这里容易漂移。

建议：

从 `BuildConfig.VERSION_NAME`、包信息，或生成资源读取版本号。

## 其他观察

- 当前数据层、提醒调度、ICS 导入导出、Widget 更新等已有较完整的单元测试。
- 新增 UI 优化主要缺少设置页、背景图、周视图动画数据一致性的回归测试。
- `SettingsScreen.kt` 中存在未使用的 imports / state，例如 `DropdownMenu`、`DropdownMenuItem`、`ActivityResultLauncher`、`LocalContext`、`backgroundMenuExpanded`、`bgModeLabel`。虽然不影响编译，但建议清理。
- `TimetableCards.kt` 使用的 `Icons.Default.EventNote` 已被编译器提示 deprecated，可换成 AutoMirrored 版本。
- Widget XML 有无障碍和布局警告，例如课程 dot `ImageView` 缺少 `contentDescription` 或 `importantForAccessibility="no"`。

## 建议优先级

1. 修复 `BackgroundLayer.kt`，恢复自定义背景图显示。
2. 修复会阻塞 lint 的 Compose errors，至少让 `lintDebug` 能作为 CI 质量门。
3. 统一周视图 hue 的取值范围和归一化逻辑。
4. 修复 `WeekViewContent` 动画期间 entries 与 weekStart 错配。
5. 梳理通知权限逻辑，避免 Android 13 以下显示错误授权状态。
6. 给背景图模式切换、设置页 hue/alpha、周切换动画增加测试或截图验证。

## 当前结论

当前代码可以编译并通过单元测试，但 UI 优化改动里有几处会直接影响用户体验的风险。最需要先处理的是自定义背景图不可见、lint 无法通过、设置页色调范围不一致，以及周视图切换动画期间的数据错配。
