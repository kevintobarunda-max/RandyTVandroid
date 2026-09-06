plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.randytv.mobile"
    // compileSdk 35 es requerido por Media3 1.7.1 (las versiones recientes exigen SDK 35+).
    compileSdk = 35

    defaultConfig {
        applicationId = "com.randytv.mobile"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Solo empaquetar recursos de idioma/densidad que usamos, reduce el tamaño del APK.
        resourceConfigurations += listOf("es", "en")

        // NextLib incluye librerías nativas FFmpeg para 4 arquitecturas. Limitamos a las de TV/
        // móviles ARM para que el APK universal no pese de más. Los splits de abajo generan además
        // un APK por arquitectura (mucho más liviano) para instalar en el proyector.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Genera un APK separado por arquitectura. En el proyector instala solo el que corresponda
    // (normalmente arm64-v8a en Android 12); pesa bastante menos que el universal.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true // también deja un APK universal por si no sabes la arquitectura
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Media3 marca muchas APIs como @UnstableApi (opt-in). El proyecto las usa a proposito,
        // asi que optamos por ellas globalmente para evitar el ruido de advertencias al compilar.
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Media3 1.7.1: alineado con la versión que empaqueta NextLib (nextlib-media3ext 1.7.1-0.9.0,
    // la variante publicada en Maven Central) para evitar conflictos de clases entre el ExoPlayer
    // de la app y el de la extensión FFmpeg.
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.7.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.7.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")
    // NextLib: decodificadores FFmpeg por software (AC3, E-AC3, DTS/dca, MP3, AAC, TrueHD, MLP,
    // Vorbis, Opus, FLAC, ALAC, PCM, AMR...). Resuelve los títulos que "no se escuchan" cuando el
    // proyector no trae decodificador de hardware para ese códec de audio. Trae binarios ya
    // compilados, no requiere compilar FFmpeg con el NDK. El formato de versión es
    // <version-media3>-<version-nextlib>.
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.7.1-0.9.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
}
