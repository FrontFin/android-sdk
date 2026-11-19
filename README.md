# Mesh Link Android SDK

Opens the hosted [Mesh Link](https://link.meshpay.com) UI full screen in a WebView.

[![Maven Central](https://img.shields.io/maven-central/v/com.meshpay/link)](https://central.sonatype.com/artifact/com.meshpay/link)

Published on Maven Central as **`com.meshpay:link`**.

## Install

Replace `<version>` with the latest version shown on the badge above.

```kotlin
// settings.gradle.kts → dependencyResolutionManagement { repositories { mavenCentral() } }

dependencies {
    implementation("com.meshpay:link:<version>")
}
```

- **Minimum SDK:** Android 7.0 (API 24)
- **Kotlin:** apps built with Kotlin 1.9 or newer.
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
        options = LinkOptions {
            environment(LinkEnvironment.PROD)
            theme(LinkTheme.SYSTEM)
            language("en")
        },
    ),
)
```

The result is delivered **exactly once** and survives host Activity recreation.

## Configuration

`LinkConfiguration(token, clientId)` or `LinkConfiguration(token, clientId, options)`:

| Field | Required | Description |
| --- | --- | --- |
| `token` | yes | Session token from `POST /v2/sessions`. A single-use exchange code; trimmed. A blank value is reported as `load-error` (see below), not thrown. |
| `clientId` | yes | Your Mesh client ID. Trimmed. A blank value is reported through `LinkResult.Error` with `load-error`, before anything opens — nothing is thrown. |
| `options` | no | `LinkOptions { … }` — every field optional. |

### `LinkOptions`

Build options with `LinkOptions { … }` and call only the options you need;
anything you don't set stays unset:

```kotlin
val options = LinkOptions {
    language("es")
    theme(LinkTheme.DARK)
    displayFiatCurrency("EUR")
}
```

| Option | Type | Default | Notes |
| --- | --- | --- | --- |
| `environment` | `LinkEnvironment?` | unset (`PROD`) | `PROD`, `SBX` or `DEV`. Unset opens production. |
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

New statuses may be added in minor releases, so give a `when` over
`LinkExitStatus` an `else` branch.

This drives your UI only; it is **never proof of payment**. Confirm the outcome
server-side.

## Closing from your app

```kotlin
LinkHandle.close()
```

Link is asked to close gracefully first, so a user who already finished the flow
is still reported as `SUCCESS`. `LinkHandle.close()` ends Link after 1 s even if
Link opens an exit prompt. Once Link has loaded, the Android back button and
gesture go through the WebView history first, then ask Link to close. If Link
opens its exit prompt, it stays open until the user answers; a second Back within
2 s closes Link at once. Before Link loads, or after a page fails to load, Back
closes Link at once.

## Errors

`LinkResult.Error(error)` carries a `LinkErrorCode`. `error.message` is for
developers only — it never contains the token, the Link URL or a stack trace.

| Code | Meaning |
| --- | --- |
| `LOAD_ERROR` | The page failed to load (including an HTTP 4xx/5xx on the main document), or `token`/`clientId` was blank. |
| `LOAD_TIMEOUT` | The page did not load within 15 seconds. |
| `SESSION_INTERRUPTED` | Link cannot resume: the WebView renderer died, the process was killed mid-session, or a page from another origin reached the Link window, so the SDK closed Link rather than show it. |

New codes may be added in minor releases, so give a `when` over `LinkErrorCode`
an `else` branch. `LinkResult` itself is always `Exit` or `Error`.

## Wallet apps

OAuth pages, on-ramp/exchange hand-offs, and wallet deep links (`metamask://…`,
`intent://…`) open **outside** the WebView, in the wallet or exchange app or the
browser, so universal links and App Links reach the right app. To return to your
app afterwards, configure your own deep link / App Link; Link resumes when your
app comes back to the foreground.

## SDK version

`LinkSdk.VERSION` is the SDK version (`MAJOR.MINOR.PATCH`). Include it in bug
reports.

## Secret handling

Only the **session token** goes in the app, and it is single-use. Your **client
secret** must never ship in the app — mint session tokens on your backend.
`LinkConfiguration.toString()` never includes the token.

## Versioning

The SDK follows [Semantic Versioning](https://semver.org). From 1.0.0, breaking
changes to the public API ship only in a new major version. Minor releases may add
options to `LinkOptions` and new values to `LinkErrorCode`, `LinkExitStatus`,
`LinkEnvironment` and `LinkTheme`, so give a `when` over any of them an `else` branch.

## License

[MIT](LICENSE)
