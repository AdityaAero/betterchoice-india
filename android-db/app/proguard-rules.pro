# Defense in depth: diagnostics are also guarded by BuildConfig.DEBUG at source.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
