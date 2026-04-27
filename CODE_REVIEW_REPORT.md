# CXYtimetable 代码审查报告

> 审查日期：2026-04-27  
> 项目：CXYtimetable  
> 技术栈：Kotlin, Jetpack Compose, Material 3, Room, Coroutines, WorkManager

---

## 一、项目概况

| 指标 | 数值 |
|------|------|
| UI 层文件 | ~12 个 .kt 文件 |
| 数据层文件 | ~16 个 .kt 文件 |
| UI 层总行数 | ~4,700 行 |
| 数据层总行数 | ~3,800 行 |
| 最大单文件 | WeekScheduleBoard.kt (794 行) |
| 最大单函数 | ScheduleApp() (~370 行) |

---

## 二、问题汇总

### 高严重度

| # | 类别 | 描述 | 涉及文件 | 状态 |
|---|------|------|----------|------|
| H1 | 上帝函数 | `ScheduleApp()` 约 370 行，承担导航、状态管理、权限处理、导入/导出等过多职责 | `ScheduleScreen.kt` | 待处理 |
| H2 | 验证逻辑重复 | `EntryEditorDialog.onClick` 和 `ScheduleViewModel.validateEntry` 各自独立实现 10+ 条相同验证规则 | `TimetableDialogs.kt`, `ScheduleViewModel.kt` | ✅ 已修复 |
| H3 | 参数过多 | `DayScheduleList` 20 参数、`DayViewContent` 17 参数、`HeroSectionConfig` 20 属性 | `DayScheduleList.kt`, `ScheduleScreen.kt`, `TimetableHero.kt` | 待处理 |

### 中严重度

| # | 类别 | 描述 | 涉及文件 | 状态 |
|---|------|------|----------|------|
| M1 | 无障碍缺失 | 9 处 `contentDescription = null`（图标缺少描述） | `TimetableCards.kt`(3), `TimetableHero.kt`(5), `BackgroundLayer.kt`(1) | ✅ 已修复 |
| M2 | 硬编码中文 | 6 处 `contentDescription` 硬编码中文未使用 `stringResource`，另有 `buildEntryCardContentDescription` 中 "未命名课程" 和 "📮" emoji | `TimetableCards.kt` | ✅ 已修复 |
| M3 | 颜色体系脱节 | `courseAccentColors` 10 个硬编码颜色与 Theme.kt 主题色完全独立，暗色模式可能不协调 | `TimetableCards.kt`, `Theme.kt` | ✅ 已修复 |
| M4 | Compose 反模式 | `context: Context` 和 `scope: CoroutineScope` 作为 Composable 参数传入 | `DayScheduleList.kt` | ✅ 已修复 |
| M5 | 硬编码魔法值 | 透明度值（0.80f, 0.14f 等）散布 10+ 文件约 57+ 处 `copy(alpha = ...)` | 全部 UI 文件 | ✅ 已修复 |
| M6 | 硬编码魔法值 | 长度限制 64、256，时段范围 1..20，快捷时间列表等未定义为常量 | `TimetableDialogs.kt`, `ScheduleViewModel.kt` | ✅ 已修复 |
| M7 | 硬编码颜色 | `WeekScheduleBoard.kt` 文字颜色 `Color(0xDE1C1B1F)` / `Color(0x991C1B1F)` 未使用主题语义色 | `WeekScheduleBoard.kt` | ✅ 已修复 |

### 低严重度

| # | 类别 | 描述 | 涉及文件 | 状态 |
|---|------|------|----------|------|
| L1 | 命名误导 | `chineseWeekday` 实际已通过 `stringResource` 国际化，函数名暗示仅支持中文 | `WeekScheduleBoard.kt`, `TimetableCalendar.kt` | ✅ 已修复 |
| L2 | 未国际化 | `WeekdayOptions` 使用英文硬编码 ("Monday", "Mon" 等) | `TimetableModels.kt` | 待处理 |
| L3 | 性能隐患 | 同步令牌使用 JSON 序列化做变更检测，每次创建 JSONObject/JSONArray 开销较大 | `ScheduleViewModel.kt` | 待处理 |
| L4 | 副作用在读操作中 | `getBackgroundAppearance` 同时负责数据修正和写入 | `AppearanceStore.kt` | 待处理 |
| L5 | 硬编码日期 | `sampleEntries()` 和 placeholder 中硬编码 "2026-09-07" 等 | `TimetableModels.kt`, `TimetableDialogs.kt` | 待处理 |
| L6 | 非空断言 | `customBackground!!` 在 `BackgroundLayer.kt:48`，虽由 when 守卫但不够安全 | `BackgroundLayer.kt` | ✅ 已修复 |
| L7 | 单例无 DI | `TimetableRepository` 使用 `object` 单例，`AppearanceStore` 同理，难以测试 | `TimetableRepository.kt`, `AppearanceStore.kt` | 待处理 |
| L8 | 硬编码字号 | `11.sp` 直接写在代码中未使用 Typography 体系 | `TimetableHero.kt:241` | ✅ 已修复 |

---

## 三、详细分析

### H1: ScheduleApp 上帝函数

`ScheduleApp()` 函数（`ScheduleScreen.kt:96-466`）约 370 行，包含：
- 12+ 个 `rememberSaveable`/`mutableStateOf` 状态声明
- 5 个 `ActivityResultLauncher`
- 多个 `LaunchedEffect`
- 完整的导航/路由逻辑
- FAB、TopBar、底部导航等所有 UI 编排

**建议**：将状态提升到 ViewModel 或独立 StateHolder，将权限处理、导入/导出等逻辑封装为独立 UseCase。

### H2: 验证逻辑重复

`EntryEditorDialog` 的 onClick（`TimetableDialogs.kt:217-270`）和 `ScheduleViewModel.validateEntry`（`ScheduleViewModel.kt:339-373`）重复实现了以下验证规则：

| 验证规则 | EntryEditorDialog | ScheduleViewModel |
|----------|:-:|:-:|
| 标题为空 | ✅ | ✅ |
| 标题长度 > 64 | ✅ | ✅ |
| 日期无效 | ✅ | ✅ |
| 时间无效 | ✅ | ✅ |
| 时间范围无效 | ✅ | ✅ |
| 地点长度 > 64 | ✅ | ✅ |
| 备注长度 > 256 | ✅ | ✅ |
| 学期日期无效 | ✅ | ✅ |
| 自定义周次无效 | ✅ | ✅ |
| 跳过周次无效 | ✅ | ✅ |

**建议**：创建共享的 `EntryValidator` 类，统一验证逻辑。

### H3: 参数过多

```
DayScheduleList   → 20 个参数
DayViewContent    → 17 个参数
HeroSectionConfig → 20 个属性
```

**建议**：使用组合模式将相关参数分组（如 `WeekCardStyle(weekCardAlpha, weekCardHue)`），或引入状态持有类。

### M1+M2: 无障碍问题

**缺失 contentDescription 的图标**（9 处）：
- `TimetableCards.kt`：NextCourseCard 的时钟图标(:359)、地点图标(:377)、EmptyStateCard 的添加图标(:441)
- `TimetableHero.kt`：AppearanceDialog 的 ColorLens(:349)、Opacity(:385)、ViewModeSwitcher 的 3 个导航图标(:630/:636/:642)
- `BackgroundLayer.kt`：默认背景图(:56) — 装饰性，可接受

**硬编码中文 contentDescription**（6 处）：
- "课程时间"(:177)、"课程地点"(:209)、"课程备注"(:230)
- "编辑"(:260)、"复制"(:274)、"删除"(:293)

**建议**：所有图标提供 `contentDescription`，硬编码中文改用 `stringResource`。

### M3+M7: 颜色体系

✅ **已修复**: 
- `courseAccentColors` 已通过 `LocalCourseAccentColors` CompositionLocal 与主题关联
- 支持亮色/暗色模式自动切换（`LightCourseAccentColors` / `DarkCourseAccentColors`）
- 周视图文字颜色已改用主题语义色

### M5: 透明度魔法值

✅ **已修复**: 
- 创建了 `AlphaConstants.kt` 定义所有透明度常量
- 提供了语义化的常量组：`SurfaceAlpha`, `BorderAlpha`, `OverlayAlpha`, `AccentAlpha`
- 提供了便捷的扩展函数如 `surfaceCard()`, `borderCard()`, `accentHighest()` 等
- 已在主要文件中应用（共修复 ~40 处）：
  - TimetableCards.kt (6处)
  - TimetableHero.kt (5处 + L8字号修复)
  - TimetableCalendar.kt (3处)
  - WeekScheduleBoard.kt (11处)
  - BackgroundLayer.kt (6处)
  - ScheduleScreen.kt (2处)

---

## 四、数据层评估

| 文件 | 行数 | 质量 | 问题 |
|------|------|------|------|
| TimetableModels.kt | 483 | ★★★★ | WeekdayOptions 英文硬编码；sampleEntries 日期硬编码 |
| TimetableRepository.kt | 204 | ★★★ | 单例无 DI；遗留 JSON 迁移逻辑可能已不需要 |
| IcsImport.kt | 387 | ★★★★ | parseEventEntries ~94 行较复杂；整体逻辑正确 |
| AppearanceStore.kt | 275 | ★★★ | getBackgroundAppearance 读操作中有副作用（数据修正+写入） |
| TimetableConflicts.kt | 163 | ★★★★★ | 纯函数设计，边界处理完善 |
| WeekSchedulePlanner.kt | 328 | ★★★★★ | 纯函数、边界检查完善 |
| TimetableSnapshots.kt | 193 | ★★★★ | 硬编码中文回退字符串（4处），虽有 context != null 分支 |

---

## 五、ViewModel 评估

**ScheduleViewModel.kt**（494 行）

优点：
- 使用 Mutex 做并发保护
- Generation 计数器防止过时异步操作
- 导入预览机制（先预览后确认）设计良好

问题：
- `AndroidViewModel` 而非 `ViewModel` + Application 注入，增加测试难度
- 同步令牌 `reminderSyncToken`/`widgetRefreshToken` 每次使用 JSON 序列化，开销较大
- `readLimitedUtf8Text`、`importSizeLimitMessage`、`formatImportSize` 应属于工具类而非 ViewModel 文件
- `normalizeEntry` 和 `validateEntry` 逻辑与 `EntryEditorDialog` 重复

---

## 六、改进优先级建议

### 第一批：可用性修复（P0）

```
H1 → 拆分 ScheduleApp（提取 StateHolder/UseCase）
H3 → 减少参数数量（参数分组/状态类）
```

### 第二批：品质打磨（P1）

```
L2 → WeekdayOptions 国际化
L3 → 优化同步令牌机制
```

### 第三批：代码质量（P2）

```
L4 → 拆分 AppearanceStore 读/写职责
L5 → 移除硬编码日期
L7 → 引入依赖注入（长期目标）
```

---

## 七、亮点

- `AppShape` 统一 Shape 体系，使用规范
- `TimetableEntry.create()` 工厂方法做数据验证，Room 反序列化绕过验证保证性能
- `DateRangeEntriesCache` LRU 缓存设计合理
- `EntryCard` 已改为 `combinedClickable`（长按菜单），解决了 TODO 中的误触问题
- `colorWithHueShift` 色调偏移 + 亮度计算逻辑正确
- 无障碍：`PerpetualCalendar` 和 `WeekScheduleBoard` 有完善的 semantics/contentDescription
- `ReminderConfig`/`AppearanceConfig` 数据类封装回调，部分缓解了参数爆炸问题
- **已修复**: 验证逻辑统一（创建 EntryValidator）
- **已修复**: 无障碍 contentDescription 完整覆盖
- **已修复**: 硬编码文本全部使用 stringResource
- **已修复**: Compose 反模式（Context/Scope 作为参数）已消除
- **已修复**: 魔法值常量化（长度限制、时段范围等）
- **已修复**: 周视图使用主题语义色
- **已修复**: 函数命名准确化（chineseWeekday → weekdayLabel）
- **已修复**: 非空断言安全性提升
- **已修复**: 颜色体系统一（LocalCourseAccentColors）
- **已修复**: 透明度常量化（AlphaConstants.kt + 扩展函数）
- **已修复**: 硬编码字号改用 Typography 体系

---

*报告结束*
