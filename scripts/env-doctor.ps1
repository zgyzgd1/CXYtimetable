$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$failed = $false

function Write-Check {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )

    if ($Passed) {
        Write-Output "[PASS] $Name - $Detail"
    }
    else {
        Write-Output "[FAIL] $Name - $Detail"
        $script:failed = $true
    }
}

# Check Gradle wrapper availability.
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"
Write-Check "Gradle wrapper" (Test-Path $gradleWrapper) $gradleWrapper

$wrapperPropertiesPath = Join-Path $projectRoot "gradle\wrapper\gradle-wrapper.properties"
$hasWrapperProperties = Test-Path $wrapperPropertiesPath
Write-Check "gradle-wrapper.properties" $hasWrapperProperties $wrapperPropertiesPath

$expectedGradleVersion = $null
$gradleDistributionUrl = $null
if ($hasWrapperProperties) {
    $distributionLine = Get-Content $wrapperPropertiesPath |
        Where-Object { $_ -match '^distributionUrl=' } |
        Select-Object -First 1
    if ($null -ne $distributionLine) {
        $gradleDistributionUrl = ($distributionLine -split '=', 2)[1].Replace('\:', ':')
        $versionMatch = [regex]::Match($gradleDistributionUrl, 'gradle-([0-9]+(?:\.[0-9]+){1,2})-')
        if ($versionMatch.Success) {
            $expectedGradleVersion = $versionMatch.Groups[1].Value
        }
    }
}

# Check local.properties and sdk.dir.
$localProperties = Join-Path $projectRoot "local.properties"
$hasLocalProperties = Test-Path $localProperties
Write-Check "local.properties" $hasLocalProperties $localProperties

$sdkDir = $null
if ($hasLocalProperties) {
    $sdkLine = Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($null -ne $sdkLine) {
        $sdkDir = ($sdkLine -split '=', 2)[1] -replace '\\\\', '\'
    }
}

$hasSdkDir = -not [string]::IsNullOrWhiteSpace($sdkDir)
$sdkDirDetail = if ($hasSdkDir) { $sdkDir } else { "Missing sdk.dir" }
Write-Check "sdk.dir configured" $hasSdkDir $sdkDirDetail

if ($hasSdkDir) {
    Write-Check "SDK root exists" (Test-Path $sdkDir) $sdkDir
    Write-Check "Android platform 36" (Test-Path (Join-Path $sdkDir "platforms\android-36")) (Join-Path $sdkDir "platforms\android-36")
    Write-Check "Build tools folder" (Test-Path (Join-Path $sdkDir "build-tools")) (Join-Path $sdkDir "build-tools")
    Write-Check "Platform tools folder" (Test-Path (Join-Path $sdkDir "platform-tools")) (Join-Path $sdkDir "platform-tools")
}

# Check Gradle + JVM runtime.
if (Test-Path $gradleWrapper) {
    $gradleVersionOutput = & $gradleWrapper --version 2>&1 | Out-String

    if (-not [string]::IsNullOrWhiteSpace($expectedGradleVersion)) {
        $expectedVersionPattern = "Gradle\s+$([regex]::Escape($expectedGradleVersion))(\s|$)"
        $hasExpectedGradleVersion = $gradleVersionOutput -match $expectedVersionPattern
        Write-Check "Gradle version" $hasExpectedGradleVersion "Expected Gradle $expectedGradleVersion"
    }
    else {
        $hasGradleVersion = $gradleVersionOutput -match 'Gradle\s+[0-9]+'
        Write-Check "Gradle version" $hasGradleVersion "Gradle version detected from wrapper runtime"
    }

    $jdk17 = $gradleVersionOutput -match 'JVM:\s+17\.'
    Write-Check "JDK runtime" $jdk17 "Expected JVM 17"
}

# Check Gradle distribution endpoint reachability (non-blocking in offline networks).
try {
    if ([string]::IsNullOrWhiteSpace($gradleDistributionUrl)) {
        if (-not [string]::IsNullOrWhiteSpace($expectedGradleVersion)) {
            $gradleDistributionUrl = "https://downloads.gradle.org/distributions/gradle-$expectedGradleVersion-bin.zip"
        }
    }

    if ([string]::IsNullOrWhiteSpace($gradleDistributionUrl)) {
        Write-Output "[WARN] Gradle distribution URL unresolved from gradle-wrapper.properties"
    }
    else {
        $response = Invoke-WebRequest -Uri $gradleDistributionUrl -Method Head -TimeoutSec 10 -UseBasicParsing
        $reachable = ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400)
        Write-Output "[INFO] Gradle distribution URL - HTTP $($response.StatusCode)"
        if (-not $reachable) {
            Write-Output "[WARN] Gradle distribution URL returned non-2xx/3xx status."
        }
    }
}
catch {
    Write-Output "[WARN] Gradle distribution URL unreachable: $($_.Exception.Message)"
}

if ($failed) {
    Write-Output ""
    Write-Output "Environment check failed. Fix [FAIL] items and rerun."
    exit 1
}

Write-Output ""
Write-Output "Environment looks good."
