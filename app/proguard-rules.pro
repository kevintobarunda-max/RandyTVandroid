# =============================================================================
# Reglas ProGuard / R8 para RandyTV
# =============================================================================
# El build de release usa isMinifyEnabled=true. Sin estas reglas, R8 podria
# ofuscar o eliminar clases que se resuelven por JNI/reflexion (NextLib/FFmpeg,
# Media3) y el audio por software dejaria de funcionar SOLO en el APK release
# (en debug funciona porque no hay minify). Estas reglas evitan ese problema.

# --- NextLib (decodificadores FFmpeg por software) -------------------------
# Las clases *Renderer/*Decoder cargan librerias nativas (.so) via JNI y se
# referencian por nombre, por eso deben conservarse intactas.
-keep class io.github.anilbeesetti.nextlib.** { *; }
-keep class org.ffmpeg.** { *; }
-dontwarn io.github.anilbeesetti.nextlib.**

# --- Media3 / ExoPlayer -----------------------------------------------------
# Media3 usa reflexion para instanciar extensiones/renderers opcionales.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Metodos y campos nativos (JNI) ----------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- OkHttp / Okio (red) ----------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# --- Gson (modelos parseados por reflexion) --------------------------------
# Mantener los modelos de datos para que Gson pueda mapear el JSON del API.
-keep class com.randytv.mobile.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
