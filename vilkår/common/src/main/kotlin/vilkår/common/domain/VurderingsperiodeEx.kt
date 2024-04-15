package vilkår.common.domain

import arrow.core.Either
import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tid.periode.NonEmptySlåttSammenIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tid.periode.NonEmptySlåttSammenIkkeOverlappendePerioder.Companion.nonEmptyMinsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende

fun Nel<Vurderingsperiode>.minsteAntallSammenhengendePerioder(): NonEmptySlåttSammenIkkeOverlappendePerioder {
    return this.map { it.periode }.nonEmptyMinsteAntallSammenhengendePerioder()
}

fun Periode.inneholderAlle(vurderingsperioder: NonEmptyList<Vurderingsperiode>): Boolean {
    return vurderingsperioder.all { this inneholder it.periode }
}

fun <T : Vurderingsperiode> Nel<T>.kronologisk(): Either<KanIkkeSortereOverlappendePerioder, NonEmptyList<T>> {
    if (this.map { it.periode }.harOverlappende()) return KanIkkeSortereOverlappendePerioder.left()
    return this.sortedBy { it.periode.fraOgMed }.toNonEmptyList().right()
}

object KanIkkeSortereOverlappendePerioder

fun <T> List<T>.slåSammenLikePerioder(): Nel<T> where T : Vurderingsperiode, T : KanPlasseresPåTidslinje<T> {
    return this.lagTidslinje()!!.fold(mutableListOf<T>()) { acc, t ->
        if (acc.isEmpty()) {
            acc.add(t)
        } else if (acc.last().tilstøterOgErLik(t)) {
            val last = acc.removeLast()
            acc.add(
                last.copy(
                    CopyArgs.Tidslinje.NyPeriode(
                        Periode.create(
                            last.periode.fraOgMed,
                            (t as Vurderingsperiode).periode.tilOgMed,
                        ),
                    ),
                ),
            )
        } else {
            acc.add(t)
        }
        acc
    }.toNonEmptyList()
}
