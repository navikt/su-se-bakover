package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.hilsen
import dokument.domain.generer.bygger.punktliste
import dokument.domain.generer.bygger.telefonnummer
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.outline.Text
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class InnkallingTilKontrollsamtalePdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.InnkallingTilKontrollsamtale
}

val innkallingKontrollsamtale = { _: String, pdfInnhold: InnkallingTilKontrollsamtalePdfInnhold ->
    val tittel = "Du må bestille time for kontrollsamtale hos Nav"
    dokument(tittel, pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        paragraph(
            "Alle som får supplerende stønad, må komme på kontrollsamtale hos Nav to ganger i året. " +
                "Du må selv ringe til oss og bestille time. Fristen er 3 uker fra du får dette brevet. " +
                "Hvis du ikke tar kontakt med oss, stopper stønaden (se lov om supplerende stønad § 18 og 21).",
        )
        paragraph {
            text("Dette må du gjøre", Text.FontType.BOLD)
            newLine()
            text("Slik bestiller du time for møte:")
        }
        punktliste(
            "Ring til Nav på tlf. ${telefonnummer(pdfInnhold.sakstype)} for å bestille time.",
            "Du må ringe innen 3 uker, ellers stopper stønaden.",
            "Si fra til oss hvis du trenger tolk på møtet.",
        )
        paragraph {
            text("Dette må du ha med deg", Text.FontType.BOLD)
            newLine()
            text("Når du kommer til Nav-kontoret, må du ha med deg:")
        }
        punktliste(
            "Alle gyldige pass eller reisebevis. Har du gyldige pass fra flere land, må du ta med alle.",
            "Dokumentasjon på ut- og hjemreise hvis du har vært i utlandet de fire siste månedene.",
            "Dokumentasjon på alle norske og utenlandske inntekter, inkludert pensjon som ikke utbetales av Nav, " +
                "både for deg og eventuell ektefelle/samboer.",
            "Utskrift som viser saldo (hvor mye penger) du og eventuell ektefelle/samboer har på konto.",
            "Den siste skattemeldingen, både for deg og eventuell ektefelle/samboer, hvis du ikke har levert dette tidligere.",
        )
        hilsen(saksbehandler = null, visPostadresse = false)
    }
}
