package no.nav.su.se.bakover.domain.journal

import com.fasterxml.jackson.annotation.JsonValue

data class JournalpostId(
    private val value: String,
) {
    @JsonValue
    override fun toString() = value
}
