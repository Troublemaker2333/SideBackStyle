plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.junjun.sideback"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.junjun.sideback"
        minSdk = 33
        targetSdk = 37
        versionCode = 4
        versionName = "2.0"

        // COUI plus Compose crosses the 64K method limit.
        multiDexEnabled = true
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The libxposed entry point is unchanged; the framework supplies these at runtime.
    compileOnly(files("../vendor/libxposed-api.jar"))

    implementation("io.github.suqi8.coui.kmp:coui-ui-android:2.0.0")
    implementation("io.github.suqi8.coui.kmp:coui-preference-android:2.0.0")
    implementation("io.github.suqi8.coui.kmp:coui-icons-android:2.0.0")
    implementation("io.github.suqi8.coui.kmp:coui-blur-android:2.0.0")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.foundation:foundation:1.7.0")
    implementation("androidx.compose.material:material-icons-core:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.multidex:multidex:2.0.1")
}
