# 拾光课程表交接文档（2026-04-24）

## 1. 当前发布目标
- 主仓库：`https://github.com/zgyzgd1/CXYtimetable`
- 归档仓库：`https://github.com/zgyzgd1/apk-`
- 本轮发布目标：`v1.24`
- 版本号目标：
  - `APP_VERSION_NAME=1.24`
  - `APP_VERSION_CODE=25`
- 本轮 APK 名称：`Timetable-v1.24.apk`
- 本轮 Release 地址：`https://github.com/zgyzgd1/CXYtimetable/releases/tag/v1.24`
- 本轮归档路径：`apk-archive-repo/releases/Timetable-v1.24.apk`

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
- 遗留 JSON 迁移已收敛到仓库级 `Mutex` + Room 事务，降低启动竞态风险。
- 遗留 JSON 解码失败时不再误注入样例课表，也不会删除原始旧文件。

仍待继续：
- 全局学期配置 / 当前周自动计算还缺统一入口。
- 冲突检测仍可继续补强为保存前的统一校验链路。

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

### 阶段 4：稳定性与发布
状态：持续推进中，本轮完成发布链路收口。

已落地能力：
- 关键回归测试补强：数据层快照、提醒调度、ViewModel 同步 token、小组件状态等路径已有覆盖。
- 性能收口：日期范围索引复用、快照查找由全量排序改为线性最小值选择、UI 侧重复派生入口减少。
- 发布链路：`testDebugUnitTest`、`assembleRelease`、GitHub Release、APK 归档流程已可重复执行。
- 归档仓库已补齐历史缺口：`Timetable-v1.20.apk`、`Timetable-v1.21.apk`、`Timetable-v1.23.apk`。

本轮待执行并交付：
- 推送主仓库 `main` 到 GitHub。
- 创建并推送 `v1.24` tag。
- 上传 `Timetable-v1.24.apk` 到 GitHub Release。
- 将 `Timetable-v1.24.apk` 复制到归档仓库并推送归档仓库。

## 4. 本轮提交范围
主仓库本轮相对 `v1.23` 的主要提交：
- `1b7ee28` `Fix startup reminder sync and update transfer report`
- `9c0e05c` `Structure reminder schedule signatures`
- `ab8956a` `Reduce reminder schedule date recomputation`
- 本文件重写提交：更新 2026-04-24 交接状态。
- `Release v1.24`：版本号提升、发布 APK 构建与 GitHub Release。

归档仓库本轮提交：
- 补齐 `Timetable-v1.20.apk`、`Timetable-v1.21.apk`、`Timetable-v1.23.apk`。
- 新增 `Timetable-v1.24.apk`。
- 更新归档仓库 `README.md` 的版本清单。

## 5. 关键验证
已完成或本轮发布脚本会执行的验证：
- `.\gradlew.bat --offline --no-daemon :app:compileDebugKotlin --rerun-tasks`
- `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.ui.ScheduleViewModelSyncTokenTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --rerun-tasks`
- `.\gradlew.bat --offline --no-daemon testDebugUnitTest --tests com.example.timetable.notify.CourseReminderSchedulerTest --rerun-tasks`
- `.\gradlew.bat --offline --no-daemon testDebugUnitTest`
- `.\gradlew.bat --offline --no-daemon assembleRelease`
- `git diff --check`
- `scripts/push-github.ps1 -Version 1.24` 发布流：提交、推送、打 tag、上传 Release APK、同步归档仓库。

已知环境提示：
- 仍会出现既有 Android SDK XML version warning，不影响当前构建和测试结果。

## 6. 当前风险与后续建议
P0 / 功能正确性：
- 精确闹钟权限已补声明和设置跳转，但 Android 14+ 权限被关闭或系统回收后的兜底策略仍可继续加强。
- 建议继续评估 `WorkManager` 作为非精确兜底链路，但不要替代当前精确提醒路径。

P1 / 性能与耗电：
- 提醒同步已减少重复 AlarmManager IPC、重复小组件刷新和重复日期派生。
- 最近提醒候选仍会扫描当前课表集合；后续可评估增量索引或按日期窗口缩小候选范围。

P2 / 可维护性：
- 中文字符串仍有硬编码，后续可逐步迁移到 `strings.xml`。
- `ScheduleScreen` 仍偏大，后续可按弹窗、列表、Hero、操作栏继续拆分。
- 全局学期配置 / 当前周自动计算建议作为下一轮阶段 2 收尾项。

## 7. 交接结论
- 本轮不是 UI 计划执行轮，未处理 `UI_OPTIMIZATION_PLAN.md`。
- `v1.24` 的重点是 v1.23 后的提醒稳定性补审、结构化签名、启动同步顺序修复和发布归档链路闭环。
- 下一位接手者可从两个方向继续：一是补更强的提醒 fallback，二是把全局学期配置和当前周自动计算做成统一入口。
