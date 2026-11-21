plugins {
    // COUI 2.0.0 requires compileSdk 37, which AGP 8.7 refuses (it caps at 35). These are the versions the
    // toolbox builds with: 8.10.1 for the SDK, 2.4.21 because COUI ships Kotlin 2.4.x metadata.
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
