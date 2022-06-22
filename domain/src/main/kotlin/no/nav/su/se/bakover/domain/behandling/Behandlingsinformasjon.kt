package no.nav.su.se.bakover.domain.behandling

import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.FlyktningGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFastOppholdINorge
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFlyktning
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePersonligOppmøte
import java.time.Clock
import java.util.UUID

data class Behandlingsinformasjon(
    val flyktning: Flyktning? = null,
    val fastOppholdINorge: FastOppholdINorge? = null,
    val institusjonsopphold: Institusjonsopphold? = null,
    val personligOppmøte: PersonligOppmøte? = null,
) {
    @JsonIgnore
    val vilkår = listOf(
        flyktning,
        fastOppholdINorge,
        institusjonsopphold,
        personligOppmøte,
    )

    fun patch(
        b: Behandlingsinformasjon,
    ) = Behandlingsinformasjon(
        flyktning = b.flyktning ?: this.flyktning,
        fastOppholdINorge = b.fastOppholdINorge ?: this.fastOppholdINorge,
        institusjonsopphold = b.institusjonsopphold ?: this.institusjonsopphold,
        personligOppmøte = b.personligOppmøte ?: this.personligOppmøte,
    )

    abstract class Base {
        abstract fun erVilkårOppfylt(): Boolean
        abstract fun erVilkårIkkeOppfylt(): Boolean
    }

    data class Flyktning(
        val status: Status,
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        fun tilVilkår(
            stønadsperiode: Stønadsperiode,
            clock: Clock,
        ): FlyktningVilkår {
            return when (status) {
                Status.VilkårOppfylt,
                Status.VilkårIkkeOppfylt,
                -> {
                    FlyktningVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodeFlyktning.tryCreate(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(clock),
                                resultat = when (erVilkårOppfylt()) {
                                    true -> Resultat.Innvilget
                                    false -> Resultat.Avslag
                                },
                                grunnlag = FlyktningGrunnlag.tryCreate(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(clock),
                                    periode = stønadsperiode.periode,
                                ).getOrHandle {
                                    throw IllegalArgumentException("Kunne ikke instansiere ${FlyktningGrunnlag::class.simpleName}. Melding: $it")
                                },
                                vurderingsperiode = stønadsperiode.periode,
                            ).getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${VurderingsperiodeFlyktning::class.simpleName}. Melding: $it")
                            },
                        ),
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${FlyktningVilkår.Vurdert::class.simpleName}. Melding: $it")
                    }
                }
                Status.Uavklart -> FlyktningVilkår.IkkeVurdert
            }
        }
    }

    data class FastOppholdINorge(
        val status: Status,
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        fun tilVilkår(
            stønadsperiode: Stønadsperiode,
            clock: Clock,
        ): FastOppholdINorgeVilkår {
            return when (status) {
                Status.VilkårOppfylt,
                Status.VilkårIkkeOppfylt,
                -> {
                    FastOppholdINorgeVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodeFastOppholdINorge.tryCreate(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(clock),
                                resultat = when (erVilkårOppfylt()) {
                                    true -> Resultat.Innvilget
                                    false -> Resultat.Avslag
                                },
                                grunnlag = FastOppholdINorgeGrunnlag.tryCreate(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(clock),
                                    periode = stønadsperiode.periode,
                                ).getOrHandle {
                                    throw IllegalArgumentException("Kunne ikke instansiere ${FastOppholdINorgeGrunnlag::class.simpleName}. Melding: $it")
                                },
                                vurderingsperiode = stønadsperiode.periode,
                            ).getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${VurderingsperiodeFastOppholdINorge::class.simpleName}. Melding: $it")
                            },
                        ),
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${FastOppholdINorgeVilkår.Vurdert::class.simpleName}. Melding: $it")
                    }
                }
                Status.Uavklart -> FastOppholdINorgeVilkår.IkkeVurdert
            }
        }
    }

    data class Institusjonsopphold(
        val status: Status,
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart,
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        fun tilVilkår(
            stønadsperiode: Stønadsperiode,
            clock: Clock,
        ): InstitusjonsoppholdVilkår {
            return when (status) {
                Status.VilkårOppfylt,
                Status.VilkårIkkeOppfylt,
                -> {
                    InstitusjonsoppholdVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodeInstitusjonsopphold.tryCreate(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(clock),
                                resultat = when (erVilkårOppfylt()) {
                                    true -> Resultat.Innvilget
                                    false -> Resultat.Avslag
                                },
                                grunnlag = InstitusjonsoppholdGrunnlag.tryCreate(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(clock),
                                    periode = stønadsperiode.periode,
                                ).getOrHandle {
                                    throw IllegalArgumentException("Kunne ikke instansiere ${InstitusjonsoppholdGrunnlag::class.simpleName}. Melding: $it")
                                },
                                vurderingsperiode = stønadsperiode.periode,
                            ).getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${VurderingsperiodeInstitusjonsopphold::class.simpleName}. Melding: $it")
                            },
                        ),
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${InstitusjonsoppholdVilkår.Vurdert::class.simpleName}. Melding: $it")
                    }
                }
                Status.Uavklart -> InstitusjonsoppholdVilkår.IkkeVurdert
            }
        }
    }

    data class PersonligOppmøte(
        val status: Status,
    ) : Base() {
        enum class Status {
            MøttPersonlig,
            IkkeMøttMenVerge,
            IkkeMøttMenSykMedLegeerklæringOgFullmakt,
            IkkeMøttMenKortvarigSykMedLegeerklæring,
            IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
            IkkeMøttPersonlig,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean =
            status.let {
                it == Status.MøttPersonlig ||
                    it == Status.IkkeMøttMenVerge ||
                    it == Status.IkkeMøttMenSykMedLegeerklæringOgFullmakt ||
                    it == Status.IkkeMøttMenKortvarigSykMedLegeerklæring ||
                    it == Status.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt
            }

        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.IkkeMøttPersonlig

        fun tilVilkår(
            stønadsperiode: Stønadsperiode,
            clock: Clock,
        ): PersonligOppmøteVilkår {
            return when (status) {
                Status.MøttPersonlig,
                Status.IkkeMøttMenVerge,
                Status.IkkeMøttMenSykMedLegeerklæringOgFullmakt,
                Status.IkkeMøttMenKortvarigSykMedLegeerklæring,
                Status.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
                Status.IkkeMøttPersonlig,
                -> {
                    PersonligOppmøteVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodePersonligOppmøte.tryCreate(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(clock),
                                resultat = when (erVilkårOppfylt()) {
                                    true -> Resultat.Innvilget
                                    false -> Resultat.Avslag
                                },
                                grunnlag = PersonligOppmøteGrunnlag.tryCreate(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(clock),
                                    periode = stønadsperiode.periode,
                                ).getOrHandle {
                                    throw IllegalArgumentException("Kunne ikke instansiere ${PersonligOppmøteGrunnlag::class.simpleName}. Melding: $it")
                                },
                                vurderingsperiode = stønadsperiode.periode,
                            ).getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${VurderingsperiodePersonligOppmøte::class.simpleName}. Melding: $it")
                            },
                        ),
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${PersonligOppmøteVilkår.Vurdert::class.simpleName}. Melding: $it")
                    }
                }
                Status.Uavklart -> PersonligOppmøteVilkår.IkkeVurdert
            }
        }
    }

    companion object {
        fun lagTomBehandlingsinformasjon() = Behandlingsinformasjon(
            flyktning = null,
            fastOppholdINorge = null,
            institusjonsopphold = null,
            personligOppmøte = null,
        )
    }
}
