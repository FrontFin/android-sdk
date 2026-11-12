# Releasing

The version in `gradle/libs.versions.toml` (`meshpay-link`) is the single source
of truth. `BuildConfig.SDK_VERSION` and the release workflow both read it; it is
never hand-maintained anywhere else.

Releasing is just:

1. **Bump the version.** Run the `bump-version` Claude Code skill
   ([`.claude/commands/bump-version.md`](.claude/commands/bump-version.md)). It
   diffs `HEAD` against the latest tag, classifies the change as MAJOR / MINOR /
   PATCH from the public API, updates `meshpay-link` in
   `gradle/libs.versions.toml`, and prepends a `## X.Y.Z` entry to
   [`CHANGELOG.md`](CHANGELOG.md). While on `0.x`, a MAJOR-class change bumps the
   minor number.
2. **Open a PR and merge it to `main`.**

On merge, [`release.yml`](.github/workflows/release.yml) does the rest:

- `check-version` — reads `meshpay-link` and the latest tag. Same version → no
  release (ends green). Lower → fails (downgrade). Tag already exists → fails.
- Validates that `CHANGELOG.md` has the matching `## X.Y.Z` section.
- Re-runs the CI gates on the release commit.
- `publish` — `./gradlew link:publishAndReleaseToMavenCentral` to the Central
  Portal, signed in memory.
- `release` — `gh release create X.Y.Z --target main` (this creates the tag),
  with notes from the changelog section and a compare link.
- Posts a Slack announcement.

## Secrets

| Secret | Used for |
| --- | --- |
| `MAVEN_USERNAME`, `MAVEN_PASSWORD` | Central Portal publishing |
| `SIGNING_KEY`, `SIGNING_PASSWORD` | In-memory artifact signing |
| `SLACK_WEBHOOK_URL` | Release announcement |

Maven Central also requires the `com.meshpay` namespace to be verified on the
Central Portal (a DNS TXT record on `meshpay.com`).
