package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak

internal class OppdragFactory(
    private val behandling: Behandling.Oppdragsinformasjon,
    private val sak: Sak.Oppdragsinformasjon
) {
    fun build(): Oppdrag {
        // TODO LOGIKK EN MASSS FOR Å FINNE RIKTIG OPPDRAG + LINJE
        return Oppdrag(
            sakId = sak.sakId,
            behandlingId = behandling.behandlingId,
            endringskode = Oppdrag.Endringskode.NY,
            oppdragslinjer = listOf(
                Oppdragslinje(
                    fom = behandling.fom,
                    tom = behandling.tom,
                    endringskode = Oppdragslinje.Endringskode.NY
                )
            )
        )
    }
}
