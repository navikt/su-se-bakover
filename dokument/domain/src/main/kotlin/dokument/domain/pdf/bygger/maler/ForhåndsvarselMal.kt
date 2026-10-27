package dokument.domain.pdf.bygger.maler

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import dokument.domain.pdf.bygger.dokument
import dokument.domain.pdf.bygger.fritekst
import dokument.domain.pdf.bygger.fødselsnummerAvsnitt
import dokument.domain.pdf.bygger.hilsen
import dokument.domain.pdf.bygger.telefonnummer
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.title2
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class ForhåndsvarselPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String,
) : PdfInnhold {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.Forhåndsvarsel
}

val forhåndsvarsel = { pdfInnhold: PdfInnhold ->
    pdfInnhold as ForhåndsvarselPdfInnhold // TODO midlertidig hack frem til jeg finner en bedre typing..
    dokument("Varsel om at vi vil ta opp din supplerende stønad til ny vurdering", pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        paragraph("Vi har fått opplysninger som gjør det nødvendig å vurdere saken din på nytt.")
        fritekst(pdfInnhold.fritekst)
        title2("Du kan uttale deg i saken")
        paragraph(
            "Før vi gjør vedtak i saken, har du rett til å uttale deg. Fristen for å uttale seg er 14 dager etter at du har mottatt dette brevet. " +
                "Uttalelsen bør være skriftlig. Kontakt oss gjerne på telefon ${telefonnummer(pdfInnhold.sakstype)} " +
                "for råd og veiledning. " +
                "Du har også som hovedregel rett til å se sakens dokumenter etter bestemmelsene i forvaltningsloven § 18.",
        )
        paragraph(
            "Hvis du vil sende din uttalelse pr post kan du finne forside med adresse på nav.no under «Søknad og skjema», " +
                "og «Pensjon», " +
                (if (pdfInnhold.sakstype == Sakstype.ALDER) "«Supplerende stønad til personer over 67 år med kort botid i Norge»" else "«Supplerende stønad til ufør flyktning»") +
                " og «Ettersendelse».",
        )
        title2("Plikt til å melde fra")
        paragraph(
            "Du har i tidligere vedtak blitt orientert om plikten din til å melde fra om endringer i forhold som kan ha betydning for retten til eller størrelsen på ytelsen din.",
        )
        hilsen(saksbehandler = pdfInnhold.saksbehandlerNavn)
    }
}
