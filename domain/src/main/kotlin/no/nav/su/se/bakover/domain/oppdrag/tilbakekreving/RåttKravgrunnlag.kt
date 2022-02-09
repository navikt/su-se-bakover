package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

@JvmInline
value class RåttKravgrunnlag private constructor(
    val melding: String,
) {
    companion object {
        operator fun invoke(
            xmlMelding: String,
        ): RåttKravgrunnlag {
            return RåttKravgrunnlag(xmlMelding)
        }
    }
}

@JvmInline
value class RåttTilbakekrevingsvedtak private constructor(
    val melding: String,
) {
    companion object {
        operator fun invoke(
            xmlMelding: String,
        ): RåttTilbakekrevingsvedtak {
            return RåttTilbakekrevingsvedtak(xmlMelding)
        }
    }
}
