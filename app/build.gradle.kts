import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Firma release: datos en un `keystore.properties` FUERA del repositorio. Se
 * busca en la ruta de la variable de entorno `BIBLIOTECAGO_SIGNING` o, si no
 * está, en `~/.bibliotecago/keystore.properties`. Claves: `storeFile`
 * (ruta al .jks), `storePassword`, `keyAlias` y `keyPassword`. Sin el
 * archivo, el APK release sale sin firmar.
 */
val signingFile = (System.getenv("BIBLIOTECAGO_SIGNING")?.let(::File)
    ?: File(System.getProperty("user.home"), ".bibliotecago/keystore.properties"))
val signing = signingFile.takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "com.bibliotecago.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.bibliotecago"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // En GitHub Actions sube con cada ejecución, para que cada APK pueda
        // instalarse encima del anterior.
        versionCode = System.getenv("BIBLIOTECAGO_VERSION_CODE")?.toInt() ?: 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signing != null) {
            create("release") {
                // Ruta relativa: respecto al propio keystore.properties.
                storeFile = signingFile.parentFile.resolve(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Lo exige Readium (lector EPUB).
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":core:eink"))
    implementation(project(":core:model"))
    implementation(project(":server"))
    implementation(project(":catalog"))
    implementation(project(":downloads"))
    implementation(project(":library"))
    implementation(project(":reader-epub"))
    implementation(project(":reader-pdf"))
    implementation(project(":settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
