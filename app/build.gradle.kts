plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    namespace = "org.campus.autoconnect"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.campus.autoconnect"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "2.4.0-beta2"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
