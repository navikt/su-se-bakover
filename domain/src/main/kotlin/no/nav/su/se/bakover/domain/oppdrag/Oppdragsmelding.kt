package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel

data class Oppdragsmelding(
    val status: Oppdragsmeldingstatus,
    val originalMelding: String,
    val avstemmingsnøkkel: Avstemmingsnøkkel
) {
    enum class Oppdragsmeldingstatus {
        SENDT,
        FEIL
    }

    fun erSendt() = Oppdragsmeldingstatus.SENDT == status
}
