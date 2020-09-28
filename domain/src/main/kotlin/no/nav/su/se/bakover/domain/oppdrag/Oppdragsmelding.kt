package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now

data class Oppdragsmelding(
    val status: Oppdragsmeldingstatus,
    val originalMelding: String,
    val tidspunkt: Tidspunkt = now()
) {
    enum class Oppdragsmeldingstatus {
        SENDT,
        FEIL
    }
    fun erSendt() = Oppdragsmeldingstatus.SENDT == status
}
