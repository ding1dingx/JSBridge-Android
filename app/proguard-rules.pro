# This will strip `Log.v`, `Log.d`, and `Log.i` statements and will leave `Log.w` and `Log.e` statements intact.
#-assumenosideeffects class android.util.Log {
#  public static boolean isLoggable(java.lang.String, int);
#  public static int v(...);
#  public static int d(...);
#  public static int i(...);
#}

# JavascriptInterface
-keepattributes *Annotation*
-keepattributes *JavascriptInterface*
-keepclassmembers class * {
  @android.webkit.JavascriptInterface <methods>;
}

# attributes
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keepclassmembers class * extends java.lang.Enum {
  <fields>;
  public static **[] values();
  public static ** valueOf(java.lang.String);
}

# Fix json parsing issue
-keep class com.ding1ding.jsbridge.model.** { *; }
