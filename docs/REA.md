# REA 阅读与后续 APK 研究

## 阅读范围与结论

本次阅读 `/Users/ryan/Documents/ryan/codes/rea` 的 `AGENTS.md`、`package.json`、`docs/architecture.mermaid`、`docs/android-analysis.md`，以及 `src/android/JadxAnalysis.ts`、`src/android/JadxProvider.ts` 的相关实现。项目包版本为 6.4.0。

观察到：REA 是本地逆向工具，CLI 与 MCP 共用应用层，分析结果带 Evidence 来源。Android 适配层通过 JADX 获取清单、类、方法和引用关系；包检查不等于签名验证。静态结果不能证明实际运行行为。

Android 路线需要完整 JDK 17+ 与 `jadx-headless-mcp 0.7.1` JAR。REA 不负责安装 Android 开发 SDK。当前 Android 构建环境由本账本项目单独准备，并非修改 REA 配置或执行 REA setup。

## 鲨鱼记账：当前状态

没有本地目标 APK，尚未执行反编译或运行行为采集，没有生成鲨鱼记账 Evidence。小米应用商店页面标识包名为 `com.shark.jizhang`：https://m.app.mi.com/details?id=com.shark.jizhang 。这个页面只用于定位目标，不构成代码或行为证据。

本账本 UI、SQLite 数据结构和统计规则均为独立设计。没有复制鲨鱼记账代码、素材、网络服务或账号机制。

## 从手机取得 APK

手机安装鲨鱼记账并开启 USB 调试，连接电脑后：

```sh
adb shell pm path com.shark.jizhang
```

将返回的 `package:` 后面的完整 APK 路径用于 `adb pull`。如果有多个 split APK，逐个保留，并记录版本、来源和 SHA-256。不要把手机账本数据库或登录凭据混入 APK 样本。

## 有 APK 后的分析问题

1. 快速记账最短流程是什么？从页面事件追踪到保存调用。
2. 分类与收支如何建模？追踪实体和持久化边界。
3. 日／月统计怎么计算？区分普通支出、转账与退款。
4. 哪些交互值得借鉴？以真实手机运行观察补充静态结果。

配置已有 JADX JAR 后，可使用 REA 的 `inspect-android-package`、`search-android-classes`、`inspect-android-class`、`inspect-android-method` 和 `trace-android-references`。先包检查，再精确追踪；按样本摘要和 Evidence ID 记录结论与未验证范围。研究不作为当前账本开发的前置条件。
