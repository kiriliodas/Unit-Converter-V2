# Replacing the project and publishing v2.1.0

This guide is for the complete replacement ZIP based on the Windows
production snapshot supplied by the project owner.

## 1. Replace the Windows folder safely

The ZIP contains a complete top-level folder named `Unit Converter`,
including source, resources, Gradle wrapper, tests, documentation, build
launcher, metadata, screenshots, and the `.git` directory from the supplied
snapshot. Generated `app/build` output and the old v2.0.0 APK copy are not
included because they are rebuilt locally and must not be confused with the
new release.

Recommended replacement method:

1. Close Android Studio and any terminal using the project.
2. Extract the ZIP somewhere temporary, for example Downloads.
3. Rename the existing Desktop folder from `Unit Converter` to
   `Unit Converter - backup before v2.1.0`.
4. Move the newly extracted `Unit Converter` folder to the Desktop so the
   active path is again:

   ```text
   C:\Users\Etrigane_House\Desktop\Unit Converter
   ```

5. Do not delete the backup until v2.1.0 builds, installs, and is published
   successfully.
6. If the backup contains changes made **after** the Drive snapshot was
   uploaded, stop and report them before committing. The replacement ZIP is
   based on that snapshot.
7. `local.properties` is machine-local and is intentionally not required in
   the ZIP. The build launcher sets the Android SDK environment when it can
   find the SDK. If Android Studio or Gradle cannot find the SDK, copy only
   `local.properties` back from the backup.

Never copy a `.jks` file, signing password, token, or other secret into the
project folder.

## 2. Build and validate v2.1.0

Open PowerShell and run:

```powershell
Set-Location 'C:\Users\Etrigane_House\Desktop\Unit Converter'

git status --short
if ($LASTEXITCODE -ne 0) { throw 'git status failed. Stop.' }

.\BUILD-UNIT-CONVERTER.bat -RequireVersionBump
if ($LASTEXITCODE -ne 0) { throw 'Build launcher failed. Stop and send the newest complete log from build-logs.' }
```

The launcher runs clean, all JVM unit tests, `lintDebug`, the signed release
build, APK signature verification, package/version verification, and SHA-256
generation. A successful run should produce:

```text
release-output\Unit-Converter-V2.1.0.apk
release-output\Unit-Converter-V2.1.0.apk.sha256
```

Install that exact APK manually over v2.0.0 and perform the device checks in
`UPDATE_SYSTEM.md`. If code changes after this build, discard the APK as a
release candidate and run the launcher again. Do not tag or publish after a
failed step.

## 3. Commit and tag

Run these commands only after the build and device checks pass:

```powershell
Set-Location 'C:\Users\Etrigane_House\Desktop\Unit Converter'

function Assert-Ok([string]$Step) {
    if ($LASTEXITCODE -ne 0) { throw "$Step failed with exit code $LASTEXITCODE. Stop." }
}

$Apk = (Resolve-Path '.\release-output\Unit-Converter-V2.1.0.apk').Path
$Checksum = "$Apk.sha256"

Get-Item $Apk, $Checksum
certutil -hashfile $Apk SHA256; Assert-Ok 'certutil SHA256'
Get-Content $Checksum

git status --short; Assert-Ok 'git status'
git diff --stat; Assert-Ok 'git diff --stat'

git add -A; Assert-Ok 'git add'

git status --short; Assert-Ok 'git staged status'
git diff --cached --check; Assert-Ok 'git diff --cached --check'
git diff --cached --stat; Assert-Ok 'git diff --cached --stat'

# Safety check: this must print nothing.
git diff --cached --name-only | Select-String -Pattern '\.(jks|keystore|p12|pfx|apk|aab)$|build-logs/|release-output/'

git commit -m 'Add native in-app updates for v2.1.0'; Assert-Ok 'git commit'
git tag -a v2.1.0 -m 'Unit Converter V2.1.0'; Assert-Ok 'git tag'

git push origin main; Assert-Ok 'git push main'
git push origin v2.1.0; Assert-Ok 'git push tag'
```

If the safety check prints a keystore, APK, AAB, build log, or release-output
path, stop before committing and investigate. The signed APK is uploaded as
a GitHub Release asset, not committed to Git.

## 4. Create the GitHub release as a draft

The GitHub CLI supports attaching local asset files, reading notes from a
file, verifying that the remote tag exists, and saving the release as a draft.
See the official command references:

- https://cli.github.com/manual/gh_release_create
- https://cli.github.com/manual/gh_release_edit

Continue in the same PowerShell session:

```powershell
$Repo = 'kiriliodas/Unit-Converter-V2'

gh auth status; Assert-Ok 'gh auth status'
gh release list --repo $Repo --limit 10; Assert-Ok 'gh release list'

gh release create v2.1.0 $Apk $Checksum `
    --repo $Repo `
    --verify-tag `
    --draft `
    --title 'Unit Converter V2.1.0' `
    --notes-file '.\release-notes-v2.1.0.md'
Assert-Ok 'gh release create draft'

gh release view v2.1.0 --repo $Repo --json tagName,name,isDraft,isPrerelease,assets,url
Assert-Ok 'gh release view draft'
```

Open the draft on GitHub and confirm:

- tag is `v2.1.0`;
- title is `Unit Converter V2.1.0`;
- it is not marked as a prerelease;
- exactly one uploaded APK asset is named `Unit-Converter-V2.1.0.apk`;
- `Unit-Converter-V2.1.0.apk.sha256` is attached;
- the release notes are correct.

GitHub's automatically generated source-code ZIP/tarball links are separate;
the updater ignores them and selects only the uploaded APK asset.

## 5. Publish the draft as Latest

Only after checking the draft, run:

```powershell
gh release edit v2.1.0 --repo $Repo --draft=false --latest
Assert-Ok 'gh release publish'

gh release view v2.1.0 --repo $Repo --json tagName,name,isDraft,isPrerelease,assets,url,publishedAt
Assert-Ok 'gh release view published'
```

Expected final state:

- `isDraft` is `false`;
- `isPrerelease` is `false`;
- the canonical APK and its `.sha256` file are attached;
- the release page is available at
  `https://github.com/kiriliodas/Unit-Converter-V2/releases/tag/v2.1.0`.

Users on v2.0.0 must install v2.1.0 manually once because v2.0.0 does not
contain the updater. Future stable releases can then be discovered inside
v2.1.0, provided every future release increases both `versionName` and
Android `versionCode`, keeps the same application ID, and uses the same
production signing identity.
