package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger

import arrow.core.getOrElse
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.bygger.GenerellData
import dokument.domain.generer.bygger.PdfBestilling
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.avvistKlagePdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.avvistKlageVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forhåndsvarselPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forventetForhåndsvarselVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forventetSoknadInnnholdVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.fritekstDokumentPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.fritekstDokumentVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.innkallingKontrollsamtalePdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.innkallingKontrollsamtaleVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.kontrollnotatPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.kontrollnotatVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.sendtTilKlageinstansPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.sendtTilKlageinstansVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.soknadPdfJson
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.vedtakAvslagPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.vedtakAvslagVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.vedtakInnvilgelsePdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.vedtakInnvilgelseVerdier
import no.nav.su.se.bakover.service.brev.tilPdfBestilling
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
import kotlin.to

val testDataJson = mapOf(
    DokumentMal.Søknad to Pair(soknadPdfJson, forventetSoknadInnnholdVerdier),
)
val testDataTypet: Map<DokumentMal, Pair<PdfInnhold, List<String>>> = mapOf(
    DokumentMal.Forhåndsvarsel to Pair(forhåndsvarselPdf, forventetForhåndsvarselVerdier),
    DokumentMal.InnkallingTilKontrollsamtale to Pair(innkallingKontrollsamtalePdf, innkallingKontrollsamtaleVerdier),
    DokumentMal.Kontrollnotat to Pair(kontrollnotatPdf, kontrollnotatVerdier),
    DokumentMal.InnvilgetVedtak to Pair(vedtakInnvilgelsePdf, vedtakInnvilgelseVerdier),
    DokumentMal.FritekstDokument("Jeg er fritekst-dokumentets tittel") to Pair(fritekstDokumentPdf, fritekstDokumentVerdier),
    DokumentMal.Klage.Oppretthold to Pair(sendtTilKlageinstansPdf, sendtTilKlageinstansVerdier),
    DokumentMal.Klage.Avvist to Pair(avvistKlagePdf, avvistKlageVerdier),
    DokumentMal.AvslagsVedtak to Pair(vedtakAvslagPdf, vedtakAvslagVerdier),
    // DokumentMal.TrukketSøknad to Pair(søknadTrukketPdf, søknadTrukketVerdier),
    // DokumentMal.AvvistSøknadVedtak to Pair(avvistSøknadVedtakPdf, avvistSøknadVedtakVerdier),
    // DokumentMal.AvvistSøknadFritekst to Pair(avvistSøknadFritekstPdf, avvistSøknadFritekstVerdier),
    // DokumentMal.Opphørsvedtak to Pair(opphørsvedtakPdf, opphørsvedtakVerdier),
    // DokumentMal.ForhåndsvarselTilbakekrevingsbehandling to Pair(forhåndsvarselTilbakekrevingsbehandlingPdf, forhåndsvarselTilbakekrevingsbehandlingVerdier),
    // DokumentMal.VedtaksbrevTilbakekrevingsbehandling to Pair(vedtaksbrevTilbakekrevingsbehandlingPdf, vedtaksbrevTilbakekrevingsbehandlingVerdier),
    // DokumentMal.ForhåndsvarselTilbakekrevingsbehandlingDødsbo to Pair(forhåndsvarselTilbakekrevingsbehandlingDødsboPdf, forhåndsvarselTilbakekrevingsbehandlingDødsboVerdier),
    // DokumentMal.VedtaksbrevTilbakekrevingsbehandlingDødsbo to Pair(vedtaksbrevTilbakekrevingsbehandlingDødsboPdf, vedtaksbrevTilbakekrevingsbehandlingDødsboVerdier),
    // DokumentMal.PåminnelseNyStønadsperiode to Pair(påminnelseOmNyStønadsperiodePdf, påminnelseOmNyStønadsperiodeVerdier),
    // DokumentMal.Revurdering.Inntekt to Pair(revurderingAvInntektPdf, revurderingAvInntektVerdier),
    // DokumentMal.Revurdering.Avslutt to Pair(avsluttRevurderingPdf, avsluttRevurderingVerdier),
    // DokumentMal.VedtaksbrevReguleringAldersfradrag to Pair(vedtaksbrevReguleringAldersfradragPdf, vedtaksbrevReguleringAldersfradragVerdier),
)

@Tag("pdf-integrasjon")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdfByggerMigreringTest {
    private val pdfBygger = GenericContainer(
        // Bruker versjon i tag publisert 6 november 2026:
        // https://github.com/navikt/pensjonsbrev/pkgs/container/pensjonsbrev%2Fpdf-bygger/1518344620?tag=1bbb4ef474430d2359560dd377800b275ef1f8b5
        DockerImageName.parse("ghcr.io/navikt/pensjonsbrev/pdf-bygger:1bbb4ef474430d2359560dd377800b275ef1f8b5"),
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
                val bestilling = mal.lagBestilling(GenerellData.parse(innholdJson))
                sammenlignGammelOgNyPdf(mal.navn, bestilling, forventetInnholdVerdier)
            }
        }

    @TestFactory
    fun `pdf fra pdf-bygger inneholder samme tekst som pdf fra pdfgen typet data`() =
        testDataTypet.mapNotNull { (mal, testData) ->
            val (pdfInnhold, forventetInnholdVerdier) = testData
            DynamicTest.dynamicTest(mal.navn) {
                val bestilling = requireNotNull(pdfInnhold.tilPdfBestilling())
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
