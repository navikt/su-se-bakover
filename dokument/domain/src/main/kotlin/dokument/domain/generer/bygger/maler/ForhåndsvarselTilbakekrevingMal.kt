package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.Innhold
import dokument.domain.generer.bygger.brev
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.kroner
import dokument.domain.generer.bygger.postadresse
import dokument.domain.generer.bygger.punktliste
import dokument.domain.generer.bygger.telefonnummer
import no.nav.brev.brevbaker.markup.Attachment
import no.nav.brev.brevbaker.markup.dsl.attachment
import no.nav.brev.brevbaker.markup.dsl.cell
import no.nav.brev.brevbaker.markup.dsl.column
import no.nav.brev.brevbaker.markup.dsl.header
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.row
import no.nav.brev.brevbaker.markup.dsl.table
import no.nav.brev.brevbaker.markup.dsl.title1
import no.nav.brev.brevbaker.markup.dsl.title3
import no.nav.brev.brevbaker.markup.outline.Block.Table.ColumnAlignment
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class ForhåndsvarselTilbakekrevingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String?,
    val dato: String,
    val kravgrunnlag: KravgrunnlagData?,
    override val sakstype: Sakstype,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Tilbakekreving.Forhåndsvarsel
}

data class KravgrunnlagData(
    val sumBruttoSkalTilbakekreve: Int,
    val beregningFeilutbetaltBeløp: List<BeregningFeilutbetaltBeløp>,
)

data class BeregningFeilutbetaltBeløp(
    val periode: String,
    val bruttoSkalTilbakekreve: Int,
)

val forhåndsvarselTilbakekrevingsbehandling = { _: String, pdfInnhold: ForhåndsvarselTilbakekrevingPdfInnhold ->
    brev(
        tittel = "Varsel om at Nav vurderer å kreve tilbake feilutbetalt supplerende stønad",
        personalia = pdfInnhold.personalia,
        saksbehandler = pdfInnhold.saksbehandlerNavn,
        attestant = null,
        vedlegg = vedleggOmFeilutbetaltBeløp(pdfInnhold.kravgrunnlag),
    ) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        pdfInnhold.kravgrunnlag?.let { kravgrunnlag ->
            paragraph("Du har fått utbetalt for mye supplerende stønad. Vi vurderer å kreve tilbake dette beløpet.")
            paragraph("Beløpet er ${kravgrunnlag.sumBruttoSkalTilbakekreve} kroner før skatt.")
            paragraph("Du finner flere detaljer om feilutbetalt beløp lenger ned i brevet.")
            title3("Hva har skjedd")
        }
        fritekst(pdfInnhold.fritekst ?: "")
        title3("Dette legger vi vekt på i vurderingen vår")
        paragraph("Når vi avgjør om du skal betale tilbake vurderer vi:")
        punktliste(
            "Om du forsto eller burde forstått at beløpet du fikk utbetalt var feil",
            "Om du har gitt riktig informasjon til Nav",
            "Om du har gitt all informasjon til Nav i rett tid",
            "Hvilken informasjon du har fått fra Nav, blant annet i vedtak",
        )
        paragraph(
            "Selv om det er Nav som er skyld i feilutbetalingen, kan vi kreve at du betaler tilbake pengene hvis du forsto " +
                "eller burde forstått at utbetalingen skyldtes en feil.",
        )
        title3("Reglene for tilbakekreving")
        paragraph(
            "Hvis du forsto eller burde forstått at utbetalingen du har mottatt er gjort på feil grunnlag, skal det " +
                "feilutbetalte beløpet kreves tilbakebetalt. Dette gjelder uavhengig av om det er Nav eller du som er skyld i " +
                "feilutbetalingen/har forårsaket feilutbetalingen.",
        )
        paragraph("Dette følger av lov om supplerende stønad § 13.")
        paragraph(
            "Dersom Nav fatter vedtak om at du skal betale tilbake det du har fått for mye utbetalt, får du beskjed i " +
                "vedtaket om hvordan tilbakebetalingen skal skje.",
        )
        title3("Dette gjør du hvis du ønsker å uttale deg i saken")
        paragraph(
            "Du har rett til å uttale deg, skriftlig eller muntlig, før vi tar den endelige avgjørelsen om " +
                "tilbakebetaling. Fristen for å gi uttale er 14 dager etter at du har mottatt dette brevet.",
        )
        paragraph("Du har rett til å se sakens dokumenter etter bestemmelsene i forvaltningsloven § 18.")
        slikKanDuUttaleDeg(pdfInnhold.sakstype)
        postadresse()
    }
}

private fun Innhold.slikKanDuUttaleDeg(sakstype: Sakstype) {
    paragraph("Slik kan du uttale deg:")
    punktliste(
        "Logg deg inn på nav.no eller",
        "Send et skriftlig svar",
        "Du kan få hjelp ved å ringe ${telefonnummer(sakstype)} eller henvende deg til Nav-kontoret ditt",
    )
    paragraph(
        "Hvis du vil sende skriftlig svar, må du bruke forsiden fra " +
            if (sakstype == Sakstype.UFØRE) {
                "https://www.nav.no/ettersende#supplerende-stonad-for-ufore-flyktninger-under-67-ar"
            } else {
                "https://www.nav.no/ettersende#supplerende-stonad-for-personer-over-67-ar-med-kort-botid-i-norge"
            },
    )
}

private fun vedleggOmFeilutbetaltBeløp(kravgrunnlag: KravgrunnlagData?): List<Attachment> {
    if (kravgrunnlag == null) return emptyList()
    val perioder = kravgrunnlag.beregningFeilutbetaltBeløp
    return listOf(
        attachment {
            title1("Vedlegg: Beregning av feilutbetalt beløp")
            outline {
                if (perioder.isEmpty()) {
                    paragraph("Periode Feilutbetalt beløp")
                } else {
                    table {
                        header {
                            column("Periode")
                            column("Feilutbetalt beløp", ColumnAlignment.RIGHT)
                        }
                        perioder.forEach { periode ->
                            row {
                                cell(periode.periode)
                                cell("${periode.bruttoSkalTilbakekreve.kroner(true)} kr")
                            }
                        }
                    }
                }
            }
        },
    )
}
