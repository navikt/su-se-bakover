package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import kotlinx.coroutines.runBlocking

enum class Fradragstype {
    NAVytelserTilLivsopphold,
    Arbeidsinntekt,
    OffentligPensjon,
    PrivatPensjon,
    Sosialstønad,
    Kontantstøtte,
    Introduksjonsstønad,
    Kvalifiseringsstønad,
    BidragEtterEkteskapsloven,
    Kapitalinntekt,
    ForventetInntekt,

    /**
     *  Resulting type of the operation that calculates EPS fradrag to be included in brukers beregning.
     *  Represents a "mixed bag" of fradrag that in total exceeds the respecive limits given by §5 and §6.
     *  Not to be used for input-operations (i.e. from frontend).
     */
    BeregnetFradragEPS;

    companion object {
        fun isValid(s: String) =
            runBlocking {
                Either.catch { valueOf(s) }
                    .isRight()
            }
    }
}
