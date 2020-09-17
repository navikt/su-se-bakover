package no.nav.su.se.bakover.domain.behandlinger.stopp

import arrow.core.Either
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.BeregningsPeriode
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.Clock
import java.util.UUID

class StoppbehandlingFactory(
    private val simuleringClient: SimuleringClient,
    private val clock: Clock
) {
    fun nyStoppbehandling(
        sak: Sak,
        saksbehandler: Saksbehandler,
        stoppÅrsak: String,
    ): Either<SimuleringFeilet, Stoppbehandling.Simulert> {
        val behandlingId = UUID.randomUUID()
        val utbetalingTilSimulering = sak.oppdrag.generererUtbetaling(
            behandlingId = behandlingId,
            beregningsperioder = listOf(
                BeregningsPeriode( // TODO jah: Gjør litt mer bevisste valg rundt disse verdiene
                    fom = idag(),
                    tom = idag().plusYears(1),
                    beløp = 0,
                    sats = Sats.HØY
                )
            )
        )
        return simuleringClient.simulerUtbetaling(
            NyUtbetaling(
                oppdrag = sak.oppdrag,
                utbetaling = utbetalingTilSimulering,
                oppdragGjelder = sak.fnr,
                attestant = Attestant("SU") // Det er ikke nødvendigvis valgt en attestant på dette tidspunktet.
            )
        ).map { simulering ->
            val utbetaling = sak.oppdrag.opprettUtbetaling(utbetalingTilSimulering).also {
                it.addSimulering(simulering)
            }
            Stoppbehandling.Simulert(
                id = UUID.randomUUID(),
                opprettet = now(clock),
                sakId = sak.id,
                utbetaling = utbetaling,
                stoppÅrsak = stoppÅrsak,
                saksbehandler = saksbehandler
            )
        }
    }
}
