package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Nel
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import java.time.LocalDate

sealed class FamiliegjenforeningVilkår : Vilkår() {
    override val vilkår: Inngangsvilkår = Inngangsvilkår.Familiegjenforening

    object IkkeVurdert : FamiliegjenforeningVilkår() {
        override val resultat: Resultat = Resultat.Uavklart
        override val erAvslag: Boolean = false
        override val erInnvilget: Boolean = false

        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår) = other is IkkeVurdert
        override fun lagTidslinje(periode: Periode) = this
        override fun slåSammenLikePerioder() = this
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeFamiliegjenforening>,
    ) : FamiliegjenforeningVilkår() {
        override val erAvslag = vurderingsperioder.all { it.resultat == Resultat.Innvilget }
        override val erInnvilget = vurderingsperioder.any { it.resultat == Resultat.Avslag }
        override val resultat =
            if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart

        override fun slåSammenLikePerioder() = copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        override fun erLik(other: Vilkår) = other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)

        override fun hentTidligesteDatoForAvslag() = vurderingsperioder
            .filter { it.resultat == Resultat.Avslag }
            .map { it.periode.fraOgMed }
            .minByOrNull { it }

        override fun lagTidslinje(periode: Periode) = copy(
            vurderingsperioder = Nel.fromListUnsafe(
                Tidslinje(
                    periode = periode,
                    objekter = vurderingsperioder,
                ).tidslinje,
            ),
        )
    }
}

