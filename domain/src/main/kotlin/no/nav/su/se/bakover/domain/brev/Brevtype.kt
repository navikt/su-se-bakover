package no.nav.su.se.bakover.domain.brev

sealed class Brevtype(
    private val pdfTemplate: PdfTemplate
) {
    fun template() = pdfTemplate.name()

    object InnvilgetVedtak : Brevtype(pdfTemplate = PdfTemplate.InnvilgetVedtak)
    object AvslagsVedtak : Brevtype(pdfTemplate = PdfTemplate.AvslagsVedtak)
    object TrukketSøknad : Brevtype(pdfTemplate = PdfTemplate.TrukketSøknad)
    object AvvistSøknadVedtak : Brevtype(pdfTemplate = PdfTemplate.AvvistSøknadVedtak)
    object AvvistSøknadFritekst : Brevtype(pdfTemplate = PdfTemplate.AvvistSøknadFritekst)
}
