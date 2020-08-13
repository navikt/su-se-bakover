package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak

internal class OppdragFactory(
    private val behandling: Behandling.BehandlingOppdragsinformasjon,
    private val sak: Sak.SakOppdragsinformasjon
) {
    fun build(): Oppdrag {
        // TODO LOGIKK EN MASSS FOR Å FINNE RIKTIG OPPDRAG + LINJE

        if (sak.oppdrag.isEmpty()) {
            return Oppdrag(
                sakId = sak.sakId,
                behandlingId = behandling.behandlingId,
                endringskode = Oppdrag.Endringskode.NY,
                oppdragslinjer = listOf(
                    // TODO: Skal vi støtte fler beløp?
                    Oppdragslinje(
                        fom = behandling.fom,
                        tom = behandling.tom,
                        endringskode = Oppdragslinje.Endringskode.NY
                    )
                )
            )
        }
        if (!harOverlapp()) {
            return Oppdrag(
                sakId = sak.sakId,
                behandlingId = behandling.behandlingId,
                endringskode = Oppdrag.Endringskode.ENDR,
                oppdragslinjer = listOf(
                    // TODO: Skal vi støtte fler beløp?
                    Oppdragslinje(
                        fom = behandling.fom,
                        tom = behandling.tom,
                        endringskode = Oppdragslinje.Endringskode.NY
                    )
                )
            )
        }
        throw NotImplementedError("TODO kod mer")
    }

    fun harOverlapp() =
        sak.oppdrag.none {
            !it.overlapper(behandling.fom, behandling.tom)
        }
}
