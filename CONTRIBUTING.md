# 贡献指南

感谢你愿意改进原阅。

## 开始之前

- 先搜索已有的 [Issues](https://github.com/hothub111/OriginRead/issues)，避免重复。
- 大改动请先开 issue 说明目标和方案，再提交 PR。
- 请勿在 issue、PR 或截图中公开签名文件、口令、Cookie 或带个人信息的书源。

## 报告问题

一个可处理的问题通常包含：

- 原阅版本与 Android 版本
- 设备型号
- 复现步骤
- 预期结果与实际结果
- 相关日志或截图（隐去个人信息）
- 如果是书源问题，请附最小化后的书源规则，而不是整份私人书源

## 开发

环境要求：JDK 17、Android SDK 35。

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

代码风格遵循 Kotlin 官方风格与现有项目结构。涉及解析器、书源规则或阅读排版时，请补充对应的单元测试。

## 提交 PR

- 从 `main` 切出分支，保持改动聚焦。
- 说明「为什么」而不只是「改了什么」。
- 确保 `testDebugUnitTest` 与 `assembleDebug` 通过。
- 不提交 `release/`、APK、签名文件、`local.properties` 或个人配置。
- UI 改动请附截图或录屏。

提交信息建议使用简短的祈使句，例如：

```text
fix: 修复漫画图片 Referer 丢失
feat: 支持图片型 EPUB 漫画
docs: 更新 1.2.0 更新日志
```

## 许可

提交的代码默认以 [MIT](LICENSE) 许可发布。

