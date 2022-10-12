package no.nav.su.se.bakover.common

@JvmInline
value class CorrelationId(val value: String) {
    override fun toString() = value
}
