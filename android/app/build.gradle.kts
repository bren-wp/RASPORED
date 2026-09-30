plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseVersion = rootProject.projectDir.parentFile.resolve("VERSION").readText().trim()
val versionMatch = Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(releaseVersion)
    ?: error("VERSION must use semantic version format x.y.z")
val releaseVersionCode =
    versionMatch.groupValues[1].toInt() * 10_000 +
    versionMatch.groupValues[2].toInt() * 100 +
    versionMatch.groupValues[3].toInt()

val backendBaseUrl = providers.gradleProperty("RASPORED_BACKEND_URL")
    .orElse("https://raspored.eu")
    .get()
    .trimEnd('/')

android {
    namespace = "hr.raspored.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "hr.raspored.app"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
    }
    compileOptions {
        sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
        targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
}


kotlin { jvmToolchain(17) }
