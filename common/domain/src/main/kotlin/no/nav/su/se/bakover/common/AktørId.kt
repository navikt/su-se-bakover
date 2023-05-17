package no.nav.su.se.bakover.common

import com.fasterxml.jackson.annotation.JsonValue

data class AktørId(
    private val aktørId: String,
) {
    @JsonValue
    override fun toString() = aktørId
}
