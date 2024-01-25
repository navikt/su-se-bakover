package no.nav.su.se.bakover.domain.vilkår

import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import vilkår.domain.Inngangsvilkår
import vilkår.domain.Vilkår
import vilkår.domain.Vurdering
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.uføre.domain.UføreVilkår

/**
 * vilkårsvurderinger - inneholder vilkårsvurdering av alle grunnlagstyper
 * vilkårsvurdering - aggregert vilkårsvurdering for en enkelt type grunnlag inneholder flere vurderingsperioder (en periode per grunnlag)
 * vurderingsperiode - inneholder vilkårsvurdering for ett enkelt grunnlag (kan være manuell (kan vurderes uten grunnlag) eller automatisk (har alltid grunnlag))
 * grunnlag - informasjon for en spesifikk periode som forteller noe om oppfyllelsen av et vilkår
 */
sealed interface Vilkårsvurderingsresultat {
    data class Avslag(
        val vilkår: Set<Vilkår>,
    ) : Vilkårsvurderingsresultat {
        val avslagsgrunner: List<Avslagsgrunn> = vilkår.flatMap { it.avslagsgrunner() }
        val tidligsteDatoForAvslag = vilkår.minOf { it.hentTidligesteDatoForAvslag()!! }

        private fun Vilkår.avslagsgrunner(): List<Avslagsgrunn> {
            return when (this) {
                is FastOppholdINorgeVilkår -> {
                    listOf(Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE)
                }
                is FlyktningVilkår -> {
                    listOf(Avslagsgrunn.FLYKTNING)
                }
                is FormueVilkår -> {
                    listOf(Avslagsgrunn.FORMUE)
                }
                is InstitusjonsoppholdVilkår -> {
                    listOf(Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON)
                }
                is LovligOppholdVilkår -> {
                    listOf(Avslagsgrunn.OPPHOLDSTILLATELSE)
                }
                is OpplysningspliktVilkår -> {
                    listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON)
                }
                is PersonligOppmøteVilkår -> {
                    listOf(Avslagsgrunn.PERSONLIG_OPPMØTE)
                }
                is UføreVilkår -> {
                    listOf(Avslagsgrunn.UFØRHET)
                }
                is UtenlandsoppholdVilkår -> {
                    listOf(Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER)
                }
                is FamiliegjenforeningVilkår -> {
                    listOf(Avslagsgrunn.FAMILIEGJENFORENING)
                }
                is PensjonsVilkår -> {
                    listOfNotNull(
                        this.grunnlag.find { it.pensjonsopplysninger.søktUtenlandskePensjoner.resultat() == Vurdering.Avslag }?.let { Avslagsgrunn.MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER },
                        this.grunnlag.find { it.pensjonsopplysninger.søktPensjonFolketrygd.resultat() == Vurdering.Avslag }?.let { Avslagsgrunn.MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN },
                        this.grunnlag.find { it.pensjonsopplysninger.søktAndreNorskePensjoner.resultat() == Vurdering.Avslag }?.let { Avslagsgrunn.MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER },
                    )
                }

                else -> throw IllegalStateException("Ukjent vilkår: $this ved mapping til avslagsgrunn")
            }
        }

        fun erNøyaktigÅrsak(inngangsvilkår: Inngangsvilkår): Boolean {
            return vilkår.singleOrNull { it.vilkår == inngangsvilkår }?.let { true }
                ?: if (vilkår.size == 1) false else throw IllegalStateException("Opphør av flere vilkår er ikke støttet, opphørte vilkår:$vilkår")
        }
    }

    data class Innvilget(
        val vilkår: Set<Vilkår>,
    ) : Vilkårsvurderingsresultat

    data class Uavklart(
        val vilkår: Set<Vilkår>,
    ) : Vilkårsvurderingsresultat
}
