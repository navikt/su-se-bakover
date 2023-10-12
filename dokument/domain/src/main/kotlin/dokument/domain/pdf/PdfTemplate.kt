package dokument.domain.pdf

/**
 * 1-1 mapping to templates defined by pdf-generator.
 */
sealed class PdfTemplate(
    private val templateName: String,
) {
    fun name() = templateName

    data object InnvilgetVedtak : PdfTemplate("vedtakInnvilgelse")
    data object AvslagsVedtak : PdfTemplate("vedtakAvslag")
    data object TrukketSøknad : PdfTemplate("søknadTrukket")
    data object AvvistSøknadVedtak : PdfTemplate("avvistSøknadVedtak")
    data object AvvistSøknadFritekst : PdfTemplate("avvistSøknadFritekst")
    data object Opphørsvedtak : PdfTemplate("opphørsvedtak")
    data object Forhåndsvarsel : PdfTemplate("forhåndsvarsel")
    data object ForhåndsvarselTilbakekreving : PdfTemplate("forhåndsvarselTilbakekreving")
    data object InnkallingTilKontrollsamtale : PdfTemplate("innkallingKontrollsamtale")
    data object PåminnelseNyStønadsperiode : PdfTemplate("påminnelseOmNyStønadsperiode")

    sealed class Revurdering(templateName: String) : PdfTemplate(templateName) {
        data object Inntekt : Revurdering("revurderingAvInntekt")
        data object MedTilbakekreving : Revurdering("revurderingMedTilbakekreving")
        data object Avslutt : Revurdering("avsluttRevurdering")
    }

    sealed class Klage(templateName: String) : PdfTemplate(templateName) {
        data object Oppretthold : Klage("sendtTilKlageinstans")
        data object Avvist : Klage("avvistKlage")
    }

    data object FritekstDokument : PdfTemplate("fritekstDokument")

    data object Skattegrunnlag : PdfTemplate("skattegrunnlag")
}
