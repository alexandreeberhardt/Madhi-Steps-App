# Android app

Package `com.madhi.tracker`, single Gradle module `app/`. Minimum SDK 29,
target SDK 34. Code under `app/src/main/java/com/madhi/tracker/`.

## Layers

    presentation/     Compose screens and ViewModels
    adapter/input/    what Android calls: foreground service, receivers, worker
    application/      use cases (usecase/) and the interfaces they need (port/)
    domain/           models and pure rules, no Android imports
    adapter/output/   implementations of the ports: Room, DataStore, OkHttp,
                      LocationManager, AlarmManager, WorkManager
    infrastructure/   Hilt modules, build configuration, logging

`./gradlew checkCoreIsFrameworkFree` (run by `check`) fails if `domain/` or
`application/` imports Android, Room, or OkHttp.

## Screens

`presentation/MadhiTrackerApp.kt` picks the root destination.

**First launch: onboarding** (`presentation/onboarding/`). Steps in order:
welcome, location permission, background location ("Allow all the time"),
activation code, battery and vendor settings, then a setup check.

- The activation code is exchanged for a device token
  (`ActivateDevice`, `POST /devices/activate`).
- The battery step opens the system battery-optimisation screen, then lists
  vendor-specific settings the app cannot grant itself
  (`VendorSetupGuidance.kt`: Xiaomi/MIUI, OnePlus/Oppo, Samsung, Huawei).
- The setup check (`RunSetupCheck`) captures a real location and uploads it.
  Finishing is allowed even if the check fails, because the traveller may be
  offline during setup. Finishing starts tracking and marks onboarding done.

**After onboarding**, three screens in a stack:

- **Home** (`presentation/map/MainScreen.kt`): the map, a period selector
  (Today, 24 h, 7 days, Whole trip; default 7 days), the tracking state, the
  age of the latest point, and a "Fix" button when something is wrong.
- **Settings** (`presentation/settings/`): capture interval (5 min, 30 min,
  1 h, or "Other" from 1 min to 24 h) and sync status. "Other settings" is
  collapsed and holds activation, diagnostics, the update page, and stopping
  tracking.
- **Diagnostics** (`presentation/diagnostics/`): raw facts meant to be read
  aloud over the phone (see [Diagnostics](#diagnostics)).

Every time the app opens, `RootViewModel` calls `RestoreTracking(APP_OPENED)`
before showing anything.

## Tracking state on the home screen

`ObserveTrackingStatus` combines the stored intent, the system state, the
pending count, and the sync journal. `TrackingHealthPolicy` turns that into one
of four states:

| State | When |
|---|---|
| Stopped | The user turned tracking off. Takes priority over everything. |
| Action required | At least one problem that will lose data. |
| Offline | No network. Shown as normal, not as an error. |
| Active | Everything else. |

Problems (`domain/model/TrackingProblem.kt`), in the order they are shown; the
home screen only shows the first one:

1. Device not activated
2. Location permission missing
3. Background location permission missing
4. Location turned off
5. Auto-start blocked (detected after the fact, see [Reboots](#reboots-and-updates))
6. Battery optimisation enabled
7. Exact alarms not permitted
8. Authentication failed (the last sync failure was `unauthorized`)
9. Notifications blocked: the only problem that does not cause "Action required"

The "Fix" button opens the screen that solves the problem: the permission
prompt, the system settings page, the vendor settings, or the activation screen.

## How tracking keeps running

Three mechanisms work together. None of them is enough on its own.

### 1. The location stream (main mechanism)

`TrackingForegroundService` is a foreground service of type `location` with a
permanent low-priority notification. Its main job is to exist: while it runs,
the process stays alive and keeps background location access.

It runs `TrackLocations`, which subscribes to `LocationManager` through
`AndroidLocationSource.stream()`:

- both the GPS and network providers, when enabled, so the phone still gets an
  approximate position indoors;
- `LocationRequestCompat` with the capture interval as both the interval and
  the minimum interval, and `QUALITY_BALANCED_POWER_ACCURACY`;
- the system decides when to power the receiver, which costs less battery than
  waking the CPU on a timer.

Since two providers deliver independently, `CaptureThrottle` drops a fix that
arrives less than 80 % of an interval after the last recorded point, or that is
older than it. Changing the interval resubscribes automatically
(`flatMapLatest` on the stored intent).

There is no Google Play Services dependency: no Fused Location Provider.

### 2. The safety alarm

`AlarmCaptureScheduler` sets one exact `ELAPSED_REALTIME_WAKEUP` alarm with
`setExactAndAllowWhileIdle` (falling back to the inexact variant if exact
alarms are refused). Elapsed time, not wall-clock time, so time-zone changes do
not move it.

The alarm wakes `CaptureAlarmReceiver`, which forwards to the service.
`RunScheduledCapture` then:

- does nothing if a point was recorded within the last **two** intervals;
- otherwise logs `STREAM_SILENT` and runs a one-off `CaptureLocation`
  (timeout: half the interval, at most 90 s, under a 2-minute wake lock);
- always reschedules itself **three** intervals later, even after a failure.

So with a 5-minute interval, the alarm checks every 15 minutes and steps in
after 10 minutes of silence.

### 3. The sync worker as a watchdog

`SyncWorker` runs every 15 minutes (WorkManager's minimum) when there is a
network. Before syncing, it calls `RestoreTracking(WATCHDOG)`, which restarts
the service if the vendor's skin killed it. Starting a foreground service from
the background is only allowed because the app is exempt from battery
optimisation.

## Recording a point

Both the stream and the one-off capture go through `RecordLocation`:

1. Record a sign of life in the reboot journal.
2. Validate (`domain/LocationValidation.kt`). Only impossible values are
   rejected: latitude or longitude out of range, NaN, exactly (0, 0), a
   timestamp before 2026-01-01, or more than one day in the future. Inaccurate
   fixes are kept.
3. Create a `LocationPoint` with a random UUID, the battery level, and state
   `PENDING`, and insert it into Room.
4. Only then, request an immediate sync. If that request fails, the periodic
   worker will pick the point up.

## Local storage

Room database `madhi-tracker.db`, table `locations`, schema version 1, exported
to `app/schemas/`. Destructive migrations are not used.

`LocationDao` has **no delete query**. Points are never removed from the phone;
a synced point only changes state to `SYNCED`. Inserting an existing id is
ignored, so a synced point can never go back to `PENDING`.

Settings and journals are in DataStore (`adapter/output/persistence/datastore/`):
tracking intent (on/off, interval), onboarding done, sync journal (last
attempt, success, failure code), reboot journal.

The device token is encrypted with AES-GCM using a non-exportable Android
Keystore key (`KeystoreDeviceCredentials`). The key does not require user
authentication, so sync works with the phone locked. Android backup is
disabled (`allowBackup="false"`).

## Synchronisation

`SyncPendingLocations`, guarded by a mutex so only one sync runs at a time:

- sends the oldest pending points first, in batches of 200;
- marks `accepted` and `duplicates` as synced: in both cases the server has
  the point, which makes retrying after a lost response safe;
- leaves `rejected` points pending with error code `rejected`;
- on any failure, records the attempt and error code on the batch and stops;
- on HTTP 413, divides the batch size by four (minimum 25) and continues;
- stops after 20 batches per run, so a week of backlog does not monopolise the
  radio;
- stops if the server confirms nothing, to avoid looping on the same batch.

HTTP errors map to `SyncFailure` (`domain/error/SyncFailure.kt`): 401/403
`unauthorized`, 413 `batch_too_large`, 429 `rate_limited`, other 4xx
`rejected_payload_<code>`, 5xx `server_error_<code>`, plus `no_network`,
`timeout`, `not_activated`, `unexpected`. **No failure deletes a point.**

Two triggers (`WorkManagerSyncScheduler`):

- **Periodic**, every 15 minutes with a network constraint, policy `UPDATE` so
  a new APK takes over the existing schedule.
- **Immediate**, after each saved point, policy `REPLACE`.

The worker never returns `retry`. WorkManager's exponential backoff could grow
to hours and keep a job waiting after the network came back; the 15-minute
period is the retry mechanism.

Stopping tracking does not stop sync: pending points keep being sent.

## Reboots and updates

`RestoreTracking` is the single recovery path. It is idempotent and called
from four places:

| Trigger | Caller |
|---|---|
| `BOOT` | `BootReceiver` (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`) |
| `PACKAGE_REPLACED` | `PackageReplacedReceiver` (`MY_PACKAGE_REPLACED`) |
| `APP_OPENED` | `RootViewModel` |
| `WATCHDOG` | `SyncWorker` |

It always ensures the periodic sync is scheduled. If tracking is enabled, it
starts the service if needed and schedules the next capture based on the last
recorded point, not on "now", so opening the app often never pushes the next
capture back.

**Known limitation.** On MIUI and OxygenOS, `BootReceiver` is only called if
the vendor's auto-start setting is enabled for the app. If it is not, nothing
fails; nothing happens. The app cannot read that setting, so it detects the
symptom instead (`domain/RebootDetection.kt`): if uptime went backwards, or the
boot time is after the last sign of life, and no boot was handled since, it
reports `AUTOSTART_BLOCKED`. This is detected only once the app runs again
(opened by hand or by the watchdog). Until auto-start is enabled, the app must
be opened once after every reboot and every APK update.

## Map

`presentation/map/TrackMap.kt` draws on a Compose `Canvas`, without a mapping
library. Geometry (projection, viewport, tile grid, scale bar, point picking)
is in `domain/Map*.kt`, `TileGrid.kt`, and `TrackPicking.kt`, tested on the JVM.

- **Track.** Read from Room, so it works offline. `ObserveTrack` groups points
  into time buckets to bound the number drawn: 1 minute for Today and 24 h,
  5 minutes for 7 days, 1 hour for Whole trip. Points with accuracy worse
  than 250 m are not drawn (they stay in the database).
- **Colours.** Synced segments are blue, pending segments orange. When a
  period other than Whole trip is selected, the full trip is drawn underneath
  as a thin light-blue line with no dots.
- **Point bubble.** Tapping a point shows its time and coordinates (always
  available) and its address, fetched from the server's
  `/reverse-geocode` relay (see [API](api.md#reverse-geocoding)). Successful
  answers are cached in memory (256 entries, 5-decimal key). Offline, the
  bubble says the address is unavailable.
- **Base map.** Optional. The tile URL template, attribution, and maximum zoom
  come from `madhi.tiles.*` in `local.properties` or `MADHI_TILE_*`
  environment variables. If the template is empty, the map has a plain
  background and makes no tile requests. Tiles go through a separate OkHttp
  client with a 192 MB disk cache in `filesDir` (not `cacheDir`, which Android
  clears under storage pressure). The cache is always checked before the
  network.

## Diagnostics

`BuildDiagnosticsReport` collects: tracking state and problems, intent and
interval, permissions and system state (`AndroidTrackingEnvironment`), whether
the service is running, activation, pending count and oldest pending point,
the sync journal, and **coverage** over the last hour: points recorded vs.
expected for the interval. Below 66 % the coverage is shown as degraded.

The technical log (`EventLog`) writes to Logcat under the tag `MadhiTracker`:

    adb logcat -s MadhiTracker

Its signature accepts an event and a short string, never coordinates, so
locations cannot end up in the logs.

## Permissions

Declared in `app/src/main/AndroidManifest.xml`, each with a comment saying why:
fine and coarse location, background location, foreground service (location
type), notifications, boot completed, exact alarms (`SCHEDULE_EXACT_ALARM` and
`USE_EXACT_ALARM`, the app is distributed outside the Play Store), battery
optimisation exemption, wake lock (held only during a one-off capture),
internet, and network state.

## Build configuration

Values injected into `BuildConfig` (`app/build.gradle.kts`,
`infrastructure/config/AppConfig.kt`), from environment variables first, then
`local.properties`:

| BuildConfig | Environment | local.properties |
|---|---|---|
| `API_BASE_URL` | `MADHI_API_BASE_URL_DEBUG` / `_RELEASE` | `madhi.api.baseUrl.debug` / `.release` |
| `UPDATE_PAGE_URL` | `MADHI_UPDATE_PAGE_URL_DEBUG` / `_RELEASE` | `madhi.updatePageUrl.debug` / `.release` |
| `TILE_URL_TEMPLATE` | `MADHI_TILE_URL_TEMPLATE` | `madhi.tiles.urlTemplate` |
| `TILE_ATTRIBUTION` | `MADHI_TILE_ATTRIBUTION` | `madhi.tiles.attribution` |
| `TILE_MAX_ZOOM` | `MADHI_TILE_MAX_ZOOM` | `madhi.tiles.maxZoom` (default 19) |

Signing uses `ANDROID_SIGNING_*` or `keystore.properties`. The release build
fails if the API URL or the signing material is missing.

## Tests

`app/src/test/`, JVM only: domain rules, use cases with fakes
(`fakes/Fakes.kt`), ViewModels, and adapters with Robolectric (Room,
DataStore, Keystore credentials, HTTP gateways against a fake call factory,
WorkManager scheduling). `SchemaContractTest` checks the exported Room schema.

    ./gradlew check
