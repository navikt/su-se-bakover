package no.nav.su.se.bakover.domain.tidslinje

import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.application.KopierbarForTidslinje
import no.nav.su.se.bakover.common.application.OriginaltTidsstempel
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon

/**
 * Egenskaper som kreves for at et element skal kunne periodiseres av [Tidslinje].
 * Som et minimum må elementet være stand til å kunne plasseres på en [Tidslinje] med bare seg selv og utføre re-periodisering
 * vha. [Tidslinje.periode] og [MaskerFraTidslinje]
 */
interface KanPlasseresPåTidslinjeMedSegSelv<Type> :
    OriginaltTidsstempel,
    PeriodisertInformasjon,
    KopierbarForTidslinje<Type>

/**
 * Et syntetisk supersett av [KanPlasseresPåTidslinjeMedSegSelv] hvis intensjon er å markere at elementer av typen [Type] er ment å
 * plasseres på en tidslinje sammen med andre elementer enn seg selv. I praksis betyr dette at det må/bør være meningen
 * at elementer av [Type] med nyere [opprettet] skal overskrive eldre elementer med overlappende [periode].
 */
interface KanPlasseresPåTidslinje<Type> : KanPlasseresPåTidslinjeMedSegSelv<Type>

/**
 * Wrapper for elementer som skal maskeres fra en tidslinje.
 */
data class MaskerFraTidslinje<T : KanPlasseresPåTidslinjeMedSegSelv<T>>(
    private val objekt: KanPlasseresPåTidslinjeMedSegSelv<T>,
) : KanPlasseresPåTidslinjeMedSegSelv<T> by objekt {

    @Suppress("UNCHECKED_CAST")
    override fun copy(args: CopyArgs.Tidslinje): T {
        return MaskerFraTidslinje(objekt.copy(CopyArgs.Tidslinje.Maskert(args))) as T
    }
}

fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> KanPlasseresPåTidslinjeMedSegSelv<T>.masker(): List<T> {
    return masker(listOf(periode))
}

/**
 * Maskerer/fjerner elementet for periodene definert av [perioder] og re-periodiserer for eventuelle gjenværende perioder.
 */
fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> KanPlasseresPåTidslinjeMedSegSelv<T>.masker(perioder: List<Periode>): List<T> {
    return perioder.filter { periode overlapper it }
        .map {
            MaskerFraTidslinje(copy(CopyArgs.Tidslinje.NyPeriode(it)))
        }.let { maskert ->
            Tidslinje(
                periode = periode,
                objekter = maskert + this,
            ).tidslinje.filterNot { it is MaskerFraTidslinje<*> }
        }
}
