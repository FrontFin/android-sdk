package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * How a Link session ended, as reported by Link.
 *
 * For driving the UI only, never proof of payment: confirm the outcome
 * server-side.
 *
 * New statuses may be added in minor releases, so handle statuses you don't
 * know, for example with an `else` branch in a `when`. Existing statuses keep
 * their meaning and [value] across 1.x.
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
    /**
     * The session ended normally.
     *
     * A plain class, not a `data class`, so a field can be added in a minor
     * release without breaking apps or libraries already built against this one.
     */
    @Parcelize
    public class Exit(public val status: LinkExitStatus) : LinkResult {
        override fun equals(other: Any?): Boolean = this === other || other is Exit && status == other.status

        override fun hashCode(): Int = status.hashCode()

        override fun toString(): String = "Exit(status=$status)"
    }

    /**
     * Link could not be shown or could not continue.
     *
     * A plain class, not a `data class`, so a field can be added in a minor
     * release without breaking apps or libraries already built against this one.
     */
    @Parcelize
    public class Error(public val error: LinkError) : LinkResult {
        override fun equals(other: Any?): Boolean = this === other || other is Error && error == other.error

        override fun hashCode(): Int = error.hashCode()

        override fun toString(): String = "Error(error=$error)"
    }
}
