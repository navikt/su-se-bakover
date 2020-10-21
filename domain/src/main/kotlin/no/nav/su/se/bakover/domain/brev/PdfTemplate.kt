package no.nav.su.se.bakover.domain.brev

sealed class PdfTemplate(
    private val templateName: String
) {
    override fun toString(): String = templateName
    object VedtakAvslag : PdfTemplate("vedtakAvslag")
    object VedtakInnvilget : PdfTemplate("vedtakInnvilgelse")
    object TrukketSøknad : PdfTemplate("søknadTrukket")
    object Søknad : PdfTemplate("soknad")
}
