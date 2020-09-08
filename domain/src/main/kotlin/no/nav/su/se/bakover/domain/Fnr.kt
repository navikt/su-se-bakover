package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue

data class Fnr @JsonCreator(mode = DELEGATING) constructor(
    @JsonValue
    val fnr: String
) {
    private val fnrPattern = Regex("[0-9]{11}")

    init {
        validate(fnr)
    }

    override fun toString(): String = fnr

    private fun validate(fnr: String) {
        if (!fnr.matches(fnrPattern)) throw UgyldigFnrException(fnr)
    }
}

class UgyldigFnrException(fnr: String?) : RuntimeException("Ugyldig fnr: $fnr")
