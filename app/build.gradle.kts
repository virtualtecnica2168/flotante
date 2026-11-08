plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.virtualtecnica.cam"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.virtualtecnica.cam"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
