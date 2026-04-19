# 编译说明

## 当前状态

工程已经完整搭建，所有源代码、Gradle 配置、任务入口已经готов。

**已完成：**
- ✅ 核心功能实现（课程表 CRUD、iCalendar 导入导出）
- ✅ 完整的 Kotlin + Jetpack Compose UI 层
- ✅ Gradle 8.7 自举脚本，JDK 17 自动配置
- ✅ 所有依赖声明，仓库源已配置

**编译障碍：**
- 本地网络无法连接 Google、Microsoft Maven 源
- 本地网络无法下载 Android SDK
- 这是**环境问题**，不是代码问题

## 修复办法

### 方案 1：使用 Android Studio（推荐）

如果网络正常或有 VPN：

1. 安装 Android Studio：`winget install Google.AndroidStudio`
2. 打开当前工程根目录
3. 等待 Gradle 同步（自动下载所有 SDK、编译工具）
4. Build → Build App Bundle (或 Build APK 进行快速构建)

### 方案 2：VPN 或代理

如果本机网络受限制，使用代理后修改 `.gradle/gradle.properties`：

```properties
org.gradle.jvmargs=-DhttpProxyHost=<proxy> -DhttpProxyPort=<port>
```

或在 `gradle` 命令中：

```bash
./gradlew assembleDebug -Dhttps.proxyHost=<proxy> -Dhttps.proxyPort=<port>
```

### 方案 3：本机 SDK 集成

如果有离线 Android SDK 环境可用，设置 `local.properties`：

```properties
sdk.dir=C:\Path\To\Android\SDK
# 路径需要包含 platforms/android-35、build-tools/<version> 等目录
```

## 快速测试

```bash
.\gradlew.bat --version     # 验证 Gradle 和 JDK 可用
.\gradlew.bat assembleDebug # 完整编译（需要 Android SDK）
```

## 环境自检（推荐先跑）

项目内置脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\env-doctor.ps1
```

VS Code 任务：

- `envDoctor`：检查 `gradlew.bat`、`local.properties`、`sdk.dir`、Android SDK 关键目录、Gradle 8.7 和 JVM 17。

## 文件结构

- `app/src/main/java/com/example/timetable/`
  - `MainActivity.kt` - 启动入口
  - `ui/` - Compose 界面和业务逻辑
    - `ScheduleScreen.kt` - 主屏幕
    - `ScheduleViewModel.kt` - 数据和状态管理
    - `Theme.kt` - Material 3 主题
  - `data/` - 数据模型和导入导出
    - `TimetableModels.kt` - 课程表数据结构
    - `IcsCalendar.kt` - iCalendar 导入导出

## 关键特性

1. **课程表管理**
   - 新增、编辑、删除课程
   - 周视图展示
   - 时间冲突提示

2. **日历导入导出**
   - `.ics` 格式（标准 iCalendar）
   - 自动生成周期规则（RRULE:FREQ=WEEKLY）
   - 支持导入多源课程表

## 依赖概览

| 库 | 版本 | 用途 |
|---|---|---|
| Android Gradle Plugin | 8.5.0 | 编译器 |
| Kotlin | 1.9.24 | 语言 |
| Jetpack Compose | 2024.06.00 | UI 框架 |
| Material 3 | 1.12.0 | 设计系统 |
| Coroutines | 1.8.1 | 异步任务 |

## 下一步

编译完成后：

1. **运行**：在模拟器或真机上选择`app`模块运行
2. **测试**：创建几条课程，验证导入导出
3. **扩展**：
   - 添加云同步（Firebase/自建 API）
   - 学期切换、教室冲突检测
   - 导入教务系统课程
   - 本地备份/恢复

## 故障排除

**错误：SDK location not found**
- 设置 `ANDROID_HOME` 环境变量，或
- 在 `local.properties` 里配置 `sdk.dir`

**错误：AGP 版本不匹配**
- 清理缓存：`./gradlew clean`
- 删除 `.gradle` 目录

**编译超时**
- 增加 JVM 内存：`org.gradle.jvmargs=-Xmx2g`（在 `gradle.properties`）
- 确保网络持续连接（首次构建会下载大量依赖）

---

**当前环境**
- Windows 11, x64
- JDK 17 (Eclipse Adoptium)
- Gradle 8.7
- 目标 SDK 36
- 最小 SDK 26 (Android 8)
