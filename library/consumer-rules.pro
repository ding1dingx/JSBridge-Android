# This will strip `Log.v`, `Log.d`, and `Log.i` statements and will leave `Log.w` and `Log.e` statements intact.
-assumenosideeffects class android.util.Log {
  public static boolean isLoggable(java.lang.String, int);
  public static int v(...);
  public static int d(...);
  public static int i(...);
}

-keep class java.lang.reflect.** { *; }
-keep class kotlin.reflect.** { *; }
