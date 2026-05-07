# 二轮代码审查报告

> **审查时间**: 2026-04  
> **审查范围**: P0/P1/P2 修复后的 8 个修改/新增文件  
> **构建状态**: ✅ BUILD SUCCESSFUL（全部单元测试通过）

---

## 1. 总体评级

| 维度 | 评级 | 说明 |
|------|------|------|
| 正确性 | ⭐⭐⭐⭐⭐ | 所有修复逻辑正确，无回归 |
| 一致性 | ⭐⭐⭐⭐☆ | 一处 KDoc 残留（见 §4） |
| 可维护性 | ⭐⭐⭐⭐⭐ | 代码结构显著改善 |
| 测试覆盖 | ⭐⭐⭐⭐☆ | 核心逻辑有测试，但缺少 `icsUnescapeText` 专项测试 |
| **综合** | **A** | **修复质量高，无新引入的严重问题** |

---

## 2. 逐项修复审查

### P0-1 ✅ `DateRangeEntriesCache` TOCTOU 竞态 — `TimetableSnapshots.kt`

**修复方式**: 将原先两个独立的 `synchronized(rangeCache)` 块合并为单个原子操作。

```kotlin
return synchronized(rangeCache) {
    rangeCache[key] ?: entriesByDateInRange(entries, startDate, endDate).also {
        rangeCache[key] = it
    }
}
```

**审查结论**: ✅ 正确。`synchronizedMap` 上的单次 `synchronized` 块保证了检查与写入的原子性。计算在锁内执行，但因 LRU 缓存（`maxRanges = 8`）的存在，重复调用的开销为 O(1)。且 `entriesByDateInRange` 的执行范围受日期范围约束（通常 1-7 天），不会造成长时间锁持有。

---

### P0-2 ✅ `icsUnescapeText` 反转义顺序 — `IcsShared.kt`

**修复方式**: 使用 `\u0000` 占位符策略，先保护已转义的反斜杠，再处理其他转义序列，最后还原。

```kotlin
return value
    .replace("\\\\", "\u0000")   // 保护已转义反斜杠
    .replace("\\n", "\n")
    .replace("\\N", "\n")
    .replace("\\,", ",")
    .replace("\\;", ";")
    .replace("\u0000", "\\")     // 还原为字面反斜杠
```

**审查结论**: ✅ 正确。验证场景：`"\\\\n"` → `"\u0000n"` → `"\u0000n"` → `"\u0000n"` → `"\u0000n"` → `"\\n"`（字面反斜杠+n），符合 RFC 5545 规范。

**⚠️ 建议**: 当前无直接单元测试覆盖此修复。建议在 `IcsCalendarTest.kt` 中增加 `icsUnescapeText` 专项测试（P3 优先级）。

---

### P1-3 ⏭️ 枚举存储方式 — 保持现状

**决策**: 保持 `String` 基础的枚举存储不变。

**审查结论**: ✅ 合理。`resolveRecurrenceType()` / `resolveWeekRule()` 对无效值返回 `null`，实现优雅降级。且当前 Room schema 版本为 v3，改变存储方式需要新的迁移，风险大于收益。

---

### P1-4 ✅ 硬编码中文回退字符串 — `TimetableSnapshots.kt`

**修复方式**: 提取为 `FbStrings` 私有对象，包含 9 个命名常量。

```kotlin
private object FbStrings {
    const val ONGOING_REMAINING = "正在进行，距离下课 %d 分钟"
    const val ONGOING = "正在进行"
    // ... 共 9 个常量
}
```

**审查结论**: ✅ 正确。
- 所有 9 个常量均有使用，无死代码
- 仅在 `context == null` 时作为回退（单元测试/无上下文场景）
- 生产代码中所有调用方均传递非空 `context`，使用 Android 资源字符串
- 为未来国际化提供了清晰的替换路径

---

### P1-5 ✅ 文件操作启动器提取 — `ScheduleScreen.kt`

**修复方式**: 
- 新增 `ScheduleLaunchers` 数据类（3 个启动器）
- 新增 `rememberScheduleLaunchers()` composable 封装注册逻辑
- `ScheduleApp()` 通过 `val launchers = rememberScheduleLaunchers(...)` 使用

**审查结论**: ✅ 正确。
- 3 个 `rememberLauncherForActivityResult` 调用从 `ScheduleApp()` 中移出
- `ScheduleLaunchers` 的 `import`、`export`、`backgroundImage` 属性分别在 `DayViewContent` 和 `AppearanceConfig.onSelectBackgroundImage` 中正确使用
- 无跨作用域引用错误

---

### P1-6 ✅ `WeekScheduleBoard.kt` 拆分（794 → 496 行）

**拆分结果**:

| 文件 | 行数 | 内容 |
|------|------|------|
| `WeekScheduleBoard.kt` | ~496 | 主板面、时间列头、时段列、日头单元格、日道、时段单元格、课程块 |
| `WeekOverviewHeader.kt` | ~160 | 概览头部（日期、统计、时段数控制） |
| `WeekEntryLayout.kt` | ~190 | 布局计算（列分配、时间锚点映射、色调偏移） |

**审查结论**: ✅ 正确。
- `internal` 可见性正确——同一包内可访问
- `buildWeekEntryLayouts()`、`WeekEntryLayout`、`colorWithHueShift()` 等符号在 `WeekScheduleBoard.kt` 中直接使用（同包无需 import）
- `WeekScheduleBoardLayoutTest` 调用 `buildWeekEntryLayouts()` 通过编译和测试，验证了提取后的可见性
- 无循环依赖

---

### P2-7 ✅ `DayScheduleList` 参数封装 — `DayListCallbacks.kt`

**修复方式**: 
- 新增 `DayListCallbacks` 数据类（5 个回调）
- `DayScheduleList` 参数从 20 个减少到 14 个
- `DayViewContent` 使用 `callbacks: DayListCallbacks` 传递

**审查结论**: ✅ 正确。
- `ScheduleApp()` 中创建 `DayListCallbacks(onDateChanged = ..., onEditEntry = ..., ...)` 并传递
- `DayScheduleList` 内部通过 `callbacks.onDateChanged(...)` 等方式调用
- 删除了冗余的 `filteredEntries`（与 `selectedDayEntries` 始终相同）

---

## 3. 新引入问题检查

| # | 严重度 | 问题 | 文件 | 说明 |
|---|--------|------|------|------|
| 1 | P3 | `DayViewContent` KDoc 残留旧参数 | `ScheduleScreen.kt:~540` | KDoc 仍列出 `onDateChanged`、`onEditEntry` 等已移除的参数名，以及 `reminderMinutes`、`exactAlarmEnabled` 等已封装到 `ReminderConfig`/`AppearanceConfig` 中的参数 |
| 2 | P3 | 缺少 `icsUnescapeText` 单元测试 | — | P0-2 修复无直接测试覆盖（通过 ICS 导入间接覆盖） |
| 3 | — | 无回归 | — | 所有 22 个测试文件编译通过并执行成功 |

**结论**: 未发现 P0/P1 级别的新问题。两个 P3 级建议可在后续迭代中处理。

---

## 4. 修复细节：DayViewContent KDoc 残留（P3）

`ScheduleScreen.kt` 中 `DayViewContent` 的 KDoc（约第 540 行）列出了旧的参数名。建议更新为：

```
 * @param reminderConfig 提醒配置
 * @param appearanceConfig 外观配置
 * @param callbacks 日视图列表回调集合
```

并移除已不存在的 `@param onDateChanged`、`@param onEditEntry` 等条目。

---

## 5. 修复前后对比

| 指标 | 修复前 | 修复后 | 改善 |
|------|--------|--------|------|
| TOCTOU 竞态 | ❌ 存在 | ✅ 已修复 | 正确性 |
| ICS 反转义 | ❌ 顺序错误 | ✅ 占位符策略 | 正确性 |
| 硬编码中文 | 散落在函数体中 | ✅ `FbStrings` 集中管理 | 可维护性 |
| `ScheduleApp()` launcher 逻辑 | 内联 3 处注册 | ✅ 提取为 `rememberScheduleLaunchers()` | 可读性 |
| `WeekScheduleBoard.kt` 行数 | 794 行 | 496 行（-37%） | 可维护性 |
| `DayScheduleList` 参数数 | 20 个 | 14 个（-30%） | 可读性 |
| 编译错误 | 0 | 0 | — |
| 测试通过 | ✅ | ✅ | — |

---

## 6. 总结

所有 P0/P1/P2 修复均**正确实现且无回归**。代码质量从首轮审查的 A- 提升至 **A**。两个 P3 级别的改进建议（KDoc 更新、补充测试）可在后续迭代中处理，不构成发布阻塞。

**建议**: 本轮审查后代码状态良好，可进入下一阶段开发或发布准备。
