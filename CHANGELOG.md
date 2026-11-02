# Changelog

All notable changes to the Meshpay Link Android SDK are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 0.1.0

### Added
- `LinkContract` Activity Result contract that opens the hosted Meshpay Link UI full screen in a WebView, delivering `LinkResult.Exit(status)` or `LinkResult.Error(error)` exactly once
- `LinkConfiguration` with `token`, `clientId` and `LinkOptions` for environment, language, theme, pre-selected integration, display fiat currency and LocalCan testing URLs
- A blank `token` or `clientId` is reported through `LinkResult.Error` with `load-error`, never thrown
- `MeshpayLink.closeActiveSession()` for host-initiated close, reporting the status Link returns
- Wallet deep links, OAuth pages and external hand-offs open in the wallet app or browser
