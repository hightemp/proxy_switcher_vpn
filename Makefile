VERSION := $(shell tr -d '[:space:]' < VERSION)

MAJOR := $(shell echo "$(VERSION)" | cut -d. -f1)
MINOR := $(shell echo "$(VERSION)" | cut -d. -f2)
PATCH := $(shell echo "$(VERSION)" | cut -d. -f3)

VERSION_CODE := $(shell echo "$$(( $(MAJOR) * 10000 + $(MINOR) * 100 + $(PATCH) ))")

APP_ID := com.hightemp.proxy_switcher_vpn
APP_NAME := proxy_switcher_vpn
GRADLE_FILE := app/build.gradle.kts
TAG := v$(VERSION)

.PHONY: help release tag update-version verify test check ci build-local build-debug build-release install install-release clean keystore adb-stop adb-vpn-status adb-logcat _commit _check-version _check-clean _check-keystore

help:
	@echo ""
	@echo "  make release          Update Gradle version, commit, tag and push"
	@echo "  make tag              Create and push git tag only"
	@echo "  make update-version   Update versionCode/versionName in app/build.gradle.kts"
	@echo "  make verify           Verify embedded libbox artifact"
	@echo "  make test             Run unit tests"
	@echo "  make check            Run libbox verification and unit tests"
	@echo "  make ci               Run local CI checks and debug APK build"
	@echo "  make build-local      Build debug APK locally"
	@echo "  make build-debug      Alias for build-local"
	@echo "  make build-release    Build SIGNED release APK locally"
	@echo "  make install          Build and install debug APK on connected device"
	@echo "  make install-release  Build and install SIGNED release APK on connected device"
	@echo "  make clean            Clean Gradle outputs"
	@echo "  make keystore         Generate release.keystore and keystore.properties"
	@echo "  make adb-stop         Force-stop the VPN app on connected device"
	@echo "  make adb-vpn-status   Show VPN/connectivity state from connected device"
	@echo "  make adb-logcat       Follow app, libbox, and sing-box logcat lines"
	@echo ""
	@echo "  Current: VERSION=$(VERSION)  versionCode=$(VERSION_CODE)  tag=$(TAG)"
	@echo ""

release: _check-version _check-clean update-version check build-release _commit tag
	@echo ""
	@echo "Released $(TAG) (versionCode=$(VERSION_CODE))"

update-version: _check-version
	@echo "Setting versionName = \"$(VERSION)\"  versionCode = $(VERSION_CODE)"
	@sed -i 's/versionCode = [0-9]*/versionCode = $(VERSION_CODE)/' $(GRADLE_FILE)
	@sed -i 's/versionName = "[^"]*"/versionName = "$(VERSION)"/' $(GRADLE_FILE)
	@echo "$(GRADLE_FILE) updated"

_commit:
	@git diff --quiet $(GRADLE_FILE) VERSION || ( \
		git add $(GRADLE_FILE) VERSION && \
		git commit -m "chore: bump version to $(VERSION) (versionCode=$(VERSION_CODE))" && \
		git push origin HEAD \
	)

tag:
	@if git rev-parse "$(TAG)" >/dev/null 2>&1; then \
		echo "Tag $(TAG) already exists - skipping"; \
	else \
		git tag -a "$(TAG)" -m "Release $(TAG)"; \
		git push origin "$(TAG)"; \
		echo "Tag $(TAG) pushed - GitHub Actions triggered"; \
	fi

verify:
	./gradlew verifyLibboxArtifact

test:
	./gradlew test

check: verify test

ci: verify test build-local

build-local:
	./gradlew assembleDebug

build-debug: build-local

build-release: _check-keystore
	./gradlew clean verifyLibboxArtifact assembleRelease
	@echo "Signed APK: app/build/outputs/apk/release/$(APP_NAME)-$(TAG).apk after GitHub release rename"
	@echo "Local Gradle output: app/build/outputs/apk/release/app-release.apk"

install:
	./gradlew installDebug

install-release: _check-keystore
	./gradlew installRelease

clean:
	./gradlew clean

keystore:
	@if [ -f release.keystore ]; then \
		echo "release.keystore already exists - refusing to overwrite"; \
		exit 1; \
	fi
	keytool -genkey -v \
		-keystore release.keystore \
		-alias $(APP_NAME) \
		-keyalg RSA -keysize 2048 -validity 10000
	@if [ ! -f keystore.properties ]; then \
		cp keystore.properties.example keystore.properties; \
		echo "keystore.properties created from example - set KEYSTORE_FILE to release.keystore and fill passwords"; \
	fi

adb-stop:
	adb shell am force-stop $(APP_ID)
	@echo "$(APP_ID) force-stopped"

adb-vpn-status:
	@echo "=== Activity services for $(APP_ID) ==="
	-adb shell dumpsys activity services $(APP_ID)
	@echo "=== Connectivity VPN references ==="
	-adb shell dumpsys connectivity | grep -iE "$(APP_ID)|vpn|tun0" | head -120

adb-logcat:
	adb logcat | grep --line-buffered -E "$(APP_ID)|Proxy Switcher VPN|libbox|sing-box"

_check-version:
	@if ! echo "$(VERSION)" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$$'; then \
		echo "VERSION must use MAJOR.MINOR.PATCH, got '$(VERSION)'"; \
		exit 1; \
	fi

_check-clean:
	@git diff --quiet || echo "Warning: uncommitted changes present"

_check-keystore:
	@if [ ! -f keystore.properties ]; then \
		echo "keystore.properties not found. Run 'make keystore' first or copy keystore.properties.example"; \
		exit 1; \
	fi
