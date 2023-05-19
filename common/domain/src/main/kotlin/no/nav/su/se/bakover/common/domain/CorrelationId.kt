package no.nav.su.se.bakover.common

import java.util.UUID

/**
 * Beware: This is the preferred header in this repo, but this is not consistent through all NAVs APIs.
 */
const val CorrelationIdHeader = "X-Correlation-ID"

@JvmInline
value class CorrelationId(val value: String) {
    override fun toString() = value

    companion object {
        fun generate(): CorrelationId = CorrelationId(UUID.randomUUID().toString())
    }
}
