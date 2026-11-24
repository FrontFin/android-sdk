# Rules shipped to consumers of com.meshpay:link.

# The @JavascriptInterface fallback bridge (old WebViews) is called by name from
# injected JavaScript, so its method must survive R8/ProGuard.
-keepclassmembers class com.meshpay.link.internal.LinkActivity$LegacyJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Public API types cross the Intent as Parcelables; keep their Parcelable plumbing.
-keep class com.meshpay.link.** implements android.os.Parcelable {
    public static final ** CREATOR;
}
