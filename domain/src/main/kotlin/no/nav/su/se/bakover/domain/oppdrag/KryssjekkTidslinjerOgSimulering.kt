package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import java.time.Clock

object KryssjekkTidslinjerOgSimulering {
    fun sjekk(
        underArbeidEndringsperiode: Periode,
        underArbeid: Utbetaling.UtbetalingForSimulering,
        eksisterende: List<Utbetaling>,
        simuler: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
        clock: Clock,
    ): Either<KryssjekkAvTidslinjeOgSimuleringFeilet, Unit> {
        val periode = Periode.create(
            fraOgMed = underArbeid.tidligsteDato(),
            tilOgMed = underArbeid.senesteDato(),
        )
        val simulertUtbetaling = simuler(underArbeid, periode)
            .getOrHandle {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke simulere: $it")
                return KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeSimulere(it).left()
            }

        val tidslinjeEksisterendeOgUnderArbeid = (eksisterende + underArbeid)
            .tidslinje(periode = periode, clock = clock)
            .getOrHandle {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it")
                return KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeGenerereTidslinje.left()
            }

        sjekkTidslinjeMotSimulering(
            tidslinje = tidslinjeEksisterendeOgUnderArbeid,
            simulering = simulertUtbetaling.simulering,
        ).getOrHandle {
            log.error("Feil (${it.map { it::class.simpleName }}) ved kryssjekk av tidslinje og simulering. Se sikkerlogg for detaljer")
            sikkerLogg.error("Feil: $it ved kryssjekk av tidslinje: $tidslinjeEksisterendeOgUnderArbeid og simulering: ${simulertUtbetaling.simulering}")
            return KryssjekkAvTidslinjeOgSimuleringFeilet.KryssjekkFeilet(it.first()).left()
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
                return KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeGenerereTidslinje.left()
            }

            val tidslinjeEksisterende = eksisterende.tidslinje(
                periode = rekonstruertPeriode,
                clock = clock,
            ).getOrHandle {
                log.error("Feil ved kryssjekk av tidslinje og simulering, kunne ikke generere tidslinjer: $it")
                return KryssjekkAvTidslinjeOgSimuleringFeilet.KunneIkkeGenerereTidslinje.left()
            }
            if (!tidslinjeUnderArbeid.ekvivalentMed(tidslinje = tidslinjeEksisterende, periode = rekonstruertPeriode)) {
                log.error("Feil ved kryssjekk av tidslinje og simulering. Tidslinje for ny utbetaling er ulik eksisterende. Se sikkerlogg for detaljer")
                sikkerLogg.error("Feil ved kryssjekk av tidslinje: Tidslinje for ny utbetaling:$tidslinjeUnderArbeid er ulik eksisterende:$tidslinjeEksisterende for rekonstruert periode:$rekonstruertPeriode")
                return KryssjekkAvTidslinjeOgSimuleringFeilet.RekonstruertUtbetalingsperiodeErUlikOpprinnelig.left()
            }
        }
        return Unit.right()
    }
}

sealed interface KryssjekkAvTidslinjeOgSimuleringFeilet {
    data class KryssjekkFeilet(val feil: KryssjekkFeil) : KryssjekkAvTidslinjeOgSimuleringFeilet
    object RekonstruertUtbetalingsperiodeErUlikOpprinnelig : KryssjekkAvTidslinjeOgSimuleringFeilet

    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : KryssjekkAvTidslinjeOgSimuleringFeilet

    object KunneIkkeGenerereTidslinje : KryssjekkAvTidslinjeOgSimuleringFeilet
}

sealed class KryssjekkFeil(val prioritet: Int) : Comparable<KryssjekkFeil> {
    data class StansMedFeilutbetaling(val måned: Måned) : KryssjekkFeil(prioritet = 1)
    data class GjenopptakMedFeilutbetaling(val måned: Måned) : KryssjekkFeil(prioritet = 1)
    data class KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
        val periode: Måned,
        val simulertType: String,
        val tidslinjeType: String,
    ) : KryssjekkFeil(prioritet = 2)
    data class SimulertBeløpOgTidslinjeBeløpErForskjellig(
        val periode: Måned,
        val simulertBeløp: Int,
        val tidslinjeBeløp: Int,
    ) : KryssjekkFeil(prioritet = 2)

    override fun compareTo(other: KryssjekkFeil): Int {
        return this.prioritet.compareTo(other.prioritet)
    }
}

private fun sjekkTidslinjeMotSimulering(
    tidslinje: TidslinjeForUtbetalinger,
    simulering: Simulering,
): Either<List<KryssjekkFeil>, Unit> {
    val feil = mutableListOf<KryssjekkFeil>()

    if (simulering.erAlleMånederUtenUtbetaling()) {
        simulering.periode().also { periode ->
            periode.måneder().forEach {
                val utbetaling = tidslinje.gjeldendeForDato(it.fraOgMed)!!
                if (!(
                    utbetaling is UtbetalingslinjePåTidslinje.Stans ||
                        utbetaling is UtbetalingslinjePåTidslinje.Opphør ||
                        utbetaling is UtbetalingslinjePåTidslinje.Ny && utbetaling.beløp == 0 ||
                        utbetaling is UtbetalingslinjePåTidslinje.Reaktivering && utbetaling.beløp == 0
                    )
                ) {
                    feil.add(
                        KryssjekkFeil.KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
                            periode = it,
                            simulertType = "IngenUtbetaling",
                            tidslinjeType = utbetaling::class.toString(),
                        ),
                    )
                }
            }
        }
    } else {
        simulering.månederMedSimuleringsresultat().forEach { måned ->
            val utbetaling = tidslinje.gjeldendeForDato(måned.fraOgMed)!!
            if (utbetaling is UtbetalingslinjePåTidslinje.Stans && simulering.harFeilutbetalinger()) {
                feil.add(KryssjekkFeil.StansMedFeilutbetaling(måned))
            }
        }

        simulering.månederMedSimuleringsresultat().forEach { måned ->
            val utbetaling = tidslinje.gjeldendeForDato(måned.fraOgMed)!!
            if (utbetaling is UtbetalingslinjePåTidslinje.Reaktivering && simulering.harFeilutbetalinger()) {
                feil.add(KryssjekkFeil.GjenopptakMedFeilutbetaling(måned))
            }
        }

        simulering.hentTotalUtbetaling().forEach { månedsbeløp ->
            kryssjekkBeløp(
                tolketPeriode = månedsbeløp.periode,
                simulertUtbetaling = månedsbeløp.beløp.sum(),
                beløpPåTidslinje = tidslinje.gjeldendeForDato(månedsbeløp.periode.fraOgMed)!!.beløp,
            ).getOrHandle { feil.add(it) }
        }
    }
    return when (feil.isEmpty()) {
        true -> Unit.right()
        false -> feil.sorted().left()
    }
}

private fun kryssjekkBeløp(
    tolketPeriode: Måned,
    simulertUtbetaling: Int,
    beløpPåTidslinje: Int,
): Either<KryssjekkFeil.SimulertBeløpOgTidslinjeBeløpErForskjellig, Unit> {
    return if (simulertUtbetaling != beløpPåTidslinje) {
        KryssjekkFeil.SimulertBeløpOgTidslinjeBeløpErForskjellig(
            periode = tolketPeriode,
            simulertBeløp = simulertUtbetaling,
            tidslinjeBeløp = beløpPåTidslinje,
        ).left()
    } else {
        Unit.right()
    }
}
