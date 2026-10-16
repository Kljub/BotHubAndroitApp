plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Version from the release tag (CI: -PappVersion=1.2.0); versionCode from it.
val appVersion = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "1.0.0"
val appVersionCode = appVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    .let { (it.getOrElse(0) { 0 } * 10000) + (it.getOrElse(1) { 0 } * 100) + it.getOrElse(2) { 0 } }

android {
    namespace = "com.bothub.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bothub.app"
        minSdk = 26
        targetSdk = 35
        versionCode = maxOf(1, appVersionCode)
        versionName = appVersion
    }

    // Release signing: the same key for every release, or Android refuses the
    // update. The keystore comes from the CI secrets (see README), never the repo.
    signingConfigs {
        create("release") {
            val store = System.getenv("BOTHUB_KEYSTORE")
            if (store != null) {
                storeFile = file(store)
                storePassword = System.getenv("BOTHUB_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BOTHUB_KEY_ALIAS") ?: "bothub"
                keyPassword = System.getenv("BOTHUB_KEY_PASSWORD") ?: System.getenv("BOTHUB_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("BOTHUB_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.embedded)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
