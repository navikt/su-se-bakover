package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonValue

data class AktørId(
    private val aktørId: String,
) {
    @JsonValue
    override fun toString() = aktørId
}
