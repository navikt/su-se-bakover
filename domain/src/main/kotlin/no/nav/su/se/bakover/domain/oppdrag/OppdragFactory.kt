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
            oppdragGjelder = sak.fnr,
            oppdragslinjer = behandling.perioder.map {
                Oppdragslinje(
                    fom = it.fom,
                    tom = it.tom,
                    endringskode = Oppdragslinje.Endringskode.NY,
                    refOppdragslinjeId = if (sak.hasOppdrag()) sak.sisteOppdrag!!.sisteOppdragslinje().id else null,
                    refSakId = sak.sakId,
                    beløp = it.beløp
                )
            }.also {
                it.zipWithNext { a, b -> b.link(a) }
            },
            saksbehandler = "saksbehandler", // TODO fyll ut denne,
            attestant = null // vi har ikke attestant på dette tidspunktet. Det feltet oppdateres på det tidspunktet vi får informasjonen.
        )
    }
}
