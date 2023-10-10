package dokument.domain

// TODO jah: Flytt disse til de respektive plassene.
sealed class PdfTemplateMedDokumentNavn(
    private val pdfTemplate: PdfTemplate,
    /**
     * navnet vi bruker i gosys og journalføring
     */
    private val dokumentNavn: String,
) {
    fun template() = pdfTemplate.name()
    fun tittel() = dokumentNavn

    data object InnvilgetVedtak : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.InnvilgetVedtak,
        dokumentNavn = "Vedtaksbrev for søknad om supplerende stønad",
    )

    data object AvslagsVedtak : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.AvslagsVedtak,
        dokumentNavn = "Avslag supplerende stønad",
    )

    data object TrukketSøknad : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.TrukketSøknad,
        dokumentNavn = "Bekrefter at søknad er trukket",
    )

    data object AvvistSøknadVedtak : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.AvvistSøknadVedtak,
        dokumentNavn = "Søknaden din om supplerende stønad er avvist",
    )

    data object AvvistSøknadFritekst : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.AvvistSøknadFritekst,
        dokumentNavn = "Søknaden din om supplerende stønad er avvist",
    )

    sealed class Opphør(pdfTemplate: PdfTemplate, brevTittel: String) : PdfTemplateMedDokumentNavn(pdfTemplate, brevTittel) {
        data object Opphørsvedtak : Opphør(
            pdfTemplate = PdfTemplate.Opphørsvedtak,
            brevTittel = "Vedtak om opphør av supplerende stønad",
        )
    }

    data object Forhåndsvarsel : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.Forhåndsvarsel,
        dokumentNavn = "Varsel om at vi vil ta opp stønaden din til ny vurdering",
    )

    data object ForhåndsvarselTilbakekreving : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.ForhåndsvarselTilbakekreving,
        dokumentNavn = "Varsel om mulig tilbakekreving",
    )

    data object InnkallingTilKontrollsamtale : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.InnkallingTilKontrollsamtale,
        dokumentNavn = "Supplerende stønad ufør flyktning – innkalling til samtale",
    )

    data object PåminnelseNyStønadsperiode : PdfTemplateMedDokumentNavn(
        pdfTemplate = PdfTemplate.PåminnelseNyStønadsperiode,
        dokumentNavn = "Påminnelse om ny stønadsperiode",
    )

    sealed class Revurdering(pdfTemplate: PdfTemplate, brevTittel: String) : PdfTemplateMedDokumentNavn(pdfTemplate, brevTittel) {
        data object Inntekt : Revurdering(
            pdfTemplate = PdfTemplate.Revurdering.Inntekt,
            brevTittel = "Vi har vurdert den supplerende stønaden din på nytt",
        )

        data object MedTilbakekreving : Revurdering(
            pdfTemplate = PdfTemplate.Revurdering.MedTilbakekreving,
            brevTittel = "Vi har vurdert den supplerende stønaden din på nytt og vil kreve tilbake penger",
        )

        data object AvsluttRevurdering : Revurdering(
            pdfTemplate = PdfTemplate.Revurdering.Avslutt,
            brevTittel = "Ikke grunnlag for revurdering",
        )
    }

    sealed class Klage(pdfTemplate: PdfTemplate, brevTittel: String) : PdfTemplateMedDokumentNavn(pdfTemplate, brevTittel) {
        data object Oppretthold : Klage(
            pdfTemplate = PdfTemplate.Klage.Oppretthold,
            brevTittel = "Oversendelsesbrev til klager",
        )

        data object Avvist : Klage(
            pdfTemplate = PdfTemplate.Klage.Avvist,
            brevTittel = "Avvist klage",
        )
    }

    data class Fritekst(val tittel: String) : PdfTemplateMedDokumentNavn(PdfTemplate.FritekstDokument, tittel)
}
