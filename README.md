# 原阅

一个以白色为主色调的 Android 阅读器 App，包含：

- 首页每日资讯摘要
- 微博、百度、知乎、哔哩哔哩等平台热搜聚合（带失败提示与本地缓存）
- 自定义 RSS 源订阅与管理
- 本地 TXT / EPUB 书籍导入
- 在线小说、漫画书源订阅；添加书源后自动抓取一批推荐书籍，在线书架不再是空壳
- 在线阅读使用与本地一致的阅读页：同样的主题、字号、行距、翻页方式和目录
- 书源支持单/多书源 JSON、JSONL、Legado 分享链接、订阅页面链接和本地 JSON 文件；导入前分析并勾选，可检测可用性后批量添加
- 兼容书源中的相对搜索地址、目录链接选择器、`class.xxx@tag.a` 规则和多段落正文
- Kindle 风格阅读页，支持横向翻页、纵向滚动、纸张主题、字号与行距调节
- 4 个底部 Tab：首页 / 书架 / 热榜 / 设置；本地、在线、漫画在“书架”内用顶部 Tab 区分

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- Room
- DataStore
- OkHttp
- Jsoup

## 环境要求

- Android Studio
- JDK 17
- Android SDK 35
- 最低 Android 8.0（API 26）

## 运行

1. 用 Android Studio 打开本目录。
2. 等待 Gradle 同步完成。
3. 选择模拟器或真机运行 `app`。

本项目没有内置账号系统，所有数据均保存在本地。

## 正式打包（v1.1.7）

- 版本：`versionName = "1.1.7"`，`versionCode = 117`
- 签名：`release/keystore.properties` 指向 `release/yuanyue-release.jks`，口令与证书指纹见 `release/signing-info.txt`（**不要提交到版本库**）
- 构建：`gradlew assembleRelease`，产物为 `app/build/outputs/apk/release/app-release.apk`
- 正式安装包：`release/YuanyueReader-1.1.7-release.apk`（v2 + v3 签名）
- 单元测试：`gradlew testDebugUnitTest`（书源 JSON/分享链接/相对地址、目录选择器、正文多段落解析）
- 旧版本安装包：`release/YuanyueReader-1.1.6-release.apk`、`1.1.5`、`1.0.0`（仅作回滚参考）

