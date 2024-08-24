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
