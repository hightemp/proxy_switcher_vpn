# Proxy Switcher VPN Tasks

Status legend: `pending`, `in_progress`, `done`, `blocked`.

Execution rule: keep each task small. Before changing code, read `AGENTS.md`, `PRD.md`, and only the relevant task section. Treat `/home/hightemp/Projects/proxy_switcher/proxy_switcher` as read-only.

## Phase 0: Repository Bootstrap From Reference Project

### TASK-000: Document Current Baseline

- Status: pending
- Goal: Capture the current new repository state before copying patterns from the reference project.
- Context files to inspect:
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `app/src/main/AndroidManifest.xml`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/AGENTS.md`
- Files likely to create/change:
  - `TASKS.md`
- Implementation notes:
  - Record that the new app currently contains a minimal Compose Android template.
  - Do not implement app code in this task.
- Acceptance criteria:
  - Baseline notes are added to the task status or implementation notes.
  - No application code is changed.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - none
- Estimated risk: low

### TASK-001: Compare Reference Project Structure

- Status: pending
- Goal: Identify the minimal reference files/patterns to copy or adapt.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/build.gradle.kts`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/gradle/libs.versions.toml`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/MainActivity.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/HomeScreen.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/data/local/ProxyEntity.kt`
- Files likely to create/change:
  - `TASKS.md`
- Implementation notes:
  - Use targeted file reads. Do not scan the whole reference project unless needed.
  - List concrete files to adapt in later phases.
- Acceptance criteria:
  - Later phases reference concrete source and destination files.
  - Reference project remains unchanged.
- Test/smoke commands:
  - none
- Dependencies:
  - TASK-000
- Estimated risk: low

## Phase 1: Branding/Package Rename/Project Setup

### TASK-010: Align Build Stack With Reference

- Status: pending
- Goal: Add the reference app stack to the new project without changing app behavior.
- Context files to inspect:
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/build.gradle.kts`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/gradle/libs.versions.toml`
- Files likely to create/change:
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
- Implementation notes:
  - Add Hilt, KSP, Room, Navigation Compose, Material icons.
  - Keep namespace/applicationId as `com.hightemp.proxy_switcher_vpn`.
  - Keep minSdk 24.
- Acceptance criteria:
  - Gradle sync/build succeeds.
  - Hilt, Room, and Navigation dependencies are available.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-001
- Estimated risk: medium

### TASK-011: Add Application Class And Manifest Wiring

- Status: pending
- Goal: Add Hilt application setup and required baseline manifest declarations.
- Context files to inspect:
  - `app/src/main/AndroidManifest.xml`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/AndroidManifest.xml`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ProxySwitcherApp.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ProxySwitcherVpnApp.kt`
  - `app/src/main/AndroidManifest.xml`
- Implementation notes:
  - Use `@HiltAndroidApp`.
  - Do not add VPN service implementation yet beyond manifest placeholders required by later tasks.
- Acceptance criteria:
  - App launches with Hilt application class.
  - No VPN code is implemented early.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-010
- Estimated risk: low

### TASK-012: Establish Package Structure

- Status: pending
- Goal: Create package directories matching the reference architecture.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/AGENTS.md`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/local/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/repository/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/di/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/`
- Implementation notes:
  - Add directories as part of real files in later tasks if empty directories are not tracked.
  - Do not create placeholder code with no consumer unless needed for build.
- Acceptance criteria:
  - Future task file locations are clear.
  - No broad copy of reference code.
- Test/smoke commands:
  - none
- Dependencies:
  - TASK-010
- Estimated risk: low

## Phase 2: Data Model And Proxy Storage

### TASK-020: Implement Proxy Entity And DAO

- Status: pending
- Goal: Store SOCKS5/HTTP/HTTPS proxies in Room.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/data/local/ProxyEntity.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/data/local/ProxyDao.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/local/ProxyEntity.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/local/ProxyDao.kt`
- Implementation notes:
  - Include `ProxyType.HTTP`, `ProxyType.HTTPS`, `ProxyType.SOCKS5`.
  - Include optional auth fields.
  - Decide whether password is stored encrypted now or represented through a secure storage abstraction.
- Acceptance criteria:
  - Room entity and DAO compile.
  - DAO supports Flow list reads, id lookup, insert, update, delete.
  - Passwords are not logged by tests or toString usage.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-010
- Estimated risk: medium

### TASK-021: Implement AppDatabase And DI Module

- Status: pending
- Goal: Provide Room database and DAO through Hilt.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/data/local/AppDatabase.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/di/DatabaseModule.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/local/AppDatabase.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/di/DatabaseModule.kt`
- Implementation notes:
  - Use database name specific to this app, such as `proxy_switcher_vpn.db`.
  - Start at schema version 1.
- Acceptance criteria:
  - Hilt can provide database and DAO.
  - Build succeeds with KSP.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-020
- Estimated risk: low

### TASK-022: Implement Proxy Repository

- Status: pending
- Goal: Provide a thin repository layer around the DAO.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/data/repository/ProxyRepository.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/repository/ProxyRepository.kt`
- Implementation notes:
  - Keep style close to the reference repository.
  - Add selected proxy persistence only if this task remains small; otherwise defer to TASK-023.
- Acceptance criteria:
  - Repository exposes proxy list Flow and CRUD methods.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-021
- Estimated risk: low

### TASK-023: Add App Settings Storage

- Status: pending
- Goal: Persist selected proxy and privacy/logging settings.
- Context files to inspect:
  - `PRD.md`
  - reference project settings approach if one exists.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/settings/AppSettings.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/settings/SettingsRepository.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/di/SettingsModule.kt`
- Implementation notes:
  - Use DataStore unless the reference project has a safer established pattern.
  - Store selected proxy id, domain logging enabled, and privacy disclosure state.
- Acceptance criteria:
  - Settings are observable as Flow.
  - Selected proxy survives process restart.
  - Domain/destination logging defaults to disabled until disclosure is handled.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-022
- Estimated risk: medium

## Phase 3: VPN Permission And Foreground Service Skeleton

### TASK-030: Add VPN Permission Flow Contract

- Status: pending
- Goal: Model the `VpnService.prepare()` permission flow without starting a real VPN yet.
- Context files to inspect:
  - `PRD.md` section 20.
  - Android `VpnService` documentation.
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/HomeScreen.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
- Implementation notes:
  - Use Activity Result API for VPN permission intent.
  - Keep UI state independent from real engine.
- Acceptance criteria:
  - START action requests VPN permission when needed.
  - UI receives permission result.
  - No real TUN or sing-box start yet.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-023
- Estimated risk: medium

### TASK-031: Create Foreground Service Skeleton

- Status: pending
- Goal: Add a foreground service shell for future VPN lifecycle.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/service/ProxyService.kt`
  - `app/src/main/AndroidManifest.xml`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnForegroundService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeState.kt`
  - `app/src/main/AndroidManifest.xml`
- Implementation notes:
  - Start foreground notification promptly.
  - Add `ACTION_START`, `ACTION_STOP`, status broadcast or StateFlow bridge.
  - Do not implement TUN setup yet.
- Acceptance criteria:
  - Service starts and stops.
  - Notification appears.
  - Runtime state updates.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual: start/stop service from UI on a device/emulator.
- Dependencies:
  - TASK-030
- Estimated risk: medium

### TASK-032: Add VpnService Subclass Skeleton

- Status: pending
- Goal: Introduce the Android `VpnService` component without a full packet/core pipeline.
- Context files to inspect:
  - Android `VpnService` documentation.
  - `app/src/main/AndroidManifest.xml`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/ProxyVpnService.kt`
  - `app/src/main/AndroidManifest.xml`
- Implementation notes:
  - Declare service with `android.permission.BIND_VPN_SERVICE`.
  - Keep lifecycle minimal and observable.
  - Decide whether this class also owns foreground notification or delegates to service abstraction.
- Acceptance criteria:
  - Android recognizes the VPN service.
  - Permission flow can target this service.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual: Android VPN permission dialog appears.
- Dependencies:
  - TASK-031
- Estimated risk: medium

## Phase 4: VPN Engine Abstraction

### TASK-040: Define Engine Interfaces And Fake Engine

- Status: pending
- Goal: Create testable engine interfaces before native/sing-box integration.
- Context files to inspect:
  - `PRD.md` sections 9, 10, 17, 18.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/VpnEngine.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/VpnEngineState.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/FakeVpnEngine.kt`
- Implementation notes:
  - Model start, stop, status Flow, logs Flow, counters Flow, and last error.
  - Include selected proxy and generated config in start request.
- Acceptance criteria:
  - Fake engine can simulate running, stopped, and error states.
  - Unit tests cover state transitions.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-032
- Estimated risk: low

### TASK-041: Define TUN Session Model

- Status: pending
- Goal: Model Android TUN configuration and lifecycle independent from sing-box.
- Context files to inspect:
  - Android `VpnService.Builder` documentation.
  - `PRD.md` sections 9 and 14.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/tun/TunConfig.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/tun/TunSession.kt`
- Implementation notes:
  - IPv4-only MVP.
  - Capture route strategy and DNS route strategy explicitly.
  - Include fields needed by sing-box integration.
- Acceptance criteria:
  - Tun config can be created without platform side effects.
  - Unit tests validate IPv4-only defaults and IPv6 unsupported state.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-040
- Estimated risk: medium

## Phase 5: SPIKE-001 sing-box Android Embedding Validation

### SPIKE-001: Validate sing-box Android Embedding

- Status: pending
- Goal: Verify whether and how sing-box can be embedded or run in this app.
- Context files to inspect:
  - `PRD.md` sections 10, 21, 22.
  - sing-box official source/docs.
  - sing-box Android client/core integration if used.
  - Android `VpnService` and `protect()` documentation.
- Files likely to create/change:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `TASKS.md`
  - optional throwaway prototype files under a clearly named spike/prototype path.
- Implementation notes:
  - This spike must be completed before full VPN implementation tasks.
  - Validate required ABIs: `arm64-v8a`, `x86_64`, optionally `armeabi-v7a`.
  - Compare embedding as Android library, native/JNI library, and embedded binary.
  - Validate how outbound sockets are protected from VPN loops.
  - Validate how logs/statistics/events are collected.
  - Validate license obligations.
- Acceptance criteria:
  - Documented recommended embedding approach.
  - Documented build steps and artifacts.
  - Proof of `VpnService` plus sing-box TUN start/stop.
  - Proof of SOCKS5 outbound.
  - Proof of HTTP outbound.
  - Proof of HTTPS proxy outbound using HTTP CONNECT over TLS.
  - Proof of DNS through proxy.
  - Proof of UDP/443 block behavior.
  - Proof or limitation note for logs/statistics access.
  - GPL/license conclusion recorded.
  - Follow-up tasks adjusted if the selected approach differs from current plan.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual device/emulator VPN start/stop.
  - Manual SOCKS5/HTTP/HTTPS proxy verification.
  - Manual DNS leak check.
  - Manual UDP/443/QUIC block check.
- Dependencies:
  - TASK-040
  - TASK-041
- Estimated risk: high

## Phase 6: sing-box Config Generator

### TASK-060: Create Config Model And Serializer

- Status: pending
- Goal: Generate sing-box config from Kotlin models.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - sing-box config documentation.
  - `PRD.md` sections 10, 11, 12, 13.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfig.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Use a structured JSON serializer if available.
  - Include secret masking helper.
- Acceptance criteria:
  - Valid JSON config generated for fake selected proxy.
  - Masked preview hides username/password where needed.
  - Unit tests cover config shape.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - SPIKE-001
- Estimated risk: medium

### TASK-061: Generate TUN Inbound And Route Defaults

- Status: pending
- Goal: Add MVP TUN inbound, IPv4 routing, and default route behavior to generated config.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - sing-box TUN inbound docs.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Keep IPv6 unsupported/disabled explicit.
  - Add route tags/constants for proxy, direct, block, and DNS.
- Acceptance criteria:
  - Generated config includes TUN inbound as validated by spike.
  - IPv4-only defaults are covered by tests.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-060
- Estimated risk: medium

## Phase 7: Upstream Proxy Config: SOCKS5/HTTP/HTTPS

### TASK-070: Add SOCKS5 Outbound Generation

- Status: pending
- Goal: Generate sing-box `socks` outbound for SOCKS5 proxies.
- Context files to inspect:
  - sing-box `socks` outbound docs.
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/local/ProxyEntity.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Support no auth and username/password auth.
- Acceptance criteria:
  - Unit tests cover SOCKS5 without auth and with auth.
  - Passwords are masked in preview.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-061
- Estimated risk: low

### TASK-071: Add HTTP Outbound Generation

- Status: pending
- Goal: Generate sing-box `http` outbound for HTTP proxies.
- Context files to inspect:
  - sing-box `http` outbound docs.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Support no auth and username/password auth.
- Acceptance criteria:
  - Unit tests cover HTTP without auth and with auth.
  - Passwords are masked in preview.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-061
- Estimated risk: low

### TASK-072: Add HTTPS Proxy Outbound Generation

- Status: pending
- Goal: Generate sing-box `http` outbound with TLS enabled for HTTPS proxy upstreams.
- Context files to inspect:
  - sing-box `http` outbound TLS docs.
  - `PRD.md` section 11.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - HTTPS proxy means HTTP CONNECT over TLS to the proxy server.
  - Support no auth and username/password auth.
- Acceptance criteria:
  - Unit tests cover HTTPS without auth and with auth.
  - Generated config enables TLS exactly as validated in SPIKE-001.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-061
- Estimated risk: medium

## Phase 8: DNS Through Proxy

### TASK-080: Add DNS Config Generation

- Status: pending
- Goal: Ensure generated config routes DNS through a proxy-safe path.
- Context files to inspect:
  - sing-box DNS docs.
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `PRD.md` section 12.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/dns/DnsMode.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Prefer DoH or DoT/TCP through selected outbound as validated by spike.
  - Avoid direct DNS default.
- Acceptance criteria:
  - Unit tests prove DNS route is not direct.
  - Diagnostics model can expose DNS mode.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-072
- Estimated risk: medium

### TASK-081: Add DNS Event Logging Adapter

- Status: pending
- Goal: Convert core DNS events/logs into app logs and counters.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `PRD.md` sections 17 and 18.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/AppLogger.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/events/VpnEvent.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/events/SingBoxEventParser.kt`
- Implementation notes:
  - Keep domain logging privacy setting in mind.
  - Mask sensitive values.
- Acceptance criteria:
  - DNS counters update.
  - DNS logs respect privacy/logging settings.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-080
- Estimated risk: medium

## Phase 9: UDP/443 Block And UDP Behavior

### TASK-090: Add UDP Policy Model

- Status: pending
- Goal: Make MVP UDP behavior explicit in code and diagnostics.
- Context files to inspect:
  - `PRD.md` section 13.
  - `docs/spikes/SPIKE-001-sing-box-android.md`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/udp/UdpPolicy.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/VpnDiagnostics.kt`
- Implementation notes:
  - Default: block UDP/443.
  - Other non-DNS UDP is blocked unless spike validates safe direct bypass.
- Acceptance criteria:
  - UDP policy is visible to config generator and diagnostics.
  - Unit tests cover default policy.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - SPIKE-001
- Estimated risk: medium

### TASK-091: Generate UDP/443 Block Rules

- Status: pending
- Goal: Add sing-box rules that block UDP/443 by default.
- Context files to inspect:
  - sing-box route/rule docs.
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/udp/UdpPolicy.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGenerator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Use exact config syntax validated in SPIKE-001.
  - Add counters/events if core exposes blocked UDP logs.
- Acceptance criteria:
  - Unit tests prove UDP/443 block rule exists.
  - Diagnostics show UDP/443 blocked.
- Test/smoke commands:
  - `./gradlew test`
  - Manual: verify QUIC/UDP/443 does not bypass proxy.
- Dependencies:
  - TASK-090
  - TASK-080
- Estimated risk: medium

## Phase 10: Statistics And Counters

### TASK-100: Add Runtime Counters Model

- Status: pending
- Goal: Track MVP counters independent from Android `TrafficStats`.
- Context files to inspect:
  - `PRD.md` section 18.
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/proxy/ProxyStats.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/stats/VpnStats.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/stats/VpnStatsStore.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/stats/VpnStatsStoreTest.kt`
- Implementation notes:
  - Include bytes in/out, total connections, failed connections, DNS queries, blocked UDP, bypassed UDP, uptime, last error.
- Acceptance criteria:
  - Counters update through events.
  - Uptime calculation is deterministic in tests.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-040
- Estimated risk: low

### TASK-101: Wire sing-box Stats/Event Source

- Status: pending
- Goal: Populate counters from sing-box logs/API/events where available.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/stats/VpnStatsStore.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/events/SingBoxEventParser.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/stats/VpnStatsStore.kt`
- Implementation notes:
  - If exact active connections are unavailable, expose unknown/not supported instead of fake precision.
- Acceptance criteria:
  - Available counters update from real or parsed engine events.
  - Unsupported exact stats are documented in diagnostics.
- Test/smoke commands:
  - `./gradlew test`
  - Manual traffic test while VPN runs.
- Dependencies:
  - TASK-100
  - SPIKE-001
- Estimated risk: high

## Phase 11: Logging

### TASK-110: Implement App Logger

- Status: pending
- Goal: Provide structured in-memory logs for UI.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/utils/AppLogger.kt`
  - `PRD.md` section 17.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/AppLogger.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/LogEntry.kt`
- Implementation notes:
  - Include timestamp, level, type, message.
  - Add filtering support.
  - Keep bounded in-memory history.
- Acceptance criteria:
  - Logs can be observed as Flow/StateFlow.
  - Logs can be cleared.
  - Secrets are masked.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-023
- Estimated risk: low

### TASK-111: Add Privacy Gate For Domain/Destination Logging

- Status: pending
- Goal: Make sensitive connection/domain logging user-visible and controllable.
- Context files to inspect:
  - `PRD.md` sections 17 and 19.
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/settings/SettingsRepository.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/AppLogger.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/data/settings/AppSettings.kt`
  - UI file selected for the disclosure/settings control.
- Implementation notes:
  - Default detailed domain/destination logging to off unless disclosure is accepted.
  - Generic event logging may remain enabled.
- Acceptance criteria:
  - Sensitive logs are suppressed when setting is disabled.
  - UI exposes disclosure or setting.
- Test/smoke commands:
  - `./gradlew test`
  - Manual: toggle setting and verify logs.
- Dependencies:
  - TASK-110
  - TASK-023
- Estimated risk: medium

## Phase 12: Home UI

### TASK-120: Build Home Screen Shell

- Status: pending
- Goal: Replace template UI with reference-style Home screen for VPN control.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/HomeScreen.kt`
  - `PRD.md` section 16.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
- Implementation notes:
  - Show selected proxy, VPN status, START/STOP VPN, bytes in/out, last error, quick diagnostics summary.
  - Do not include system proxy permission UI.
- Acceptance criteria:
  - Home screen compiles and navigates.
  - Button labels reference VPN, not proxy server/system proxy.
- Test/smoke commands:
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-030
  - TASK-100
- Estimated risk: medium

### TASK-121: Wire Home Screen To VPN Runtime

- Status: pending
- Goal: Connect Home UI to VPN permission, service, selected proxy, and stats state.
- Context files to inspect:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeState.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
- Implementation notes:
  - Disable START if no selected proxy or validation fails.
  - Show last error.
- Acceptance criteria:
  - User can request start/stop from Home.
  - UI state remains accurate across service status changes.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual: start/stop flow.
- Dependencies:
  - TASK-120
  - TASK-031
- Estimated risk: medium

## Phase 13: Proxy List/Add/Edit UI

### TASK-130: Port Proxy List Screen Pattern

- Status: pending
- Goal: Provide saved proxy list with selected indicator and CRUD actions.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/ProxyListScreen.kt`
  - `PRD.md` section 16.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/ProxyListScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
- Implementation notes:
  - Adapt naming to VPN.
  - Add active/selected indicator.
  - Test proxy action can be placeholder only if wired to a clear future task.
- Acceptance criteria:
  - Saved proxies list renders.
  - User can select, edit, and delete proxies.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual CRUD smoke.
- Dependencies:
  - TASK-022
  - TASK-023
- Estimated risk: medium

### TASK-131: Port Add/Edit Proxy Screen Pattern

- Status: pending
- Goal: Add form for SOCKS5/HTTP/HTTPS proxy creation and editing.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/AddEditProxyScreen.kt`
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/ProxyFormValidator.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/AddEditProxyScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/ProxyFormValidator.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/ui/screens/ProxyFormValidatorTest.kt`
- Implementation notes:
  - Validate host and port.
  - Username/password optional.
  - Avoid logging password.
- Acceptance criteria:
  - Form supports create and edit.
  - Validator tests cover invalid host, invalid port, and valid auth/no-auth cases.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-130
- Estimated risk: low

### TASK-132: Add Proxy Test Action

- Status: pending
- Goal: Test upstream proxy reachability without starting full VPN.
- Context files to inspect:
  - `PRD.md` section 6.
  - Reference proxy utilities if needed.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/proxy/ProxyTester.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/ProxyListScreen.kt`
- Implementation notes:
  - Keep timeout short.
  - Mask secrets in errors.
  - HTTPS proxy means CONNECT over TLS to proxy.
- Acceptance criteria:
  - User can run a test and see success/failure.
  - Failed test does not alter VPN state.
- Test/smoke commands:
  - `./gradlew test`
  - Manual with known proxy.
- Dependencies:
  - TASK-131
- Estimated risk: medium

## Phase 14: Logs UI

### TASK-140: Build Logs Screen

- Status: pending
- Goal: Show structured logs with filters and clear action.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/LogsScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/AppLogger.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/LogsScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
- Implementation notes:
  - Filters by level/type.
  - Include timestamps.
  - Keep UI dense and scan-friendly.
- Acceptance criteria:
  - Logs render and update live.
  - Clear logs works.
  - Filters work.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual: generate start/stop logs and filter.
- Dependencies:
  - TASK-110
- Estimated risk: low

## Phase 15: VPN Diagnostics UI

### TASK-150: Create Diagnostics Model

- Status: pending
- Goal: Consolidate VPN/core/TUN/DNS/UDP status for UI.
- Context files to inspect:
  - `PRD.md` sections 16, 17, 18.
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/VpnDiagnostics.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/VpnDiagnosticsRepository.kt`
- Implementation notes:
  - Include generated config preview with masked secrets.
  - Include unsupported IPv6 status.
- Acceptance criteria:
  - Diagnostics state is available as Flow/StateFlow.
  - Unit tests cover masked config preview.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-060
  - TASK-090
  - TASK-100
- Estimated risk: medium

### TASK-151: Build VPN Diagnostics Screen

- Status: pending
- Goal: Replace old System Proxy concept with VPN diagnostics UI.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/app/src/main/java/com/hightemp/proxy_switcher/ui/screens/SystemProxyScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/diagnostics/VpnDiagnostics.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/screens/VpnDiagnosticsScreen.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/MainActivity.kt`
- Implementation notes:
  - Do not copy system proxy text/actions.
  - Show permission, foreground service, sing-box, TUN, DNS, IPv4, IPv6, UDP, proxy, counters, last error, config preview.
- Acceptance criteria:
  - Diagnostics screen renders all MVP diagnostics.
  - No system proxy controls remain.
  - Secrets are masked.
- Test/smoke commands:
  - `./gradlew assembleDebug`
  - Manual: inspect diagnostics while stopped and running.
- Dependencies:
  - TASK-150
- Estimated risk: medium

## Phase 16: Error Handling And Lifecycle Hardening

### TASK-160: Implement Fail-Closed Upstream Failure Handling

- Status: pending
- Goal: Stop VPN and show error when selected upstream proxy fails.
- Context files to inspect:
  - `PRD.md` sections 7 and 11.
  - `docs/spikes/SPIKE-001-sing-box-android.md`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnForegroundService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/vpn/engine/`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/utils/AppLogger.kt`
- Implementation notes:
  - No silent direct TCP fallback.
  - Log last error.
  - Update UI state.
- Acceptance criteria:
  - Simulated upstream failure stops engine/service.
  - UI shows last error.
  - Logs contain failure.
- Test/smoke commands:
  - `./gradlew test`
  - Manual: start with unavailable proxy.
- Dependencies:
  - TASK-101
  - TASK-121
- Estimated risk: high

### TASK-161: Harden Start/Stop Race Conditions

- Status: pending
- Goal: Make rapid start/stop, permission cancellation, and process recreation predictable.
- Context files to inspect:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnForegroundService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
- Files likely to create/change:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnForegroundService.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeState.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
- Implementation notes:
  - Use explicit states: idle, permissionRequired, starting, running, stopping, stopped, error.
  - Avoid duplicate engine instances.
- Acceptance criteria:
  - Rapid taps do not create multiple engines.
  - Permission cancellation leaves app stopped.
  - Notification stop works.
- Test/smoke commands:
  - `./gradlew test`
  - Manual rapid start/stop.
- Dependencies:
  - TASK-160
- Estimated risk: high

## Phase 17: Tests

### TASK-170: Add Config Generator Test Suite

- Status: pending
- Goal: Cover all MVP sing-box config generation paths.
- Context files to inspect:
  - Existing config generator tests.
- Files likely to create/change:
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/vpn/singbox/SingBoxConfigGeneratorTest.kt`
- Implementation notes:
  - Cover SOCKS5/HTTP/HTTPS auth/no-auth.
  - Cover DNS through proxy.
  - Cover UDP/443 block.
  - Cover IPv4-only/IPv6 unsupported.
  - Cover secret masking.
- Acceptance criteria:
  - Tests fail on direct DNS default.
  - Tests fail if HTTPS proxy TLS flag is missing.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-091
- Estimated risk: low

### TASK-171: Add ViewModel And Runtime State Tests

- Status: pending
- Goal: Cover UI/runtime state behavior.
- Context files to inspect:
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModel.kt`
  - `app/src/main/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeState.kt`
- Files likely to create/change:
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/ui/viewmodel/VpnViewModelTest.kt`
  - `app/src/test/java/com/hightemp/proxy_switcher_vpn/service/VpnRuntimeStateTest.kt`
- Implementation notes:
  - Use fake repository/engine.
- Acceptance criteria:
  - Tests cover start disabled without selected proxy.
  - Tests cover permission cancellation.
  - Tests cover error state and last error.
- Test/smoke commands:
  - `./gradlew test`
- Dependencies:
  - TASK-161
- Estimated risk: medium

### TASK-172: Add Manual VPN QA Script

- Status: pending
- Goal: Create a repeatable manual checklist for real VPN behavior.
- Context files to inspect:
  - `PRD.md` section 23.
  - `docs/spikes/SPIKE-001-sing-box-android.md`
- Files likely to create/change:
  - `docs/qa/manual-vpn-checklist.md`
- Implementation notes:
  - Include device/emulator setup, proxy endpoints, DNS leak checks, UDP/443 checks, failure cases.
- Acceptance criteria:
  - Checklist covers all MVP manual tests.
  - Checklist includes expected results.
- Test/smoke commands:
  - Manual checklist execution.
- Dependencies:
  - TASK-160
- Estimated risk: low

## Phase 18: CI/Release

### TASK-180: Add Debug CI Workflow

- Status: pending
- Goal: Run build and unit tests on pushes/PRs.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/.github/workflows/release.yml`
- Files likely to create/change:
  - `.github/workflows/android-ci.yml`
- Implementation notes:
  - Use JDK 17.
  - Run unit tests and assembleDebug.
  - Add native artifact checks only after sing-box approach is chosen.
- Acceptance criteria:
  - CI builds debug APK and runs tests.
- Test/smoke commands:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- Dependencies:
  - TASK-010
- Estimated risk: low

### TASK-181: Add Release Workflow

- Status: pending
- Goal: Add signed APK release flow similar to the reference project.
- Context files to inspect:
  - `/home/hightemp/Projects/proxy_switcher/proxy_switcher/.github/workflows/release.yml`
  - `app/build.gradle.kts`
- Files likely to create/change:
  - `.github/workflows/release.yml`
  - `app/build.gradle.kts`
  - `keystore.properties.example`
- Implementation notes:
  - Rename artifact to `proxy_switcher_vpn-${tag}.apk`.
  - Include native artifact checks if applicable.
- Acceptance criteria:
  - Release workflow builds signed APK for `v*.*.*` tags.
  - APK signature verification step exists.
- Test/smoke commands:
  - `./gradlew assembleRelease`
- Dependencies:
  - TASK-180
  - SPIKE-001
- Estimated risk: medium

## Phase 19: Manual QA Checklist

### TASK-190: Execute MVP Manual QA

- Status: pending
- Goal: Verify the complete MVP on a real device or emulator.
- Context files to inspect:
  - `docs/qa/manual-vpn-checklist.md`
  - `PRD.md` MVP scope.
- Files likely to create/change:
  - `docs/qa/results/`
  - `TASKS.md`
- Implementation notes:
  - Record Android version, device/emulator, proxy endpoints, and results.
- Acceptance criteria:
  - VPN permission flow passes.
  - Foreground notification passes.
  - SOCKS5/HTTP/HTTPS pass.
  - DNS through proxy passes.
  - UDP/443 block passes.
  - IPv6 unsupported/no leak behavior passes.
  - Upstream failure stops VPN.
  - Logs and diagnostics are useful.
- Test/smoke commands:
  - Manual checklist.
- Dependencies:
  - TASK-172
  - TASK-181
- Estimated risk: high

## Phase 20: Post-MVP Improvements

### TASK-200: Evaluate Full UDP Proxying

- Status: pending
- Goal: Decide whether full UDP proxying is feasible and safe.
- Context files to inspect:
  - `docs/spikes/SPIKE-001-sing-box-android.md`
  - sing-box UDP support docs.
- Files likely to create/change:
  - `PRD.md`
  - `TASKS.md`
  - future UDP implementation files.
- Implementation notes:
  - Do not enable by default without leak tests.
- Acceptance criteria:
  - Decision documented.
  - Follow-up tasks created if feasible.
- Test/smoke commands:
  - Manual UDP tests.
- Dependencies:
  - TASK-190
- Estimated risk: high

### TASK-201: Evaluate IPv6 Support

- Status: pending
- Goal: Plan IPv6 support after MVP.
- Context files to inspect:
  - `PRD.md` section 14.
  - sing-box IPv6/TUN docs.
- Files likely to create/change:
  - `PRD.md`
  - `TASKS.md`
  - future IPv6 implementation files.
- Implementation notes:
  - Require DNS, TCP, UDP, and leak tests.
- Acceptance criteria:
  - IPv6 plan is documented with tests and risks.
- Test/smoke commands:
  - Manual IPv6 leak checks.
- Dependencies:
  - TASK-190
- Estimated risk: high

### TASK-202: Evaluate Split Tunneling And Per-App Routing

- Status: pending
- Goal: Plan post-MVP per-app include/exclude routing.
- Context files to inspect:
  - Android `VpnService.Builder` app allow/disallow docs.
- Files likely to create/change:
  - `PRD.md`
  - `TASKS.md`
- Implementation notes:
  - Keep UX explicit and avoid accidental bypass.
- Acceptance criteria:
  - Split tunneling scope and risks documented.
- Test/smoke commands:
  - Manual per-app route tests.
- Dependencies:
  - TASK-190
- Estimated risk: medium
