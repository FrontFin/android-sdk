package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Why Link could not be shown or could not continue.
 *
 * New codes may be added in minor releases, so handle codes you don't know, for
 * example with an `else` branch in a `when`. Existing codes keep their meaning and
 * [value] across 1.x.
 */
public enum class LinkErrorCode(public val value: String) {
    /** The page failed to load (network error or an HTTP error on the page itself). */
    LOAD_ERROR("load-error"),

    /** The page did not load within 15 seconds. */
    LOAD_TIMEOUT("load-timeout"),

    /**
     * The WebView renderer died, or the host process was killed mid-session, and
     * Link cannot resume. The session token is single-use, so the SDK does not
     * blindly reload a spent token.
     */
    SESSION_INTERRUPTED("session-interrupted"),
}

/**
 * Reported through [LinkResult.Error] when Link can't be shown or can't continue.
 *
 * A plain class, not a `data class`, and its constructor has no default
 * arguments, so a field can be added in a minor release (as a new constructor
 * overload) without breaking apps or libraries already built against this one.
 *
 * @property code why Link stopped.
 * @property message for developers only — it never contains the token, the Link
 *   URL or a stack trace.
 */
@Parcelize
public class LinkError(
    public val code: LinkErrorCode,
    public val message: String,
) : Parcelable {
    override fun equals(other: Any?): Boolean =
        this === other || other is LinkError && code == other.code && message == other.message

    override fun hashCode(): Int = 31 * code.hashCode() + message.hashCode()

    override fun toString(): String = "LinkError(code=$code, message=$message)"
}
