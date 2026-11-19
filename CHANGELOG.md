# Changelog

All notable changes to the MeshPay Link Android SDK are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 0.2.0

### Added
- `LinkSdk.VERSION` with the SDK version
- `LinkOptions { … }` builder, e.g. `LinkOptions { environment(LinkEnvironment.SBX) }`

### Changed
- Public API is locked and checked in CI (`link/api/link.api`)
- `LinkOptions`, `LinkConfiguration`, `LinkError`, `LinkResult.Exit` and `LinkResult.Error` are no longer data classes: no `copy()` or destructuring
- `LinkOptions` is created only with `LinkOptions { … }` or `LinkOptions()`; its constructor is no longer public
- `LinkOptions.environment` defaults to `null`, which still opens `PROD`
- `LinkConfiguration.toString()` no longer includes the token
- `LinkErrorCode`, `LinkExitStatus`, `LinkEnvironment` and `LinkTheme` may gain values in minor releases; handle unknown ones with `else`

### Fixed
- Back no longer closes Link's exit prompt after 1 s. A second Back within 2 s closes Link.
- Back closes Link at once before it loads or after a page fails to load.

### Security
- A page from another origin can no longer stay full screen inside Link.

## 0.1.0

### Added
- `LinkContract` Activity Result contract that opens the hosted MeshPay Link UI full screen in a WebView, delivering `LinkResult.Exit(status)` or `LinkResult.Error(error)` exactly once
- `LinkConfiguration` with `token`, `clientId` and `LinkOptions` for environment, language, theme, pre-selected integration and display fiat currency
- A blank `token` or `clientId` is reported through `LinkResult.Error` with `load-error`, never thrown
- `LinkHandle.close()` for host-initiated close, reporting the status Link returns
- Wallet deep links, OAuth pages and external hand-offs open in the wallet app or browser
- A native loading spinner and close button appear during loading if Link is slow to respond
