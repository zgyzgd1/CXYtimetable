# 课程表助手

一个简约的 Android 课程表应用，包含：

- 课程表新增、编辑、删除
- 周视图查看
- 日历导入导出（ICS）
- 二维码分享课程表

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- ViewModel + StateFlow
- ZXing 二维码生成
- ICS 文本导入导出

## 打开方式

1. 使用 Android Studio 打开当前工作区根目录。
2. 等待 Gradle 同步完成（自动下载 SDK 和构建工具）。
3. 运行 `app` 模块到模拟器或真机。

## 编译说明

详见 [COMPILE.md](COMPILE.md)，涵盖 VS Code、命令行、网络配置等场景。

**快速编译**（需要 Android SDK）：
```bash
.\gradlew.bat assembleDebug   # 生成 debug APK
.\gradlew.bat assembleRelease # 生成 release APK
```

- Gradle 会通过 `gradlew.bat` 自动下载（缓存到 `.gradle-wrapper/`）
- 首次构建需要联网下载 SDK 和依赖
- 在 Android Studio 里编译更能自动处理 SDK 和工具链

## 说明

- 导入功能支持 `.ics` 日历文件。
- 导出功能会生成 `.ics` 文件。
- 二维码分享用于把当前课程表编码为可分享字符串。
