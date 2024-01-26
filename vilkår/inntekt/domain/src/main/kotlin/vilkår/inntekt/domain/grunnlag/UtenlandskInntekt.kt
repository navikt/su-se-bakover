package vilkår.inntekt.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right

data class UtenlandskInntekt private constructor(
    val beløpIUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Double,
) {
    companion object {
        fun create(beløpIUtenlandskValuta: Int, valuta: String, kurs: Double): UtenlandskInntekt {
            return tryCreate(
                beløpIUtenlandskValuta,
                valuta,
                kurs,
            ).getOrElse { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(
            beløpIUtenlandskValuta: Int,
            valuta: String,
            kurs: Double,
        ): Either<UgyldigUtenlandskInntekt, UtenlandskInntekt> {
            if (beløpIUtenlandskValuta < 0) return UgyldigUtenlandskInntekt.BeløpKanIkkeVæreNegativ.left()
            if (valuta.isBlank()) return UgyldigUtenlandskInntekt.ValutaMåFyllesUt.left()
            if (kurs < 0) return UgyldigUtenlandskInntekt.KursKanIkkeVæreNegativ.left()

            return UtenlandskInntekt(beløpIUtenlandskValuta, valuta, kurs).right()
        }
    }

    sealed class UgyldigUtenlandskInntekt {
        data object BeløpKanIkkeVæreNegativ : UgyldigUtenlandskInntekt()
        data object ValutaMåFyllesUt : UgyldigUtenlandskInntekt()
        data object KursKanIkkeVæreNegativ : UgyldigUtenlandskInntekt()
    }
}
