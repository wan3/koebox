# 签名说明

仓库**不包含**签名密钥，也不包含任何口令。`keystore/koebox.jks` 只在本地存在。

构建时会按下面的顺序找签名信息（见 `app/build.gradle.kts`）：

1. `local.properties`（不进仓库）里的 `keystore.path` / `keystore.password` / `keystore.keyPassword` / `keystore.alias`
2. 环境变量 `KOEBOX_STORE_PASSWORD` / `KOEBOX_KEY_PASSWORD`

两者都没有时，Release 包会自动退回调试签名（能装，但不能覆盖安装正式版）。

自己出正式包：

```bash
keytool -genkeypair -v -keystore keystore/my.jks -alias mykey \
  -keyalg RSA -keysize 2048 -validity 10000
```

然后在 `local.properties` 里写上路径和口令即可。
