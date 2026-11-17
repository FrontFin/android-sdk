---
name: bump-version
description: >
  Bumps the com.meshpay:link version using semantic versioning, then updates
  CHANGELOG.md. Use this skill whenever the user asks to bump the version, release a new
  version, update the version number, prepare a release, or update the changelog. Also
  trigger when the user says things like "we're ready to release" or "what version should
  this be?".

  Steps: diff HEAD against the latest git tag → classify changes as MAJOR/MINOR/PATCH →
  increment the `meshpay-link` entry in gradle/libs.versions.toml → prepend a new entry to
  CHANGELOG.md.
---

## bump-version skill

### Step 1 — Sync with remote, find the latest tag, and build the diff

Fetch the latest commits and all tags so the diff reflects the published state:

```bash
git fetch origin main --tags
```

Find the latest tag (semver-sorted). Android tags have no leading `v`:

```bash
LATEST_TAG=$(git tag --sort=-v:refname | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | head -n 1)
echo "Latest tag: ${LATEST_TAG:-<none>}"
```

If no tags exist, treat the full history as the diff (`git log --oneline`) and assume the
current version in `gradle/libs.versions.toml` is the starting point.

```bash
git diff <latest-tag>..HEAD --stat
git diff <latest-tag>..HEAD -- \
  'link/src/main/java/com/meshpay/link/LinkConfiguration.kt' \
  'link/src/main/java/com/meshpay/link/LinkOptions.kt' \
  'link/src/main/java/com/meshpay/link/LinkResult.kt' \
  'link/src/main/java/com/meshpay/link/LinkError.kt' \
  'link/src/main/java/com/meshpay/link/LinkContract.kt' \
  'link/src/main/java/com/meshpay/link/LinkHandle.kt'
```

Also capture the commit log for the changelog summary:

```bash
git log <latest-tag>..HEAD --oneline
```

---

### Step 2 — Classify the bump

Analyse the diff against the public API (apply the highest matching level):

#### MAJOR — any of:
- A public type is **deleted or renamed** (`LinkConfiguration`, `LinkOptions`,
  `LinkResult`, `LinkExitStatus`, `LinkError`, `LinkErrorCode`, `LinkEnvironment`,
  `LinkTheme`, `LinkContract`, `LinkHandle`)
- A public property/parameter is removed, or a **required** parameter is added
- A value is removed from `LinkErrorCode`, `LinkExitStatus`, `LinkEnvironment` or `LinkTheme`
- The entry point or callback/result semantics change in a breaking way
- `minSdk` is raised

#### MINOR — any of (and no MAJOR):
- A new **optional** parameter or option is added
- A new public type, or a new value in a public enum / sealed hierarchy

#### PATCH — everything else:
- Bug fixes, internal refactors (anything under `internal/`), test additions
- README, CHANGELOG, `CLAUDE.md` edits
- Dependency bumps that don't affect the public API
- Changes to `example/`, CI workflows or Gradle config that don't change the API

While the version is `0.x`, a MAJOR-class change bumps the minor number instead.

When in doubt between MINOR and PATCH, prefer MINOR. When in doubt between MAJOR and MINOR,
**ask the user** before proceeding.

---

### Step 3 — Read and bump the version

```bash
grep -E '^meshpay-link\s*=' gradle/libs.versions.toml
```

Apply the bump:
- **MAJOR**: `1.3.0` → `2.0.0`
- **MINOR**: `1.3.0` → `1.4.0`
- **PATCH**: `1.3.0` → `1.3.1`

Edit **only** the `meshpay-link = "X.Y.Z"` line in `gradle/libs.versions.toml`, nothing
else. `BuildConfig.SDK_VERSION` and the release workflow both read this one value.

---

### Step 4 — Update CHANGELOG.md

Follow [Keep a Changelog](https://keepachangelog.com/en/1.0.0/). Prepend a new section at
the very top:

```markdown
## X.Y.Z

### Added
- ...

### Changed
- ...

### Fixed
- ...

### Removed
- ...
```

Rules:
- No date — only the version number: `## X.Y.Z`.
- Always include at least one `### Added` / `### Changed` / `### Fixed` / `### Removed`
  sub-heading; omit empty ones.
- One line per bullet, describing **what changed and why it matters** to a consumer, not
  internal implementation details.
- `example/`, tests, `CLAUDE.md` and `README.md` changes go under **Changed** only if they
  affect the developer experience.

---

### Step 5 — Report to the user

```
Bumped version: 0.1.0 → 0.2.0  (MINOR)

Reason: added `displayFiatCurrency` option to LinkOptions.

Files updated:
  • gradle/libs.versions.toml   (meshpay-link)
  • CHANGELOG.md                (new entry prepended)
```

Then ask whether to commit. On yes, stage only the two files:

```bash
git add gradle/libs.versions.toml CHANGELOG.md
git commit -m "chore: bump version to X.Y.Z"
```
