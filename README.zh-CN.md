<p align="center">
  <img src="proxy_switcher_vpn_logox1254.png" width="120" alt="Proxy Switcher VPN" />
</p>

<h1 align="center">Proxy Switcher VPN</h1>

<p align="center">
  <a href="https://github.com/hightemp/proxy_switcher_vpn/releases/latest"><img src="https://img.shields.io/github/v/release/hightemp/proxy_switcher_vpn" alt="GitHub release (latest SemVer)" /></a>
  <a href="https://github.com/hightemp/proxy_switcher_vpn/releases"><img src="https://img.shields.io/github/downloads/hightemp/proxy_switcher_vpn/total" alt="GitHub all releases" /></a>
  <a href="https://github.com/hightemp/proxy_switcher_vpn/actions/workflows/android-ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/hightemp/proxy_switcher_vpn/android-ci.yml?branch=main&amp;label=ci" alt="Android CI" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue" alt="License: GPL-3.0-or-later" /></a>
  <a href="https://developer.android.com/about/versions/nougat"><img src="https://img.shields.io/badge/android-7.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 7.0+" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/kotlin-2.0.0-7F52FF?logo=kotlin&amp;logoColor=white" alt="Kotlin" /></a>
  <img src="https://asdertasd.site/counter/proxy_switcher_vpn" alt="Statistics" />
</p>

<p align="center"><a href="README.md">English</a> · <a href="README.ru.md">Русский</a> · <a href="README.uk.md">Українська</a> · <a href="README.de.md">Deutsch</a> · <a href="README.fr.md">Français</a> · <a href="README.es.md">Español</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.pl.md">Polski</a> · <b>简体中文</b> · <a href="README.ja.md">日本語</a> · <a href="README.ko.md">한국어</a></p>

一款 Android 应用：运行本地 VPN，将设备流量通过所选的上游代理转发，或使用明确选择的直连模式。

本应用不会修改 Android 系统代理设置，而是使用 `VpnService` 和内置的 sing-box 内核。

## 截图

截取自 Android API 35 上的调试版应用（v0.4.1）。代理配置只使用保留的 TEST-NET 地址，不含任何凭据；代理检测结果为示例数据。

<table>
  <tr>
    <th>首页 — VPN 运行中</th>
    <th>代理配置</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="320" alt="首页 — VPN 运行中" /></td>
    <td align="center"><img src="docs/screenshots/proxies.png" width="320" alt="代理配置" /></td>
  </tr>
  <tr>
    <th>添加代理</th>
    <th>VPN 诊断</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/add-proxy.png" width="320" alt="添加代理" /></td>
    <td align="center"><img src="docs/screenshots/diagnostics.png" width="320" alt="VPN 诊断" /></td>
  </tr>
  <tr>
    <th colspan="2">筛选后的 VPN 日志</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/screenshots/logs.png" width="320" alt="筛选后的 VPN 日志" /></td>
  </tr>
</table>

## 语言

应用支持英语、俄语、乌克兰语、德语、法语、西班牙语、意大利语、葡萄牙语（巴西）、波兰语、简体中文、日语和韩语。默认跟随系统语言；也可以在“设置 → 语言”中选择，或在 Android 13 及以上版本的系统“应用语言”设置中选择。日志条目和诊断中的技术值保持英文，方便阅读错误报告。

## 限制

- 仅支持 IPv4；
- 明确不支持 IPv6；
- 默认拦截 UDP/443，防止 QUIC/HTTP3 绕过代理；
- 如果所选上游代理在重试后仍不可用，VPN 会以 fail-closed 方式停止；
- 直连模式需要明确选择，绝不会被悄悄用作后备方案；
- 代理密码在界面、日志和诊断中都会被遮盖。

## 保持 VPN 运行

Android（尤其是三星 One UI）可能会在后台终止应用进程。要保持 VPN 运行：

- 允许应用在后台运行（只要电池优化处于开启状态，首页就会显示带“允许”按钮的提示）；在三星设备上，还需将应用移出“休眠应用”；
- 可选：在 Android 的 VPN 设置中开启“始终开启的 VPN”（设置对话框 → 始终开启的 VPN）。配合“屏蔽未使用 VPN 的连接”即相当于 kill switch：VPN 未运行时，任何流量都不会离开设备。

如果在 VPN 开启时进程被终止，应用会在一两分钟内恢复 VPN。诊断页会显示上次 VPN 停止的原因和上次应用退出的原因。

## 构建

```bash
make test
make build-local
```

首次构建会把固定版本的 sing-box/libbox AAR（约 97 MB）下载到用户的 Gradle 缓存中。`gradle/libbox.properties` 固定了源码版本、不可变的制品 URL、文件大小和 SHA-256。每次构建都会在编译前校验哈希和所需的原生 ABI。手动下载：

```bash
make fetch-libbox
```

调试版 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接的设备：

```bash
make install
```

## 调试日志

应用会把去除敏感信息的运行日志写入应用内“日志”页面和 Android logcat：

```bash
adb logcat -s ProxySwitcherVPN
```

日志包括线路选择、代理测试、上游代理监控结果、重连尝试、退避等待、TUN 设置、引擎生命周期以及最终 fail-closed 停止的原因。代理密码会被遮盖。

## 发布

版本号读取自 `VERSION`。

```bash
make update-version
make release
```

`make release` 会更新 Gradle 的 `versionCode`/`versionName`，运行检查，构建发布版，创建提交和标签并推送标签。

签名发布需要 `keystore.properties`。不要提交任何密钥或 keystore。

## 许可证

Proxy Switcher VPN 采用 [GNU GPL v3 或更高版本](LICENSE) 授权。发布版内置 sing-box/libbox，因此分发者还必须保留第三方声明，并按 GPLv3 要求提供完全对应的源代码。参见 [第三方声明](THIRD_PARTY_NOTICES.md) 和 [依赖许可证审计](docs/legal/dependency-license-audit.md)。
