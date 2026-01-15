package no.nav.su.se.bakover.common.domain.config

data class TilbakekrevingConfig(
    val mq: Mq,
    val serviceUserConfig: ServiceUserConfig, // Blir brukt her createJmsContextWithTimeout for jms
) {
    data class Mq(
        val mottak: String,
    ) {
        // Tillater extension functions.
        companion object
    }

    // Tillater extension functions.
    companion object
}
