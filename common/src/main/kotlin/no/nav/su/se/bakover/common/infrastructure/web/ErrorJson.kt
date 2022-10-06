package no.nav.su.se.bakover.common.infrastructure.web

import com.fasterxml.jackson.annotation.JsonInclude

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
)
