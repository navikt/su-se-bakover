package no.nav.su.se.bakover.web.routes.regulering

/**
 * Brukes dersom CSV sendes inn som text
 */
data class SupplementBody(
    val csv: String,
)
