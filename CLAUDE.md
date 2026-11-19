# CLAUDE.md — Mesh Link Android SDK

Context for working on this repo. The SDK opens the hosted Mesh Link UI in a
WebView; it is the Android peer of `FrontFin/react-native-sdk`, `FrontFin/ios-sdk`
and `FrontFin/flutter-sdk`, and shares their public API, Link URL contract and
bridge protocol. The solution design is the "MFS SDK Solution Design" (Notion).

## Layout

```
link/                                  → com.meshpay:link (published AAR)
  src/main/java/com/meshpay/link/
    LinkConfiguration, LinkOptions, LinkResult, LinkError   (public API)
    LinkContract        ActivityResultContract<LinkConfiguration, LinkResult>
    LinkHandle          close()
    LinkSdk             VERSION
    internal/           LinkActivity, ActiveSession, LinkUrlBuilder, Origin, NavigationPolicy, BridgeMessage,
                        ExternalLauncher, LinkOverlays
  consumer-rules.pro
  api/link.api                         locked public API (binary-compatibility-validator)
example/                               pasted-token sample app
test-vectors/                          link-url.json, navigation.json (shared across the four SDKs)
```

## Rules that matter

- **Version** is `meshpay-link` in `gradle/libs.versions.toml`. `BuildConfig.SDK_VERSION`
  is generated from it; never hard-code a version anywhere else. Release with the
  `bump-version` skill (`.claude/commands/bump-version.md`).
- **Shared test vectors** (`test-vectors/`) are copied from the other SDK repos and
  must stay in sync. The URL builder (U1–U7) and navigation policy (N1–N8) are
  pure functions tested against them.
- **Pure logic stays pure.** `LinkUrlBuilder`, `Origin`, `NavigationPolicy`,
  `BridgeMessage` take no Android framework types (beyond `org.json`), so they run
  under Robolectric unit tests. WebView/Activity glue lives in `LinkActivity` and
  is covered by instrumented tests.
- **Security:** never log the token / Link URL / bridge payloads in release builds;
  never persist them; accept bridge messages only from the Link origin + main
  frame; JSON-encode every injected script.
- **Coverage floor:** lines/functions/statements ≥ 90 %, branches ≥ 80 %
  (`link:jacocoCoverageVerification`). Ratchet upward.
- Public API uses Kotlin `explicitApi()` — mark new public declarations `public`.
- **The public API is locked** in `link/api/link.api`; CI runs `apiCheck`. A change
  that touches it must be deliberate: run `./gradlew link:apiDump` and commit the
  file. Public types are plain classes, not data classes (no `copy`/`componentN`
  to break), and public constructors have no default arguments (no hidden
  `DefaultConstructorMarker` constructor). The SDK is Kotlin-only; Java is not a
  supported caller. `LinkOptions`' constructor is `internal` on purpose: it is
  public in bytecode but not in `link.api`. Don't make it `private`: Kotlin then
  adds a synthetic `DefaultConstructorMarker` accessor to `link.api` that changes
  with every new option. A new option is a new `LinkOptions.Builder` function
  (not a `var`); a new field on `LinkConfiguration`, `LinkError`,
  `LinkResult.Exit` or `LinkResult.Error` is a new constructor overload, keeping
  the existing ones.
  `LinkErrorCode`/`LinkExitStatus`/`LinkEnvironment`/`LinkTheme` may gain values
  in 1.x; `LinkResult` stays `Exit` | `Error`.

## Commands

| Command | Does |
| --- | --- |
| `./gradlew link:test` | Unit tests (Robolectric) |
| `./gradlew link:jacocoCoverageVerification` | Tests + coverage gate |
| `./gradlew link:ktlintCheck link:detekt` | Lint |
| `./gradlew link:apiCheck` / `link:apiDump` | Check / update the locked public API |
| `./gradlew link:lintRelease` | Android Lint |
| `./gradlew link:assembleRelease` | Build the AAR |
| `./gradlew :example:installDebug` | Install the sample app |
