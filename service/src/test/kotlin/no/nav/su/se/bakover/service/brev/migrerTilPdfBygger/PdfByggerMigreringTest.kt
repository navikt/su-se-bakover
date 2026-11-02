package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger

import arrow.core.getOrElse
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.bygger.GenerellData
import dokument.domain.generer.bygger.PdfBestilling
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forhåndsvarselPdf
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

val testDataJson = mapOf(
    DokumentMal.Søknad to Pair(soknadPdfJson, forventetSoknadInnnholdVerdier),
)
val testDataTypet = mapOf(
    DokumentMal.Forhåndsvarsel to Pair(forhåndsvarselPdf, forventetForhåndsvarselVerdier),
)

@Tag("pdf-integrasjon")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdfByggerMigreringTest {
    private val pdfBygger = GenericContainer(
        // Bruker versjon i tag publisert 28 oktober 2026:
        // https://github.com/navikt/pensjonsbrev/pkgs/container/pensjonsbrev%2Fpdf-bygger/1460179211?tag=103124d0b863a6f132121cfdf36d89027abb9a21
        DockerImageName.parse("ghcr.io/navikt/pensjonsbrev/pdf-bygger:103124d0b863a6f132121cfdf36d89027abb9a21"),
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

    private fun gammelPdf(malnavn: String): ByteArray {
        return requireNotNull(
            javaClass.classLoader.getResourceAsStream("pdfByggerMigreringTest/$malnavn-gammel.pdf"),
        ).use {
            it.readAllBytes()
        }
    }

    @TestFactory
    fun `pdf fra pdf-bygger inneholder samme tekst som pdf fra pdfgen generell data`() =
        testDataJson.mapNotNull { (mal, testData) ->
            val (innholdJson, forventetInnholdVerdier) = testData
            DynamicTest.dynamicTest(mal.navn) {
                val bestilling = mal.lagInnholdGenerell(GenerellData.parse(innholdJson))
                sammenlignGammelOgNyPdf(mal.navn, bestilling, forventetInnholdVerdier)
            }
        }

    @TestFactory
    fun `pdf fra pdf-bygger inneholder samme tekst som pdf fra pdfgen typet data`() =
        testDataTypet.mapNotNull { (mal, testData) ->
            val (pdfInnhold, forventetInnholdVerdier) = testData
            DynamicTest.dynamicTest(mal.navn) {
                val bestilling = mal.lagInnhold(pdfInnhold)
                sammenlignGammelOgNyPdf(mal.navn, bestilling, forventetInnholdVerdier)
            }
        }

    private fun sammenlignGammelOgNyPdf(
        mal: String,
        bestilling: PdfBestilling,
        forventet: List<String>,
    ) {
        val gammelPdf = gammelPdf(mal)
        Files.write(utdata.resolve("$mal-gammel.pdf"), gammelPdf)
        val gammelResultat = sammenlignOrd(forventet, PdfTekst.ord(gammelPdf))

        val nyPdf = klient.lagPdf(bestilling).getOrElse { throw IllegalStateException("PdfBygger feilet") }
        Files.write(utdata.resolve("$mal-ny.pdf"), nyPdf)
        val nyResultat = sammenlignOrd(forventet, PdfTekst.ord(nyPdf))

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
