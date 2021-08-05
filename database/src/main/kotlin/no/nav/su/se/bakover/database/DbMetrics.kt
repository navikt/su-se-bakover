package no.nav.su.se.bakover.database

interface DbMetrics {
    fun <T> timeQuery(label: String, block: () -> T): T
}
