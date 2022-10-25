package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.komplement
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketUtbetaling
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import java.time.Clock

object KryssjekkTidslinjerOgSimulering {
    fun sjekk(
        underArbeidEndringsperiode: Periode,
        underArbeid: Utbetaling.UtbetalingForSimulering,
        eksisterende: List<Utbetaling>,
        simuler: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
        clock: Clock,
    ): Either<FeilVedKryssjekkAvTidslinjerOgSimulering, Unit> {
        val periode = Periode.create(
            fraOgMed = underArbeid.tidligsteDato(),
            tilOgMed = underArbeid.senesteDato(),
        )
        val simulertUtbetaling = simuler(underArbeid, periode)
            .getOrHandle {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke simulere: $it")
                return FeilVedKryssjekkAvTidslinjerOgSimulering.KunneIkkeSimulere(it).left()
            }

        val tidslinjeEksisterendeOgUnderArbeid = (eksisterende + underArbeid)
            .tidslinje(periode = periode, clock = clock)
            .getOrHandle {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it")
                return FeilVedKryssjekkAvTidslinjerOgSimulering.KunneIkkeGenerereTidslinje.left()
            }

        sjekkTidslinjeMotSimulering(
            tidslinje = tidslinjeEksisterendeOgUnderArbeid,
            simulering = simulertUtbetaling.simulering,
        ).getOrHandle {
            log.error("Feil ved kryssjekk av tidslinje og simulering. Se sikkerlogg for detaljer")
            sikkerLogg.error("Feil: $it ved kryssjekk av tidslinje: $tidslinjeEksisterendeOgUnderArbeid og simulering: ${simulertUtbetaling.simulering}")
            return FeilVedKryssjekkAvTidslinjerOgSimulering.FeilVedSjekkAvTidslinjeMotSimulering(it.first()).left()
        }

        if (eksisterende.harUtbetalingerEtter(underArbeidEndringsperiode.tilOgMed)) {
            val rekonstruertPeriode = Periode.create(
                fraOgMed = underArbeidEndringsperiode.tilOgMed.førsteINesteMåned(),
                tilOgMed = eksisterende.maxOf { it.senesteDato() },
            )
            val tidslinjeUnderArbeid = listOf(underArbeid).tidslinje(
                periode = rekonstruertPeriode,
                clock = clock,
            ).getOrHandle {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it")
                return FeilVedKryssjekkAvTidslinjerOgSimulering.KunneIkkeGenerereTidslinje.left()
            }

            val tidslinjeEksisterende = eksisterende.tidslinje(
                periode = rekonstruertPeriode,
                clock = clock,
            ).getOrHandle {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it")
                return FeilVedKryssjekkAvTidslinjerOgSimulering.KunneIkkeGenerereTidslinje.left()
            }
            if (!tidslinjeUnderArbeid.ekvivalentMed(tidslinje = tidslinjeEksisterende, periode = rekonstruertPeriode)) {
                log.error("Feil ved kryssjekk av tidslinje og simulering. Se sikkerlogg for detaljer")
                sikkerLogg.error("Feil ved kryssjekk av tidslinje: Tidslinje for ny utbetaling:$tidslinjeUnderArbeid er ulik eksisterende:$tidslinjeEksisterende for rekonstruert periode:$rekonstruertPeriode")
                return FeilVedKryssjekkAvTidslinjerOgSimulering.RekonstruertUtbetalingsperiodeErUlikOpprinnelig.left()
            }
        }
        return Unit.right()
    }
}

sealed interface FeilVedKryssjekkAvTidslinjerOgSimulering {
    data class FeilVedSjekkAvTidslinjeMotSimulering(val feil: no.nav.su.se.bakover.domain.oppdrag.FeilVedSjekkAvTidslinjeMotSimulering) : FeilVedKryssjekkAvTidslinjerOgSimulering
    object RekonstruertUtbetalingsperiodeErUlikOpprinnelig : FeilVedKryssjekkAvTidslinjerOgSimulering

    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : FeilVedKryssjekkAvTidslinjerOgSimulering

    object KunneIkkeGenerereTidslinje : FeilVedKryssjekkAvTidslinjerOgSimulering
}

sealed class FeilVedSjekkAvTidslinjeMotSimulering(val prioritet: Int) : Comparable<FeilVedSjekkAvTidslinjeMotSimulering> {
    object StansMedFeilutbetaling : FeilVedSjekkAvTidslinjeMotSimulering(prioritet = 1)
    data class KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
        val periode: Periode,
        val simulertType: String,
        val tidslinjeType: String,
    ) : FeilVedSjekkAvTidslinjeMotSimulering(prioritet = 2)
    data class SimulertBeløpOgTidslinjeBeløpErForskjellig(
        val periode: Periode,
        val simulertBeløp: Int,
        val tidslinjeBeløp: Int,
    ) : FeilVedSjekkAvTidslinjeMotSimulering(prioritet = 2)

    override fun compareTo(other: FeilVedSjekkAvTidslinjeMotSimulering): Int {
        return this.prioritet.compareTo(other.prioritet)
    }
}

private fun sjekkTidslinjeMotSimulering(
    tidslinje: TidslinjeForUtbetalinger,
    simulering: Simulering,
): Either<List<FeilVedSjekkAvTidslinjeMotSimulering>, Unit> {
    val tolketSimulering = simulering.tolk()
    val feil = mutableListOf<FeilVedSjekkAvTidslinjeMotSimulering>()

    if (tolketSimulering.erTomSimulering()) {
        tidslinje.tidslinje.forEach { utbetaling ->
            kryssjekkType(
                tolketPeriode = utbetaling.periode,
                tolket = TolketUtbetaling.IngenUtbetaling(),
                utbetaling = utbetaling,
            ).getOrHandle { feil.add(it) }
        }
    } else {
        tolketSimulering.simulertePerioder.map { it.periode }.komplement().forEach { periode ->
            /**
             * Fravær av måneder her er det samme som at ingenting vil bli utbetalt. Dette kan skyldes
             * at ytelsen er stanset/opphørt, avkortet, eller at det aldri har eksistert utbetalinger for perioden
             * (f.eks ved hull mellom stønadsperioder e.l). Dersom vi har utbetalinger på tidslinjen for
             * de aktuelle periodene må vi sjekke at vi er "enige" i at månedene ikke fører til utbetaling.
             */
            tidslinje.gjeldendeForDato(periode.fraOgMed)?.also { utbetalingslinjePåTidslinje ->
                kryssjekkType(
                    tolketPeriode = periode,
                    tolket = TolketUtbetaling.IngenUtbetaling(),
                    utbetaling = utbetalingslinjePåTidslinje,
                ).getOrHandle { feil.add(it) }
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
                    ).getOrHandle { feil.add(it) }
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
                    ).getOrHandle { feil.add(it) }
                    kryssjekkType(
                        tolketPeriode = tolketPeriode.periode,
                        tolket = tolket,
                        utbetaling = utbetaling,
                    ).getOrHandle { feil.add(it) }
                }
            }
        }
    }

    return when (feil.isEmpty()) {
        true -> Unit.right()
        false -> feil.sorted().left()
    }
}

private fun kryssjekkType(
    tolketPeriode: Periode,
    tolket: TolketUtbetaling,
    utbetaling: UtbetalingslinjePåTidslinje,
): Either<FeilVedSjekkAvTidslinjeMotSimulering, Unit> {
    return when (tolket) {
        is TolketUtbetaling.IngenUtbetaling -> {
            if (!(
                utbetaling is UtbetalingslinjePåTidslinje.Stans ||
                    utbetaling is UtbetalingslinjePåTidslinje.Opphør ||
                    utbetaling is UtbetalingslinjePåTidslinje.Ny && utbetaling.beløp == 0 ||
                    utbetaling is UtbetalingslinjePåTidslinje.Reaktivering && utbetaling.beløp == 0
                )
            ) {
                FeilVedSjekkAvTidslinjeMotSimulering.KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
                    periode = tolketPeriode,
                    simulertType = tolket::class.toString(),
                    tidslinjeType = utbetaling::class.toString(),
                ).left()
            } else {
                Unit.right()
            }
        }
        is TolketUtbetaling.Etterbetaling -> {
            if (!(utbetaling is UtbetalingslinjePåTidslinje.Ny || utbetaling is UtbetalingslinjePåTidslinje.Reaktivering)) {
                FeilVedSjekkAvTidslinjeMotSimulering.KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
                    periode = tolketPeriode,
                    simulertType = tolket::class.toString(),
                    tidslinjeType = utbetaling::class.toString(),
                ).left()
            } else {
                Unit.right()
            }
        }
        is TolketUtbetaling.Feilutbetaling -> {
            if (utbetaling is UtbetalingslinjePåTidslinje.Stans) {
                return FeilVedSjekkAvTidslinjeMotSimulering.StansMedFeilutbetaling.left()
            }
            if (!(utbetaling is UtbetalingslinjePåTidslinje.Ny || utbetaling is UtbetalingslinjePåTidslinje.Opphør)) {
                FeilVedSjekkAvTidslinjeMotSimulering.KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
                    periode = tolketPeriode,
                    simulertType = tolket::class.toString(),
                    tidslinjeType = utbetaling::class.toString(),
                ).left()
            } else {
                Unit.right()
            }
        }
        is TolketUtbetaling.Ordinær -> {
            if (!(utbetaling is UtbetalingslinjePåTidslinje.Ny || utbetaling is UtbetalingslinjePåTidslinje.Reaktivering)) {
                FeilVedSjekkAvTidslinjeMotSimulering.KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
                    periode = tolketPeriode,
                    simulertType = tolket::class.toString(),
                    tidslinjeType = utbetaling::class.toString(),
                ).left()
            } else {
                Unit.right()
            }
        }
        is TolketUtbetaling.UendretUtbetaling -> {
            if (!(utbetaling is UtbetalingslinjePåTidslinje.Ny || utbetaling is UtbetalingslinjePåTidslinje.Reaktivering)) {
                FeilVedSjekkAvTidslinjeMotSimulering.KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
                    periode = tolketPeriode,
                    simulertType = tolket::class.toString(),
                    tidslinjeType = utbetaling::class.toString(),
                ).left()
            } else {
                Unit.right()
            }
        }
    }
}

private fun kryssjekkBeløp(
    tolketPeriode: Periode,
    tolket: TolketUtbetaling,
    utbetaling: UtbetalingslinjePåTidslinje,
): Either<FeilVedSjekkAvTidslinjeMotSimulering.SimulertBeløpOgTidslinjeBeløpErForskjellig, Unit> {
    return if (tolket.hentØnsketUtbetaling().sum() != utbetaling.beløp) {
        FeilVedSjekkAvTidslinjeMotSimulering.SimulertBeløpOgTidslinjeBeløpErForskjellig(
            periode = tolketPeriode,
            simulertBeløp = tolket.hentØnsketUtbetaling().sum(),
            tidslinjeBeløp = utbetaling.beløp,
        ).left()
    } else {
        Unit.right()
    }
}
