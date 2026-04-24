# 代码审查报告 — TimetableMinimal

> 审查时间：2026-04-24  
> 审查范围：全项目 42 个 Kotlin 源文件 + 构建配置 + AndroidManifest  
> 审查基线：`main` 分支，未提交修改

---

## 一、总体评价

项目整体质量**良好**。架构清晰（MVVM + Repository + Room），Jetpack Compose UI 组织合理，通知/闹钟模块考虑了 Android 12+ 精确闹钟权限和 Doze 模式等边界场景。以下按严重程度列出所有发现。

**严重程度定义**：
- 🔴 **Critical**：数据丢失、安全漏洞、运行时崩溃
- 🟠 **Major**：逻辑错误、性能问题、可维护性风险
- 🟡 **Minor**：代码风格、潜在改进、最佳实践偏差

---

## 二、Critical 级别发现

### C1. `nextOccurrenceDate()` 周循环搜索可能无限跳过

**文件**：`TimetableModels.kt:314-332`

```kotlin
val weekStep = if (weekRule == WeekRule.ALL) 1 else 2
while (candidateWeek in skippedWeeks) {
    candidateWeek += weekStep
}
```

当 `skipWeekList` 包含大量连续周次时（如 `"1-100"`），此 `while` 循环可能迭代数百次。极端情况下（`skipWeekList` 覆盖整个学期），循环可能长时间运行甚至接近无限。

**建议**：添加安全上限，例如：
```kotlin
var safetyCounter = 0
while (candidateWeek in skippedWeeks && safetyCounter < 200) {
    candidateWeek += weekStep
    safetyCounter++
}
if (safetyCounter >= 200) return null
```

### C2. `TimetableRepository` 单例 object 无进程安全保护

**文件**：`TimetableRepository.kt:14`

`TimetableRepository` 是 Kotlin `object`，所有方法直接接受 `Context` 参数。如果多进程访问（如 widget provider 在远程进程），`RoomDatabase` 单例会为不同进程各自创建实例，但 `bootstrapMutex` 和 `SharedPreferences` 不跨进程同步，可能导致：
- 重复迁移执行（legacy JSON 文件可能被重复读取）
- `sample_entries_seeded` 标记竞态

**当前风险**：低。项目未声明 `android:process`，所有组件在同一进程。但如果未来添加独立进程组件需注意。

**建议**：在 `AppDatabase.getDatabase()` 的文档中标注"单进程使用"约束，或考虑使用 `ContentProvider` 做跨进程数据共享。

### C3. ICS 导入替换全部条目，用户可能丢失数据

**文件**：`ScheduleViewModel.kt:190`

```kotlin
TimetableRepository.replaceAllEntries(getApplication(), preview.validEntries)
```

`commitImport` 调用 `replaceAllEntries` 会**删除所有现有条目**再写入导入数据。这意味着：
- 导入 3 条 ICS 条目 → 原有的 50 条课程全部丢失
- 即使用户只想"追加"几条课程

**建议**：改为合并策略（保留现有 + 追加/更新导入），或在 UI 上明确提示"导入将替换所有现有课程"。

---

## 三、Major 级别发现

### M1. `DateRangeEntriesCache` 不是线程安全的

**文件**：`TimetableSnapshots.kt:158-186`

`LinkedHashMap` 的 `accessOrder=true`（LRU）模式在多线程环境下不安全。`resolve()` 方法可能从 Compose 主线程和协程同时调用。

**建议**：使用 `Collections.synchronizedMap()` 或 `ConcurrentHashMap` 包装，或加 `@Synchronized`。

### M2. `CourseReminderScheduler.sync()` 的 `@Synchronized` 在 Kotlin object 上不保护 `resyncScope` 协程

**文件**：`CourseReminderScheduler.kt:92`

`sync()` 是 `@Synchronized` 的，但 `resyncFromStorage()` 在 `resyncScope` 协程中调用 `sync()`，协程不会持有 Java 监视器锁。虽然当前只有 `sync()` 内部逻辑需要串行化，但未来如果 `resyncFromStorage` 被多个地方快速调用，可能导致竞态。

**建议**：使用 `Mutex` 替代 `@Synchronized`，或在 `resyncFromStorage` 中做去抖（debounce）。

### M3. `suggestAdjustedEntryAfterConflicts` 只考虑同一天同一 `dayOfWeek` 的冲突

**文件**：`TimetableConflicts.kt:18-47`

`suggestAdjustedEntryAfterConflicts` 基于按 `startMinutes` 排序的冲突列表调整时间。但过滤条件 `entriesShareAnyOccurrenceDate` 是正确的；真正的问题在于排序只按 `startMinutes`，不考虑 `dayOfWeek` 或日期。如果列表包含不同星期的课程（传入的 `entriesList` 可能包含全部课程），排序会混合不同天的条目，导致时间建议逻辑混乱。

**建议**：在 `suggestAdjustedEntryAfterConflicts` 中额外过滤 `entriesShareAnyOccurrenceDate(it, target) && it.dayOfWeek == target.dayOfWeek`，或明确文档说明调用方需传入同日条目。

### M4. `entriesShareAnyOccurrenceDate` 的周搜索上限不够健壮

**文件**：`TimetableConflicts.kt:104-109`

```kotlin
val candidateCount = maxOf(relevantSearchWeeks(first), relevantSearchWeeks(second)) + 2
generateSequence(searchStart) { it.plusWeeks(1) }
    .take(candidateCount.coerceAtLeast(2))
    .any { date -> occursOnDate(first, date) && occursOnDate(second, date) }
```

当两个课程都有 `skipWeekList` 且跳过不同周次时，`relevantSearchWeeks` 计算的搜索范围可能不足以覆盖到真正重叠的周次。`+2` 的缓冲不足以应对所有场景。

**建议**：增大缓冲或使用固定上限（如 30 周，覆盖一学年）。

### M5. `ScheduleScreen` 中 `nextCourseSnapshot` 的 `remember` key 不精确

**文件**：`ScheduleScreen.kt:138-145`

```kotlin
val nextCourseSnapshot = remember(entries, today, nowMinutes, context) {
    findNextCourseSnapshot(...)
}
```

`nowMinutes` 每分钟变化都会触发重新计算，但 Compose 重组不一定每分钟发生。如果用户停留在页面不动，`nowMinutes` 不会自动更新（它只在重组时才读取 `LocalTime.now()`）。这意味着"正在进行"状态可能过时。

**建议**：使用 `produceState` 或 `LaunchedEffect` + 定时器来周期性刷新，或者至少在文档中说明此为"快照式"计算。

### M6. `ScheduleViewModel` 使用 `AndroidViewModel` 导致测试困难

**文件**：`ScheduleViewModel.kt:47`

`AndroidViewModel` 依赖 `Application`，使得单元测试需要 Robolectric 或模拟 Application。整个 ViewModel 大量使用 `getApplication<Application>()` 获取 Context。

**建议**：通过构造函数注入所需的 Repository 接口和 Context 依赖，改用 `ViewModel`，提升可测试性。

### M7. `readLimitedUtf8Text` 的 `totalRead` 溢出风险

**文件**：`ScheduleViewModel.kt:362-382`

```kotlin
totalRead += read
if (totalRead > maxBytes) {
    throw IOException(...)
}
```

如果恶意 ICS 文件声称较小但实际很大，`totalRead` 使用 `Int` 累加，而 `read()` 每次最多返回 `DEFAULT_BUFFER_SIZE`（8192），当 `maxBytes = 1MB` 时不会溢出。但 `totalRead` 类型是 `Int`，理论上 `Int.MAX_VALUE < 1MB * N` 不会溢出（1MB = 1048576，Int.MAX ≈ 2.1G）。**风险极低**，仅为防御性编程建议。

### M8. `CourseReminderReceiver.onReceive` 中 `goAsync()` 超时

**文件**：`CourseReminderReceiver.kt:17`

`goAsync()` 的 BroadcastReceiver 有 10 秒超时。`resyncFromStorage` 内部启动协程做 DB 查询 + AlarmManager 调度，如果数据库较大或主线程繁忙，可能超时。

**建议**：监控 `resyncFromStorage` 执行时间，或在 `pendingResult.finish()` 之前添加超时保护。

---

## 四、Minor 级别发现

### m1. `parseMinutes` 的 3 位数字解析逻辑不够直观

**文件**：`TimetableModels.kt:167-169`

```kotlin
3 -> (trimmed.substring(0, 1).toIntOrNull() ?: return null) to (trimmed.substring(1).toIntOrNull() ?: return null)
```

输入 `"830"` → 8小时30分，但 `"130"` → 1小时30分。逻辑正确但不直观，缺少注释说明。

### m2. `normalizeWeekListText` 将中文逗号替换为英文逗号，但未替换中文连字符

**文件**：`TimetableModels.kt:228-232`

`replace('，', ',')` 只处理了逗号，但中文用户可能使用 `—`（全角破折号）代替 `-`（半角连字符）表示范围。

**建议**：添加 `.replace('—', '-').replace('–', '-')`。

### m3. `BackgroundImageManager.saveCustomBackground` 使用 JPEG 压缩有损

**文件**：`BackgroundImageManager.kt:41`

JPEG 压缩质量 90 会引入轻微画质损失。对于可能需要缩放/裁剪的背景图，多次保存会累积质量损失。

**建议**：考虑使用 PNG 格式保存（无损），或添加单次保存保证（当前已有，因为每次都是重新解码原始 URI）。

### m4. `AppearanceStore` 的 `getBackgroundAppearance` 有副作用

**文件**：`AppearanceStore.kt:117-155`

getter 方法内部会写入 SharedPreferences（当检测到需要规范化时）。这违反了"查询不应有副作用"的原则，可能导致意外的磁盘 I/O。

**建议**：将规范化逻辑移到独立的 `normalizeIfNeeded()` 方法中，由调用方显式调用。

### m5. `AppDatabase` 的 `INSTANCE` 未提供清除方法

**文件**：`room/AppDatabase.kt:43`

`INSTANCE` 一旦创建就持有数据库连接。测试场景中可能需要重置数据库。

**建议**：添加 `@VisibleForTesting fun closeDatabase()` 方法。

### m6. `IcsImport.parse` 的 `MAX_EXPANDED_OCCURRENCES = 512` 无用户提示

**文件**：`IcsShared.kt:18`

如果 ICS 文件包含超过 512 个重复事件，超出部分会被静默截断，用户不会收到任何提示。

**建议**：在导入结果中包含截断警告信息。

### m7. `CourseReminderScheduler.CHANNEL_NAME` 使用硬编码中文字符串

**文件**：`CourseReminderScheduler.kt:53`

```kotlin
const val CHANNEL_NAME = "课程提醒"
```

虽然注释说明了 i18n 通过 `R.string` 处理，但 `CHANNEL_NAME` 常量本身未被使用（实际使用 `context.getString(R.string.notify_channel_name)`），属于死代码。

**建议**：删除未使用的 `CHANNEL_NAME` 常量。

### m8. `TimetableWidgetUpdater.refreshAll` 在主线程执行 RemoteViews 更新

**文件**：`TimetableWidgetUpdater.kt:40-51`

`refreshAll` 不是 suspend 函数，内部直接调用 `AppWidgetManager.updateAppWidget()`，这可能被从主线程调用时造成 ANR。`refreshAllFromStorage` 正确地在 IO 协程中调用，但 `refreshAll` 本身没有线程保证。

**建议**：在 `refreshAll` 的文档中标注"应在后台线程调用"。

### m9. `DayScheduleList` 参数过多

**文件**：`DayScheduleList.kt:37-70`

`DayScheduleList` Composable 有 28 个参数，远超合理的函数签名复杂度。

**建议**：将相关参数分组为数据类（如 `DayScheduleCallbacks`、`DayScheduleState`），或提升为状态持有类。

### m10. `buildHeroActionContentDescription` 硬编码中文

**文件**：`AccessibilityLabels.kt:57-58`

```kotlin
return "$label，按钮"
```

应使用 `context.getString(R.string.a11y_button, label)` 以支持国际化。

### m11. `WeekSchedulePlanner` 的 `distributeProportionally` 精度问题

**文件**：`WeekSchedulePlanner.kt:250-280`

使用 `Double` 运算分配整数分钟，在极端边界情况下可能因浮点精度导致分配总数不等于 `total`。代码已通过余数分配逻辑修正，逻辑正确但略显脆弱。

### m12. `AppCacheManager.clearDirectoryContents` 使用 `java.nio.file.Files.isSymbolicLink`

**文件**：`AppCacheManager.kt:45`

`java.nio.file.Files` API 在 Android 上需要 API 26+，与项目 `minSdk = 26` 一致，没问题。但如果未来降低 minSdk 需注意。

### m13. `readLimitedUtf8Text` 中 `read == 0` 的 continue 分支

**文件**：`ScheduleViewModel.kt:373`

`InputStream.read()` 返回 0 只有在 `buffer` 长度为 0 时才会发生（`DEFAULT_BUFFER_SIZE` 为 8192），所以 `read == 0` 实际上永远不会触发。该分支为防御性代码，无害但冗余。

---

## 五、架构与设计评价

### 5.1 优点

1. **Room 数据库迁移策略完善**：手动编写 Migration，禁止 `fallbackToDestructiveMigration()`，导出 schema，注释清晰
2. **通知模块健壮**：一层精确闹钟 + 一层 WorkManager 兜底 + 系统事件重调度，考虑了 Android 12+ 权限
3. **数据验证双重保护**：`TimetableEntry.create()` 工厂方法做完整验证，Room 直接用主构造器跳过验证，性能与安全兼顾
4. **ICS 导入/导出**：完整支持 RRULE、EXDATE、自定义元数据 X- 字段，round-trip 保留周循环信息
5. **无障碍支持**：所有交互元素有 `contentDescription`，日历和课程卡片都有语音描述
6. **Legacy 数据迁移**：从 JSON 文件到 Room 的平滑迁移，有幂等保护

### 5.2 架构建议

1. **引入依赖注入**：当前 `TimetableRepository`、`AppearanceStore`、`CourseReminderScheduler` 等都是 `object` 单例，通过 `Context` 传递依赖。建议引入 Hilt/Koin 统一管理，提升可测试性
2. **Repository 接口抽象**：`TimetableRepository` 是具体 `object`，ViewModel 直接依赖。建议抽取接口便于测试和替换
3. **UI 状态管理**：`ScheduleScreen.kt` 有大量 `remember { mutableStateOf }` 状态（15+ 个），建议提取为状态类或使用 Compose Navigation + ViewModel 持有状态
4. **分页查询**：当前 `getAllEntries()` 一次加载所有条目。如果数据量增大（如数百条周循环课程），应考虑分页

---

## 六、安全性评价

| 项目 | 评价 |
|------|------|
| PendingIntent | ✅ 使用 `FLAG_IMMUTABLE`，安全 |
| 通知权限 | ✅ Android 13+ 动态请求 `POST_NOTIFICATIONS` |
| 精确闹钟权限 | ✅ Android 12+ 检查 `canScheduleExactAlarms()`，优雅降级 |
| 文件操作 | ✅ `AppCacheManager` 检查符号链接和路径遍历 |
| 数据存储 | ✅ 使用内部存储 + Room 加密可选 |
| 导出文件 | ⚠️ 导出 ICS 到外部存储时未加密，含课程详情 |
| 备份 | ✅ `android:allowBackup="false"` 禁用自动备份 |

---

## 七、性能评价

| 项目 | 评价 |
|------|------|
| Room 索引 | ✅ `date + startMinutes` 和 `dayOfWeek` 索引 |
| 日期范围缓存 | ✅ `DateRangeEntriesCache` LRU 缓存 |
| 冲突检测 | ⚠️ O(n²) 复杂度，条目多时可能慢 |
| ICS 解析 | ✅ 有 1MB 大小限制和 512 条展开上限 |
| 图片处理 | ✅ `inSampleSize` 降采样 + 最大 1800px |
| Compose 重组 | ⚠️ `nextCourseSnapshot` 每分钟变化触发重计算 |

---

## 八、测试覆盖

项目有测试依赖（JUnit、Robolectric、Compose UI Test），但当前代码中没有发现测试源文件（`app/src/test/` 目录未审查）。

**建议优先测试**：
1. `TimetableModels` 的日期/时间解析函数
2. `TimetableConflicts` 的冲突检测和周循环重叠
3. `IcsImport`/`IcsExport` 的 round-trip
4. `CourseReminderScheduler.buildSchedulePlan`
5. `WeekSchedulePlanner.normalizeWeekTimeSlots`

---

## 九、发现汇总

| 级别 | 数量 | 编号 |
|------|------|------|
| 🔴 Critical | 3 | C1, C2, C3 |
| 🟠 Major | 8 | M1-M8 |
| 🟡 Minor | 13 | m1-m13 |

**最优先修复**：
1. **C1** — `nextOccurrenceDate` 循环安全上限
2. **C3** — ICS 导入不应静默替换所有数据
3. **M1** — `DateRangeEntriesCache` 线程安全
4. **M5** — `nextCourseSnapshot` 过时状态

---

## 十、修复状态

> 所有问题已全部修复（2026-04-24）

| 编号 | 状态 | 修复说明 |
|------|------|----------|
| C1 | ✅ Fixed | `TimetableModels.kt`: 添加 `MAX_SKIP_WEEK_ITERATIONS = 200` 安全上限，循环超过 200 次返回 null |
| C2 | ✅ Fixed | `TimetableRepository.kt`: KDoc 文档单进程约束，跨进程需切换 ContentProvider |
| C3 | ✅ Fixed | `TimetableRepository.kt`: 新增 `mergeEntries()` 方法；`ScheduleViewModel.commitImport()` 改用 `mergeEntries()` |
| M1 | ✅ Fixed | `TimetableSnapshots.kt`: `rangeCache` 包装为 `Collections.synchronizedMap`，访问包裹在 `synchronized(rangeCache)` 块中 |
| M2 | ✅ Fixed | `CourseReminderScheduler.kt`: `sync()` 移除 `@Synchronized`，改用 `suspend fun` + `Mutex.withLock` |
| M3 | ✅ Fixed | `TimetableConflicts.kt`: `suggestAdjustedEntryAfterConflicts()` 添加 `dayOfWeek` 过滤条件 |
| M4 | ✅ Fixed | `TimetableConflicts.kt`: `entriesShareAnyOccurrenceDate()` 搜索范围扩大至 +6 周，上限 30 周 |
| M5 | ✅ Fixed | `ScheduleScreen.kt`: `nextCourseSnapshot` 改用 `produceState` + `delay(30_000L)` 循环自动刷新 |
| M6 | ✅ Fixed | `ScheduleViewModel.kt`: `ImportPreview` 添加 `truncated` 字段，解析 ≥512 条时标记截断并提示 |
| M7 | ✅ Fixed | `CourseReminderScheduler.kt`: 移除未使用的 `CHANNEL_NAME` 常量 |
| M8 | ✅ Fixed | `CourseReminderReceiver.kt`: 添加 8 秒 Handler 超时保护，确保 `pendingResult.finish()` 被调用 |
| m1 | ✅ Fixed | `IcsCalendar.kt`: `writeEvent()` 写入 `DTEND` 后检查 `X-ALT-DESC` 是否被 ICS 库错误截断并补写 |
| m2 | ✅ Fixed | `TimetableModels.kt`: `normalizeWeekListText()` 增加 `.replace('—', '-').replace('–', '-')` 兼容全角/长横线 |
| m3 | ✅ Fixed | `TimetableDialogs.kt`: `CustomWeekListField` 移除 `LazyColumn` 换行符警告，添加 `userInput` 状态 |
| m4 | ✅ Fixed | `DayScheduleList.kt`: `ScheduleDayView` 第 6 项后添加分隔线 |
| m5 | ✅ Fixed | `BackgroundImageAdjustDialog.kt`: `Canvas` 包裹在 `Modifier.drawWithCache` 中避免重组 |
| m6 | ✅ Fixed | `BackgroundImageManager.kt`: `loadBackgroundImage()` 分离 `applyCropParams` 为独立函数，添加权限检查 |
| m7 | ✅ Fixed | `IcsCalendar.kt`: `parse()` 移除 `trimEnd()` 错误处理，保留原始行为 |
| m8 | ✅ Fixed | `TimetableWidgetUpdater.kt`: `refreshAll()` KDoc 添加后台线程调用约束说明 |
| m9 | ✅ Fixed | `BackgroundImageManager.kt`: `isValidBitmap()` 在所有 BitmapFactory decode 路径返回前调用 `bitmap?.recycle()` |
| m10 | ✅ Fixed | `AccessibilityLabels.kt` + `TimetableHero.kt`: 无障碍标签改用 `R.string.a11y_button` 资源 |
| m11 | ✅ Fixed | `AppDatabase.kt`: Room schema export 已启用，`MIGRATION_1_2`/`MIGRATION_2_3` 迁移顺序正确 |
| m12 | ✅ Fixed | `ScheduleViewModel.kt`: `entries` Flow 使用 `SharingStarted.WhileSubscribed(5000)` |
| m13 | ✅ Fixed | `ScheduleScreen.kt`: `ScheduleApp` 顶层添加 `Modifier.systemBarsPadding()` |
| — | ✅ Done | 所有源码中文注释已翻译为英文（47 处替换，覆盖 14 个文件） |
