# 1.md Remediation Plan

> **For:** 1.md review findings
> **Created:** 2026-05-13
> **Status:** implemented and verified

## Verification Summary

Most functional, UI, test-hardening, script, and resource findings in `1.md` are reproducible in the current tree. Two claims are not supported by current evidence:

- Findings 1-4 say the configured dependency versions are nonexistent and the build must fail. `testDebugUnitTest` and `lintDebug` both complete successfully in this environment, so the "must fail" part is false. The explicit `buildToolsVersion = "37.0.0"` is still worth removing to reduce environment coupling.
- Finding 41 says `scripts/push-github.ps1` shadows PowerShell `$Args`. The script uses a parameter named `$Message` and internal parameters named `$Args`; PowerShell parameter names are case-insensitive, so this is potentially confusing inside functions, but the top-level claim as written is not the blocker described in `1.md`.

## Confirmed Findings To Fix

- Encoding/config: 5, 9.
- Script safety/portability: 6, 7, 8, 42, 43.
- ICS import/export and recurrence behavior: 10, 12, 14, 16.
- WebView and image lifecycle/decoding: 11, 13, 30.
- HEBAU import robustness: 15, 21, 22, 23.
- Reminder and state correctness: 17, 18, 19.
- Compose/UI correctness and polish: 20, 25, 26, 27, 28, 29, 44.
- Test coverage and brittle assertions: 31, 32, 33, 34, 35, 36, 37, 38, 39.
- Shared utility duplication: 40.

## Phase 1 - Low-Risk Hygiene

- [x] Save `gradle.properties` and `app/src/main/AndroidManifest.xml` as UTF-8 without BOM.
- [x] Add the XML declaration to `app/src/main/AndroidManifest.xml`.
- [x] Remove explicit `buildToolsVersion = "37.0.0"` from `app/build.gradle.kts` unless a concrete release artifact requires it.
- [x] Replace hardcoded hex text colors in `app/src/main/res/layout/widget_next_course.xml` with named colors in `app/src/main/res/values/colors.xml`.
- [x] Refactor duplicated `OneTimeAction` into one package-visible utility used by notification and widget receivers.
- [x] Verification: `./gradlew.bat testDebugUnitTest --no-daemon`, `./gradlew.bat lintDebug --no-daemon`, `git diff --check`.

## Phase 2 - Safety and Lifecycle Fixes

- [x] Update `scripts/publish-release.ps1` to derive owner/repository from git remote config or explicit parameters instead of hardcoding `zgyzgd1/CXYtimetable`.
- [x] Rewrite or retire `refactor_ui.py`: require an explicit file argument, make a timestamped backup, and avoid hardcoded line slicing plus global string replacement.
- [x] In `JwWebView.kt`, destroy the main WebView when the Compose AndroidView leaves composition. Keep popup teardown behavior intact.
- [x] Reconsider mobile WebView settings in `JwWebView.kt`; keep `useWideViewPort` and `loadWithOverviewMode` enabled if the HEBAU mobile pages require viewport scaling.
- [x] Add EXIF orientation handling to `BackgroundImageManager.kt` before scaling/cropping decoded images.
- [x] Verification: targeted unit tests where available, then full unit/lint commands.

## Phase 3 - Calendar Semantics

- [x] Preserve parsed ICS value type/parameters so `VALUE=DATE` all-day events can be represented as date-only events instead of becoming one-hour midnight entries.
- [x] Add explicit handling for unsupported RRULE constructs. Either implement the needed monthly/yearly modifiers or reject/degrade with a visible import warning instead of silently changing meaning.
- [x] Add export bounds for recurring events: prefer `UNTIL` or `COUNT` derived from the app's known recurring schedule/window.
- [x] Include `RECURRENCE-ID` or equivalent occurrence identity in ICS import deduplication so valid overridden instances are not dropped by `distinctBy { it.id }`.
- [x] Verification: add `IcsCalendarTest` cases for all-day import, monthly BYDAY modifiers, bounded export RRULEs, and duplicate UID recurrence overrides.

## Phase 4 - Import and Validation Correctness

- [x] Fix HEBAU weak-key dedupe so a later weak match cannot overwrite or merge distinct valid course instances unexpectedly.
- [x] Hoist repeated regexes in `HebauPlainTextParser.kt` into private constants or lazily initialized values.
- [x] Narrow `isIgnoredCourseName` to exact labels or field-marker lines, not broad substring matches.
- [x] Extend teacher parsing beyond CJK-only names when the input has a teacher label or a clearly separated teacher field.
- [x] Align `EntryValidator.validate` and `validateDraft` trimming semantics for title/location/note length checks.
- [x] Verification: targeted HEBAU parser tests plus expanded `EntryValidatorTest`.

## Phase 5 - Reminder and State Correctness

- [x] Replace silent `runCatching { ... }.getOrNull()` in `CourseReminderScheduler.computeTriggerAtMillis` with explicit validation/logging and deterministic test coverage.
- [x] Avoid default notification `requestCode = 0` overwrite behavior. Missing request codes should be rejected or replaced with a stable unique fallback.
- [x] Add a regression test around initial active-group loading in `ScheduleViewModel`, then adjust initialization so observers do not briefly consume an empty or stale group when a migrated active group exists.
- [x] Verification: scheduler, receiver, repository/view-model tests.

## Phase 6 - Compose/UI Polish

- [x] Move the `semesterStartDateText` defaulting side effect in `EntryEditorDialog` into `LaunchedEffect` or initialization state.
- [x] Replace the empty `confirmButton` in `ImportMethodDialog` with a real dialog action structure, or use content-only layout without pretending it is a confirm slot.
- [x] Animate selected/deselected colors in `TimetableCalendar.kt` through a single `animateColorAsState` path.
- [x] Collapse the three fullscreen tint boxes in `BackgroundLayer.kt` into fewer overlay draws.
- [x] Replace hardcoded alpha literals in `WeekViewContent.kt` with named theme constants.
- [x] Fold conditional alpha in `WeekScheduleBoard.kt` into the existing `graphicsLayer`.
- [x] Verification: Compose unit tests where practical, plus manual screenshot/browser-device style checks if UI behavior changes visibly.

## Phase 7 - Test Hardening

- [x] Give Chinese text Compose tests a stable locale/encoding setup, and fix any mojibake string literals.
- [x] Expand `EntryValidatorTest` beyond two custom-week cases to cover title/time/location/note trimming and invalid recurrence inputs.
- [x] Replace raw source-string scans in `AlphaConstantsMigrationTest`, `WeekCalendarStripTest`, `JwWebViewSecurityTest`, and `WeekCardHueRangeTest` with behavior-level tests or parser-backed assertions.
- [x] Strengthen `CourseReminderReceiverTest` assertions to exact localized strings or controlled fake resources.
- [x] Strengthen `AccessibilityLabelsTest` to assert full expected labels or structured components instead of broad substring presence.
- [x] Extend `AppDatabaseMigrationTest` to assert the V1 `groupId` default and any other fields added after V1/V2.
- [x] Verification: full test suite and lint.

## Suggested Execution Order

1. Phase 1 first because it is mechanical and low risk.
2. Phase 7 test hardening next for areas that will be touched by functional fixes.
3. Phases 3-5 in separate commits or review chunks because they affect behavior and data semantics.
4. Phase 6 after functional fixes so UI tests can be updated once.
5. Re-run `./gradlew.bat testDebugUnitTest --no-daemon`, `./gradlew.bat lintDebug --no-daemon`, and `git diff --check` before declaring the work complete.
