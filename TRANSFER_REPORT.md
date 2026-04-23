# 转接报告（2026-04-23）

## 1. 当前发布基线
- 当前远端基线：`origin/main` = `c8849c3`（`Release v1.21`）
- 本轮功能提交：`45854c8` `perf: optimize snapshot range caching and edge tests`
- 当前 release 提交：`2088d7a` `Release v1.21`
- GitHub Release：`v1.21`
- Release 地址：`https://github.com/zgyzgd1/CXYtimetable/releases/tag/v1.21`
- 本地发布 APK：`app/build/release-assets/Timetable-v1.21.apk`
- 当前版本号：
  - `APP_VERSION_NAME=1.21`
  - `APP_VERSION_CODE=22`

## 2. 已发布内容范围
- 多档提前提醒
- 今日课表 / 下一节课桌面小组件
- 通知、小组件点击跳转到对应日期
- 关键回归测试补强
- 主要可访问性语义补强
- 发布链路、签名和导入大小限制加固
- 日期范围索引复用与相关性能收口

## 3. 阶段 4 当前进度
### 3.1 已完成的本地能力
- 新增 `entriesByDateInRange(...)`，把按可见日期范围预索引的能力下沉到数据层
- `TimetableCalendar.kt` 改为复用可见月份范围索引，不再为每个日期重复全量扫描
- `WeekScheduleBoard.kt` 改为消费预计算好的 `entriesByDay`
- `ScheduleScreen.kt` 统一预计算当前可见日期范围的课程索引，日视图、周视图和快速新增模板共用同一份结果
- `ScheduleScreen.kt` 已切到数据层的 `findNextCourseSnapshot(...)` / `NextCourseSnapshot`
- `ScheduleScreen.kt` 里停用的旧快照 / 单日筛选实现已物理删除
- `ScheduleScreen.kt` 的 `filteredEntries` 已收窄为日视图所需数据，避免周视图时重复整周 flatten
- `WeekScheduleBoard.kt` 的 `weekEntries` 增加了 `remember(...)`，避免头部统计重复拼整周列表
- 删除 `ScheduleViewModel.entriesByDate` 这条未被使用、且只按原始 `entry.date` 聚合的旧状态流
- `ScheduleViewModel` 里的小组件刷新改成与提醒同步相同的串行调度，减少连续数据变更时的重复刷新

### 3.2 测试补强
- `ScheduleScreenTest.kt` 已显式引用数据层快照实现，避免 UI 包里的重复逻辑继续成为事实入口
- 新增 `app/src/test/java/com/example/timetable/data/TimetableSnapshotsTest.kt`
- 当前已覆盖：
  - 范围索引对循环课的映射
  - 同日课程排序
  - 反向日期范围返回空结果
  - 自定义周 + 跳周在可见窗口内的命中行为

### 3.4 本次续做（2026-04-23）
- `findNextCourseSnapshot(...)` 的未来候选选择由全量排序改为线性最小值选择，降低大课表下的计算开销
- 新增 `DateRangeEntriesCache`，并在 `ScheduleScreen.kt` 与 `TimetableCalendar.kt` 间共享日期窗口索引入口，减少跨组件重复派生
- `ScheduleScreen.kt` 的下一节课快照增加 `remember(...)` 约束，降低无关重组时的重复计算
- `TimetableSnapshotsTest.kt` 补充：大范围循环课窗口命中测试、日期范围缓存复用/淘汰测试
- `CourseReminderSchedulerTest.kt` 补充：1500 条课程场景下“仅保留最早触发提醒”测试
- `TimetableWidgetUpdaterTest.kt` 补充：进行中课程的小组件状态测试
- `scripts/env-doctor.ps1` 改为按 `gradle-wrapper.properties` 自动识别 Gradle 版本与分发地址，修复版本写死导致的误报
- `COMPILE.md` 已同步更新为当前 wrapper 版本说明（Gradle 9.3.1）

### 3.3 主要涉及文件
- `app/src/main/java/com/example/timetable/data/TimetableSnapshots.kt`
- `app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt`
- `app/src/main/java/com/example/timetable/ui/ScheduleViewModel.kt`
- `app/src/main/java/com/example/timetable/ui/TimetableCalendar.kt`
- `app/src/main/java/com/example/timetable/ui/WeekScheduleBoard.kt`
- `app/src/test/java/com/example/timetable/data/TimetableSnapshotsTest.kt`
- `app/src/test/java/com/example/timetable/ui/ScheduleScreenTest.kt`

## 4. 本轮审查与发布结果
### 4.1 漏洞审查结论
- 本轮未发现新的高 / 中风险阻塞项
- 已确认仍然有效的安全收口：
  - `release` 不再回退到 debug 签名
  - `android:allowBackup="false"`
  - ICS 导入存在 1 MB 大小上限
  - 通知和小组件 `PendingIntent` 使用不可变 flag

### 4.2 发布执行结果
- 已推送 `main` 到 GitHub
- 已推送标签 `v1.21`
- 已上传 `Timetable-v1.21.apk` 到 GitHub Release
- 发布过程中出现过一次 `git push origin main` 的 TLS 握手失败
- 该问题已通过后续重试补推解决，不影响当前 release 状态
- 已同步交接文档并推送最新记录

### 4.3 代码审查发现
#### 本轮优化代码安全性检查
- **DateRangeEntriesCache 线程安全性** ✅
  - 缓存实例在 `ScheduleScreen` 的单个 Composable 作用域内创建和使用
  - 缓存值是不可变 `Map<LocalDate, List<TimetableEntry>>` 对象
  - 虽然 LinkedHashMap 本身非线程安全，但使用场景保证了单线程访问（仅在 Composable 重组时调用）
  - 建议：在代码中添加注释说明单线程使用前提
  
- **快照快速查找性能优化** ✅
  - 改用 `.minWithOrNull(compareBy(...))` 替代 `.sortedWith().firstOrNull()`
  - 比较器完整性验证：覆盖日期、开始时间、结束时间、标题，保证全序关系
  - 大范围循环课（60 周）与大数据量（1500 课程）测试已补齐
  
- **UI 层 remember 约束** ✅
  - `nextCourseSnapshot` 依赖项齐全：`remember(entries, today, nowMinutes)`
  - `dateRangeEntriesCache` 依赖项正确：`remember(entries)`
  - entries 变化时自动重新计算缓存，避免陈旧数据
  
- **缓存内存管理** ✅
  - LRU 淘汰机制限制在 maxRanges = 8，内存占用有限
  - 缓存值不直接修改，只做只读引用

#### 已知风险的现状评估
根据 CODE_REVIEW.md 列举的 5 大风险，本轮审查确认：
1. **闹铃机制失效**（Android 14+ 精准闹钟权限缺失）- 未修复，标记为下阶段改进
2. **本地存储竞态条件**（多协程并行读写文件）- 未修复，标记为下阶段改进
3. **系统级耗电与 CPU 开销**（syncReminders 暴力全量校验）- 未修复，但本轮线性选择避免排序，略微降低 CPU 开销
4. **硬编码与时区灾难**（中文硬编码、北京时区写死）- 未修复，标记为下阶段改进
5. **Compose 巨型类问题**（ScheduleScreen ~1000 行）- 未修复，标记为下阶段改进

**结论**：本轮未发现新的高/中风险问题，优化代码安全合规。已知的 5 大架构风险可作为后续改进清单。

## 5. 验证结果
- 已执行：
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.ui.ScheduleScreenTest --tests com.example.timetable.data.TimetableSnapshotsTest --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon assembleDebug --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon assembleRelease --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.data.TimetableSnapshotsTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --tests com.example.timetable.widget.TimetableWidgetUpdaterTest --tests com.example.timetable.ui.ScheduleScreenTest --rerun-tasks`
  - VS Code 任务 `assembleDebug`
  - VS Code 任务 `envDoctor`
  - `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\publish-release.ps1`
- 结果：
  - 相关单测通过
  - `assembleRelease` 构建通过
  - `v1.21` Release 与 APK 上传通过
  - `assembleDebug` 构建通过
  - `envDoctor` 在脚本修正后通过
  - 仅保留既有的 Android SDK XML warning

## 6. 当前计划状态
- 阶段 1：完成
- 阶段 2：基本完成；仍缺“全局学期配置 / 当前周自动计算”的统一入口
- 阶段 3：完成并已发布
- 阶段 4：
  - 任务 1 `关键回归测试`：已补强
  - 任务 2 `可访问性补强`：关键路径已完成一轮
  - 任务 3 `性能和边界治理`：继续推进中；目前已完成范围索引复用、UI 侧重复入口收口、小组件刷新串行化、停用逻辑物理删除和边界测试补齐

## 7. 当前工作区状态
- 当前分支：`main`
- 当前工作区：clean
- 当前基线：`c8849c3` / `v1.21`

## 8. 建议下一步

### 8.1 阶段 4 继续推进
- 继续阶段 4 任务 3"性能和边界治理"
- 近期方向：
  - 基于真实大课表数据做一次 Compose 重组与耗时采样，确认缓存收益与剩余慢路径
  - 继续补提醒/小组件"异常系统状态"路径（权限变化、系统时间跳变、跨时区）
  - 评估是否把 `DateRangeEntriesCache` 继续下沉到 ViewModel 层，统一为可观察窗口数据源

### 8.2 已知高风险的改进清单（优先度排序）

**优先度 P0（功能正确性）**：
1. **闹铃机制失效 (Android 14+ 精准闹钟权限缺失)**
   - 影响范围：提醒功能核心，现代 Android 系统可能无法触发提醒
   - 修复方向：
     - 在 `AndroidManifest.xml` 添加 `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>`
     - 动态申请权限（Android 12+）
     - 改进 fallback 策略，使用 `WorkManager` 配合 ExactAlarmDispatcher

2. **本地存储竞态条件 (多协程并行读写导致数据损坏)**
   - 影响范围：用户课表数据可能损坏清空，直接影响体验
   - 修复方向：
     - 引入 `Mutex` 互斥锁确保文件读写单线程化
     - 或迁移到官方推荐的 `Room Database` / `DataStore`
     - 补完整的竞态条件单测

**优先度 P1（性能与体验）**：
3. **系统级耗电与 CPU 开销 (syncReminders 暴力全量校验)**
   - 影响范围：续航时间、系统流畅度，高定制 ROM（ColorOS/MIUI）易被强杀
   - 修复方向：
     - 改进闹钟增量更新机制，持久化每个 Entry 的 RequestCode
     - 或切换到 `WorkManager` 处理 PeriodicWork
     - 进行电池消耗采样验证

**优先度 P2（可维护性与国际化）**：
4. **硬编码与时区灾难 (中文硬编码与北京时区写死)**
   - 影响范围：本地化扩展困难，跨时区场景容易出错
   - 修复方向：
     - 提取所有中文字符到 `strings.xml`（多语言支持）
     - 时区改为从 system 读取或用户配置，避免写死 `Asia/Shanghai`
     - 新增时区单测

5. **Compose 巨型类 (ScheduleScreen ~1000 行)**
   - 影响范围：代码可维护性、重组性能分析困难
   - 修复方向：
     - 拆分 Hero 组件、长列表、弹窗等独立 Composable 文件
     - 按职责划分为 UI 层多个模块
     - 预计可降低每次重组的耗时 5-15%

