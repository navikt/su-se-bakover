package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.message

internal data class UtenlandskInntektJson(
    val beløpIUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Double
) {
    fun toUtlandskInntekt(): Either<Resultat, UtenlandskInntekt> {
        return UtenlandskInntekt.tryCreate(
            beløpIUtenlandskValuta = beløpIUtenlandskValuta,
            valuta = valuta,
            kurs = kurs
        ).mapLeft {
            when (it) {
                UtenlandskInntekt.UgyldigUtenlandskInntekt.BeløpKanIkkeVæreNegativ -> HttpStatusCode.BadRequest.message("Beløpet kan ikke være negativt")
                UtenlandskInntekt.UgyldigUtenlandskInntekt.ValutaMåFyllesUt -> HttpStatusCode.BadRequest.message("Valuta må fylles ut")
                UtenlandskInntekt.UgyldigUtenlandskInntekt.KursKanIkkeVæreNegativ -> HttpStatusCode.BadRequest.message("Kursen kan ikke være negativ")
            }
        }
    }

    companion object {
        fun UtenlandskInntekt.toJson(): UtenlandskInntektJson {
            return UtenlandskInntektJson(beløpIUtenlandskValuta, valuta, kurs)
        }
    }
}
