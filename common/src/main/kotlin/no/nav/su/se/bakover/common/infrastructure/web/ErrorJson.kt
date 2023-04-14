package no.nav.su.se.bakover.common.infrastructure.web

import com.fasterxml.jackson.annotation.JsonInclude
import io.ktor.http.HttpStatusCode

data class ErrorJson(
    val message: String,
    /**
     * En maskintolkbar representasjon av feilmeldingen, unik innenfor sitt scope/endepunkt.
     * Ønskelig å holde koden statisk over tid (en del av APIet), men med mulighet for å endre innholdet i 'message'-feltet.
     * Format: snake_case
     * I utgangspunktet nullable for å være bakoverkompatibel.
     * */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val code: String? = null,
) {
    /**
     * Kaster exception dersom denne brukes på en ErrorJson uten code
     */
    fun tilResultat(httpStatusCode: HttpStatusCode): Resultat {
        if (code == null) throw IllegalArgumentException("Prøvde å gjøre en errorJson til resultat uten en error code. Error code er påkrevd")
        return httpStatusCode.errorJson(message, code)
    }
}
