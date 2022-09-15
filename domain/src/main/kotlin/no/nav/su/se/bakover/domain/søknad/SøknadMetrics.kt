package no.nav.su.se.bakover.domain.søknad

interface SøknadMetrics {
    fun incrementNyCounter(handling: NyHandlinger)

    enum class NyHandlinger {
        PERSISTERT,
        JOURNALFØRT,
        OPPRETTET_OPPGAVE,
    }
}
