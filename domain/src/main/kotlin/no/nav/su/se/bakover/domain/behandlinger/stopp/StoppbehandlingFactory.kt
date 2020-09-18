package no.nav.su.se.bakover.domain.behandlinger.stopp

import arrow.core.Either
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.BeregningsPeriode
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.Clock
import java.time.temporal.TemporalAdjusters

class StoppbehandlingFactory(
    private val simuleringClient: SimuleringClient,
    private val clock: Clock = Clock.systemUTC(),
    private val uuidFactory: UUIDFactory = UUIDFactory()
) {
    fun nyStoppbehandling(
        sak: Sak,
        saksbehandler: Saksbehandler,
        stoppÅrsak: String,
    ): Either<SimuleringFeilet, Stoppbehandling.Simulert> {

        val sisteUtbetaling = sak.oppdrag.sisteUtbetaling()!!
        require(!sisteUtbetaling.erNullUtbetaling()) {
            "Kan ikke stoppbehandle siden forrige behandling var en stoppbehandling."
        }
        // TODO validere: Sjekk at vi har faktiske utbetalinger som kan stoppes
        val firstDayNextMonth = idag().with(TemporalAdjusters.firstDayOfNextMonth())
        val utbetalingTilSimulering = sak.oppdrag.generererUtbetaling(
            beregningsperioder = listOf(
                BeregningsPeriode(
                    fom = firstDayNextMonth,
                    tom = sisteUtbetaling.sisteUtbetalingslinje()!!.tom,
                    beløp = 0,
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
                id = uuidFactory.newUUID(),
                opprettet = now(clock),
                sakId = sak.id,
                utbetaling = utbetaling,
                stoppÅrsak = stoppÅrsak,
                saksbehandler = saksbehandler
            )
        }
    }
}
