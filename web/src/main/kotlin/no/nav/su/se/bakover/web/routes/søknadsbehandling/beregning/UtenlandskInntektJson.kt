package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt

internal data class UtenlandskInntektJson(
    val beløpIUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Double,
) {
    fun toUtenlandskInntekt(): Either<Resultat, UtenlandskInntekt> {
        return UtenlandskInntekt.tryCreate(
            beløpIUtenlandskValuta = beløpIUtenlandskValuta,
            valuta = valuta,
            kurs = kurs,
        ).mapLeft {
            when (it) {
                UtenlandskInntekt.UgyldigUtenlandskInntekt.BeløpKanIkkeVæreNegativ -> HttpStatusCode.BadRequest.errorJson(
                    "Beløpet kan ikke være negativt",
                    "utenlandsk_inntekt_negativt_beløp",
                )
                UtenlandskInntekt.UgyldigUtenlandskInntekt.ValutaMåFyllesUt -> HttpStatusCode.BadRequest.errorJson(
                    "Valuta må fylles ut",
                    "utenlandsk_inntekt_mangler_valuta",
                )
                UtenlandskInntekt.UgyldigUtenlandskInntekt.KursKanIkkeVæreNegativ -> HttpStatusCode.BadRequest.errorJson(
                    "Kursen kan ikke være negativ",
                    "utenlandsk_inntekt_negativ_kurs",
                )
            }
        }
    }

    companion object {
        fun UtenlandskInntekt.toJson(): UtenlandskInntektJson {
            return UtenlandskInntektJson(beløpIUtenlandskValuta, valuta, kurs)
        }
    }
}
