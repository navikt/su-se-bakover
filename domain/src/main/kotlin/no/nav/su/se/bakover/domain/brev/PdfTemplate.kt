package no.nav.su.se.bakover.domain.brev

/**
 * 1-1 mapping to templates defined by pdf-generator.
 */
sealed class PdfTemplate(
    private val templateName: String
) {
    fun name() = templateName

    object InnvilgetVedtak : PdfTemplate("vedtakInnvilgelse")
    object AvslagsVedtak : PdfTemplate("vedtakAvslag")
    object TrukketSøknad : PdfTemplate("søknadTrukket")
    object AvvistSøknadVedtak : PdfTemplate("avvistSøknadVedtak")
    object AvvistSøknadFritekst : PdfTemplate("avvistSøknadFritekst")
    object Opphørsvedtak : PdfTemplate("opphørsvedtak")
    object VedtakIngenEndring : PdfTemplate("vedtakIngenEndring")
    object Forhåndsvarsel : PdfTemplate("forhåndsvarsel")
    object InnkallingTilKontrollsamtale : PdfTemplate("innkallingKontrollsamtale")

    sealed class Revurdering(templateName: String) : PdfTemplate(templateName) {
        object Inntekt : Revurdering("revurderingAvInntekt")
        object Avslutt : Revurdering("avsluttRevurdering")
    }
    sealed class Klage(templateName: String) : PdfTemplate(templateName) {
        object Oppretthold : Klage("sendtTilKlageinstans")
        object Avvist : Klage("avvistKlage")
    }
}
