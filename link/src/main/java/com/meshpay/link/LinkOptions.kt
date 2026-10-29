package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Which hosted Link deployment to open. */
public enum class LinkEnvironment {
    /** `https://link.meshpay.com` (default). */
    PROD,

    /** `https://link.sbx.meshpay.com`. */
    SBX,

    /** `https://link.dev.meshpay.com`. */
    DEV,
}

/** How Link is themed. */
public enum class LinkTheme {
    LIGHT,
    DARK,

    /** Resolved from the device appearance when Link opens. */
    SYSTEM,
}

/**
 * Optional Link settings. Every field is optional and has the same name,
 * semantics and URL parameter as `OpenLinkOptions` in `@meshpayhq/link-web-sdk`
 * (minus `target`, §2.2).
 *
 * @param environment which hosted deployment to open. Defaults to [LinkEnvironment.PROD].
 * @param language UI language as a locale code (e.g. `"es"`, `"pt-BR"`), sent to Link as `lng`.
 * @param theme how Link is themed. [LinkTheme.SYSTEM] is resolved from the device
 *   appearance at open time, so the `th` parameter is always `light` or `dark`.
 * @param flow which screen Link opens on. Passed to Link as JSON, never validated here.
 * @param integrationId pre-selects a wallet integration so Link opens on its connect step.
 * @param displayFiatCurrency ISO 4217 code (e.g. `"DKK"`) for the currency Link shows amounts in.
 *   Passed through unvalidated, as on web.
 * @param linkUrl advanced/testing only: load Link from a LocalCan tunnel
 *   (`https://*.localcan.dev`). Any other value is ignored and [environment] is
 *   used. MUST NOT be wired to untrusted input.
 */
@Parcelize
public data class LinkOptions(
    public val environment: LinkEnvironment = LinkEnvironment.PROD,
    public val language: String? = null,
    public val theme: LinkTheme? = null,
    public val flow: LinkFlowConfig? = null,
    public val integrationId: String? = null,
    public val displayFiatCurrency: String? = null,
    public val linkUrl: String? = null,
) : Parcelable
