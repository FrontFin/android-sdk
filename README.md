# MeshPay Link Android SDK

Opens the hosted [MeshPay Link](https://link.meshpay.com) UI full screen in a
WebView, as the Android peer of the MeshPay React Native, iOS and Flutter SDKs.
One public API, one Link URL contract and one bridge protocol across all of them.

Published on Maven Central as **`com.meshpay:link`**.

## Install

```kotlin
// settings.gradle.kts → dependencyResolutionManagement { repositories { mavenCentral() } }

dependencies {
    implementation("com.meshpay:link:0.1.0")
}
```

- **Minimum SDK:** Android 7.0 (API 24)
- No runtime permissions are required. Nothing is added to your manifest for v1,
  apart from optional deep-link return configuration (see [Wallet apps](#wallet-apps)).

## Quick start

Mint a session `token` on your backend with `POST /v2/sessions` (the client
secret stays server-side), then open Link with the Activity Result API:

```kotlin
val linkLauncher = registerForActivityResult(LinkContract()) { result ->
    when (result) {
        is LinkResult.Exit  -> Log.d("Link", "exit: ${result.status}")   // SUCCESS | CANCELED
        is LinkResult.Error -> Log.e("Link", "error: ${result.error.code}")
    }
}

linkLauncher.launch(
    LinkConfiguration(
        token = sessionToken,
        clientId = clientId,
        options = LinkOptions(
            environment = LinkEnvironment.PROD,
            theme = LinkTheme.SYSTEM,
            language = "en",
        ),
    ),
)
```

The result is delivered **exactly once** and survives host Activity recreation.

## Configuration

`LinkConfiguration(token, clientId, options)`:

| Field | Required | Description |
| --- | --- | --- |
| `token` | yes | Session token from `POST /v2/sessions`. A single-use exchange code; trimmed. A blank value is reported as `load-error` (see below), not thrown. |
| `clientId` | yes | Your Mesh client ID. Trimmed. A blank value is reported through `LinkResult.Error` with `load-error`, before anything opens — nothing is thrown. |
| `options` | no | `LinkOptions` — every field optional. |

### `LinkOptions`

| Option | Type | Default | Notes |
| --- | --- | --- | --- |
| `environment` | `LinkEnvironment` | `PROD` | `PROD`, `SBX` or `DEV`. |
| `language` | `String?` | unset | Locale code, sent to Link as `lng`. |
| `theme` | `LinkTheme?` | unset | `LIGHT`, `DARK` or `SYSTEM` (resolved from the device appearance at open time). |
| `integrationId` | `String?` | unset | Pre-selects a wallet integration. |
| `displayFiatCurrency` | `String?` | unset | ISO 4217 code (e.g. `"DKK"`). |

## Session status

`LinkResult.Exit(status)` reports how a session ended:

- **`SUCCESS`** — the user completed the target action (submitted a deposit or
  transfer, finished a verify-/connect-only flow, confirmed a withdrawal, or
  created a fiat ramp transfer).
- **`CANCELED`** — anything else, including every session end where Link reported
  no status.

This drives your UI only; it is **never proof of payment**. Confirm the outcome
server-side.

## Closing from your app

```kotlin
LinkHandle.closeActiveSession()
```

Link is asked to close gracefully first, so a user who already finished the flow
is still reported as `SUCCESS`. The Android back button and gesture go through the
WebView history first, then exit.

## Errors

`LinkResult.Error(error)` carries a `LinkErrorCode`. `error.message` is for
developers only — it never contains the token, the Link URL or a stack trace.

| Code | Meaning |
| --- | --- |
| `LOAD_ERROR` | The page failed to load (including an HTTP 4xx/5xx on the main document), or `token`/`clientId` was blank. |
| `LOAD_TIMEOUT` | The page did not load within 15 seconds. |
| `SESSION_INTERRUPTED` | The WebView renderer died, or the process was killed mid-session, and Link cannot resume. |

## Wallet apps

OAuth pages, on-ramp/exchange hand-offs, and wallet deep links (`metamask://…`,
`intent://…`) open **outside** the WebView, in the wallet or exchange app or the
browser, so universal links and App Links reach the right app. To return to your
app afterwards, configure your own deep link / App Link; Link resumes when your
app comes back to the foreground.

## Secret handling

Only the **session token** goes in the app, and it is single-use. Your **client
secret** must never ship in the app — mint session tokens on your backend.

## License

[MIT](LICENSE)
