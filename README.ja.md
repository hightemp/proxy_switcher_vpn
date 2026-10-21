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

<p align="center"><a href="README.md">English</a> · <a href="README.ru.md">Русский</a> · <a href="README.uk.md">Українська</a> · <a href="README.de.md">Deutsch</a> · <a href="README.fr.md">Français</a> · <a href="README.es.md">Español</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.pl.md">Polski</a> · <a href="README.zh-CN.md">简体中文</a> · <b>日本語</b> · <a href="README.ko.md">한국어</a></p>

ローカル VPN を実行し、端末のトラフィックを選択したアップストリームプロキシ、または明示的に選んだ直接接続モードで送る Android アプリです。

Android のシステムプロキシ設定は変更しません。`VpnService` と組み込みの sing-box コアを使用します。

## スクリーンショット

Android API 35 上のデバッグ版アプリ（v0.4.1）で撮影しました。プロキシのプロファイルには予約済みの TEST-NET アドレスのみを使い、認証情報は含みません。プロキシのチェック結果はサンプルデータです。

<table>
  <tr>
    <th>ホーム — VPN 実行中</th>
    <th>プロキシのプロファイル</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="320" alt="ホーム — VPN 実行中" /></td>
    <td align="center"><img src="docs/screenshots/proxies.png" width="320" alt="プロキシのプロファイル" /></td>
  </tr>
  <tr>
    <th>プロキシを追加</th>
    <th>VPN 診断</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/add-proxy.png" width="320" alt="プロキシを追加" /></td>
    <td align="center"><img src="docs/screenshots/diagnostics.png" width="320" alt="VPN 診断" /></td>
  </tr>
  <tr>
    <th colspan="2">絞り込んだ VPN ログ</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/screenshots/logs.png" width="320" alt="絞り込んだ VPN ログ" /></td>
  </tr>
</table>

## 言語

アプリは英語、ロシア語、ウクライナ語、ドイツ語、フランス語、スペイン語、イタリア語、ポルトガル語（ブラジル）、ポーランド語、中国語（簡体字）、日本語、韓国語に対応しています。システムの言語に従いますが、「設定 → 言語」や、Android 13 以降ではシステムのアプリ別言語設定でも選択できます。ログの内容と診断の技術的な値は、バグ報告を読みやすくするため英語のままです。

## 制限事項

- IPv4 のみ。
- IPv6 には明示的に対応していません。
- QUIC/HTTP3 による迂回を防ぐため、UDP/443 はデフォルトでブロックされます。
- 選択したアップストリームプロキシが再試行後も利用できない場合、VPN は fail-closed で停止します。
- 直接接続モードは明示的に選ぶもので、黙って代替手段として使われることはありません。
- プロキシのパスワードは画面、ログ、診断でマスクされます。

## VPN を動かし続けるには

Android、特に Samsung One UI は、バックグラウンドのアプリのプロセスを終了することがあります。VPN を維持するには:

- アプリのバックグラウンド実行を許可してください（電池の最適化が有効な間は、ホームに「許可」ボタン付きの警告が表示されます）。Samsung 端末では「スリープ中のアプリ」からも外してください。
- 必要に応じて、Android の VPN 設定で常時接続 VPN をオンにしてください（設定ダイアログ → 常時接続 VPN）。「VPN なしの接続をブロック」と組み合わせると kill switch として機能し、VPN が停止している間はトラフィックが端末から出ません。

VPN が有効なままプロセスが終了した場合、アプリは 1～2 分以内に VPN を復元します。診断画面には、最後に VPN が停止した理由と最後にアプリが終了した理由が表示されます。

## ビルド

```bash
make test
make build-local
```

初回のビルドでは、固定バージョンの sing-box/libbox AAR（約 97 MB）をユーザーの Gradle キャッシュにダウンロードします。`gradle/libbox.properties` には、ソースのリビジョン、変更されない成果物 URL、サイズ、SHA-256 が固定されています。各ビルドはコンパイル前にハッシュと必要なネイティブ ABI を検証します。手動でダウンロードするには:

```bash
make fetch-libbox
```

デバッグ版 APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

接続した端末へのインストール:

```bash
make install
```

## デバッグログ

アプリは機密情報を除いた実行ログを、アプリ内のログ画面と Android logcat に出力します:

```bash
adb logcat -s ProxySwitcherVPN
```

ログには、経路の選択、プロキシのテスト、アップストリームの監視結果、再接続の試行、待機時間、TUN の設定、エンジンのライフサイクル、最終的な fail-closed 停止の理由が含まれます。プロキシのパスワードはマスクされます。

## リリース

バージョンは `VERSION` から読み込まれます。

```bash
make update-version
make release
```

`make release` は Gradle の `versionCode`/`versionName` を更新し、チェックを実行してリリースをビルドし、コミットとタグを作成してタグをプッシュします。

署名付きリリースには `keystore.properties` が必要です。秘密情報や keystore はコミットしないでください。

## ライセンス

Proxy Switcher VPN は [GNU GPL v3 以降](LICENSE) でライセンスされています。リリースには sing-box/libbox が組み込まれているため、配布者はサードパーティの告知を保持し、GPLv3 が求める正確に対応するソースコードも提供する必要があります。[サードパーティの告知](THIRD_PARTY_NOTICES.md) と [依存関係のライセンス監査](docs/legal/dependency-license-audit.md) を参照してください。
