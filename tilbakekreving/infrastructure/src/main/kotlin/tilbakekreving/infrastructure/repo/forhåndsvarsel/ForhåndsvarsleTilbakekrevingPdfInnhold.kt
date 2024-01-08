@file:Suppress("unused")

package tilbakekreving.infrastructure.repo.forhåndsvarsel

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.domain.brev.beregning.BrevTilbakekrevingInfo

/**
 * @param dagensDato brukes øverst til venstre i brevet for å datere det.
 */
class ForhåndsvarsleTilbakekrevingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String,
    val bruttoTilbakekreving: String,
    val nettoTilbakekreving: String,
    val tilbakekreving: List<BrevTilbakekrevingInfo>,
    val dagensDato: String,
) : PdfInnhold() {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.ForhåndsvarselTilbakekreving
}
