import java.util.Properties
import java.security.MessageDigest
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
val libboxAarFile = layout.projectDirectory.file("libs/libbox.aar")
val libboxSha256File = layout.projectDirectory.file("libs/libbox.aar.sha256")
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

android {
    namespace = "com.hightemp.proxy_switcher_vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hightemp.proxy_switcher_vpn"
        minSdk = 24
        targetSdk = 35
        versionCode = 200
        versionName = "0.2.0"

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

val verifyLibboxArtifact by tasks.registering {
    group = "verification"
    description = "Verifies the embedded libbox AAR hash and required native ABI entries."

    inputs.file(libboxAarFile)
    inputs.file(libboxSha256File)

    doLast {
        val aar = libboxAarFile.asFile
        if (!aar.isFile) {
            throw GradleException("Missing libbox artifact at ${aar.path}.")
        }

        val expectedHash = libboxSha256File.asFile
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?: throw GradleException("Missing libbox SHA-256 file at ${libboxSha256File.asFile.path}.")
        val actualHash = aar.sha256Hex()
        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            throw GradleException(
                "libbox artifact hash mismatch. Expected $expectedHash but found $actualHash."
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

tasks.named("preBuild") {
    dependsOn(verifyLibboxArtifact)
}

tasks.named("check") {
    dependsOn(verifyLibboxArtifact)
}

dependencies {

    implementation(files("libs/libbox.aar"))
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
