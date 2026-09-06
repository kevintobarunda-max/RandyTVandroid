plugins {
    // AGP 8.7.2: necesario para compilar con compileSdk 35 (requerido por Media3 1.7.1).
    // Compatible con el Gradle 8.14.5 del wrapper y con Kotlin 1.9.20.
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
