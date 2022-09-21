package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue

// https://www.skatteetaten.no/person/folkeregister/fodsel-og-navnevalg/barn-fodt-i-norge/fodselsnummer/#:~:text=De%20seks%20f%C3%B8rste%20sifrene%20viser,fem%20siste%20sifrene%20i%20f%C3%B8dselsnummeret.

data class Fnr
@JsonCreator(mode = DELEGATING)
constructor(private val fnr: String) {

    private val fnrPattern = Regex("[0-9]{11}")

    init {
        validate(fnr)
    }

    @JsonValue
    override fun toString(): String = fnr

    private fun validate(fnr: String) {
        if (!fnr.matches(fnrPattern)) throw UgyldigFnrException(fnr)
    }

    // tilgjengeliggjør for test
    companion object
}

class UgyldigFnrException(fnr: String?) : RuntimeException("Ugyldig fnr: $fnr")
