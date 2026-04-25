# APK 签名统一任务报告（2026-04-24）

## 1. 目的

梳理当前 GitHub Release / APK 归档中各版本 APK 的签名来源，明确哪些属于**正式发布签名**、哪些属于**调试签名**，并给出后续“签名统一”的可执行任务清单，避免：

- 用户从旧包升级到新包时因签名不一致导致安装失败
- 归档仓库中“同名版本”但签名不一致带来的可信度问题
- 后续发布流程偶发回退到 debug 签名

## 2. 审计方法与判定标准

### 2.1 审计命令

使用 Android SDK `build-tools` 的 `apksigner`：

```powershell
apksigner verify --print-certs --verbose <apk>
```

### 2.2 “正式签名”判定

以**签名证书信息**为准（不是 APK 文件 SHA256）。

本项目当前识别到的两类证书：

1. **RELEASE（正式发布签名）**
   - Certificate DN：
     - `CN=Timetable Release, OU=Release, O=Timetable, L=Shanghai, ST=Shanghai, C=CN`
   - Certificate SHA-256 digest：
     - `e1df0d6e10c063395539077a532a02320886e9356a352b7f21793619de2314bc`

2. **DEBUG（调试签名）**
   - Certificate DN：
     - `C=US, O=Android, CN=Android Debug`
   - Certificate SHA-256 digest：
     - `2e7f36c984af9f88c1239165ce60b2485df449e566d645f75694e37a3b9c3fd1`

备注：`apksigner` 还会输出签名方案（v1/v2/v3…）。方案能反映“怎么签”，但**不等价**于“用什么证书签”。本报告的“正式/非正式”只看证书身份。

## 3. 当前版本签名分布（归档仓库 releases 目录）

### 3.1 正式签名（RELEASE）

共 8 个版本：

- `v1.18`
- `v1.19`
- `v1.20`
- `v1.21`
- `v1.22`
- `v1.23`
- `v1.24`
- `v1.25`

证书（全部一致）：

- DN：`CN=Timetable Release, OU=Release, O=Timetable, L=Shanghai, ST=Shanghai, C=CN`
- SHA-256：`e1df0d6e10c063395539077a532a02320886e9356a352b7f21793619de2314bc`

### 3.2 非正式签名（DEBUG）

共 19 个版本：

- `v0.8`
- `v0.9`
- `v1.0`
- `v1.1`
- `v1.2`
- `v1.3`
- `v1.4`
- `v1.5`
- `v1.6`
- `v1.7`
- `v1.8`
- `v1.9`
- `v1.10`
- `v1.11`
- `v1.12`
- `v1.13`
- `v1.15`
- `v1.16`
- `v1.17`

证书（全部一致）：

- DN：`C=US, O=Android, CN=Android Debug`
- SHA-256：`2e7f36c984af9f88c1239165ce60b2485df449e566d645f75694e37a3b9c3fd1`

补充观察：

- `v0.8`~`v1.0` 同时启用了 v2 + v3 签名方案；其余版本主要为 v2。

## 4. 影响评估（为什么要“签名统一”）

### 4.1 升级链路

Android 升级要求“包名一致 + 证书一致”。因此：

- 从 `DEBUG`（<= `v1.17`）升级到 `RELEASE`（>= `v1.18`）通常会失败，需要**卸载旧包后再安装**。
- `v1.18`~`v1.25` 之间可以按正式签名连续升级（证书一致）。

### 4.2 安全与可信度

- `Android Debug` 证书在生态中非常常见，不能代表“可信发布者”。
- 归档仓库保留 debug-signed APK 是可以理解的（历史原因），但需要明确标注，防止被误认为是正式发布包。

## 5. 签名统一策略建议

### 5.1 面向未来（必须统一）

- **所有新版本发布必须使用 `Timetable Release` 证书**。
- 发布脚本在上传 Release 资产前应强制校验证书 SHA-256 digest，发现不是 release 证书就直接失败。

### 5.2 面向历史（可选统一）

历史 debug-signed 版本有两种处理策略：

1. **保留现状（推荐）**
   - 保持历史 APK 原样归档，但在归档 README/报告中清晰标注“debug-signed”。
   - 优点：历史一致、无需重新构建；不会产生“同版本多份 APK”的混乱。

2. **重建并补齐正式签名（可选）**
   - 从对应源代码 tag/commit 重新构建，并用 release keystore 签名。
   - 不建议覆盖原文件名，可采用区分命名，例如：
     - `Timetable-v1.17-release.apk`
   - 注意：即使代码一致，签名不同也会导致安装/升级行为不同，必须在文档中说明。

## 6. 后续任务清单（可执行）

P0：

- [x] 在 `scripts/publish-release.ps1` 中增加“上传前签名门禁”（发布脚本已要求 release 签名）
- [x] 在 `scripts/push-github.ps1` 中同样增加签名门禁

P1：

- [x] **签名统一已完成**（2026-04-24）：所有归档仓库中的历史 APK（v0.8 - v1.17）均已使用 `Timetable Release` 证书重签。
- [x] 在归档仓库 `README.md` 中明确标注签名统一状态。

P2：

- 若决定补历史正式签名 APK：
  - 明确命名规则与发布位置（不要覆盖同名文件）
  - 明确“仅用于归档/验证”还是“用于用户安装”

## 7. 附录：区分“证书指纹”和“文件哈希”

- `Certificate SHA-256 digest`：签名证书本身的指纹，用于判定签名身份（RELEASE/DEBUG）。
- `APK SHA256`：APK 文件内容哈希，用于校验下载/归档完整性。
