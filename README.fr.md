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

<p align="center"><a href="README.md">English</a> · <a href="README.ru.md">Русский</a> · <a href="README.uk.md">Українська</a> · <a href="README.de.md">Deutsch</a> · <b>Français</b> · <a href="README.es.md">Español</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.pl.md">Polski</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.ja.md">日本語</a> · <a href="README.ko.md">한국어</a></p>

Application Android qui fait tourner un VPN local et achemine le trafic de
l’appareil via le proxy amont sélectionné ou via le mode Direct choisi
explicitement.

L’application ne modifie pas les paramètres de proxy système d’Android. Elle
utilise `VpnService` et un cœur sing-box intégré.

## Captures d’écran

Prises sur la vraie application de débogage sous Android API 35. Les profils
de proxy n’utilisent que des adresses TEST-NET réservées et ne contiennent
aucun identifiant.

<table>
  <tr>
    <th>Accueil — VPN actif</th>
    <th>Profils de proxy</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="320" alt="Accueil — VPN actif" /></td>
    <td align="center"><img src="docs/screenshots/proxies.png" width="320" alt="Profils de proxy" /></td>
  </tr>
  <tr>
    <th>Ajout d’un proxy</th>
    <th>Diagnostic VPN</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/add-proxy.png" width="320" alt="Ajout d’un proxy" /></td>
    <td align="center"><img src="docs/screenshots/diagnostics.png" width="320" alt="Diagnostic VPN" /></td>
  </tr>
  <tr>
    <th colspan="2">Journaux VPN filtrés</th>
  </tr>
  <tr>
    <td colspan="2" align="center"><img src="docs/screenshots/logs.png" width="320" alt="Journaux VPN filtrés" /></td>
  </tr>
</table>

## Langues

L’application est disponible en anglais, russe, ukrainien, allemand,
français, espagnol, italien, portugais (Brésil), polonais, chinois
(simplifié), japonais et coréen. Elle suit la langue du système ; vous pouvez
aussi choisir une langue dans Paramètres → Langue ou, sous Android 13+, dans
les paramètres de langue par application du système. Les entrées de journal
et les valeurs techniques du diagnostic restent en anglais pour que les
rapports de bug soient faciles à lire.

## Limites

- IPv4 uniquement ;
- IPv6 n’est explicitement pas pris en charge ;
- UDP/443 est bloqué par défaut pour éviter que QUIC/HTTP3 contourne le
  proxy ;
- si le proxy amont sélectionné reste indisponible après les nouvelles
  tentatives, le VPN s’arrête en mode fail-closed ;
- le mode Direct est choisi explicitement et n’est jamais utilisé en secours
  silencieux ;
- les mots de passe des proxys sont masqués dans l’interface, les journaux et
  le diagnostic.

## Garder le VPN actif

Android, et Samsung One UI en particulier, peut arrêter le processus de
l’application en arrière-plan. Pour garder le VPN actif :

- autorisez l’application à fonctionner en arrière-plan (l’accueil affiche un
  avertissement « Autoriser » tant que l’optimisation de la batterie est
  active) ; sur Samsung, retirez-la aussi des « Applications en veille » ;
- si vous le souhaitez, activez le VPN permanent dans les paramètres VPN
  d’Android (boîte de dialogue Paramètres → VPN permanent). Avec « Bloquer les
  connexions sans VPN », cela agit comme un kill switch : aucun trafic ne sort
  de l’appareil tant que le VPN est arrêté.

Si le processus est arrêté alors que le VPN est activé, l’application
restaure le VPN en une à deux minutes. Le diagnostic affiche le motif du
dernier arrêt du VPN et de la dernière fermeture de l’application.

## Compilation

```bash
make test
make build-local
```

La première compilation télécharge l’AAR sing-box/libbox épinglé (environ
97 Mo) dans le cache Gradle de l’utilisateur. `gradle/libbox.properties` fixe
la révision des sources, l’URL immuable de l’artefact, sa taille et son
SHA-256. Chaque compilation vérifie le hash et les ABI natives requises avant
de compiler. Pour le télécharger explicitement :

```bash
make fetch-libbox
```

APK de débogage :

```text
app/build/outputs/apk/debug/app-debug.apk
```

Installation sur un appareil connecté :

```bash
make install
```

## Journaux de débogage

L’application écrit des journaux d’exécution expurgés dans l’écran Journaux
et dans Android logcat :

```bash
adb logcat -s ProxySwitcherVPN
```

Les journaux contiennent la sélection d’itinéraire, les tests de proxy, les
résultats de la surveillance du proxy, les tentatives de reconnexion, les
délais d’attente, la configuration du TUN, le cycle de vie du moteur et les
motifs finaux des arrêts fail-closed. Les mots de passe des proxys sont
masqués.

## Publication

La version est lue depuis `VERSION`.

```bash
make update-version
make release
```

`make release` met à jour `versionCode`/`versionName` dans Gradle, lance les
vérifications, compile la version, crée un commit et un tag, puis pousse le
tag.

Une version signée nécessite `keystore.properties`. Ne committez ni secrets ni
keystore.

## Licence

Proxy Switcher VPN est distribué sous
[GNU GPL v3 ou ultérieure](LICENSE). Les versions intègrent sing-box/libbox :
les distributeurs doivent donc aussi conserver les mentions des composants
tiers et fournir le code source correspondant exact exigé par la GPLv3. Voir
les [mentions des composants tiers](THIRD_PARTY_NOTICES.md) et
l’[audit des licences des dépendances](docs/legal/dependency-license-audit.md).
