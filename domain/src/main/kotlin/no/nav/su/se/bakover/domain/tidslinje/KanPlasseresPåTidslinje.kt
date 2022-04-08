package no.nav.su.se.bakover.domain.tidslinje

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Copyable
import no.nav.su.se.bakover.domain.OriginaltTidsstempel

interface KanPlasseresPåTidslinje<Type> :
    OriginaltTidsstempel,
    PeriodisertInformasjon,
    Copyable<CopyArgs.Tidslinje, Type>

data class MaskerFraTidslinje<T : KanPlasseresPåTidslinje<T>>(
    private val objekt: KanPlasseresPåTidslinje<T>,
    private val maskeringsPeriode: Periode = objekt.periode,
) : KanPlasseresPåTidslinje<T> by objekt {

    @Suppress("UNCHECKED_CAST")
    override fun copy(args: CopyArgs.Tidslinje): T {
        return MaskerFraTidslinje(objekt.copy(CopyArgs.Tidslinje.Maskert(args))) as T
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : KanPlasseresPåTidslinje<T>> KanPlasseresPåTidslinje<T>.masker(): List<KanPlasseresPåTidslinje<T>> {
    return listOf(this, MaskerFraTidslinje(this) as T)
}

@Suppress("UNCHECKED_CAST")
fun <T : KanPlasseresPåTidslinje<T>> KanPlasseresPåTidslinje<T>.masker(periode: Periode): List<KanPlasseresPåTidslinje<T>> {
    return listOf(this, MaskerFraTidslinje(this.copy(CopyArgs.Tidslinje.NyPeriode(periode))))
}
