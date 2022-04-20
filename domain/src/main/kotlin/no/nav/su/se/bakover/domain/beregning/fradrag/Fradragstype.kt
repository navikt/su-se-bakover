package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import arrow.core.left
import arrow.core.right

data class Fradragstype(
    val type: F,
    val spesifisertType: String? = null,
) {
    init {
        if (type == F.Annet && spesifisertType == null) throw IllegalArgumentException("Typen må spesifiseres")
        if (type != F.Annet && spesifisertType != null) throw IllegalArgumentException("Typen skal kun spesifieres dersom den er 'Annet'")
    }
}

// TODO: navn
enum class F {
    Alderspensjon,
    Annet,
    Arbeidsavklaringspenger,
    Arbeidsinntekt,
    AvkortingUtenlandsopphold,

    // AFP
    AvtalefestetPensjon,
    AvtalefestetPensjonPrivat,
    BidragEtterEkteskapsloven,
    Dagpenger,
    ForventetInntekt,
    Gjenlevendepensjon,
    Introduksjonsstønad,
    Kapitalinntekt,
    Kontantstøtte,
    Kvalifiseringsstønad,
    NAVytelserTilLivsopphold,
    OffentligPensjon,
    PrivatPensjon,
    Sosialstønad,
    SupplerendeStønad,
    Sykepenger,
    Uføretrygd,

    /**
     *  Resulting type of the operation that calculates EPS fradrag to be included in brukers beregning.
     *  Represents a "mixed bag" of fradrag that in total exceeds the respecive limits given by §5 and §6.
     *  Not to be used for input-operations (i.e. from frontend).
     */
    BeregnetFradragEPS,
    UnderMinstenivå;

    companion object {
        fun tryParse(value: String): Either<UgyldigFradragstype, F> {
            return values().firstOrNull { it.name == value }?.right() ?: UgyldigFradragstype.left()
        }
    }

    object UgyldigFradragstype
}
