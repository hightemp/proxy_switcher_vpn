import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun signingValue(key: String): String? {
    return keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }
}

val releaseKeystorePath = signingValue("KEYSTORE_FILE")
val releaseKeystoreFile = releaseKeystorePath?.let { rootProject.file(it) }
val releaseKeystorePassword = signingValue("KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("KEY_ALIAS")
val releaseKeyPassword = signingValue("KEY_PASSWORD")
val hasReleaseSigningConfig =
    releaseKeystoreFile != null &&
        releaseKeystorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

val libboxPropertiesFile = rootProject.file("gradle/libbox.properties")
val libboxProperties = Properties().apply {
    if (!libboxPropertiesFile.isFile) {
        throw GradleException("Missing libbox metadata at ${libboxPropertiesFile.path}.")
    }
    libboxPropertiesFile.inputStream().use(::load)
}

fun requiredLibboxProperty(name: String): String {
    return libboxProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: throw GradleException("Missing '$name' in ${libboxPropertiesFile.path}.")
}

val libboxVersion = requiredLibboxProperty("version")
val libboxSourceCommit = requiredLibboxProperty("sourceCommit")
val libboxSourceUrl = requiredLibboxProperty("sourceUrl")
val libboxArtifactCommit = requiredLibboxProperty("artifactCommit")
val libboxArtifactUrl = requiredLibboxProperty("artifactUrl")
val libboxArtifactUri = URI.create(libboxArtifactUrl)
val libboxSourceUri = URI.create(libboxSourceUrl)
val libboxExpectedSize = requiredLibboxProperty("size").toLongOrNull()?.takeIf { it > 0 }
    ?: throw GradleException("Invalid libbox byte size in ${libboxPropertiesFile.path}.")
val libboxExpectedSha256 = requiredLibboxProperty("sha256").lowercase()
if (libboxArtifactUri.scheme != "https" || libboxSourceUri.scheme != "https") {
    throw GradleException("libbox artifact and source URLs must use HTTPS.")
}
if (!libboxSourceCommit.matches(Regex("[0-9a-f]{40}")) ||
    !libboxArtifactCommit.matches(Regex("[0-9a-f]{40}"))
) {
    throw GradleException("Invalid libbox source or artifact commit in ${libboxPropertiesFile.path}.")
}
if (!libboxExpectedSha256.matches(Regex("[0-9a-f]{64}"))) {
    throw GradleException("Invalid libbox SHA-256 in ${libboxPropertiesFile.path}.")
}

val configuredLibboxCacheDirectory = providers.gradleProperty("libboxCacheDir")
    .orElse(providers.environmentVariable("LIBBOX_CACHE_DIR"))
    .orNull
    ?.takeIf { it.isNotBlank() }
val libboxCacheDirectory = configuredLibboxCacheDirectory?.let(rootProject::file)
    ?: gradle.gradleUserHomeDir.resolve(
        "caches/proxy-switcher-vpn/libbox/$libboxVersion-$libboxExpectedSha256"
    )
val libboxAarFile = libboxCacheDirectory.resolve("libbox.aar")
val requiredLibboxAbiEntries = listOf(
    "jni/arm64-v8a/libbox.so",
    "jni/x86_64/libbox.so"
)

fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

fun File.isExpectedLibboxArtifact(): Boolean {
    return isFile &&
        length() == libboxExpectedSize &&
        sha256Hex().equals(libboxExpectedSha256, ignoreCase = true)
}

android {
    namespace = "com.hightemp.proxy_switcher_vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hightemp.proxy_switcher_vpn"
        minSdk = 24
        targetSdk = 35
        versionCode = 201
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

val downloadLibboxArtifact by tasks.registering {
    group = "build setup"
    description = "Downloads the pinned libbox AAR into the Gradle user cache."

    inputs.file(libboxPropertiesFile)
    inputs.property("artifactUrl", libboxArtifactUrl)
    inputs.property("artifactCommit", libboxArtifactCommit)
    inputs.property("sourceCommit", libboxSourceCommit)
    inputs.property("sourceUrl", libboxSourceUrl)
    outputs.file(libboxAarFile)
    outputs.upToDateWhen { libboxAarFile.isExpectedLibboxArtifact() }

    doLast {
        if (libboxAarFile.isExpectedLibboxArtifact()) {
            return@doLast
        }
        if (gradle.startParameter.isOffline) {
            throw GradleException(
                "Gradle is offline and no verified libbox $libboxVersion artifact exists at " +
                    "${libboxAarFile.path}. Run downloadLibboxArtifact once without --offline."
            )
        }

        Files.createDirectories(libboxCacheDirectory.toPath())
        val temporaryArtifact = Files.createTempFile(
            libboxCacheDirectory.toPath(),
            "libbox-",
            ".aar.part"
        )
        try {
            val connection = libboxArtifactUri.toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 300_000
                setRequestProperty("User-Agent", "proxy-switcher-vpn-gradle/$libboxVersion")
            }
            connection.getInputStream().use { input ->
                Files.copy(input, temporaryArtifact, StandardCopyOption.REPLACE_EXISTING)
            }

            val downloadedArtifact = temporaryArtifact.toFile()
            if (downloadedArtifact.length() != libboxExpectedSize) {
                throw GradleException(
                    "Downloaded libbox size mismatch. Expected $libboxExpectedSize bytes " +
                        "but found ${downloadedArtifact.length()} bytes."
                )
            }
            val downloadedHash = downloadedArtifact.sha256Hex()
            if (!downloadedHash.equals(libboxExpectedSha256, ignoreCase = true)) {
                throw GradleException(
                    "Downloaded libbox hash mismatch. Expected $libboxExpectedSha256 " +
                        "but found $downloadedHash."
                )
            }

            try {
                Files.move(
                    temporaryArtifact,
                    libboxAarFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryArtifact,
                    libboxAarFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (error: Exception) {
            throw GradleException(
                "Failed to download verified libbox $libboxVersion from $libboxArtifactUrl.",
                error
            )
        } finally {
            Files.deleteIfExists(temporaryArtifact)
        }
    }
}

val verifyLibboxArtifact by tasks.registering {
    group = "verification"
    description = "Verifies the downloaded libbox AAR hash and required native ABI entries."

    dependsOn(downloadLibboxArtifact)

    inputs.file(libboxAarFile)
    inputs.file(libboxPropertiesFile)

    doLast {
        val aar = libboxAarFile
        if (!aar.isFile) {
            throw GradleException("Missing libbox artifact at ${aar.path}.")
        }
        if (aar.length() != libboxExpectedSize) {
            throw GradleException(
                "libbox artifact size mismatch. Expected $libboxExpectedSize bytes " +
                    "but found ${aar.length()} bytes."
            )
        }
        val actualHash = aar.sha256Hex()
        if (!actualHash.equals(libboxExpectedSha256, ignoreCase = true)) {
            throw GradleException(
                "libbox artifact hash mismatch. Expected $libboxExpectedSha256 " +
                    "but found $actualHash."
            )
        }

        ZipFile(aar).use { zip ->
            val missingEntries = requiredLibboxAbiEntries.filter { entryName ->
                zip.getEntry(entryName) == null
            }
            if (missingEntries.isNotEmpty()) {
                throw GradleException(
                    "libbox artifact is missing required ABI entries: ${missingEntries.joinToString()}."
                )
            }
        }
    }
}

tasks.register("printLibboxArtifactPath") {
    group = "help"
    description = "Prints the absolute path of the cached, verified libbox AAR."
    dependsOn(verifyLibboxArtifact)
    doLast {
        println(libboxAarFile.absolutePath)
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    dependsOn(verifyLibboxArtifact)
    inputs.file(libboxAarFile)
    inputs.file(libboxPropertiesFile)
    systemProperty("libbox.artifact.path", libboxAarFile.absolutePath)
}

tasks.named("preBuild") {
    dependsOn(verifyLibboxArtifact)
}

tasks.named("check") {
    dependsOn(verifyLibboxArtifact)
}

dependencies {

    implementation(files(libboxAarFile).builtBy(downloadLibboxArtifact))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
