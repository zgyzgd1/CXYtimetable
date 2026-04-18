# Release Process

This repository now uses a single release flow.

## Rules

- Version tags use `v1.0`, `v1.1`, `v1.2` in order.
- The app version is stored in `gradle.properties`:
  - `APP_VERSION_NAME`
  - `APP_VERSION_CODE`
- Every GitHub release keeps exactly one APK asset:
  - `Timetable-vX.Y.apk`
- GitHub releases currently use the Android `debug` keystore so the APK stays installable and the signing stays consistent.

## Command

Run the release script from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\publish-release.ps1
```

Default behavior:

- Reads the current version from `gradle.properties`
- Increments the minor version automatically
  - `1.1` -> `1.2`
- Increments `APP_VERSION_CODE`
- Runs `testDebugUnitTest`
- Runs `assembleRelease`
- Copies the final APK to `app/build/release-assets/Timetable-vX.Y.apk`
- Commits the version bump
- Pushes `main`
- Creates and pushes the Git tag
- Creates or updates the GitHub release
- Uploads the single APK asset

## Optional flags

Publish a specific version:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\publish-release.ps1 -Version 1.2
```

Skip unit tests:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\publish-release.ps1 -SkipTests
```

## Notes

- The script requires a clean git working tree.
- The script reads the GitHub token from the configured git credential helper.
- If a dedicated release keystore is added later, only the signing section in `app/build.gradle.kts` and the release note text need to change.
