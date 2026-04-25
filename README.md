# 课程表助手 (Timetable Assistant)

一个基于 Kotlin、Jetpack Compose 和 Material 3 的 Android 原生课表应用，强调本地离线、轻量界面和可维护的代码结构。

---

## 全球项目审查报告 (2026-04-24)

### 1. 架构评估 (Architecture Assessment)
项目采用现代 Android 开发的最佳实践：
- **MVVM 架构**: `ScheduleViewModel` 作为核心，通过 `StateFlow` 驱动 UI，实现了严格的单向数据流 (UDF)。
- **Repository 模式**: `TimetableRepository` 封装了 Room 数据库操作，为 UI 层提供简洁、线程安全的数据访问接口。
- **模块化 UI**: UI 层按功能拆分为 `DayScheduleList`、`WeekScheduleBoard` 等独立组件，有效解决了单文件膨胀问题（如 `ScheduleScreen` 的重构）。
- **解耦逻辑**: ICS 解析、冲突检测 (`TimetableConflicts`) 和提醒调度 (`CourseReminderScheduler`) 均抽离为独立逻辑模块，易于测试和维护。

### 2. 技术亮点 (Technical Highlights)
- **持久化方案**: 全面迁移至 **Room**，相比早期 JSON 存储，提供了更好的事务支持和查询性能。
- **后台任务**: 实现了“接力式”提醒机制，结合 `AlarmManager` 的准时性和 `WorkManager` 的兜底补偿，平衡了功耗与提醒准确度。
- **高度定制化**:
    - 支持自定义背景图（含透明度、遮罩、裁剪位移调节）。
    - 支持动态色相偏移，实现个性化主题色切换。
- **数据兼容性**: 完善的 `.ics` 导入导出能力，支持自定义周次解析和重复规则。

### 3. 工程质量与稳定性
- **签名统一 (Milestone)**: 已完成对全版本（从 v0.8 到 v1.27）的签名统一。所有历史 APK 现均由正式 Release 密钥重签，解决了用户升级时的签名冲突痛点。
- **自动化测试**: 核心业务逻辑（ICS 解析、日期计算、闹钟触发算法、数据快照）均由单元测试覆盖，确保了逻辑回归的安全性。
- **发布流程**: 建立了标准化的 PowerShell 发布流，集成了版本自动提升、构建校验、GitHub Release 上传及 APK 自动化归档。

### 4. 近期里程碑 (v1.27)
- **统一本地化**: 修复了提醒格式化和状态文本中的中英文混杂问题，实现了全界面本地化对齐。
- **归档一致性**: `apk-archive-repo` 现已同步所有正式签名的历史版本。
- **UI 性能优化**: 优化了复杂周视图下的渲染逻辑，减少了不必要的 Recomposition。

### 5. 未来演进建议 (Roadmap)
1. **云同步**: 引入 Firebase 或 WebDAV 实现跨设备数据备份。
2. **无障碍增强**: 进一步完善 Compose 的 `semantics` 语义，提升 TalkBack 使用体验。
3. **自动化 UI 测试**: 补充 `ComposeTestRule` 覆盖关键点击路径。
4. **数据库演进**: 随着功能增加，需设计更精细的 Room 自动迁移 (Migration) 策略。

---

## 版本历史与核心特征 (Version History & Highlights)

| 版本 | 核心特征 | 技术演进 |
|:---:|:---|:---|
| **v1.31** | **项目归档与文档完善** | 更新项目全局审查报告；同步最新功能至 README；完善 APK 归档说明；项目进入维护模式。 |
| **v1.30** | **桌面小部件重构** | 重新设计今日课表 Widget，支持课程状态指示器（进行中/即将开始/已结束）；优化 Widget 资源引用与颜色体系。 |
| **v1.29** | **UI 细节打磨** | 精细化 UI 间距与视觉观感；优化大屏/平板模式下的布局表现；提升系统状态栏交互体验。 |
| **v1.28** | **新图标 & 签名大一统** | 引入全新“深色动漫风格”应用图标；彻底完成历史版本签名统一任务；强化签名完整性校验。 |
| **v1.27** | **本地化对齐** | 修复提醒格式化和状态文本中的中英文混杂问题，实现全界面本地化对齐。 |
| **v1.26** | **国际化 (i18n) 迁移** | 提取 280+ 字符串资源；移除冗余权限；优化 Android 15/16 预览版兼容性。 |
| **v1.25** | **个性化背景支持** | 引入自定义背景图持久化方案，支持透明度与遮罩实时调节。 |
| **v1.24** | **架构模块化重构** | 拆解巨型 Composable 函数；Room 数据库新增复合索引优化查询性能。 |
| **v1.23** | **桌面小部件 & 无障碍** | 新增今日课表/下一节课 Widget；强化 TalkBack 语义适配；提升闹钟调度可靠性。 |
| **v1.22** | **节次时间精控** | 支持每节课时间的微调；新增内置渐变动态壁纸；完善自动化发布脚本。 |
| **v1.20** | **交互体验升级** | 改进周视图网格滚动逻辑；引入 Compose 实现的周日期选择条。 |
| **v1.18** | **正式发布里程碑** | 首次切换至官方 Release 签名，确立了生产级的版本迭代路径。 |
| **v1.0** | **Room 架构转型** | 从原生 JSON 文件完全迁移至 Room 数据库；引入自研高性能 ICS 解析算法。 |
| **v0.8** | **项目起航** | 实现基础课表展示与 ICS 导入功能，确定了本地优先、隐私第一的产品理念。 |

---

## 技术栈
- **语言**: Kotlin (Coroutines, Flow)
- **界面**: Jetpack Compose, Material 3
- **存储**: Room (SQLite)
- **工具**: WorkManager, AlarmManager
- **构建**: Gradle Kotlin DSL, KSP

## 快速开始
```powershell
# 运行单元测试
.\gradlew.bat testDebugUnitTest

# 构建调试包
.\gradlew.bat assembleDebug

# 执行一键发布流 (含备份、推送、构建、发布、归档)
.\scripts\push-github.ps1 -Message "Your update message"
```

## 许可证
本项目采用 MIT 许可证。

---

## APK 归档仓库 (apk-archive-repo)

### 归档概述
APK 归档仓库 (`apk-archive-repo`) 是本项目的辅助仓库，用于存储所有历史版本的 APK 文件。归档仓库与主仓库并行维护，每次发布时会自动同步最新 APK。

### 归档结构
```
apk-archive-repo/
├── README.md      # 版本列表，记录每个版本对应的 APK 文件名
└── releases/      # APK 文件存储目录
    ├── Timetable-v0.8.apk
    ├── Timetable-v1.0.apk
    ├── Timetable-v1.1.apk
    └── ...
```

### 版本签名状态 (v0.8 - v1.27)

| 版本范围 | 签名类型 | 证书 DN | 证书 SHA-256 |
|:---:|:---:|:---|:---|
| **v0.8 ~ v1.17** | DEBUG → RELEASE 已重签 | `CN=Timetable Release, OU=Release, O=Timetable, L=Shanghai, ST=Shanghai, C=CN` | `e1df0d6e10c063395539077a532a02320886e9356a352b7f21793619de2314bc` |
| **v1.18 ~ v1.31** | RELEASE 正式签名 | `CN=Timetable Release, OU=Release, O=Timetable, L=Shanghai, ST=Shanghai, C=CN` | `e1df0d6e10c063395539077a532a02320886e9356a352b7f21793619de2314bc` |

> **说明**：2026-04-24 完成签名统一，所有历史 DEBUG 签名的 APK（v0.8 ~ v1.17）已使用正式 Release 证书重签。

### 升级兼容性
- `v0.8` ~ `v1.17`（原 DEBUG 签名）升级到 `v1.18`+（RELEASE 签名）：**需卸载旧包后全新安装**
- `v1.18` ~ `v1.31` 之间：可正常覆盖升级（证书一致）

### APK 命名规范
归档中的 APK 文件遵循命名规范：`Timetable-v{X}.apk`，例如：
- `Timetable-v1.27.apk`
- `Timetable-v1.0.apk`
- `Timetable-v0.8.apk`
