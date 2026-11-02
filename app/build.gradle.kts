plugins {
    id("com.android.application")
}

android {
    namespace = "app.numbercleaner"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.numbercleaner"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Signs with the debug key so `assembleRelease` gives an installable APK.
            // Replace with your own signingConfig before publishing to a store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // No runtime dependencies: the app uses only the Android framework (keeps the APK ~60 KB).
    testImplementation("junit:junit:4.13.2")
}
