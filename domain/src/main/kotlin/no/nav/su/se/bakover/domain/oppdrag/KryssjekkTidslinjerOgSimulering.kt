package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.komplement
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketUtbetaling
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import java.time.Clock

object KryssjekkTidslinjerOgSimulering {
    fun sjekkNyEllerOpphør(
        underArbeidEndringsperiode: Periode,
        underArbeid: Utbetaling.UtbetalingForSimulering,
        eksisterende: List<Utbetaling>,
        simuler: (request: SimulerUtbetalingForPeriode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
        clock: Clock,
    ) {
        val periode = Periode.create(
            fraOgMed = underArbeid.tidligsteDato(),
            tilOgMed = underArbeid.senesteDato(),
        )
        val simulertUtbetaling = simuler(
            SimulerUtbetalingForPeriode(
                utbetaling = underArbeid,
                simuleringsperiode = periode,
            ),
        ).getOrHandle { throw RuntimeException("Kunne ikke simulere utbetaling: $it") }

        val tidslinjeEksisterendeOgUnderArbeid = (eksisterende + underArbeid)
            .tidslinje(periode = periode, clock = clock)
            .getOrHandle { throw IllegalStateException("Kunne ikke generere tidslinje, feil: $it") }

        Either.catch {
            sjekkTidslinjeMotSimulering(
                tidslinje = tidslinjeEksisterendeOgUnderArbeid,
                simulering = simulertUtbetaling.simulering,
            )
        }.mapLeft {
            log.error("Feil ved kryssjekk av tidslinje og simulering. Se sikkerlogg for detaljer")
            sikkerLogg.error("Feil: $it ved kryssjekk av tidslinje: $tidslinjeEksisterendeOgUnderArbeid og simulering: ${simulertUtbetaling.simulering}")
            throw it
        }

        if (eksisterende.harUtbetalingerEtter(underArbeidEndringsperiode.tilOgMed)) {
            val rekonstruertPeriode = Periode.create(
                fraOgMed = underArbeidEndringsperiode.tilOgMed.førsteINesteMåned(),
                tilOgMed = eksisterende.maxOf { it.senesteDato() },
            )
            val tidslinjeUnderArbeid = listOf(underArbeid).tidslinje(
                periode = rekonstruertPeriode,
                clock = clock,
            ).getOrHandle { throw IllegalStateException("Kunne ikke generere tidslinje, feil: $it") }

            val tidslinjeEksisterende = eksisterende.tidslinje(
                periode = rekonstruertPeriode,
                clock = clock,
            ).getOrHandle { throw IllegalStateException("Kunne ikke generere tidslinje, feil: $it") }

            check(tidslinjeUnderArbeid.ekvivalentMed(tidslinje = tidslinjeEksisterende, periode = rekonstruertPeriode)) {
                "Tidslinje for ny utbetaling:$tidslinjeUnderArbeid er ulik eksisterende:$tidslinjeEksisterende for rekonstruert periode:$rekonstruertPeriode"
            }
        }
    }

    fun sjekkGjenopptak(
        underArbeid: Utbetaling.SimulertUtbetaling,
        eksisterende: List<Utbetaling>,
        clock: Clock,
    ) {
        val periode = underArbeid.simulering.tolk().periode()

        check(underArbeid.erReaktivering()) { "Forventet utbetaling for reaktivering" }

        val tidslinje = (eksisterende + underArbeid)
            .tidslinje(periode = periode, clock = clock)
            .getOrHandle { throw IllegalStateException("Kunne ikke generere tidslinje, feil: $it") }

        Either.catch {
            sjekkTidslinjeMotSimulering(
                tidslinje = tidslinje,
                simulering = underArbeid.simulering,
            )
        }.mapLeft {
            log.error("Feil ved kryssjekk av tidslinje og simulering. Se sikkerlogg for detaljer")
            sikkerLogg.error("Feil: $it ved kryssjekk av tidslinje: $tidslinje og simulering: ${underArbeid.simulering}")
            throw it
        }
    }

    fun sjekkStans(
        underArbeid: Utbetaling.SimulertUtbetaling,
    ) {
        check(underArbeid.erStans()) { "Forventet utbetaling for stans" }
        check(!underArbeid.simulering.harFeilutbetalinger()) { "Stans har feilutbetalinger" }
        check(underArbeid.simulering.tolk().erTomSimulering()) { "Forventer ingen måneder med utbetaling ved stans" }
    }
}

private fun sjekkTidslinjeMotSimulering(
    tidslinje: TidslinjeForUtbetalinger,
    simulering: Simulering,
) {
    val tolketSimulering = simulering.tolk()

    tolketSimulering.simulertePerioder.map { it.periode }.komplement().forEach { periode ->
        /**
         * Fravær av måneder her er det samme som at ingenting vil bli utbetalt. Dette kan skyldes
         * at ytelsen er stanset/opphørt, avkortet, eller at det aldri har eksistert utbetalinger for perioden
         * (f.eks ved hull mellom stønadsperioder e.l). Dersom vi har utbetalinger på tidslinjen for
         * de aktuelle periodene må vi sjekke at vi er "enige" i at månedene ikke fører til utbetaling.
         */
        tidslinje.gjeldendeForDato(periode.fraOgMed)?.also {
            kryssjekkType(
                tolketPeriode = periode,
                tolket = TolketUtbetaling.IngenUtbetaling(),
                utbetaling = it,
            )
        }
    }

    tolketSimulering.simulertePerioder.forEach { tolketPeriode ->
        val utbetaling = tidslinje.gjeldendeForDato(tolketPeriode.periode.fraOgMed)!!
        when (val tolket = tolketPeriode.utbetaling) {
            is TolketUtbetaling.IngenUtbetaling -> {
                kryssjekkType(
                    tolketPeriode = tolketPeriode.periode,
                    tolket = tolket,
                    utbetaling = utbetaling,
                )
            }
            is TolketUtbetaling.Etterbetaling,
            is TolketUtbetaling.Feilutbetaling,
            is TolketUtbetaling.Ordinær,
            is TolketUtbetaling.UendretUtbetaling,
            -> {
                kryssjekkBeløp(
                    tolketPeriode = tolketPeriode.periode,
                    tolket = tolket,
                    utbetaling = utbetaling,
                )
                kryssjekkType(
                    tolketPeriode = tolketPeriode.periode,
                    tolket = tolket,
                    utbetaling = utbetaling,
                )
            }
        }
    }
}

private fun kryssjekkType(
    tolketPeriode: Periode,
    tolket: TolketUtbetaling,
    utbetaling: UtbetalingslinjePåTidslinje,
) {
    when (tolket) {
        is TolketUtbetaling.IngenUtbetaling -> {
            check(
                (
                    utbetaling is UtbetalingslinjePåTidslinje.Stans ||
                        utbetaling is UtbetalingslinjePåTidslinje.Opphør ||
                        utbetaling is UtbetalingslinjePåTidslinje.Ny && utbetaling.beløp == 0 ||
                        utbetaling is UtbetalingslinjePåTidslinje.Reaktivering && utbetaling.beløp == 0
                    ),
            ) {
                errMsgType(
                    periode = tolketPeriode,
                    tolket = tolket,
                    utbetaling = utbetaling,
                )
            }
        }
        is TolketUtbetaling.Etterbetaling -> {
            check((utbetaling is UtbetalingslinjePåTidslinje.Ny || utbetaling is UtbetalingslinjePåTidslinje.Reaktivering)) {
                errMsgType(
                    periode = tolketPeriode,
                    tolket = tolket,
                    utbetaling = utbetaling,
                )
            }
        }
        is TolketUtbetaling.Feilutbetaling -> {
            check((utbetaling is UtbetalingslinjePåTidslinje.Ny || utbetaling is UtbetalingslinjePåTidslinje.Opphør)) {
                errMsgType(
                    periode = tolketPeriode,
                    tolket = tolket,
                    utbetaling = utbetaling,
                )
            }
        }
        is TolketUtbetaling.Ordinær -> {
            check((utbetaling is UtbetalingslinjePåTidslinje.Ny || utbetaling is UtbetalingslinjePåTidslinje.Reaktivering)) {
                errMsgType(
                    periode = tolketPeriode,
                    tolket = tolket,
                    utbetaling = utbetaling,
                )
            }
        }
        is TolketUtbetaling.UendretUtbetaling -> {
            check((utbetaling is UtbetalingslinjePåTidslinje.Ny || utbetaling is UtbetalingslinjePåTidslinje.Reaktivering)) {
                errMsgType(
                    periode = tolketPeriode,
                    tolket = tolket,
                    utbetaling = utbetaling,
                )
            }
        }
    }
}

private fun errMsgType(
    periode: Periode,
    tolket: TolketUtbetaling,
    utbetaling: UtbetalingslinjePåTidslinje,
): String {
    return "Kombinasjon av simulert:${tolket::class} og ${utbetaling::class} for periode:$periode er ugyldig"
}

private fun kryssjekkBeløp(
    tolketPeriode: Periode,
    tolket: TolketUtbetaling,
    utbetaling: UtbetalingslinjePåTidslinje,
) {
    check(tolket.hentØnsketUtbetaling().sum() == utbetaling.beløp) {
        errMsgBeløp(
            periode = tolketPeriode,
            tolket = tolket.hentØnsketUtbetaling().sum(),
            utbetaling = utbetaling.beløp,
        )
    }
}
private fun errMsgBeløp(
    periode: Periode,
    tolket: Int,
    utbetaling: Int,
): String {
    return "Beløp:$tolket for simulert periode:$periode er ikke likt beløp fra utbetalingstidlsinje:$utbetaling"
}
