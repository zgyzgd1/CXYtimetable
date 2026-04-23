# UI 优化方案

> 基于对 `ui/` 目录下 16 个文件的全面审查，按优先级从高到低排列。

---

## P0 — 必须修复（影响可用性/数据安全）

### 1. 周视图硬编码颜色，深色模式不可用

**文件**: `WeekScheduleBoard.kt`（全文件）

**问题**: 周视图中大量硬编码颜色，在深色模式下深色文字在深色背景上几乎不可见：

```kotlin
// 硬编码示例（WeekOverviewHeader）
color = Color(0xFF111319)   // 日期
color = Color(0xFF3A4050)   // 周次
color = Color(0xFF677086)   // 统计

// SummaryPill
contentColor = Color(0xFF111319)

// DayHeaderCell
Color(0xFF495164), Color(0xFF778096)

// TimeSlotCell
Color(0xFF10131A), Color(0xFF2E3442)

// GlassActionChip
Color(0xFF111319)
```

**方案**: 统一改用 `MaterialTheme.colorScheme` 语义色：

| 原硬编码 | 替换为 |
|---------|--------|
| `Color(0xFF111319)` / `Color(0xFF10131A)` | `MaterialTheme.colorScheme.onSurface` |
| `Color(0xFF3A4050)` / `Color(0xFF2E3442)` | `MaterialTheme.colorScheme.onSurfaceVariant` |
| `Color(0xFF677086)` / `Color(0xFF778096)` / `Color(0xFF495164)` | `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f)` |
| `Color(0x36FFFFFF)` / `Color(0x3CFFFFFF)` / `Color(0x40FFFFFF)` | `MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)` |
| `Color(0x2CFFFFFF)` | `MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.2f)` |
| `Color(0x42FFFFFF)` / `Color(0x18FFFFFF)` / `Color(0x10FFFFFF)` | `MaterialTheme.colorScheme.primary.copy(alpha=0.1f)` |

**影响范围**: `WeekOverviewHeader`, `SummaryPill`, `DayHeaderCell`, `TimeSlotCell`, `GlassActionChip`, `WeekDayLane`

---

### 2. 删除操作无二次确认

**文件**: `ScheduleScreen.kt:559`, `TimetableCards.kt:243-255`

**问题**: 点击删除图标直接执行删除，误触无法恢复：

```kotlin
onDelete = { viewModel.deleteEntry(entry.id) },
```

**方案 A — 确认弹窗（推荐）**:

在 `ScheduleScreen.kt` 中增加删除确认状态：

```kotlin
var pendingDeleteEntryId by remember { mutableStateOf<String?>(null) }

// EntryCard 调用处改为
onDelete = { pendingDeleteEntryId = entry.id },

// 在对话框区域添加
pendingDeleteEntryId?.let { entryId ->
    val entryName = entries.find { it.id == entryId }?.title.orEmpty()
    AlertDialog(
        onDismissRequest = { pendingDeleteEntryId = null },
        title = { Text("确认删除") },
        text = { Text("确定删除「${entryName.ifBlank { "未命名课程" }}」吗？此操作不可撤销。") },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.deleteEntry(entryId)
                    pendingDeleteEntryId = null
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = { pendingDeleteEntryId = null }) { Text("取消") }
        },
    )
}
```

**方案 B — Snackbar + Undo（更高级）**:

ViewModel 中暂存已删除条目，5 秒内可通过 Undo 恢复。

---

### 3. `ScheduleApp` 巨型函数拆分

**文件**: `ScheduleScreen.kt`（670+ 行）

**问题**: 单函数包含 20+ 状态变量、多个 Launcher、多个 Dialog 条件渲染，可维护性极差。

**方案**: 按功能域拆分为子 Composable + StateHolder：

```
ScheduleApp (入口，保留 Scaffold + 导航)
├── DayViewContent (日视图 LazyColumn)
│   ├── HeroSection (已有)
│   ├── NextCourseCard (已有)
│   ├── PerpetualCalendar (已有)
│   └── EntryCard list (已有)
├── WeekViewContent (周视图 Column)
│   ├── WeekCalendarStrip (已有)
│   └── WeekScheduleBoard (已有)
├── ReminderState (提醒相关状态 + Launcher)
│   ├── reminderMinutes, exactAlarmEnabled
│   ├── notificationPermissionLauncher
│   └── exactAlarmSettingsLauncher
├── AppearanceState (外观相关状态 + Launcher)
│   ├── backgroundAppearance, weekCardAlpha, weekCardHue
│   └── backgroundImageLauncher
└── Dialogs (已有，保持独立)
```

具体实施：

```kotlin
// 提取提醒状态到独立类
@Stable
class ReminderState(
    val reminderMinutes: List<Int>,
    val exactAlarmEnabled: Boolean,
    val reminderOptions: List<Int>,
    val onUpdateMinutes: (List<Int>) -> Unit,
    val onEnableNotifications: () -> Unit,
    val onOpenExactAlarmSettings: () -> Unit,
)

// 提取日视图内容
@Composable
fun DayViewContent(
    entries: List<TimetableEntry>,
    selectedLocalDate: LocalDate,
    selectedDate: String,
    onSelectedDateChange: (String) -> Unit,
    reminderState: ReminderState,
    appearanceState: AppearanceState,
    onEditEntry: (TimetableEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) { /* LazyColumn 内容 */ }
```

---

## P1 — 重要优化（影响体验/一致性）

### 4. `WeekEntryBlock` 白色文字在低透明度色块上不可读

**文件**: `WeekScheduleBoard.kt:542-568`

**问题**: 当 `cardAlpha` 较低（0.35）时，白色文字在浅色色块上对比度极差。

**方案**: 根据色块亮度自动选择文字颜色：

```kotlin
// 在 WeekScheduleBoard.kt 或 Theme.kt 中添加
fun contentColorForBackground(background: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(background.toArgb(), hsv)
    // HSV[2] = brightness (0-1)
    val brightness = hsv[2] * background.alpha
    return if (brightness > 0.55f) Color.Black.copy(alpha = 0.88f) else Color.White
}

// 使用
val textContentColor = contentColorForBackground(color)
Text(text = entry.title, color = textContentColor)
```

---

### 5. `NavigationBarItem` 缺少图标

**文件**: `TimetableHero.kt:610-627`

**问题**: `icon = {}` 空实现，违反 Material 3 规范，视觉和可访问性均差。

**方案**:

```kotlin
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Settings

NavigationBarItem(
    selected = currentDestination == AppDestination.DAY,
    onClick = { onDestinationChange(AppDestination.DAY) },
    icon = { Icon(Icons.Default.CalendarViewDay, contentDescription = null) },
    label = { Text("日视图") },
)
NavigationBarItem(
    selected = currentDestination == AppDestination.WEEK,
    onClick = { onDestinationChange(AppDestination.WEEK) },
    icon = { Icon(Icons.Default.CalendarViewWeek, contentDescription = null) },
    label = { Text("周视图") },
)
NavigationBarItem(
    selected = currentDestination == AppDestination.SETTINGS,
    onClick = { onDestinationChange(AppDestination.SETTINGS) },
    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
    label = { Text("设置") },
)
```

---

### 6. `EntryEditorDialog` 表单过长，需滚动支持

**文件**: `TimetableDialogs.kt:41-238`

**问题**: 选择"按周循环"后字段多达 10+，对话框溢出屏幕，无滚动指示。

**方案**: 将 `AlertDialog` 的 `text` 内容包裹在 `verticalScroll` 中：

```kotlin
text = {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ... 所有表单字段
    }
},
```

---

### 7. 时间输入改为 TimePicker 或快捷选项

**文件**: `TimetableDialogs.kt:85-96`, `314-340`

**问题**: 纯文本输入 `08:00` 格式，容易出错。

**方案 A — 快捷时间选项（改动小）**:

在 `TimeRangeFields` 上方添加常用时间行：

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    listOf("08:00", "08:30", "10:00", "10:30", "14:00", "16:00").forEach { time ->
        OutlinedButton(
            onClick = { onStartChanged(time) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) { Text(time, style = MaterialTheme.typography.labelSmall) }
    }
}
```

**方案 B — Material TimePicker（更正式）**:

点击文本框弹出 `TimePickerDialog`，确认后回填。改动较大但体验最佳。

---

### 8. `EntryCard` 操作按钮易误触

**文件**: `TimetableCards.kt:211-257`

**问题**: 编辑/复制/删除三个按钮始终暴露，排列紧密（间距 2.dp），删除无确认。

**方案 A — 长按菜单（推荐）**:

```kotlin
// 使用 DropdownMenu
var showMenu by remember { mutableStateOf(false) }

Box {
    // 卡片主体，长按触发菜单
    Card(
        modifier = Modifier
            .combinedClickable(
                onClick = onEdit,
                onLongClick = { showMenu = true },
            ),
    ) { /* 卡片内容 */ }

    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(text = { Text("编辑") }, onClick = { showMenu = false; onEdit() })
        DropdownMenuItem(text = { Text("复制") }, onClick = { showMenu = false; onDuplicate() })
        DropdownMenuItem(text = { Text("删除") }, onClick = { showMenu = false; onDelete() })
    }
}
```

**方案 B — 保留按钮但增大间距 + 删除确认**:

将按钮间距从 `2.dp` 增大到 `6.dp`，删除按钮使用 `MaterialTheme.colorScheme.error` 色调警示。

---

### 9. 统一两套色板

**文件**: `TimetableCards.kt:51-62` vs `WeekScheduleBoard.kt:53-62`

**问题**: 日视图用 10 色色板，周视图用 8 色色板，同一课程在两种视图下颜色不同。

**方案**: 提取统一色板到公共位置：

```kotlin
// 新建 ui/ThemeColors.kt 或放在 Theme.kt 中
val CourseAccentColors = listOf(
    Color(0xFF7986CB),
    Color(0xFF4DB6AC),
    Color(0xFFFF8A65),
    Color(0xFFA1887F),
    Color(0xFF4FC3F7),
    Color(0xFFAED581),
    Color(0xFFBA68C8),
    Color(0xFFFFB74D),
    Color(0xFFF06292),
    Color(0xFF4DD0E1),
)

fun accentColorFor(title: String): Color =
    CourseAccentColors[(title.hashCode() and Int.MAX_VALUE) % CourseAccentColors.size]
```

`TimetableCards.kt` 和 `WeekScheduleBoard.kt` 都引用同一色板和同一 `accentColorFor` 函数。

---

### 10. `normalizeWeekListText` 函数重复定义

**文件**: `TimetableDialogs.kt:306-311`, `ScheduleViewModel.kt:275-279`

**方案**: 提取到 `data/` 包的工具函数中：

```kotlin
// data/StringUtils.kt
fun normalizeWeekListText(raw: String): String =
    raw.trim().replace('，', ',').replace(" ", "")
```

---

## P2 — 改善优化（提升品质）

### 11. 卡片圆角体系统一

**当前状态**: 6 种圆角值（16/18/20/24/28/999.dp）

**方案**: 定义 `Shape` 体系常量：

```kotlin
// Theme.kt 或新建 ui/Shape.kt
object AppShape {
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(18.dp)
    val Large = RoundedCornerShape(24.dp)
    val XL = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(999.dp)
}
```

映射：
| 组件 | 当前 | 统一为 |
|------|------|--------|
| `EntryCard` | 24.dp | `AppShape.Large` |
| `NextCourseCard` | 20.dp | `AppShape.Large` |
| `EmptyStateCard` | 24.dp | `AppShape.Large` |
| `WeekScheduleBoard` | 28.dp | `AppShape.XL` |
| `PerpetualCalendar` | 24.dp | `AppShape.Large` |
| `TimeSlotCell` | 16.dp | `AppShape.Medium` |
| `DayHeaderCell` | 18.dp | `AppShape.Medium` |

---

### 12. `HeroSection` 参数过多（22 个）

**文件**: `TimetableHero.kt:63-85`

**方案**: 封装为数据类：

```kotlin
data class ReminderConfig(
    val minutes: List<Int>,
    val options: List<Int>,
    val exactAlarmRequired: Boolean,
    val exactAlarmEnabled: Boolean,
    val onMinutesChange: (List<Int>) -> Unit,
    val onEnableNotifications: () -> Unit,
    val onOpenExactAlarmSettings: () -> Unit,
)

data class AppearanceConfig(
    val backgroundMode: AppBackgroundMode,
    val hasCustomBackground: Boolean,
    val weekCardAlpha: Float,
    val weekCardHue: Float,
    val onSelectBackgroundImage: () -> Unit,
    val onUseBundledBackground: () -> Unit,
    val onUseGradientBackground: () -> Unit,
    val onAdjustCustomBackground: () -> Unit,
    val onClearCustomBackground: () -> Unit,
    val onWeekCardAlphaChange: (Float) -> Unit,
    val onWeekCardHueChange: (Float) -> Unit,
)

@Composable
fun HeroSection(
    courseCount: Int,
    onImport: () -> Unit,
    onExport: () -> Unit,
    reminderConfig: ReminderConfig,
    appearanceConfig: AppearanceConfig,
)
```

---

### 13. `PerpetualCalendar` 日期卡片颜色动画优化

**文件**: `TimetableCalendar.kt:154-171`

**问题**: 30+ 个日期卡片都持有 `animateColorAsState`，切换时全部参与动画。

**方案**: 仅选中/取消选中项使用动画：

```kotlin
val containerColor = if (isSelected || wasJustDeselected) {
    // 使用 animateColorAsState
    animatedColor
} else {
    // 静态颜色
    when {
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    }
}
```

或更简单地，移除动画改用 `updateTransition` 只对选中项做动画。

---

### 14. 周视图手势方向锁定

**文件**: `ScheduleScreen.kt:346-368`, `397-419`

**问题**: 外层水平滑动切换周，内层纵向滚动 + 课表水平滚动，手势可能冲突。

**方案**: 使用 `pointerInput` 时增加方向锁定：

```kotlin
Modifier.pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { /* 记录起始方向 */ },
        onDrag = { change, dragAmount ->
            if (directionLocked == null) {
                directionLocked = if (abs(dragAmount.x) > abs(dragAmount.y)) HORIZONTAL else VERTICAL
            }
            if (directionLocked == HORIZONTAL) {
                // 处理水平拖拽
            }
        },
        onDragEnd = { directionLocked = null },
    )
}
```

---

### 15. `AppBackgroundLayer` 避免重复解码图片

**文件**: `BackgroundLayer.kt:31-42`

**方案**: 将 `mode` 单独作为 `produceState` 的 key：

```kotlin
val customBackground by produceState<ImageBitmap?>(
    initialValue = null,
    key1 = backgroundAppearance.mode,  // 仅 mode 变化时重新读取
) {
    value = if (backgroundAppearance.mode == AppBackgroundMode.CUSTOM_IMAGE) {
        withContext(Dispatchers.IO) { /* 解码 */ }
    } else null
}
```

---

### 16. 辅助功能描述增强

**文件**: `AccessibilityLabels.kt:48-50`

**当前**:
```kotlin
internal fun buildHeroActionContentDescription(label: String): String = "$label，按钮"
```

**优化**:

```kotlin
internal fun buildHeroActionContentDescription(label: String): String = when {
    label.startsWith("提醒") -> "$label，点击设置课前提醒时间"
    label == "背景" -> "$label，点击调整背景和外观"
    label == "导入" -> "$label，点击导入课表文件"
    label == "导出" -> "$label，点击导出课表为文件"
    else -> "$label，按钮"
}
```

---

## 改动量估算

| 编号 | 改动文件 | 预估工作量 |
|------|---------|-----------|
| 1 | WeekScheduleBoard.kt | 中（批量替换颜色） |
| 2 | ScheduleScreen.kt + TimetableCards.kt | 小（加确认弹窗） |
| 3 | ScheduleScreen.kt | 大（重构拆分） |
| 4 | WeekScheduleBoard.kt | 小（添加颜色计算函数） |
| 5 | TimetableHero.kt | 小（添加图标） |
| 6 | TimetableDialogs.kt | 小（加 verticalScroll） |
| 7 | TimetableDialogs.kt | 中（TimePicker 或快捷选项） |
| 8 | TimetableCards.kt | 中（改为长按菜单） |
| 9 | TimetableCards.kt + WeekScheduleBoard.kt + 新文件 | 小（提取公共色板） |
| 10 | TimetableDialogs.kt + ScheduleViewModel.kt + 新文件 | 小（提取公共函数） |
| 11 | 全局 | 中（统一 Shape 常量） |
| 12 | TimetableHero.kt + ScheduleScreen.kt | 中（封装数据类） |
| 13 | TimetableCalendar.kt | 小 |
| 14 | ScheduleScreen.kt | 中 |
| 15 | BackgroundLayer.kt | 小 |
| 16 | AccessibilityLabels.kt | 小 |

---

## 建议实施顺序

```
第一批（可用性修复）: #1 → #2 → #4
第二批（体验优化）:   #5 → #6 → #8 → #9
第三批（架构改善）:   #3 → #12 → #10 → #11
第四批（品质打磨）:   #7 → #13 → #14 → #15 → #16
```
