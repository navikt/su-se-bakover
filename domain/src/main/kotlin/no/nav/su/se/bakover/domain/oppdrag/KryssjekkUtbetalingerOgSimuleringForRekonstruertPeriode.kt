package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.komplement
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketUtbetaling
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import java.time.Clock

class KryssjekkUtbetalingerOgSimuleringForRekonstruertPeriode(
    endringsperiode: Periode,
    utbetaling: Utbetaling.UtbetalingForSimulering,
    eksisterendeUtbetalinger: List<Utbetaling>,
    simuler: (request: SimulerUtbetalingForPeriode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    clock: Clock
) {
    private lateinit var simulering: Simulering

    init {
        try {
            if (utbetaling.senesteDato().isAfter(endringsperiode.tilOgMed)) {
                val kontrollPeriode = Periode.create(
                    fraOgMed = endringsperiode.tilOgMed.førsteINesteMåned(),
                    tilOgMed = utbetaling.senesteDato()
                )

                simuler(
                    SimulerUtbetalingForPeriode(
                        utbetaling = utbetaling,
                        simuleringsperiode = kontrollPeriode
                    )
                ).getOrHandle { throw RuntimeException("Kunne ikke simulere utbetaling: $it") }
                    .let { simulertUtbetaling ->
                        simulering = simulertUtbetaling.simulering

                        val tolketSimulering = simulertUtbetaling.simulering.tolk()
                        val tidslinjeEksisterende = eksisterendeUtbetalinger.tidslinje(periode = kontrollPeriode, clock = clock)
                            .getOrHandle { throw IllegalStateException("Klarte ikke å konstruere tidslinje utbetalinger") }
                        val tidslinjeRekonstruert = listOf(utbetaling).tidslinje(periode = kontrollPeriode, clock = clock)
                            .getOrHandle { throw IllegalStateException("Klarte ikke å konstruere tidslinje utbetalinger") }

                        check(
                            tidslinjeEksisterende.ekvivalentMed(
                                periode = kontrollPeriode,
                                tidslinje = tidslinjeRekonstruert,
                            )
                        ) { "Tidslinje for eksisterende utbetalinger er ulik tidslinje for rekonstruerte linjer." }

                        tolketSimulering.simulertePerioder.map { it.periode }.komplement().forEach {
                            /**
                             * Fravær av måneder her er det samme som at ingenting vil bli utbetalt. Dette kan skyldes
                             * at ytelsen er stanset/opphørt, eller at det aldri har eksistert utbetalinger for perioden
                             * (f.eks ved hull mellom stønadsperioder e.l). Dersom vi har utbetalinger på tidslinjen for
                             * de aktuelle periodene må vi sjekke at vi er "enige" i at månedene ikke fører til utbetaling.
                             */
                            val eksisterende = tidslinjeEksisterende.gjeldendeForDato(it.fraOgMed)
                            val rekonstruert = tidslinjeRekonstruert.gjeldendeForDato(it.fraOgMed)
                            if (eksisterende != null && rekonstruert != null) {
                                kryssjekkTyper(
                                    tolketPeriode = it,
                                    tolket = TolketUtbetaling.IngenUtbetaling(), // ekvivalent med fravær av måned i utbetaling
                                    tidslinjeEksisterende = tidslinjeEksisterende,
                                    tidslinjeRekonstruert = tidslinjeRekonstruert,
                                )
                            }
                        }

                        tolketSimulering.simulertePerioder.forEach { tolketPeriode ->
                            when (val tolket = tolketPeriode.utbetaling) {
                                is TolketUtbetaling.IngenUtbetaling -> {
                                    kryssjekkTyper(
                                        tolketPeriode = tolketPeriode.periode,
                                        tolket = tolket,
                                        tidslinjeEksisterende = tidslinjeEksisterende,
                                        tidslinjeRekonstruert = tidslinjeRekonstruert,
                                    )
                                }
                                is TolketUtbetaling.Etterbetaling,
                                is TolketUtbetaling.Feilutbetaling,
                                is TolketUtbetaling.Ordinær,
                                is TolketUtbetaling.UendretUtbetaling -> {
                                    kryssjekkBeløp(
                                        tolketPeriode = tolketPeriode.periode,
                                        tolket = tolket,
                                        tidslinjeEksisterende = tidslinjeEksisterende,
                                        tidslinjeRekonstruert = tidslinjeRekonstruert
                                    )
                                    kryssjekkTyper(
                                        tolketPeriode = tolketPeriode.periode,
                                        tolket = tolket,
                                        tidslinjeEksisterende = tidslinjeEksisterende,
                                        tidslinjeRekonstruert = tidslinjeRekonstruert,
                                    )
                                }
                            }
                        }
                    }
            }
        } catch (ex: Throwable) {
            sikkerLogg.error("Kontroll av rekonstruerte utbetalinger feilet med melding:$ex, simulering: $simulering")
            throw ex
        }
    }
}

private fun kryssjekkTyper(
    tolketPeriode: Periode,
    tolket: TolketUtbetaling,
    tidslinjeEksisterende: TidslinjeForUtbetalinger,
    tidslinjeRekonstruert: TidslinjeForUtbetalinger,
) {
    val eksisterende = tidslinjeEksisterende.gjeldendeForDato(tolketPeriode.fraOgMed)!!
    val rekonstruert = tidslinjeRekonstruert.gjeldendeForDato(tolketPeriode.fraOgMed)!!

    when (tolket) {
        is TolketUtbetaling.IngenUtbetaling -> {
            check(
                (eksisterende is UtbetalingslinjePåTidslinje.Stans || eksisterende is UtbetalingslinjePåTidslinje.Opphør) &&
                    (rekonstruert is UtbetalingslinjePåTidslinje.Stans || rekonstruert is UtbetalingslinjePåTidslinje.Opphør)
            ) {
                errMsgTyper(
                    periode = tolketPeriode,
                    tolket = tolket,
                    eksisterende = eksisterende,
                    rekonstruert = rekonstruert
                )
            }
        }
        is TolketUtbetaling.Etterbetaling,
        is TolketUtbetaling.Feilutbetaling,
        is TolketUtbetaling.Ordinær,
        is TolketUtbetaling.UendretUtbetaling -> {
            check(
                (eksisterende is UtbetalingslinjePåTidslinje.Ny || eksisterende is UtbetalingslinjePåTidslinje.Reaktivering) &&
                    (rekonstruert is UtbetalingslinjePåTidslinje.Ny || rekonstruert is UtbetalingslinjePåTidslinje.Reaktivering)
            ) {
                errMsgTyper(
                    periode = tolketPeriode,
                    tolket = tolket,
                    eksisterende = eksisterende,
                    rekonstruert = rekonstruert
                )
            }
        }
    }
}

private fun errMsgTyper(
    periode: Periode,
    tolket: TolketUtbetaling,
    eksisterende: UtbetalingslinjePåTidslinje,
    rekonstruert: UtbetalingslinjePåTidslinje,
): String {
    return "Kombinasjon av simulert:${tolket::class}, eksisterende:${eksisterende::class} og rekonstruert:${rekonstruert::class} for periode:$periode er ugyldig"
}

private fun kryssjekkBeløp(
    tolketPeriode: Periode,
    tolket: TolketUtbetaling,
    tidslinjeEksisterende: TidslinjeForUtbetalinger,
    tidslinjeRekonstruert: TidslinjeForUtbetalinger,
) {
    val eksisterende = tidslinjeEksisterende.gjeldendeForDato(tolketPeriode.fraOgMed)!!
    val rekonstruert = tidslinjeRekonstruert.gjeldendeForDato(tolketPeriode.fraOgMed)!!
    check(
        tolket.hentØnsketUtbetaling().sum() == eksisterende.beløp
    ) {
        errMsgBeløp(
            periode = tolketPeriode,
            tolket = tolket.hentØnsketUtbetaling().sum(),
            eksisterende = eksisterende.beløp,
            rekonstruert = rekonstruert.beløp
        )
    }
    check(
        tolket.hentØnsketUtbetaling().sum() == rekonstruert.beløp
    ) {
        errMsgBeløp(
            periode = tolketPeriode,
            tolket = tolket.hentØnsketUtbetaling().sum(),
            eksisterende = eksisterende.beløp,
            rekonstruert = rekonstruert.beløp
        )
    }
}

private fun errMsgBeløp(
    periode: Periode,
    tolket: Int,
    eksisterende: Int,
    rekonstruert: Int,
): String {
    return "Beløp:$tolket for simulert periode:$periode er forskjellig er ikke likt eksisterende:$eksisterende og rekonstruert:$rekonstruert"
}
