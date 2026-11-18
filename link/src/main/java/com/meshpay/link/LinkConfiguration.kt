package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Everything needed to open one Link session. Passed to
 * `linkLauncher.launch(...)` with a [LinkContract].
 *
 * `@Parcelize` so it survives the Intent to the SDK's Activity. The token
 * travels only through the Intent and is never written to `SavedStateHandle` or
 * any other persisted store (§2.7). [toString] never includes the token, so the
 * configuration is safe to log.
 *
 * A plain class, not a `data class`, so it can grow in minor releases without
 * breaking apps or libraries already built against this one.
 *
 * A blank `token` or `clientId` is not thrown here: Link is launched and the
 * session ends immediately with [LinkResult.Error] carrying [LinkErrorCode.LOAD_ERROR]
 * and a message naming the field (§2.2, §2.3).
 *
 * @param token session token from `POST /v2/sessions`, minted by your backend.
 *   A single-use exchange code, not a JWT. Trimmed; a blank value yields `load-error`.
 * @param clientId your Mesh client ID. Trimmed; a blank value yields `load-error`.
 * @param options optional Link settings.
 */
@Parcelize
public class LinkConfiguration
    @JvmOverloads
    public constructor(
        public val token: String,
        public val clientId: String,
        public val options: LinkOptions = LinkOptions(),
    ) : Parcelable {
        override fun equals(other: Any?): Boolean =
            this === other ||
                other is LinkConfiguration &&
                token == other.token &&
                clientId == other.clientId &&
                options == other.options

        override fun hashCode(): Int = listOf(token, clientId, options).hashCode()

        /** Never includes the token: it is a single-use credential (§2.7). */
        override fun toString(): String = "LinkConfiguration(token=<redacted>, clientId=$clientId, options=$options)"
    }
