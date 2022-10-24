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
    fun sjekkNyEllerOpphør(
        underArbeidEndringsperiode: Periode,
        underArbeid: Utbetaling.UtbetalingForSimulering,
        eksisterende: List<Utbetaling>,
        simuler: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
        clock: Clock,
    ): Either<FeilVedKryssjekkAvTidslinjerOgSimulering.NyEllerOpphør, Unit> {
        val periode = Periode.create(
            fraOgMed = underArbeid.tidligsteDato(),
            tilOgMed = underArbeid.senesteDato(),
        )
        val simulertUtbetaling = simuler(underArbeid, periode)
            .getOrHandle {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke simulere: $it")
                return FeilVedKryssjekkAvTidslinjerOgSimulering.NyEllerOpphør.KunneIkkeSimulere(it).left()
            }

        val tidslinjeEksisterendeOgUnderArbeid = (eksisterende + underArbeid)
            .tidslinje(periode = periode, clock = clock)
            .getOrHandle {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it")
                return FeilVedKryssjekkAvTidslinjerOgSimulering.NyEllerOpphør.KunneIkkeGenerereTidslinje.left()
            }

        sjekkTidslinjeMotSimulering(
            tidslinje = tidslinjeEksisterendeOgUnderArbeid,
            simulering = simulertUtbetaling.simulering,
        ).getOrHandle {
            log.error("Feil ved kryssjekk av tidslinje og simulering. Se sikkerlogg for detaljer")
            sikkerLogg.error("Feil: $it ved kryssjekk av tidslinje: $tidslinjeEksisterendeOgUnderArbeid og simulering: ${simulertUtbetaling.simulering}")
            return FeilVedKryssjekkAvTidslinjerOgSimulering.NyEllerOpphør.FeilVedSjekkAvTidslinjeMotSimulering.left()
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
                return FeilVedKryssjekkAvTidslinjerOgSimulering.NyEllerOpphør.KunneIkkeGenerereTidslinje.left()
            }

            val tidslinjeEksisterende = eksisterende.tidslinje(
                periode = rekonstruertPeriode,
                clock = clock,
            ).getOrHandle {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it")
                return FeilVedKryssjekkAvTidslinjerOgSimulering.NyEllerOpphør.KunneIkkeGenerereTidslinje.left()
            }
            if (!tidslinjeUnderArbeid.ekvivalentMed(tidslinje = tidslinjeEksisterende, periode = rekonstruertPeriode)) {
                log.error("Feil ved kryssjekk av tidslinje og simulering. Se sikkerlogg for detaljer")
                sikkerLogg.error("Feil ved kryssjekk av tidslinje: Tidslinje for ny utbetaling:$tidslinjeUnderArbeid er ulik eksisterende:$tidslinjeEksisterende for rekonstruert periode:$rekonstruertPeriode")
                return FeilVedKryssjekkAvTidslinjerOgSimulering.NyEllerOpphør.RekonstruertUtbetalingsperiodeErUlikOpprinnelig.left()
            }
        }
        return Unit.right()
    }

    fun sjekkGjenopptak(
        underArbeid: Utbetaling.SimulertUtbetaling,
        eksisterende: List<Utbetaling>,
        clock: Clock,
    ): Either<FeilVedKryssjekkAvTidslinjerOgSimulering.Gjenopptak.FeilVedSjekkAvTidslinjeMotSimulering, Unit> {
        check(underArbeid.erReaktivering()) { "Forventet utbetaling for reaktivering" }

        val periode = underArbeid.simulering.tolk().periode()

        val tidslinje = (eksisterende + underArbeid)
            .tidslinje(periode = periode, clock = clock)
            .getOrHandle { throw IllegalStateException("Kunne ikke generere tidslinje, feil: $it") }

        sjekkTidslinjeMotSimulering(
            tidslinje = tidslinje,
            simulering = underArbeid.simulering,
        ).getOrHandle {
            log.error("Feil ved kryssjekk av tidslinje og simulering. Se sikkerlogg for detaljer")
            sikkerLogg.error("Feil: $it ved kryssjekk av tidslinje: $tidslinje og simulering: ${underArbeid.simulering}")
            return FeilVedKryssjekkAvTidslinjerOgSimulering.Gjenopptak.FeilVedSjekkAvTidslinjeMotSimulering.left()
        }
        return Unit.right()
    }

    fun sjekkStans(
        underArbeid: Utbetaling.SimulertUtbetaling,
    ): Either<FeilVedKryssjekkAvTidslinjerOgSimulering.Stans, Utbetaling.SimulertUtbetaling> {
        check(underArbeid.erStans()) { "Forventet utbetaling for stans" }
        return when {
            underArbeid.simulering.harFeilutbetalinger() -> {
                FeilVedKryssjekkAvTidslinjerOgSimulering.Stans.SimuleringHarFeilutbetaling.left()
            }
            !underArbeid.simulering.tolk().erTomSimulering() -> {
                FeilVedKryssjekkAvTidslinjerOgSimulering.Stans.SimuleringInneholderUtbetalinger.left()
            }
            else -> {
                underArbeid.right()
            }
        }
    }
}

sealed interface FeilVedKryssjekkAvTidslinjerOgSimulering {

    sealed interface NyEllerOpphør : FeilVedKryssjekkAvTidslinjerOgSimulering {
        object FeilVedSjekkAvTidslinjeMotSimulering : NyEllerOpphør
        object RekonstruertUtbetalingsperiodeErUlikOpprinnelig : NyEllerOpphør

        data class KunneIkkeSimulere(val feil: SimuleringFeilet) : NyEllerOpphør

        object KunneIkkeGenerereTidslinje : NyEllerOpphør
    }

    sealed interface Gjenopptak : FeilVedKryssjekkAvTidslinjerOgSimulering {
        object FeilVedSjekkAvTidslinjeMotSimulering : Gjenopptak
    }
    sealed interface Stans : FeilVedKryssjekkAvTidslinjerOgSimulering {
        object SimuleringHarFeilutbetaling : Stans
        object SimuleringInneholderUtbetalinger : Stans
    }
}

sealed interface FeilVedSjekkAvTidslinjeMotSimulering {
    data class KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
        val periode: Periode,
        val simulertType: String,
        val tidslinjeType: String,
    ) : FeilVedSjekkAvTidslinjeMotSimulering
    data class SimulertBeløpOgTidslinjeBeløpErForskjellig(
        val periode: Periode,
        val simulertBeløp: Int,
        val tidslinjeBeløp: Int,
    ) : FeilVedSjekkAvTidslinjeMotSimulering
}

private fun sjekkTidslinjeMotSimulering(
    tidslinje: TidslinjeForUtbetalinger,
    simulering: Simulering,
): Either<List<FeilVedSjekkAvTidslinjeMotSimulering>, Unit> {
    val tolketSimulering = simulering.tolk()
    val feil = mutableListOf<FeilVedSjekkAvTidslinjeMotSimulering>()

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
    return when (feil.isEmpty()) {
        true -> Unit.right()
        false -> feil.left()
    }
}

private fun kryssjekkType(
    tolketPeriode: Periode,
    tolket: TolketUtbetaling,
    utbetaling: UtbetalingslinjePåTidslinje,
): Either<FeilVedSjekkAvTidslinjeMotSimulering.KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig, Unit> {
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
