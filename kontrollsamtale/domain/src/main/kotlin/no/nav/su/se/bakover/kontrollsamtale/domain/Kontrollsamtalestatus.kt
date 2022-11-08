package no.nav.su.se.bakover.kontrollsamtale.domain

enum class Kontrollsamtalestatus(val value: String) {
    PLANLAGT_INNKALLING("PLANLAGT_INNKALLING"),
    INNKALT("INNKALT"),
    GJENNOMFØRT("GJENNOMFØRT"),
    ANNULLERT("ANNULLERT"),
    IKKE_MØTT_INNEN_FRIST("IKKE_MØTT_INNEN_FRIST"),
}
