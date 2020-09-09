package no.nav.su.se.bakover.domain.oppdrag

data class Oppdragsmelding(
    val status: Oppdragsmeldingstatus,
    val originalMelding: String
) {
    enum class Oppdragsmeldingstatus {
        SENDT,
        FEIL
    }
    fun erSendt() = Oppdragsmeldingstatus.SENDT == status
}
