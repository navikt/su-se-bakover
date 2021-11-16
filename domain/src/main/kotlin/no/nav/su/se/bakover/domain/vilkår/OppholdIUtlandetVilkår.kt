package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.periode.overlappende
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.OppholdIUtlandetGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOppholdIUtlandet.Companion.slåSammenVurderingsperioder
import java.time.LocalDate
import java.util.UUID

sealed class OppholdIUtlandetVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.OppholdIUtlandet
    abstract val grunnlag: List<OppholdIUtlandetGrunnlag>

    abstract override fun lagTidslinje(periode: Periode): OppholdIUtlandetVilkår
    abstract override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): OppholdIUtlandetVilkår

    object IkkeVurdert : OppholdIUtlandetVilkår() {
        override val resultat: Resultat = Resultat.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val grunnlag = emptyList<OppholdIUtlandetGrunnlag>()
        override fun lagTidslinje(periode: Periode): OppholdIUtlandetVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeOppholdIUtlandet>,
    ) : OppholdIUtlandetVilkår() {

        override val grunnlag: List<OppholdIUtlandetGrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }
        override fun lagTidslinje(periode: Periode): OppholdIUtlandetVilkår {
            return copy(
                vurderingsperioder = Nel.fromListUnsafe(
                    Tidslinje(
                        periode = periode,
                        objekter = vurderingsperioder,
                    ).tidslinje,
                ),
            )
        }

        override val erInnvilget: Boolean = vurderingsperioder.all { it.resultat == Resultat.Innvilget }

        override val erAvslag: Boolean = vurderingsperioder.any { it.resultat == Resultat.Avslag }

        override val resultat: Resultat =
            if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart

        override fun hentTidligesteDatoForAvslag(): LocalDate? {
            return vurderingsperioder
                .filter { it.resultat == Resultat.Avslag }
                .map { it.periode.fraOgMed }
                .minByOrNull { it }
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        fun slåSammenVurderingsperioder(): Either<UgyldigOppholdIUtlandetVilkår, OppholdIUtlandetVilkår> {
            return tryCreateFromVurderingsperioder(vurderingsperioder = vurderingsperioder.slåSammenVurderingsperioder())
        }

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeOppholdIUtlandet>,
            ): Either<UgyldigOppholdIUtlandetVilkår, Vurdert> {
                if (vurderingsperioder.overlappende()) {
                    return UgyldigOppholdIUtlandetVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder).right()
            }

            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeOppholdIUtlandet>,
            ): Vurdert =
                tryCreateFromVurderingsperioder(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }

            fun tryCreateFromVurderingsperioder(
                vurderingsperioder: Nel<VurderingsperiodeOppholdIUtlandet>,
            ): Either<UgyldigOppholdIUtlandetVilkår, Vurdert> {
                if (vurderingsperioder.overlappende()) {
                    return UgyldigOppholdIUtlandetVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder).right()
            }
        }

        sealed class UgyldigOppholdIUtlandetVilkår {
            object OverlappendeVurderingsperioder : UgyldigOppholdIUtlandetVilkår()
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): OppholdIUtlandetVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn èn vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }
    }
}

data class VurderingsperiodeOppholdIUtlandet private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val resultat: Resultat,
    override val grunnlag: OppholdIUtlandetGrunnlag?,
    override val periode: Periode,
    val begrunnelse: String?,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodeOppholdIUtlandet> {

    override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeOppholdIUtlandet {
        return create(
            id = id,
            opprettet = opprettet,
            resultat = resultat,
            periode = stønadsperiode.periode,
            grunnlag = this.grunnlag?.oppdaterPeriode(stønadsperiode.periode),
            begrunnelse = begrunnelse,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeOppholdIUtlandet = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(
                id = UUID.randomUUID(),
                grunnlag = grunnlag?.copy(args),
            )
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(
                id = UUID.randomUUID(),
                periode = args.periode,
                grunnlag = grunnlag?.copy(args),
            )
        }
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodeOppholdIUtlandet &&
            resultat == other.resultat &&
            when {
                grunnlag != null && other.grunnlag != null -> grunnlag.erLik(other.grunnlag)
                grunnlag == null && other.grunnlag == null -> true
                else -> false
            }
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            resultat: Resultat,
            grunnlag: OppholdIUtlandetGrunnlag?,
            periode: Periode,
            begrunnelse: String?,
        ): VurderingsperiodeOppholdIUtlandet {
            return tryCreate(id, opprettet, resultat, grunnlag, periode, begrunnelse).getOrHandle {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            resultat: Resultat,
            grunnlag: OppholdIUtlandetGrunnlag?,
            vurderingsperiode: Periode,
            begrunnelse: String?,
        ): Either<UgyldigVurderingsperiode, VurderingsperiodeOppholdIUtlandet> {

            grunnlag?.let {
                if (vurderingsperiode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodeOppholdIUtlandet(
                id = id,
                opprettet = opprettet,
                resultat = resultat,
                grunnlag = grunnlag,
                periode = vurderingsperiode,
                begrunnelse = begrunnelse,
            ).right()
        }

        fun Nel<VurderingsperiodeOppholdIUtlandet>.slåSammenVurderingsperioder(): Nel<VurderingsperiodeOppholdIUtlandet> {
            val slåttSammen = this.sortedBy { it.periode.fraOgMed }
                .fold(mutableListOf<MutableList<VurderingsperiodeOppholdIUtlandet>>()) { acc, oppholdIUtlandet ->
                    if (acc.isEmpty()) {
                        acc.add(mutableListOf(oppholdIUtlandet))
                    } else if (acc.last().sistePeriodeErLikOgTilstøtende(oppholdIUtlandet)) {
                        acc.last().add(oppholdIUtlandet)
                    } else {
                        acc.add(mutableListOf(oppholdIUtlandet))
                    }
                    acc
                }.map {
                    val periode = it.map { it.periode }.minAndMaxOf()
                    it.first().copy(CopyArgs.Tidslinje.NyPeriode(periode = periode))
                }
            return NonEmptyList.fromListUnsafe(slåttSammen)
        }

        private fun List<VurderingsperiodeOppholdIUtlandet>.sistePeriodeErLikOgTilstøtende(other: VurderingsperiodeOppholdIUtlandet) =
            this.last().tilstøterOgErLik(other)
    }

    sealed class UgyldigVurderingsperiode {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode()
    }
}
