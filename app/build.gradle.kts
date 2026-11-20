plugins {
    id("com.android.application")
}

android {
    namespace = "app.goldrate"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.goldrate"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.1")
}
