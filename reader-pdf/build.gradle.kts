plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.bibliotecago.reader.pdf"
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
    implementation(project(":core:model"))
    implementation(project(":core:eink"))

    testImplementation(libs.junit)
}
