plugins { id("com.android.library") }
android {
    namespace = "androidx.media3.decoder.av1"
    compileSdk = 34
    ndkVersion = "26.1.10909125"
    defaultConfig {
        minSdk = 21
        consumerProguardFiles("upstream/proguard-rules.txt")
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
        externalNativeBuild { cmake {
            arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DBUILD_TESTING=OFF", "-DCPU_FEATURES_BUILD_TESTS=OFF", "-DLIBGAV1_ENABLE_TESTS=OFF", "-DLIBGAV1_ENABLE_EXAMPLES=OFF", "-DLIBGAV1_THREADPOOL_USE_STD_MUTEX=1", "-DCMAKE_POSITION_INDEPENDENT_CODE=ON")
            targets += "gav1JNI"
        } }
    }
    sourceSets["main"].java.srcDir("upstream/src/main/java")
    externalNativeBuild { cmake { path = file("upstream/src/main/jni/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.annotation:annotation:1.8.0")
    implementation("androidx.media3:media3-decoder:1.4.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    compileOnly("org.jetbrains.kotlin:kotlin-annotations-jvm:1.9.20")
}
