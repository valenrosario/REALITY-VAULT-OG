plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Los datos de Supabase salen del mismo config.js del sitio (igual que la app de TV)
val siteConfig = rootProject.file("../config.js").readText()
fun cfg(key: String): String =
    Regex("$key\\s*:\\s*'([^']*)'").find(siteConfig)?.groupValues?.get(1) ?: ""

// Firma: igual que la app de TV. Si el CI escribió el keystore fijo de release se usa ese;
// si no, cae al de debug (en ese caso las actualizaciones hay que instalarlas desinstalando antes).
val relKeystorePath = System.getenv("RV_KEYSTORE_PATH")?.takeIf { file(it).exists() }

// Código compartido con la app de TV (modelos, conexión con Supabase, sesión, progreso).
// Se copia tal cual desde app-tv para que lo que cargues en el panel se lea igual en las dos apps.
// El paquete queda com.realityvault.tv, por eso el namespace es el mismo.
val tvSrc = rootProject.file("../app-tv/app/src/main/java/com/realityvault/tv")
val sharedOut = layout.buildDirectory.dir("generated/shared")
val copyShared = tasks.register<Copy>("copyShared") {
    from(tvSrc) { include("Data.kt", "Embed.kt") }
    into(sharedOut.map { it.dir("com/realityvault/tv") })
}

android {
    namespace = "com.realityvault.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.realityvault.app"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        buildConfigField("boolean", "TV_LINKS", "false")
        buildConfigField("String", "SUPABASE_URL", "\"${cfg("supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${cfg("supabaseAnonKey")}\"")
        buildConfigField("String", "SITE_URL", "\"https://reality-vault.vercel.app/\"")
    }

    sourceSets.getByName("main").java.srcDir(sharedOut)

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

tasks.configureEach {
    if (name.startsWith("compile") && name.contains("Kotlin")) dependsOn(copyShared)
    if (name.contains("Lint") || name.startsWith("generate") && name.contains("Resources")) mustRunAfter(copyShared)
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
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
