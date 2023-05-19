package no.nav.su.se.bakover.common.person

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue

/**
 * TODO jah: Bør lage en Json-versjon, domenetyper skal ikke serialiseres/deserialiseres direkte.
 */
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
    companion object {
        fun tryCreate(fnr: String): Fnr? {
            return try {
                Fnr(fnr)
            } catch (e: UgyldigFnrException) {
                null
            }
        }
    }
}

class UgyldigFnrException(fnr: String?) : RuntimeException("Ugyldig fnr: $fnr")
