# Releasing

The version in `gradle/libs.versions.toml` (`meshpay-link`) is the single source
of truth. `BuildConfig.SDK_VERSION` and the release workflow both read it; it is
never hand-maintained anywhere else.

Releasing is just:

1. **Bump the version.** Run the `bump-version` Claude Code skill
   ([`.claude/commands/bump-version.md`](.claude/commands/bump-version.md)). It
   diffs `HEAD` against the latest tag, classifies the change as MAJOR / MINOR /
   PATCH from the public API, updates `meshpay-link` in
   `gradle/libs.versions.toml`, and turns the `## Unreleased` section of
   [`CHANGELOG.md`](CHANGELOG.md) into the `## X.Y.Z` entry (or adds one if there
   is no Unreleased section). While on `0.x`, a MAJOR-class change bumps the
   minor number.
2. **Open a PR and merge it to `main`.**

On merge, [`release.yml`](.github/workflows/release.yml) does the rest:

- `check-version` — reads `meshpay-link` and the latest `X.Y.Z` tag. Same
  version → no release (ends green). Lower → fails (downgrade). Validates that
  `CHANGELOG.md` has the matching `## X.Y.Z` section.
- `ci` — re-runs the full PR CI (`ci.yml`) on the release commit.
- `publish` — `./gradlew link:publishAndReleaseToMavenCentral` to the Central
  Portal, signed in memory. Maven Central versions are immutable, so if the
  GitHub release fails after publishing, use **Re-run failed jobs** (not all
  jobs) to create the tag and release without publishing again.
- `release` — `gh release create X.Y.Z --target <release commit>` (this creates
  the tag), with notes from the changelog section and a compare link.
- `notify` — posts the outcome (released, published but no GitHub release, or
  which step failed) to Slack through the `chat.postMessage` API. A Slack
  failure never turns the release red; the run summary says whether it posted.

## Secrets and variables

| Name | Kind | Where | Used for |
| --- | --- | --- | --- |
| `MAVEN_USERNAME`, `MAVEN_PASSWORD` | secret | `maven-central` environment | Central Portal user token (see below) |
| `SIGNING_KEY`, `SIGNING_PASSWORD` | secret | `maven-central` environment | In-memory artifact signing |
| `GH_ACTIONS_SLACK_APP_TOKEN` | secret | repository | Slack bot token for the release post |
| `RELEASE_SLACK_AUDIENCE` | variable | repository | Where the release post goes; unset = no post (flagged in the run summary) |
| `RELEASE_SUCCESS_MENTION` | variable | repository | Optional space-separated Slack usergroup IDs (`S…`) pinged on success |
| `RELEASE_FAILURE_MENTION` | variable | repository | Optional space-separated Slack usergroup IDs (`S…`) pinged on failure |

Maven Central also requires the `com.meshpay` namespace to be verified on the
Central Portal (a DNS TXT record on `meshpay.com`).

## Maven Central credentials

The Central Portal has no OIDC trusted publishing, so publishing uses a Portal
user token. Only the `publish` job can read it:

- **`maven-central` environment.** The four publish secrets live only here, not
  as repository secrets. The environment requires a reviewer's approval
  (self-review is blocked, so whoever merged the release PR asks another
  reviewer to approve the `publish` job) and allows deployments from `main`
  only, so no other branch or job can reach the secrets.
- **User token, never the account password.** `MAVEN_USERNAME` /
  `MAVEN_PASSWORD` are the two halves of the Portal user token
  `android-sdk-release-token`.
- **Rotation.** The token expires on **2027-02-17**. Before then, generate a
  new token with an expiry at
  [central.sonatype.com/usertoken](https://central.sonatype.com/usertoken),
  replace both secrets in the `maven-central` environment, revoke the old
  token, and update the token name and date here.
- **Account.** The `com.meshpay` namespace belongs to a Central Portal account
  owned by the Product Engineering team. Its login is in 1Password
  (`PED SDK` vault, `Maven Central` item), and 2FA is enabled.
