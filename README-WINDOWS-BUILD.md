# Unit Converter V2 - Windows build launcher

These files automate the normal Windows validation and signed release-build
sequence for the production project currently located at:

```text
C:\Users\Etrigane_House\Desktop\Unit Converter
```

## Install the launcher

Extract these two files into the project root, beside `gradlew.bat`:

- `BUILD-UNIT-CONVERTER.bat`
- `Build-UnitConverter.ps1`

Keep both files together. The `.bat` file is the double-click launcher; the
PowerShell file performs the logged, fail-fast build sequence and provides a
hidden password prompt.

Do **not** copy the `.jks` keystore into the project.

## Normal use

Double-click:

```text
BUILD-UNIT-CONVERTER.bat
```

The default run performs, in order:

1. JDK 17 and Android SDK 36 detection.
2. `gradlew --version`.
3. `:app:clean`.
4. `:app:testDebugUnitTest`.
5. `:app:lintDebug`.
6. Hidden signing-password prompts, if the corresponding environment
   variables are not already set.
7. `:app:assembleRelease`.
8. APK signature verification with `apksigner`.
9. Package/version verification with `aapt2`.
10. SHA-256 creation and a distinctly named copy in `release-output`.

The script stops immediately if a step fails. It does not continue to lint,
release compilation, or signing after a failed test or lint step.

## Signing behaviour

The inspected production `app/build.gradle.kts` already contains a `release`
signing configuration. It obtains the keystore from
`UNIT_CONVERTER_KEYSTORE`, or defaults to:

```text
%USERPROFILE%\Documents\AndroidSigning\unit-converter-v2-release.jks
```

The launcher checks that this file exists but does not copy or upload it.

When needed, it prompts for:

- `UNIT_CONVERTER_STORE_PASSWORD`
- `UNIT_CONVERTER_KEY_PASSWORD`

The typed passwords are hidden, used only for the child Gradle process, and
are not written to the log. At the key-password prompt, press Enter to reuse
the store password if both passwords are the same.

Never send the keystore file or either password in chat, email, Git, or a
project ZIP.

## Outputs

A successful full run creates a signed APK and checksum under:

```text
release-output\Unit-Converter-V<versionName>.apk
release-output\Unit-Converter-V<versionName>.apk.sha256
```

If that output name already belongs to a different APK, the new output name
receives a timestamp suffix instead of silently replacing it.

Every run creates a log under:

```text
build-logs\UnitConverter-build-YYYYMMDD-HHMMSS.log
```

Revision r1 also captures external Gradle/apksigner stdout and stderr with
`Tee-Object`, merges that output into the main log, and inserts JUnit XML or
lint text-report failure details when those reports exist. This fixes the
truncated native-command output seen in the 20261029-161338 transcript.

Revision r2 converts native stderr records to plain text before logging (so
ordinary SDK warnings do not look like `NativeCommandError` failures) and
computes SHA-256 directly with .NET instead of `Get-FileHash`, which was
unavailable in the Windows PowerShell 5.1 session used for build
20261029-163233.

If a run fails, send the complete newest log file. That is normally enough to
identify the first real error. Do not send signing secrets.

## Version warning

This merged Windows tree is version `2.1.0`, versionCode `2`, and contains
the native in-app update implementation. The launcher validates and builds
whatever source is currently in the Windows folder.

Before publishing a real update, both values in `app/build.gradle.kts` must
be increased, for example to a newer semantic version and a larger integer
versionCode. The launcher warns when it builds `2.0.0 (1)` so that a
validation APK is not accidentally mistaken for a distributable update.

## Optional command-line modes

From PowerShell or Command Prompt in the project root:

```bat
BUILD-UNIT-CONVERTER.bat -Quick
```

Skips `:app:clean`; Gradle up-to-date checks decide what reruns.

```bat
BUILD-UNIT-CONVERTER.bat -SkipRelease
```

Runs the JVM tests and lint, but does not request signing passwords or build
the release APK.

```bat
BUILD-UNIT-CONVERTER.bat -RequireVersionBump
```

Fails if the generated APK is still `2.0.0 (1)`. This is useful immediately
before preparing a distributable update.

## Equivalent core Gradle commands

The launcher is the recommended path, but its core Gradle sequence is:

```powershell
.\gradlew.bat :app:clean --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace
.\gradlew.bat :app:lintDebug --no-daemon --stacktrace
.\gradlew.bat :app:assembleRelease --no-daemon --stacktrace
```

Do not run a later command after an earlier command fails. The release
command additionally requires the signing environment described above.
