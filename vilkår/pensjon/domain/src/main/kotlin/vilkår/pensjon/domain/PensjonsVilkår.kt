package vilkår.pensjon.domain

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.IkkeVurdertVilkår
import vilkår.common.domain.Inngangsvilkår
import vilkår.common.domain.Vilkår
import vilkår.common.domain.Vurdering
import vilkår.common.domain.Vurderingsperiode
import vilkår.common.domain.VurdertVilkår
import vilkår.common.domain.erLik
import vilkår.common.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.common.domain.kronologisk
import vilkår.common.domain.slåSammenLikePerioder

sealed interface PensjonsVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.Pensjon
    override val grunnlag: List<Pensjonsgrunnlag>

    abstract override fun lagTidslinje(periode: Periode): PensjonsVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PensjonsVilkår
    abstract override fun slåSammenLikePerioder(): PensjonsVilkår

    data object IkkeVurdert : PensjonsVilkår, IkkeVurdertVilkår {
        override val grunnlag = emptyList<Pensjonsgrunnlag>()
        override fun lagTidslinje(periode: Periode): PensjonsVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): PensjonsVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodePensjon>,
    ) : PensjonsVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<Pensjonsgrunnlag> = vurderingsperioder.map { it.grunnlag }

        override fun lagTidslinje(periode: Periode): PensjonsVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }

        override val avslagsgrunner: List<Avslagsgrunn> = when (vurdering) {
            Vurdering.Innvilget -> emptyList()
            Vurdering.Uavklart -> emptyList()
            Vurdering.Avslag -> listOfNotNull(
                this.grunnlag.find { it.pensjonsopplysninger.søktUtenlandskePensjoner.resultat() == Vurdering.Avslag }?.let { Avslagsgrunn.MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER },
                this.grunnlag.find { it.pensjonsopplysninger.søktPensjonFolketrygd.resultat() == Vurdering.Avslag }?.let { Avslagsgrunn.MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN },
                this.grunnlag.find { it.pensjonsopplysninger.søktAndreNorskePensjoner.resultat() == Vurdering.Avslag }?.let { Avslagsgrunn.MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER },
            )
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PensjonsVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): PensjonsVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        override fun copyWithNewId(): Vurdert =
            this.copy(vurderingsperioder = vurderingsperioder.map { it.copyWithNewId() })

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodePensjon>,
            ): Either<KunneIkkeLagePensjonsVilkår, Vurdert> {
                return vurderingsperioder.kronologisk().map {
                    Vurdert(it)
                }.mapLeft {
                    KunneIkkeLagePensjonsVilkår.OverlappendeVurderingsperioder
                }
            }

            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodePensjon>,
            ): Vurdert =
                tryCreateFromVurderingsperioder(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }

            fun tryCreateFromVurderingsperioder(
                vurderingsperioder: Nel<VurderingsperiodePensjon>,
            ): Either<KunneIkkeLagePensjonsVilkår, Vurdert> {
                return vurderingsperioder.kronologisk().map {
                    Vurdert(it)
                }.mapLeft {
                    KunneIkkeLagePensjonsVilkår.OverlappendeVurderingsperioder
                }
            }
        }

        sealed interface UgyldigPensjonsVilkår {
            data object OverlappendeVurderingsperioder : UgyldigPensjonsVilkår
        }
    }
}

sealed interface KunneIkkeLagePensjonsVilkår {
    sealed interface Vurderingsperiode : KunneIkkeLagePensjonsVilkår {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : Vurderingsperiode
    }

    data object OverlappendeVurderingsperioder : KunneIkkeLagePensjonsVilkår
}
