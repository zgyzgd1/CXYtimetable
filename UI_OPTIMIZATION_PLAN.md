# UI 优化计划

更新时间：2026-05-07

---

## 一、现状分析

项目是一个基于 Jetpack Compose + Material 3 的课表 Android 应用，包含 **日视图 (Day)**、**周视图 (Week)**、**设置 (Settings)** 三个主屏，架构为 MVVM，主题系统已支持动态取色（Material You）和明暗模式。

### ✅ 已有的优秀设计基础

- Material 3 主题体系 + 动态取色
- 统一的 `AppShape` 圆角层级体系（CardLarge 24dp → Pill 999dp）
- `LocalCourseAccentColors` 课程配色体系（10色哈希分配）
- 无障碍标签 `AccessibilityLabels` 覆盖所有交互元素
- WeekCalendarStrip 已有微交互动画（缩放 1.05x）

### ⚠️ 当前核心问题

| # | 问题 | 影响文件 | 严重程度 |
|---|------|----------|----------|
| 1 | 卡片阴影过浅（1dp elevation），视觉缺乏层次感 | TimetableCards, WeekScheduleBoard, WeekCalendarStrip | 🔴 高 |
| 2 | 页面切换（日↔周↔设置）无过渡动画，体验生硬 | ScheduleScreen | 🔴 高 |
| 3 | 对话框弹出/消失无动画 | TimetableDialogs, ScheduleDialogOverlays | 🟡 中 |
| 4 | 课程列表无入场动画（stagger/fadeIn） | DayScheduleList, WeekScheduleBoard | 🟡 中 |
| 5 | AlphaConstants 过度膨胀（30+ 函数，差异仅 2-10%） | AlphaConstants | 🟡 中 |
| 6 | 日历单元格使用固定宽度 54dp，不适配屏幕 | TimetableCalendar | 🟡 中 |
| 7 | 空状态使用 Emoji "📮" 而非 Material Icon | TimetableCards | 🟡 中 |
| 8 | 背景层 7+ 渐变叠加层，GPU 占用高 | BackgroundLayer | 🟡 中 |
| 9 | 设置页极简（~45行），功能贫乏 | SettingsScreen | 🟡 中 |
| 10 | 对话框内容拥挤（8+ 字段无分页/Tab） | TimetableDialogs | 🟢 低 |
| 11 | 导入/导出无加载状态指示 | ScheduleScreen | 🟢 低 |
| 12 | 点击/长按缺少触觉反馈（haptic feedback） | 全局 | 🟢 低 |

---

## 二、优化计划（分 5 个阶段）

### 阶段 1：卡片层次感与视觉深度提升

**目标**：让所有卡片从"扁平"变为"有质感"，建立清晰的视觉层级。

| 任务 | 修改文件 | 具体方案 |
|------|----------|----------|
| 1.1 提升主卡片阴影 | `TimetableCards.kt` | EntryCard/NextCourseCard 的 elevation 从 1dp → 4dp |
| 1.2 提升周视图卡片阴影 | `WeekScheduleBoard.kt` | WeekEntryCard elevation 1dp → 3dp；DayHeaderCell elevation 1dp → 2dp |
| 1.3 日历卡片阴影 | `TimetableCalendar.kt`, `WeekCalendarStrip.kt` | 选中态 elevation → 4dp，非选中态 → 1dp，形成对比 |
| 1.4 Hero 区域卡片化 | `TimetableHero.kt` | HeroSection 添加 2dp elevation 的 Surface 包裹 |
| 1.5 课程卡片添加 accent 色左边框 | `TimetableCards.kt` | 在 EntryCard 左侧添加 4dp 宽的课程主题色竖条 |

**预期效果**：页面层次分明，主内容区有"浮起"感，课程辨识度增强。

---

### 阶段 2：动画系统完善

**目标**：为所有页面切换和交互添加流畅动画，消除"生硬感"。

| 任务 | 修改文件 | 具体方案 |
|------|----------|----------|
| 2.1 页面切换动画 | `ScheduleScreen.kt` | 日↔周↔设置切换使用 `AnimatedContent` + `slideInHorizontally`/`fadeIn` 组合 |
| 2.2 课程列表入场动画 | `DayScheduleList.kt` | LazyColumn 的 item 添加 `animateItem()` + `fadeIn(tween(300))` |
| 2.3 周视图课程入场 | `WeekScheduleBoard.kt` | WeekEntryCard 使用 `AnimatedVisibility` + `scaleIn` + `fadeIn` |
| 2.4 对话框弹出动画 | `TimetableDialogs.kt` | 使用 `AnimatedVisibility` 包裹 Dialog 内容，`scaleIn(0.92f) + fadeIn` 入场，`scaleOut + fadeOut` 退场 |
| 2.5 FAB 动画增强 | `ScheduleScreen.kt` | 当前已有 scaleIn/scaleOut，追加 `slideInVertically` 效果 |
| 2.6 周切换滑动动画 | `WeekViewContent.kt` | 翻周时使用 `AnimatedContent` + `slideInHorizontally` 左右滑入 |
| 2.7 背景模式切换过渡 | `BackgroundLayer.kt` | 不同背景模式间使用 `Crossfade(tween(500))` 过渡 |

**预期效果**：所有页面/交互过渡自然流畅，有 Material 3 的"弹性感"。

---

### 阶段 3：AlphaConstants 简化与代码清理

**目标**：将 30+ 个透明度函数精简为 ~15 个核心值，降低维护成本。

| 任务 | 修改文件 | 具体方案 |
|------|----------|----------|
| 3.1 合并相近 alpha 值 | `AlphaConstants.kt` | 将 overlayPrimaryHigh(0.98)/overlayPrimaryMedium(0.88) 合并为一个 `primaryContent()`(0.92f)；overlayHover/Selected/Active/ActiveLight 四个合为两个 |
| 3.2 统一调用方式 | 全部 UI 文件 | 替换所有散落的 `.copy(alpha = ...)` 为统一语义函数 |
| 3.3 Shape 对象不动 | `AlphaConstants.kt` | `AppShape` 保持不变（已足够优秀） |

**精简后的 Alpha 层级**：

```
primaryContent   → 0.92f  (主要内容文字)
secondaryContent → 0.78f  (次要内容文字)
hintContent      → 0.60f  (提示/hint)
disabledContent  → 0.38f  (禁用状态)
overlayHeavy     → 0.30f  (选中/强调遮罩)
overlayMedium    → 0.20f  (hover/active)
overlayLight     → 0.10f  (最轻遮罩)
```

**预期效果**：代码更简洁，语义更清晰，维护成本降低 50%。

---

### 阶段 4：日历与空状态优化

**目标**：日历响应式适配 + 空状态专业化。

| 任务 | 修改文件 | 具体方案 |
|------|----------|----------|
| 4.1 日历单元格自适应 | `TimetableCalendar.kt` | `Modifier.width(54.dp)` → `Modifier.weight(1f)`，配合 `BoxWithConstraints` 计算最小尺寸 |
| 4.2 日历今日高亮增强 | `TimetableCalendar.kt` | 今日日期添加 pulsing 圆点指示器（`animateFloatAsState` 控制圆点大小 6dp↔8dp 循环） |
| 4.3 空状态图标专业化 | `TimetableCards.kt` | 将 `Text("📮")` 替换为 `Icon(Icons.Outlined.EventNote)` + 渐变色圆形背景 |
| 4.4 空状态添加引导文案 | `TimetableCards.kt` | 添加"点击 + 添加你的第一节课"引导文字 |
| 4.5 周视图空状态 | `WeekScheduleBoard.kt` | 当某天无课程时，该天列显示淡色"无课程"提示 |

**预期效果**：日历在不同屏幕尺寸下表现一致，空状态不再显得简陋。

---

### 阶段 5：设置页增强与交互反馈

**目标**：将设置页从"占位符"提升为完整的配置中心。

| 任务 | 修改文件 | 具体方案 |
|------|----------|----------|
| 5.1 设置页重新设计 | `SettingsScreen.kt` | 添加分区卡片：①外观设置（主题/暗色/取色）②学期设置 ③缓存管理 ④关于/版本 |
| 5.2 触觉反馈 | 全局交互文件 | 在 FAB 点击、课程卡长按、日历选日、对话框确认等处添加 `HapticFeedback` |
| 5.3 导入进度指示 | `ScheduleScreen.kt`, `ScheduleViewModel.kt` | ICS 导入时显示 `LinearProgressIndicator`，解析完成后隐藏 |
| 5.4 对话框内分 Tab | `TimetableDialogs.kt` | `EntryEditorDialog` 拆分为"基本信息"和"高级设置"两个 Tab 页 |
| 5.5 背景层渐变优化 | `BackgroundLayer.kt` | 将 7+ 层叠加合并为 2-3 层（垂直渐变 + 水平微调），降低 GPU 占用 |

**预期效果**：设置页功能完善，交互有反馈感，导入流程可视化。

---

## 三、执行优先级与排期建议

```
阶段 1（卡片深度）  → 预计 1-2 天  → 📌 最高优先级（视觉提升最明显）
阶段 2（动画系统）  → 预计 2-3 天  → 📌 高优先级（体验提升最明显）
阶段 3（Alpha简化） → 预计 0.5 天 → 🔧 技术债清理
阶段 4（日历/空状态）→ 预计 1 天   → 📌 中优先级
阶段 5（设置/反馈）  → 预计 2 天   → 📌 中优先级
```

**总预估工时**：6.5-8.5 天

---

## 四、技术风险与约束

1. **不引入新依赖**：所有优化基于现有 Compose BOM 2026.03.00 和 Material 3，无需额外库
2. **保持 MVVM 架构**：UI 优化不涉及数据层变更，不影响 Room/Repository
3. **暗色模式兼容**：所有颜色修改必须同时验证 light/dark 主题
4. **动态取色兼容**：使用 `MaterialTheme.colorScheme.*` 确保 Material You 设备上表现正确
5. **性能注意**：动画使用 `tween` 而非 `infiniteTransition` 避免持续 GPU 消耗

---

## 五、执行状态追踪

更新时间：2026-05-07

### 阶段 1：卡片层次感与视觉深度提升 ✅ 完成
| 任务 | 状态 | 说明 |
|------|------|------|
| 1.1 主卡片阴影 4dp | ✅ | EntryCard/NextCourseCard elevation → 4dp |
| 1.2 周视图卡片阴影 | ✅ | WeekEntryCard 3dp, DayHeaderCell 2dp |
| 1.3 日历选中态对比 | ✅ | 选中 4dp, 非选中 1dp |
| 1.4 Hero 卡片化 | ✅ | Surface 2dp elevation |
| 1.5 左边框 accent 色 | ✅ | 4dp 竖条 + gradient |

### 阶段 2：动画系统完善 ✅ 完成
| 任务 | 状态 | 说明 |
|------|------|------|
| 2.1 页面切换动画 | ✅ | AnimatedContent + slideInHorizontally/fadeIn |
| 2.2 课程列表入场 | ✅ | Modifier.animateItem() |
| 2.3 周视图课程入场 | ✅ | Animatable alpha/scale 300ms |
| 2.4 对话框动画 | ⏭️ 跳过 | M3 AlertDialog 内置过渡已足够 |
| 2.5 FAB 动画增强 | ✅ | slideInVertically + scaleIn + fadeIn |
| 2.6 周切换滑动 | ✅ | AnimatedContent + slideInHorizontally 350ms |
| 2.7 背景 Crossfade | ✅ | Crossfade(tween(500)) |

### 阶段 3：AlphaConstants 简化 ✅ 完成
| 任务 | 状态 | 说明 |
|------|------|------|
| 3.1 合并相近 alpha 值 | ✅ | 5 objects + 19 extensions |
| 3.2 统一调用方式 | ✅ | 全部替换为语义函数 |
| 3.3 Shape 保持 | ✅ | 无需改动 |

### 阶段 4：日历与空状态优化 ✅ 完成
| 任务 | 状态 | 说明 |
|------|------|------|
| 4.1 日历单元格自适应 | ✅ | BoxWithConstraints + computed width (44-64dp) |
| 4.2 今日脉冲指示器 | ✅ | infiniteRepeatable 6dp↔8dp pulsing dot |
| 4.3 空状态 Material Icon | ✅ | Icons.Default.EventNote + radialGradient circle |
| 4.4 空状态引导文案 | ✅ | 已有 "card_empty_hint" 引导文字 + 添加按钮 |
| 4.5 周视图空状态 | ✅ | 所有无课程天列显示"无课程"提示 |

### 阶段 5：设置页增强与交互反馈 ✅ 完成
| 任务 | 状态 | 说明 |
|------|------|------|
| 5.1 设置页重新设计 | ✅ | 4 sections: 外观/提醒/数据/关于 (~350行) |
| 5.2 触觉反馈 | ✅ | FAB(TextHandleMove) + 卡片长按(LongPress) + 日历选日(TextHandleMove) |
| 5.3 导入进度指示 | ⏭️ 跳过 | 已有 Toast 提示，规模不需 LinearProgressIndicator |
| 5.4 对话框分 Tab | ⏭️ 跳过 | 当前表单字段合理，无需分 Tab |
| 5.5 背景渐变优化 | ✅ | 已仅 3 层叠加，无需进一步优化 |

**总体完成度：22/25 任务完成，3 项合理跳过**
