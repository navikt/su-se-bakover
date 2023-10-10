package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.PdfTemplateMedDokumentNavn
import dokument.domain.brev.PdfInnhold
import no.nav.su.se.bakover.common.extensions.norwegianLocale
import no.nav.su.se.bakover.common.extensions.toBrevformat
import no.nav.su.se.bakover.domain.brev.beregning.BrevTilbakekrevingInfo
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselTilbakekrevingDokumentCommand
import java.text.NumberFormat
import java.time.Clock
import java.time.LocalDate

data class ForhåndsvarselTilbakekrevingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String,
    val bruttoTilbakekreving: String,
    val tilbakekreving: List<BrevTilbakekrevingInfo>,
    val periodeStart: String,
    val periodeSlutt: String,
    val dato: String,
) : PdfInnhold() {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.ForhåndsvarselTilbakekreving

    companion object {
        fun fromBrevCommand(
            command: ForhåndsvarselTilbakekrevingDokumentCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            clock: Clock,
        ): ForhåndsvarselTilbakekrevingPdfInnhold {
            return ForhåndsvarselTilbakekrevingPdfInnhold(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                fritekst = command.fritekst,
                bruttoTilbakekreving = NumberFormat.getNumberInstance(norwegianLocale)
                    .format(command.bruttoTilbakekreving),
                periodeStart = command.tilbakekreving.periodeStart,
                periodeSlutt = command.tilbakekreving.periodeSlutt,
                tilbakekreving = command.tilbakekreving.tilbakekrevingavdrag,
                // Denne formateres annerledes enn i personalia, selvom begge deler er dagens dato. 2021-01-01 vil gi 01.01.2021 i personalia, mens 1. januar 2021 i dette feltet.
                // TODO jah: Kanskje vi kan bruke denne i su-pdfgen? https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
                dato = LocalDate.now(clock).toBrevformat(),
            )
        }
    }
}
