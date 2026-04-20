# GitHub UI Research for Timetable

Focused GitHub screening for Compose-based week calendar and timetable UI references that can be integrated into this project without a full UI rewrite.

## Current Project Fit

The current app already has the right technical baseline for modern Compose calendar libraries:

- Android app with `Kotlin + Jetpack Compose + Material 3`
- `minSdk = 26`
- `coreLibraryDesugaring` already enabled in [app/build.gradle.kts](/e:/vswenjian_workspace_copy/app/build.gradle.kts:1)
- Week mode is driven by `selectedDate` in [ScheduleScreen.kt](/e:/vswenjian_workspace_copy/app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt:80)
- The main integration seam is `WeekScheduleBoard()` in [WeekScheduleBoard.kt](/e:/vswenjian_workspace_copy/app/src/main/java/com/example/timetable/ui/WeekScheduleBoard.kt:55), currently mounted from [ScheduleScreen.kt](/e:/vswenjian_workspace_copy/app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt:222)

That means the lowest-risk path is to add a reusable week selector or compact week calendar above the existing board, not to replace the board itself.

## Narrow Search Scope

Search phrases used:

- `week-calendar compose`
- `schedule calendar compose`
- `android week view compose`
- `timetable kotlin android`

Screening rules:

- Must support Jetpack Compose directly, not only View system widgets
- Must support week mode, not just month picker behavior
- Must allow custom day cell or selection rendering
- Must show recent maintenance signs
- Must fit above or alongside the existing `WeekScheduleBoard`

## Top 3 Candidates

| Candidate | Why it fits this app | Risks | Verdict |
| --- | --- | --- | --- |
| [`kizitonwose/Calendar`](https://github.com/kizitonwose/Calendar) | Best match for a top week strip or full compact week calendar. Supports week/month/year, custom day composables, programmatic scrolling, and active Compose support. Latest release shown on GitHub is `2.10.1` on March 28, 2026. | Adds a real dependency and needs version alignment with Compose UI. Rich API means more implementation choices. | Best primary candidate |
| [`boguszpawlowski/ComposeCalendar`](https://github.com/boguszpawlowski/ComposeCalendar) | Good for a lighter week selector with hoisted state and built-in selectable week calendar APIs like `SelectableWeekCalendar`. Lower conceptual surface area than `kizitonwose/Calendar`. | Maintenance signal is weaker than `kizitonwose/Calendar`. Fewer ecosystem examples. Search result snippet shows repo activity on February 16, 2025, so freshness is acceptable but not strong. | Best fallback candidate |
| [`uuranus/schedule-calendar-compose`](https://github.com/uuranus/schedule-calendar-compose) | Name and goal are very close to the desired timetable scenario, so it is useful as a layout reference. | Low maturity and weaker maintenance signal. Harder to justify as a production dependency. | Reference only, not recommended as dependency |

## Supporting References

These are useful for product structure and information density, but not as direct week-calendar dependencies:

- [`FossifyOrg/Calendar`](https://github.com/FossifyOrg/Calendar)
  - Good reference for settings organization, event density, reminders, and widget-oriented product thinking.
  - Actively maintained. GitHub shows release `1.10.3` on February 17, 2026 and repo updates into April 2026.
  - Not a Compose week-calendar library, so this is a UX reference only.
- [`rajatdiptabiswas/timetable-android`](https://github.com/rajatdiptabiswas/timetable-android)
  - Old Java-era student timetable app with screenshots for projects, widget entry points, and class-management flows.
  - Good for feature grouping ideas.
  - Not suitable as a technical dependency.

## Best Fit for This Codebase

### 1. `kizitonwose/Calendar`

Why it is the best fit:

- Native Compose artifact: `com.kizitonwose.calendar:compose`
- Explicit support for week mode, custom date cells, week headers, horizontal/vertical scrolling, and programmatic scrolling
- Its README explicitly notes Compose UI compatibility ranges, which reduces integration ambiguity
- This repo is maintained enough to be a safe dependency decision for a modern Android app

Why it fits this project specifically:

- Your app already stores the selected date as a `LocalDate`-compatible string and computes `selectedWeekStart`
- You can map calendar selection directly to `selectedDate`
- You do not need to replace the timetable board; you only need a better date/week navigator above it

Recommended usage here:

- Add a compact horizontal week calendar above `WeekScheduleBoard()`
- Use it only for week navigation and day selection
- Keep all course layout and overlap rendering inside the existing board

### 2. `ComposeCalendar`

Why it is still viable:

- Includes explicit week-calendar APIs
- Strong state-hoisting story
- Easier mental model if the goal is only a top week selector

Why it ranks below `kizitonwose/Calendar`:

- Smaller ecosystem
- Less visible maintenance momentum
- Fewer signs that it is the de facto default Compose calendar choice today

Recommended usage here:

- Use only if you want the simplest integration path and accept lower ecosystem confidence

### 3. `schedule-calendar-compose`

Why it does not make the cut:

- It is interesting as a visual or structural reference
- It does not show enough maturity to justify production dependency risk for this app

Use it for:

- Layout ideas
- Schedule density ideas
- Interaction inspiration

Do not use it for:

- Production dependency choice
- Core date navigation

## Concrete Integration Direction

The implementation target should be:

1. Keep the current `WeekScheduleBoard()` as the primary timetable renderer.
2. Insert a compact week selector above it in week mode.
3. Bind day taps to `selectedDate`.
4. Bind horizontal week paging to `selectedWeekStart` / `selectedDate`.
5. Leave the existing slot grid, overlap handling, and entry cards unchanged in the first pass.

The best insertion point is inside the week-mode branch in [ScheduleScreen.kt](/e:/vswenjian_workspace_copy/app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt:196), immediately before `WeekScheduleBoard()`, or inside [WeekScheduleBoard.kt](/e:/vswenjian_workspace_copy/app/src/main/java/com/example/timetable/ui/WeekScheduleBoard.kt:55) as a dedicated top strip composable if the week header should stay visually fused with the board.

## Decision

If this project adds one GitHub-derived UI dependency for week navigation, the recommended order is:

1. `kizitonwose/Calendar`
2. `boguszpawlowski/ComposeCalendar`
3. No dependency, keep current custom board and only borrow visual ideas

`uuranus/schedule-calendar-compose` should not be the chosen dependency unless new evidence shows stronger maintenance and better sample quality.

## Sources

- Project files:
  - [app/build.gradle.kts](/e:/vswenjian_workspace_copy/app/build.gradle.kts:1)
  - [ScheduleScreen.kt](/e:/vswenjian_workspace_copy/app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt:80)
  - [WeekScheduleBoard.kt](/e:/vswenjian_workspace_copy/app/src/main/java/com/example/timetable/ui/WeekScheduleBoard.kt:55)
- GitHub:
  - https://github.com/kizitonwose/Calendar
  - https://github.com/boguszpawlowski/ComposeCalendar
  - https://github.com/uuranus/schedule-calendar-compose
  - https://github.com/FossifyOrg/Calendar
  - https://github.com/rajatdiptabiswas/timetable-android
