package tilbakekreving.domain.forhåndsvarsel

import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.BeregningFeilutbetaltBeløp
import dokument.domain.generer.bygger.maler.ForhåndsvarselTilbakekrevingDødsboPdfInnhold
import dokument.domain.generer.bygger.maler.ForhåndsvarselTilbakekrevingPdfInnhold
import dokument.domain.generer.bygger.maler.KravgrunnlagData
import no.nav.su.se.bakover.common.domain.tid.toBrevformat
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.time.Clock
import java.time.LocalDate
import javax.jms.IllegalStateException

fun ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    clock: Clock,
): PdfInnhold {
    return if (dødsbo) {
        val kravgrunnlagPeriode = kravgrunnlag?.periode ?: throw IllegalStateException("Kan ikke forhåndsvarsle tilbakekreving dødsbo uten kravgrunnlag.")
        ForhåndsvarselTilbakekrevingDødsboPdfInnhold(
            personalia = personalia,
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
            dato = LocalDate.now(clock).toBrevformat(),
            kravgrunnlag = kravgrunnlag.toBrev(),
            sakstype = sakstype,
            fraOgMed = kravgrunnlagPeriode.fraOgMed.toString(),
            tilOgMed = kravgrunnlagPeriode.tilOgMed.toString(),
        )
    } else {
        ForhåndsvarselTilbakekrevingPdfInnhold(
            personalia = personalia,
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
            dato = LocalDate.now(clock).toBrevformat(),
            kravgrunnlag = kravgrunnlag?.toBrev(),
            sakstype = sakstype,
        )
    }
}

private fun Kravgrunnlag.toBrev() = KravgrunnlagData(
    sumBruttoSkalTilbakekreve = summertBruttoFeilutbetaling,
    beregningFeilutbetaltBeløp = grunnlagsperioder.map {
        BeregningFeilutbetaltBeløp(
            periode = it.periode.ddMMyyyy(),
            bruttoSkalTilbakekreve = it.bruttoFeilutbetaling,
        )
    },
)
