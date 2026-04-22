[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Message,
    [string]$Version,
    [switch]$SkipTests,
    [string]$Branch = "main",
    [string]$Remote = "origin",
    [string]$BackupPrefix = "backup",
    [string]$ArchiveRepoRelativePath = "apk-archive-repo"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)][string]$RepoPath,
        [Parameter(Mandatory = $true)][string[]]$Args
    )

    Push-Location $RepoPath
    try {
        & git @Args
        if ($LASTEXITCODE -ne 0) {
            throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Invoke-GitCapture {
    param(
        [Parameter(Mandatory = $true)][string]$RepoPath,
        [Parameter(Mandatory = $true)][string[]]$Args
    )

    Push-Location $RepoPath
    try {
        $output = & git @Args
        if ($LASTEXITCODE -ne 0) {
            throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE."
        }
        return ($output | Out-String).TrimEnd()
    } finally {
        Pop-Location
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

function Update-ArchiveReadme {
    param(
        [Parameter(Mandatory = $true)][string]$ReadmePath,
        [Parameter(Mandatory = $true)][string]$Version,
        [Parameter(Mandatory = $true)][string]$AssetName
    )

    $versionLine = '- `v{0}` -> `{1}`' -f $Version, $AssetName
    $content = Get-Content $ReadmePath
    if ($content -contains $versionLine) {
        return
    }

    $insertAt = -1
    for ($i = 0; $i -lt $content.Count; $i++) {
        if ($content[$i] -match '^- `v\d+\.\d+` -> `Timetable-v\d+\.\d+\.apk`$') {
            $insertAt = $i + 1
        }
    }

    if ($insertAt -lt 0) {
        throw "Unable to find version list in $ReadmePath."
    }

    $updated = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt $content.Count; $i++) {
        if ($i -eq $insertAt) {
            $updated.Add($versionLine)
        }
        $updated.Add($content[$i])
    }
    if ($insertAt -eq $content.Count) {
        $updated.Add($versionLine)
    }

    Set-Content -Path $ReadmePath -Value $updated -Encoding UTF8
}

function Invoke-PublishRelease {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [string]$Version,
        [switch]$SkipTests
    )

    $scriptPath = Join-Path $RepoRoot "scripts\publish-release.ps1"
    $command = @(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $scriptPath
    )
    if ($Version) {
        $command += @("-Version", $Version)
    }
    if ($SkipTests) {
        $command += "-SkipTests"
    }

    $invokeArgs = @($command[1..($command.Count - 1)])
    & $command[0] @invokeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "publish-release.ps1 failed with exit code $LASTEXITCODE."
    }
}

function Sync-BranchWithRemote {
    param(
        [Parameter(Mandatory = $true)][string]$RepoPath,
        [Parameter(Mandatory = $true)][string]$Remote,
        [Parameter(Mandatory = $true)][string]$Branch
    )

    Invoke-Git -RepoPath $RepoPath -Args @("fetch", $Remote, $Branch)
    $trackingRef = "$Remote/$Branch"
    $countsRaw = Invoke-GitCapture -RepoPath $RepoPath -Args @("rev-list", "--left-right", "--count", "HEAD...$trackingRef")
    $counts = $countsRaw -split '\s+'
    if ($counts.Count -lt 2) {
        throw "Unable to determine ahead/behind count for $trackingRef."
    }

    $behind = [int]$counts[1]
    if ($behind -gt 0) {
        Invoke-Git -RepoPath $RepoPath -Args @("rebase", $trackingRef)
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$archiveRepoRoot = Join-Path $repoRoot $ArchiveRepoRelativePath
$propertiesPath = Join-Path $repoRoot "gradle.properties"
$archiveReadmePath = Join-Path $archiveRepoRoot "README.md"

if (-not (Test-Path $archiveRepoRoot)) {
    throw "Archive repo not found: $archiveRepoRoot"
}

$status = Invoke-GitCapture -RepoPath $repoRoot -Args @("status", "--porcelain")
if (-not $status) {
    throw "No local changes to commit."
}

$archiveStatus = Invoke-GitCapture -RepoPath $archiveRepoRoot -Args @("status", "--porcelain")
if ($archiveStatus) {
    throw "APK archive repo working tree is not clean."
}

$currentCommit = Invoke-GitCapture -RepoPath $repoRoot -Args @("rev-parse", "HEAD")
$shortCommit = Invoke-GitCapture -RepoPath $repoRoot -Args @("rev-parse", "--short", "HEAD")
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupTag = "$BackupPrefix-$timestamp-$shortCommit"

Invoke-Git -RepoPath $repoRoot -Args @("tag", "-a", $backupTag, $currentCommit, "-m", "Backup before: $Message")
Invoke-Git -RepoPath $repoRoot -Args @("push", $Remote, $backupTag)

Invoke-Git -RepoPath $repoRoot -Args @("add", "-A")
Invoke-Git -RepoPath $repoRoot -Args @("commit", "-m", $Message)

Sync-BranchWithRemote -RepoPath $repoRoot -Remote $Remote -Branch $Branch
Invoke-Git -RepoPath $repoRoot -Args @("push", $Remote, $Branch)

Invoke-PublishRelease -RepoRoot $repoRoot -Version $Version -SkipTests:$SkipTests

$releasedVersion = Get-PropertyValue -Path $propertiesPath -Name "APP_VERSION_NAME"
$assetName = "Timetable-v$releasedVersion.apk"
$releaseAssetPath = Join-Path $repoRoot "app\build\release-assets\$assetName"
if (-not (Test-Path $releaseAssetPath)) {
    throw "Release APK not found: $releaseAssetPath"
}

Sync-BranchWithRemote -RepoPath $archiveRepoRoot -Remote $Remote -Branch $Branch

$archiveReleaseDir = Join-Path $archiveRepoRoot "releases"
New-Item -ItemType Directory -Force $archiveReleaseDir | Out-Null
Copy-Item $releaseAssetPath (Join-Path $archiveReleaseDir $assetName) -Force
Update-ArchiveReadme -ReadmePath $archiveReadmePath -Version $releasedVersion -AssetName $assetName

Invoke-Git -RepoPath $archiveRepoRoot -Args @("add", "README.md", "releases/$assetName")
Invoke-Git -RepoPath $archiveRepoRoot -Args @("commit", "-m", "Archive timetable v$releasedVersion APK")
Invoke-Git -RepoPath $archiveRepoRoot -Args @("push", $Remote, $Branch)

Write-Host "Backup tag: $backupTag"
Write-Host "Code push complete: $Branch"
Write-Host "Release version: v$releasedVersion"
Write-Host "Release asset: $releaseAssetPath"
Write-Host "Archive repo updated: releases/$assetName"
