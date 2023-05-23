package no.nav.su.se.bakover.domain.brev

sealed class PdfTemplate(
    private val pdfTemplate: PdfTemplateMedNavn,
    private val brevTittel: String,
) {
    fun template() = pdfTemplate.name()
    fun tittel() = brevTittel

    object InnvilgetVedtak : PdfTemplate(
        pdfTemplate = PdfTemplateMedNavn.InnvilgetVedtak,
        brevTittel = "Vedtaksbrev for søknad om supplerende stønad",
    )

    object AvslagsVedtak : PdfTemplate(
        pdfTemplate = PdfTemplateMedNavn.AvslagsVedtak,
        brevTittel = "Avslag supplerende stønad",
    )

    object TrukketSøknad : PdfTemplate(
        pdfTemplate = PdfTemplateMedNavn.TrukketSøknad,
        brevTittel = "Bekrefter at søknad er trukket",
    )

    object AvvistSøknadVedtak : PdfTemplate(
        pdfTemplate = PdfTemplateMedNavn.AvvistSøknadVedtak,
        brevTittel = "Søknaden din om supplerende stønad er avvist",
    )

    object AvvistSøknadFritekst : PdfTemplate(
        pdfTemplate = PdfTemplateMedNavn.AvvistSøknadFritekst,
        brevTittel = "Søknaden din om supplerende stønad er avvist",
    )

    sealed class Opphør(pdfTemplate: PdfTemplateMedNavn, brevTittel: String) : PdfTemplate(pdfTemplate, brevTittel) {
        object Opphørsvedtak : Opphør(
            pdfTemplate = PdfTemplateMedNavn.Opphørsvedtak,
            brevTittel = "Vedtak om opphør av supplerende stønad",
        )
    }

    object Forhåndsvarsel : PdfTemplate(
        pdfTemplate = PdfTemplateMedNavn.Forhåndsvarsel,
        brevTittel = "Varsel om at vi vil ta opp stønaden din til ny vurdering",
    )

    object ForhåndsvarselTilbakekreving : PdfTemplate(
        pdfTemplate = PdfTemplateMedNavn.ForhåndsvarselTilbakekreving,
        brevTittel = "Varsel om mulig tilbakekreving",
    )

    object InnkallingTilKontrollsamtale : PdfTemplate(
        pdfTemplate = PdfTemplateMedNavn.InnkallingTilKontrollsamtale,
        brevTittel = "Supplerende stønad ufør flyktning – innkalling til samtale",
    )

    object PåminnelseNyStønadsperiode : PdfTemplate(
        pdfTemplate = PdfTemplateMedNavn.PåminnelseNyStønadsperiode,
        brevTittel = "Påminnelse om ny stønadsperiode",
    )

    sealed class Revurdering(pdfTemplate: PdfTemplateMedNavn, brevTittel: String) : PdfTemplate(pdfTemplate, brevTittel) {
        object Inntekt : Revurdering(
            pdfTemplate = PdfTemplateMedNavn.Revurdering.Inntekt,
            brevTittel = "Vi har vurdert den supplerende stønaden din på nytt",
        )

        object MedTilbakekreving : Revurdering(
            pdfTemplate = PdfTemplateMedNavn.Revurdering.MedTilbakekreving,
            brevTittel = "Vi har vurdert den supplerende stønaden din på nytt og vil kreve tilbake penger",
        )

        object AvsluttRevurdering : Revurdering(
            pdfTemplate = PdfTemplateMedNavn.Revurdering.Avslutt,
            brevTittel = "Ikke grunnlag for revurdering",
        )
    }

    sealed class Klage(pdfTemplate: PdfTemplateMedNavn, brevTittel: String) : PdfTemplate(pdfTemplate, brevTittel) {
        object Oppretthold : Klage(
            pdfTemplate = PdfTemplateMedNavn.Klage.Oppretthold,
            brevTittel = "Oversendelsesbrev til klager",
        )

        object Avvist : Klage(
            pdfTemplate = PdfTemplateMedNavn.Klage.Avvist,
            brevTittel = "Avvist klage",
        )
    }

    data class Fritekst(val tittel: String) : PdfTemplate(PdfTemplateMedNavn.FritekstDokument, tittel)
}
