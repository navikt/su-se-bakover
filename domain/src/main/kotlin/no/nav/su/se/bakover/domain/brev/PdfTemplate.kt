package no.nav.su.se.bakover.domain.brev

/**
 * 1-1 mapping to templates defined by pdf-generator.
 */
sealed class PdfTemplate(
    private val templateName: String
) {
    fun name() = templateName

    object InnvilgetVedtak : PdfTemplate("vedtakInnvilgelse")
    object AvslagsVedtak : PdfTemplate("vedtakAvslag")
    object TrukketSøknad : PdfTemplate("søknadTrukket")
    object AvvistSøknadVedtak : PdfTemplate("avvistSøknadVedtak")
    object AvvistSøknadFritekst : PdfTemplate("avvistSøknadFritekst")
}
