package no.nav.su.se.bakover.domain.brev

sealed class Brevtype(
    private val pdfTemplate: PdfTemplate,
    private val brevTittel: String
) {
    fun template() = pdfTemplate.name()
    fun tittel() = brevTittel

    object InnvilgetVedtak : Brevtype(
        pdfTemplate = PdfTemplate.InnvilgetVedtak,
        brevTittel = "Vedtaksbrev for søknad om supplerende stønad"
    )

    object AvslagsVedtak : Brevtype(
        pdfTemplate = PdfTemplate.AvslagsVedtak,
        brevTittel = "Vedtaksbrev for søknad om supplerende stønad"
    )

    object TrukketSøknad : Brevtype(
        pdfTemplate = PdfTemplate.TrukketSøknad,
        brevTittel = "Bekrefter at søknad er trukket"
    )

    object AvvistSøknadVedtak : Brevtype(
        pdfTemplate = PdfTemplate.AvvistSøknadVedtak,
        brevTittel = "Vedtak om avvist søknad"
    )

    object AvvistSøknadFritekst : Brevtype(
        pdfTemplate = PdfTemplate.AvvistSøknadFritekst,
        brevTittel = "Info om avvist søknad"
    )
}
