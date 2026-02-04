# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Security: Rename source files to hide implementation details
-renamesourcefileattribute SourceFile

# Keep crash reporting information for debugging (but hide source file names)
-keepattributes SourceFile,LineNumberTable

# Security: Remove debug logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep data models for Room and serialization
-keep class org.qosp.notes.data.model.** { *; }

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class org.qosp.notes.**$$serializer { *; } # <-- change package name to your app's
-keepclassmembers class org.qosp.notes.** { # <-- change package name to your app's
    *** Companion;
}
-keepclasseswithmembers class org.qosp.notes.** { # <-- change package name to your app's
    kotlinx.serialization.KSerializer serializer(...);
}