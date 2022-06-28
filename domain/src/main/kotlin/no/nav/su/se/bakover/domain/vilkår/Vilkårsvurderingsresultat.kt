package no.nav.su.se.bakover.domain.vilkår

import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn

/**
 * vilkårsvurderinger - inneholder vilkårsvurdering av alle grunnlagstyper
 * vilkårsvurdering - aggregert vilkårsvurdering for en enkelt type grunnlag inneholder flere vurderingsperioder (en periode per grunnlag)
 * vurderingsperiode - inneholder vilkårsvurdering for ett enkelt grunnlag (kan være manuell (kan vurderes uten grunnlag) eller automatisk (har alltid grunnlag))
 * grunnlag - informasjon for en spesifikk periode som forteller noe om oppfyllelsen av et vilkår
 */
sealed class Vilkårsvurderingsresultat {
    data class Avslag(
        val vilkår: Set<Vilkår>,
    ) : Vilkårsvurderingsresultat() {
        val avslagsgrunner = vilkår.map { it.avslagsgrunn() }
        val tidligsteDatoForAvslag = vilkår.minOf { it.hentTidligesteDatoForAvslag()!! }

        private fun Vilkår.avslagsgrunn(): Avslagsgrunn {
            return when (this) {
                is FastOppholdINorgeVilkår -> {
                    Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
                }
                is FlyktningVilkår -> {
                    Avslagsgrunn.FLYKTNING
                }
                is FormueVilkår -> {
                    Avslagsgrunn.FORMUE
                }
                is InstitusjonsoppholdVilkår -> {
                    Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON
                }
                is LovligOppholdVilkår -> {
                    Avslagsgrunn.OPPHOLDSTILLATELSE
                }
                is OpplysningspliktVilkår -> {
                    Avslagsgrunn.MANGLENDE_DOKUMENTASJON
                }
                is PersonligOppmøteVilkår -> {
                    Avslagsgrunn.PERSONLIG_OPPMØTE
                }
                is UføreVilkår -> {
                    Avslagsgrunn.UFØRHET
                }
                is UtenlandsoppholdVilkår -> {
                    Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
                }
                is FamiliegjenforeningVilkår -> {
                    Avslagsgrunn.FAMILIEGJENFORENING
                }
                is PensjonsVilkår -> {
                    Avslagsgrunn.PENSJON
                }
            }
        }

        fun erNøyaktigÅrsak(inngangsvilkår: Inngangsvilkår): Boolean {
            return vilkår.singleOrNull { it.vilkår == inngangsvilkår }?.let { true }
                ?: if (vilkår.size == 1) false else throw IllegalStateException("Opphør av flere vilkår er ikke støttet, opphørte vilkår:$vilkår")
        }
    }

    data class Innvilget(
        val vilkår: Set<Vilkår>,
    ) : Vilkårsvurderingsresultat()

    data class Uavklart(
        val vilkår: Set<Vilkår>,
    ) : Vilkårsvurderingsresultat()
}
