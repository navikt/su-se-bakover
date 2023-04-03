package no.nav.su.se.bakover.common

import com.fasterxml.jackson.annotation.JsonValue
import java.util.UUID

data class UUID30 constructor(
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
        fun fromUUID(uuid: UUID) = UUID30(uuid.toString().substring(0, 30))
        fun fromString(value: String) = UUID30(value)
    }

    override fun toString() = value
}
