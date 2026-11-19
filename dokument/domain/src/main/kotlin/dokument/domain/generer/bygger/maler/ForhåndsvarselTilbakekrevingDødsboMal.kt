package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.brev
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.kroner
import dokument.domain.generer.bygger.postadresse
import dokument.domain.generer.bygger.telefonnummer
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.title3
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class ForhåndsvarselTilbakekrevingDødsboPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String?,
    val dato: String,
    val fraOgMed: String,
    val tilOgMed: String,
    val kravgrunnlag: KravgrunnlagBrev,
    override val sakstype: Sakstype,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Tilbakekreving.Forhåndsvarsel(true)
}

val forhåndsvarselTilbakekrevingsbehandlingDødsbo = { _: String, pdfInnhold: ForhåndsvarselTilbakekrevingDødsboPdfInnhold ->
    brev(
        tittel = "Vi vurderer om boet må betale tilbake supplerende stønad",
        personalia = pdfInnhold.personalia,
        saksbehandler = pdfInnhold.saksbehandlerNavn,
        attestant = null,
        vedlegg = vedleggOmFeilutbetaltBeløp(pdfInnhold.kravgrunnlag),
    ) {
        paragraph(
            "Avdøde ${pdfInnhold.personalia.fornavn} ${pdfInnhold.personalia.etternavn} har fått utbetalt " +
                "${pdfInnhold.kravgrunnlag.sumBruttoSkalTilbakekreve.kroner(true)} kroner for mye " +
                "supplerende stønad fra og med ${pdfInnhold.fraOgMed} til og med ${pdfInnhold.tilOgMed}.",
        )
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
            "Dødsboet har rett til å uttale seg, skriftlig eller muntlig, " +
                "før vi tar den endelige avgjørelsen om tilbakebetaling. " +
                "Fristen for å gi uttale er 14 dager etter at boet har mottatt dette brevet.",
        )
        paragraph(
            "Dødsboet kan uttale seg muntlig ved fremmøte hos det lokale Nav-kontoret, " +
                "ved å ringe telefonnummeret nedenfor, eller det kan sende et skriftlig svar sammen med forside. " +
                "Boet finner forsiden under \"Skjema og søknad\" ved å velge \"Pensjon\" og deretter " +
                "\"Supplerende stønad til personer med kort botid i Norge\". Bruk valget \"Ettersendelse\", \"Ettersend på papir\" " +
                "og \"Annet\". Fyll ut aktuelle punkter og fødselsnummeret til avdøde før boet velger \"Neste\". " +
                "Last ned \"Førsteside til saken din\" og skriv ut denne.",
        )
        title3("Hva skjer videre i saken?")
        paragraph(
            "Vi vil vurdere saken og sende boet et vedtak. Hvis boet må betale tilbake hele eller deler av beløpet, får " +
                "boet beskjed i vedtaket om hvordan tilbakebetalingen skal skje.",
        )
        title3("Boet har rett til innsyn")
        paragraph("Boet har som hovedregel rett til å se dokumentene i saken etter reglene i forvaltningsloven § 18.")
        title3("Har boet spørsmål?")
        paragraph(
            "Kontakt oss gjerne på nav.no eller på telefon ${telefonnummer(pdfInnhold.sakstype)}. " +
                "Hvis boet oppgir fødselsnummeret til avdøde når " +
                "de tar kontakt med Nav, kan vi lettere gi rask og god hjelp.",
        )
        postadresse()
    }
}
