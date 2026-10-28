plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Los datos de Supabase salen del mismo config.js del sitio
val siteConfig = rootProject.file("../config.js").readText()
fun cfg(key: String): String =
    Regex("$key\\s*:\\s*'([^']*)'").find(siteConfig)?.groupValues?.get(1) ?: ""

// Firma de las versiones publicadas: si el workflow de CI escribió un keystore de release
// (a partir de un secret de GitHub) en la ruta que indica RV_KEYSTORE_PATH, se usa ESE
// keystore fijo para que todas las versiones queden firmadas con la MISMA clave y Android
// pueda instalar la actualización encima de la anterior sin pedir desinstalar. Si el archivo
// no existe (build local, o antes de configurar el secret en el repo), se cae al keystore de
// debug de siempre — pero en ese caso las actualizaciones NO van a poder instalarse sobre una
// versión ya instalada sin desinstalar primero.
val relKeystorePath = System.getenv("RV_KEYSTORE_PATH")?.takeIf { file(it).exists() }

android {
    namespace = "com.realityvault.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.realityvault.tv"
        minSdk = 21
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        buildConfigField("String", "SUPABASE_URL", "\"${cfg("supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${cfg("supabaseAnonKey")}\"")
        buildConfigField("String", "SITE_URL", "\"https://reality-vault.vercel.app/\"")
        // Manifiesto JSON con la última versión publicada (ver sección 5 de la auditoría de
        // actualizaciones). Centralizado acá para poder cambiarlo sin tocar la lógica del updater.
        buildConfigField("String", "UPDATE_URL", "\"https://github.com/valenrosario/REALITY-VAULT-OG/releases/download/tv/update.json\"")
    }

    signingConfigs {
        if (relKeystorePath != null) {
            create("release") {
                storeFile = file(relKeystorePath)
                storePassword = System.getenv("RV_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RV_KEY_ALIAS") ?: "realityvault"
                keyPassword = System.getenv("RV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (relKeystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
