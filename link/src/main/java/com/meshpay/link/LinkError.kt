package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Why Link could not be shown or could not continue. */
public enum class LinkErrorCode(public val value: String) {
    /** The page failed to load (network error or an HTTP error on the page itself). */
    LOAD_ERROR("load-error"),

    /** The page did not load within 15 seconds. */
    LOAD_TIMEOUT("load-timeout"),

    /**
     * The session ended before Link finished and cannot resume: the WebView
     * renderer died, the host process was killed mid-session, or a page from
     * another origin reached the Link window (the SDK closes Link rather than
     * show it). The session token is single-use, so the SDK does not blindly
     * reload a spent token.
     */
    SESSION_INTERRUPTED("session-interrupted"),
}

/**
 * Reported through [LinkResult.Error] when Link can't be shown or can't continue.
 *
 * @property message for developers only — it never contains the token, the Link
 *   URL or a stack trace.
 */
@Parcelize
public data class LinkError(
    public val code: LinkErrorCode,
    public val message: String,
) : Parcelable
