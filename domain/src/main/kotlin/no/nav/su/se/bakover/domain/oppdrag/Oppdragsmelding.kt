package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.now
import java.time.Instant

data class Oppdragsmelding(
    val status: Oppdragsmeldingstatus,
    val originalMelding: String,
    val tidspunkt: Instant = now()
) {
    enum class Oppdragsmeldingstatus {
        SENDT,
        FEIL
    }
    fun erSendt() = Oppdragsmeldingstatus.SENDT == status
}
