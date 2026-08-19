package com.madhi.tracker.fakes

import com.madhi.tracker.application.usecase.CaptureLocation
import com.madhi.tracker.application.usecase.RecordLocation

/**
 * Assemble les use cases de capture à partir de doubles.
 *
 * Factorisé parce que plusieurs tests en ont besoin et que la liste des
 * dépendances a déjà changé une fois : la centraliser évite d'avoir à
 * toucher cinq fichiers au prochain remaniement.
 */
fun recordLocationWith(
    locationStore: FakeLocationStore,
    environment: FakeTrackingEnvironment = FakeTrackingEnvironment(),
    rebootJournalStore: FakeRebootJournalStore = FakeRebootJournalStore(),
    syncScheduler: FakeSyncScheduler = FakeSyncScheduler(),
    eventLog: RecordingEventLog = RecordingEventLog(),
    clock: FakeClock = FakeClock(),
) = RecordLocation(locationStore, environment, rebootJournalStore, syncScheduler, eventLog, clock)

fun captureLocationWith(
    locationSource: FakeLocationSource,
    trackingIntentStore: FakeTrackingIntentStore,
    recordLocation: RecordLocation,
    rebootJournalStore: FakeRebootJournalStore = FakeRebootJournalStore(),
    eventLog: RecordingEventLog = RecordingEventLog(),
    clock: FakeClock = FakeClock(),
) = CaptureLocation(locationSource, trackingIntentStore, recordLocation, rebootJournalStore, eventLog, clock)
