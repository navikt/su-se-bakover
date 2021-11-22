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

    object Opphørsvedtak : BrevTemplate(
        pdfTemplate = PdfTemplate.Opphørsvedtak,
        brevTittel = "Vedtak om opphør av supplerende stønad"
    )

    object VedtakIngenEndring : BrevTemplate(
        pdfTemplate = PdfTemplate.VedtakIngenEndring,
        brevTittel = "Ny behandling førte ikke til endring av stønaden"
    )

    object Forhåndsvarsel : BrevTemplate(
        pdfTemplate = PdfTemplate.Forhåndsvarsel,
        brevTittel = "Varsel om at vi vil ta opp stønaden din til ny vurdering"
    )

    sealed class Revurdering(pdfTemplate: PdfTemplate, brevTittel: String) : BrevTemplate(pdfTemplate, brevTittel) {
        object Inntekt : Revurdering(
            pdfTemplate = PdfTemplate.Revurdering.Inntekt,
            brevTittel = "Vi har vurdert den supplerende stønaden din på nytt",
        )

        object AvsluttRevurdering : Revurdering(
            pdfTemplate = PdfTemplate.Revurdering.Avslutt,
            brevTittel = "Ikke grunnlag for revurdering",
        )
    }

    sealed class Klage(pdfTemplate: PdfTemplate, brevTittel: String) : BrevTemplate(pdfTemplate, brevTittel) {
        object Oppretthold : Klage(
            pdfTemplate = PdfTemplate.Klage.Oppretthold,
            brevTittel = "",
        )
    }
}
