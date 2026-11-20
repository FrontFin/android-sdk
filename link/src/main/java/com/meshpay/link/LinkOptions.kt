package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Which hosted Link deployment to open.
 *
 * New values may be added in minor releases, so give a `when` over it an `else`
 * branch. Existing values keep their meaning across 1.x.
 */
public enum class LinkEnvironment {
    PROD,
    SBX,
    DEV,
}

/**
 * How Link is themed.
 *
 * New values may be added in minor releases, so give a `when` over it an `else`
 * branch. Existing values keep their meaning across 1.x.
 */
public enum class LinkTheme {
    LIGHT,
    DARK,
    SYSTEM,
}

/**
 * Optional Link settings. Every field is optional and defaults to `null` (unset).
 * Create one with the [LinkOptions] builder function:
 *
 * ```kotlin
 * val options = LinkOptions {
 *     environment(LinkEnvironment.SBX)
 *     theme(LinkTheme.SYSTEM)
 * }
 * ```
 *
 * The constructor is not public: a new option is a new [Builder] function, so
 * options can be added in minor releases without breaking apps or libraries
 * already built against an older version.
 *
 * @property environment which hosted deployment to open. `null` opens [LinkEnvironment.PROD].
 * @property language UI language as a locale code (e.g. `"es"`).
 * @property theme how Link is themed. [LinkTheme.LIGHT] / [LinkTheme.DARK] force that mode.
 *   [LinkTheme.SYSTEM] or unset uses your portal styling, then the device appearance.
 * @property integrationId pre-selects a wallet integration.
 * @property displayFiatCurrency ISO 4217 code (e.g. `"DKK"`) for the currency Link shows.
 */
@Parcelize
public class LinkOptions internal constructor(
    public val environment: LinkEnvironment?,
    public val language: String?,
    public val theme: LinkTheme?,
    public val integrationId: String?,
    public val displayFiatCurrency: String?,
) : Parcelable {
    /**
     * Receiver of the [LinkOptions] builder function. Every option starts unset
     * (`null`); call a function to set it.
     *
     * Options are set with functions, not assignable properties, on purpose:
     * inside the block, `environment = environment` would silently assign a
     * same-named local variable to itself, while `environment(environment)`
     * always reaches the builder.
     */
    public class Builder internal constructor() {
        private var environment: LinkEnvironment? = null
        private var language: String? = null
        private var theme: LinkTheme? = null
        private var integrationId: String? = null
        private var displayFiatCurrency: String? = null

        /** Which hosted deployment to open. `null` opens [LinkEnvironment.PROD]. */
        public fun environment(value: LinkEnvironment?) {
            environment = value
        }

        /** UI language as a locale code (e.g. `"es"`). */
        public fun language(value: String?) {
            language = value
        }

        /**
         * How Link is themed. [LinkTheme.LIGHT] / [LinkTheme.DARK] force that mode.
         * [LinkTheme.SYSTEM] or unset uses your portal styling, then the device appearance.
         */
        public fun theme(value: LinkTheme?) {
            theme = value
        }

        /** Pre-selects a wallet integration. */
        public fun integrationId(value: String?) {
            integrationId = value
        }

        /** ISO 4217 code (e.g. `"DKK"`) for the currency Link shows. */
        public fun displayFiatCurrency(value: String?) {
            displayFiatCurrency = value
        }

        internal fun build(): LinkOptions =
            LinkOptions(
                environment,
                language,
                theme,
                integrationId,
                displayFiatCurrency,
            )
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is LinkOptions &&
            environment == other.environment &&
            language == other.language &&
            theme == other.theme &&
            integrationId == other.integrationId &&
            displayFiatCurrency == other.displayFiatCurrency

    override fun hashCode(): Int {
        var result = environment.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + theme.hashCode()
        result = 31 * result + integrationId.hashCode()
        result = 31 * result + displayFiatCurrency.hashCode()
        return result
    }

    override fun toString(): String =
        "LinkOptions(environment=$environment, language=$language, theme=$theme, " +
            "integrationId=$integrationId, displayFiatCurrency=$displayFiatCurrency)"
}

/** [LinkOptions] with every option unset: production, Link's own defaults. */
public fun LinkOptions(): LinkOptions = LinkOptions.Builder().build()

/**
 * Builds [LinkOptions]; set only the options you need:
 *
 * ```kotlin
 * LinkOptions {
 *     environment(LinkEnvironment.SBX)
 *     language("es")
 * }
 * ```
 */
public fun LinkOptions(block: LinkOptions.Builder.() -> Unit): LinkOptions = LinkOptions.Builder().apply(block).build()
