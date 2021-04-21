package no.nav.su.se.bakover.domain.vilkår

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

/**
Et inngangsvilkår er de vilkårene som kan føre til avslag før det beregnes?

Her har vi utelatt for høy inntekt (SU<0) og su under minstegrense (SU<2%)
 */
sealed class Inngangsvilkår {
    object Uførhet : Inngangsvilkår()
    object Flyktning : Inngangsvilkår()
    object Oppholdstillatelse : Inngangsvilkår()
    object PersonligOppmøte : Inngangsvilkår()
    object Formue : Inngangsvilkår()
    object BorOgOppholderSegINorge : Inngangsvilkår()
    object utenlandsoppholdOver90Dager : Inngangsvilkår()
    object innlagtPåInstitusjon : Inngangsvilkår()
}

data class Paragraf(
    val value: String,
    val lovverkVersjon: String,
)

data class Vilkårsvurderinger(private val value: Set<Vilkårsvurdering<*>>) : Set<Vilkårsvurdering<*>> by value {
    fun hentResultat(): Vilkårsvurderingsresultat {
        return when {
            erInnvilget() -> Vilkårsvurderingsresultat.Innvilget(value)
            erAvslag() -> Vilkårsvurderingsresultat.Avslag(
                value.filter { it.vurdering.resultat is Resultat.Avslag }
                    .toSet(),
            )
            else -> Vilkårsvurderingsresultat.Uavklart(emptySet()) // TODO fyll inn
        }
    }

    private fun erInnvilget(): Boolean {
        return value.all { it.vurdering.resultat is Resultat.Innvilget }
    }

    private fun erAvslag(): Boolean {
        return value.any { it.vurdering.resultat is Resultat.Avslag }
    }
}

sealed class Vilkårsvurderingsresultat {
    data class Avslag(
        val vilkår: Set<Vilkårsvurdering<*>>,
    ) : Vilkårsvurderingsresultat()

    data class Innvilget(
        val vilkår: Set<Vilkårsvurdering<*>>,
    ) : Vilkårsvurderingsresultat()

    data class Uavklart(
        val vilkår: Set<Inngangsvilkår>,
    ) : Vilkårsvurderingsresultat()
}

/**
 * Vurderingen av et vilkår mot en eller flere grunnlagsdata
 */
sealed class Vilkårsvurdering<T : Grunnlag> {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val vilkår: Inngangsvilkår
    abstract val vurdering: Vurdering
    abstract val grunnlag: T?

    data class Uførhet(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val vurdering: Vurdering,
        override val grunnlag: Grunnlag.Uføregrunnlag?,
    ) : Vilkårsvurdering<Grunnlag.Uføregrunnlag>() {
        override val vilkår = Inngangsvilkår.Uførhet
    }

    data class Flyktning(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val vurdering: Vurdering,
        override val grunnlag: Grunnlag.Flyktninggrunnlag,
    ) : Vilkårsvurdering<Grunnlag.Flyktninggrunnlag>() {
        override val vilkår = Inngangsvilkår.Flyktning
    }
}

sealed class Vurdering {
    abstract val resultat: Resultat

    data class Automatisk(
        override val resultat: Resultat,
    ) : Vurdering()

    data class Manuell(
        override val resultat: Resultat,
        val begrunnelse: String,
    ) : Vurdering()
}

sealed class Resultat {
    object Avslag : Resultat()
    object Innvilget : Resultat()
}
