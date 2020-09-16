package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.Boforhold

data class Behandlingsinformasjon(
    val uførhet: Uførhet? = null,
    val flyktning: Flyktning? = null,
    val lovligOpphold: LovligOpphold? = null,
    val fastOppholdINorge: FastOppholdINorge? = null,
    val oppholdIUtlandet: OppholdIUtlandet? = null,
    val formue: Formue? = null,
    val personligOppmøte: PersonligOppmøte? = null,
    val sats: Sats? = null
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
        sats = b.sats ?: this.sats
    )

    fun isComplete() =
        listOf(
            uførhet,
            flyktning,
            lovligOpphold,
            fastOppholdINorge,
            oppholdIUtlandet,
            formue,
            personligOppmøte,
            sats
        ).all { it != null && it.isValid() && it.isComplete() }

    fun isInnvilget() =
        isComplete() &&
            listOf(
                uførhet?.status == Uførhet.Status.VilkårOppfylt,
                flyktning?.status == Flyktning.Status.VilkårOppfylt,
                lovligOpphold?.status == LovligOpphold.Status.VilkårOppfylt,
                fastOppholdINorge?.status == FastOppholdINorge.Status.VilkårOppfylt,
                oppholdIUtlandet?.status == OppholdIUtlandet.Status.SkalHoldeSegINorge,
                formue?.status == Formue.Status.Ok,
                personligOppmøte?.status.let {
                    it == PersonligOppmøte.Status.FullmektigMedLegeattest ||
                        it == PersonligOppmøte.Status.MøttPersonlig ||
                        it == PersonligOppmøte.Status.Verge
                }
            ).all { it }

    fun isAvslag() =
        listOf(
            uførhet?.status == Uførhet.Status.VilkårIkkeOppfylt,
            flyktning?.status == Flyktning.Status.VilkårIkkeOppfylt,
            lovligOpphold?.status == LovligOpphold.Status.VilkårIkkeOppfylt,
            fastOppholdINorge?.status == FastOppholdINorge.Status.VilkårIkkeOppfylt,
            oppholdIUtlandet?.status == OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet,
            personligOppmøte?.status.let {
                it == PersonligOppmøte.Status.FullmektigUtenLegeattest ||
                    it == PersonligOppmøte.Status.IkkeMøttOpp
            }
        ).any { it }

    abstract class Base {
        abstract fun isValid(): Boolean
        abstract fun isComplete(): Boolean
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

        override fun isValid(): Boolean =
            when (status) {
                Status.VilkårOppfylt -> uføregrad != null && forventetInntekt != null
                Status.VilkårIkkeOppfylt -> uføregrad == null && forventetInntekt == null
                Status.HarUføresakTilBehandling -> uføregrad != null && uføregrad > 0 && forventetInntekt != null && forventetInntekt > 0
            }

        override fun isComplete(): Boolean = isValid()
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

        override fun isValid(): Boolean =
            when (status) {
                Status.VilkårOppfylt -> begrunnelse == null
                Status.VilkårIkkeOppfylt -> begrunnelse == null
                Status.Uavklart -> begrunnelse != null
            }

        override fun isComplete(): Boolean = status != Status.Uavklart
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

        override fun isValid(): Boolean =
            when (status) {
                Status.VilkårOppfylt -> begrunnelse == null
                Status.VilkårIkkeOppfylt -> begrunnelse == null
                Status.Uavklart -> begrunnelse != null
            }

        override fun isComplete(): Boolean = status != Status.Uavklart
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

        override fun isValid(): Boolean =
            when (status) {
                Status.VilkårOppfylt -> begrunnelse == null
                Status.VilkårIkkeOppfylt -> begrunnelse == null
                Status.Uavklart -> begrunnelse != null
            }

        override fun isComplete(): Boolean = status != Status.Uavklart
    }

    data class OppholdIUtlandet(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            SkalVæreMerEnn90DagerIUtlandet,
            SkalHoldeSegINorge
        }

        override fun isValid(): Boolean =
            when (status) {
                Status.SkalVæreMerEnn90DagerIUtlandet -> begrunnelse != null
                Status.SkalHoldeSegINorge -> begrunnelse == null
            }

        override fun isComplete(): Boolean = true
    }

    data class Formue(
        val status: Status,
        val verdiIkkePrimærbolig: Int?,
        val verdiKjøretøy: Int?,
        val innskudd: Int?,
        val verdipapir: Int?,
        val pengerSkyldt: Int?,
        val kontanter: Int?,
        val depositumskonto: Int?,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            Ok,
            MåInnhenteMerInformasjon
        }

        override fun isValid(): Boolean =
            when (status) {
                Status.Ok ->
                    verdiIkkePrimærbolig != null &&
                        verdiKjøretøy !== null &&
                        innskudd !== null &&
                        verdipapir !== null &&
                        pengerSkyldt !== null &&
                        kontanter !== null &&
                        depositumskonto !== null
                Status.MåInnhenteMerInformasjon -> true
            }

        override fun isComplete(): Boolean = status != Status.MåInnhenteMerInformasjon
    }

    data class PersonligOppmøte(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            MøttPersonlig,
            Verge,
            FullmektigMedLegeattest,
            FullmektigUtenLegeattest,
            IkkeMøttOpp
        }

        override fun isValid(): Boolean = true
        override fun isComplete(): Boolean = true
    }

    data class Sats(
        val delerBolig: Boolean,
        val delerBoligMed: Boforhold.DelerBoligMed?,
        val ektemakeEllerSamboerUnder67År: Boolean?,
        val ektemakeEllerSamboerUførFlyktning: Boolean?,
        val begrunnelse: String?
    ) : Base() {
        fun utledSats() =
            if (!delerBolig) {
                no.nav.su.se.bakover.domain.beregning.Sats.HØY
            } else {
                // Vi gjør en del null assertions her for at logikken ikke skal bli så vanskelig å følge
                // Det _bør_ være trygt fordi gyldighet av objektet skal bli sjekket andre plasser
                when (delerBoligMed!!) {
                    Boforhold.DelerBoligMed.VOKSNE_BARN ->
                        no.nav.su.se.bakover.domain.beregning.Sats.LAV
                    Boforhold.DelerBoligMed.ANNEN_VOKSEN ->
                        no.nav.su.se.bakover.domain.beregning.Sats.LAV
                    Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER ->
                        if (!ektemakeEllerSamboerUnder67År!!) {
                            no.nav.su.se.bakover.domain.beregning.Sats.LAV
                        } else {
                            if (ektemakeEllerSamboerUførFlyktning!!) {
                                no.nav.su.se.bakover.domain.beregning.Sats.LAV
                            } else {
                                no.nav.su.se.bakover.domain.beregning.Sats.HØY
                            }
                        }
                }
            }

        override fun isValid(): Boolean =
            if (delerBolig && delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER) {
                if (ektemakeEllerSamboerUnder67År == true) {
                    ektemakeEllerSamboerUførFlyktning != null
                } else {
                    ektemakeEllerSamboerUførFlyktning == null
                }
            } else {
                true
            }

        override fun isComplete(): Boolean = true
    }
}
