package no.nav.su.se.bakover.hendelse.domain

import java.util.UUID

/**
 * Representerer en hendelse unikt på tvers av alle hendelser.
 */
@JvmInline
value class HendelseId private constructor(val value: UUID) {
    companion object {
        fun generer(): HendelseId = HendelseId(UUID.randomUUID())

        fun fromString(value: String): HendelseId = HendelseId(UUID.fromString(value))
        fun fromUUID(value: UUID): HendelseId = HendelseId(value)
    }

    override fun toString() = value.toString()
}
