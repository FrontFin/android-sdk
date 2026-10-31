package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * How a Link session ended, as reported by Link.
 *
 * For driving the UI only, never proof of payment: confirm the outcome
 * server-side.
 */
public enum class LinkExitStatus(public val value: String) {
    /**
     * The user completed the target action — submitted a deposit or transfer,
     * finished a verify-only or connect-only flow, confirmed a withdrawal, or
     * created a fiat ramp transfer.
     */
    SUCCESS("success"),

    /**
     * Anything else, including every session end where Link reported no status.
     */
    CANCELED("canceled"),
}

/**
 * The outcome of a Link session, delivered exactly once through the Activity
 * Result API ([LinkContract]). [Exit] and [Error] are never both delivered for
 * the same session.
 */
public sealed interface LinkResult : Parcelable {

    /** The session ended normally. */
    @Parcelize
    public data class Exit(public val status: LinkExitStatus) : LinkResult

    /** Link could not be shown or could not continue. */
    @Parcelize
    public data class Error(public val error: LinkError) : LinkResult
}
