package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger

import dokument.domain.pdf.bygger.PdfByggerMalOgBestilling
import dokument.domain.pdf.bygger.Verdi
import dokument.domain.pdf.bygger.maler.soknad
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forventetSoknadInnnholdVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.soknadPdfJson
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

val malerTilTest: Map<String, PdfByggerMalOgBestilling> = listOf(
    soknad,
    /*
    alderHarIkkeBrevEnda,
    avsluttRevurdering,
    avvistKlage,
    avvistSøknadFritekst,
    avvistSøknadVedtak,
    forhåndsvarsel,
    forhåndsvarselTilbakekrevingsbehandling,
    forhåndsvarselTilbakekrevingsbehandlingDødsbo,
    fritekstDokument,
    innkallingKontrollsamtale,
    kontrollnotat,
    opphørsvedtak,
    påminnelseOmNyStønadsperiode,
    revurderingAvInntekt,
    sendtTilKlageinstans,
    skattegrunnlag,
    søknadTrukket,
    vedtakAvslag,
    vedtakInnvilgelse,
    vedtaksbrevReguleringAldersfradrag,
    vedtaksbrevTilbakekrevingsbehandling,
    vedtaksbrevTilbakekrevingsbehandlingDødsbo,
     */
).associateBy { it.navn }

val migreringstestDataPdfBygger = mapOf(
    soknad.navn to Pair(soknadPdfJson, forventetSoknadInnnholdVerdier),
)

@Tag("pdf-integrasjon")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdfByggerMigreringTest {
    private val pdfBygger = GenericContainer(
        DockerImageName.parse(
            System.getenv("PDF_BYGGER_IMAGE") ?: "ghcr.io/navikt/pensjonsbrev/pdf-bygger:main",
        ),
    )
        .withExposedPorts(8080)
        .withEnv("JAVA_TOOL_OPTIONS", "-Dio.ktor.development=true")
        .withEnv("PDF_BYGGER_COMPILE_TMP_DIR", "/tmp")
        .withEnv("PDF_COMPILE_TIMEOUT_SECONDS", "200")
        .waitingFor(Wait.forHttp("/isReady").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)))

    private lateinit var klient: PdfByggerKlientImpl
    private val utdata = Path.of("build/pdf-integrasjon").also { Files.createDirectories(it) }

    @BeforeAll
    fun start() {
        pdfBygger.start()
        klient = PdfByggerKlientImpl("http://${pdfBygger.host}:${pdfBygger.getMappedPort(8080)}/", AzureClientStub, "")
    }

    @AfterAll
    fun stopp() {
        pdfBygger.stop()
    }

    private fun gammelPdf(mal: String): ByteArray {
        return requireNotNull(
            javaClass.classLoader.getResourceAsStream("pdfByggerMigreringTest/$mal-gammel.pdf"),
        ).use {
            it.readAllBytes()
        }
    }

    @TestFactory
    fun `pdf fra pdf-bygger inneholder samme tekst som pdf fra pdfgen`() =
        migreringstestDataPdfBygger.mapNotNull { testData ->
            val mal = testData.key
            val innholdJson = testData.value.first
            val forventetInnholdVerdier = testData.value.second
            DynamicTest.dynamicTest(mal) {
                val bestilling = malerTilTest[mal]!!.lagBestillingMedMal(Verdi.parse(innholdJson))
                val gammel = gammelPdf(mal)

                val ny = klient.lagPdf(bestilling).getOrNull()!! // TODO brukes gjennom service ?

                Files.write(utdata.resolve("$mal-gammel.pdf"), gammel)
                Files.write(utdata.resolve("$mal-ny.pdf"), ny)

                val gammelResultat = sammenlignOrd(forventetInnholdVerdier, PdfTekst.ord(gammel))
                val nyResultat = sammenlignOrd(forventetInnholdVerdier, PdfTekst.ord(ny))

                val tillatt = Ramme.tillatteOrd(bestilling)
                val uventetNy = nyResultat.ekstra.filterNot { it in tillatt }
                val uventetGammel = gammelResultat.ekstra.filterNot { it in tillatt }

                val feil = buildList {
                    if (gammelResultat.mangler.isNotEmpty()) add("gammel PDF mangler: ${gammelResultat.mangler}")
                    if (nyResultat.mangler.isNotEmpty()) add("ny PDF mangler: ${nyResultat.mangler}")
                    if (uventetGammel.isNotEmpty()) add("gammel PDF har uventet tekst: $uventetGammel")
                    if (uventetNy.isNotEmpty()) add("ny PDF har uventet tekst: $uventetNy")
                }
                if (feil.isNotEmpty()) throw AssertionError("$mal:\n" + feil.joinToString("\n"))
            }
        }
}
