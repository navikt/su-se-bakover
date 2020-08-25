package no.nav.su.se.bakover.common

import java.util.UUID

data class UUID30(
    val value: String
) {
    init {
        require(value.trim().length == 30)
    }

    companion object {
        fun randomUUID() = fromUUID(UUID.randomUUID())
        fun fromUUID(uuid: UUID) = UUID30(uuid.toString().substring(0, 30))
        fun fromString(value: String) = UUID30(value)
    }

    override fun toString() = value
}
