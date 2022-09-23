package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Nel
import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje

fun Nel<Vurderingsperiode>.minsteAntallSammenhengendePerioder() =
    this.map { it.periode }.minsteAntallSammenhengendePerioder()

fun Periode.inneholderAlle(vurderingsperioder: NonEmptyList<Vurderingsperiode>): Boolean {
    return vurderingsperioder.all { this inneholder it.periode }
}

fun <T : Vurderingsperiode> Nel<T>.kronologisk(): NonEmptyList<T> {
    return sortedBy { it.periode }.toNonEmptyList()
}

fun <T> List<T>.slåSammenLikePerioder(): Nel<T> where T : Vurderingsperiode, T : KanPlasseresPåTidslinje<T> {
    return Tidslinje(
        periode = map { it.periode }.minAndMaxOf(),
        objekter = this,
    ).tidslinje.fold(mutableListOf<T>()) { acc, t ->
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
