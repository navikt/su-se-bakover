package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.hilsen
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.su.se.bakover.common.domain.sak.Sakstype

/**
 * Brev for når en revurdering er forhåndsvarslet
 * hvis revurderingen ikke er forhåndsvarslet, er det ikke noe brev.
 */
data class AvsluttRevurderingPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String?,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Revurdering.Avslutt
}

val avsluttRevurdering = { _: String, pdfInnhold: AvsluttRevurderingPdfInnhold ->
    val tittel = "Varsel om at ny vurdering av din supplerende stønad er blitt avsluttet"
    dokument(tittel, pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        paragraph(
            "Vi har ikke funnet grunnlag for å revurdere din supplerende stønad, din supplerende stønad vil derfor ikke " +
                "bli endret.",
        )
        pdfInnhold.fritekst?.takeIf { it.isNotBlank() }?.let { fritekst(it) }
        hilsen(saksbehandler = pdfInnhold.saksbehandlerNavn)
    }
}
