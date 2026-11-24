package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Everything needed to open one Link session. Passed to
 * `linkLauncher.launch(...)` with a [LinkContract].
 *
 * `@Parcelize` so it survives the Intent to the SDK's Activity. The token
 * travels only through the Intent and is never written to `SavedStateHandle` or
 * any other persisted store. [toString] never includes the token, so the
 * configuration is safe to log.
 *
 * The constructors have no default arguments on purpose: a new field is added
 * as a new constructor overload, so it never breaks apps or libraries already
 * built against an older version.
 *
 * A blank `token` or `clientId` is not thrown here: Link is launched and the
 * session ends immediately with [LinkResult.Error] carrying [LinkErrorCode.LOAD_ERROR]
 * and a message naming the field.
 *
 * @property token session token from `POST /v2/sessions`, minted by your backend.
 *   A single-use exchange code, not a JWT. Trimmed; a blank value yields `load-error`.
 * @property clientId your Mesh client ID. Trimmed; a blank value yields `load-error`.
 * @property options optional Link settings, built with `LinkOptions { … }`.
 */
@Parcelize
public class LinkConfiguration(
    public val token: String,
    public val clientId: String,
    public val options: LinkOptions,
) : Parcelable {
    /** A configuration with every option unset (production, Link's own defaults). */
    public constructor(token: String, clientId: String) : this(token, clientId, LinkOptions())

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is LinkConfiguration &&
            token == other.token &&
            clientId == other.clientId &&
            options == other.options

    override fun hashCode(): Int {
        var result = token.hashCode()
        result = 31 * result + clientId.hashCode()
        result = 31 * result + options.hashCode()
        return result
    }

    /** Never includes the token: it is a single-use credential. */
    override fun toString(): String = "LinkConfiguration(token=<redacted>, clientId=$clientId, options=$options)"
}
