# 项目交接文档

> **最后更新**: 2026-04-24  
> **基于版本**: v1.26 (Version Code: 27)  
> **审查修复轮次**: 第 3 轮（全面 i18n 迁移已完成）

## 1. 基本信息
- **项目名称**: CXYtimetable
- **当前版本**: v1.26 (Version Code: 27)
- **开发语言**: Kotlin
- **核心框架**: Jetpack Compose, Room, Coroutines, WorkManager
- **目标 SDK**: API 36 (Android 16 预览/最新标准)
- **最低 SDK**: API 26 (Android 8.0)
- **Java 版本**: 17

## 2. 核心功能边界
根据产品定义，本项目遵循以下**硬性约束**：
- **纯本地应用**：不引入账号系统，不依赖后端服务器。
- **无云同步**：数据完全存储在本地（Room DB），跨设备迁移依赖 `.ics` 文件的导入导出。
- **轻量化**：保持包体积小，UI 简洁无广告。

## 3. 项目架构与关键目录
```text
app/src/main/java/com/example/timetable/
├── data/                         # 数据层
│   ├── room/                     #   Room 数据库
│   │   ├── AppDatabase.kt        #     数据库定义 (v3)，含 Migration 策略
│   │   └── TimetableDao.kt       #     DAO 接口
│   ├── AppCacheManager.kt        #   缓存清理工具
│   ├── AppearanceStore.kt        #   外观偏好 (背景/卡片透明度/色相/节次时间)
│   ├── BackgroundImageManager.kt #   自定义背景图 URI 持久化
│   ├── BackgroundImageTransform.kt
│   ├── IcsCalendar.kt            #   ICS 日历解析/导出 (自研，支持 RRULE/EXDATE/TZID)
│   ├── SemesterStore.kt          #   学期起止日期存储
│   ├── TimetableConflicts.kt     #   冲突检测与顺延建议
│   ├── TimetableModels.kt        #   核心数据模型 (TimetableEntry, 枚举, 工厂方法, 工具函数)
│   ├── TimetableRepository.kt    #   数据仓库 (Room + 遗留 JSON 迁移兜底)
│   ├── TimetableSnapshots.kt     #   下一节课快照计算
│   └── WeekSchedulePlanner.kt    #   周视图节次配置
├── notify/                       # 调度层
│   ├── CourseReminderReceiver.kt       # 闹钟触发 Receiver
│   ├── CourseReminderRescheduleReceiver.kt  # 开机/时区变更后重新调度
│   ├── CourseReminderScheduler.kt      # 接力式调度核心 (含 syncToken 增量防抖)
│   └── ReminderFallbackWorker.kt       # WorkManager 兜底 Worker
├── ui/                           # 视图层
│   ├── ScheduleScreen.kt         #   主页面脚手架 (日/周/设置三栏切换)
│   ├── ScheduleViewModel.kt      #   核心 ViewModel (StateFlow + SharedFlow)
│   ├── ScheduleDialogOverlays.kt #   弹窗组件 (删除确认/导入冲突/时间冲突)
│   ├── DayScheduleList.kt        #   日视图列表
│   ├── WeekScheduleBoard.kt      #   周视图网格板
│   ├── WeekCalendarStrip.kt      #   周日期选择条
│   ├── TimetableCards.kt         #   课程卡片组件
│   ├── TimetableDialogs.kt       #   课程编辑/节次编辑等弹窗
│   ├── TimetableHero.kt          #   Hero 区域 + 外观/提醒设置弹窗
│   ├── TimetableCalendar.kt      #   万年历组件
│   ├── SettingsScreen.kt         #   设置页面
│   ├── Theme.kt                  #   Material 3 主题
│   ├── AccessibilityLabels.kt    #   无障碍内容描述
│   ├── AppDestination.kt         #   导航目标定义
│   ├── AppLaunchTarget.kt        #   启动参数
│   ├── BackgroundLayer.kt        #   背景渲染层
│   ├── BackgroundImageAdjustDialog.kt
│   └── CustomBackgroundImage.kt
├── widget/                       # 桌面小部件
│   ├── TimetableWidgetProviders.kt  # 今日课表 + 下一节课两个 Widget
│   └── TimetableWidgetUpdater.kt    # Widget 数据刷新
└── MainActivity.kt               # 入口 Activity
```

## 4. 核心机制说明

### 4.1 数据持久化
- 采用 **Room** 作为单一数据源 (`AppDatabase` v3)。
- 已建立显式 **Migration 策略**：`MIGRATION_1_2`（新增周循环字段）、`MIGRATION_2_3`（新增查询索引）。
- **禁止** `fallbackToDestructiveMigration()`，所有 Schema 变更必须编写显式迁移脚本。
- `exportSchema = true` 已开启，schema 文件位于 `app/schemas/`。
- 遗留的 JSON 缓存机制 (`AppCacheManager`) 仅作为兼容兜底，`bootstrapMutex` 确保迁移安全。
- **数据库索引**：`TimetableEntry` 实体已声明 `@Index(value = ["date", "startMinutes"])` 和 `@Index(value = ["dayOfWeek"])`，优化高频排序和范围查询。

### 4.2 课前提醒
- 采用**接力式调度** (`CourseReminderScheduler`)：每次只注册最近的一批提醒，闹钟触发后再计算并注册下一批。
- **增量防抖**：`syncReminders()` 通过 `reminderSyncMutex` + `syncToken` 机制，仅在数据实际变更时才触发调度；`scheduleSignature` 实现闹钟级增量注册，避免全量 IPC。
- **精确闹钟权限**：Android 12+ 仅声明 `SCHEDULE_EXACT_ALARM`（已移除不适用的 `USE_EXACT_ALARM`），并注册了权限变更广播。未授权时降级为非精确闹钟，UI 会提示用户。
- **哈希碰撞保护**：`requestCodeFor` 使用 `Objects.hash()` 替代字符串拼接 `hashCode()`，并在 `buildSchedulePlan` 中检测碰撞后 XOR 兜底。
- **WorkManager 兜底**：`ReminderFallbackWorker` 以 15 分钟间隔运行，确保精确闹钟失效后提醒延迟不超过 15 分钟。

### 4.3 背景图持久化
- 用户选择自定义背景后，应用通过 `Intent.FLAG_GRANT_READ_URI_PERMISSION` 获取永久读取权限，并将 URI 保存在 SharedPreferences 中。

### 4.4 数据验证
- `TimetableEntry` 主构造器**不含** `init` 验证块，Room 反序列化零开销。
- 人工构造时**必须**使用 `TimetableEntry.create()` 工厂方法，内含完整的数据合法性验证（日期、时间范围、重复规则、周次格式等），验证失败抛出 `IllegalArgumentException` 并附带可读消息。
- 当前所有人工构造处（`ScheduleScreen.kt`、`IcsCalendar.kt`、`TimetableRepository.kt`、`TimetableDialogs.kt`、`sampleEntries()`）均已迁移至 `.create()`。
- **注意**：`EntryEditorDialog` 中保存逻辑已使用 `TimetableEntry.create()` 而非 `initial.copy()`，确保 UI 层保存的数据也经过验证。

### 4.5 国际化
- UI 文本已**全面**迁移至 `strings.xml`（280+ 条字符串资源），覆盖：
  - 主页面消息（ScheduleScreen、DayScheduleList、WeekScheduleBoard）
  - 弹窗标题和标签（TimetableDialogs、ScheduleDialogOverlays、TimetableHero）
  - ViewModel 提示消息（通过 `getApplication<Application>().getString()` 引用）
  - ViewModel 验证错误（`validateEntry` 使用 `getApplication<Application>().getString()` 引用）
  - 通知渠道和内容文案（CourseReminderScheduler、CourseReminderReceiver）
  - Widget 显示文本（TimetableWidgetUpdater）
  - 星期名称（weekday_mon ~ weekday_sun）
  - 课程卡片文本（TimetableCards）
  - 周选择条和万年历（WeekCalendarStrip、TimetableCalendar）
  - 设置页面（SettingsScreen）
  - 背景调整弹窗（BackgroundImageAdjustDialog）
  - 无障碍内容描述（AccessibilityLabels，接受 Context 参数）
  - 下一节课快照状态文本（TimetableSnapshots，接受可选 Context 参数）
  - ICS 导出默认日历名（IcsCalendar，调用方传入 i18n 字符串）
- ICS 解析/导出使用 `ZoneId.systemDefault()`，支持设备本地时区。
- **已修复的 i18n 缺陷**：`DayScheduleList.kt` 中曾使用 `.replace("已开启通知提醒", "...")` 对另一个字符串资源的内容做文本替换，翻译后必崩。已改为独立字符串资源 `R.string.msg_notifications_not_required`。
- **仍保留中文的位置**（均为非用户可见或需较大重构的场景）：
  - `TimetableModels.kt` 中 `require()` 验证消息（开发者断言，非用户文本）
  - `TimetableModels.kt` 中 `DayOption` 枚举标签（数据模型，重构代价高）
  - `TimetableModels.kt` 中 `sampleEntries()` 测试数据
  - `TimetableSnapshots.kt` 中 context 为 null 时的回退字符串
  - `CourseReminderScheduler.CHANNEL_NAME` 常量（实际渠道名通过 `context.getString()` 设置）
  - 代码注释

### 4.6 权限声明
当前 `AndroidManifest.xml` 声明的权限：
| 权限 | 用途 |
|:---|:---|
| `POST_NOTIFICATIONS` | Android 13+ 通知权限 |
| `SCHEDULE_EXACT_ALARM` | Android 12+ 精确闹钟权限 |
| `RECEIVE_BOOT_COMPLETED` | 开机后重新调度提醒 |

> **已移除**：`USE_EXACT_ALARM`（该权限仅适用于闹钟类应用，课程表声明可能导致 Google Play 拒审）

## 5. 数据库版本历史
| 版本 | 变更 | Migration |
|:---:|:---|:---|
| v1 | 初始 Schema | — |
| v2 | 新增周循环课程字段 (recurrenceType, semesterStartDate, weekRule, customWeekList, skipWeekList) | `MIGRATION_1_2` |
| v3 | 新增查询索引 (date+startMinutes 复合索引, dayOfWeek 单字段索引) | `MIGRATION_2_3` |

## 6. 本地编译与发布指南
**常用构建命令 (PowerShell)**:
```powershell
# 运行单元测试
.\gradlew.bat testDebugUnitTest

# 构建 Debug 包
.\gradlew.bat assembleDebug

# 构建 Release 包 (需配置环境变量或 gradle.properties 中的签名信息)
.\gradlew.bat assembleRelease
```
*注：发布 Release 版本需要在宿主环境配置 `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`，否则只会生成 Unsigned APK。*

## 7. 测试覆盖
当前共 22 个测试文件，覆盖范围：
- **数据层**: ICS 解析、Repository 遗留迁移、冲突检测、周次规划、快照计算、背景图裁剪
- **调度层**: 闹钟调度计划、Receiver、增量 syncToken
- **视图层**: 屏幕状态、导入预览、编辑弹窗、无障碍标签、周视图布局算法
- **Widget**: 数据刷新逻辑

**缺失的测试类型**：Compose UI 仪器测试（图片选择、通知权限、系统文档选择器）、Room Migration 自动化测试。

## 8. 已完成的审查修复记录

### 第 1 轮修复（P1-P3 共 6 项）
| # | 修复项 | 修改文件 |
|:---:|:---|:---|
| 1 | CODE_REVIEW.md 重写，标注历史问题修复状态 | `CODE_REVIEW.md` |
| 2 | Room 索引补充 + 数据库版本升级 v3 | `TimetableModels.kt`, `AppDatabase.kt` |
| 3 | 硬编码中文字符串迁移到 strings.xml | `strings.xml`, `ScheduleScreen.kt`, `WeekScheduleBoard.kt`, `DayScheduleList.kt` |
| 4 | ScheduleScreen 弹窗逻辑拆分 | 新建 `ScheduleDialogOverlays.kt`，修改 `ScheduleScreen.kt` |
| 5 | requestCodeFor 碰撞风险加固 | `CourseReminderScheduler.kt` |
| 6 | TimetableEntry.init 验证移出 | `TimetableModels.kt`, `IcsCalendar.kt`, `TimetableRepository.kt`, `ScheduleScreen.kt` |

### 第 2 轮修复（P1-P3 共 7 项）
| # | 修复项 | 修改文件 |
|:---:|:---|:---|
| 1 | 移除 `USE_EXACT_ALARM` 权限 | `AndroidManifest.xml` |
| 2 | 修复 DayScheduleList 破损的 i18n (.replace 方式) | `DayScheduleList.kt`, `strings.xml` |
| 3 | EntryEditorDialog 改用 `TimetableEntry.create()` | `TimetableDialogs.kt` |
| 4 | DayScheduleList 残留硬编码 + 导出文件名 | `DayScheduleList.kt`, `strings.xml` |
| 5 | ScheduleViewModel 全部 postMessage i18n | `ScheduleViewModel.kt`, `strings.xml` |
| 6 | CourseReminderReceiver + Widget i18n | `CourseReminderScheduler.kt`, `CourseReminderReceiver.kt`, `TimetableWidgetUpdater.kt`, `strings.xml` |
| 7 | 星期名称 + Dialogs/Hero 字符串资源 | `WeekScheduleBoard.kt`, `WeekCalendarStrip.kt`, `TimetableCalendar.kt`, `strings.xml` |

### 第 3 轮修复 — 全面 i18n 迁移（10 项）
| # | 修复项 | 修改文件 |
|:---:|:---|:---|
| 1 | TimetableDialogs.kt 全部硬编码中文替换为 `stringResource()` | `TimetableDialogs.kt` |
| 2 | TimetableHero.kt 全部硬编码中文替换为 `stringResource()` | `TimetableHero.kt` |
| 3 | TimetableCards.kt 硬编码中文替换为 `stringResource()` | `TimetableCards.kt` |
| 4 | WeekCalendarStrip / TimetableCalendar 硬编码中文替换 | `WeekCalendarStrip.kt`, `TimetableCalendar.kt` |
| 5 | SettingsScreen.kt 硬编码中文替换 | `SettingsScreen.kt` |
| 6 | BackgroundImageAdjustDialog.kt 硬编码中文替换 | `BackgroundImageAdjustDialog.kt` |
| 7 | AccessibilityLabels.kt 添加 Context 参数支持 i18n | `AccessibilityLabels.kt`, `WeekScheduleBoard.kt`, `WeekCalendarStrip.kt`, `TimetableCalendar.kt` |
| 8 | ScheduleViewModel.validateEntry 硬编码中文替换 | `ScheduleViewModel.kt` |
| 9 | TimetableSnapshots 状态文本支持 i18n（添加可选 Context 参数） | `TimetableSnapshots.kt`, `ScheduleScreen.kt`, `TimetableWidgetUpdater.kt` |
| 10 | IcsCalendar 默认日历名 i18n | `IcsCalendar.kt`, `ScheduleViewModel.kt` |

## 9. 后续演进建议 (Next Steps)
交接后，建议接手团队在以下方向继续深耕：
1. **重构周视图**: `WeekScheduleBoard.kt` 目前承担了较多布局计算（`buildWeekEntryLayouts`、`assignEntryColumns`），建议将布局算法进一步拆分为独立的数据类和纯函数。
2. **DayScheduleList 参数封装**: 当前传递约 25 个参数，建议封装为状态容器类或引入局部 ViewModel。
3. **DayOption 枚举 i18n**: `TimetableModels.kt` 中 `DayOption` 枚举的中文标签（如"周一"）目前仍是硬编码，建议重构为资源 ID 映射或接受 Context 参数的工厂方法。
4. **无障碍适配 (Accessibility)**: 为核心交互按钮和卡片补充 Compose 的 `semantics`，优化 TalkBack 播报体验。
5. **UI 自动化测试**: 引入 Compose Test 框架，对新建课程、课表滑动、导入导出等核心路径编写端到端测试。
6. **Room Migration 测试**: 接入 `MigrationTestHelper`，确保每次 Schema 升级的迁移路径正确。
6. **Room Migration 测试**: 接入 `MigrationTestHelper`，确保每次 Schema 升级的迁移路径正确。
