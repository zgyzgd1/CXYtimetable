# HEBAU 教务系统适配任务计划书

## 1. 文档目标

本文档用于指导当前课表项目接入河北农业大学本科教务系统，实现从教务系统导入课程表数据的能力。

本文档重点覆盖：

- 目标范围
- 当前项目现状评估
- 实际入口确认
- 技术方案选择
- 模块拆分
- 分阶段开发计划
- 风险与应对
- 验收标准
- 推荐实施顺序

## 2. 项目背景

当前项目已经具备较成熟的本地课表能力，包括：

- 课程数据模型
- 周课表展示
- 课程冲突检测
- 课程提醒
- `ICS` 导入导出
- 导入预览与确认
- Room 持久化存储

目前欠缺的是“教务系统接入层”。

因此，本次工作不是重写课表主体，而是在现有项目基础上新增一个“教务系统导入通道”。

## 3. 目标范围

### 3.1 本期目标

本期目标是实现：

- 打开河北农业大学教务系统入口
- 用户在应用内完成登录
- 获取当前课表数据
- 解析课程、周次、节次、地点、教师等信息
- 转换为项目现有 `TimetableEntry`
- 复用现有导入预览、冲突确认、落库逻辑

### 3.2 本期不做

以下内容不作为第一阶段目标：

- 原生账号密码登录模拟
- 自动验证码识别
- 自动定时同步
- 多学校统一适配平台
- 账号密码长期安全保存
- 教务成绩、考试、选课等非课表功能

## 4. 实际入口确认

基于 `2026-04-30` 的官方页面核实结果，河北农业大学当前本科教务相关入口为：

- 教务系统：`urp.hebau.edu.cn:1009`
- 选课系统：`urp1.hebau.edu.cn:1010`

结论：

- 当前适配目标应优先按 `URP` 体系处理
- 暂不按“金智教务系统”进行实现
- 第一阶段主入口锁定为 `urp.hebau.edu.cn:1009`
- `urp1.hebau.edu.cn:1010` 作为后续兼容入口保留

## 5. 当前代码现状评估

### 5.1 已有能力

当前项目已经具备以下可复用能力：

- `TimetableEntry` 已支持课程名称、日期、星期、开始结束时间、按周循环、单双周、自定义周、跳周
- `ScheduleViewModel` 已具备导入预览、冲突统计、确认导入、最终提交逻辑
- `TimetableRepository` 已具备合并导入能力
- UI 已存在导入入口和导入确认弹窗
- Room 数据库结构已经稳定

### 5.2 当前缺失

当前项目缺少：

- `WebView` 教务登录页承载
- 页面脚本注入能力
- JS 与 Android 的桥接层
- 教务原始课程数据解析器
- 教务课程到 `TimetableEntry` 的映射器
- 教务导入专属入口 UI

### 5.3 结论

本次适配应尽量复用当前项目的“导入后半段”，只新增“教务抓取与转换前半段”。

## 6. 技术方案选择

## 6.1 候选方案

### 方案 A：原生网络请求模拟登录

做法：

- 使用原生网络库请求登录接口
- 管理 Cookie / Session
- 请求课表接口
- 解析返回数据

优点：

- 可完全掌控网络流程
- 后续便于做自动同步

缺点：

- 登录、验证码、统一认证复杂度高
- HEBAU 实际系统细节未知时排障困难
- 第一阶段成本过高

### 方案 B：WebView + 页面内脚本注入

做法：

- 在应用中打开教务系统网页
- 用户手动登录
- 登录成功后在当前页面注入 JS
- 利用页面已有会话和 Cookie 拉取课程数据
- 通过 `JavascriptInterface` 回传 Android

优点：

- 无需第一阶段处理复杂登录逻辑
- 登录态直接复用浏览器上下文
- 更贴合 `拾光课程表` 的现成经验
- 开发风险最低、落地最快

缺点：

- 首版自动化程度较低
- 后续如果做后台同步，需要再抽原生网络层

## 6.2 本期推荐方案

本期采用：

- `WebView + 页面脚本注入 + Android 桥接 + 现有导入流程复用`

原因：

- 对现有项目改动最小
- 最容易先跑通 HEBAU 真实场景
- 可以最大程度减少登录环节的不确定性

## 7. 参考实现结论

通过参考 `拾光课程表` 及其适配仓库，可提炼出以下关键经验：

### 7.1 适配脚本与 App 分层

- 页面环境内执行脚本
- 脚本负责抓取和初步解析
- App 负责接收、保存和展示

### 7.2 优先抓 JSON 接口

- 若教务系统页面内部有隐藏接口，优先直接请求接口
- 接口结构通常比 HTML 更稳定
- 解析复杂度显著低于 DOM 抓取

### 7.3 登录态复用

- 不强求原生模拟登录
- 在用户已登录的网页上下文内抓取数据最稳

## 8. 第一阶段总体方案

第一阶段目标是跑通：

1. 应用打开教务系统主入口
2. 用户手动登录
3. 用户到达课表页面
4. 应用注入 JS 抓取课表数据
5. Android 接收并解析数据
6. 转换为 `TimetableEntry`
7. 使用现有预览与确认流程导入

## 9. 模块设计

## 9.1 新增模块总览

建议新增以下模块：

- `jw/`：教务导入总入口
- `jw/hebau/`：HEBAU 专属解析和映射
- `assets/jw/`：WebView 注入脚本

建议文件如下：

- `app/src/main/java/com/example/timetable/jw/JwImportActivity.kt`
- `app/src/main/java/com/example/timetable/jw/WebViewBridge.kt`
- `app/src/main/java/com/example/timetable/jw/JwImportContract.kt`
- `app/src/main/java/com/example/timetable/jw/hebau/HebauUrpRawCourse.kt`
- `app/src/main/java/com/example/timetable/jw/hebau/HebauUrpParser.kt`
- `app/src/main/java/com/example/timetable/jw/hebau/HebauUrpMapper.kt`
- `app/src/main/assets/jw/hebau_urp.js`

## 9.2 模块职责

### `JwImportActivity.kt`

职责：

- 承载 `WebView`
- 打开 HEBAU 教务系统入口
- 控制脚本注入时机
- 向 `ScheduleViewModel` 或中间导入层提交抓取结果

建议功能：

- 页面加载状态
- “重新打开教务系统”按钮
- “开始导入”按钮
- “帮助提示”区域

### `WebViewBridge.kt`

职责：

- 提供 JS 到 Android 的桥接接口
- 接收课程 JSON
- 接收错误信息
- 接收调试日志

建议接口：

- `postCourses(json: String)`
- `postError(message: String)`
- `postLog(message: String)`

### `HebauUrpRawCourse.kt`

职责：

- 定义从 HEBAU 教务接口解析出的中间数据模型

建议字段：

- `name`
- `teacher`
- `position`
- `day`
- `startSection`
- `endSection`
- `weeks`

### `HebauUrpParser.kt`

职责：

- 将 JS 回传的 JSON 文本解析为中间模型列表
- 校验字段完整性

### `HebauUrpMapper.kt`

职责：

- 将中间课程模型映射为 `TimetableEntry`
- 处理星期、周次、日期、单双周、自定义周等规则

## 10. 与现有代码的衔接设计

## 10.1 复用导入后半段

不应重写以下逻辑：

- 导入预览
- 冲突统计
- 用户确认导入
- 最终写库

这些应继续沿用现有 `ScheduleViewModel` 与 `TimetableRepository`。

## 10.2 建议新增 ViewModel 接口

建议在 `ScheduleViewModel` 新增：

- `importFromAcademicRaw(json: String)`
- 或 `importFromAcademicEntries(entries: List<TimetableEntry>)`

推荐内部流程：

1. 解析教务 JSON
2. 映射为 `List<TimetableEntry>`
3. 调用现有 `buildImportPreview(...)`
4. 若有冲突则走现有确认弹窗
5. 用户确认后走现有 `commitImport(...)`

## 10.3 推荐导入链路

完整链路建议为：

1. `WebView` 注入 JS
2. JS 获取 HEBAU 课表数据
3. JS 通过桥接回传 JSON
4. Android 解析为中间模型
5. Android 映射为 `TimetableEntry`
6. `ScheduleViewModel` 复用现有导入预览
7. 最终保存数据库

## 11. JS 抓取策略

## 11.1 优先级

推荐抓取优先顺序：

1. 教务系统内部 JSON 接口
2. 页面全局变量中的课表对象
3. HTML DOM 解析

## 11.2 第一阶段优先做法

优先参考 `拾光课程表` 中 `URP` 适配策略：

- 先尝试在已登录页面内请求课表接口
- 如果能拿到结构化 JSON，就不做 HTML 解析

## 11.3 JS 任务

`hebau_urp.js` 的目标：

- 检查当前页面是否可能已经登录
- 尝试调用课表接口
- 提取课程数组
- 提取节次和时间段
- 组装简化 JSON
- 回传给 Android

## 12. 数据映射规则

## 12.1 课程字段映射

中间课程模型到 `TimetableEntry` 需要完成以下映射：

- `name -> title`
- `position -> location`
- `teacher -> note` 或扩展备注拼接
- `day -> dayOfWeek`
- `startSection/endSection -> startMinutes/endMinutes`
- `weeks -> recurrenceType/weekRule/customWeekList`

## 12.2 周次映射建议

若教务返回显式周次数组，例如：

- `[1,2,3,4,5,6,7,8]`

则建议：

- `recurrenceType = WEEKLY`
- `weekRule = CUSTOM`
- `customWeekList = "1-8"`

如果教务返回的是所有单周或双周，也可以进一步压缩为：

- 单周：`weekRule = ODD`
- 双周：`weekRule = EVEN`

但首版为了降低风险，统一转换为：

- `WEEKLY + CUSTOM + customWeekList`

更稳妥。

## 12.3 起始日期处理

项目当前 `TimetableEntry` 需要有：

- `date`
- `semesterStartDate`

建议第一阶段处理方案：

- 要求用户在导入前确认“学期起始日期”
- 或从现有全局学期配置读取

若教务系统中能拿到学年学期和周一日期，再做自动推导。

第一阶段不建议在没有依据的情况下硬编码日期。

## 13. UI 方案

## 13.1 入口位置

建议在现有导入区域新增并列入口：

- `ICS 导入`
- `教务导入`

不要替换原有 `ICS` 导入。

## 13.2 交互流程

建议交互流程：

1. 点击“教务导入”
2. 打开导入说明页
3. 进入 `WebView`
4. 用户手动登录教务系统
5. 用户导航到课表页
6. 点击“开始导入”
7. 成功抓取后进入现有导入预览弹窗

## 13.3 提示文案建议

首次说明建议包含：

- 请输入教务系统账号密码完成登录
- 请进入课表查询页面后再点击导入
- 导入后不会自动修改原课表，需确认后才保存

## 14. 分阶段任务计划

## 阶段一：入口打通

目标：

- 新增教务导入 UI 入口
- 新增 `JwImportActivity`
- `WebView` 成功打开 `urp.hebau.edu.cn:1009`

任务：

- 新增页面和路由
- 配置 `WebView`
- 增加基础加载状态和错误提示

交付：

- 可以在应用内打开 HEBAU 教务系统

## 阶段二：桥接打通

目标：

- JS 能与 Android 通讯

任务：

- 实现 `WebViewBridge`
- 注入测试 JS
- 实现简单的回传验证

交付：

- 页面脚本能回传测试字符串到 Android

## 阶段三：课程抓取

目标：

- 从 HEBAU 课表页面抓到真实课程数据

任务：

- 编写 `hebau_urp.js`
- 尝试 URP 内部接口抓取
- 输出标准中间 JSON

交付：

- 成功获取至少一个学期的课程数据样本

## 阶段四：解析与映射

目标：

- 抓取结果转换为项目课程模型

任务：

- 实现 `HebauUrpParser`
- 实现 `HebauUrpMapper`
- 节次映射到时间
- 周次映射到 `TimetableEntry`

交付：

- 成功生成 `List<TimetableEntry>`

## 阶段五：接入现有导入链路

目标：

- 复用现有预览与确认能力

任务：

- 新增 `ScheduleViewModel` 教务导入入口
- 接入导入预览
- 接入冲突确认
- 接入最终导入

交付：

- 教务导入全链路跑通

## 阶段六：测试与收尾

目标：

- 提升稳定性和可维护性

任务：

- 补充单元测试
- 补充失败态提示
- 处理空课表、未登录、接口失效场景

交付：

- 可用于真实使用的首版导入能力

## 15. 测试计划

## 15.1 单元测试

建议新增测试：

- 周次字符串解析测试
- 节次时间映射测试
- 原始课程转 `TimetableEntry` 测试
- 教务导入预览冲突统计测试

## 15.2 集成测试

建议验证场景：

- 成功登录并导入
- 未登录直接点击导入
- 课表为空
- 多周次课程
- 单双周课程
- 同一课程多时间段
- 导入后与现有课表冲突

## 15.3 回归测试

需要保证原有功能不受影响：

- `ICS` 导入
- `ICS` 导出
- 手动新增课程
- 周视图显示
- 提醒功能

## 16. 风险与应对

## 16.1 真实接口不稳定

风险：

- HEBAU 的 `URP` 接口路径或字段可能与参考项目不同

应对：

- 第一阶段先做脚本探测与日志输出
- 保留降级 HTML 解析方案

## 16.2 登录流程复杂

风险：

- 存在统一认证、验证码、校内网限制等因素

应对：

- 首版用 `WebView` 手动登录
- 暂不做原生登录模拟

## 16.3 学期起始日期缺失

风险：

- 项目内周次展示依赖 `semesterStartDate`

应对：

- 第一阶段允许用户手动确认或设置学期起始日期
- 后续再研究自动推导

## 16.4 节次时间不统一

风险：

- 教务课表的节次时间可能与默认节次配置不一致

应对：

- 若接口能返回节次时间，则一并导入
- 若无法返回，先用现有节次配置并允许用户后续手动调整

## 17. 验收标准

首版功能验收需满足：

- 应用内能打开 `urp.hebau.edu.cn:1009`
- 用户登录后可以从课表相关页面发起导入
- 能抓取真实课程数据
- 能正确导入课程名称、星期、节次、周次、地点、教师
- 能复用现有导入预览和冲突确认
- 导入后周视图和日视图显示正常
- 原有 `ICS` 导入导出功能不回归

## 18. 推荐实施顺序

建议按以下顺序推进：

1. 新增“教务导入”入口
2. 新建 `JwImportActivity`
3. 跑通 `WebView`
4. 实现 `WebViewBridge`
5. 编写最小测试脚本
6. 编写 `hebau_urp.js`
7. 实现 `HebauUrpParser`
8. 实现 `HebauUrpMapper`
9. 接入 `ScheduleViewModel`
10. 接入现有导入确认流程
11. 补测试
12. 补失败态与交互优化

## 19. 里程碑建议

### 里程碑 M1

- 应用内打开 HEBAU 教务系统成功

### 里程碑 M2

- JS 与 Android 桥接成功

### 里程碑 M3

- 成功抓取真实课表 JSON

### 里程碑 M4

- 成功转换为 `TimetableEntry`

### 里程碑 M5

- 教务导入全链路跑通

## 20. 最终建议

本次适配最重要的策略不是“做一个完美的通用教务框架”，而是：

- 先围绕 HEBAU 的真实入口跑通首个可用版本
- 最大程度复用当前项目已有导入能力
- 优先使用 `WebView + 页面脚本` 方式降低首期风险

待首版验证成功后，再考虑抽象出更通用的学校适配能力。

