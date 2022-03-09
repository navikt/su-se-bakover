package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.overlappende
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import java.time.LocalDate
import java.util.UUID

sealed class FastOppholdINorgeVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.FastOppholdINorge
    abstract val grunnlag: List<FastOppholdINorgeGrunnlag>

    abstract override fun lagTidslinje(periode: Periode): FastOppholdINorgeVilkår
    abstract override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FastOppholdINorgeVilkår

    object IkkeVurdert : FastOppholdINorgeVilkår() {
        override val resultat: Resultat = Resultat.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val grunnlag = emptyList<FastOppholdINorgeGrunnlag>()
        override fun lagTidslinje(periode: Periode): FastOppholdINorgeVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeFastOppholdINorge>,
    ) : FastOppholdINorgeVilkår() {
        override val grunnlag: List<FastOppholdINorgeGrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }
        override fun lagTidslinje(periode: Periode): FastOppholdINorgeVilkår {
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

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeFastOppholdINorge>,
            ): Either<UgyldigFastOppholdINorgeVikår, Vurdert> {
                if (vurderingsperioder.overlappende()) {
                    return UgyldigFastOppholdINorgeVikår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder).right()
            }
        }

        sealed class UgyldigFastOppholdINorgeVikår {
            object OverlappendeVurderingsperioder : UgyldigFastOppholdINorgeVikår()
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FastOppholdINorgeVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn èn vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }
    }
}

data class VurderingsperiodeFastOppholdINorge private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val grunnlag: FastOppholdINorgeGrunnlag?,
    override val resultat: Resultat,
    override val periode: Periode,
    val begrunnelse: String?,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodeFastOppholdINorge> {

    override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeFastOppholdINorge {
        return create(
            id = id,
            opprettet = opprettet,
            resultat = resultat,
            periode = stønadsperiode.periode,
            grunnlag = this.grunnlag?.oppdaterPeriode(stønadsperiode.periode),
            begrunnelse = begrunnelse,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeFastOppholdINorge = when (args) {
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
        return other is VurderingsperiodeFastOppholdINorge &&
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
            grunnlag: FastOppholdINorgeGrunnlag?,
            periode: Periode,
            begrunnelse: String?,
        ): VurderingsperiodeFastOppholdINorge {
            return tryCreate(id, opprettet, resultat, grunnlag, periode, begrunnelse).getOrHandle {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            resultat: Resultat,
            grunnlag: FastOppholdINorgeGrunnlag?,
            vurderingsperiode: Periode,
            begrunnelse: String?,
        ): Either<UgyldigVurderingsperiode, VurderingsperiodeFastOppholdINorge> {

            grunnlag?.let {
                if (vurderingsperiode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodeFastOppholdINorge(
                id = id,
                opprettet = opprettet,
                resultat = resultat,
                grunnlag = grunnlag,
                periode = vurderingsperiode,
                begrunnelse = begrunnelse,
            ).right()
        }
    }

    sealed class UgyldigVurderingsperiode {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode()
    }
}
