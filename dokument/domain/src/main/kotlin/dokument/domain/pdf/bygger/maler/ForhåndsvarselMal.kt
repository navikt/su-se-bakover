package dokument.domain.pdf.bygger.maler

import dokument.domain.pdf.bygger.GenerellData
import dokument.domain.pdf.bygger.dokument
import dokument.domain.pdf.bygger.fritekst
import dokument.domain.pdf.bygger.fødselsnummerAvsnitt
import dokument.domain.pdf.bygger.standardHilsen
import dokument.domain.pdf.bygger.telefonnummer
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.title2

val forhåndsvarsel = { data: GenerellData ->
    val alder = data["sakstype"].er("ALDER")
    dokument(data, "Varsel om at vi vil ta opp din supplerende stønad til ny vurdering") {
        fødselsnummerAvsnitt(data["personalia"])
        paragraph("Vi har fått opplysninger som gjør det nødvendig å vurdere saken din på nytt.")
        fritekst(data["fritekst"])
        title2("Du kan uttale deg i saken")
        paragraph(
            "Før vi gjør vedtak i saken, har du rett til å uttale deg. Fristen for å uttale seg er 14 dager etter at du har mottatt dette brevet. " +
                "Uttalelsen bør være skriftlig. Kontakt oss gjerne på telefon ${telefonnummer(data["sakstype"])} " +
                "for råd og veiledning. " +
                "Du har også som hovedregel rett til å se sakens dokumenter etter bestemmelsene i forvaltningsloven § 18.",
        )
        paragraph(
            "Hvis du vil sende din uttalelse pr post kan du finne forside med adresse på nav.no under «Søknad og skjema», " +
                "og «Pensjon», " +
                (if (alder) "«Supplerende stønad til personer over 67 år med kort botid i Norge»" else "«Supplerende stønad til ufør flyktning»") +
                " og «Ettersendelse».",
        )
        title2("Plikt til å melde fra")
        paragraph(
            "Du har i tidligere vedtak blitt orientert om plikten din til å melde fra om endringer i forhold som kan ha betydning for retten til eller størrelsen på ytelsen din.",
        )
        standardHilsen(data)
    }
}
