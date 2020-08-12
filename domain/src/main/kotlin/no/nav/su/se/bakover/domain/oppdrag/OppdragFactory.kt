package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak

class OppdragFactory(
    private val behandling: Behandling,
    private val sak: Sak
) {
    fun build(): Oppdrag {
        // TODO LOGIKK EN MASSS FOR Å FINNE RIKTIG OPPDRAG + LINJE
        return Oppdrag(
            sakId = sak.toDto().id,
            behandlingId = behandling.toDto().id,
            endringskode = Oppdrag.Endringskode.NY,
            oppdragslinjer = emptyList()
        )
    }
}
