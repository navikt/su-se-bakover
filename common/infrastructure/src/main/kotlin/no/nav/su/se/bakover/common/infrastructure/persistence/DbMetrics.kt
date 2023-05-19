package no.nav.su.se.bakover.common.infrastructure.persistence

interface DbMetrics {
    fun <T> timeQuery(label: String, block: () -> T): T
}
