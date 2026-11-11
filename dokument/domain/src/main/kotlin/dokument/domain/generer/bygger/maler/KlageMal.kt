package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fellesKlageinfo
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.hilsen
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.title2
import no.nav.su.se.bakover.common.domain.sak.Sakstype

sealed interface KlagePdfInnhold : PdfInnhold {
    data class Oppretthold(
        override val sakstype: Sakstype,
        val saksnummer: Long,
        val personalia: PersonaliaPdfInnhold,
        val saksbehandlerNavn: String,
        val attestantNavn: String?,
        val fritekst: String,
        val klageDato: String,
        val vedtakDato: String?,
    ) : KlagePdfInnhold {
        override val dokumentMal = DokumentMal.Klage.Oppretthold
    }

    data class Avvist(
        override val sakstype: Sakstype,
        val saksnummer: Long,
        val personalia: PersonaliaPdfInnhold,
        val saksbehandlerNavn: String,
        val attestantNavn: String?,
        val fritekst: String,
    ) : KlagePdfInnhold {
        override val dokumentMal = DokumentMal.Klage.Avvist
    }
}

val sendtTilKlageinstans = { _: String, pdfInnhold: KlagePdfInnhold.Oppretthold ->
    val tittel = "Vi har sendt saken til Nav Klageinstans"
    val vedtakDato = pdfInnhold.vedtakDato ?: ""
    dokument(tittel, pdfInnhold.personalia) {
        paragraph("Fødselsnummer: ${pdfInnhold.personalia.fødselsnummer}")
        paragraph(
            "Vi har mottatt klagen din på vedtaket om supplerende stønad $vedtakDato, og kommet " +
                "frem til at det ikke endres. Nav Klageinstans skal derfor vurdere saken din på nytt.",
        )
        paragraph("Informasjon om saksbehandlingstid finner du på nav.no.")
        title2("Dette har vi lagt vekt på i vurderingen vår")
        paragraph(
            "Vi viser til vedtak av $vedtakDato og klagen din på dette vedtaket som vi mottok ${pdfInnhold.klageDato}.",
        )
        fritekst(pdfInnhold.fritekst)
        paragraph(
            "Har du nye opplysninger eller ønsker å uttale deg, kontakt Nav " +
                "Klageinstans på nav.no eller på telefon 21 07 17 30.",
        )
        title2("Du har rett til innsyn")
        paragraph("På nav.no/minside kan du se dokumentene i saken din.")
        title2("Har du spørsmål?")
        paragraph(
            "Du finner nyttig informasjon på " +
                if (pdfInnhold.sakstype == Sakstype.ALDER) "www.nav.no/supplerende-stonad-over-67." else "nav.no/supplerende-stonad-flyktninger.",
        )
        paragraph(
            "Kontakt gjerne Nav Klageinstans på nav.no eller på telefon 21 07 17 30. " +
                "Hvis du oppgir fødselsnummeret ditt når du tar kontakt med Nav, kan de " +
                "lettere gi deg rask og god hjelp.",
        )
        hilsen(saksbehandler = pdfInnhold.saksbehandlerNavn, attestant = pdfInnhold.attestantNavn)
    }
}
val avvistKlage = { _: String, pdfInnhold: KlagePdfInnhold.Avvist ->
    val tittel = "Nav har avvist klagen din"
    dokument(tittel, pdfInnhold.personalia) {
        paragraph("Fødselsnummer: ${pdfInnhold.personalia.fødselsnummer}")
        fritekst(pdfInnhold.fritekst)
        fellesKlageinfo(pdfInnhold.sakstype)
        hilsen(saksbehandler = pdfInnhold.saksbehandlerNavn, attestant = pdfInnhold.attestantNavn)
    }
}
