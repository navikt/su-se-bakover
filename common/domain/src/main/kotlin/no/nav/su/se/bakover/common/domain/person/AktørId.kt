package no.nav.su.se.bakover.common.person

import com.fasterxml.jackson.annotation.JsonValue

/**
 * TODO jah: Bør lage en Json-versjon, domenetyper skal ikke serialiseres/deserialiseres direkte.
 */
data class AktørId(
    private val aktørId: String,
) {
    @JsonValue
    override fun toString() = aktørId
}
