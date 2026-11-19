package com.meshpay.link

/** Information about the Mesh Link Android SDK itself. */
public object LinkSdk {
    /**
     * The SDK version (`MAJOR.MINOR.PATCH`), the same value sent to Link. Include
     * it in bug reports and logs.
     *
     * A `@JvmField`, not a `const`, so it is read from the SDK at run time instead
     * of being inlined into your app when it is compiled.
     */
    @JvmField
    public val VERSION: String = BuildConfig.SDK_VERSION
}
