# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Gson
-keep class com.immichtv.api.** { *; }
-keepclassmembers class com.immichtv.api.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
