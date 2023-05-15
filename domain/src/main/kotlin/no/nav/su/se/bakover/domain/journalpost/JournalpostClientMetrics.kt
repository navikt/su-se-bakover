package no.nav.su.se.bakover.domain.journalpost

interface JournalpostClientMetrics {
    fun inkrementerBenyttetSkjema(skjema: BenyttetSkjema)

    enum class BenyttetSkjema {
        NAV_SU_KONTROLLNOTAT,
        DOKUMENTASJON_AV_OPPFØLGINGSSAMTALE,
    }
}
