# Native In-App Update System

## Status and scope

This document describes the updater **merged into the owner's Windows
production source snapshot** supplied through Google Drive. The merge started
from that production tree, preserved its two Windows-only source differences,
and did not overwrite its production Gradle identity, signing configuration,
wrapper, README structure, metadata, or screenshots.

| Item | This merged source |
|---|---|
| Application ID | `com.kirilliodas.unitconverter` |
| Version | `2.1.2`, code `4` |
| Previous shipped release | `2.1.1`, code `3` |
| Signing | Existing production keystore/alias on the owner's Windows PC; no key or password is stored in this project export |
| Gradle wrapper | Included from the production tree |
| Git state | This v2.1.2 source export intentionally contains no `.git` directory; apply it over the existing repository and review `git status` so the published history and tags are preserved |

This source is **not yet a validated release**. It must pass the included
Windows clean/test/lint/signed-build launcher and the manual device checklist
below before it is tagged or published.

The currently released v2.1.1 app contains this updater and was itself
installed through it from v2.1.0. Version 2.1.2 uses the same discovery,
download, validation, and Android-installer flow.

## User experience

- On the first composition of the app shell, `UpdateHost` requests one startup
  check. It is asynchronous and never blocks the converter.
- A startup check runs at most once per 24 hours after a successful metadata
  fetch. Offline/network failures are silent at startup.
- If no newer stable update exists, no dialog appears.
- If a newer stable release exists, a dismissible Material 3 dialog shows:
  - installed version;
  - available version;
  - release title/date/APK size when present;
  - a shortened plain-text form of the release notes;
  - **Download update**;
  - **View release notes** (opens the exact GitHub release page in a browser);
  - **Later** / close.
- Dismissing stores that release tag. The same version is not prompted again at
  startup, but a newer tag will prompt. A manual Settings check bypasses both
  the interval and startup dismissal display suppression.
- Settings contains an **Updates** card with checking, up-to-date, available,
  downloading, validating, ready-to-install, and error/retry states.
- No notification, WorkManager job, silent install, root install, or forced
  update exists.

## Architecture

All new Kotlin code is under:

```text
app/src/main/java/app/unitconverter/feature/update/
```

| File | Responsibility |
|---|---|
| `UpdateModels.kt` | Semantic versions, release/asset models, stable-update policy, APK/checksum asset selection, SHA-256 helpers, release-note cleanup, UI state. |
| `GitHubUpdateClient.kt` | Minimal strict JSON parser, GitHub latest-release parser, injectable HTTP transport, `HttpURLConnection` transport, HTTP/rate-limit mapping. |
| `UpdatePreferences.kt` | Separate update-state serializer/store over the existing app-private SharedPreferences storage. |
| `UpdateDownloadManager.kt` | HTTPS APK download into `cacheDir/updates/`, progress, cancellation, size/storage checks, reuse, deletion, SHA-256 calculation. |
| `ApkValidator.kt` | PackageManager APK parsing; package ID, semantic version, version-code, and signing-certificate checks. |
| `UpdateInstaller.kt` | FileProvider content URI, unknown-apps permission handoff, standard package-installer intent. |
| `UpdateManager.kt` | App-shell coordinator and `StateFlow<UpdateUiState>`; startup/manual checks, dismissal, download/validation/install/delete flows. |
| `UpdateUi.kt` | `UpdateHost`, update dialog, Settings update card, byte/date formatting, localized error mapping. |

Integration points are intentionally small:

- `UnitConverterApp.kt` wraps the existing shell in `UpdateHost`.
- `ConverterScreen.kt` only receives and passes `UpdateManager` to Settings.
- `SettingsScreen.kt` only adds the Updates section/card.
- `AndroidManifest.xml` adds update permissions and the FileProvider.
- `app/build.gradle.kts` / `gradle/libs.versions.toml` add one explicit
  `androidx.core:core:1.17.0` dependency used directly for `FileProvider`.
- No HTTP client, JSON, WorkManager, Navigation, DI, or ViewModel dependency
  was added.

Data flow:

```text
App startup or Settings check
  -> UpdateManager
  -> GitHubUpdateClient (Dispatchers.IO)
  -> GitHub /releases/latest JSON
  -> UpdatePolicy stable/SemVer/APK-asset decision
  -> dialog or silent state
  -> UpdateDownloadManager (.part -> cacheDir/updates/*.apk)
  -> optional published SHA-256 verification
  -> ApkValidator (package/versionCode/versionName/signing certificates)
  -> explicit Install action
  -> FileProvider content URI -> Android package installer
```

## GitHub release source and semantics

The only update endpoint is the constant in `UpdatePolicy`:

```text
https://api.github.com/repos/kiriliodas/Unit-Converter-V2/releases/latest
```

GitHub's REST documentation describes `/releases/latest` as the latest
published full release: the most recent **non-prerelease, non-draft** release,
sorted by `created_at`. See the official GitHub reference:
<https://docs.github.com/en/rest/releases/releases#get-the-latest-release>.

The app does not rely on that statement blindly. It also:

- rejects `draft == true`;
- rejects `prerelease == true`;
- rejects hyphenated prerelease SemVer tags;
- compares numeric SemVer components, so a later-created backport with an
  older/equal version is treated as up to date;
- requires `tag_name` and an HTTPS `html_url`;
- caps release JSON at 2 MiB and fails safely on malformed JSON;
- maps 404, 403-with-zero-rate-limit/429, other HTTP statuses, timeouts, SSL,
  DNS, and network errors to typed states;
- sends `Accept: application/vnd.github+json`, a stable User-Agent, and
  `X-GitHub-Api-Version: 2022-11-28`;
- uses no GitHub token or embedded secret.

### How a release must be published

For a future version to be offered:

1. Build from the production source tree after merging and validating this
   feature.
2. Set both:
   - a strictly greater Android `versionCode` than every distributed build
     (after v2.0.0 code `1`, the next must be at least `2`); and
   - a strictly greater stable SemVer `versionName` (for example `2.1.0`).
3. Sign the APK with the **same production keystore and alias** already used by
   the installed app. Never generate a casual replacement key.
4. Verify the signed APK locally (signature, package, version, device install
   over the previous production version).
5. Create a GitHub Release whose tag is the matching stable version, for
   example `v2.1.0`.
6. Publish it as a full release: **not draft, not prerelease**, and ensure it is
   the repository's latest published full release.
7. Upload exactly one official APK, preferably named:

   ```text
   Unit-Converter-V2.1.0.apk
   ```

8. Recommended: upload a checksum sidecar named exactly:

   ```text
   Unit-Converter-V2.1.0.apk.sha256
   ```

   containing either the 64-hex-digit hash alone or a standard
   `hash  Unit-Converter-V2.1.0.apk` line.
9. Put user-facing changes in the release body. The app strips Markdown/HTML
   decoration and truncates for the dialog; the full page remains available
   from **View release notes**.

Do not use GitHub's automatic source ZIP/tarball as the update. The asset
selector only considers uploaded assets whose names end in `.apk` and whose
initial download URLs are under this repository's official
`https://github.com/kiriliodas/Unit-Converter-V2/releases/download/` prefix;
GitHub's subsequent HTTPS redirects to its asset storage are followed.

### Asset selection

Ranking is deterministic:

1. exact `Unit-Converter-V<version>.apk`;
2. another `Unit-Converter-V<semver>.apk` / `Unit-Converter-<semver>.apk` official-style name;
3. another name containing `Unit-Converter`;
4. another name containing `UnitConverter`;
5. a lone other `.apk` asset.

If the best rank is tied by multiple APKs, the release is treated as missing a
usable APK rather than guessing. This avoids downloading an arbitrary asset if
a release accidentally contains several APKs.

A checksum asset is recognized if it is `<apk-name>.sha256`, a conventional
`SHA256SUMS`/`checksums.txt` file, or an unambiguous SHA-256 file tied to the
APK stem; checksum assets are capped at 1 MiB.

## Version strategy and limitations

GitHub does not provide Android `versionCode` as structured release metadata.
The app therefore uses a two-stage strategy:

1. **Discovery:** numeric SemVer comparison of:
   - remote stable tag; and
   - installed Android `versionName`.
2. **Before installation:** downloaded APK validation compares:
   - APK package ID to the running app package;
   - APK `versionName` to the release tag;
   - APK long `versionCode` strictly greater than installed long version code;
   - APK signing-certificate SHA-256 set exactly to the installed app set.

Rules:

- Tags may start with `v` or `V`.
- `2.0.10 > 2.0.9`; components are parsed as numbers, never compared as text.
- SemVer build metadata (`+...`) is accepted in a tag and ignored.
- Hyphen suffixes in a remote tag are treated as prerelease/dev and rejected.
- Installed development names such as `0.0.1-dev` use only their numeric core
  for comparison, so development builds can exercise discovery safely.
- Leading-zero components such as `v02.0.1` are rejected.
- Unparseable remote/installed versions produce no update offer.

Consequences:

- Increasing only `versionCode` while keeping the same `versionName` will not
  be discovered. Bump both for an update release.
- A development build with a different application ID may discover a newer
  official SemVer, but APK validation will reject the production APK as the
  wrong package. That is intentional; only the production build can install
  production updates.
- Strict signing-set equality does not attempt to support signing-key rotation
  in this first implementation. The Android installer remains the final
  authority, but the precheck will reject a different certificate set.

## Download, validation, and storage

Downloads use `HttpURLConnection` on `Dispatchers.IO`:

- HTTPS only, redirects enabled for GitHub asset delivery;
- 15-second connect timeout and 30-second read timeout;
- progress callbacks with downloaded/total bytes and percentage when GitHub or
  the asset metadata gives a usable total;
- cancellation disconnects the active connection and removes the `.part` file;
- expected asset size and HTTP content length are checked when available;
- an initial usable-space check requires expected bytes plus a 10 MiB margin;
- target names are generated locally from the sanitized tag and GitHub asset
  ID, for example `unit-converter-v2.1.0-123456.apk`;
- completed files live only in `cacheDir/updates/`;
- a same-asset file with matching size is reused instead of downloaded again;
- no broad storage permission or unrelated file overwrite is used.

Validation order after download:

1. file exists and is non-empty;
2. HTTP/metadata byte counts match when known;
3. SHA-256 is calculated;
4. if a recognized checksum asset was published, it is downloaded and its
   expected hash must match; mismatch deletes the APK;
5. Android `PackageManager.getPackageArchiveInfo` must parse the APK;
6. package ID must equal the running app package;
7. APK `versionName` must equal the release SemVer;
8. APK `versionCode` must be greater than installed;
9. signing-certificate SHA-256 set must exactly equal the installed app set;
10. only then is the state **Ready to install**.

If no checksum asset is published, the UI says so. The APK is not described as
checksum-verified; integrity/authenticity rely on HTTPS plus Android package,
version, and signing-certificate validation. The signatures are the critical
identity check: an APK signed by a different key is deleted and never handed
to the installer by the success path.

## Installation and Android security

After validation, installation still requires an explicit **Install update**
tap. The app:

- rechecks file size, stored SHA-256, package, version, and signing set;
- creates a FileProvider URI using authority
  `${applicationId}.fileprovider`;
- exposes only `cacheDir/updates/` through `res/xml/update_file_paths.xml`;
- grants temporary read permission to the package installer;
- opens `ACTION_VIEW` with MIME type
  `application/vnd.android.package-archive`;
- never uses `file://`, silent installation, root, or PackageInstaller session
  tricks.

On API 26+, Android applies the per-app **Install unknown apps** capability.
The manifest declares `REQUEST_INSTALL_PACKAGES`; if
`PackageManager.canRequestPackageInstalls()` is false, the app opens
`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for its own package and tells the
user to allow it and tap Install again.

Opening the installer is not reported as success. The user may cancel, Android
may reject the APK, or the device may have insufficient space. The app remains
in **Ready to install** state until a later launch/check observes the installed
version has advanced and stale downloaded state is cleaned.

## Manifest and permissions

Added:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

- `INTERNET` is used only to read GitHub release metadata/checksums and to
  download an explicitly selected APK. Conversion, catalog, favorites, pins,
  history, settings, and translations remain fully offline.
- `REQUEST_INSTALL_PACKAGES` is required for this app to ask Android's package
  installer to install an APK on API 26+. It does not grant silent install.
- `androidx.core.content.FileProvider` is non-exported, grants URI permissions
  only through intents, and is limited to `cacheDir/updates/`.

No storage, notification, account, location, analytics, advertising, or
background-work permission/component was added.

## Persistence

Existing keys and payloads are untouched:

- `UC_LIBRARY_V1` remains the conversion-library payload.
- `UC_APP_PREFERENCES_V1` remains theme/language.

New update state uses the same app-private SharedPreferences file
`conversion_library` but a separate key:

```text
SharedPreferences key: update_state_v1
Payload header:        UC_UPDATE_STATE_V1
```

Stored fields:

- `LAST_SUCCESSFUL_CHECK` — epoch milliseconds of the last successfully parsed
  GitHub release response;
- `DISMISSED_TAG` — latest dismissed release tag;
- `LAST_KNOWN_TAG` — latest observed release tag;
- `DOWNLOADED` — tag, generated cache filename, byte size, SHA-256, and whether
  a published checksum was verified.

Malformed/unknown records are ignored. Downloaded filenames are revalidated
as safe `.apk` names inside `cacheDir/updates/`; stale/missing/size-mismatched
downloads are cleaned.

## Localization and accessibility

Fifty-one new string resources were added to every shipped locale:

- English, French, Spanish, Portuguese, German, Italian, Simplified Chinese,
  Japanese, Korean, and Arabic.

Each locale now has 280 strings plus the existing plural. Kotlin update UI uses
`stringResource` for visible text. Buttons have at least 48 dp height, the
dialog is scrollable for large fonts, icons that are purely decorative have no
duplicate content description, the close button is labelled, and the design
uses the existing light/dark monochrome tokens. Symbol/layout behavior follows
the current RTL-aware Material 3 UI.

## Tests

New JVM tests under `app/src/test/java/app/unitconverter/feature/update/`:

| File | Tests | Coverage |
|---|---:|---|
| `UpdatePolicyTest.kt` | 10 | SemVer numeric comparison, `v` tags, prerelease suffixes, development installed versions, up-to-date/newer decisions, draft/prerelease rejection, missing/ambiguous APK, official/lone APK selection, 24-hour interval, dismissed versions. |
| `GitHubUpdateClientTest.kt` | 5 | GitHub JSON parsing, assets/checksum selection inputs, malformed/missing metadata, draft/prerelease flags, 404/rate-limit/HTTP mapping, injected transport. |
| `UpdatePreferencesTest.kt` | 3 | Serializer round-trip, malformed/unknown/path-traversal records, separate key/header. |
| `UpdateUtilitiesTest.kt` | 6 | Known SHA-256 digest/stream, checksum-list parsing/ambiguity, release-note cleanup/truncation, progress fractions. |

Static test count after this change: **177 `@Test` methods in 22 JVM files**
(the production v2.0.0 suite had 153 in 18 files).

These tests were **not executed during the Arena merge**: that environment has
JDK 11 and no usable Android SDK/ADB/emulator/device, although this production
tree includes the Gradle wrapper. Run them on Windows before release.
Android-specific download, PackageManager, FileProvider, and UI behavior also
have no instrumented tests yet.

## Windows validation commands

Run in this production tree after replacement. Confirm the production
application ID, version, signing environment/config, and wrapper files first.
The included `BUILD-UNIT-CONVERTER.bat -RequireVersionBump` launcher is the
preferred one-click path; the equivalent core sequence is below. Stop after
any failure.

```powershell
Set-Location 'C:\Users\Etrigane_House\Desktop\Unit Converter'

if (-not (Test-Path '.\gradlew.bat')) { throw 'gradlew.bat missing. Stop.' }
if (-not (Test-Path '.\gradle\wrapper\gradle-wrapper.jar')) { throw 'gradle-wrapper.jar missing. Stop.' }
# local.properties is optional when ANDROID_HOME/ANDROID_SDK_ROOT or the default SDK path is valid.

java -version
if ($LASTEXITCODE -ne 0) { throw 'Java check failed. Stop.' }

.\gradlew.bat --version
if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper check failed. Stop.' }

git status --short
if ($LASTEXITCODE -ne 0) { throw 'git status failed. Stop.' }

.\gradlew.bat :app:compileDebugKotlin
if ($LASTEXITCODE -ne 0) { throw 'compileDebugKotlin failed. Stop.' }

.\gradlew.bat :app:testDebugUnitTest
if ($LASTEXITCODE -ne 0) { throw 'testDebugUnitTest failed. Stop.' }

.\gradlew.bat :app:lintDebug
if ($LASTEXITCODE -ne 0) { throw 'lintDebug failed. Stop.' }

.\gradlew.bat :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'assembleDebug failed. Stop.' }
```

Expected static unit-test count before any further edits: 177. Record the
actual Gradle-reported count.

Only after debug gates and device smoke tests pass, build/sign the production
release with the existing local signing process. Verify the exact final APK
with `apksigner verify --verbose --print-certs`, `aapt2 dump badging`, and
`Get-FileHash -Algorithm SHA256`, checking `$LASTEXITCODE` after native tools
and confirming the signer is unchanged before uploading.

## Manual device-test checklist

Use the production application ID and existing production signing identity. Do
not test the complete install path with a development application ID against
official production APKs; package validation should reject them.

### Safe test-release setup

Do not publish a throwaway stable release to the official repository merely to
test. Prefer one of:

- a disposable build in a scratch branch with `UpdatePolicy` constants pointed
  at a test repository, then discard that constant change; or
- a carefully planned real next release after all other validation passes.

The test APK must have the same production application ID and signing
certificate as the installed test build, greater `versionCode`, and greater
SemVer `versionName`.

### Checklist

- [ ] Fresh install: converter opens immediately and remains usable offline.
- [ ] Airplane mode startup: no dialog/crash; manual check shows a network
      error only in Settings/dialog error state.
- [ ] No newer release: startup silent; Settings manual check says up to date.
- [ ] Repeated app restarts within 24 hours do not repeatedly hit GitHub after
      a successful check.
- [ ] New stable release: dialog shows correct current/new versions, notes,
      publication date, and APK size.
- [ ] **Later** suppresses the same tag at next startup; Settings still allows
      viewing/manual checking.
- [ ] A newer tag after a dismissed tag prompts again.
- [ ] Draft and prerelease releases are never offered.
- [ ] **View release notes** opens the exact detected tag page.
- [ ] Download progress advances; Cancel stops and cleans the partial file.
- [ ] Network loss during download produces a retryable error and no corrupt
      ready state.
- [ ] Low-storage device/emulator state is handled without crashing.
- [ ] Re-tapping Download after a completed/validated download reuses the file.
- [ ] Published checksum mismatch deletes the APK and blocks installation.
- [ ] APK with wrong package ID, wrong signature, same/lower versionCode, or
      mismatched versionName is deleted/blocked with the correct message.
- [ ] **Install update** first routes to unknown-apps settings when permission
      is denied; after allowing, tapping Install opens Android's installer.
- [ ] Cancelling Android's installer returns to the app without claiming an
      installation occurred.
- [ ] Successful install upgrades in place and preserves favorites, pins,
      history, precision, theme, and language.
- [ ] After upgrade, stale downloaded-update state is cleaned and manual check
      reports up to date.
- [ ] Test light/dark/System themes, largest system font, TalkBack focus order,
      and every language, especially Arabic RTL.
- [ ] Rotate/recreate the Activity during check/download dialogs; verify no
      duplicate prompt, stuck spinner, or corrupted state.
- [ ] Convert representative values in Length, Temperature, Fuel, Data storage
      (`B`/`bit`, `KM`), and Scientific formatting including `-1E-10`.

## Known limitations and unresolved questions

- **Windows validation pending:** this merge was based on the supplied Drive
  snapshot. Confirm there were no later local edits, then run the full Windows
  launcher and inspect `git diff` before committing.
- **No Android build/device evidence yet:** compile, lint, unit tests, and a
  physical-device update/install cycle remain mandatory.
- **No resume:** interrupted downloads restart from byte zero; only completed
  APKs are reused.
- **Process lifetime:** downloads are coroutine-based while the app process is
  alive. There is no notification or background worker if Android kills the
  process.
- **GitHub availability/rate limits:** unauthenticated public API limits and
  network failures fail closed and are retryable manually.
- **Latest semantics:** GitHub selects `/latest` primarily by release creation
  metadata; numeric SemVer checks prevent downgrade offers.
- **VersionCode discovery:** GitHub has no structured Android versionCode, so a
  release must increase SemVer as well as versionCode.
- **Checksum trust:** a sidecar in the same release detects corruption/substitution
  after publication only if the release account/assets remain trustworthy;
  package signature equality remains the decisive Android identity check.
- **Signing rotation:** strict certificate-set equality assumes the same
  production key continues to be used, as required for this project.
- **Store/listing wording:** any external listing copy that says the app has
  no network permission must be updated before shipping this feature. The
  converter remains offline; network is used only for update metadata/APK
  download. The project README in this merge has been corrected.
