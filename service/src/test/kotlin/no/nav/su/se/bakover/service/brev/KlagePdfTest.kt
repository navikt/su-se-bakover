import arrow.core.getOrElse
import dokument.domain.generer.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.brev.jsonRequest.fromBrevCommand
import no.nav.su.se.bakover.service.brev.PdfByggerExtension
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.PdfTekst
import no.nav.su.se.bakover.service.brev.tilPdfBestilling
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertFalse

@ExtendWith(PdfByggerExtension::class)
internal class KlagePdfTest(private val klient: PdfByggerKlientImpl) {

    private val personalia = PersonaliaPdfInnhold(
        dato = "01.01.2020",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021L,
    )
    private val sendtTilKlageinstansCommand =
        KlageDokumentCommand.OpprettholdEllerDelvisOmgjøring(
            fødselsnummer = Fnr("12345678901"),
            saksnummer = Saksnummer(2021L),
            sakstype = Sakstype.ALDER,
            saksbehandler = NavIdentBruker.Saksbehandler("Malenia, blade of Miquella"),
            attestant = null,
            fritekst = "Her kommer det en lang fritekst om hvorfor saken din er sendt til klageinstansen.",
            klageDato = LocalDate.of(2021, 12, 20),
            vedtaksbrevDato = LocalDate.of(2021, 12, 1),
            dødsbo = false,
        )

    private val avvistKlageCommand = KlageDokumentCommand.Avvist(
        fødselsnummer = Fnr("12345678901"),
        saksnummer = Saksnummer(2021L),
        sakstype = Sakstype.ALDER,
        saksbehandler = NavIdentBruker.Saksbehandler("Malenia, blade of Miquella"),
        attestant = null,
        fritekst = "Her kommer det en lang fritekst om hvorfor klagen din er avvist.",
        dødsbo = false,
    )

    @Test
    fun `skal generere riktig pdf for oversendelse av klage med dødsbo`() {
        val pdfTekst = genererOgVerifiserKlagePdf(dødsbo = true)

        assertContains(pdfTekst, "Boet har rett til innsyn")
        assertFalse(pdfTekst.contains("Du har rett til innsyn"))
    }

    @Test
    fun `skal generere riktig pdf for oversendelse av klage uten dødsbo`() {
        val pdfTekst = genererOgVerifiserKlagePdf(dødsbo = false)

        assertContains(pdfTekst, "Du har rett til innsyn")
        assertFalse(pdfTekst.contains("Boet har rett til innsyn"))
    }

    @Test
    fun `skal generere riktig pdf for avvist klage med dødsbo`() {
        val pdfTekst = genererOgVerifiserAvvistKlagePdf(dødsbo = true)

        assertContains(pdfTekst, "Nav har avvist dødsboets klage")
        assertFalse(pdfTekst.contains("Nav har avvist klagen din"))
    }

    @Test
    fun `skal generere riktig pdf for avvist klage uten dødsbo`() {
        val pdfTekst = genererOgVerifiserAvvistKlagePdf(dødsbo = false)

        assertContains(pdfTekst, "Nav har avvist klagen din")
        assertFalse(pdfTekst.contains("Nav har avvist dødsboets klage"))
    }

    private fun genererOgVerifiserKlagePdf(dødsbo: Boolean): String {
        val pdfInnhold = sendtTilKlageinstansCommand
            .copy(dødsbo = dødsbo)
            .fromBrevCommand(
                personalia = personalia,
                saksbehandlerNavn = "Malenia, blade of Miquella",
                attestantNavn = "James Eugene Raynor",
            )
        val bestilling = requireNotNull(pdfInnhold.tilPdfBestilling())
        val nyPdf = klient.lagPdf(bestilling).getOrElse {
            throw IllegalStateException("PdfBygger feilet")
        }
        val pdfTekst = PdfTekst.ord(nyPdf).joinToString(" ") { it.ord }

        fun normalisert(verdi: String) = PdfTekst.innholdsord(verdi).joinToString(" ")
        assertContains(pdfTekst, normalisert(pdfInnhold.fritekst))
        assertContains(pdfTekst, normalisert(pdfInnhold.klageDato))
        assertContains(pdfTekst, normalisert(pdfInnhold.saksbehandlerNavn))
        assertContains(pdfTekst, normalisert(requireNotNull(pdfInnhold.attestantNavn)))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.fornavn))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.etternavn))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.fødselsnummer))
        assertContains(pdfTekst, normalisert(pdfInnhold.saksnummer.toString()))

        return pdfTekst
    }

    private fun genererOgVerifiserAvvistKlagePdf(dødsbo: Boolean): String {
        val pdfInnhold = avvistKlageCommand
            .copy(dødsbo = dødsbo)
            .fromBrevCommand(
                personalia = personalia,
                saksbehandlerNavn = "Malenia, blade of Miquella",
                attestantNavn = "James Eugene Raynor",
            )
        val bestilling = requireNotNull(pdfInnhold.tilPdfBestilling())
        val nyPdf = klient.lagPdf(bestilling).getOrElse {
            throw IllegalStateException("PdfBygger feilet")
        }
        val pdfTekst = PdfTekst.ord(nyPdf).joinToString(" ") { it.ord }

        fun normalisert(verdi: String) = PdfTekst.innholdsord(verdi).joinToString(" ")
        assertContains(pdfTekst, normalisert(pdfInnhold.fritekst))
        assertContains(pdfTekst, normalisert(pdfInnhold.saksbehandlerNavn))
        assertContains(pdfTekst, normalisert(requireNotNull(pdfInnhold.attestantNavn)))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.fornavn))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.etternavn))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.fødselsnummer))
        assertContains(
            pdfTekst,
            normalisert(pdfInnhold.saksnummer.toString()),
        )

        return pdfTekst
    }
}
