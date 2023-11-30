package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.periode.Periode
import økonomi.domain.utbetaling.Utbetalingslinje
import java.time.LocalDate

sealed class UtbetalingslinjePåTidslinje {
    abstract val kopiertFraId: UUID30
    abstract val periode: Periode
    abstract val beløp: Int

    /**
     * Ekvivalent i denne contexten betyr at linjen er av klasse, har samme [periode] og samme [beløp] som en annen linje.
     * Ekskluderer sjekk av [kopiertFraId]
     */
    abstract fun ekvivalentMed(other: UtbetalingslinjePåTidslinje): Boolean

    abstract fun nyPeriode(periode: Periode): UtbetalingslinjePåTidslinje

    data class Ny(
        override val kopiertFraId: UUID30,
        override val periode: Periode,
        override val beløp: Int,
    ) : UtbetalingslinjePåTidslinje() {

        override fun ekvivalentMed(other: UtbetalingslinjePåTidslinje): Boolean {
            return other is Ny && periode == other.periode && beløp == other.beløp
        }

        override fun nyPeriode(periode: Periode): Ny {
            return this.copy(periode = periode)
        }
    }

    data class Stans(
        override val kopiertFraId: UUID30,
        override val periode: Periode,
        override val beløp: Int = 0,
    ) : UtbetalingslinjePåTidslinje() {
        override fun ekvivalentMed(other: UtbetalingslinjePåTidslinje): Boolean {
            return other is Stans && periode == other.periode && beløp == other.beløp
        }

        override fun nyPeriode(periode: Periode): Stans {
            return this.copy(periode = periode)
        }
    }

    data class Opphør(
        override val kopiertFraId: UUID30,
        override val periode: Periode,
        override val beløp: Int = 0,
    ) : UtbetalingslinjePåTidslinje() {
        override fun ekvivalentMed(other: UtbetalingslinjePåTidslinje): Boolean {
            return other is Opphør && periode == other.periode && beløp == other.beløp
        }

        override fun nyPeriode(periode: Periode): Opphør {
            return this.copy(periode = periode)
        }
    }

    data class Reaktivering(
        override val kopiertFraId: UUID30,
        override val periode: Periode,
        override val beløp: Int,
    ) : UtbetalingslinjePåTidslinje() {
        override fun ekvivalentMed(other: UtbetalingslinjePåTidslinje): Boolean {
            return other is Reaktivering && periode == other.periode && beløp == other.beløp
        }

        override fun nyPeriode(periode: Periode): Reaktivering {
            return this.copy(periode = periode)
        }
    }
}

/**
 * En variant av 'copy' som kopierer innholdet i tidslinjen, men krymper på perioden.
 * Tar ikke hensyn til om det er overlapp eller hull i periodene.
 * @return Dersom perioden som sendes inn ikke finnes i tidslinjen, så null
 */
fun List<UtbetalingslinjePåTidslinje>.krympTilPeriode(
    periodenDetSkalKrympesTil: Periode,
): List<UtbetalingslinjePåTidslinje> {
    return this.mapNotNull {
        if (periodenDetSkalKrympesTil inneholder it.periode) {
            it
        } else if (periodenDetSkalKrympesTil overlapper it.periode) {
            it.nyPeriode(periode = periodenDetSkalKrympesTil.snitt(it.periode)!!)
        } else {
            null
        }
    }
}

internal fun List<Utbetalingslinje>.mapTilTidslinje(): List<UtbetalingslinjePåTidslinje> {
    return this.map { it.mapTilTidslinje(null) }
}

/**
 * Sjekker om denne tidslinjen er ekvivalent med [other] innenfor gitt periode.
 * Ulik dersom antall linjer er ulik.
 * Lik dersom begge listene er tomme.
 */
fun List<UtbetalingslinjePåTidslinje>.ekvivalentMedInnenforPeriode(
    other: List<UtbetalingslinjePåTidslinje>,
    periode: Periode,
): Boolean {
    val thisKrympet = this.krympTilPeriode(periode)
    val otherKrympet = other.krympTilPeriode(periode)
    return when {
        // Spesialtilfelle: perioden er utenfor begge tidslinjene
        // Bevarer eksisterende oppførsel.
        thisKrympet.isEmpty() && otherKrympet.isEmpty() -> false
        else -> thisKrympet.ekvivalentMed(otherKrympet)
    }
}

/**
 * Sjekker om denne tidslinjen er ekvivalent med [other].
 * Ulik dersom antall linjer er ulik.
 * Lik dersom begge listene er tomme.
 */
fun List<UtbetalingslinjePåTidslinje>.ekvivalentMed(
    other: List<UtbetalingslinjePåTidslinje>,
): Boolean {
    return when {
        this.size != other.size -> false
        this.isEmpty() -> true
        else -> this.zip(other).all {
            it.first.ekvivalentMed(it.second)
        }
    }
}

/**
 *  Mapper utbetalingslinjer til objekter som kan plasseres på tidslinjen.
 *  For subtyper av [no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalingslinje.Endring] erstattes
 *  [no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalingslinje.Endring.fraOgMed] med
 *  [no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalingslinje.Endring.virkningsperiode] da dette gjenspeiler datoen
 *  endringen effektueres hos oppdrag.
 *
 *  For typer som i praksis fører til at ingen ytelse utbetales, settes beløpet til 0.
 */
internal fun Utbetalingslinje.mapTilTidslinje(nyPeriode: Periode? = null): UtbetalingslinjePåTidslinje {
    return when (this) {
        is Utbetalingslinje.Endring.Opphør -> UtbetalingslinjePåTidslinje.Opphør(
            kopiertFraId = id,
            periode = nyPeriode ?: periode,
        )

        is Utbetalingslinje.Endring.Reaktivering -> UtbetalingslinjePåTidslinje.Reaktivering(
            kopiertFraId = id,
            periode = periode,
            beløp = beløp,
        )
        is Utbetalingslinje.Endring.Stans -> UtbetalingslinjePåTidslinje.Stans(
            kopiertFraId = id,
            periode = nyPeriode ?: periode,
        )

        is Utbetalingslinje.Ny -> UtbetalingslinjePåTidslinje.Ny(
            kopiertFraId = id,
            periode = nyPeriode ?: periode,
            beløp = beløp,
        )
    }
}

fun Utbetalinger.hentGjeldendeUtbetaling(forDato: LocalDate): Either<Utbetalinger.FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
    return tidslinje().fold(
        { Utbetalinger.FantIkkeGjeldendeUtbetaling.left() },
        { it.gjeldendeForDato(forDato)?.right() ?: Utbetalinger.FantIkkeGjeldendeUtbetaling.left() },
    )
}
