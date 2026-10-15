plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.bibliotecago.catalog"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":server"))

    testImplementation(libs.junit)
}
