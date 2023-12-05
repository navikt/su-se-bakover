package no.nav.su.se.bakover.common.domain.config

data class TilbakekrevingConfig(
    val mq: Mq,
    val soap: Soap,
    val serviceUserConfig: ServiceUserConfig,
) {
    data class Mq(
        val mottak: String,
    ) {
        // Tillater extension functions.
        companion object
    }

    data class Soap(
        val url: String,
        val stsSoapUrl: String,
    ) {
        // Tillater extension functions.
        companion object
    }

    // Tillater extension functions.
    companion object
}
