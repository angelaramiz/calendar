# Add project specific ProGuard rules here.

# Supabase / Ktor
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.calendarfinance.app.**$$serializer { *; }
-keepclassmembers class com.calendarfinance.app.** { *** Companion; }
-keepclasseswithmembers class com.calendarfinance.app.** { kotlinx.serialization.KSerializer serializer(...); }
