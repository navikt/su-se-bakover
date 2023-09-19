package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt

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

data class RåTilbakekrevingsvedtakForsendelse(
    private val requestXml: String,
    private val tidspunkt: Tidspunkt,
    private val responseXml: String,
) {
    fun originalRequest(): String = requestXml
    fun tidspunkt(): Tidspunkt = tidspunkt
    fun originalRespons(): String = responseXml
}
