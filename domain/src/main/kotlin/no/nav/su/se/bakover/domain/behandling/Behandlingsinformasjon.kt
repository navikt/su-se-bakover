package no.nav.su.se.bakover.domain.behandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.FlyktningGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.OppholdIUtlandetGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.singleOrThrow
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFastOppholdINorge
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFlyktning
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeLovligOpphold
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOppholdIUtlandet
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePersonligOppmøte
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.util.UUID

data class Behandlingsinformasjon(
    val uførhet: Uførhet? = null,
    val flyktning: Flyktning? = null,
    val lovligOpphold: LovligOpphold? = null,
    val fastOppholdINorge: FastOppholdINorge? = null,
    val institusjonsopphold: Institusjonsopphold? = null,
    val oppholdIUtlandet: OppholdIUtlandet? = null,
    val formue: Formue? = null,
    val personligOppmøte: PersonligOppmøte? = null,
    val bosituasjon: Bosituasjon? = null,
    val ektefelle: EktefellePartnerSamboer? = null,
) {
    @JsonIgnore
    val vilkår = listOf(
        uførhet,
        flyktning,
        lovligOpphold,
        fastOppholdINorge,
        institusjonsopphold,
        oppholdIUtlandet,
        formue,
        personligOppmøte,
    )

    fun patch(
        b: Behandlingsinformasjon,
    ) = Behandlingsinformasjon(
        uførhet = b.uførhet ?: this.uførhet,
        flyktning = b.flyktning ?: this.flyktning,
        lovligOpphold = b.lovligOpphold ?: this.lovligOpphold,
        fastOppholdINorge = b.fastOppholdINorge ?: this.fastOppholdINorge,
        institusjonsopphold = b.institusjonsopphold ?: this.institusjonsopphold,
        oppholdIUtlandet = b.oppholdIUtlandet ?: this.oppholdIUtlandet,
        formue = b.formue ?: this.formue,
        personligOppmøte = b.personligOppmøte ?: this.personligOppmøte,
        bosituasjon = b.bosituasjon ?: this.bosituasjon,
        ektefelle = b.ektefelle ?: this.ektefelle,
    )

    @JsonIgnore
    fun utledSats(): Either<UfullstendigBehandlingsinformasjon, Sats> {
        return getBeregningStrategy().map { it.sats() }
    }

    @JsonIgnore
    fun getSatsgrunn(): Either<UfullstendigBehandlingsinformasjon, Satsgrunn> {
        return getBeregningStrategy().map { it.satsgrunn() }
    }

    @JsonIgnore
    internal fun getBeregningStrategy(): Either<UfullstendigBehandlingsinformasjon, BeregningStrategy> {
        if (ektefelle == null) return UfullstendigBehandlingsinformasjon.EktefelleErUbesvart.left()
        if (bosituasjon == null) return UfullstendigBehandlingsinformasjon.BosituasjonErUbesvart.left()

        return when (ektefelle) {
            is EktefellePartnerSamboer.Ektefelle -> when {
                ektefelle.er67EllerEldre() -> BeregningStrategy.Eps67EllerEldre
                else -> when (bosituasjon.ektemakeEllerSamboerUførFlyktning) {
                    null -> return UfullstendigBehandlingsinformasjon.EpsUførFlyktningErUbesvart.left()
                    true -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
                    false -> BeregningStrategy.EpsUnder67År
                }
            }
            EktefellePartnerSamboer.IngenEktefelle -> when (bosituasjon.delerBolig) {
                null -> return UfullstendigBehandlingsinformasjon.DelerBoligErUbesvart.left()
                true -> BeregningStrategy.BorMedVoksne
                false -> BeregningStrategy.BorAlene
            }
        }.right()
    }

    /** Gjelder for utleding av sats, satsgrunn og beregningsstrategi */
    sealed class UfullstendigBehandlingsinformasjon {
        object BosituasjonErUbesvart : UfullstendigBehandlingsinformasjon()
        object EktefelleErUbesvart : UfullstendigBehandlingsinformasjon()

        /** Dersom man bor med ektefelle kan ikke bosituasjon->ektemakeEllerSamboerUførFlyktning være ubesvart */
        object EpsUførFlyktningErUbesvart : UfullstendigBehandlingsinformasjon()

        /** Når man ikke bor med ektefelle kan ikke bosituasjon->deler_bolig være ubesvart */
        object DelerBoligErUbesvart : UfullstendigBehandlingsinformasjon()
    }

    fun harEktefelle(): Boolean {
        return ektefelle is EktefellePartnerSamboer.Ektefelle
    }

    abstract class Base {
        abstract fun erVilkårOppfylt(): Boolean
        abstract fun erVilkårIkkeOppfylt(): Boolean
    }

    data class Uførhet(
        val status: Status,
        val uføregrad: Int?,
        val forventetInntekt: Int?,
        val begrunnelse: String?,
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            HarUføresakTilBehandling
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt
    }

    data class Flyktning(
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
                                begrunnelse = begrunnelse ?: "",
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
                                begrunnelse = begrunnelse ?: "",
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

    data class OppholdIUtlandet(
        val status: Status,
        val begrunnelse: String?,
    ) : Base() {
        enum class Status {
            SkalVæreMerEnn90DagerIUtlandet,
            SkalHoldeSegINorge,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.SkalHoldeSegINorge
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.SkalVæreMerEnn90DagerIUtlandet

        fun tilVilkår(
            stønadsperiode: Stønadsperiode,
            clock: Clock,
        ): OppholdIUtlandetVilkår {
            return when (status) {
                Status.SkalHoldeSegINorge,
                Status.SkalVæreMerEnn90DagerIUtlandet,
                -> {
                    OppholdIUtlandetVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodeOppholdIUtlandet.tryCreate(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(clock),
                                resultat = when (erVilkårOppfylt()) {
                                    true -> Resultat.Innvilget
                                    false -> Resultat.Avslag
                                },
                                grunnlag = OppholdIUtlandetGrunnlag.tryCreate(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(clock),
                                    periode = stønadsperiode.periode,
                                ).getOrHandle {
                                    throw IllegalArgumentException("Kunne ikke instansiere ${OppholdIUtlandetGrunnlag::class.simpleName}. Melding: $it")
                                },
                                vurderingsperiode = stønadsperiode.periode,
                                begrunnelse = begrunnelse ?: "",
                            ).getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${VurderingsperiodeOppholdIUtlandet::class.simpleName}. Melding: $it")
                            },
                        ),
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${OppholdIUtlandetVilkår.Vurdert::class.simpleName}. Melding: $it")
                    }
                }
                Status.Uavklart -> OppholdIUtlandetVilkår.IkkeVurdert
            }
        }
    }

    data class Formue(
        val status: Status,
        val verdier: Verdier?,
        val epsVerdier: Verdier?,
        val begrunnelse: String?,
    ) : Base() {
        data class Verdier(
            val verdiIkkePrimærbolig: Int?,
            val verdiEiendommer: Int?,
            val verdiKjøretøy: Int?,
            val innskudd: Int?,
            val verdipapir: Int?,
            val pengerSkyldt: Int?,
            val kontanter: Int?,
            val depositumskonto: Int?,
        ) {
            fun erVerdierStørreEnn0(): Boolean = sumVerdier() > 0

            private fun sumVerdier(): Int {
                return (verdiIkkePrimærbolig ?: 0) +
                    (verdiEiendommer ?: 0) +
                    (verdiKjøretøy ?: 0) +
                    (innskudd ?: 0) +
                    (verdipapir ?: 0) +
                    (pengerSkyldt ?: 0) +
                    (kontanter ?: 0) +
                    (depositumskonto ?: 0)
            }
        }

        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            MåInnhenteMerInformasjon
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        fun harEpsFormue(): Boolean {
            return epsVerdier?.erVerdierStørreEnn0() == true
        }

        fun tilVilkår(
            stønadsperiode: Stønadsperiode,
            bosituasjon: List<Grunnlag.Bosituasjon>,
            clock: Clock,
        ): Vilkår.Formue {
            return Vilkår.Formue.Vurdert.tryCreateFromGrunnlag(
                grunnlag = nonEmptyListOf(
                    Formuegrunnlag.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = stønadsperiode.periode,
                        epsFormue = this.epsVerdier?.let {
                            Formuegrunnlag.Verdier.tryCreate(
                                verdiIkkePrimærbolig = it.verdiIkkePrimærbolig ?: 0,
                                verdiEiendommer = it.verdiEiendommer ?: 0,
                                verdiKjøretøy = it.verdiKjøretøy ?: 0,
                                innskudd = it.innskudd ?: 0,
                                verdipapir = it.verdipapir ?: 0,
                                pengerSkyldt = it.pengerSkyldt ?: 0,
                                kontanter = it.kontanter ?: 0,
                                depositumskonto = it.depositumskonto ?: 0,
                            ).getOrHandle {
                                throw IllegalStateException("Kunne ikke create formue-verdier. Sjekk om data er gyldig")
                            }
                        },
                        søkersFormue = this.verdier.let {
                            Formuegrunnlag.Verdier.tryCreate(
                                verdiIkkePrimærbolig = it?.verdiIkkePrimærbolig ?: 0,
                                verdiEiendommer = it?.verdiEiendommer ?: 0,
                                verdiKjøretøy = it?.verdiKjøretøy ?: 0,
                                innskudd = it?.innskudd ?: 0,
                                verdipapir = it?.verdipapir ?: 0,
                                pengerSkyldt = it?.pengerSkyldt ?: 0,
                                kontanter = it?.kontanter ?: 0,
                                depositumskonto = it?.depositumskonto ?: 0,
                            ).getOrHandle {
                                throw IllegalStateException("Kunne ikke create formue-verdier. Sjekk om data er gyldig")
                            }
                        },
                        begrunnelse = this.begrunnelse,
                        bosituasjon = bosituasjon.singleOrThrow(),
                        behandlingsPeriode = stønadsperiode.periode,
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${Formuegrunnlag::class.simpleName}. Melding: $it")
                    },
                ),
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

    data class Bosituasjon(
        val ektefelle: EktefellePartnerSamboer?,
        val delerBolig: Boolean?,
        val ektemakeEllerSamboerUførFlyktning: Boolean?,
        val begrunnelse: String?,
    ) : Base() {
        override fun erVilkårOppfylt(): Boolean {
            val ektefelleEr67EllerEldre = (ektefelle as? EktefellePartnerSamboer.Ektefelle)?.er67EllerEldre()
            if ((ektefelleEr67EllerEldre == false && ektemakeEllerSamboerUførFlyktning == null) && delerBolig == null) {
                return false
            }
            if (ektemakeEllerSamboerUførFlyktning != null && delerBolig != null) {
                throw IllegalStateException("ektemakeEllerSamboerUførFlyktning og delerBolig kan ikke begge være true samtidig")
            }
            return true
        }

        override fun erVilkårIkkeOppfylt(): Boolean = false
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = EktefellePartnerSamboer.Ektefelle::class, name = "Ektefelle"),
        JsonSubTypes.Type(value = EktefellePartnerSamboer.IngenEktefelle::class, name = "IngenEktefelle"),
    )
    sealed class EktefellePartnerSamboer : Base() {
        data class Ektefelle(
            val fnr: Fnr,
            val navn: Person.Navn?,
            val kjønn: String?,
            val fødselsdato: LocalDate?,
            val adressebeskyttelse: String?,
            val skjermet: Boolean?,
        ) : EktefellePartnerSamboer() {
            // TODO jah: Hva når fødselsdato er null?
            fun getAlder(): Int? = fødselsdato?.let { Period.between(it, LocalDate.now()).years }

            /**
             * TODO jah: Hva når fødselsdato er null?
             * @throws NullPointerException
             */
            fun er67EllerEldre(): Boolean = getAlder()!! >= 67
        }

        object IngenEktefelle : EktefellePartnerSamboer() {
            override fun equals(other: Any?): Boolean = other is IngenEktefelle
        }

        override fun erVilkårOppfylt(): Boolean = true
        override fun erVilkårIkkeOppfylt(): Boolean = false
    }

    companion object {
        fun lagTomBehandlingsinformasjon() = Behandlingsinformasjon(
            uførhet = null,
            flyktning = null,
            lovligOpphold = null,
            fastOppholdINorge = null,
            institusjonsopphold = null,
            oppholdIUtlandet = null,
            formue = null,
            personligOppmøte = null,
            bosituasjon = null,
            ektefelle = null,
        )
    }

    /**
     * Midlertidig migreringsfunksjon fra Behandlingsinformasjon + Grunnlag.Bosituasjon -> Behandlingsinformasjon
     * Behandlingsinformasjonen ligger blant annet i Vedtaket inntil videre.
     *
     * TODO: helhet rundt løsning for oppdatering av formue og bosituasjon (formue avhengig av bosituasjon).
     */
    fun oppdaterBosituasjonOgEktefelleOgNullstillFormueForEpsHvisIngenEps(
        bosituasjon: Grunnlag.Bosituasjon,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeHentePerson, Behandlingsinformasjon> {
        val (oppdatertBosituasjon, oppdatertFormue) = when (bosituasjon) {
            is Grunnlag.Bosituasjon.Ufullstendig.HarEps -> {
                Bosituasjon(
                    ektefelle = hentEktefelle(bosituasjon.fnr, hentPerson).getOrHandle { return it.left() },
                    delerBolig = null,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = null,
                ) to formue
            }
            is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps -> {
                Bosituasjon(
                    ektefelle = EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = null,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = null,
                    // TODO helhetlig løsning for oppdatering av begge avhengige verdier samtidig
                ) to formue?.copy(epsVerdier = null) // fjerner eventuelle eps-verdier for å unngå ugyldig tilstand på tvers av bostituasjon og formue
            }
            is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> {
                Bosituasjon(
                    ektefelle = EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = true,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = bosituasjon.begrunnelse,
                ) to formue
            }
            is Grunnlag.Bosituasjon.Fullstendig.Enslig -> {
                Bosituasjon(
                    ektefelle = EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = false,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = bosituasjon.begrunnelse,
                ) to formue
            }
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> {
                Bosituasjon(
                    ektefelle = hentEktefelle(bosituasjon.fnr, hentPerson).getOrHandle { return it.left() },
                    delerBolig = null,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = bosituasjon.begrunnelse,
                ) to formue
            }
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> {
                Bosituasjon(
                    ektefelle = hentEktefelle(bosituasjon.fnr, hentPerson).getOrHandle { return it.left() },
                    delerBolig = null,
                    ektemakeEllerSamboerUførFlyktning = false,
                    begrunnelse = bosituasjon.begrunnelse,
                ) to formue
            }
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> {
                Bosituasjon(
                    ektefelle = hentEktefelle(bosituasjon.fnr, hentPerson).getOrHandle { return it.left() },
                    delerBolig = null,
                    ektemakeEllerSamboerUførFlyktning = true,
                    begrunnelse = bosituasjon.begrunnelse,
                ) to formue
            }
        }
        return this.copy(
            bosituasjon = oppdatertBosituasjon,
            ektefelle = oppdatertBosituasjon.ektefelle,
            formue = oppdatertFormue,
        ).right()
    }

    private fun hentEktefelle(
        fnr: Fnr,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeHentePerson, EktefellePartnerSamboer.Ektefelle> {
        val eps = hentPerson(fnr).getOrHandle {
            return KunneIkkeHentePerson.FantIkkePerson.left()
        }
        return EktefellePartnerSamboer.Ektefelle(
            fnr = fnr,
            navn = eps.navn,
            kjønn = eps.kjønn,
            fødselsdato = eps.fødselsdato,
            adressebeskyttelse = eps.adressebeskyttelse,
            skjermet = eps.skjermet,
        ).right()
    }
}
