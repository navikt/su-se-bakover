package no.nav.su.se.bakover.domain.behandling

import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.FlyktningGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFastOppholdINorge
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFlyktning
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeLovligOpphold
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePersonligOppmøte
import java.time.Clock
import java.util.UUID

data class Behandlingsinformasjon(
    val flyktning: Flyktning? = null,
    val lovligOpphold: LovligOpphold? = null,
    val fastOppholdINorge: FastOppholdINorge? = null,
    val institusjonsopphold: Institusjonsopphold? = null,
    val formue: Formue? = null,
    val personligOppmøte: PersonligOppmøte? = null,
) {
    @JsonIgnore
    val vilkår = listOf(
        flyktning,
        lovligOpphold,
        fastOppholdINorge,
        institusjonsopphold,
        formue,
        personligOppmøte,
    )

    fun patch(
        b: Behandlingsinformasjon,
    ) = Behandlingsinformasjon(
        flyktning = b.flyktning ?: this.flyktning,
        lovligOpphold = b.lovligOpphold ?: this.lovligOpphold,
        fastOppholdINorge = b.fastOppholdINorge ?: this.fastOppholdINorge,
        institusjonsopphold = b.institusjonsopphold ?: this.institusjonsopphold,
        formue = b.formue ?: this.formue,
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

    data class LovligOpphold(
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
        ): LovligOppholdVilkår {
            return when (status) {
                Status.VilkårOppfylt,
                Status.VilkårIkkeOppfylt,
                -> {
                    LovligOppholdVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodeLovligOpphold.tryCreate(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(clock),
                                resultat = when (erVilkårOppfylt()) {
                                    true -> Resultat.Innvilget
                                    false -> Resultat.Avslag
                                },
                                grunnlag = LovligOppholdGrunnlag.tryCreate(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(clock),
                                    periode = stønadsperiode.periode,
                                ).getOrHandle {
                                    throw IllegalArgumentException("Kunne ikke instansiere ${LovligOppholdGrunnlag::class.simpleName}. Melding: $it")
                                },
                                vurderingsperiode = stønadsperiode.periode,
                            ).getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${VurderingsperiodeLovligOpphold::class.simpleName}. Melding: $it")
                            },
                        ),
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${LovligOppholdVilkår.Vurdert::class.simpleName}. Melding: $it")
                    }
                }
                Status.Uavklart -> LovligOppholdVilkår.IkkeVurdert
            }
        }
    }

    data class FastOppholdINorge(
        val status: Status,
        val begrunnelse: String?,
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
                                begrunnelse = begrunnelse ?: "",
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
        val begrunnelse: String?,
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
                                begrunnelse = begrunnelse ?: "",
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

    data class Formue(
        val status: Status,
        val verdier: Verdier,
        val epsVerdier: Verdier?,
        val begrunnelse: String?,
    ) : Base() {

        fun erDepositumHøyereEnnInnskudd(): Boolean {
            return verdier.depositumHøyereEnnInnskudd() || epsVerdier?.depositumHøyereEnnInnskudd() == true
        }

        data class Verdier(
            val verdiIkkePrimærbolig: Int,
            val verdiEiendommer: Int,
            val verdiKjøretøy: Int,
            val innskudd: Int,
            val verdipapir: Int,
            val pengerSkyldt: Int,
            val kontanter: Int,
            val depositumskonto: Int,
        ) {
            fun depositumHøyereEnnInnskudd(): Boolean {
                return this.depositumskonto > this.innskudd
            }

            companion object {
                fun lagTomVerdier() = Verdier(
                    verdiIkkePrimærbolig = 0,
                    verdiEiendommer = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0,
                )
            }
        }

        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            MåInnhenteMerInformasjon
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        /**
         * Midlertidig migreringsfunksjon fra Behandlingsinformasjon + Grunnlag.Bosituasjon -> Behandlingsinformasjon
         * Behandlingsinformasjonen ligger blant annet i Vedtaket inntil videre.
         *
         * TODO: helhet rundt løsning for oppdatering av formue og bosituasjon (formue avhengig av bosituasjon).
         */
        fun nullstillEpsFormueHvisIngenEps(
            bosituasjon: Grunnlag.Bosituasjon,
        ): Formue {
            return when (bosituasjon) {
                is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps,
                is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen,
                is Grunnlag.Bosituasjon.Fullstendig.Enslig,
                -> {
                    // fjerner eventuelle eps-verdier for å unngå ugyldig tilstand på tvers av bostituasjon og formue
                    this.copy(epsVerdier = null)
                }
                is Grunnlag.Bosituasjon.Ufullstendig.HarEps,
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning,
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre,
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning,
                -> this
            }
        }

        fun tilVilkår(
            stønadsperiode: Stønadsperiode,
            bosituasjon: List<Grunnlag.Bosituasjon>,
            clock: Clock,
            formuegrenserFactory: FormuegrenserFactory,
        ): Vilkår.Formue {
            return Vilkår.Formue.Vurdert.tryCreateFromGrunnlag(
                grunnlag = nonEmptyListOf(
                    Formuegrunnlag.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = stønadsperiode.periode,
                        epsFormue = this.epsVerdier?.let {
                            Formuegrunnlag.Verdier.tryCreate(
                                verdiIkkePrimærbolig = it.verdiIkkePrimærbolig,
                                verdiEiendommer = it.verdiEiendommer,
                                verdiKjøretøy = it.verdiKjøretøy,
                                innskudd = it.innskudd,
                                verdipapir = it.verdipapir,
                                pengerSkyldt = it.pengerSkyldt,
                                kontanter = it.kontanter,
                                depositumskonto = it.depositumskonto,
                            ).getOrHandle {
                                throw IllegalStateException("Kunne ikke create formue-verdier. Sjekk om data er gyldig")
                            }
                        },
                        søkersFormue = this.verdier.let {
                            Formuegrunnlag.Verdier.tryCreate(
                                verdiIkkePrimærbolig = it.verdiIkkePrimærbolig,
                                verdiEiendommer = it.verdiEiendommer,
                                verdiKjøretøy = it.verdiKjøretøy,
                                innskudd = it.innskudd,
                                verdipapir = it.verdipapir,
                                pengerSkyldt = it.pengerSkyldt,
                                kontanter = it.kontanter,
                                depositumskonto = it.depositumskonto,
                            ).getOrHandle {
                                throw IllegalStateException("Kunne ikke create formue-verdier. Sjekk om data er gyldig")
                            }
                        },
                        begrunnelse = this.begrunnelse,
                        bosituasjon = bosituasjon,
                        behandlingsPeriode = stønadsperiode.periode,
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${Formuegrunnlag::class.simpleName}. Melding: $it")
                    },
                ),
                formuegrenserFactory = formuegrenserFactory,
            ).getOrHandle {
                throw IllegalArgumentException("Kunne ikke instansiere ${Vilkår.Formue.Vurdert::class.simpleName}. Melding: $it")
            }
        }
    }

    data class PersonligOppmøte(
        val status: Status,
        val begrunnelse: String?,
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
                                begrunnelse = begrunnelse ?: "",
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
            lovligOpphold = null,
            fastOppholdINorge = null,
            institusjonsopphold = null,
            formue = null,
            personligOppmøte = null,
        )
    }
}
