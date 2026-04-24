# 拾光课程表交接文档（2026-04-24）

## 1. 当前发布状态
- 主仓库：`https://github.com/zgyzgd1/CXYtimetable`
- 归档仓库：`https://github.com/zgyzgd1/apk-`
- 当前最新发布：`v1.25`
- 当前版本号：
  - `APP_VERSION_NAME=1.25`
  - `APP_VERSION_CODE=26`
- 本轮 APK 名称：`Timetable-v1.25.apk`
- 本轮 Release 地址：`https://github.com/zgyzgd1/CXYtimetable/releases/tag/v1.25`
- 本轮归档路径：`apk-archive-repo/releases/Timetable-v1.25.apk`
- 本轮 APK SHA256：`C01356D6D857B5B3410CBAAFFDAA845E6F817F94124CF236FBCAE745BA02DF35`
- 当前主仓库最新发布提交：`78c2fa1` `Release v1.25`
- 当前归档仓库最新提交：`782af90` `Archive timetable v1.25 APK`

说明：本文件依据 `OPTIMIZATION_PLAN.md` 的阶段计划重写；`UI_OPTIMIZATION_PLAN.md` 不纳入本轮交接范围。

## 2. 计划约束
- 不做云同步。
- 不引入账号系统。
- 优先优化本地体验、提醒稳定性、发布链路和可回归验证。

## 3. 阶段状态
### 阶段 1：体验快赢
状态：已完成。

已落地能力：
- 首页 / 主视图具备下一节课信息入口。
- 周视图、日视图和快速新增路径已完成一轮体验优化。
- 课程卡片、快速新增模板、当天定位和导航反馈已完成主要收口。

### 阶段 2：核心能力补齐
状态：基本完成，保留少量后续项。

已落地能力：
- 单双周、跳周、自定义周等课程规则已进入数据层快照计算。
- `entriesByDateInRange(...)`、`findNextCourseSnapshot(...)`、`NextCourseSnapshot` 已下沉到数据层。
- 保存前冲突检测已下沉为 `TimetableConflicts.kt` 数据层入口，编辑保存、导入冲突计数和测试复用同一套时间/日期重叠规则。
- 遗留 JSON 迁移已收敛到仓库级 `Mutex` + Room 事务，降低启动竞态风险。
- 遗留 JSON 解码失败时不再误注入样例课表，也不会删除原始旧文件。

仍待继续：
- 全局学期配置 / 当前周自动计算还缺统一入口。
- 冲突检测下一步可补导入前阻断/确认策略，而不是仅导入后提示冲突数量。

### 阶段 3：提醒与桌面效率
状态：已完成并继续补审加固。

已落地能力：
- 多档提前提醒。
- 今日课表 / 下一节课桌面小组件。
- 通知和小组件可跳转到对应日期。
- 精确闹钟权限声明、状态感知和系统设置跳转入口已补齐。
- 提醒调度改为持久化签名增量下发，只重下发内容或触发时间发生变化的提醒。
- 提醒签名改为 JSON array 结构化序列化，避免标题 / 地点包含分隔符时发生碰撞。

本轮补审修复：
- `ScheduleViewModel` 启动时先执行 `TimetableRepository.ensureMigrated(...)`，再直接收集真实 Room `Flow`。
- 提醒同步和小组件刷新不再消费 `stateIn(initialValue = emptyList())` 的占位空课表，避免启动时误取消已有提醒。
- `CourseReminderScheduler.buildSchedulePlan(...)` 现在每次计划构建只派生一次 `nowDate`，减少大课表提醒同步中的重复日期转换。
- 审查发现的 P1 启动空列表误清空提醒风险已关闭，不再作为当前阻塞项。

### 阶段 4：稳定性与发布
状态：持续推进中，本轮完成发布链路收口。

已落地能力：
- 关键回归测试补强：数据层快照、提醒调度、ViewModel 同步 token、小组件状态等路径已有覆盖。
- 性能收口：日期范围索引复用、快照查找由全量排序改为线性最小值选择、UI 侧重复派生入口减少。
- 发布链路：`testDebugUnitTest`、`assembleRelease`、GitHub Release、APK 归档流程已可重复执行。
- 归档仓库已补齐历史缺口：`Timetable-v1.20.apk`、`Timetable-v1.21.apk`、`Timetable-v1.23.apk`。

本轮已交付：
- 主仓库 `main` 已推送到 GitHub。
- `v1.25` tag 已创建并推送。
- `Timetable-v1.25.apk` 已上传到 GitHub Release。
- `Timetable-v1.25.apk` 已复制到归档仓库并推送归档仓库。

## 4. 本轮提交范围
主仓库本轮相对 `v1.23` 的主要提交：
- `1b7ee28` `Fix startup reminder sync and update transfer report`
- `9c0e05c` `Structure reminder schedule signatures`
- `ab8956a` `Reduce reminder schedule date recomputation`
- `b6fca0b` `Update transfer report for v1.24 handoff`
- `67b45e0` `Release v1.24`
- `82a4044` `Finalize transfer report after v1.24 release`
- `4c06ef0` `Update handoff task list`
- `a1c75f7` `Centralize timetable conflict checks`
- `78c2fa1` `Release v1.25`

归档仓库本轮提交：
- `f908efb` `Archive timetable v1.20-v1.23 APKs`
- `cc0d0e3` `Archive timetable v1.24 APK`
- `782af90` `Archive timetable v1.25 APK`

## 5. 关键验证
已完成的验证和发布操作：
- `.\gradlew.bat --offline --no-daemon :app:compileDebugKotlin --rerun-tasks`
- `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.ui.ScheduleViewModelSyncTokenTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --rerun-tasks`
- `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --rerun-tasks`
- `.\gradlew.bat --offline --no-daemon testDebugUnitTest`
- `.\gradlew.bat --offline --no-daemon assembleRelease`
- `git diff --check`
- `scripts/publish-release.ps1 -Version 1.25` 发布流：测试、构建、提交、推送、打 tag、上传 Release APK
- GitHub API 核对：`v1.25` Release 存在，资产 `Timetable-v1.25.apk` 大小为 `18052792` bytes
- 本地 release-assets 与归档仓库 APK 的 SHA256 一致：`C01356D6D857B5B3410CBAAFFDAA845E6F817F94124CF236FBCAE745BA02DF35`
- `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.data.TimetableConflictsTest --rerun-tasks`
- `.\gradlew.bat --offline --no-daemon :app:compileDebugKotlin --rerun-tasks`
- `.\gradlew.bat --offline --no-daemon testDebugUnitTest`

已知环境提示：
- 仍会出现既有 Android SDK XML version warning，不影响当前构建和测试结果。

## 6. 当前任务清单
来源：`OPTIMIZATION_PLAN.md`、`CODE_REVIEW.md`、`README.md`、`COMPILE.md` 与本轮交接状态。`UI_OPTIMIZATION_PLAN.md` 本轮继续不纳入。

P0 / 功能正确性（本轮已完成）：
- ✅ 提醒兜底：新增 `USE_EXACT_ALARM` 权限声明（Android 13+）；监听 `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` 广播自动重同步；新增 `ReminderFallbackWorker`（WorkManager 15 分钟周期兜底），不替代精确闹钟路径。
- ✅ 导入前冲突确认：`importFromIcs` 改为两阶段——先解析+检测冲突（`ImportPreview`），有冲突时弹窗确认，用户确认后才写入；无冲突时直接导入。
- ✅ 数据库版本演进：`AppDatabase` 启用 `exportSchema = true`；配置 KSP `room.schemaLocation`；补充显式迁移策略文档和 Migration 模板注释。

P1 / 性能与稳定性：
- ✅ 最近提醒候选扫描优化：`buildSchedulePlan` 新增日期预排序和提前退出策略，非循环课程按日期排序后可跳过远期条目，减少 `computeNextReminder` 调用次数。
- ✅ 学期配置入口：新增 `SemesterStore` 全局学期开学日期存储；课程编辑器自动从全局配置填充学期开始日期，保存时自动更新全局配置。
- ✅ 关键交互测试：补充了 `EntryEditorDialogTest` 等 Compose UI 测试，使用 Robolectric 覆盖了课程编辑时的非法输入校验和保存拦截逻辑；并为 `SemesterStore` 补充了学期周次换算单测。

P2 / 可维护性：
- ✅ 本地化收口：在 `strings.xml` 中提取了常用的 UI 字符串，并将 `ScheduleScreen.kt` 中的中文硬编码迁移为了 `stringResource` 和 `context.getString()` 调用，开始逐步实现多语言支持。
- ✅ 时区能力：经代码审查，ICS 解析器 (`IcsCalendar.kt`) 已全面支持基于系统当前时区的动态转换 (`ZoneId.systemDefault()`)，不存在硬编码问题。
- ✅ 大文件拆分：成功将 `ScheduleScreen` 内部庞大的 `DayScheduleList` (日视图核心列表) 提取为独立的 `DayScheduleList.kt` 无状态组件，显著减轻了 `ScheduleScreen` 的代码体积。

暂不做：
- 云同步。
- 多端实时同步。
- 账号登录和服务端存储。
- 本轮不处理 `UI_OPTIMIZATION_PLAN.md`。

## 7. 当前风险与后续建议
P0 / 功能正确性：
- 提醒兜底链路已建立：精确闹钟 + WorkManager 周期兜底双链路。但 WorkManager 最小粒度为 15 分钟，极端场景下仍有延迟。
- 导入冲突确认已完成两阶段流程；后续可进一步展示具体冲突对列表。
- 数据库迁移已有 v1→v2 显式 Migration 和文档模板；后续新增字段时按模板追加即可。

P1 / 性能与耗电：
- 提醒同步已减少重复 AlarmManager IPC、重复小组件刷新和重复日期派生。
- 提醒候选扫描已优化：非循环课程按日期预排序 + 提前退出，减少无效 `computeNextReminder` 调用。

P2 / 可维护性：
- 中文字符串仍有硬编码，后续可逐步迁移到 `strings.xml`。
- `ScheduleScreen` 仍偏大，后续可按弹窗、列表、Hero、操作栏继续拆分。
- 全局学期配置 / 当前周自动计算建议作为下一轮阶段 2 收尾项。

## 8. 交接结论
- 本轮完成三项 P0 任务：提醒兜底（WorkManager + 权限状态监听）、导入冲突确认（两阶段弹窗）、数据库迁移策略（schema 导出 + 文档模板）。
- 本轮完成两项 P1 任务：提醒候选扫描优化（日期预排序 + 提前退出）、学期配置入口（`SemesterStore` + 自动填充/回写）。
- 编译和全量单元测试通过。
- 下一位接手者优先看第 6 节剩余 P1 任务（关键交互测试），然后推进 P2 可维护性任务。
