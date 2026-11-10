plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingVariables = listOf(
    "REJOIN_KEYSTORE_PATH",
    "REJOIN_KEYSTORE_PASSWORD",
    "REJOIN_KEY_ALIAS",
    "REJOIN_KEY_PASSWORD",
)
val releaseSigning = signingVariables.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = releaseSigning.values.any { !it.isNullOrEmpty() }
if (hasReleaseSigning) {
    val missing = releaseSigning.filterValues { it.isNullOrEmpty() }.keys
    require(missing.isEmpty()) { "Missing release signing variables: ${missing.joinToString()}" }
}

android {
    namespace = "com.eithonx.rejoin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eithonx.rejoin"
        minSdk = 23
        targetSdk = 35
        versionCode = 20
        versionName = "2.4.0-rc2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (hasReleaseSigning) {
        val config = signingConfigs.create("release") {
            storeFile = rootProject.file(releaseSigning.getValue("REJOIN_KEYSTORE_PATH")!!)
            storePassword = releaseSigning.getValue("REJOIN_KEYSTORE_PASSWORD")
            keyAlias = releaseSigning.getValue("REJOIN_KEY_ALIAS")
            keyPassword = releaseSigning.getValue("REJOIN_KEY_PASSWORD")
        }
        buildTypes.named("release") { signingConfig = config }
    }

    buildTypes.named("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
}
