package no.nav.su.se.bakover.domain.brev

sealed class PdfTemplateMedDokumentNavn(
    private val pdfTemplate: PdfTemplate,
    /**
     * navnet vi bruker i gosys og journalføring
     */
    private val dokumentNavn: String,
) {
    fun template() = pdfTemplate.name()
    fun tittel() = dokumentNavn

    object InnvilgetVedtak : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.InnvilgetVedtak,
        dokumentNavn = "Vedtaksbrev for søknad om supplerende stønad",
    )

    object AvslagsVedtak : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.AvslagsVedtak,
        dokumentNavn = "Avslag supplerende stønad",
    )

    object TrukketSøknad : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.TrukketSøknad,
        dokumentNavn = "Bekrefter at søknad er trukket",
    )

    object AvvistSøknadVedtak : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.AvvistSøknadVedtak,
        dokumentNavn = "Søknaden din om supplerende stønad er avvist",
    )

    object AvvistSøknadFritekst : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.AvvistSøknadFritekst,
        dokumentNavn = "Søknaden din om supplerende stønad er avvist",
    )

    sealed class Opphør(pdfTemplate: PdfTemplate, brevTittel: String) : PdfTemplateMedDokumentNavn(pdfTemplate, brevTittel) {
        object Opphørsvedtak : Opphør(
            pdfTemplate = PdfTemplate.Opphørsvedtak,
            brevTittel = "Vedtak om opphør av supplerende stønad",
        )
    }

    object Forhåndsvarsel : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.Forhåndsvarsel,
        dokumentNavn = "Varsel om at vi vil ta opp stønaden din til ny vurdering",
    )

    object ForhåndsvarselTilbakekreving : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.ForhåndsvarselTilbakekreving,
        dokumentNavn = "Varsel om mulig tilbakekreving",
    )

    object InnkallingTilKontrollsamtale : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.InnkallingTilKontrollsamtale,
        dokumentNavn = "Supplerende stønad ufør flyktning – innkalling til samtale",
    )

    object PåminnelseNyStønadsperiode : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.PåminnelseNyStønadsperiode,
        dokumentNavn = "Påminnelse om ny stønadsperiode",
    )

    sealed class Revurdering(pdfTemplate: PdfTemplate, brevTittel: String) : PdfTemplateMedDokumentNavn(pdfTemplate, brevTittel) {
        object Inntekt : Revurdering(
            pdfTemplate = PdfTemplate.Revurdering.Inntekt,
            brevTittel = "Vi har vurdert den supplerende stønaden din på nytt",
        )

        object MedTilbakekreving : Revurdering(
            pdfTemplate = PdfTemplate.Revurdering.MedTilbakekreving,
            brevTittel = "Vi har vurdert den supplerende stønaden din på nytt og vil kreve tilbake penger",
        )

        object AvsluttRevurdering : Revurdering(
            pdfTemplate = PdfTemplate.Revurdering.Avslutt,
            brevTittel = "Ikke grunnlag for revurdering",
        )
    }

    sealed class Klage(pdfTemplate: PdfTemplate, brevTittel: String) : PdfTemplateMedDokumentNavn(pdfTemplate, brevTittel) {
        object Oppretthold : Klage(
            pdfTemplate = PdfTemplate.Klage.Oppretthold,
            brevTittel = "Oversendelsesbrev til klager",
        )

        object Avvist : Klage(
            pdfTemplate = PdfTemplate.Klage.Avvist,
            brevTittel = "Avvist klage",
        )
    }

    data class Fritekst(val tittel: String) : PdfTemplateMedDokumentNavn(PdfTemplate.FritekstDokument, tittel)
}
