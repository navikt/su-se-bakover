package no.nav.su.se.bakover.domain.oppdrag

data class Utbetalingsrequest(
    val value: String,
) {
    override fun toString() = value
}
