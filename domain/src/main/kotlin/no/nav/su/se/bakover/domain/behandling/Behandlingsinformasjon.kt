package no.nav.su.se.bakover.domain.behandling

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE
import no.nav.su.se.bakover.domain.behandling.Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
import no.nav.su.se.bakover.domain.behandling.Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
import no.nav.su.se.bakover.domain.behandling.Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Sats
import java.time.LocalDate
import java.time.Period

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
    private val vilkår = listOf(
        uførhet,
        flyktning,
        lovligOpphold,
        fastOppholdINorge,
        institusjonsopphold,
        oppholdIUtlandet,
        formue,
        personligOppmøte,
    )
    private val allBehandlingsinformasjon: List<Base?>
        get() {
            return vilkår + bosituasjon + ektefelle
        }

    fun patch(
        b: Behandlingsinformasjon
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

    fun erInnvilget(): Boolean = allBehandlingsinformasjon.all { it !== null && it.erVilkårOppfylt() }
    fun utledAvslagsgrunner(): List<Avslagsgrunn> = allBehandlingsinformasjon.mapNotNull { it?.avslagsgrunn() }
    fun erAvslag(): Boolean {
        return uførhetOgFlyktningsstatusErVurdertOgMinstEnAvDeErIkkeOppfylt() ||
            (vilkår.all { it !== null } && vilkår.any { it!!.erVilkårIkkeOppfylt() })
    }

    private fun uførhetOgFlyktningsstatusErVurdertOgMinstEnAvDeErIkkeOppfylt(): Boolean {
        if (uførhet != null && flyktning != null) {
            if (uførhet.erVilkårIkkeOppfylt() || flyktning.erVilkårIkkeOppfylt()) {
                return true
            }
        }
        return false
    }

    fun harEktefelle(): Boolean {
        return ektefelle is EktefellePartnerSamboer.Ektefelle
    }

    abstract class Base {
        abstract fun erVilkårOppfylt(): Boolean
        abstract fun erVilkårIkkeOppfylt(): Boolean
        abstract fun avslagsgrunn(): Avslagsgrunn?
    }

    data class Uførhet(
        val status: Status,
        val uføregrad: Int?,
        val forventetInntekt: Int?,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            HarUføresakTilBehandling
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.UFØRHET else null
    }

    data class Flyktning(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.FLYKTNING else null
    }

    data class LovligOpphold(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.OPPHOLDSTILLATELSE else null
    }

    data class FastOppholdINorge(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE else null
    }

    data class Institusjonsopphold(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart,
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt
        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON else null
    }

    data class OppholdIUtlandet(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            SkalVæreMerEnn90DagerIUtlandet,
            SkalHoldeSegINorge,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.SkalHoldeSegINorge
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.SkalVæreMerEnn90DagerIUtlandet

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER else null
    }

    data class Formue(
        val status: Status,
        val verdier: Verdier?,
        val borSøkerMedEPS: Boolean,
        val epsVerdier: Verdier?,
        val begrunnelse: String?
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
        )

        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            MåInnhenteMerInformasjon
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.FORMUE else null
    }

    data class PersonligOppmøte(
        val status: Status,
        val begrunnelse: String?
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

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.PERSONLIG_OPPMØTE else null
    }

    data class Bosituasjon(
        val epsAlder: Int?,
        val delerBolig: Boolean?,
        val ektemakeEllerSamboerUførFlyktning: Boolean?,
        val begrunnelse: String?
    ) : Base() {

        @JsonIgnore
        fun utledSats(): Sats = getBeregningStrategy().sats()

        @JsonIgnore
        internal fun getBeregningStrategy(): BeregningStrategy {
            if (epsAlder == null && delerBolig == false) {
                return BeregningStrategy.BorAlene
            } else {
                if (delerBolig == true) {
                    return BeregningStrategy.BorMedVoksne
                }
                if (epsAlder != null) {
                    if (epsAlder >= 67) {
                        return BeregningStrategy.Eps67EllerEldre
                    }
                    if (ektemakeEllerSamboerUførFlyktning == true) {
                        return BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
                    }
                    return BeregningStrategy.EpsUnder67År
                }
                throw RuntimeException("Uhåndtert case for beregning strategy: epsAlder: $epsAlder, delerBolig: $delerBolig, ektemakeEllerSamboerUførFlyktning: $ektemakeEllerSamboerUførFlyktning")
            }
        }

        override fun erVilkårOppfylt(): Boolean {
            if (epsAlder == null && delerBolig == null) {
                return false
            }
            if (epsAlder != null) {
                if (epsAlder < 67) {
                    return ektemakeEllerSamboerUførFlyktning != null
                }
                if (epsAlder >= 67) {
                    return ektemakeEllerSamboerUførFlyktning == null
                }
            }
            return delerBolig != null
        }

        override fun erVilkårIkkeOppfylt(): Boolean = false

        override fun avslagsgrunn(): Avslagsgrunn? = null

        @JsonIgnore
        fun getSatsgrunn(): Satsgrunn {
            val eps67EllerEldre = epsAlder != null && epsAlder >= 67
            val epsUnder67 = epsAlder != null && epsAlder < 67
            return when {
                delerBolig == false -> Satsgrunn.ENSLIG
                delerBolig == true -> DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
                eps67EllerEldre -> DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE
                epsUnder67 && ektemakeEllerSamboerUførFlyktning == false -> DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
                epsUnder67 && ektemakeEllerSamboerUførFlyktning == true -> DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
                else -> throw IllegalStateException("Kunne ikke utlede satsgrunn")
            }
        }
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
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
            val skjermet: Boolean?
        ) : EktefellePartnerSamboer() {
            fun getAlder() = fødselsdato?.let { Period.between(it, LocalDate.now()).years }
        }

        object IngenEktefelle : EktefellePartnerSamboer() {
            override fun equals(other: Any?): Boolean = other is IngenEktefelle
        }

        override fun erVilkårOppfylt(): Boolean = true
        override fun erVilkårIkkeOppfylt(): Boolean = false
        override fun avslagsgrunn(): Avslagsgrunn? = null
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
            ektefelle = null
        )
    }
}
