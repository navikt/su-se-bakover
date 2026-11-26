package tilbakekreving.domain.forhåndsvarsel

import dokument.domain.Brevtype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.Dokumenttype
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.BeregningFeilutbetaltBeløp
import dokument.domain.generer.bygger.maler.ForhåndsvarselTilbakekrevingPdfInnhold
import dokument.domain.generer.bygger.maler.KravgrunnlagBrev
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.tilOgMed
import no.nav.su.se.bakover.common.domain.tid.toBrevformat
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.time.LocalDate
import javax.jms.IllegalStateException

fun ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    idag: LocalDate,
): PdfInnhold {
    if (dødsbo) {
        kravgrunnlag?.periode
            ?: throw IllegalStateException("Kan ikke forhåndsvarsle tilbakekreving dødsbo uten kravgrunnlag.")
    }
    return ForhåndsvarselTilbakekrevingPdfInnhold(
        personalia = personalia,
        saksbehandlerNavn = saksbehandlerNavn,
        fritekst = fritekst,
        dato = idag.toBrevformat(),
        kravgrunnlag = kravgrunnlag?.toBrev(),
        sakstype = sakstype,
        dødsbo = dødsbo,
        // TODO kan fjernes med su-pdfgen
        fraOgMed = kravgrunnlag?.periode?.fraOgMed?.toString(),
        tilOgMed = kravgrunnlag?.periode?.tilOgMed?.toString(),
    )
}

private fun Kravgrunnlag.toBrev() = KravgrunnlagBrev(
    sumBruttoSkalTilbakekreve = summertBruttoFeilutbetaling,
    beregningFeilutbetaltBeløp = grunnlagsperioder.map {
        BeregningFeilutbetaltBeløp(
            periode = it.periode.ddMMyyyy(),
            bruttoSkalTilbakekreve = it.bruttoFeilutbetaling,
        )
    },
    periode = periode,
)

val forhåndsvarselTilbakekrevingBrev = Brevmal<ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand>(
    dokumenttype = Dokumenttype.INFORMASJON_VIKTIG,
    brevtype = Brevtype.FORHANDSVARSEL,
) { felles, kommando ->
    kommando.fromBrevCommand(
        personalia = felles.personalia,
        saksbehandlerNavn = felles.saksbehandlerNavn,
        idag = felles.idag,
    )
}
