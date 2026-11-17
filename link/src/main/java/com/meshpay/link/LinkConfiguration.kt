package com.meshpay.link

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Everything needed to open one Link session. Passed to
 * `linkLauncher.launch(...)` with a [LinkContract].
 *
 * `@Parcelize` so it survives the Intent to [internal.LinkActivity]. The token
 * travels only through the Intent and is never written to `SavedStateHandle` or
 * any other persisted store (§2.7).
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
public data class LinkConfiguration(
    public val token: String,
    public val clientId: String,
    public val options: LinkOptions = LinkOptions(),
) : Parcelable
