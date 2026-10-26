# Gson models are instantiated through reflection in the minified release.
-keep class com.randytv.mobile.data.model.** { *; }
-keepattributes Signature,*Annotation*

# Gson 2.10.1 with R8 full mode requires preserving TypeToken signatures.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.randytv.mobile.data.network.Tmdb** { *; }

# Reflectively constructed renderer and JNI callback names must survive the installer build.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class com.randytv.mobile.data.repository.CatalogViewModel { public <init>(android.app.Application); }
