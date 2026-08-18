package com.madhi.tracker.application.port

import kotlin.time.Duration

/**
 * Le métronome d'acquisition. Implémenté par AlarmManager, mais les use
 * cases n'ont pas à le savoir : ils expriment « prochaine capture dans X ».
 *
 * La disponibilité des alarmes exactes n'apparaît pas ici : elle est déjà
 * exposée par [TrackingEnvironment], que le diagnostic consomme. Deux
 * chemins vers la même information auraient fini par se contredire.
 */
interface CaptureScheduler {
    fun scheduleNext(delay: Duration)
    fun cancel()
}
