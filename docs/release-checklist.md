# 发版检查清单

每次发版都必须同时产出并上传两个 APK：

- 通用包：`release/YuanyueReader-<版本>.apk`
- 32 位包：`release/YuanyueReader-<版本>-armeabi-v7a.apk`

## 构建

```bash
# 通用包
./gradlew clean :app:testDebugUnitTest :app:assembleRelease
cp app/build/outputs/apk/release/app-release.apk release/YuanyueReader-<版本>.apk

# 32 位包（armeabi-v7a）
./gradlew :app:assembleRelease -Pabi32
cp app/build/outputs/apk/release/app-armeabi-v7a-release.apk \
   release/YuanyueReader-<版本>-armeabi-v7a.apk
```

## 校验

```bash
aapt dump badging release/YuanyueReader-<版本>.apk | head -n 1
aapt dump badging release/YuanyueReader-<版本>-armeabi-v7a.apk | head -n 1
# 32 位包应只包含：native-code: 'armeabi-v7a'
apksigner verify --verbose release/YuanyueReader-<版本>-armeabi-v7a.apk
sha256sum release/YuanyueReader-<版本>*.apk
```

两个包的 `versionCode`、`versionName` 必须一致。

## GitHub Release

- 创建对应 tag（如 `v1.3.1`）
- 同时上传通用包和 32 位包
- Release 说明中列出两个包的名称、大小、SHA-256

## 蓝奏云

默认上传到蓝奏云「原阅」文件夹：

- `https://pc.woozooo.com/mydisk.php`
- 同时上传通用包和 32 位包

当前环境若没有蓝奏云登录态，先在浏览器登录后按本清单手动上传；不要只上传通用包。
