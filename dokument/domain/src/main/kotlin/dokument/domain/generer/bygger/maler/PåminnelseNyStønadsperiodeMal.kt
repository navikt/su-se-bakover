package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.PdfBestilling
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.punktliste
import dokument.domain.generer.bygger.telefonnummer
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.outline.Text.FontType
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class PåminnelseNyStønadsperiodePdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val utløpsdato: String,
    // Er en bruker med uføre som fyller 67 år i stønadsperiode sin siste måned
    val uføreSomFyller67: Boolean,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Tidsstyrt.PåminnelseNyStønadsperiode
    override fun bestilling(): PdfBestilling = påminnelseOmNyStønadsperiode(dokumentMal.tittel, this)
}

val påminnelseOmNyStønadsperiode = { _: String, pdfInnhold: PåminnelseNyStønadsperiodePdfInnhold ->
    dokument("Du må søke supplerende stønad på nytt", personalia = pdfInnhold.personalia) {
        if (pdfInnhold.uføreSomFyller67) {
            paragraph(
                "Din supplerende stønad stopper ${pdfInnhold.utløpsdato} fordi du har fylt 67 år og dermed ikke lenger har rett til " +
                    "supplerende stønad som ufør flyktning.",
            )
            paragraph(
                "Du må bestille time hos Nav så snart som mulig for å søke på nytt. Du må nå søke om supplerende stønad " +
                    "for personer over 67 år med kort botid i Norge.",
            )
            paragraph(
                "Vi minner om at du må ha et vedtak – enten innvilgelse eller avslag – om alderspensjon fra folketrygden " +
                    "for å kunne få supplerende stønad.",
            )
            paragraph("Dette må du gjøre", FontType.BOLD)
            punktliste(
                "Søk om alderspensjon fra folketrygden, hvis ikke du allerede har fått vedtak",
                "Ring til Nav på tlf. 55 55 33 34 for å bestille time.",
                "Si fra til oss hvis du trenger tolk på møtet.",
            )
        } else {
            paragraph(
                "Alle som får supplerende stønad, må søke på nytt en gang i året. Den supplerende stønaden din " +
                    "stopper ${pdfInnhold.utløpsdato}. Du må bestille time hos Nav nå for å søke på nytt.",
            )
            paragraph("Dette må du gjøre", FontType.BOLD)
            paragraph("Slik bestiller du time for møte:")
            punktliste(
                "Ring til Nav på tlf. ${telefonnummer(pdfInnhold)} for å bestille time.",
                "Si fra til oss hvis du trenger tolk på møtet.",
            )
        }
        paragraph("Når du kommer til Nav-kontoret, må du ha med deg:")
        punktliste(
            "Alle gyldige pass eller reisebevis. Har du gyldige pass fra flere land, må du ta med alle.",
            "Dokumentasjon på ut- og hjemreise hvis du har vært i utlandet de fire siste månedene.",
            "Dokumentasjon på alle norske og utenlandske inntekter og pensjon som ikke utbetales av Nav, både for " +
                "deg og eventuell ektefelle/samboer.",
            "Den siste skattemeldingen, både for deg-, og eventuell ektefelle/samboer, hvis du ikke har levert " +
                "dette tidligere.",
        )
        paragraph("Nødvendige og riktige opplysninger om situasjonen din", FontType.BOLD)
        paragraph(
            "Når vi behandler søknaden din, kontrollerer vi opplysningene du har gitt oss. Hvis du har gitt feil " +
                "opplysninger, kan du få feil utbetaling. Feil utbetaling må betales tilbake.",
        )
        paragraph("Med hilsen")
        paragraph("Nav Familie-og pensjonsytelser Ålesund")
    }
}
