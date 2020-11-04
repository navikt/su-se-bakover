package no.nav.su.se.bakover.domain.behandling

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.brev.Avslagsgrunn
import no.nav.su.se.bakover.domain.brev.Satsgrunn

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

    private fun erFerdigbehandlet() =
        listOf(
            uførhet,
            flyktning,
            lovligOpphold,
            fastOppholdINorge,
            oppholdIUtlandet,
            formue,
            personligOppmøte,
            bosituasjon,
            ektefelle,
        ).all { it != null && it.erGyldig() && it.erFerdigbehandlet() }

    fun erInnvilget() =
        listOf(
            uførhet?.erVilkårOppfylt(),
            flyktning?.erVilkårOppfylt(),
            lovligOpphold?.erVilkårOppfylt(),
            fastOppholdINorge?.erVilkårOppfylt(),
            oppholdIUtlandet?.erVilkårOppfylt(),
            formue?.erVilkårOppfylt(),
            personligOppmøte?.erVilkårOppfylt(),
        ).all { it ?: false }

    fun erAvslag() = erFerdigbehandlet() && !erInnvilget()

    fun getAvslagsgrunn() =
        listOfNotNull(
            uførhet?.avslagsgrunn(),
            flyktning?.avslagsgrunn(),
            lovligOpphold?.avslagsgrunn(),
            fastOppholdINorge?.avslagsgrunn(),
            oppholdIUtlandet?.avslagsgrunn(),
            formue?.avslagsgrunn(),
            personligOppmøte?.avslagsgrunn(),
        ).firstOrNull()

    abstract class Base {
        abstract fun erGyldig(): Boolean
        abstract fun erFerdigbehandlet(): Boolean
        abstract fun erVilkårOppfylt(): Boolean
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

        override fun erFerdigbehandlet(): Boolean = status != Status.HarUføresakTilBehandling
        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun avslagsgrunn(): Avslagsgrunn? =
            if (status == Status.VilkårIkkeOppfylt) Avslagsgrunn.UFØRHET else null
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
        override fun erFerdigbehandlet(): Boolean = status != Status.Uavklart
        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun avslagsgrunn(): Avslagsgrunn? =
            if (status == Status.VilkårIkkeOppfylt) Avslagsgrunn.FLYKTNING else null
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
        override fun erFerdigbehandlet(): Boolean = status != Status.Uavklart
        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun avslagsgrunn(): Avslagsgrunn? =
            if (status == Status.VilkårIkkeOppfylt) Avslagsgrunn.OPPHOLDSTILLATELSE else null
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
        override fun erFerdigbehandlet(): Boolean = status != Status.Uavklart
        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun avslagsgrunn(): Avslagsgrunn? =
            if (status == Status.VilkårIkkeOppfylt) Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE else null
    }

    data class OppholdIUtlandet(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            SkalVæreMerEnn90DagerIUtlandet,
            SkalHoldeSegINorge
        }

        override fun erGyldig(): Boolean = true
        override fun erFerdigbehandlet(): Boolean = erGyldig()
        override fun erVilkårOppfylt(): Boolean = status == Status.SkalHoldeSegINorge
        override fun avslagsgrunn(): Avslagsgrunn? =
            if (status == Status.SkalVæreMerEnn90DagerIUtlandet) Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER else null
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
                    verdier?.verdiIkkePrimærbolig !== null &&
                        verdier.verdiKjøretøy !== null &&
                        verdier.innskudd !== null &&
                        verdier.verdipapir !== null &&
                        verdier.pengerSkyldt !== null &&
                        verdier.kontanter !== null &&
                        verdier.depositumskonto !== null
            }

        override fun erFerdigbehandlet(): Boolean = status != Status.MåInnhenteMerInformasjon
        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun avslagsgrunn(): Avslagsgrunn? =
            if (status == Status.VilkårIkkeOppfylt) Avslagsgrunn.FORMUE else null
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
            IkkeMøttPersonlig
        }

        override fun erGyldig(): Boolean = true
        override fun erFerdigbehandlet(): Boolean = erGyldig()
        override fun erVilkårOppfylt(): Boolean =
            status.let {
                it == Status.MøttPersonlig ||
                    it == Status.IkkeMøttMenVerge ||
                    it == Status.IkkeMøttMenSykMedLegeerklæringOgFullmakt ||
                    it == Status.IkkeMøttMenKortvarigSykMedLegeerklæring ||
                    it == Status.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt
            }

        override fun avslagsgrunn(): Avslagsgrunn? =
            if (status == Status.IkkeMøttPersonlig) Avslagsgrunn.PERSONLIG_OPPMØTE else null
    }

    data class Bosituasjon(
        val delerBolig: Boolean,
        val delerBoligMed: Boforhold.DelerBoligMed?,
        val ektemakeEllerSamboerUnder67År: Boolean?,
        val ektemakeEllerSamboerUførFlyktning: Boolean?,
        val begrunnelse: String?
    ) : Base() {
        fun utledSats() = getBeregningStrategy().sats()

        internal fun getBeregningStrategy() =
            if (!delerBolig) {
                BeregningStrategy.BorAlene
            } else {
                // Vi gjør en del null assertions her for at logikken ikke skal bli så vanskelig å følge
                // Det _bør_ være trygt fordi gyldighet av objektet skal bli sjekket andre plasser
                when (delerBoligMed!!) {
                    Boforhold.DelerBoligMed.VOKSNE_BARN, Boforhold.DelerBoligMed.ANNEN_VOKSEN -> BeregningStrategy.BorMedVoksne
                    Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER ->
                        if (!ektemakeEllerSamboerUnder67År!!) {
                            BeregningStrategy.EpsOver67År
                        } else {
                            if (ektemakeEllerSamboerUførFlyktning!!) {
                                BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
                            } else {
                                BeregningStrategy.EpsUnder67År
                            }
                        }
                }
            }

        override fun erGyldig(): Boolean =
            if (delerBolig && delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER) {
                if (ektemakeEllerSamboerUnder67År == true) {
                    ektemakeEllerSamboerUførFlyktning != null
                } else {
                    ektemakeEllerSamboerUførFlyktning == null
                }
            } else {
                true
            }

        fun getSatsgrunn() = when {
            !delerBolig -> Satsgrunn.ENSLIG
            delerBoligMed == Boforhold.DelerBoligMed.VOKSNE_BARN ->
                Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
            delerBoligMed == Boforhold.DelerBoligMed.ANNEN_VOKSEN ->
                Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
            delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER && ektemakeEllerSamboerUnder67År == false ->
                Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67
            delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER && ektemakeEllerSamboerUnder67År == true && ektemakeEllerSamboerUførFlyktning == false ->
                Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
            delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER && ektemakeEllerSamboerUførFlyktning == true ->
                Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
            else -> null
        }

        override fun erFerdigbehandlet(): Boolean = erGyldig()
        override fun erVilkårOppfylt(): Boolean = erGyldig()
        override fun avslagsgrunn(): Avslagsgrunn? = null
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
        override fun erGyldig() = true
        override fun erFerdigbehandlet() = true
        override fun erVilkårOppfylt() = true
        override fun avslagsgrunn(): Avslagsgrunn? = null

        data class Ektefelle(val fnr: Fnr) : EktefellePartnerSamboer()
        object IngenEktefelle : EktefellePartnerSamboer()
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
