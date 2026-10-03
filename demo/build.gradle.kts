plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseVersion = rootProject.projectDir.parentFile.resolve("VERSION").readText().trim()
val match = Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(releaseVersion)
    ?: error("VERSION must use semantic version format x.y.z")
val code = match.groupValues[1].toInt() * 10_000 +
    match.groupValues[2].toInt() * 100 +
    match.groupValues[3].toInt()

val releaseStoreFile = providers.environmentVariable("RASPORED_RELEASE_STORE_FILE").orNull?.trim().orEmpty()
val releaseStorePassword = providers.environmentVariable("RASPORED_RELEASE_STORE_PASSWORD").orNull.orEmpty()
val releaseKeyAlias = providers.environmentVariable("RASPORED_RELEASE_KEY_ALIAS").orNull?.trim().orEmpty()
val releaseKeyPassword = providers.environmentVariable("RASPORED_RELEASE_KEY_PASSWORD").orNull.orEmpty()
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
)
val releaseSigningConfigured = releaseSigningValues.all { it.isNotBlank() }
if (releaseSigningValues.any { it.isNotBlank() } && !releaseSigningConfigured) {
    error("Release signing requires store file, store password, key alias and key password.")
}

android {
    namespace = "hr.raspored.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "hr.raspored.demo"
        minSdk = 26
        targetSdk = 36
        versionCode = code
        versionName = releaseVersion
        buildConfigField("String", "BACKEND_BASE_URL", "\"https://raspored.eu\"")
        buildConfigField("boolean", "DEMO_MODE", "true")
    }
    sourceSets {
        getByName("main") {
            java.srcDir("../android/app/src/main/java")
            res.srcDir("../android/app/src/main/res")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
kotlin { jvmToolchain(17) }
