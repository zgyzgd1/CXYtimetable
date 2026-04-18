[CmdletBinding()]
param(
    [string]$Version,
    [switch]$SkipTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Git {
    param([Parameter(Mandatory = $true)][string[]]$Args)

    & git @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE."
    }
}

function Get-PropertyValue {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $line = Get-Content $Path | Where-Object { $_ -match "^$Name=" } | Select-Object -First 1
    if (-not $line) {
        throw "Property '$Name' not found in $Path."
    }

    return $line.Substring($Name.Length + 1)
}

function Set-PropertyValue {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $content = Get-Content $Path
    $updated = $false
    for ($i = 0; $i -lt $content.Count; $i++) {
        if ($content[$i] -match "^$Name=") {
            $content[$i] = "$Name=$Value"
            $updated = $true
            break
        }
    }

    if (-not $updated) {
        $content += "$Name=$Value"
    }

    Set-Content -Path $Path -Value $content -Encoding UTF8
}

function Get-NextVersion {
    param([Parameter(Mandatory = $true)][string]$CurrentVersion)

    if ($CurrentVersion -notmatch '^(?<major>\d+)\.(?<minor>\d+)$') {
        throw "Current version '$CurrentVersion' must match major.minor format."
    }

    $major = [int]$Matches.major
    $minor = [int]$Matches.minor + 1
    return "$major.$minor"
}

function Get-GitHubToken {
    $credentialRequest = @"
protocol=https
host=github.com
path=zgyzgd1/-.git

"@
    $credential = $credentialRequest | git credential fill
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read GitHub credentials from git credential helper."
    }

    $passwordLine = $credential | Select-String '^password=' | Select-Object -First 1
    if (-not $passwordLine) {
        throw "GitHub token not found in git credential helper output."
    }

    return $passwordLine.ToString().Substring(9)
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$status = git status --porcelain
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect git status."
}
if ($status) {
    throw "Working tree is not clean. Commit or stash changes before publishing."
}

$propertiesPath = Join-Path $repoRoot "gradle.properties"
$currentVersion = Get-PropertyValue -Path $propertiesPath -Name "APP_VERSION_NAME"
$currentCode = [int](Get-PropertyValue -Path $propertiesPath -Name "APP_VERSION_CODE")

if (-not $Version) {
    $Version = Get-NextVersion -CurrentVersion $currentVersion
}

if ($Version -notmatch '^\d+\.\d+$') {
    throw "Version '$Version' must match major.minor format, for example 1.2."
}

if ($Version -eq $currentVersion) {
    throw "Version '$Version' is already the current version."
}

$nextCode = $currentCode + 1
$tag = "v$Version"
$assetName = "Timetable-v$Version.apk"
$releaseAssetDir = Join-Path $repoRoot "app\build\release-assets"
$releaseAssetPath = Join-Path $releaseAssetDir $assetName

Set-PropertyValue -Path $propertiesPath -Name "APP_VERSION_NAME" -Value $Version
Set-PropertyValue -Path $propertiesPath -Name "APP_VERSION_CODE" -Value $nextCode

try {
    if (-not $SkipTests) {
        & .\gradlew.bat testDebugUnitTest
        if ($LASTEXITCODE -ne 0) {
            throw "Unit tests failed."
        }
    }

    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Release build failed."
    }

    New-Item -ItemType Directory -Force $releaseAssetDir | Out-Null
    Copy-Item "app\build\outputs\apk\release\app-release.apk" $releaseAssetPath -Force

    Invoke-Git -Args @("add", "gradle.properties", "app/build.gradle.kts")
    Invoke-Git -Args @("commit", "-m", "Release $tag")
    Invoke-Git -Args @("push", "origin", "main")
    Invoke-Git -Args @("tag", "-a", $tag, "-m", "Release $tag")
    Invoke-Git -Args @("push", "origin", $tag)

    $token = Get-GitHubToken
    $headers = @{
        Authorization = "Bearer $token"
        Accept = "application/vnd.github+json"
        "X-GitHub-Api-Version" = "2022-11-28"
        "User-Agent" = "Codex"
    }

    try {
        $release = Invoke-RestMethod -Method Get -Headers $headers -Uri "https://api.github.com/repos/zgyzgd1/-/releases/tags/$tag"
    } catch {
        $body = @{
            tag_name = $tag
            target_commitish = "main"
            name = $tag
            body = @"
Version $Version release.

Asset:
- ${assetName}: signed release APK for installation. The project currently uses the Android debug keystore for all GitHub releases so signing stays consistent until a dedicated release keystore is configured.
"@
            draft = $false
            prerelease = $false
        } | ConvertTo-Json
        $release = Invoke-RestMethod -Method Post -Headers $headers -Uri "https://api.github.com/repos/zgyzgd1/-/releases" -Body $body -ContentType "application/json"
    }

    foreach ($existing in @($release.assets | Where-Object { $_.name -eq $assetName })) {
        Invoke-RestMethod -Method Delete -Headers $headers -Uri "https://api.github.com/repos/zgyzgd1/-/releases/assets/$($existing.id)" | Out-Null
    }

    $uploadUrl = ($release.upload_url -replace '\{\?name,label\}$', '') + "?name=$assetName"
    Invoke-WebRequest -Method Post -Headers @{
        Authorization = "Bearer $token"
        Accept = "application/vnd.github+json"
        "Content-Type" = "application/vnd.android.package-archive"
        "User-Agent" = "Codex"
    } -Uri $uploadUrl -InFile $releaseAssetPath -UseBasicParsing | Out-Null

    Write-Host "Published $tag"
    Write-Host "Release URL: https://github.com/zgyzgd1/-/releases/tag/$tag"
    Write-Host "Asset: $releaseAssetPath"
} catch {
    Set-PropertyValue -Path $propertiesPath -Name "APP_VERSION_NAME" -Value $currentVersion
    Set-PropertyValue -Path $propertiesPath -Name "APP_VERSION_CODE" -Value $currentCode
    throw
}
