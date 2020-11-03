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
    ForventetInntekt;

    companion object {
        fun isValid(s: String) =
            runBlocking {
                Either.catch { valueOf(s) }
                    .isRight()
            }
    }
}
