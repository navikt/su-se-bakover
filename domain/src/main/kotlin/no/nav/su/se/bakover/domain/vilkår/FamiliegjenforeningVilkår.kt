package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import java.time.LocalDate

sealed class FamiliegjenforeningVilkår : Vilkår() {
    override val vilkår: Inngangsvilkår = Inngangsvilkår.Familiegjenforening

    abstract override fun lagTidslinje(periode: Periode): FamiliegjenforeningVilkår
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FamiliegjenforeningVilkår

    object IkkeVurdert : FamiliegjenforeningVilkår() {
        override val vurdering: Vurdering = Vurdering.Uavklart
        override val erAvslag: Boolean = false
        override val erInnvilget: Boolean = false
        override val perioder: List<Periode> = emptyList()

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode) = this
        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår) = other is IkkeVurdert
        override fun lagTidslinje(periode: Periode) = this
        override fun slåSammenLikePerioder() = this
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeFamiliegjenforening>,
    ) : FamiliegjenforeningVilkår() {
        override val erInnvilget = vurderingsperioder.all { it.vurdering == Vurdering.Innvilget }
        override val erAvslag = vurderingsperioder.any { it.vurdering == Vurdering.Avslag }
        override val vurdering =
            if (erInnvilget) Vurdering.Innvilget else if (erAvslag) Vurdering.Avslag else Vurdering.Uavklart

        override val perioder: Nel<Periode> = vurderingsperioder.minsteAntallSammenhengendePerioder()
        override fun erLik(other: Vilkår) = other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        override fun slåSammenLikePerioder() = copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())

        override fun hentTidligesteDatoForAvslag() = vurderingsperioder
            .filter { it.vurdering == Vurdering.Avslag }
            .map { it.periode.fraOgMed }
            .minByOrNull { it }

        override fun lagTidslinje(periode: Periode) = copy(
            vurderingsperioder = Tidslinje(
                periode = periode,
                objekter = vurderingsperioder,
            ).tidslinje.toNonEmptyList(),
        )

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FamiliegjenforeningVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med mer enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map { it.oppdaterStønadsperiode(stønadsperiode) },
            )
        }

        companion object {
            fun create(
                vurderingsperioder: Nel<VurderingsperiodeFamiliegjenforening>,
            ) =
                if (vurderingsperioder.harOverlappende()) {
                    UgyldigFamiliegjenforeningVilkår.OverlappendeVurderingsperioder.left()
                } else {
                    Vurdert(vurderingsperioder).right()
                }

            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeFamiliegjenforening>,
            ) = create(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }
        }
    }
}

sealed interface UgyldigFamiliegjenforeningVilkår {
    object OverlappendeVurderingsperioder : UgyldigFamiliegjenforeningVilkår
}
