# 课程表助手

一个基于 Kotlin、Jetpack Compose 和 Material 3 的 Android 原生课表应用，强调本地离线、轻量界面和可维护的代码结构。

## 当前能力

- 课程的新增、编辑、删除与按日期查看
- `.ics` 课表导入与导出
- 课前提醒与接力式闹钟调度
- 本地 Room 持久化与旧 JSON 数据迁移
- 自定义背景图导入，并在应用重启后保持生效

## 近期变化

### `4cd856f` Add customizable background image support

- 新增自定义背景图导入入口
- 支持持久化保存图片 URI 和系统读取权限
- 主界面增加背景图渲染层，并叠加遮罩保证内容可读
- 补充背景图存储和缩放逻辑的单元测试

### `3af1263` Fix reminder scheduling and legacy migration edge cases

- 修复“没有未来课程时旧提醒不会被清除”的问题
- 后台提醒重建前增加旧 JSON 数据迁移兜底
- 去掉 Room 的破坏性迁移默认配置，避免后续版本静默清库
- README 与实际代码状态重新对齐

### `2873eb1` Upgrade Android build stack and clean warnings

- 升级 Android 构建栈与依赖版本
- 统一到较新的 Compose、Lifecycle、Room 和 SDK 配置
- 清理一部分构建与代码层面的告警

## 目前好的地方

### 1. 架构比较清晰

- UI、ViewModel、Repository、通知调度、ICS 解析基本分层明确
- `ScheduleViewModel` 用 `StateFlow` 驱动界面，数据流方向简单
- Room 已经替代旧 JSON 成为主数据源，读取和写入边界更稳定

### 2. 功能闭环完整

- 课表编辑、按日浏览、导入导出、提醒都已经串起来
- 自定义背景图这样的偏个性化功能也已经接入持久化，不只是临时 UI 状态
- 提醒逻辑采用“只保留最近一批提醒”的接力式策略，更符合 Android 后台限制

### 3. 工程可继续演进

- 关键逻辑已经有一批单元测试覆盖，包括 ICS、Repository、提醒计划和背景图辅助逻辑
- 代码基本没有明显的大型 God Object，继续拆分和扩展的成本可控
- 近期修复已经开始关注边界行为，而不只是表层界面改动

## 目前不足

### 1. 仍然偏本地单机应用

- 没有账号体系
- 没有云同步
- 没有跨设备恢复能力

### 2. 可访问性还不够

- 自定义 Compose 组件缺少系统化的 `semantics`
- TalkBack、无障碍朗读和大字体适配还不完整

### 3. 测试覆盖还不均衡

- 当前以单元测试为主
- 缺少 Compose UI 测试和真机或仪器测试
- 图片选择、通知权限、系统文档选择器这类系统交互还没有自动化覆盖

### 4. 发布流程还不够正式

- 仓库当前没有接入正式的 release keystore
- GitHub Release 中的可安装 APK 目前依赖 debug 签名包用于测试分发
- 如果要做真正面向用户的正式发布，还需要补完整签名配置

### 5. 数据库版本演进还要继续补

- 现在主存储已经切到 Room
- 但未来 schema 变化时，仍然需要显式 migration 方案
- 否则版本升级会成为新的维护风险点

## 项目结构

```text
app/src/main/java/com/example/timetable/
├── data/
│   ├── BackgroundImageStore.kt
│   ├── IcsCalendar.kt
│   ├── TimetableModels.kt
│   ├── TimetableRepository.kt
│   └── room/
├── notify/
│   ├── CourseReminderReceiver.kt
│   ├── CourseReminderRescheduleReceiver.kt
│   └── CourseReminderScheduler.kt
└── ui/
    ├── ScheduleBackground.kt
    ├── ScheduleScreen.kt
    ├── ScheduleViewModel.kt
    ├── Theme.kt
    ├── TimetableCalendar.kt
    ├── TimetableCards.kt
    ├── TimetableDialogs.kt
    └── TimetableHero.kt
```

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX Lifecycle
- Room
- Coroutines

## 构建环境

- `minSdk = 26`
- `targetSdk = 36`
- Java 17
- Gradle + Android Gradle Plugin

常用命令：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

## 适合的下一步

- 参考 [GITHUB_UI_RESEARCH.md](/e:/vswenjian_workspace_copy/GITHUB_UI_RESEARCH.md:1) 评估可接入的 Compose 周历 / 周选择器库
- 补正式 release 签名配置
- 为关键交互增加 Compose UI 测试
- 增加无障碍语义和更完整的内容描述
- 设计数据库显式迁移策略
- 如果产品目标扩大，再考虑云同步和账号系统
