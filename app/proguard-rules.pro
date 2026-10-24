# Gson models are instantiated through reflection in the minified release.
-keep class com.randytv.mobile.data.model.** { *; }
-keepattributes Signature,*Annotation*

# Gson 2.10.1 with R8 full mode requires preserving TypeToken signatures.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.randytv.mobile.data.network.Tmdb** { *; }
