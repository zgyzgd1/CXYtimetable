# TimetableMinimal 代码审查改进计划

> 基于 2026-05-12 全量代码审查（62 源文件 + 33 测试文件）生成

---

## 优先级分级说明

| 等级 | 含义 | 处理时间窗口 |
|------|------|-------------|
| 🔴 高 | 影响正确性或用户体验，需尽快修复 | 本周 |
| 🟡 中 | 影响可维护性或扩展性 | 本月 |
| 🟢 低 | 代码整洁度或技术债务 | 下个迭代 |

---

## 计划任务列表

---

### 任务 1 🔴 消除 ScheduleScreen.kt 与 ScheduleAppState.kt 之间的重复代码

**问题描述**: `ScheduleScreen.kt` 中定义了 `ScreenScheduleLaunchers` 和对应的 `rememberScreenScheduleLaunchers()`，与 `ScheduleLaunchers.kt` / `ScheduleAppState.kt` 中的 `rememberScheduleLaunchers()` 功能高度重复。两套代码同时存在，维护成本高且容易产生行为差异。

**涉及文件**:
- [ScheduleScreen.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt)
- [ScheduleLaunchers.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/ScheduleLaunchers.kt)
- [ScheduleAppState.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/ScheduleAppState.kt)

**执行步骤**:
1. 确认 `ScheduleAppState` 是主状态管理器，`ScheduleScreen.kt` 中的 `ScheduleApp` composable 是否同时使用了两套 launcher
2. 将 `ScheduleScreen.kt` 中所有 `launchers.import` / `launchers.export` / `launchers.backgroundImage` 调用改为通过 `ScheduleAppState.launchers` 访问
3. 删除 `ScheduleScreen.kt` 中的 `ScreenScheduleLaunchers` 数据类和 `rememberScreenScheduleLaunchers()` 函数
4. 删除 `ScheduleLaunchers.kt` 中的 `rememberScheduleLaunchers()`（若逻辑已完全移入 `ScheduleAppState`），或将 `ScheduleAppState.kt` 中的实现委托给 `ScheduleLaunchers.kt`
5. 运行 `ImportPreviewTest`、`ScheduleScreenTest` 确保导入导出功能不受影响

**验收标准**: 项目内仅保留一套 launcher 创建逻辑，编译无警告，ICS 导入导出正常。

---

### 任务 2 🔴 统一日期边界

**问题描述**: 三个位置定义了不一致的日期范围：
- `ScheduleAppState` / `ScheduleScreen`: `minDate = 1970-01-01`, `maxDate = 2100-12-31`
- `WeekCalendarStrip`: 硬编码 `startDate = 2020-01-01`, `endDate = 2035-12-31`

用户切换到 2035 年之后的日期时，周视图无法正常显示。

**涉及文件**:
- [ScheduleAppState.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/ScheduleAppState.kt#L199-L200)
- [ScheduleScreen.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt#L211-L217)
- [WeekCalendarStrip.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/WeekCalendarStrip.kt#L55-L57)

**执行步骤**:
1. 将日期边界提取为全局常量（可放在 `EntryConstants` 或新建 `AppConstants`）：
   ```kotlin
   object AppConstants {
       val MIN_DATE = LocalDate.of(1970, 1, 1)
       val MAX_DATE = LocalDate.of(2100, 12, 31)
   }
   ```
2. 在 `ScheduleAppState.kt`、`ScheduleScreen.kt`、`WeekCalendarStrip.kt` 三处引用该常量
3. 验证周视图支持从 1970 到 2100 的日期选择

**验收标准**: 所有日期边界引用同一常量源，周视图与日视图的日期范围一致。

---

### 任务 3 🟡 国际化硬编码文本

**问题描述**: `JwWebMode.kt` 及相关 JW 导入文件中存在硬编码的中文字符串（如 `"文本模式"`），未通过 `R.string` 引用。

**涉及文件**:
- [JwWebMode.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/jw/JwWebMode.kt)
- 其他 `jw/` 目录下的文件（逐个检查）

**执行步骤**:
1. 搜索 `jw/` 包下所有硬编码的中文/英文字符串：`"文本模式"`、`"网页模式"` 等
2. 在 `strings.xml` 中补充对应的 `<string>` 资源
3. 用 `stringResource()` 或 `context.getString()` 替换
4. 确保 WebView 内部注入的 JS 字符串（如用户提示）也考虑多语言场景

**验收标准**: `jw/` 包下无硬编码 UI 文本，`strings.xml` 内含完整的中文/英文对照。

---

### 任务 4 🟡 ICS 导入错误信息细化

**问题描述**: `ScheduleViewModel.readText()` 使用 `runCatching { ... }.getOrDefault("")` 吞掉所有异常类型。用户遇到导入失败时，只能看到 `"vm_import_empty"` 通用消息，无法判断是文件损坏、权限不足还是大小超限。

**涉及文件**:
- [ScheduleViewModel.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/ScheduleViewModel.kt#L389-L403)

**执行步骤**:
1. 在 `readText()` 中区分异常类型：
   - `FileNotFoundException` / `SecurityException` → 权限/文件不存在
   - `IOException` (含自定义 size-limit 消息) → 文件过大
   - 其他异常 → 通用读取失败
2. 对每个异常类型定义独立的 `R.string` 消息
3. 通过 `postMessage()` 把信息传递给 SnackBar
4. 更新 `ScheduleViewModelImportTest` 以覆盖三种异常场景

**验收标准**: 用户能在 Toast/Snackbar 中看到明确的失败原因。

---

### 任务 5 🟡 Compose API 兼容性检查

**问题描述**: `ScheduleAppState.kt` 使用了 `MutableFloatState` / `mutableFloatStateOf()`，该 API 从 Compose 1.5.0 开始提供。需确认项目依赖版本是否匹配。

**涉及文件**:
- [ScheduleAppState.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/ScheduleAppState.kt#L90-L91)
- [app/build.gradle.kts](file:///e:/vs1/app/build.gradle.kts)

**执行步骤**:
1. 检查 `app/build.gradle.kts` 中的 Compose BOM 版本
2. 若 BOM < 2023.08.00（对应 Compose 1.5.0），升级 BOM 或将 `MutableFloatState` 降级为 `MutableState<Float>`
3. 确认 `BackgroundImageAdjustDialog.kt` 中也用了 `mutableFloatStateOf()`，一并检查

**验收标准**: Compose 版本与 API 使用匹配，编译通过。

---

### 任务 6 🟢 优化自定义图片无图片时的交互

**问题描述**: `SettingsScreen.kt` 中 CUSTOM_IMAGE 模式下，若用户没有自定义图片，点击模式按钮会直接弹出文件选择器；如果用户取消选择，UI 没有回退到之前的模式。

**涉及文件**:
- [SettingsScreen.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/SettingsScreen.kt#L128-L135)

**执行步骤**:
1. 当用户切换到 CUSTOM_IMAGE 且没有图片时，先弹 SnackBar 提示"请选择背景图片"
2. 或在文件选择器取消后自动回退到之前的背景模式
3. 此行为需要同时修改 `ScheduleScreen.kt` 中 `onBackgroundModeChange` 的逻辑

**验收标准**: 用户不会卡在 CUSTOM_IMAGE 模式却看不到任何背景。

---

### 任务 7 🟢 AlphaConstants 迁移路线规划

**问题描述**: [AlphaConstants.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/AlphaConstants.kt) 使用精简后的透明度层级 + 大量向后兼容别名（如 `overlayPrimaryHigh` → `primaryContent`）。这些别名是过渡期的桥梁，长期存在会增加代码阅读成本。

**涉及文件**:
- [AlphaConstants.kt](file:///e:/vs1/app/src/main/java/com/example/timetable/ui/AlphaConstants.kt)

**执行步骤**:
1. 统计所有使用旧别名的调用点（`overlayPrimaryHigh`、`overlaySecondary`、`overlayHint` 等）
2. 逐步替换为新的语义化方法（`primaryContent`、`secondaryContent`、`hintContent` 等）
3. 全部替换完成后，移除别名扩展函数
4. 每个别名单独 PR，降低风险

**验收标准**: 最终只有一个统一的透明度扩展函数集合，无别名。

---

## 执行顺序建议

```
第 1 周: 任务 1 (消除重复) + 任务 2 (日期边界)
第 2 周: 任务 4 (ICS 错误细化) + 任务 5 (Compose 兼容性)
第 3 周: 任务 3 (国际化) + 任务 6 (图片交互)
第 4 周: 任务 7 (AlphaConstants 迁移)
```

---

## 禁忌事项

- ❌ 不在任务 1 完成前去修改 ScheduleLaunchers / ScreenScheduleLaunchers 的逻辑
- ❌ 不在国际化任务中改动 WebView JS 注入逻辑（仅改字符串资源）
- ❌ 不在没有测试的情况下修改 ICS 导入解析路径