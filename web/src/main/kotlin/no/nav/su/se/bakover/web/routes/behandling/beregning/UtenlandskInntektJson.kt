package no.nav.su.se.bakover.web.routes.behandling.beregning

import arrow.core.Either
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt.*

data class UtenlandskInntektJson(
    val beløpIUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Double
) {
    fun toUtlandskInntekt() : Either<UgyldigUtenlandskInntekt, UtenlandskInntekt> {
        return UtenlandskInntekt.tryCreate(
            beløpIUtenlandskValuta = beløpIUtenlandskValuta,
            valuta = valuta,
            kurs = kurs
        )
    }

    companion object {
        fun UtenlandskInntekt.toJson(): UtenlandskInntektJson {
            return UtenlandskInntektJson(beløpIUtenlandskValuta, valuta, kurs)
        }
    }
}
