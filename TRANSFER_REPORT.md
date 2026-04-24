# 交接文档（2026-04-24）

## 1. 当前发布状态
- 主仓库：`https://github.com/zgyzgd1/CXYtimetable`
- 当前最新发布：`v1.26`
- 当前版本号：
  - `APP_VERSION_NAME=1.26`
  - `APP_VERSION_CODE=27`
- 本轮 APK 名称：`app-release.apk`
- 本轮 Release 地址：`https://github.com/zgyzgd1/CXYtimetable/releases/tag/v1.26`
- 当前主仓库最新发布提交：`4610718` `Release v1.26`

## 2. 计划约束
- 不做云同步。
- 不引入账号系统。
- 优先优化本地体验、提醒稳定性、发布链路和可回归验证。

## 3. 阶段状态
### 阶段 1-3：已全部完成
状态：已完成。

### 阶段 4：稳定性与发布（本轮交付）
状态：已完成。

本轮核心交付：
- **UI 架构重构 (P2-3)**：
    - 成功将 `ScheduleScreen.kt` 解耦，将日视图核心逻辑提取至独立的 `DayScheduleList.kt` 组件。
    - 显著降低了 `ScheduleScreen` 的代码体积（约 300 行），实现了 UI 逻辑的关注点分离。
- **安全加固与合规性审查**：
    - 修复了 Android 13+ 通知权限的潜在崩溃漏洞。
    - 修复了 Android 12+ 数据备份合规性问题，新增 `data_extraction_rules.xml`。
    - 补充了 `AndroidManifest.xml` 的 `<queries>` 声明，解决了 Android 11+ 包可见性导致的权限跳转失败问题。
- **发布自动化与归档**：
    - 代码已推送到 GitHub 主仓库。
    - 已打出 `v1.26` Tag。
    - `assembleRelease` 已成功构建，APK 位于 `app/build/outputs/apk/release/app-release.apk`。

## 4. 交接结论
- 本项目已完成所有 P0-P2 阶段既定目标。
- 代码结构已由单体大文件转向模块化组件架构。
- 安全性、稳定性和本地化体验已达到发布标准。
- 建议接手者后续关注：进一步拆分周视图 (`WeekScheduleBoard`) 逻辑，以及引入更完善的多语言支持。
