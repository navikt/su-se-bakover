package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.BeregningFeilutbetaltBeløp
import dokument.domain.generer.bygger.maler.ForhåndsvarselTilbakekrevingPdfInnhold
import dokument.domain.generer.bygger.maler.KravgrunnlagBrev
import no.nav.su.se.bakover.common.domain.sak.Sakstype

val forhåndsvarselTilbakekrevingPdf = ForhåndsvarselTilbakekrevingPdfInnhold(
    personalia = PersonaliaPdfInnhold(
        dato = "01.01.2020",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021,
    ),
    saksbehandlerNavn = "Malenia, blade of Miquella",
    fritekst = "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum",
    dato = "01.01.2020",
    kravgrunnlag = KravgrunnlagBrev(
        sumBruttoSkalTilbakekreve = 5000,
        beregningFeilutbetaltBeløp = listOf(
            BeregningFeilutbetaltBeløp(
                periode = "01.01.2023 - 31.01.2023",
                bruttoSkalTilbakekreve = 3000,
            ),
            BeregningFeilutbetaltBeløp(
                periode = "01.02.2023 - 28.02.2023",
                bruttoSkalTilbakekreve = 3000,
            ),
        ),
        periode = null,
    ),
    sakstype = Sakstype.UFØRE,
    dødsbo = false,
)
