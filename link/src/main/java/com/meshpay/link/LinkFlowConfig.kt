package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Which screen Link opens on. Mirrors `LinkFlowConfig` in
 * `@meshpayhq/link-web-sdk` (`flow-config.ts`), so the web, React Native, iOS,
 * Android and Flutter SDKs accept the same `flow` shape. Keep the five in sync.
 *
 * The SDK serialises this to JSON and passes it straight to the Link UI as the
 * `flow` query parameter; it never inspects or validates it.
 */
public sealed interface LinkFlowConfig : Parcelable {

    /** Opens Link on a login/connect entry screen. */
    @Parcelize
    public data class Login(public val entry: LoginEntry) : LinkFlowConfig

    /**
     * Opens Link on the amount-entry screen.
     *
     * @param depositHomeDrawer when true, the "Pay with" drawer renders a
     *   deposit-home-style list (top integrations, other wallets & exchanges, QR
     *   deposit, and Buy crypto when [showBuyOption]).
     * @param showBuyOption adds a "Buy crypto" option to the rich drawer; only
     *   honoured when [depositHomeDrawer] is true.
     */
    @Parcelize
    public data class EnterAmount(
        public val depositHomeDrawer: Boolean? = null,
        public val showBuyOption: Boolean? = null,
    ) : LinkFlowConfig

    /** Fiat on-ramp ("Buy") flow. Routes straight to the on-ramp screen. */
    @Parcelize
    public data object FiatOnRamp : LinkFlowConfig

    /** Fiat off-ramp ("Sell") flow. Routes straight to the off-ramp screen. */
    @Parcelize
    public data object FiatOffRamp : LinkFlowConfig
}

/** The entry screen for [LinkFlowConfig.Login]. */
public sealed interface LoginEntry : Parcelable {

    /** The integrations catalog. */
    @Parcelize
    public data object Catalog : LoginEntry

    /** The deposit home screen. */
    @Parcelize
    public data class DepositHome(public val showBuyOption: Boolean? = null) : LoginEntry

    /** The deposit home screen scoped to integrations. */
    @Parcelize
    public data class DepositHomeIntegrations(public val showBuyOption: Boolean? = null) : LoginEntry
}
