@file:Suppress("unused")

package tilbakekreving.infrastructure

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.beregning.BrevTilbakekrevingInfo
import no.nav.su.se.bakover.domain.brev.jsonRequest.PersonaliaPdfInnhold

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

//    companion object {
//        fun fromBrevCommand(
//            fritekst: String,
//            kravgrunnlag: Kravgrunnlag,
//            personalia: PersonaliaPdfInnhold,
//            saksbehandlerNavn: String,
//            clock: Clock,
//        ): ForhåndsvarsleTilbakekrevingPdfInnhold {
//            TODO()
// //            return ForhåndsvarsleTilbakekrevingPdfInnhold(
// //                personalia = personalia,
// //                saksbehandlerNavn = saksbehandlerNavn,
// //                fritekst = fritekst,
// //                bruttoTilbakekreving = TODO(),
// //                tilbakekreving = TODO(),
// //                // Denne formateres annerledes enn i personalia, selvom begge deler er dagens dato. 2021-01-01 vil gi 01.01.2021 i personalia, mens 1. januar 2021 i dette feltet.
// //                // TODO jah: Kanskje vi kan bruke denne i su-pdfgen? https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
// //                dagensDato = LocalDate.now(clock).toString(),
// //            )
//        }
//    }
}
