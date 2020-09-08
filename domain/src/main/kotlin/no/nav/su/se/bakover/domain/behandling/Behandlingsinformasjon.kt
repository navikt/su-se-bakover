package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.dto.DtoConvertable

data class Behandlingsinformasjon(
    private var uførhet: Uførhet? = null,
    private var flyktning: Flyktning? = null,
    private var lovligOpphold: LovligOpphold? = null,
    private var fastOppholdINorge: FastOppholdINorge? = null,
    private var oppholdIUtlandet: OppholdIUtlandet? = null,
    private var formue: Formue? = null,
    private var personligOppmøte: PersonligOppmøte? = null,
    private var sats: Sats? = null
) : DtoConvertable<BehandlingsinformasjonDto> {
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

    override fun toDto(): BehandlingsinformasjonDto =
        BehandlingsinformasjonDto(
            uførhet?.toDto(),
            flyktning?.toDto(),
            lovligOpphold?.toDto(),
            fastOppholdINorge?.toDto(),
            oppholdIUtlandet?.toDto(),
            formue?.toDto(),
            personligOppmøte?.toDto(),
            sats?.toDto()
        )

    abstract class Base {
        abstract fun isValid(): Boolean
        abstract fun isComplete(): Boolean
    }

    data class Uførhet(
        val status: Status,
        val uføregrad: Int?,
        val forventetInntekt: Int?
    ) : Base(), DtoConvertable<Uførhet.Dto> {
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

        override fun toDto(): Dto = Dto(status, uføregrad, forventetInntekt)

        data class Dto(
            val status: Status,
            val uføregrad: Int?,
            val forventetInntekt: Int?
        )
    }

    data class Flyktning(
        val status: Status,
        val begrunnelse: String?
    ) : Base(), DtoConvertable<Flyktning.Dto> {
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

        override fun toDto(): Dto = Dto(status, begrunnelse)

        data class Dto(
            val status: Status,
            val begrunnelse: String?
        )
    }

    data class LovligOpphold(
        val status: Status,
        val begrunnelse: String?
    ) : Base(), DtoConvertable<LovligOpphold.Dto> {
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

        override fun toDto(): Dto = Dto(status, begrunnelse)

        class Dto(
            val status: Status,
            val begrunnelse: String?
        )
    }

    data class FastOppholdINorge(
        val status: Status,
        val begrunnelse: String?
    ) : Base(), DtoConvertable<FastOppholdINorge.Dto> {
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

        override fun toDto(): Dto = Dto(status, begrunnelse)

        class Dto(
            val status: Status,
            val begrunnelse: String?
        )
    }

    data class OppholdIUtlandet(
        val status: Status,
        val begrunnelse: String?
    ) : Base(), DtoConvertable<OppholdIUtlandet.Dto> {
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

        override fun toDto(): Dto = Dto(status, begrunnelse)

        class Dto(
            val status: Status,
            val begrunnelse: String?
        )
    }

    data class Formue(
        val status: Status,
        val verdiIkkePrimærbolig: Int?,
        val verdiKjøretøy: Int?,
        val innskudd: Int?,
        val verdipapir: Int?,
        val pengerSkyldt: Int?,
        val kontanter: Int?,
        val depositumskonto: Int?
    ) : Base(), DtoConvertable<Formue.Dto> {
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

        override fun toDto(): Dto = Dto(
            status,
            verdiIkkePrimærbolig,
            verdiKjøretøy,
            innskudd,
            verdipapir,
            pengerSkyldt,
            kontanter,
            depositumskonto
        )

        data class Dto(
            val status: Status,
            val verdiIkkePrimærbolig: Int?,
            val verdiKjøretøy: Int?,
            val innskudd: Int?,
            val verdipapir: Int?,
            val pengerSkyldt: Int?,
            val kontanter: Int?,
            val depositumskonto: Int?
        )
    }

    data class PersonligOppmøte(
        val status: Status,
        val begrunnelse: String?
    ) : Base(), DtoConvertable<PersonligOppmøte.Dto> {
        enum class Status {
            MøttPersonlig,
            Verge,
            FullmektigMedLegeattest,
            FullmektigUtenLegeattest,
            IkkeMøttOpp
        }

        override fun isValid(): Boolean = true
        override fun isComplete(): Boolean = true

        override fun toDto(): Dto = Dto(status, begrunnelse)

        data class Dto(
            val status: Status,
            val begrunnelse: String?
        )
    }

    data class Sats(
        val delerBolig: Boolean,
        val delerBoligMed: Boforhold.DelerBoligMed?,
        val ektemakeEllerSamboerUnder67År: Boolean?,
        val ektemakeEllerSamboerUførFlyktning: Boolean?,
        val begrunnelse: String?
    ) : Base(), DtoConvertable<Sats.Dto> {
        fun utledSats() =
            no.nav.su.se.bakover.domain.beregning.Sats.HØY

        override fun isValid(): Boolean =
            if (delerBolig) {
                if (delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER) {
                    ektemakeEllerSamboerUnder67År != null &&
                        ektemakeEllerSamboerUførFlyktning != null
                } else {
                    false
                }
            } else {
                true
            }

        override fun isComplete(): Boolean = true

        override fun toDto(): Dto =
            Dto(
                delerBolig,
                delerBoligMed,
                ektemakeEllerSamboerUnder67År,
                ektemakeEllerSamboerUførFlyktning,
                begrunnelse
            )

        data class Dto(
            val delerBolig: Boolean,
            val delerBoligMed: Boforhold.DelerBoligMed?,
            val ektemakeEllerSamboerUnder67År: Boolean?,
            val ektemakeEllerSamboerUførFlyktning: Boolean?,
            val begrunnelse: String?
        )
    }
}

data class BehandlingsinformasjonDto(
    val uførhet: Behandlingsinformasjon.Uførhet.Dto?,
    val flyktning: Behandlingsinformasjon.Flyktning.Dto?,
    val lovligOpphold: Behandlingsinformasjon.LovligOpphold.Dto?,
    val fastOppholdINorge: Behandlingsinformasjon.FastOppholdINorge.Dto?,
    val oppholdIUtlandet: Behandlingsinformasjon.OppholdIUtlandet.Dto?,
    val formue: Behandlingsinformasjon.Formue.Dto?,
    val personligOppmøte: Behandlingsinformasjon.PersonligOppmøte.Dto?,
    val sats: Behandlingsinformasjon.Sats.Dto?
)
