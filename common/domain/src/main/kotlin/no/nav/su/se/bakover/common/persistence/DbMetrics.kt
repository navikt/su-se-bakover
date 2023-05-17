package no.nav.su.se.bakover.common.persistence

interface DbMetrics {
    fun <T> timeQuery(label: String, block: () -> T): T
}
