package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/** Which hosted Link deployment to open. */
public enum class LinkEnvironment {
    PROD,
    SBX,
    DEV,
}

/** How Link is themed. */
public enum class LinkTheme {
    LIGHT,
    DARK,
    SYSTEM,
}

/**
 * Optional Link settings. Every field is optional.
 *
 * @param environment which hosted deployment to open. Defaults to [LinkEnvironment.PROD].
 * @param language UI language as a locale code (e.g. `"es"`).
 * @param theme how Link is themed. [LinkTheme.SYSTEM] follows the device appearance.
 * @param flow which screen Link opens on. Passed to Link as JSON, not validated.
 * @param integrationId pre-selects a wallet integration.
 * @param displayFiatCurrency ISO 4217 code (e.g. `"DKK"`) for the currency Link shows.
 * @param linkUrl testing only: a LocalCan tunnel (`*.localcan.dev`). Any other value is ignored.
 */

@Parcelize
public data class LinkOptions(
    public val environment: LinkEnvironment = LinkEnvironment.PROD,
    public val language: String? = null,
    public val theme: LinkTheme? = null,
    // `@RawValue`: LinkFlowConfig is a sealed interface, so Parcelize serialises it
    // through writeValue()/readValue(). Every implementation is itself @Parcelize,
    // so the value round-trips correctly.
    public val flow: @RawValue LinkFlowConfig? = null,
    public val integrationId: String? = null,
    public val displayFiatCurrency: String? = null,
    public val linkUrl: String? = null,
) : Parcelable





