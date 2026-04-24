# 转接报告（2026-04-23）

## 1. 当前发布基线
- 当前远端基线：`origin/main` = `1eb22df`（`Release v1.23`）
- 本轮功能提交：`be72f5f` `ui: dark mode fix, delete confirm, nav icons, card click, color unify, FAB/list animations, density-aware swipe, locale fix`
- 当前 release 提交：`1eb22df` `Release v1.23`
- GitHub Release：`v1.23`
- Release 地址：`https://github.com/zgyzgd1/CXYtimetable/releases/tag/v1.23`
- 本地发布 APK：`app/build/release-assets/Timetable-v1.23.apk`
- 当前版本号：
  - `APP_VERSION_NAME=1.23`
  - `APP_VERSION_CODE=24`

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
- `AndroidManifest.xml` / `ScheduleScreen.kt` / `TimetableHero.kt` / `CourseReminderScheduler.kt` 已补齐精确闹钟权限声明、状态感知与系统设置跳转入口
- `TimetableRepository.kt` 已把遗留 JSON 迁移与首次样例数据注入收敛到仓库级 `Mutex` + 数据库事务，消除启动阶段并发迁移窗口
- `CourseReminderScheduler.kt` 已改为持久化提醒签名，仅取消失效提醒、仅重下发发生变化的提醒；通知标题/地点变化也会触发重下发
- `ScheduleViewModel.kt` 已新增提醒同步 / 小组件刷新的去重 token，避免启动空数据发射、相同数据重复 collect 和相同提醒档位重复保存导致的无效重算
- `TimetableRepository.kt` 已修正遗留 JSON 存在但解码失败时的处理：不再误注入样例课表，也不会删除原始旧文件
- `ScheduleViewModel.kt` 的提醒 / 小组件去重 token 已改为 JSON 序列化，避免标题、地点包含分隔符时发生 token 碰撞
- `CourseReminderScheduler.kt` 的精确闹钟设置页跳转已改为真实可解析 fallback：优先请求精确闹钟设置页，否则回退到应用详情页
- 新增 `app/src/test/java/com/example/timetable/ui/ScheduleViewModelSyncTokenTest.kt`

### 3.5 本次补审（2026-04-24）
- 修复 `ScheduleViewModel.kt` 启动阶段副作用同步顺序：先完成 `TimetableRepository.ensureMigrated(...)`，再直接收集仓库的 Room `Flow`
- 提醒同步 / 小组件刷新不再消费 `stateIn(initialValue = emptyList())` 的占位空列表，避免启动时误取消已有提醒
- `CourseReminderScheduler.kt` 的持久化提醒签名已改为 JSON array 结构化序列化，避免标题 / 地点包含 `|` 等分隔符时发生签名碰撞
- `CourseReminderSchedulerTest.kt` 已补充分隔符碰撞回归测试，确认通知内容变化会触发重下发
- `CourseReminderScheduler.kt` 在 `buildSchedulePlan(...)` 入口一次性派生 `nowDate`，避免课程 × 提醒档位循环里重复从 `nowMillis` 转换日期
- 已重新验证 `:app:compileDebugKotlin`、提醒相关单测与完整 `testDebugUnitTest`

### 3.3 主要涉及文件
- `app/src/main/java/com/example/timetable/data/TimetableSnapshots.kt`
- `app/src/main/java/com/example/timetable/data/TimetableRepository.kt`
- `app/src/main/java/com/example/timetable/notify/CourseReminderScheduler.kt`
- `app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt`
- `app/src/main/java/com/example/timetable/ui/ScheduleViewModel.kt`
- `app/src/main/java/com/example/timetable/ui/TimetableHero.kt`
- `app/src/main/java/com/example/timetable/ui/TimetableCalendar.kt`
- `app/src/main/java/com/example/timetable/ui/WeekScheduleBoard.kt`
- `app/src/test/java/com/example/timetable/data/TimetableSnapshotsTest.kt`
- `app/src/test/java/com/example/timetable/notify/CourseReminderSchedulerTest.kt`
- `app/src/test/java/com/example/timetable/ui/ScheduleViewModelSyncTokenTest.kt`
- `app/src/test/java/com/example/timetable/ui/ScheduleScreenTest.kt`

## 4. 本轮审查与发布结果
### 4.1 漏洞审查结论
- 补审发现 1 个 P1 风险并已修复：`ScheduleViewModel` 启动时不再用占位空课表触发提醒同步 / 小组件刷新
- 当前未发现新的发布阻塞项
- 已确认仍然有效的安全收口：
  - `release` 不再回退到 debug 签名
  - `android:allowBackup="false"`
  - ICS 导入存在 1 MB 大小上限
  - 通知和小组件 `PendingIntent` 使用不可变 flag

### 4.2 发布执行结果
- 已推送 `main` 到 GitHub（截至 `1eb22df` / `v1.23` 发布提交）
- 已推送标签 `v1.23`
- 已生成本地发布 APK：`app/build/release-assets/Timetable-v1.23.apk`
- 已补齐本地归档仓库缺口：`Timetable-v1.20.apk`、`Timetable-v1.21.apk`、`Timetable-v1.23.apk`
- 归档仓库最新本地提交：`22b2ac9` `Archive timetable v1.20-v1.23 APKs`
- 发布过程中再次出现过一次 `git push origin main` 的 TLS 握手失败
- 该问题已通过后续手动补推解决，不影响当前 release 状态
- 当前交接文档已同步到 `v1.23` 发布与归档补齐后的真实状态，待本次文档与补审修复提交推送

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
1. **闹铃机制失效**（Android 14+ 精准闹钟权限缺失）- 已部分修复：Manifest 权限、UI 状态提示、系统授权跳转与授权返回后的强制重同步已落地；仍缺更强的 fallback 策略
2. **本地存储竞态条件**（多协程并行读写文件）- 已收口：主存储已是 Room，遗留 JSON 迁移与种子数据注入已补仓库级串行化与事务保护；遗留文件解码失败时不再误删原始数据或误注入样例
3. **系统级耗电与 CPU 开销**（syncReminders 暴力全量校验）- 已部分修复：提醒调度改为签名增量下发，ViewModel 层新增提醒/小组件去重 token，且 ViewModel token 与提醒持久化签名均已改为结构化序列化避免碰撞；补审已修复启动占位空课表误触发同步，并把调度计划中的 `nowDate` 派生收敛到每次计划构建一次；仍保留“全量扫描课程求最近提醒”的计算路径，可作为下一阶段继续优化
4. **硬编码与时区灾难**（中文硬编码、北京时区写死）- 未修复，标记为下阶段改进
5. **Compose 巨型类问题**（ScheduleScreen ~1000 行）- 未修复，标记为下阶段改进

**结论**：本轮新增审查发现的 5 个具体问题已完成修复：旧文件迁移失败误删数据、同步 token 分隔符碰撞、精确闹钟设置页伪 fallback、启动占位空课表误触发提醒同步、提醒持久化签名分隔符碰撞。本次继续收口了最近提醒候选路径中的重复日期派生，但尚未改变全量扫描模型。剩余高优先事项主要是“最近提醒候选仍需全量扫描”的计算路径以及更强的提醒 fallback。

## 5. 验证结果
- 已执行：
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.ui.ScheduleScreenTest --tests com.example.timetable.data.TimetableSnapshotsTest --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon assembleDebug --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon assembleRelease --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.data.TimetableSnapshotsTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --tests com.example.timetable.widget.TimetableWidgetUpdaterTest --tests com.example.timetable.ui.ScheduleScreenTest --rerun-tasks`
  - `.\gradlew.bat :app:compileDebugKotlin`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.data.TimetableRepositoryTest --tests com.example.timetable.ui.ScheduleScreenTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.ui.ScheduleViewModelSyncTokenTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.data.TimetableRepositoryTest --tests com.example.timetable.ui.ScheduleViewModelSyncTokenTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon :app:compileDebugKotlin --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.ui.ScheduleViewModelSyncTokenTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --rerun-tasks`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest`
  - `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --rerun-tasks`
  - `git diff --check`
  - `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\publish-release.ps1`
  - VS Code 任务 `assembleDebug`
  - VS Code 任务 `envDoctor`
- 结果：
  - 相关单测通过
  - `assembleRelease` 构建通过
  - `v1.23` 本地发布 APK 已生成：`app/build/release-assets/Timetable-v1.23.apk`
  - `apk-archive-repo` 已补齐 `Timetable-v1.20.apk`、`Timetable-v1.21.apk`、`Timetable-v1.23.apk`
  - `assembleDebug` 构建通过
  - `:app:compileDebugKotlin` 通过
  - `envDoctor` 在脚本修正后通过
  - `git diff --check` 通过
  - 仅保留既有的 Android SDK XML warning

## 6. 当前计划状态
- 阶段 1：完成
- 阶段 2：基本完成；仍缺“全局学期配置 / 当前周自动计算”的统一入口
- 阶段 3：完成并已发布
- 阶段 4：
  - 任务 1 `关键回归测试`：已补强
  - 任务 2 `可访问性补强`：关键路径已完成一轮
  - 任务 3 `性能和边界治理`：继续推进中；目前已完成范围索引复用、UI 侧重复入口收口、小组件刷新串行化、停用逻辑物理删除、精确提醒权限链路、提醒增量调度、迁移竞态收口、提醒/小组件同步去重和边界测试补齐

## 7. 当前工作区状态
- 当前分支：`main`
- 当前远端基线：`1eb22df` / `v1.23`
- 当前主仓库本地提交范围：`TRANSFER_REPORT.md`、`app/src/main/java/com/example/timetable/ui/ScheduleViewModel.kt`、`app/src/main/java/com/example/timetable/notify/CourseReminderScheduler.kt`、`app/src/test/java/com/example/timetable/notify/CourseReminderSchedulerTest.kt`
- 当前本地补审修复：`ScheduleViewModel.kt` 启动后先完成迁移，再收集真实 Room 数据流同步提醒和小组件
- 当前本地提醒补强：`CourseReminderScheduler.kt` 持久化签名改为结构化 JSON array，避免分隔符碰撞
- 当前本地提醒调度优化：`CourseReminderScheduler.kt` 在每次计划构建中只派生一次 `nowDate`，减少大课表同步时的重复日期转换
- 当前归档仓库本地提交：`22b2ac9` `Archive timetable v1.20-v1.23 APKs`，待推送到归档仓库远端

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
   - 当前状态：已补权限声明、设置页跳转和授权返回重同步；提醒不再静默缺口
   - 修复方向：
     - 补更强的 fallback 策略，使用 `WorkManager` 配合 ExactAlarmDispatcher
     - 覆盖权限被系统回收 / 关闭后的自动降级与提示路径
     - 增加相关 UI / 调度测试

2. **本地存储竞态条件 (多协程并行读写导致数据损坏)**
   - 当前状态：主存储已是 Room；遗留 JSON 迁移窗口已补串行化与事务保护
   - 修复方向：
     - 如需进一步加固，可补仓库初始化并发单测
     - 评估是否彻底删除遗留 JSON 兼容代码，减少维护面

**优先度 P1（性能与体验）**：
3. **系统级耗电与 CPU 开销 (syncReminders 暴力全量校验)**
   - 当前状态：已完成签名增量下发与 ViewModel 层去重，显著减少重复 AlarmManager IPC 与重复刷新
   - 修复方向：
     - 继续把“最近提醒候选”从全量扫描收敛到更轻的增量入口
     - 评估是否需要切换到 `WorkManager` 处理更长周期的兜底调度
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
