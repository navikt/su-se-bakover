package no.nav.su.se.bakover.web.metrics

import no.nav.su.se.bakover.common.metrics.SuMetrics
import no.nav.su.se.bakover.database.DbMetrics

class DbMicrometerMetrics : DbMetrics {
    override fun <T> timeQuery(label: String, block: () -> T): T {
        val timer = SuMetrics.dbTimer.labels(label).startTimer()
        return block().also {
            timer.observeDuration()
        }
    }
}
