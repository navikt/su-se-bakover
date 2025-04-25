package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.IkkeVurdertVilkår
import vilkår.common.domain.Inngangsvilkår
import vilkår.common.domain.Vilkår
import vilkår.common.domain.Vurdering
import vilkår.common.domain.VurdertVilkår
import vilkår.common.domain.erLik
import vilkår.common.domain.grunnlag.Grunnlag
import vilkår.common.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.common.domain.kronologisk
import vilkår.common.domain.slåSammenLikePerioder

sealed interface InstitusjonsoppholdVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.Institusjonsopphold
    override val grunnlag: List<Grunnlag> get() = emptyList()

    abstract override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): InstitusjonsoppholdVilkår
    abstract override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår

    data object IkkeVurdert : InstitusjonsoppholdVilkår, IkkeVurdertVilkår {
        override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold>,
    ) : InstitusjonsoppholdVilkår,
        VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }

        override val avslagsgrunner: List<Avslagsgrunn> = when (vurdering) {
            Vurdering.Innvilget -> emptyList()
            Vurdering.Uavklart -> emptyList()
            Vurdering.Avslag -> listOf(Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON)
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): InstitusjonsoppholdVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        override fun copyWithNewId(): Vurdert =
            this.copy(vurderingsperioder = vurderingsperioder.map { it.copyWithNewId() })

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold>,
            ): Either<UgyldigInstitisjonsoppholdVilkår, Vurdert> {
                return vurderingsperioder.kronologisk().map {
                    Vurdert(it)
                }.mapLeft {
                    UgyldigInstitisjonsoppholdVilkår.OverlappendeVurderingsperioder
                }
            }

            fun create(
                vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold>,
            ): Vurdert {
                return tryCreate(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }
            }
        }

        sealed interface UgyldigInstitisjonsoppholdVilkår {
            data object OverlappendeVurderingsperioder : UgyldigInstitisjonsoppholdVilkår
        }
    }
}
