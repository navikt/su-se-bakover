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

// //TODO ENKELT Å FÅ TAK I HVER ENKELT TYPE
// data class Vilkårsvurderinger(
//    private val value: Set<Vilkårsvurdering<*>>,
// ) : Set<Vilkårsvurdering<*>> by value {
//
//    //TODO FLAT UT FOR HVER TYPE GRUNNAG
//
//
//    fun hentResultat(): Vilkårsvurderingsresultat {
//        return when {
//            erInnvilget() -> Vilkårsvurderingsresultat.Innvilget(value)
//            erAvslag() -> Vilkårsvurderingsresultat.Avslag(
//                value.filter { it.vurdering.resultat is Resultat.Avslag }
//                    .toSet(),
//            )
//            else -> Vilkårsvurderingsresultat.Uavklart(emptySet()) // TODO fyll inn
//        }
//    }
//
//    private fun erInnvilget(): Boolean {
//        return value.all { it.vurdering.resultat is Resultat.Innvilget }
//    }
//
//    private fun erAvslag(): Boolean {
//        return value.any { it.vurdering.resultat is Resultat.Avslag }
//    }
// }

/**
 * vilkårsvurderinger - inneholder vilkårsvurdering av alle grunnlagstyper
 * vilkårsvurdering - aggregert vilkårsvurdering for en enkelt type grunnlag inneholder flere vurderingsperioder (en periode per grunnlag)
 * vurderingsperiode - inneholder vilkårsvurdering for ett enkelt grunnlag (kan være manuell (kan vurderes uten grunnlag) eller automatisk (har alltid grunnlag))
 * grunnlag - informasjon for en spesifikk periode som forteller noe om oppfyllelsen av et vilkår
 */

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
    abstract val grunnlag: List<T>
    abstract val vurdering: List<Vurderingsperiode<T>>

    data class Uførhet private constructor(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val grunnlag: List<Grunnlag.Uføregrunnlag>,
        override val vurdering: List<Vurderingsperiode<Grunnlag.Uføregrunnlag>>,
    ) : Vilkårsvurdering<Grunnlag.Uføregrunnlag>() {
        override val vilkår = Inngangsvilkår.Uførhet

        companion object {
            fun manuell(
                resultat: Resultat,
                begrunnelse: String,
                grunnlag: List<Grunnlag.Uføregrunnlag>,
            ) = Uførhet(
                grunnlag = grunnlag,
                vurdering = grunnlag.map {
                    Vurderingsperiode.Manuell(
                        resultat = resultat,
                        grunnlag = it,
                        begrunnelse = begrunnelse,
                    )
                }.ifEmpty {
                    listOf(
                        Vurderingsperiode.Manuell<Grunnlag.Uføregrunnlag>(
                            resultat = resultat,
                            grunnlag = null,
                            begrunnelse = begrunnelse,
                        ),
                    )
                },
            )
        }
    }

//    data class Flyktning(
//        override val id: UUID = UUID.randomUUID(),
//        override val opprettet: Tidspunkt = Tidspunkt.now(),
//        override val vurdering: Vurdering,
//        override val grunnlag: Grunnlag.Flyktninggrunnlag,
//    ) : Vilkårsvurdering<Grunnlag.Flyktninggrunnlag>() {
//        override val vilkår = Inngangsvilkår.Flyktning
//    }
}

sealed class Vurderingsperiode<T : Grunnlag> {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val resultat: Resultat
    abstract val grunnlag: T?

    data class Manuell<T : Grunnlag>(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val resultat: Resultat,
        override val grunnlag: T?,
        val begrunnelse: String,
    ) : Vurderingsperiode<T>()
}

sealed class Resultat {
    object Avslag : Resultat()
    object Innvilget : Resultat()
}
