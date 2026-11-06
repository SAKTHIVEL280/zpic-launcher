plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.serialization)
}

android {
    namespace = "com.zpic.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zpic.launcher"
        minSdk = 29
        targetSdk = 37
        versionCode = 5
        versionName = "1.4.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-signed so the release APK installs directly; swap in your own key before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.material3)
    implementation(libs.icons)
    implementation(libs.ui)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.datastore)
    implementation(libs.graphics.shapes)
    implementation(libs.serialization)
    implementation(libs.profileinstaller)
    testImplementation(libs.junit)
}
