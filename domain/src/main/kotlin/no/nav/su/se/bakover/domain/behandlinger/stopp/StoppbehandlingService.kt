package no.nav.su.se.bakover.domain.behandlinger.stopp

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandlinger.stopp.StoppbehandlingService.ValidertStoppbehandling.Companion.validerStoppbehandling
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsperiode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class StoppbehandlingService(
    private val simuleringClient: SimuleringClient,
    private val clock: Clock = Clock.systemUTC(),
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val stoppbehandlingRepo: StoppbehandlingRepo
) {
    object KunneIkkeOppretteStoppbehandling

    fun stoppUtbetalinger(
        sak: Sak,
        saksbehandler: Saksbehandler,
        stoppÅrsak: String
    ): Either<KunneIkkeOppretteStoppbehandling, Stoppbehandling> {
        return stoppbehandlingRepo.hentPågåendeStoppbehandling(sak.id)?.right()
            ?: nyStoppbehandling(sak, saksbehandler, stoppÅrsak)
                .mapLeft { KunneIkkeOppretteStoppbehandling }
                .map { stoppbehandlingRepo.opprettStoppbehandling(it) }
    }

    private fun nyStoppbehandling(
        sak: Sak,
        saksbehandler: Saksbehandler,
        stoppÅrsak: String,
    ): Either<SimuleringFeilet, Stoppbehandling.Simulert> {

        val stoppesFraOgMed = idag(clock).with(TemporalAdjusters.firstDayOfNextMonth())
        val validertStoppbehandling = sak.oppdrag.validerStoppbehandling(stoppesFraOgMed)
        require(validertStoppbehandling.isValid()) {
            "Kan ikke stoppbehandle: $validertStoppbehandling"
        }
        val stoppesTilOgMed = sak.oppdrag.sisteOversendteUtbetaling()!!.sisteUtbetalingslinje()!!.tom

        val utbetalingTilSimulering = sak.oppdrag.generererUtbetaling(
            utbetalingsperioder = listOf(
                Utbetalingsperiode(
                    fom = stoppesFraOgMed,
                    tom = stoppesTilOgMed,
                    beløp = 0,
                )
            )
        )
        return simuleringClient.simulerUtbetaling(
            NyUtbetaling(
                oppdrag = sak.oppdrag,
                utbetaling = utbetalingTilSimulering,
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

    data class ValidertStoppbehandling(
        val harUtbetalingerSomKanStoppes: Boolean,
        val harBeløpOver0: Boolean
    ) {
        companion object {
            fun Oppdrag.validerStoppbehandling(stoppesFraDato: LocalDate) = ValidertStoppbehandling(
                harUtbetalingerSomKanStoppes = harOversendteUtbetalingerEtter(stoppesFraDato),
                harBeløpOver0 = sisteOversendteUtbetaling()?.utbetalingslinjer?.any {
                    // TODO jah: I en annen pull-request bør vi utvide en utbetaling til å være en sealed class med de forskjellig typene utbetaling.
                    it.beløp > 0 // Stopputbetalinger vil ha beløp 0. Vi ønsker ikke å stoppe en stopputbetaling.
                } ?: true
            )
        }

        fun isValid() = harUtbetalingerSomKanStoppes && harBeløpOver0
    }
}
