plugins {
    // AGP 8.7.2: necesario para compilar con compileSdk 35 (requerido por Media3 1.7.1).
    // Compatible con el Gradle 8.14.5 del wrapper.
    id("com.android.application") version "8.7.2" apply false
    // Kotlin 2.1.0: NextLib 1.7.1-0.9.0 se compiló con Kotlin 2.1, y el compilador 1.9 no puede
    // leer sus clases. Subimos a 2.1.0 para que sean compatibles.
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    // Desde Kotlin 2.0 el compilador de Compose es un plugin de Kotlin (ya no se usa
    // kotlinCompilerExtensionVersion). Su versión debe coincidir con la de Kotlin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
