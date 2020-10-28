package no.nav.su.se.bakover.domain.brev

sealed class BrevTemplate(
    private val pdfTemplate: PdfTemplate,
    private val brevTittel: String
) {
    fun template() = pdfTemplate.name()
    fun tittel() = brevTittel

    object InnvilgetVedtak : BrevTemplate(
        pdfTemplate = PdfTemplate.InnvilgetVedtak,
        brevTittel = "Vedtaksbrev for søknad om supplerende stønad"
    )

    object AvslagsVedtak : BrevTemplate(
        pdfTemplate = PdfTemplate.AvslagsVedtak,
        brevTittel = "Vedtaksbrev for søknad om supplerende stønad"
    )

    object TrukketSøknad : BrevTemplate(
        pdfTemplate = PdfTemplate.TrukketSøknad,
        brevTittel = "Bekrefter at søknad er trukket"
    )

    object AvvistSøknadVedtak : BrevTemplate(
        pdfTemplate = PdfTemplate.AvvistSøknadVedtak,
        brevTittel = "Vedtak om avvist søknad"
    )

    object AvvistSøknadFritekst : BrevTemplate(
        pdfTemplate = PdfTemplate.AvvistSøknadFritekst,
        brevTittel = "Info om avvist søknad"
    )
}
