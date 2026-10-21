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

<p align="center"><a href="README.md">English</a> · <a href="README.ru.md">Русский</a> · <a href="README.uk.md">Українська</a> · <a href="README.de.md">Deutsch</a> · <a href="README.fr.md">Français</a> · <a href="README.es.md">Español</a> · <a href="README.it.md">Italiano</a> · <b>Português (Brasil)</b> · <a href="README.pl.md">Polski</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.ja.md">日本語</a> · <a href="README.ko.md">한국어</a></p>

App Android que executa uma VPN local e encaminha o tráfego do dispositivo
pelo proxy upstream selecionado ou pelo modo Direto escolhido explicitamente.

O app não altera as configurações de proxy do sistema Android. Ele usa
`VpnService` e um núcleo sing-box embutido.

## Capturas de tela

Feitas no app de depuração (v0.4.1) no Android API 35. Os perfis de proxy usam
apenas endereços TEST-NET reservados e não contêm credenciais; os resultados das
verificações de proxy são dados de exemplo.

<table>
  <tr>
    <th>Início — VPN ativa</th>
    <th>Perfis de proxy</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="320" alt="Início — VPN ativa" /></td>
    <td align="center"><img src="docs/screenshots/proxies.png" width="320" alt="Perfis de proxy" /></td>
  </tr>
  <tr>
    <th>Adicionar proxy</th>
    <th>Diagnóstico de VPN</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/add-proxy.png" width="320" alt="Adicionar proxy" /></td>
    <td align="center"><img src="docs/screenshots/diagnostics.png" width="320" alt="Diagnóstico de VPN" /></td>
  </tr>
  <tr>
    <th colspan="2">Registros de VPN filtrados</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/screenshots/logs.png" width="320" alt="Registros de VPN filtrados" /></td>
  </tr>
</table>

## Idiomas

O app está disponível em inglês, russo, ucraniano, alemão, francês,
espanhol, italiano, português (Brasil), polonês, chinês (simplificado),
japonês e coreano. Ele segue o idioma do sistema; você também pode escolher
um idioma em Configurações → Idioma ou, no Android 13+, nas configurações de
idioma por app do sistema. As entradas de registro e os valores técnicos do
diagnóstico continuam em inglês para facilitar a leitura de relatórios de
bug.

## Limitações

- somente IPv4;
- IPv6 explicitamente não é compatível;
- UDP/443 é bloqueado por padrão para evitar que QUIC/HTTP3 contorne o proxy;
- se o proxy upstream selecionado continuar indisponível após as tentativas,
  a VPN para em modo fail-closed;
- o modo Direto é explícito e nunca é usado como alternativa silenciosa;
- as senhas dos proxies são mascaradas na interface, nos registros e no
  diagnóstico.

## Manter a VPN funcionando

O Android, e o Samsung One UI em especial, pode encerrar o processo do app em
segundo plano. Para manter a VPN ativa:

- permita que o app rode em segundo plano (enquanto a otimização de bateria
  estiver ativa, o Início mostra um aviso com "Permitir"); no Samsung, remova
  também o app de "Apps em suspensão";
- se quiser, ative a VPN sempre ativa nas configurações de VPN do Android
  (caixa Configurações → VPN sempre ativa). Junto com "Bloquear conexões sem
  VPN", isso funciona como kill switch: nenhum tráfego sai do dispositivo
  enquanto a VPN estiver fora do ar.

Se o processo for encerrado com a VPN ativada, o app restaura a VPN em um ou
dois minutos. O diagnóstico mostra o motivo da última parada da VPN e do
último encerramento do app.

## Compilação

```bash
make test
make build-local
```

A primeira compilação baixa o AAR fixado do sing-box/libbox (cerca de 97 MB)
para o cache do Gradle do usuário. `gradle/libbox.properties` fixa a revisão
do código-fonte, a URL imutável do artefato, o tamanho e o SHA-256. Cada
compilação verifica o hash e as ABIs nativas necessárias antes de compilar.
Para baixá-lo explicitamente:

```bash
make fetch-libbox
```

APK de depuração:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instalar em um dispositivo conectado:

```bash
make install
```

## Registros de depuração

O app grava registros de execução sem dados sensíveis na tela Registros e no
Android logcat:

```bash
adb logcat -s ProxySwitcherVPN
```

Os registros incluem a seleção de rota, os testes de proxy, os resultados do
monitoramento do proxy, as tentativas de reconexão, as esperas, a
configuração do TUN, o ciclo de vida do mecanismo e os motivos finais das
paradas fail-closed. As senhas dos proxies são mascaradas.

## Lançamento

A versão é lida de `VERSION`.

```bash
make update-version
make release
```

`make release` atualiza `versionCode`/`versionName` no Gradle, executa as
verificações, compila a versão, cria commit e tag e envia a tag.

Uma versão assinada exige `keystore.properties`. Não faça commit de segredos
nem do keystore.

## Licença

Proxy Switcher VPN é distribuído sob a
[GNU GPL v3 ou posterior](LICENSE). As versões incluem sing-box/libbox, então
quem distribuir também deve manter os avisos de terceiros e fornecer o
código-fonte correspondente exato exigido pela GPLv3. Veja os
[avisos de terceiros](THIRD_PARTY_NOTICES.md) e a
[auditoria de licenças das dependências](docs/legal/dependency-license-audit.md).
