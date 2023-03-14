package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import java.time.LocalDate

sealed class LovligOppholdVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.LovligOpphold
    abstract val grunnlag: List<LovligOppholdGrunnlag>

    abstract override fun lagTidslinje(periode: Periode): LovligOppholdVilkår
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): LovligOppholdVilkår
    abstract override fun slåSammenLikePerioder(): LovligOppholdVilkår

    object IkkeVurdert : LovligOppholdVilkår() {
        override val vurdering: Vurdering = Vurdering.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val grunnlag = emptyList<LovligOppholdGrunnlag>()
        override val perioder: List<Periode> = emptyList()

        override fun lagTidslinje(periode: Periode): LovligOppholdVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): LovligOppholdVilkår {
            return this
        }

        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeLovligOpphold>,
    ) : LovligOppholdVilkår() {

        override val grunnlag: List<LovligOppholdGrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }
        override fun lagTidslinje(periode: Periode): LovligOppholdVilkår =
            copy(vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList())

        override val erInnvilget: Boolean = vurderingsperioder.all { it.vurdering == Vurdering.Innvilget }

        override val erAvslag: Boolean = vurderingsperioder.any { it.vurdering == Vurdering.Avslag }

        override val vurdering: Vurdering =
            if (erInnvilget) Vurdering.Innvilget else if (erAvslag) Vurdering.Avslag else Vurdering.Uavklart

        override val perioder: Nel<Periode> = vurderingsperioder.minsteAntallSammenhengendePerioder()

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
        }

        override fun hentTidligesteDatoForAvslag(): LocalDate? {
            return vurderingsperioder
                .filter { it.vurdering == Vurdering.Avslag }
                .map { it.periode.fraOgMed }
                .minByOrNull { it }
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        fun minsteAntallSammenhengendePerioder(): List<Periode> {
            return vurderingsperioder.map { it.periode }.minsteAntallSammenhengendePerioder()
        }

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeLovligOpphold>,
            ): Either<KunneIkkeLageLovligOppholdVilkår.OverlappendeVurderingsperioder, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return KunneIkkeLageLovligOppholdVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }

            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeLovligOpphold>,
            ) = tryCreate(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): LovligOppholdVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = nonEmptyListOf(
                    vurderingsperioder.first().oppdaterStønadsperiode(stønadsperiode),
                ),
            )
        }

        override fun slåSammenLikePerioder(): LovligOppholdVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }
    }
}

sealed interface KunneIkkeLageLovligOppholdVilkår {
    sealed interface Vurderingsperiode : KunneIkkeLageLovligOppholdVilkår {
        object PeriodeForGrunnlagOgVurderingErForskjellig : Vurderingsperiode
    }

    object OverlappendeVurderingsperioder : KunneIkkeLageLovligOppholdVilkår
}
