package no.nav.su.se.bakover.domain.brev

/**
 * 1-1 mapping to templates defined by pdf-generator.
 */
sealed class PdfTemplateMedNavn(
    private val templateName: String,
) {
    fun name() = templateName

    object InnvilgetVedtak : PdfTemplateMedNavn("vedtakInnvilgelse")
    object AvslagsVedtak : PdfTemplateMedNavn("vedtakAvslag")
    object TrukketSøknad : PdfTemplateMedNavn("søknadTrukket")
    object AvvistSøknadVedtak : PdfTemplateMedNavn("avvistSøknadVedtak")
    object AvvistSøknadFritekst : PdfTemplateMedNavn("avvistSøknadFritekst")
    object Opphørsvedtak : PdfTemplateMedNavn("opphørsvedtak")
    object Forhåndsvarsel : PdfTemplateMedNavn("forhåndsvarsel")
    object ForhåndsvarselTilbakekreving : PdfTemplateMedNavn("forhåndsvarselTilbakekreving")
    object InnkallingTilKontrollsamtale : PdfTemplateMedNavn("innkallingKontrollsamtale")
    object PåminnelseNyStønadsperiode : PdfTemplateMedNavn("påminnelseOmNyStønadsperiode")

    sealed class Revurdering(templateName: String) : PdfTemplateMedNavn(templateName) {
        object Inntekt : Revurdering("revurderingAvInntekt")
        object MedTilbakekreving : Revurdering("revurderingMedTilbakekreving")
        object Avslutt : Revurdering("avsluttRevurdering")
    }
    sealed class Klage(templateName: String) : PdfTemplateMedNavn(templateName) {
        object Oppretthold : Klage("sendtTilKlageinstans")
        object Avvist : Klage("avvistKlage")
    }

    object FritekstDokument : PdfTemplateMedNavn("fritekstDokument")
}
