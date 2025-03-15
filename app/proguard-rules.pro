# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep model classes used with Room or Serialization
-keep class com.freeaac.communicationbuddy.data.** { *; }

# Keep Compose-related classes (fixes some reflection issues)
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Optimize but keep line numbers for better error reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Most of the time the Android framework will be able to keep references to these
# through meta-data in the AndroidManifest
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application