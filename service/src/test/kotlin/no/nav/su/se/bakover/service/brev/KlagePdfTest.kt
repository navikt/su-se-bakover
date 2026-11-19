import arrow.core.getOrElse
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.brev.jsonRequest.fromBrevCommand
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.PdfTekst
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.avvistKlagePdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.sendtTilKlageinstansPdf
import no.nav.su.se.bakover.service.brev.tilPdfBestilling
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertFalse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class KlagePdfTest {
    private val pdfBygger = GenericContainer(
        // Bruker versjon i tag publisert 6 november 2026:
        // https://github.com/navikt/pensjonsbrev/pkgs/container/pensjonsbrev%2Fpdf-bygger/1518344620?tag=main
        // Main er bakoveerkompatibel
        DockerImageName.parse("ghcr.io/navikt/pensjonsbrev/pdf-bygger:main"),
    )
        .withImagePullPolicy(PullPolicy.alwaysPull())
        .withExposedPorts(8080)
        .withCreateContainerCmdModifier { it.withPlatform("linux/amd64") }
        .withEnv("JAVA_TOOL_OPTIONS", "-Dio.ktor.development=true")
        .withEnv("PDF_BYGGER_COMPILE_TMP_DIR", "/tmp")
        .withEnv("PDF_COMPILE_TIMEOUT_SECONDS", "200")
        .waitingFor(Wait.forHttp("/isReady").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)))

    private lateinit var klient: PdfByggerKlientImpl

    @BeforeAll
    fun start() {
        pdfBygger.start()
        klient = PdfByggerKlientImpl("http://${pdfBygger.host}:${pdfBygger.getMappedPort(8080)}/", AzureClientStub, "")
    }

    @AfterAll
    fun stopp() {
        pdfBygger.stop()
    }

    val sendtTilKlageinstansCommand =
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

    val avvistKlageCommand = KlageDokumentCommand.Avvist(
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
                personalia = sendtTilKlageinstansPdf.personalia,
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
                personalia = avvistKlagePdf.personalia,
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
