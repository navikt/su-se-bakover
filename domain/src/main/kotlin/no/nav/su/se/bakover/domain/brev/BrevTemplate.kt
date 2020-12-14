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
        brevTittel = "Søknaden din om supplerende stønad er avvist"
    )

    object AvvistSøknadFritekst : BrevTemplate(
        pdfTemplate = PdfTemplate.AvvistSøknadFritekst,
        brevTittel = "Søknaden din om supplerende stønad er avvist"
    )
}
