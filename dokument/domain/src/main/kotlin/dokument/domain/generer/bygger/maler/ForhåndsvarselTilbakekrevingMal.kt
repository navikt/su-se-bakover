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
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.fraOgMed
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.tilOgMed
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall

data class ForhåndsvarselTilbakekrevingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String?,
    val dato: String,
    val kravgrunnlag: KravgrunnlagBrev?,
    val dødsbo: Boolean,
    override val sakstype: Sakstype,
    // TODO PdfBygger ryddejobb - kan fjernes med su-pdfgen
    val fraOgMed: String? = null,
    val tilOgMed: String? = null,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Tilbakekreving.Forhåndsvarsel(dødsbo)
}

data class KravgrunnlagBrev(
    val sumBruttoSkalTilbakekreve: Int,
    val beregningFeilutbetaltBeløp: List<BeregningFeilutbetaltBeløp>,
    val periode: DatoIntervall?,
)

data class BeregningFeilutbetaltBeløp(
    val periode: String,
    val bruttoSkalTilbakekreve: Int,
)

val forhåndsvarselTilbakekreving = { _: String, pdfInnhold: ForhåndsvarselTilbakekrevingPdfInnhold ->
    when (pdfInnhold.dødsbo) {
        false -> forhåndsvarselBruker(pdfInnhold)
        true -> forhåndsvarselDødsbo(pdfInnhold)
    }
}

private val forhåndsvarselBruker = { pdfInnhold: ForhåndsvarselTilbakekrevingPdfInnhold ->
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
            paragraph("Beløpet er ${kravgrunnlag.sumBruttoSkalTilbakekreve.kroner(utenDesimaler = true)} kroner før skatt.")
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
    }
}

private val forhåndsvarselDødsbo =
    { pdfInnhold: ForhåndsvarselTilbakekrevingPdfInnhold ->
        brev(
            tittel = "Vi vurderer om boet må betale tilbake supplerende stønad",
            personalia = pdfInnhold.personalia,
            saksbehandler = pdfInnhold.saksbehandlerNavn,
            attestant = null,
            vedlegg = vedleggOmFeilutbetaltBeløp(pdfInnhold.kravgrunnlag),
        ) {
            if (pdfInnhold.kravgrunnlag != null) {
                val periodeStreng = if (pdfInnhold.kravgrunnlag.periode != null) {
                    val fraOgMed = pdfInnhold.kravgrunnlag.periode.fraOgMed
                    val tilOgMed = pdfInnhold.kravgrunnlag.periode.tilOgMed
                    // TODO PdfBygger ryddejobb - forbedre formatering
                    "supplerende stønad fra og med $fraOgMed til og med $tilOgMed."
                } else {
                    "supplerende stønad."
                }
                paragraph(
                    "Avdøde ${pdfInnhold.personalia.fornavn} ${pdfInnhold.personalia.etternavn} har fått utbetalt " +
                        "${pdfInnhold.kravgrunnlag.sumBruttoSkalTilbakekreve.kroner(true)} kroner for mye " +
                        periodeStreng,
                )
            }
            paragraph(
                "Før vi avgjør om dette beløpet skal betales tilbake, har boet rett til å uttale seg. Dette må boet gjøre " +
                    "innen 14 dager etter at boet har fått dette varselet.",
            )
            paragraph(
                "Dette er kun et varsel om at vi vurderer å kreve tilbake det feilutbetalte beløpet. Det er ikke et vedtak om " +
                    "tilbakekreving.",
            )
            paragraph(
                "Hvis vi vedtar at boet må betale tilbake hele eller deler av det feilutbetalte beløpet, trekker vi fra " +
                    "skatten på beløpet vi krever tilbake.",
            )
            title3("Hva som har skjedd")
            fritekst(pdfInnhold.fritekst ?: "")
            title3("Reglene for tilbakekreving")
            paragraph(
                "Hvis mottakeren av feilutbetalingen forsto eller burde forstått at utbetalingen som han har mottatt er " +
                    "gjort på feil grunnlag, skal det feilutbetalte beløpet kreves tilbakebetalt. Når mottakeren er død, blir " +
                    "det avgjørende for spørsmålet om tilbakekreving hva dødsboet forsto eller burde forstått på det tidspunktet " +
                    "det fikk tilgang til avdødes post og bankkontoer.",
            )
            paragraph("Dette følger av lov om supplerende stønad § 13.")
            title3("Slik uttaler boet seg")
            paragraph(
                "Boet har rett til å uttale seg, skriftlig eller muntlig, " +
                    "før vi tar den endelige avgjørelsen om tilbakebetaling. " +
                    "Fristen for å gi uttale er 14 dager etter at boet har mottatt dette brevet.",
            )
            paragraph("Boet har rett til å se sakens dokumenter etter bestemmelsene i forvaltningsloven § 18.")
            paragraph("Slik kan boet komme med en uttalelse:")
            punktliste(
                "Boet kan gi tilbakemelding via Nav sin nettside www.nav.no/melde. " +
                    "Velg tema «Pensjon» og aktuell tjeneste.",
                "Send et skriftlig svar ved å bruke vedlagte forside " +
                    if (pdfInnhold.sakstype == Sakstype.UFØRE) {
                        forsideEttersendelseUføre
                    } else {
                        forsideEttersendelseAlder
                    } +
                    " eller du kan sende inn dokumentasjon i tjenesten www.nav.no/ettersende.",
            )
            paragraph("Boet kan få hjelp ved å ringe ${telefonnummer(pdfInnhold.sakstype)} eller boet kan kontakte det nærmeste Nav-kontor.")

            title3("Hva skjer videre i saken?")
            paragraph(
                "Vi vil vurdere saken og sende boet et vedtak. Hvis boet må betale tilbake hele eller deler av beløpet, får " +
                    "boet beskjed i vedtaket om hvordan tilbakebetalingen skal skje.",
            )
            title3("Har boet spørsmål?")
            paragraph(
                "Kontakt oss gjerne på nav.no eller på telefon ${telefonnummer(pdfInnhold.sakstype)}. " +
                    "Hvis boet oppgir fødselsnummeret til avdøde når " +
                    "de tar kontakt med Nav, kan vi lettere gi rask og god hjelp.",
            )
            postadresse()
        }
    }

private val forsideEttersendelseUføre = "(Ettersendelse til Søknad om supplerende stønad til ufør flyktning)"
private val forsideEttersendelseAlder = "(Ettersendelse til Søknad om supplerende stønad til personer med kort botid i Norge)"

private fun Innhold.slikKanDuUttaleDeg(sakstype: Sakstype) {
    val forside = if (sakstype == Sakstype.UFØRE) forsideEttersendelseUføre else forsideEttersendelseAlder
    paragraph("Slik kan du uttale deg:")
    punktliste(
        "Du kan gi tilbakemelding via Nav sin nettside nav.no/melde. Velg tema «Pensjon» og aktuell tjeneste.",
        "Send et skriftlig svar ved å bruke vedlagte forside $forside " +
            "eller du kan sende inn dokumentasjon i tjenesten nav.no/ettersende.",
    )
    paragraph("Du kan få hjelp ved å ringe ${telefonnummer(sakstype)} eller henvende deg til Nav-kontoret ditt.")
}

fun vedleggOmFeilutbetaltBeløp(kravgrunnlag: KravgrunnlagBrev?): List<Attachment> {
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
