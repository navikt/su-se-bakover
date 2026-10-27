package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger

import arrow.core.getOrElse
import dokument.domain.pdf.PdfTemplate
import dokument.domain.pdf.bygger.GenerellData
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forhåndsvarselPdfJson
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forventetForhåndsvarselVerdier
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

val migreringstestDataPdfBygger = mapOf(
    PdfTemplate.Søknad to Pair(soknadPdfJson, forventetSoknadInnnholdVerdier),
    PdfTemplate.Forhåndsvarsel to Pair(forhåndsvarselPdfJson, forventetForhåndsvarselVerdier),
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

    private fun gammelPdf(mal: PdfTemplate): ByteArray {
        val navn = mal.navn
        return requireNotNull(
            javaClass.classLoader.getResourceAsStream("pdfByggerMigreringTest/$navn-gammel.pdf"),
        ).use {
            it.readAllBytes()
        }
    }

    @TestFactory
    fun `pdf fra pdf-bygger inneholder samme tekst som pdf fra pdfgen`() =
        migreringstestDataPdfBygger.mapNotNull { (mal, testData) ->
            val malNavn = mal.navn
            val (innholdJson, forventetInnholdVerdier) = testData
            DynamicTest.dynamicTest(malNavn) {
                val bestilling = mal.malInnholdGenerell?.let { it(GenerellData.parse(innholdJson)) }
                    ?: throw IllegalStateException("Malinnhold mangler eller ikke implementert")
                val gammelPdf = gammelPdf(mal)

                val ny = klient.lagPdf(bestilling).getOrElse {
                    throw IllegalStateException("PdfBygger feilet")
                }

                Files.write(utdata.resolve("$malNavn-gammel.pdf"), gammelPdf)
                Files.write(utdata.resolve("$malNavn-ny.pdf"), ny)

                val gammelResultat = sammenlignOrd(forventetInnholdVerdier, PdfTekst.ord(gammelPdf))
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
                if (feil.isNotEmpty()) throw AssertionError("$malNavn:\n" + feil.joinToString("\n"))
            }
        }
}
