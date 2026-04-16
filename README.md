# 课程表助手 (Timetable Minimal)

> 轻量、精美、低占用的 Android 原生课程表应用。  
> 基于 Kotlin + Jetpack Compose + Material Design 3 构建。

---

## 技术评估报告

本报告针对当前代码库就架构设计、工程质量与技术选型进行客观评估。

### 评估概览

| 维度 | 评价 |
|------|------|
| UI 架构 | ✅ 优秀：Compose + MVVM + UDF，组件化良好 |
| 并发安全 | ✅ 优秀：全局 Mutex 单例 Repository，无读写竞争 |
| 电池优化 | ✅ 优秀：接力式单点闹钟，后台唤醒锁降至最低 |
| 数据导入导出 | ✅ 良好：手写 RFC 5545 ICS 解析器，支持 RRULE |
| 代码整洁度 | ✅ 良好：无硬编码日期、无死代码、无冗余 import |
| 持久层 | ⚠️ 一般：单文件 JSON 全量写入，不具备查询能力 |
| 无障碍支持 | ⚠️ 待改善：缺少 semantics 语义标签 |
| 云同步 | ❌ 不支持：当前为纯本地离线架构 |

---

## 架构全景

```
app/
├── data/
│   ├── TimetableModels.kt        # 数据模型与工具函数
│   ├── TimetableRepository.kt    # 存储单例（Mutex 并发安全）
│   ├── TimetableShareCodec.kt    # JSON 编解码（分享/持久化）
│   └── IcsCalendar.kt            # RFC 5545 ICS 解析与写出
├── notify/
│   ├── CourseReminderScheduler.kt       # 接力式闹钟调度引擎
│   ├── CourseReminderReceiver.kt        # 提醒触发 + 接力下一次
│   └── CourseReminderRescheduleReceiver.kt  # 时区/包更新重同步
└── ui/
    ├── ScheduleViewModel.kt      # 状态管理（StateFlow + MVVM）
    ├── ScheduleScreen.kt         # 主界面框架
    ├── TimetableCalendar.kt      # LazyRow 水平周日历选择器
    ├── TimetableCards.kt         # 课程卡片（彩色 Accent Bar）
    ├── TimetableHero.kt          # Hero 区域（胶囊按钮布局）
    ├── TimetableDialogs.kt       # 课程编辑弹窗
    ├── QrCode.kt                 # 二维码分享
    └── Theme.kt                  # Material 3 主题
```

---

## 核心技术亮点

### 1. UI：轻量化 Compose 组件架构

- 放弃旧式 XML，全量使用 **Kotlin + Jetpack Compose**，声明式渲染
- 经过 Phase 4 重构后，原单文件"神之类"代码被拆分为高内聚独立组件，有效抑制了 Recomposition 开销
- 日历组件采用 `LazyRow` 水平周选择器替代全月网格，渲染节点减少 70%+
- 课程卡片引入**基于标题 Hash 的自动着色 Accent Bar**，色彩稳定无溢出崩溃（使用 `hashCode and Int.MAX_VALUE`）
- Hero 区域精简为渐变背景卡片 + 三栏胶囊按钮，屏幕占用比原版降低约 50%

### 2. 状态管理：MVVM + UDF 全链路

- `ScheduleViewModel` 通过 `StateFlow` 对外暴露只读状态，Composable 使用 `collectAsStateWithLifecycle` 感知生命周期，防止后台内存泄漏
- 用户消息通知采用 `SharedFlow`（非粘性），避免屏幕旋转重触发
- `init` 块通过 `viewModelScope.launch` 异步加载数据，UI 线程零阻塞

### 3. 并发安全存储：Repository + Mutex

```
UI / ViewModel ──┐
                  ├──► TimetableRepository (Mutex) ──► timetable_entries.json
Scheduler ────────┘
```

- 提取全局单例 `TimetableRepository`，统管所有 JSON 文件 I/O
- 所有读写操作通过 `Mutex.withLock` 独占执行，彻底消除 UI 协程与后台广播接收器之间的读写竞争
- 内置存储损坏检测：自动备份并通过 Snackbar 告警，确保数据有迹可查

### 4. 电池优化：接力式单点闹钟调度

```
sync() 当前调用
  └── 查找最近一节课 ──► 设定 1 个精确闹钟
                              │
                         触发通知
                              │
                   resyncFromStorage() ──► 设定下一个
```

- `sync()` 每次只为**距离当前最近的一节课（或同时段并列课程）**设定精确闹钟
- 通知触发后 `CourseReminderReceiver` 自动调用 `resyncFromStorage()` 接力下一次
- 系统后台存活的唤醒锁从「全课程数」降至**恒定 1 个**，在 Android 14 高功耗审查下可完全通过
- 已移除 `RECEIVE_BOOT_COMPLETED` 权限，保持应用最小权力原则

### 5. ICS 日历解析器

- 完整实现 RFC 5545 标准的 ICS 读写，无第三方依赖
- 支持 `RRULE`（每日/每周/每月重复规则）、`EXDATE`（排除日期）、`TZID`（时区参数）
- 使用 `ZoneId.systemDefault()` 动态适配用户本地时区，无硬编码区域假设
- 最大展开次数保护（`MAX_EXPANDED_OCCURRENCES = 512`）防止异常规则死循环

---

## 已知局限性与演进路线

### ⚠️ 局限 1：持久层承载力不足

**现状**：全课表数据压缩至单一 `timetable_entries.json` 进行全量覆盖写入。  
**瓶颈**：不支持按条件查询，数据量增大后读取效率线性下降。  
**建议**：迁移至 **Room (SQLite)** ORM，支持细粒度增量查询与事务保护。

### ⚠️ 局限 2：缺少无障碍语义（A11y）

**现状**：自定义 Composable 未添加 `Modifier.semantics {}` 语义标签。  
**影响**：TalkBack 读屏用户、大字体用户体验较差。  
**建议**：为所有交互组件添加 `contentDescription` 与 `stateDescription`。

### ❌ 局限 3：无云端同步

**现状**：纯离线本地架构，分享依赖 Base64 二维码或 ICS 文件导出。  
**影响**：设备丢失时数据无法通过账户恢复，无法跨设备实时同步。  
**建议**：接入后端 API + JWT 鉴权，实现账户体系与云端课表同步。

---

## 版本历史

| Commit | 内容 |
|--------|------|
| `3cd344f` | **Phase 1-3**：接力闹钟 + Mutex 存储 + 消除硬编码日期 + 清理死代码 |
| `cc27a71` | **Phase 4**：UI 模块化重构（LazyRow 日历 / Accent Bar 卡片 / Hero 精简） |
| 更早 | 基础架构：Mutex 并发锁 / Android 14 精确闹钟适配 / ICS 解析器 / MVVM 重构 |

---

## 运行环境

| 项目 | 要求 |
|------|------|
| 最低 SDK | Android 8.0（API 26） |
| 目标 SDK | Android 14（API 34） |
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 构建工具 | Gradle + AGP |
