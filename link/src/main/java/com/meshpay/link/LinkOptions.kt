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
 *
 * From Kotlin, create one with the [LinkOptions] builder function:
 *
 * ```kotlin
 * val options = LinkOptions {
 *     environment(LinkEnvironment.SBX)
 *     theme(LinkTheme.SYSTEM)
 * }
 * ```
 *
 * From Java, use [Builder]:
 *
 * ```java
 * LinkOptions options = new LinkOptions.Builder()
 *     .environment(LinkEnvironment.SBX)
 *     .theme(LinkTheme.SYSTEM)
 *     .build();
 * ```
 *
 * The constructor is not public: a new option is a new [Builder] function, so
 * options can be added in minor releases without breaking apps or libraries
 * already built against an older version.
 *
 * @property environment which hosted deployment to open. `null` opens [LinkEnvironment.PROD].
 * @property language UI language as a locale code (e.g. `"es"`).
 * @property theme how Link is themed. [LinkTheme.SYSTEM] follows the device appearance.
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
     * Builds [LinkOptions]. Every option starts unset (`null`); call a function to
     * set it. Each function returns the builder, so calls chain from Java; in the
     * Kotlin `LinkOptions { … }` block the return value is simply ignored.
     *
     * Options are set with functions, not assignable properties, on purpose:
     * inside the Kotlin block, `environment = environment` would silently assign a
     * same-named local variable to itself, while `environment(environment)`
     * always reaches the builder.
     */
    public class Builder {
        private var environment: LinkEnvironment? = null
        private var language: String? = null
        private var theme: LinkTheme? = null
        private var integrationId: String? = null
        private var displayFiatCurrency: String? = null

        /** Which hosted deployment to open. `null` opens [LinkEnvironment.PROD]. */
        public fun environment(value: LinkEnvironment?): Builder = apply { environment = value }

        /** UI language as a locale code (e.g. `"es"`). */
        public fun language(value: String?): Builder = apply { language = value }

        /** How Link is themed. [LinkTheme.SYSTEM] follows the device appearance. */
        public fun theme(value: LinkTheme?): Builder = apply { theme = value }

        /** Pre-selects a wallet integration. */
        public fun integrationId(value: String?): Builder = apply { integrationId = value }

        /** ISO 4217 code (e.g. `"DKK"`) for the currency Link shows. */
        public fun displayFiatCurrency(value: String?): Builder = apply { displayFiatCurrency = value }

        /** Creates the [LinkOptions]. The builder can keep being used afterwards. */
        public fun build(): LinkOptions =
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

/**
 * [LinkOptions] with every option unset: production, Link's own defaults.
 * Kotlin only; from Java use `new LinkOptions.Builder().build()`.
 */
@JvmSynthetic
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
 *
 * Kotlin only; from Java use [LinkOptions.Builder].
 */
@JvmSynthetic
public fun LinkOptions(block: LinkOptions.Builder.() -> Unit): LinkOptions = LinkOptions.Builder().apply(block).build()
