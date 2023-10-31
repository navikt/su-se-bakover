package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.extensions.toBrevformat
import tilbakekreving.domain.forhåndsvarsel.VedtaksbrevTilbakekrevingsbehandlingDokumentCommand
import java.time.Clock
import java.time.LocalDate

/**
 * TODO - her må vi fylle inn med mer info
 */
data class VedtaksbrevTilbakekrevingsbehandlingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String?,
    val dato: String,
) : PdfInnhold() {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.VedtaksbrevTilbakekrevingsbehandling

    companion object {
        fun fromBrevCommand(
            command: VedtaksbrevTilbakekrevingsbehandlingDokumentCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            clock: Clock,
        ): VedtaksbrevTilbakekrevingsbehandlingPdfInnhold {
            return VedtaksbrevTilbakekrevingsbehandlingPdfInnhold(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                fritekst = command.fritekst,
                // Denne formateres annerledes enn i personalia, selvom begge deler er dagens dato. 2021-01-01 vil gi 01.01.2021 i personalia, mens 1. januar 2021 i dette feltet.
                // TODO jah: Kanskje vi kan bruke denne i su-pdfgen? https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
                dato = LocalDate.now(clock).toBrevformat(),
            )
        }
    }
}
