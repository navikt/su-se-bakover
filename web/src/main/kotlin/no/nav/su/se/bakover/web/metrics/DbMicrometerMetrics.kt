package no.nav.su.se.bakover.web.metrics

import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics

data class DbMicrometerMetrics(
    private val suMetrics: SuMetrics,
) : DbMetrics {
    override fun <T> timeQuery(label: String, block: () -> T): T {
        val timer = suMetrics.dbTimer.labelValues(label).startTimer()
        return block().also {
            timer.observeDuration()
        }
    }
}
