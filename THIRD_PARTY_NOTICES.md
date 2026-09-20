# Third-Party Notices

Proxy Switcher VPN is licensed under `GPL-3.0-or-later`. Third-party
components remain under their own licenses. This file is an attribution index;
the detailed, versioned audit and the remaining release obligations are in
[`docs/legal/dependency-license-audit.md`](docs/legal/dependency-license-audit.md).

## sing-box and libbox

The distributed app embeds `libbox.aar`, built from sing-box `v1.13.13`, commit
`83b73048ff772b919af18653b78ffeaa2d48b66e`.
The AAR is downloaded and verified during the build as configured in
`gradle/libbox.properties`; this delivery mechanism does not change its
license.

- Project: https://github.com/SagerNet/sing-box
- License at the exact revision:
  https://github.com/SagerNet/sing-box/blob/v1.13.13/LICENSE
- License: `GPL-3.0-or-later`, with the upstream additional naming and
  non-association term.
- Copyright: Copyright (C) 2022 by nekohasekai.

This application is an independent project. It is not an official sing-box or
SagerNet application and does not imply association with those projects.

The native binary also includes GPL-3.0-or-later SagerNet modules from the
`sing`, `sing-*`, `cronet-go`, and `fswatch` families, plus
`github.com/anytls/sing-anytls` (Copyright (C) 2025 anytls). Exact module
versions are recorded in the audit.

## Other Native Dependencies

The embedded Go modules include software under Apache-2.0, BSD-2-Clause,
BSD-3-Clause, MIT, ISC, MPL-2.0, CC0-1.0, and the Unlicense. Notable notices
include:

- `github.com/hashicorp/yamux` under MPL-2.0, Copyright (c) 2014 HashiCorp,
  Inc.
- `github.com/sagernet/tailscale` under BSD-3-Clause, Copyright (c) 2020
  Tailscale Inc & AUTHORS.
- `google.golang.org/grpc` under Apache-2.0, Copyright 2014 gRPC authors.

The audit checks all four packaged `libbox.so` files. Each ABI records 89
dependency modules; their union contains 92 module/version coordinates because
Cronet uses a separate architecture-specific module. The audit also identifies
nested license files that a release notice bundle must retain.

## Android and Kotlin Dependencies

The packaged Android/Kotlin dependency families are predominantly
Apache-2.0: AndroidX and Compose, Dagger/Hilt, Kotlin, kotlinx coroutines,
kotlinx serialization, Okio, JetBrains annotations, `javax.inject`, Guava
ListenableFuture, and `com.google.code.findbugs:jsr305`.

JUnit 4 (EPL-1.0) and Hamcrest (BSD-3-Clause) are test-only and are not part of
the release APK. Gradle, Android Gradle Plugin, KSP, and the other build plugins
are build-time tools and are not shipped as application runtime code.
