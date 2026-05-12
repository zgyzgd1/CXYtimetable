# 📋 项目文档指南

**生成日期**: 2026年5月10日

## 📚 可用文档

### 1. **PROJECT_SUMMARY.md** (15.8 KB)
项目的完整技术架构和功能总结。

**包含内容**:
- ✅ 项目类型和用途简介
- ✅ 完整的技术架构 (Compose, Room, MVVM等)
- ✅ 详细的源代码目录结构
- ✅ 8个核心组件的详细说明
- ✅ 7大功能模块的实现细节
- ✅ 主要依赖库清单 (38+个)
- ✅ 数据库架构与表结构
- ✅ 关键业务流程说明
- ✅ 编译构建说明
- ✅ 快速参考索引

**适用场景**:
- 新开发人员快速上手
- 架构设计讨论
- 功能模块查询
- 依赖升级评估

**快速链接**:
- [主要功能流程](PROJECT_SUMMARY.md#🎯-主要功能流程)
- [数据库结构](PROJECT_SUMMARY.md#📊-数据库架构)
- [核心组件](PROJECT_SUMMARY.md#🔑-核心组件详解)
- [构建命令](PROJECT_SUMMARY.md#🛠️-构建与编译)

---

### 2. **HEBAU_IMPORT_IMPROVEMENT_GUIDE.md** (31.1 KB)
教务系统导入功能的代码审查和改进指南。

**包含内容**:
- 🔴 **3个P0严重问题** (安全性/数据完整性/正确性)
  - WebView混合内容安全漏洞
  - 教学时间段解析的数据损坏风险
  - JwBridge竞态条件

- ⚠️ **5个P1中等问题** (下一版本应修复)
  - 周次范围解析的内存溢出风险
  - 导入预览无法显示具体错误
  - 大型导入缺少条目限制
  - 导入界面缺少超时控制
  - 自定义节次验证失败被忽略

- ℹ️ **3个P2/P3轻微问题** (优化和完善)

- ✅ **具体的代码修复方案** (每个问题都包含完整的改进代码)

- 📋 **快速修复清单** (按优先级列出1-2周的工作量)

- 🧪 **测试建议** (单元测试、集成测试、手动测试场景)

**适用场景**:
- 缺陷修复和性能优化
- 安全审计
- 代码质量改进
- 团队代码审查

**快速导航**:
- [执行总结](HEBAU_IMPORT_IMPROVEMENT_GUIDE.md#📊-执行总结)
- [严重问题P0](HEBAU_IMPORT_IMPROVEMENT_GUIDE.md#🔴-严重问题-p0---必须立即修复)
- [中等问题P1](HEBAU_IMPORT_IMPROVEMENT_GUIDE.md#⚠️-中等问题-p1---应该在下个版本修复)
- [改进方案总结](HEBAU_IMPORT_IMPROVEMENT_GUIDE.md#✅-改进方案总结)
- [快速修复清单](HEBAU_IMPORT_IMPROVEMENT_GUIDE.md#快速修复清单-1-2天内完成)
- [测试建议](HEBAU_IMPORT_IMPROVEMENT_GUIDE.md#🧪-测试建议)
- [验收标准](HEBAU_IMPORT_IMPROVEMENT_GUIDE.md#🎯-验收标准)

---

## 🎯 如何使用这些文档

### 场景1: 新开发人员快速上手
**阅读顺序**:
1. PROJECT_SUMMARY.md 的 **项目概览** 和 **技术架构** 部分 (5分钟)
2. PROJECT_SUMMARY.md 的 **源代码结构** 部分，了解关键文件位置 (10分钟)
3. PROJECT_SUMMARY.md 的 **主要功能流程** 部分，理解数据流 (10分钟)

**预计时间**: 25分钟

---

### 场景2: 参与教务导入功能的开发
**阅读顺序**:
1. PROJECT_SUMMARY.md 的 **教务系统集成** 部分 (5分钟)
2. HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的 **执行总结** - 架构图 (5分钟)
3. HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的 **严重问题P0** 部分，了解必须修复的缺陷 (15分钟)
4. HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的 **中等问题P1** 部分，理解改进空间 (20分钟)
5. HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的 **快速修复清单** 部分，制定工作计划 (5分钟)

**预计时间**: 50分钟

**待办任务** (可复制到项目管理工具):
- [ ] 修复P0: WebView混合内容 (1小时)
- [ ] 修复P0: HebauSectionTimes parseMinutes (1.5小时)
- [ ] 修复P0: JwBridge竞态条件 (1小时)
- [ ] 修复P1: 周次范围检查 (1.5小时)
- [ ] 修复P1: 导入错误显示 (2小时)
- [ ] 修复P1: 条目限制 (1.5小时)
- [ ] 修复P1: 超时控制 (1.5小时)

**预计总工时**: 10.5小时 (~1.3天)

---

### 场景3: 代码审查和质量评估
**阅读顺序**:
1. HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的 **现有代码中的问题总结** 表格 (5分钟)
2. HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的全部问题部分 (45分钟)
3. HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的 **代码质量度量** 表格 (5分钟)
4. HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的 **验收标准** 部分 (5分钟)

**预计时间**: 60分钟

---

### 场景4: 安全审计
**关键部分**:
- HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的 **问题1: WebView混合内容安全漏洞** (10分钟)
- HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的 **问题3: JwBridge竞态条件** (10分钟)
- HEBAU_IMPORT_IMPROVEMENT_GUIDE.md 的 **安全性检查清单** (5分钟)

**预计时间**: 25分钟

**安全建议**:
- ✅ JavaScript Bridge白名单验证 - 已实现
- ✅ JSON大小限制 - 已实现 (512KB)
- ✅ 输入验证 - 已实现 (需改进详细程度)
- ⚠️ **MIXED_CONTENT设置 - 需立即修复**
- ⚠️ **竞态条件 - 需立即修复**

---

## 📊 统计信息

### 代码问题统计

| 优先级 | 数量 | 影响 |
|--------|------|------|
| **P0** (严重) | 3 | 安全/数据/正确性 |
| **P1** (中等) | 5 | 可用性/性能/UX |
| **P2** (轻微) | 2 | 优化/文档 |
| **P3** (不紧急) | 1 | 政策/合规 |
| **总计** | **11** | |

### 影响范围

| 模块 | 问题数 | 关键性 |
|------|--------|--------|
| JwWebView.kt | 1 | 🔴 P0 |
| HebauSectionTimes.kt | 2 | 🔴 P0 + ⚠️ P1 |
| JwBridge.kt | 1 | 🔴 P0 |
| HebauCourseParser.kt | 1 | ⚠️ P1 |
| ScheduleViewModel.kt | 2 | ⚠️ P1 + ℹ️ P3 |
| TimetableRepository.kt | 1 | ⚠️ P1 |
| JwImportScreen.kt | 1 | ⚠️ P1 |
| JwWebView.kt (User-Agent) | 1 | ℹ️ P3 |

### 工作量评估

| 类别 | 时间 | 优先级 |
|------|------|--------|
| 紧急修复 (P0) | 3.5小时 | 立即 |
| 常规改进 (P1) | 7小时 | 本周 |
| 优化 (P2/P3) | 5小时 | 下周 |
| 测试编写 | 8小时 | 并行 |
| **总计** | **23.5小时** | ~3天 |

---

## 🔗 关键文件导航

### 核心导入流程的关键文件

```
教务系统导入 (WebView)
    ↓
jw/JwWebView.kt              # WebView配置和加载
    ↓
jw/JwBridge.kt               # JS-Native通信 ⚠️ P0竞态条件
    ↓
data/jw/hebau/HebauCourseParser.kt   # JSON解析 ⚠️ P1内存溢出
    ↓
data/jw/hebau/HebauSectionTimes.kt   # 时间段配置 🔴 P0数据损坏
    ↓
data/jw/hebau/HebauCourseMapper.kt   # 数据映射
    ↓
ui/ScheduleViewModel.kt      # 预览构建 ⚠️ P1错误显示 ⚠️ P1超时
    ↓
data/TimetableRepository.kt  # 数据库存储 ⚠️ P1批处理
    ↓
完成
```

### 需要优先修复的文件

1. **🔴 jw/JwWebView.kt** (安全)
   - 第XX行: `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW`

2. **🔴 data/jw/hebau/HebauSectionTimes.kt** (数据完整性)
   - `resolve()` 方法: parseMinutes返回null时处理

3. **🔴 jw/JwBridge.kt** (竞态条件)
   - `markImportRequested()` 和 `consumeImportRequest()` 同步问题

---

## 💡 快速参考

### 常见问题

**Q: 如果用户导入5000个课程会发生什么?**
A: 查看HEBAU_IMPORT_IMPROVEMENT_GUIDE.md的**问题6**

**Q: 教务系统数据格式变更如何处理?**
A: 查看PROJECT_SUMMARY.md的**HebauCourseParser详解**

**Q: 如何添加新的教学机构支持?**
A: 创建新的 `jw/[school]/[School]CourseParser.kt` 文件，参考Hebau的实现

**Q: 导入失败了用户无法看到具体错误**
A: 这是已知的P1问题，查看HEBAU_IMPORT_IMPROVEMENT_GUIDE.md的**问题5**

**Q: 为什么WebView要伪装User-Agent?**
A: 这是有争议的，查看HEBAU_IMPORT_IMPROVEMENT_GUIDE.md的**问题10**

---

## 📝 文档维护

### 如何更新这些文档

1. **PROJECT_SUMMARY.md** - 项目结构或功能变更时更新
   - 新增UI屏幕
   - 新增数据实体
   - 新增功能模块
   - 依赖库升级

2. **HEBAU_IMPORT_IMPROVEMENT_GUIDE.md** - 缺陷修复时更新
   - 问题修复后，在"改进方案总结"中标记为✅完成
   - 添加修复验证的测试用例
   - 更新统计信息

3. **README.md** (建议创建)
   - 项目入门指南
   - 构建和运行说明
   - 贡献者指南

---

## 📞 文档反馈

如果您发现文档中有不准确的地方或有改进建议，请：

1. 修改对应的 `.md` 文件
2. 提交代码审查
3. 在审查中标记问题所在的行号和部分

---

**文档最后更新**: 2026年5月10日
**覆盖版本**: v1.33 (代码34)
**下一步**: 优先修复P0问题，预计1-2天完成
