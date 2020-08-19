package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak

internal class OppdragFactory(

    private val behandling: Behandling.BehandlingOppdragsinformasjon,
    private val sak: Sak.SakOppdragsinformasjon
) {
    fun build(): Oppdrag {
        return Oppdrag(
            sakId = sak.sakId,
            behandlingId = behandling.behandlingId,
            oppdragslinjer = behandling.perioder.map {
                Oppdragslinje(
                    fom = it.fom,
                    tom = it.tom,
                    forrigeOppdragslinjeId = if (sak.hasOppdrag()) sak.sisteOppdrag!!.sisteOppdragslinje().id else null,
                    beløp = it.beløp
                )
            }.also {
                it.zipWithNext { a, b -> b.link(a) }
            }
        )
    }
}
