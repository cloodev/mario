plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.cartoonquest.game"
    compileSdk = 35
    androidResources { noCompress += "wav" }
    defaultConfig {
        applicationId = "com.cartoonquest.game"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
