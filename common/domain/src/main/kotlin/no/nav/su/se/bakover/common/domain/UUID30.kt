package no.nav.su.se.bakover.common

import com.fasterxml.jackson.annotation.JsonValue
import java.util.UUID

/**
 * TODO jah: Bør lage en Json-versjon, domenetyper skal ikke serialiseres/deserialiseres direkte.
 */
data class UUID30(
    @JsonValue
    val value: String,
) {
    init {
        require(value.trim().length == 30) {
            "Kunne ikke lage en UUID30 fra $value"
        }
    }

    companion object {
        fun randomUUID() = fromUUID(UUID.randomUUID())
        private fun fromUUID(uuid: UUID) = UUID30(uuid.toString().substring(0, 30))
        fun fromString(value: String) = UUID30(value)
    }

    override fun toString() = value
}
