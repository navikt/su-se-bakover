package vilkår.uføre.domain

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

/**
 * @throws IllegalArgumentException hvis forventetInntekt er negativ
 */
data class Uføregrunnlag(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val periode: Periode,
    val uføregrad: Uføregrad,
    /** Kan ikke være negativ. */
    val forventetInntekt: Int,
) : Grunnlag, KanPlasseresPåTidslinje<Uføregrunnlag> {
    init {
        if (forventetInntekt < 0) throw IllegalArgumentException("forventetInntekt kan ikke være mindre enn 0")
    }

    fun oppdaterPeriode(periode: Periode): Uføregrunnlag {
        return this.copy(periode = periode)
    }

    override fun copy(args: CopyArgs.Tidslinje): Uføregrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            this.copy(id = UUID.randomUUID())
        }

        is CopyArgs.Tidslinje.NyPeriode -> {
            this.copy(id = UUID.randomUUID(), periode = args.periode)
        }
    }

    /**
     * Sjekker at periodene tilstøter, og om uføregrad og forventet inntekt er lik
     */
    override fun erLik(other: Grunnlag): Boolean {
        if (other !is Uføregrunnlag) {
            return false
        }

        return this.uføregrad == other.uføregrad && this.forventetInntekt == other.forventetInntekt
    }

    override fun copyWithNewId(): Uføregrunnlag = this.copy(id = UUID.randomUUID())

    companion object {
        fun List<Uføregrunnlag>.slåSammenPeriodeOgUføregrad(): List<Pair<Periode, Uføregrad>> {
            return this.sortedBy { it.periode.fraOgMed }
                .fold(mutableListOf<MutableList<Uføregrunnlag>>()) { acc, uføregrunnlag ->
                    if (acc.isEmpty()) {
                        acc.add(mutableListOf(uføregrunnlag))
                    } else if (acc.last().sisteUføregrunnlagErLikOgTilstøtende(uføregrunnlag)) {
                        acc.last().add(uføregrunnlag)
                    } else {
                        acc.add(mutableListOf(uføregrunnlag))
                    }
                    acc
                }.map {
                    val periode = it.map { it.periode }.minAndMaxOf()

                    periode to it.first().uføregrad
                }
        }

        private fun List<Uføregrunnlag>.sisteUføregrunnlagErLikOgTilstøtende(other: Uføregrunnlag) =
            this.last().tilstøterOgErLik(other)
    }
}
