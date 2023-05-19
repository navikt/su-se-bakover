package no.nav.su.se.bakover.common.journal

import com.fasterxml.jackson.annotation.JsonValue

/**
 * TODO jah: Bør lage en Json-versjon, domenetyper skal ikke serialiseres/deserialiseres direkte.
 */
data class JournalpostId(
    private val value: String,
) {
    @JsonValue
    override fun toString() = value
}
