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
 * @param token session token from `POST /v2/sessions`, minted by your backend.
 *   A single-use exchange code, not a JWT. Trimmed; rejected when blank.
 * @param clientId your Mesh client ID. Trimmed; rejected when blank with an
 *   [IllegalArgumentException] at launch time, before anything opens.
 * @param options optional Link settings.
 */
@Parcelize
public data class LinkConfiguration(
    public val token: String,
    public val clientId: String,
    public val options: LinkOptions = LinkOptions(),
) : Parcelable {
    init {
        require(token.isNotBlank()) { "LinkConfiguration: token is required" }
        require(clientId.isNotBlank()) { "LinkConfiguration: clientId is required" }
    }
}
