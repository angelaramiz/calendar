# Add project specific ProGuard rules here.

# Supabase v3.x
-keep class io.github.jan.supabase.** { *; }
-keep class io.github.jan.supabase.**$Companion { *; }
-dontwarn io.github.jan.supabase.**

# Ktor 3.x
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# OkHttp (OTA download)
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.calendarfinance.app.**$$serializer { *; }
-keepclassmembers class com.calendarfinance.app.** { *** Companion; }
-keepclasseswithmembers class com.calendarfinance.app.** { kotlinx.serialization.KSerializer serializer(...); }

# General
-keepattributes SourceFile,LineNumberTable
