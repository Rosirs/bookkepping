# 自动记账 (BookKeeping)

一款简单的 Android 本地记账 App：通过通知读取识别支付宝、微信、银行等应用发出的收支信息，并在本机自动生成账单。仅在本地保存财务数据，不上传到服务器、不依赖账号登录。

## 功能

- 通知栏监听识别收支
- 本地 Room 数据库存储
- 自动去重（按包名、金额、方向、分钟桶、内容指纹）
- 收入、支出、结余汇总
- 账单编辑（金额、方向、商户、备注）与删除
- 通知来源白名单 + 文本最小化（自动遮蔽验证码/链接）
- 仅本地数据，禁止云端备份/设备转出

## 环境

- compile / target SDK 36
- min SDK 26
- AGP 8.13.0、Gradle 8.13
- Kotlin 1.9.20、Compose BOM 2023.10.01
- Android Studio 自带 JBR（系统 Java 缺 `javac`）

## 构建

设置本地 JBR 与 SDK：

```bash
ANDROID_HOME=/home/robin/Android/Sdk ./gradlew <task>
```

常用任务：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

## Release 签名

发布签名密钥**不在**本仓库内。密钥和密码由环境变量或 `~/.gradle/bookkeeping-signing.properties` 提供：

```properties
storeFile=/home/robin/.gradle/bookkeeping-upload.jks
storePassword=...
keyAlias=bookkeeping_upload
keyPassword=...
```

可通过 `BOOKKEEPING_SIGNING_PROPERTIES=/path/to/signing.properties` 指定其他位置。

## 隐私与上架

- 隐私政策草稿：`docs/release/privacy-policy-zh.md`
- 商店清单草稿：`docs/release/store-listing-zh.md`
- 发布 checklist：`docs/release/release-checklist.md`

