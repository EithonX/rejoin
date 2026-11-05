plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "org.campus.autoconnect"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.campus.autoconnect"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "2.0.0-beta1"
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
