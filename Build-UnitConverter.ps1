[CmdletBinding()]
param(
    [switch]$Quick,
    [switch]$SkipRelease,
    [switch]$RequireVersionBump
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $ProjectRoot

$GradleBat = Join-Path $ProjectRoot 'gradlew.bat'
$ScriptStarted = Get-Date
$Timestamp = $ScriptStarted.ToString('yyyyMMdd-HHmmss')
$LogDirectory = Join-Path $ProjectRoot 'build-logs'
$LogFile = Join-Path $LogDirectory ("UnitConverter-build-$Timestamp.log")
$NativeLogFile = Join-Path $LogDirectory ("UnitConverter-build-$Timestamp.commands.log")
$ReleaseOutputDirectory = Join-Path $ProjectRoot 'release-output'

$script:TranscriptActive = $false
$script:CurrentStep = 'Preflight'
$script:ExitCode = 0
$script:StorePasswordWasPrompted = $false
$script:KeyPasswordWasPrompted = $false
$script:JavaExe = $null
$script:AndroidSdkRoot = $null
$script:ApkSigner = $null
$script:Aapt2 = $null
$script:FinalApk = $null
$script:FinalApkSha256 = $null

function Start-BuildTranscript {
    if ($script:TranscriptActive) { return }
    try {
        Start-Transcript -Path $LogFile -Append | Out-Null
        $script:TranscriptActive = $true
    } catch {
        $script:TranscriptActive = $false
        Write-Host "WARNING: Could not start the transcript log: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

function Stop-BuildTranscript {
    if (-not $script:TranscriptActive) { return }
    try {
        Stop-Transcript | Out-Null
    } catch {
        # The build result is more important than a transcript shutdown warning.
    }
    $script:TranscriptActive = $false
}

function Protect-OutputDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Path
    )

    New-Item -ItemType Directory -Path $Path -Force | Out-Null
    $ignorePath = Join-Path $Path '.gitignore'
    if (-not (Test-Path -LiteralPath $ignorePath)) {
        Set-Content -LiteralPath $ignorePath -Encoding Ascii -Value @('*', '!.gitignore')
    }
}

function Get-Sha256Hex {
    param(
        [Parameter(Mandatory = $true)][string]$Path
    )

    # Do not use Get-FileHash here: it was unavailable in the owner's
    # Windows PowerShell 5.1 session (build 20261029-163233), even though the
    # signed APK had already been produced and verified. The .NET SHA-256
    # implementation is available in both Windows PowerShell and PowerShell 7.
    $stream = $null
    $sha256 = $null
    try {
        $stream = [System.IO.File]::OpenRead($Path)
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        $hashBytes = $sha256.ComputeHash($stream)
        return ([System.BitConverter]::ToString($hashBytes).Replace('-', '').ToLowerInvariant())
    } finally {
        if ($null -ne $sha256) { $sha256.Dispose() }
        if ($null -ne $stream) { $stream.Dispose() }
    }
}

function Invoke-ExternalStep {
    param(
        [Parameter(Mandatory = $true)][string]$StepName,
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter()][string[]]$Arguments = @()
    )

    $script:CurrentStep = $StepName
    $commandText = "{0} {1}" -f $FilePath, ($Arguments -join ' ')
    Write-Host ""
    Write-Host "=== $StepName ===" -ForegroundColor Cyan
    Write-Host ("Command: $commandText") -ForegroundColor DarkGray

    # Windows PowerShell transcripts do not reliably capture all native
    # process stdout/stderr (Gradle output was truncated in build
    # 20261029-161338). Tee every external command to a separate command log;
    # it is merged into the main log in the finally block. Native stderr
    # records are converted to plain strings first so warnings do not appear
    # as misleading NativeCommandError blocks.
    "=== $StepName ===" | Tee-Object -FilePath $NativeLogFile -Append | Out-Null
    "Command: $commandText" | Tee-Object -FilePath $NativeLogFile -Append | Out-Null

    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $exitCode = 1
    try {
        & $FilePath @Arguments 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath $NativeLogFile -Append
        if ($null -eq $LASTEXITCODE) {
            $exitCode = 0
        } else {
            $exitCode = $LASTEXITCODE
        }
    } catch {
        ("PowerShell failed to run the external command: " + $_.Exception.Message) |
            Tee-Object -FilePath $NativeLogFile -Append | Out-Null
        throw
    } finally {
        $ErrorActionPreference = $oldPreference
    }

    "Exit code: $exitCode" | Tee-Object -FilePath $NativeLogFile -Append | Out-Null
    if ($exitCode -ne 0) {
        throw "$StepName failed with exit code $exitCode."
    }

    Write-Host "--- $StepName : OK ---" -ForegroundColor Green
}

function Merge-NativeLog {
    if (-not (Test-Path -LiteralPath $NativeLogFile -PathType Leaf)) { return }
    try {
        Add-Content -LiteralPath $LogFile -Encoding UTF8 -Value @(
            '',
            '=== CAPTURED EXTERNAL COMMAND OUTPUT (stdout + stderr) ==='
        )
        Get-Content -LiteralPath $NativeLogFile |
            Add-Content -LiteralPath $LogFile -Encoding UTF8
        Remove-Item -LiteralPath $NativeLogFile -Force
    } catch {
        Write-Host "WARNING: External command output could not be merged and remains at: $NativeLogFile" -ForegroundColor Yellow
    }
}

function Write-TestFailureDetails {
    $resultsDirectory = Join-Path $ProjectRoot 'app\build\test-results\testDebugUnitTest'
    if (-not (Test-Path -LiteralPath $resultsDirectory -PathType Container)) {
        Write-Host 'No JUnit XML test-results directory was produced; use the captured Gradle output at the end of this log.' -ForegroundColor Yellow
        return
    }

    $reported = 0
    foreach ($file in Get-ChildItem -LiteralPath $resultsDirectory -Filter '*.xml' -File) {
        try {
            [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
            foreach ($case in $xml.SelectNodes('//testcase')) {
                foreach ($bad in @($case.ChildNodes | Where-Object { $_.Name -eq 'failure' -or $_.Name -eq 'error' })) {
                    $reported++
                    Write-Host ""
                    Write-Host ("FAILED TEST: {0}.{1}" -f $case.GetAttribute('classname'), $case.GetAttribute('name')) -ForegroundColor Red
                    $message = $bad.GetAttribute('message')
                    if (-not [string]::IsNullOrWhiteSpace($message)) {
                        Write-Host "Message: $message"
                    }
                    $details = [string]$bad.InnerText
                    if ($details.Length -gt 6000) {
                        $details = $details.Substring(0, 6000) + "`n... <truncated>"
                    }
                    if (-not [string]::IsNullOrWhiteSpace($details)) {
                        Write-Host $details
                    }
                    if ($reported -ge 20) { break }
                }
                if ($reported -ge 20) { break }
            }
        } catch {
            Write-Host "WARNING: Could not parse JUnit XML file $($file.FullName): $($_.Exception.Message)" -ForegroundColor Yellow
        }
        if ($reported -ge 20) { break }
    }

    if ($reported -eq 0) {
        Write-Host 'JUnit XML results exist but contain no failure/error node; the failure may have occurred during compilation or test process startup.' -ForegroundColor Yellow
    }
}

function Write-LintFailureDetails {
    $lintTextReport = Join-Path $ProjectRoot 'app\build\reports\lint-results-debug.txt'
    if (-not (Test-Path -LiteralPath $lintTextReport -PathType Leaf)) {
        Write-Host 'No lint text report was produced; use the captured Gradle output at the end of this log.' -ForegroundColor Yellow
        return
    }

    Write-Host ""
    Write-Host '=== LINT REPORT (first 250 lines) ===' -ForegroundColor Yellow
    Get-Content -LiteralPath $lintTextReport -TotalCount 250 | ForEach-Object { Write-Host $_ }
}

$script:JavaCandidates = New-Object System.Collections.ArrayList

function Add-JavaCandidate {
    param(
        [Parameter()][string]$JavaExe
    )

    if ([string]::IsNullOrWhiteSpace($JavaExe)) { return }
    if (-not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) { return }

    try {
        $fullPath = (Resolve-Path -LiteralPath $JavaExe).Path
    } catch {
        return
    }

    if (-not $script:JavaCandidates.Contains($fullPath)) {
        [void]$script:JavaCandidates.Add($fullPath)
    }
}

function Get-JavaMajorVersion {
    param(
        [Parameter(Mandatory = $true)][string]$JavaExe
    )

    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& $JavaExe -version 2>&1)
    } catch {
        return 0
    } finally {
        $ErrorActionPreference = $oldPreference
    }

    $text = (($output | ForEach-Object { $_.ToString() }) -join ' ')
    if ($text -match 'version "1\.(?<major>\d+)') {
        return [int]$Matches['major']
    }
    if ($text -match 'version "(?<major>\d+)') {
        return [int]$Matches['major']
    }
    return 0
}

function Resolve-Java17 {
    $script:CurrentStep = 'Java 17 detection'

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        Add-JavaCandidate (Join-Path $env:JAVA_HOME 'bin\java.exe')
    }

    $javacCommand = Get-Command 'javac.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $javacCommand) {
        Add-JavaCandidate (Join-Path (Split-Path -Parent $javacCommand.Source) 'java.exe')
    }

    $javaCommand = Get-Command 'java.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $javaCommand) {
        $javaDirectory = Split-Path -Parent $javaCommand.Source
        if (Test-Path -LiteralPath (Join-Path $javaDirectory 'javac.exe') -PathType Leaf) {
            Add-JavaCandidate $javaCommand.Source
        }
    }

    Add-JavaCandidate 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
    Add-JavaCandidate 'C:\Program Files\Android\Android Studio\jre\bin\java.exe'

    $searchRoots = @(
        'C:\Program Files\Java',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Amazon Corretto'
    )
    foreach ($root in $searchRoots) {
        if (Test-Path -LiteralPath $root -PathType Container) {
            Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                Add-JavaCandidate (Join-Path $_.FullName 'bin\java.exe')
            }
        }
    }

    foreach ($candidate in $script:JavaCandidates) {
        $binDirectory = Split-Path -Parent $candidate
        $javacPath = Join-Path $binDirectory 'javac.exe'
        if (-not (Test-Path -LiteralPath $javacPath -PathType Leaf)) { continue }

        $major = Get-JavaMajorVersion -JavaExe $candidate
        Write-Host "Checked JDK: $candidate (major version $major)"
        if ($major -eq 17) {
            $script:JavaExe = $candidate
            $env:JAVA_HOME = Split-Path -Parent $binDirectory
            if ($env:Path -notlike "*$binDirectory*") {
                $env:Path = "$binDirectory;$env:Path"
            }
            Write-Host "Using JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green
            return
        }
    }

    throw 'No JDK 17 installation was found. Install/use the Android Studio JBR or another JDK 17, then run this script again.'
}

$script:SdkCandidates = New-Object System.Collections.ArrayList

function Add-SdkCandidate {
    param(
        [Parameter()][string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) { return }
    $trimmed = $Path.Trim()
    if (-not $script:SdkCandidates.Contains($trimmed)) {
        [void]$script:SdkCandidates.Add($trimmed)
    }
}

function Resolve-AndroidSdk {
    $script:CurrentStep = 'Android SDK detection'

    Add-SdkCandidate $env:ANDROID_SDK_ROOT
    Add-SdkCandidate $env:ANDROID_HOME

    $localProperties = Join-Path $ProjectRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        foreach ($line in Get-Content -LiteralPath $localProperties) {
            if ($line -match '^\s*sdk\.dir\s*=\s*(.+)$') {
                $sdkPath = $Matches[1].Trim()
                $sdkPath = $sdkPath -replace '\\:', ':'
                $sdkPath = $sdkPath -replace '\\\\', '\'
                Add-SdkCandidate $sdkPath
            }
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        Add-SdkCandidate (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    }

    foreach ($candidate in $script:SdkCandidates) {
        $platformJar = Join-Path $candidate 'platforms\android-36\android.jar'
        $buildToolsRoot = Join-Path $candidate 'build-tools'
        if (-not (Test-Path -LiteralPath $platformJar -PathType Leaf)) { continue }
        if (-not (Test-Path -LiteralPath $buildToolsRoot -PathType Container)) { continue }

        $toolDirectories = @(
            Get-ChildItem -LiteralPath $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
                Where-Object {
                    (Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat') -PathType Leaf) -and
                    (Test-Path -LiteralPath (Join-Path $_.FullName 'aapt2.exe') -PathType Leaf)
                } |
                Sort-Object @{ Expression = { try { [version]$_.Name } catch { [version]'0.0' } } } -Descending
        )

        if ($toolDirectories.Count -eq 0) { continue }

        $script:AndroidSdkRoot = $candidate
        $script:ApkSigner = Join-Path $toolDirectories[0].FullName 'apksigner.bat'
        $script:Aapt2 = Join-Path $toolDirectories[0].FullName 'aapt2.exe'
        $env:ANDROID_HOME = $candidate
        $env:ANDROID_SDK_ROOT = $candidate
        Write-Host "Using Android SDK: $candidate" -ForegroundColor Green
        Write-Host "Using build-tools: $($toolDirectories[0].FullName)"
        return
    }

    throw 'No Android SDK with platform android-36 and build-tools was found. Install them with Android Studio SDK Manager, then run this script again.'
}

function Convert-SecureStringToPlainText {
    param(
        [Parameter(Mandatory = $true)][System.Security.SecureString]$SecureString
    )

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Initialize-SigningEnvironment {
    $script:CurrentStep = 'Signing input preparation'

    # Stop the transcript before any password prompt. Password values are
    # never printed by this script and are not written to the log.
    Stop-BuildTranscript

    $storePasswordPlain = $null
    $keyPasswordPlain = $null

    try {
        Write-Host ""
        Write-Host '=== Release signing inputs ===' -ForegroundColor Cyan

        if ([string]::IsNullOrWhiteSpace($env:UNIT_CONVERTER_KEYSTORE)) {
            $env:UNIT_CONVERTER_KEYSTORE = Join-Path $env:USERPROFILE 'Documents\AndroidSigning\unit-converter-v2-release.jks'
        }

        if (-not (Test-Path -LiteralPath $env:UNIT_CONVERTER_KEYSTORE -PathType Leaf)) {
            throw "The release keystore was not found at: $env:UNIT_CONVERTER_KEYSTORE"
        }
        Write-Host "Keystore found: $env:UNIT_CONVERTER_KEYSTORE"

        if ([string]::IsNullOrEmpty($env:UNIT_CONVERTER_STORE_PASSWORD)) {
            $secureStorePassword = Read-Host 'Enter the keystore (store) password' -AsSecureString
            $storePasswordPlain = Convert-SecureStringToPlainText -SecureString $secureStorePassword
            if ([string]::IsNullOrEmpty($storePasswordPlain)) {
                throw 'The keystore password cannot be empty.'
            }
            $env:UNIT_CONVERTER_STORE_PASSWORD = $storePasswordPlain
            $script:StorePasswordWasPrompted = $true
        } else {
            $storePasswordPlain = $env:UNIT_CONVERTER_STORE_PASSWORD
            Write-Host 'Using UNIT_CONVERTER_STORE_PASSWORD from the current environment (value hidden).'
        }

        if ([string]::IsNullOrEmpty($env:UNIT_CONVERTER_KEY_PASSWORD)) {
            $secureKeyPassword = Read-Host 'Enter the key password (press Enter if it is the same as the store password)' -AsSecureString
            $keyPasswordPlain = Convert-SecureStringToPlainText -SecureString $secureKeyPassword
            if ([string]::IsNullOrEmpty($keyPasswordPlain)) {
                $keyPasswordPlain = $storePasswordPlain
            }
            $env:UNIT_CONVERTER_KEY_PASSWORD = $keyPasswordPlain
            $script:KeyPasswordWasPrompted = $true
        } else {
            Write-Host 'Using UNIT_CONVERTER_KEY_PASSWORD from the current environment (value hidden).'
        }
    } finally {
        $storePasswordPlain = $null
        $keyPasswordPlain = $null
        Start-BuildTranscript
    }

    Write-Host 'Signing inputs are ready. Password values were not logged.' -ForegroundColor Green
}

function Get-ApkBadging {
    param(
        [Parameter(Mandatory = $true)][string]$ApkPath
    )

    $script:CurrentStep = 'APK metadata inspection'
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& $script:Aapt2 dump badging $ApkPath 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldPreference
    }

    if ($exitCode -ne 0) {
        throw "aapt2 could not inspect the APK (exit code $exitCode)."
    }
    if ($output.Count -eq 0) {
        throw 'aapt2 returned no APK metadata.'
    }

    $firstLine = $output[0].ToString()
    $pattern = "package: name='(?<package>[^']+)' versionCode='(?<code>\d+)' versionName='(?<name>[^']*)'"
    if ($firstLine -notmatch $pattern) {
        throw "Could not parse APK package metadata from: $firstLine"
    }

    return [pscustomobject]@{
        PackageName = $Matches['package']
        VersionCode = [int]$Matches['code']
        VersionName = $Matches['name']
    }
}

try {
    Protect-OutputDirectory -Path $LogDirectory
    Start-BuildTranscript

    Write-Host 'Unit Converter V2 - Windows validation and signed release build' -ForegroundColor Cyan
    Write-Host "Project root: $ProjectRoot"
    Write-Host "Log file: $LogFile"
    Write-Host "Started: $ScriptStarted"

    if (-not (Test-Path -LiteralPath $GradleBat -PathType Leaf)) {
        throw 'gradlew.bat was not found. Put both build scripts in the project root, beside gradlew.bat.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot 'app\build.gradle.kts') -PathType Leaf)) {
        throw 'app\build.gradle.kts was not found. These scripts are not in the correct project root.'
    }

    Resolve-Java17
    Resolve-AndroidSdk

    Invoke-ExternalStep -StepName 'Gradle and Java versions' -FilePath $GradleBat -Arguments @('--version', '--no-daemon')

    if ($Quick) {
        Write-Host 'Quick mode: skipping clean. Gradle up-to-date checks will decide what reruns.' -ForegroundColor Yellow
    } else {
        Invoke-ExternalStep -StepName 'Clean build outputs' -FilePath $GradleBat -Arguments @(':app:clean', '--no-daemon', '--stacktrace')
    }

    Invoke-ExternalStep -StepName 'JVM unit tests (testDebugUnitTest)' -FilePath $GradleBat -Arguments @(':app:testDebugUnitTest', '--no-daemon', '--stacktrace')
    Invoke-ExternalStep -StepName 'Android lint (lintDebug)' -FilePath $GradleBat -Arguments @(':app:lintDebug', '--no-daemon', '--stacktrace')

    if (-not $SkipRelease) {
        Initialize-SigningEnvironment

        Invoke-ExternalStep -StepName 'Signed release APK (assembleRelease)' -FilePath $GradleBat -Arguments @(':app:assembleRelease', '--no-daemon', '--stacktrace')

        $releaseDirectory = Join-Path $ProjectRoot 'app\build\outputs\apk\release'
        $builtApk = Join-Path $releaseDirectory 'app-release.apk'
        $unsignedApk = Join-Path $releaseDirectory 'app-release-unsigned.apk'

        $script:CurrentStep = 'Release APK verification'
        if (-not (Test-Path -LiteralPath $builtApk -PathType Leaf)) {
            if (Test-Path -LiteralPath $unsignedApk -PathType Leaf) {
                throw 'Gradle produced app-release-unsigned.apk. The expected signed release APK was not produced; do not publish the unsigned file.'
            }
            throw "The expected release APK was not found at: $builtApk"
        }

        $builtItem = Get-Item -LiteralPath $builtApk
        if ($builtItem.Length -le 0) {
            throw 'The generated release APK is empty.'
        }
        Write-Host "Built APK: $builtApk"
        Write-Host "Built APK size: $($builtItem.Length) bytes"
        Write-Host "Built APK modified: $($builtItem.LastWriteTime)"

        Invoke-ExternalStep -StepName 'APK signature verification (apksigner)' -FilePath $script:ApkSigner -Arguments @('verify', '--verbose', '--print-certs', $builtApk)

        $badging = Get-ApkBadging -ApkPath $builtApk
        Write-Host "APK package: $($badging.PackageName)"
        Write-Host "APK versionName: $($badging.VersionName)"
        Write-Host "APK versionCode: $($badging.VersionCode)"

        if ($badging.PackageName -ne 'com.kirilliodas.unitconverter') {
            throw "Unexpected APK application ID '$($badging.PackageName)'. Expected 'com.kirilliodas.unitconverter'. Do not publish this APK."
        }

        if ($badging.VersionName -eq '2.0.0' -and $badging.VersionCode -eq 1) {
            $versionMessage = 'This build is still version 2.0.0 (versionCode 1), the already shipped baseline. It is suitable for validation, but a distributable update must increase BOTH versionName and versionCode first.'
            if ($RequireVersionBump) {
                throw $versionMessage
            }
            Write-Host "WARNING: $versionMessage" -ForegroundColor Yellow
        }

        $script:CurrentStep = 'Release APK packaging and SHA-256'
        $script:FinalApkSha256 = Get-Sha256Hex -Path $builtApk

        Protect-OutputDirectory -Path $ReleaseOutputDirectory
        $safeVersionName = $badging.VersionName -replace '[^A-Za-z0-9._-]', '-'
        if ([string]::IsNullOrWhiteSpace($safeVersionName)) { $safeVersionName = 'unknown' }
        $targetName = "Unit-Converter-V$safeVersionName.apk"
        $targetPath = Join-Path $ReleaseOutputDirectory $targetName

        if (Test-Path -LiteralPath $targetPath -PathType Leaf) {
            $existingHash = Get-Sha256Hex -Path $targetPath
            if ($existingHash -ne $script:FinalApkSha256) {
                $targetName = "Unit-Converter-V$safeVersionName-$Timestamp.apk"
                $targetPath = Join-Path $ReleaseOutputDirectory $targetName
                Write-Host "A different APK already used the normal output name, so this build uses: $targetName" -ForegroundColor Yellow
            }
        }

        Copy-Item -LiteralPath $builtApk -Destination $targetPath -Force
        $script:FinalApk = $targetPath

        $sidecarPath = "$targetPath.sha256"
        Set-Content -LiteralPath $sidecarPath -Encoding Ascii -Value ("{0}  {1}" -f $script:FinalApkSha256, $targetName)

        Write-Host ""
        Write-Host '=== BUILD SUCCESS ===' -ForegroundColor Green
        Write-Host "Signed APK: $script:FinalApk"
        Write-Host "SHA-256: $script:FinalApkSha256"
        Write-Host "Checksum file: $sidecarPath"
    } else {
        Write-Host ""
        Write-Host '=== VALIDATION SUCCESS (release build skipped) ===' -ForegroundColor Green
    }

    $testReport = Join-Path $ProjectRoot 'app\build\reports\tests\testDebugUnitTest\index.html'
    $lintReport = Join-Path $ProjectRoot 'app\build\reports\lint-results-debug.html'
    if (Test-Path -LiteralPath $testReport -PathType Leaf) {
        Write-Host "Unit-test report: $testReport"
    }
    if (Test-Path -LiteralPath $lintReport -PathType Leaf) {
        Write-Host "Lint report: $lintReport"
    }
    Write-Host "Complete log: $LogFile"
} catch {
    $script:ExitCode = 1
    Write-Host ""
    Write-Host "=== BUILD FAILED during: $script:CurrentStep ===" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red

    if ($script:CurrentStep -like '*unit tests*') {
        Write-TestFailureDetails
    }
    if ($script:CurrentStep -like '*lint*') {
        Write-LintFailureDetails
    }

    Write-Host ""
    Write-Host "Send this complete log file for diagnosis: $LogFile" -ForegroundColor Yellow

    $testReport = Join-Path $ProjectRoot 'app\build\reports\tests\testDebugUnitTest\index.html'
    $lintReport = Join-Path $ProjectRoot 'app\build\reports\lint-results-debug.html'
    if ($script:CurrentStep -like '*unit tests*' -and (Test-Path -LiteralPath $testReport -PathType Leaf)) {
        Write-Host "Also send or mention this unit-test report if the log does not identify the failing test: $testReport" -ForegroundColor Yellow
    }
    if ($script:CurrentStep -like '*lint*' -and (Test-Path -LiteralPath $lintReport -PathType Leaf)) {
        Write-Host "Also send or mention this lint report if needed: $lintReport" -ForegroundColor Yellow
    }
    Write-Host 'Never send the .jks keystore or any signing password.' -ForegroundColor Yellow
} finally {
    if ($script:StorePasswordWasPrompted) {
        $env:UNIT_CONVERTER_STORE_PASSWORD = $null
    }
    if ($script:KeyPasswordWasPrompted) {
        $env:UNIT_CONVERTER_KEY_PASSWORD = $null
    }
    Stop-BuildTranscript
    Merge-NativeLog
}

exit $script:ExitCode
