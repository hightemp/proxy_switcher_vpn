# Dependency License Audit

Audit date: 2026-09-20.

This is an engineering license inventory, not legal advice. It records the
licenses found in the dependency metadata and source trees used by this
checkout. A binary release still needs the distribution work listed under
"Release requirements and blockers".

## Decision

Proxy Switcher VPN is licensed under `GPL-3.0-or-later`.

The decisive dependency is the bundled sing-box/libbox native
library. The exact sing-box revision is `v1.13.13`, commit
`83b73048ff772b919af18653b78ffeaa2d48b66e`, and its license grants use under
GPL version 3 or any later version. It also adds a naming/non-association term;
the app must remain clearly independent and must not use the sing-box name as
its own product identity or imply upstream endorsement.

The audited permissive, Apache-2.0, and MPL-2.0 dependencies can be combined
with a GPLv3 work when their own notice and source obligations are preserved;
the audited yamux checkout has no applied "Incompatible With Secondary
Licenses" notice.
The EPL-1.0 dependency is JUnit and is test-only, so it is not linked into or
distributed with the release APK.

Primary references:

- sing-box license at the embedded revision:
  https://github.com/SagerNet/sing-box/blob/v1.13.13/LICENSE
- GPLv3 text and object-code source requirements:
  https://www.gnu.org/licenses/gpl-3.0.html
- GNU license compatibility overview:
  https://www.gnu.org/licenses/license-compatibility.html
- AndroidX Apache-2.0 license:
  https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt
- Kotlin license:
  https://github.com/JetBrains/kotlin/blob/v2.0.20/license/LICENSE.txt
- Dagger/Hilt license:
  https://github.com/google/dagger/blob/dagger-2.51.1/LICENSE.txt
- yamux MPL-2.0 license:
  https://github.com/hashicorp/yamux/blob/v0.1.2/LICENSE

## Evidence and Scope

The audit covered:

- declared dependencies and plugins in `gradle/libs.versions.toml` and
  `app/build.gradle.kts`;
- the resolved `releaseRuntimeClasspath` from Gradle in offline mode;
- test-only dependencies separately from shipped runtime dependencies;
- the libbox AAR pinned by `gradle/libbox.properties`, SHA-256
  `f8dbec0658177ef3310fec8c38d917d75e0db74a1565f88ef78a96df3e0a3905`;
- the Go build information embedded in all four packaged `libbox.so` files,
  including every recorded module and version;
- root license files from the corresponding Go module cache entries;
- the clean sing-box source checkout at the exact tagged revision.

Reproduce the main checks with:

```bash
./gradlew --offline --console=plain \
  :app:dependencies --configuration releaseRuntimeClasspath
./gradlew downloadLibboxArtifact
./gradlew verifyLibboxArtifact

audit_libbox_path=$(./gradlew -q printLibboxArtifactPath)
audit_tmp_dir=$(mktemp -d)
unzip -q "$audit_libbox_path" 'jni/*/libbox.so' -d "$audit_tmp_dir"
for audit_libbox_so in "$audit_tmp_dir"/jni/*/libbox.so; do
  go version -m "$audit_libbox_so"
done
rm -r "$audit_tmp_dir"

git -C tmp/sing-box rev-parse HEAD
git -C tmp/sing-box describe --tags --always --dirty
git -C tmp/sing-box status --short
```

## Gradle Runtime Dependencies

| Component family | Resolved/shipped role | License |
| --- | --- | --- |
| AndroidX, Compose, Material 3, Room, Navigation, DataStore | Application runtime and UI | Apache-2.0 |
| Dagger/Hilt and AndroidX Hilt | Dependency injection | Apache-2.0 |
| Kotlin standard library and JetBrains annotations | Language runtime/annotations | Apache-2.0 |
| kotlinx coroutines and kotlinx serialization | Runtime concurrency/JSON | Apache-2.0 |
| Okio | DataStore transitive runtime | Apache-2.0 |
| Guava ListenableFuture | AndroidX transitive runtime | Apache-2.0 |
| `javax.inject` | Dagger transitive runtime | Apache-2.0 |
| `com.google.code.findbugs:jsr305` | Hilt transitive annotations | Apache-2.0 |
| Pinned downloaded `libbox.aar` | Native VPN/proxy engine | GPL-3.0-or-later plus the dependencies below |

AndroidX POM constraints show several requested versions in the dependency
tree, but Gradle resolves one selected runtime version per module. License
classification is by project family because those artifacts share the same
upstream license.

## Test and Build Dependencies

| Component family | Scope | License |
| --- | --- | --- |
| JUnit 4.13.2 | Unit tests only | EPL-1.0 |
| Hamcrest | JUnit transitive test dependency | BSD-3-Clause |
| AndroidX Test and Espresso | Instrumentation tests only | Apache-2.0 |
| kotlinx-coroutines-test | Unit tests only | Apache-2.0 |
| Gradle 8.9 and Android Gradle Plugin 8.7.2 | Build tooling | Apache-2.0 |
| Kotlin/Compose plugins, KSP, Hilt plugin | Build tooling | Apache-2.0 |

These tools and test libraries do not change the license selected for the
release APK because they are not shipped as application runtime code.

## Embedded libbox Inventory

Each packaged ABI library reports sing-box itself plus 89 dependency modules.
There are 88 common modules and one architecture-specific Cronet module in each
binary, for 92 unique module/version coordinates across the whole AAR. Root
license-file classification of that union produced these totals:

| License family | Module count |
| --- | ---: |
| Apache-2.0 | 13 |
| BSD-2-Clause | 3 |
| BSD-3-Clause | 25 |
| CC0-1.0 | 1 |
| GPL-3.0-or-later | 16 |
| ISC | 1 |
| MIT | 31 |
| MPL-2.0 | 1 |
| Unlicense | 1 |

The GPL-3.0-or-later modules are:

- `github.com/anytls/sing-anytls@v0.0.11`
- `github.com/sagernet/cronet-go@v0.0.0-20260516035203-b3eec8134aec`
- `github.com/sagernet/cronet-go/all@v0.0.0-20260516035203-b3eec8134aec`
- `github.com/sagernet/cronet-go/lib/android_386@v0.0.0-20260516034431-d86a63399c27`
- `github.com/sagernet/cronet-go/lib/android_amd64@v0.0.0-20260516034431-d86a63399c27`
- `github.com/sagernet/cronet-go/lib/android_arm@v0.0.0-20260516034431-d86a63399c27`
- `github.com/sagernet/cronet-go/lib/android_arm64@v0.0.0-20260516034431-d86a63399c27`
- `github.com/sagernet/fswatch@v0.1.2`
- `github.com/sagernet/sing@v0.8.11-0.20260514110501-905ad103a4df`
- `github.com/sagernet/sing-mux@v0.3.4`
- `github.com/sagernet/sing-quic@v0.6.2-0.20260525051024-9467ede27fb7`
- `github.com/sagernet/sing-shadowsocks@v0.2.8`
- `github.com/sagernet/sing-shadowsocks2@v0.2.1`
- `github.com/sagernet/sing-shadowtls@v0.2.1`
- `github.com/sagernet/sing-tun@v0.8.11-0.20260603045801-6e76db79f94a`
- `github.com/sagernet/sing-vmess@v0.2.8-0.20250909125414-3aed155119a1`

The non-GPL modules are grouped below by the root license file found at the
recorded version.

### Apache-2.0

- `github.com/caddyserver/certmagic@v0.25.3-0.20260421143802-60d9d8b415d6`
- `github.com/golang/groupcache@v0.0.0-20210331224755-41bb18bfe9da`
- `github.com/google/btree@v1.1.3`
- `github.com/klauspost/compress@v1.18.0`
- `github.com/mholt/acmez/v3@v3.1.6`
- `github.com/pires/go-proxyproto@v0.8.1`
- `github.com/sagernet/gvisor@v0.0.0-20250811.0-sing-box-mod.1`
- `github.com/sagernet/netlink@v0.0.0-20240612041022-b9a21c07ac6a`
- `github.com/sagernet/nftables@v0.3.0-mod.2`
- `github.com/vishvananda/netns@v0.0.5`
- `go4.org/mem@v0.0.0-20240501181205-ae6ca9944745`
- `google.golang.org/genproto/googleapis/rpc@v0.0.0-20251202230838-ff82c1b0f217`
- `google.golang.org/grpc@v1.79.1`

### BSD-2-Clause

- `github.com/godbus/dbus/v5@v5.2.2`
- `github.com/pkg/sftp@v1.13.10`
- `github.com/tailscale/goupnp@v1.0.1-0.20210804011211-c64d0f06ea05`

### BSD-3-Clause

- `filippo.io/edwards25519@v1.1.0`
- `github.com/ajg/form@v1.5.1`
- `github.com/database64128/netx-go@v0.1.1`
- `github.com/fsnotify/fsnotify@v1.9.0`
- `github.com/go-json-experiment/json@v0.0.0-20250813024750-ebf49471dced`
- `github.com/hdevalence/ed25519consensus@v0.2.0`
- `github.com/kr/fs@v0.1.0`
- `github.com/metacubex/utls@v1.8.4`
- `github.com/miekg/dns@v1.1.72`
- `github.com/sagernet/gliderssh@v0.3.4-0.20260531100337-2194faca5648`
- `github.com/sagernet/gomobile@v0.1.12`
- `github.com/sagernet/tailscale@v1.92.4-sing-box-1.13-mod.7.0.20260527101438-dc40932c32d9`
- `github.com/tailscale/peercred@v0.0.0-20250107143737-35a0c7bd7edc`
- `go4.org/netipx@v0.0.0-20231129151722-fdeea329fbba`
- `golang.org/x/crypto@v0.48.0`
- `golang.org/x/exp@v0.0.0-20251219203646-944ab1f22d93`
- `golang.org/x/mod@v0.33.0`
- `golang.org/x/net@v0.50.0`
- `golang.org/x/oauth2@v0.34.0`
- `golang.org/x/sync@v0.19.0`
- `golang.org/x/sys@v0.41.0`
- `golang.org/x/term@v0.40.0`
- `golang.org/x/text@v0.34.0`
- `golang.org/x/time@v0.11.0`
- `google.golang.org/protobuf@v1.36.11`

### MIT

- `github.com/andybalholm/brotli@v1.1.0`
- `github.com/anmitsu/go-shlex@v0.0.0-20200514113438-38f4b401e2be`
- `github.com/caddyserver/zerossl@v0.1.5`
- `github.com/creack/pty@v1.1.24`
- `github.com/cretz/bine@v0.2.0`
- `github.com/database64128/tfo-go/v2@v2.3.2`
- `github.com/florianl/go-nfqueue/v2@v2.0.2`
- `github.com/fxamacker/cbor/v2@v2.7.0`
- `github.com/gaissmai/bart@v0.18.0`
- `github.com/go-chi/chi/v5@v5.2.5`
- `github.com/go-chi/render@v1.0.3`
- `github.com/gobwas/httphead@v0.1.0`
- `github.com/gobwas/pool@v0.2.1`
- `github.com/gofrs/uuid/v5@v5.4.0`
- `github.com/jsimonetti/rtnetlink@v1.4.0`
- `github.com/klauspost/cpuid/v2@v2.3.0`
- `github.com/libdns/libdns@v1.1.1`
- `github.com/mdlayher/netlink@v1.9.0`
- `github.com/mdlayher/socket@v0.5.1`
- `github.com/quic-go/qpack@v0.6.0`
- `github.com/sagernet/bbolt@v0.0.0-20231014093535-ea5cb2fe9f0a`
- `github.com/sagernet/cors@v1.2.1`
- `github.com/sagernet/quic-go@v0.59.0-sing-box-mod.4`
- `github.com/sagernet/smux@v1.5.50-sing-box-mod.1`
- `github.com/sagernet/wireguard-go@v0.0.3`
- `github.com/sagernet/ws@v0.0.0-20231204124109-acfe8907c854`
- `github.com/x448/float16@v0.8.4`
- `go.uber.org/multierr@v1.11.0`
- `go.uber.org/zap@v1.27.1`
- `go.uber.org/zap/exp@v0.3.0`
- `lukechampine.com/blake3@v1.3.0`

### Other License Families

- ISC: `github.com/coder/websocket@v1.8.14`
- MPL-2.0: `github.com/hashicorp/yamux@v0.1.2`
- CC0-1.0: `github.com/zeebo/blake3@v0.2.4`
- Unlicense: `github.com/logrusorgru/aurora@v2.0.3+incompatible`

Some modules contain additional nested license files, notably
`github.com/klauspost/compress` and `github.com/metacubex/utls`. The release
notice bundle must preserve applicable nested notices as well as root license
files. `google.golang.org/grpc` also ships `NOTICE.txt`.

## Release Requirements and Blockers

Selecting GPL-3.0-or-later and adding a root license does not by itself make an
APK distribution complete. Before the next release that embeds this AAR:

1. Make the exact corresponding source for the app, sing-box/libbox, included
   Go dependencies, and build scripts available with access equivalent to the
   APK. A link to a different server is allowed only while the distributor
   keeps that exact source available and gives clear directions next to the
   binary.
2. Ship the GPL text, third-party copyright/license notices, and any required
   NOTICE content with the binary distribution. Root repository files alone do
   not prove that APK recipients received them; package them in the app or as
   clearly adjacent release assets and expose a user-visible legal-notices
   entry where appropriate.
3. Preserve the sing-box additional naming/non-association term and do not
   present Proxy Switcher VPN as an official upstream application.
4. Resolve the Cronet source gap. The compiled inventory contains separate
   `github.com/sagernet/cronet-go/lib/android_{386,amd64,arm,arm64}` modules.
   Each contains a roughly 59-74 MB prebuilt `libcronet.a`, wrapper files, and
   a license, but no Chromium/Cronet source. Either obtain and publish the exact
   corresponding source, build scripts, and third-party notices for those
   archives, or rebuild libbox without the `with_naive_outbound`/Cronet path if
   that feature is not needed, then repeat this audit against the new AAR hash.
5. Regenerate the inventory whenever `gradle/libs.versions.toml`,
   `app/build.gradle.kts`, the libbox hash, sing-box build tags, or the sing-box
   revision changes.

Until items 1, 2, and 4 are resolved, the repository has a compatible project
license, but this audit does not declare a newly published APK fully compliant.
