package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.førsteINesteMåned
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingslinjePåTidslinje
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.simulering.Simulering

data object KryssjekkTidslinjerOgSimulering {
    fun sjekk(
        log: Logger = LoggerFactory.getLogger(this::class.java),
        underArbeidEndringsperiode: Periode,
        underArbeid: Utbetaling.UtbetalingForSimulering,
        eksisterende: Utbetalinger,
        simuler: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    ): Either<KryssjekkAvTidslinjeOgSimuleringFeilet, Unit> {
        val periode = Periode.create(
            fraOgMed = underArbeid.tidligsteDato(),
            tilOgMed = underArbeid.senesteDato(),
        )
        val simulertUtbetaling = simuler(underArbeid, periode)
            .getOrElse {
                log.error(
                    "Feil ved kryssjekk av tidslinje og simulering, kunne ikke simulere: $it",
                    RuntimeException("Genererer en stacktrace for enklere debugging."),
                )
                return KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeSimulere(it).left()
            }

        val tidslinjeEksisterendeOgUnderArbeid = (eksisterende + underArbeid)
            .tidslinje()
            .getOrElse {
                log.error(
                    "Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it",
                    RuntimeException("Genererer en stacktrace for enklere debugging."),
                )
                return KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeGenerereTidslinje.left()
            }

        sjekkTidslinjeMotSimulering(
            tidslinjeEksisterendeOgUnderArbeid = tidslinjeEksisterendeOgUnderArbeid,
            simulering = simulertUtbetaling.simulering,
        ).getOrElse {
            log.error(
                "Feil (${it.map { it::class.simpleName }}) ved kryssjekk av tidslinje og simulering. Se sikkerlogg for detaljer",
                RuntimeException("Genererer en stacktrace for enklere debugging."),
            )
            sikkerLogg.error("Feil: $it ved kryssjekk av tidslinje: $tidslinjeEksisterendeOgUnderArbeid og simulering: ${simulertUtbetaling.simulering}")
            return KryssjekkAvTidslinjeOgSimuleringFeilet.KryssjekkFeilet(it.first()).left()
        }

        if (eksisterende.harUtbetalingerEtterDato(underArbeidEndringsperiode.tilOgMed)) {
            val rekonstruertPeriode = Periode.create(
                fraOgMed = underArbeidEndringsperiode.tilOgMed.førsteINesteMåned(),
                tilOgMed = eksisterende.maxOf { it.senesteDato() },
            )
            val tidslinjeUnderArbeid = underArbeid.tidslinje()

            val tidslinjeEksisterende = eksisterende.tidslinje().getOrElse {
                log.error(
                    "Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it",
                    RuntimeException("Genererer en stacktrace for enklere debugging."),
                )
                return KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeGenerereTidslinje.left()
            }
            if (!tidslinjeUnderArbeid.ekvivalentMedInnenforPeriode(tidslinjeEksisterende, rekonstruertPeriode)) {
                log.error(
                    "Feil ved kryssjekk av tidslinje og simulering. Tidslinje for ny utbetaling er ulik eksisterende. Se sikkerlogg for detaljer",
                    RuntimeException("Genererer en stacktrace for enklere debugging."),
                )
                sikkerLogg.error("Feil ved kryssjekk av tidslinje: Tidslinje for ny utbetaling:$tidslinjeUnderArbeid er ulik eksisterende:$tidslinjeEksisterende for rekonstruert periode:$rekonstruertPeriode")
                return KryssjekkAvTidslinjeOgSimuleringFeilet.RekonstruertUtbetalingsperiodeErUlikOpprinnelig.left()
            }
        }
        return Unit.right()
    }
}

sealed interface KryssjekkAvTidslinjeOgSimuleringFeilet {
    data class KryssjekkFeilet(val feil: KryssjekkFeil) : KryssjekkAvTidslinjeOgSimuleringFeilet
    data object RekonstruertUtbetalingsperiodeErUlikOpprinnelig : KryssjekkAvTidslinjeOgSimuleringFeilet

    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : KryssjekkAvTidslinjeOgSimuleringFeilet

    data object KunneIkkeGenerereTidslinje : KryssjekkAvTidslinjeOgSimuleringFeilet
}

private fun sjekkTidslinjeMotSimulering(
    tidslinjeEksisterendeOgUnderArbeid: TidslinjeForUtbetalinger,
    simulering: Simulering,
): Either<List<KryssjekkFeil>, Unit> {
    val feil = mutableListOf<KryssjekkFeil>()

    if (simulering.erAlleMånederUtenUtbetaling()) {
        simulering.periode().also { periode ->
            periode.måneder().forEach {
                val utbetaling = tidslinjeEksisterendeOgUnderArbeid.gjeldendeForDato(it.fraOgMed)!!
                if (!(
                        utbetaling is UtbetalingslinjePåTidslinje.Stans ||
                            utbetaling is UtbetalingslinjePåTidslinje.Opphør ||
                            (utbetaling is UtbetalingslinjePåTidslinje.Ny && utbetaling.beløp == 0) ||
                            (utbetaling is UtbetalingslinjePåTidslinje.Reaktivering && utbetaling.beløp == 0)
                        )
                ) {
                    feil.add(
                        KryssjekkFeil.KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
                            måned = it,
                            simulertType = "IngenUtbetaling",
                            tidslinjeType = utbetaling::class.toString(),
                        ),
                    )
                }
            }
        }
    } else {
        simulering.hentTotalUtbetaling().forEach { månedsbeløp ->
            kryssjekkBeløp(
                måned = månedsbeløp.periode,
                simulertUtbetaling = månedsbeløp.beløp.sum(),
                beløpPåTidslinje = tidslinjeEksisterendeOgUnderArbeid.gjeldendeForDato(månedsbeløp.periode.fraOgMed)!!.beløp,
            ).getOrElse { feil.add(it) }
        }
    }
    return when (feil.isEmpty()) {
        true -> Unit.right()
        false -> feil.sorted().left()
    }
}

private fun kryssjekkBeløp(
    måned: Måned,
    simulertUtbetaling: Int,
    beløpPåTidslinje: Int,
): Either<KryssjekkFeil.SimulertBeløpOgTidslinjeBeløpErForskjellig, Unit> {
    return if (simulertUtbetaling != beløpPåTidslinje) {
        KryssjekkFeil.SimulertBeløpOgTidslinjeBeløpErForskjellig(
            måned = måned,
            simulertBeløp = simulertUtbetaling,
            tidslinjeBeløp = beløpPåTidslinje,
        ).left()
    } else {
        Unit.right()
    }
}
