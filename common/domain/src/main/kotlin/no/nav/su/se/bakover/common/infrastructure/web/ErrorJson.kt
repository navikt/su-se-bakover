package no.nav.su.se.bakover.common.infrastructure.web

import io.ktor.http.HttpStatusCode

data class ErrorJson(
    val message: String,
    /**
     * En maskintolkbar representasjon av feilmeldingen, unik innenfor sitt scope/endepunkt.
     * Ønskelig å holde koden statisk over tid (en del av APIet), men med mulighet for å endre innholdet i 'message'-feltet.
     * Format: snake_case
     * */
    val code: String,
) {
    fun tilResultat(httpStatusCode: HttpStatusCode): Resultat = httpStatusCode.errorJson(message, code)
}
