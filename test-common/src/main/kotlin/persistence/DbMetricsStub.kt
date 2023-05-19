package no.nav.su.se.bakover.test.persistence

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics

val dbMetricsStub: DbMetrics = object : DbMetrics {
    override fun <T> timeQuery(label: String, block: () -> T): T {
        return block()
    }
}
