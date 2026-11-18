package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

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
 * A plain class, not a `data class`, so new options can be added in minor
 * releases without breaking apps or libraries already built against this one.
 * For the same reason there is no `copy()`: build a new instance instead.
 *
 * @param environment which hosted deployment to open. `null` (the default) opens
 *   [LinkEnvironment.PROD].
 * @param language UI language as a locale code (e.g. `"es"`).
 * @param theme how Link is themed. [LinkTheme.SYSTEM] follows the device appearance.
 * @param integrationId pre-selects a wallet integration.
 * @param displayFiatCurrency ISO 4217 code (e.g. `"DKK"`) for the currency Link shows.
 */
@Parcelize
public class LinkOptions
    @JvmOverloads
    public constructor(
        public val environment: LinkEnvironment? = null,
        public val language: String? = null,
        public val theme: LinkTheme? = null,
        public val integrationId: String? = null,
        public val displayFiatCurrency: String? = null,
    ) : Parcelable {
        override fun equals(other: Any?): Boolean =
            this === other ||
                other is LinkOptions &&
                environment == other.environment &&
                language == other.language &&
                theme == other.theme &&
                integrationId == other.integrationId &&
                displayFiatCurrency == other.displayFiatCurrency

        override fun hashCode(): Int =
            listOf(environment, language, theme, integrationId, displayFiatCurrency).hashCode()

        override fun toString(): String =
            "LinkOptions(environment=$environment, language=$language, theme=$theme, " +
                "integrationId=$integrationId, displayFiatCurrency=$displayFiatCurrency)"
    }
