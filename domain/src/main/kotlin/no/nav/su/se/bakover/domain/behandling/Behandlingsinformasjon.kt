package no.nav.su.se.bakover.domain.behandling

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.brev.Avslagsgrunn
import no.nav.su.se.bakover.domain.brev.Satsgrunn
import java.time.LocalDate
import java.time.Period

data class Behandlingsinformasjon(
    val uførhet: Uførhet? = null,
    val flyktning: Flyktning? = null,
    val lovligOpphold: LovligOpphold? = null,
    val fastOppholdINorge: FastOppholdINorge? = null,
    val oppholdIUtlandet: OppholdIUtlandet? = null,
    val formue: Formue? = null,
    val personligOppmøte: PersonligOppmøte? = null,
    val bosituasjon: Bosituasjon? = null,
    val ektefelle: EktefellePartnerSamboer? = null,
) {
    private val alleVilkår = listOf(
        uførhet,
        flyktning,
        lovligOpphold,
        fastOppholdINorge,
        oppholdIUtlandet,
        formue,
        personligOppmøte,
        bosituasjon,
        ektefelle,
    )

    fun patch(
        b: Behandlingsinformasjon
    ) = Behandlingsinformasjon(
        uførhet = b.uførhet ?: this.uførhet,
        flyktning = b.flyktning ?: this.flyktning,
        lovligOpphold = b.lovligOpphold ?: this.lovligOpphold,
        fastOppholdINorge = b.fastOppholdINorge ?: this.fastOppholdINorge,
        oppholdIUtlandet = b.oppholdIUtlandet ?: this.oppholdIUtlandet,
        formue = b.formue ?: this.formue,
        personligOppmøte = b.personligOppmøte ?: this.personligOppmøte,
        bosituasjon = b.bosituasjon ?: this.bosituasjon,
        ektefelle = b.ektefelle ?: this.ektefelle,
    )

    fun erInnvilget() = alleVilkår.all { it !== null && it.erVilkårOppfylt() }
    fun getAvslagsgrunn() = alleVilkår.mapNotNull { it?.avslagsgrunn() }.firstOrNull()
    fun erAvslag(): Boolean {
        return uførhetOgFlyktningsstatusErVurdertOgMinstEnAvDeErIkkeOppfylt() ||
            (alleVilkår.all { it !== null } && alleVilkår.any { it!!.erVilkårIkkeOppfylt() })
    }

    private fun uførhetOgFlyktningsstatusErVurdertOgMinstEnAvDeErIkkeOppfylt(): Boolean {
        if (uførhet != null && flyktning != null) {
            if (uførhet.erVilkårIkkeOppfylt() || flyktning.erVilkårIkkeOppfylt()) {
                return true
            }
        }
        return false
    }

    abstract class Base {
        abstract fun erGyldig(): Boolean
        abstract fun erVilkårOppfylt(): Boolean
        abstract fun erVilkårIkkeOppfylt(): Boolean
        abstract fun avslagsgrunn(): Avslagsgrunn?
    }

    data class Uførhet(
        val status: Status,
        val uføregrad: Int?,
        val forventetInntekt: Int?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            HarUføresakTilBehandling
        }

        override fun erGyldig(): Boolean =
            when (status) {
                Status.VilkårOppfylt -> uføregrad != null && forventetInntekt != null
                Status.VilkårIkkeOppfylt -> uføregrad == null && forventetInntekt == null
                Status.HarUføresakTilBehandling -> uføregrad != null && uføregrad > 0 && forventetInntekt != null && forventetInntekt > 0
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

        override fun erGyldig(): Boolean = true

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

        override fun erGyldig(): Boolean = true

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

        override fun erGyldig(): Boolean = true

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE else null
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

        override fun erGyldig(): Boolean = true

        override fun erVilkårOppfylt(): Boolean = status == Status.SkalHoldeSegINorge
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.SkalVæreMerEnn90DagerIUtlandet

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (erVilkårIkkeOppfylt()) Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER else null
    }

    data class Formue(
        val status: Status,
        val verdier: Verdier?,
        val ektefellesVerdier: Verdier?,
        val begrunnelse: String?
    ) : Base() {
        data class Verdier(
            val verdiIkkePrimærbolig: Int?,
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

        override fun erGyldig(): Boolean =
            when (status) {
                Status.MåInnhenteMerInformasjon -> true
                else ->
                    verdier?.verdiIkkePrimærbolig != null &&
                        verdier.verdiKjøretøy != null &&
                        verdier.innskudd != null &&
                        verdier.verdipapir != null &&
                        verdier.pengerSkyldt != null &&
                        verdier.kontanter != null &&
                        verdier.depositumskonto != null
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

        override fun erGyldig(): Boolean = true

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
        val epsFnr: Fnr?,
        val delerBolig: Boolean?,
        val ektemakeEllerSamboerUførFlyktning: Boolean?,
        val begrunnelse: String?
    ) : Base() {
        fun utledSats() = getBeregningStrategy().sats()

        internal fun getBeregningStrategy(): BeregningStrategy {
            if (epsFnr == null && (delerBolig != null && !delerBolig)) {
                return BeregningStrategy.BorAlene
            } else {
                if (delerBolig != null && delerBolig) {
                    return BeregningStrategy.BorMedVoksne
                }
                if (epsFnr != null) {
                    println(epsUnder67År())
                    if (!epsUnder67År()) {
                        return BeregningStrategy.EpsOver67År
                    }
                    if (ektemakeEllerSamboerUførFlyktning != null && ektemakeEllerSamboerUførFlyktning) {
                        return BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
                    }
                    return BeregningStrategy.EpsUnder67År
                }
                throw RuntimeException("TODO")
            }
        }

        override fun erGyldig(): Boolean {
            if (epsFnr == null && delerBolig == null) {
                return false
            }
            if (epsFnr != null) {
                return ektemakeEllerSamboerUførFlyktning != null
            }
            return delerBolig != null
        }

        override fun erVilkårOppfylt(): Boolean = erGyldig()
        override fun erVilkårIkkeOppfylt(): Boolean = false

        override fun avslagsgrunn(): Avslagsgrunn? = null

        fun getSatsgrunn() = when {
            delerBolig != null && !delerBolig -> Satsgrunn.ENSLIG
            delerBolig != null && delerBolig ->
                Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
            epsFnr != null && !epsUnder67År() ->
                Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67
            epsFnr != null && epsUnder67År() && ektemakeEllerSamboerUførFlyktning == false ->
                Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
            epsFnr != null && epsUnder67År() && ektemakeEllerSamboerUførFlyktning == true ->
                Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
            else -> null
        }

        private fun epsUnder67År() =
            Period.between(
                LocalDate.of(
                    epsFnr.toString().substring(5, 6).toInt(),
                    epsFnr.toString().substring(3, 4).toInt(),
                    epsFnr.toString().substring(1, 2).toInt(),
                ),
                LocalDate.now()
            ).years < 67
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
        data class Ektefelle(val fnr: Fnr) : EktefellePartnerSamboer()
        object IngenEktefelle : EktefellePartnerSamboer()

        override fun erGyldig(): Boolean = true
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
            oppholdIUtlandet = null,
            formue = null,
            personligOppmøte = null,
            bosituasjon = null,
            ektefelle = null
        )
    }
}
