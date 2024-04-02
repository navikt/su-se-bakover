package vilkår.fastopphold.domain

import arrow.core.Either
import arrow.core.Nel
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
import vilkår.common.domain.VurdertVilkår
import vilkår.common.domain.erLik
import vilkår.common.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.common.domain.kronologisk
import vilkår.common.domain.slåSammenLikePerioder

sealed interface FastOppholdINorgeVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.FastOppholdINorge
    override val grunnlag: List<FastOppholdINorgeGrunnlag>

    abstract override fun lagTidslinje(periode: Periode): FastOppholdINorgeVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FastOppholdINorgeVilkår
    abstract override fun slåSammenLikePerioder(): FastOppholdINorgeVilkår

    data object IkkeVurdert : FastOppholdINorgeVilkår, IkkeVurdertVilkår {
        override val grunnlag = emptyList<FastOppholdINorgeGrunnlag>()
        override fun lagTidslinje(periode: Periode): FastOppholdINorgeVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): FastOppholdINorgeVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeFastOppholdINorge>,
    ) : FastOppholdINorgeVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<FastOppholdINorgeGrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }

        override fun lagTidslinje(periode: Periode): FastOppholdINorgeVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }

        override val avslagsgrunner: List<Avslagsgrunn> = when (vurdering) {
            Vurdering.Innvilget -> emptyList()
            Vurdering.Uavklart -> emptyList()
            Vurdering.Avslag -> listOf(Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE)
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FastOppholdINorgeVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): FastOppholdINorgeVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        override fun copyWithNewId(): Vurdert = this.copy(
            vurderingsperioder = vurderingsperioder.map { it.copyWithNewId() },
        )

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeFastOppholdINorge>,
            ): Either<UgyldigFastOppholdINorgeVikår, Vurdert> {
                return vurderingsperioder.kronologisk().map {
                    Vurdert(it)
                }.mapLeft {
                    UgyldigFastOppholdINorgeVikår.OverlappendeVurderingsperioder
                }
            }
        }

        sealed interface UgyldigFastOppholdINorgeVikår {
            data object OverlappendeVurderingsperioder : UgyldigFastOppholdINorgeVikår
        }
    }
}
