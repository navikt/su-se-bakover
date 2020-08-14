package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak

internal class OppdragFactory(
    private val behandling: Behandling.BehandlingOppdragsinformasjon,
    private val sak: Sak.SakOppdragsinformasjon
) {
    fun build(): Oppdrag {
        // TODO LOGIKK EN MASSS FOR Å FINNE RIKTIG OPPDRAG + LINJE

        return Oppdrag(
            sakId = sak.sakId,
            behandlingId = behandling.behandlingId,
            endringskode = if (sak.hasOppdrag()) Oppdrag.Endringskode.ENDR else Oppdrag.Endringskode.NY,
            oppdragslinjer = listOf(
                // TODO: Skal vi støtte fler beløp?
                Oppdragslinje(
                    fom = behandling.fom,
                    tom = behandling.tom,
                    endringskode = Oppdragslinje.Endringskode.NY,
                    refOppdragslinjeId = if (sak.hasOppdrag()) sak.sisteOppdrag!!.sisteOppdragslinje().id else null
                )
            )
        )
    }
}
