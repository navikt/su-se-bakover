package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger

import arrow.core.getOrElse
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.bygger.PdfBestilling
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.service.brev.PdfByggerExtension
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forventetSoknadAlderVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forventetSoknadUføreVerdier
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.soknadAlderPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.soknadUførePdf
import no.nav.su.se.bakover.service.brev.tilPdfBestilling
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Files
import java.nio.file.Path

/**
 * Søknad testes for seg fordi den har ett testsett per sakstype (uføre og alder),
 * mens [PdfByggerMigreringTest] har ett testsett per [DokumentMal].
 */
@Tag("pdf-integrasjon")
@ExtendWith(PdfByggerExtension::class)
class PdfByggerSøknadMigreringTest(private val klient: PdfByggerKlientImpl) {
    private val utdata = Path.of("build/pdf-migreringstest-søknad").also { Files.createDirectories(it) }

    private val testdata = listOf(
        "uføre" to Pair(soknadUførePdf, forventetSoknadUføreVerdier),
        "alder" to Pair(soknadAlderPdf, forventetSoknadAlderVerdier),
    )

    @TestFactory
    fun `pdf fra pdf-bygger inneholder samme tekst som pdf fra pdfgen for søknad`() =
        testdata.map { (sakstype, data) ->
            val (pdfInnhold, forventet) = data
            val malOgSakstype = "${DokumentMal.Søknad.navn}-$sakstype"
            DynamicTest.dynamicTest(malOgSakstype) {
                val bestilling = requireNotNull(pdfInnhold.tilPdfBestilling())
                pdfHarForventaVerdier(malOgSakstype, bestilling, forventet)
            }
        }

    private fun pdfHarForventaVerdier(
        malOgSakstype: String,
        bestilling: PdfBestilling,
        forventet: List<String>,
    ) {
        val nyPdf = klient.lagPdf(bestilling).getOrElse { throw IllegalStateException("PdfBygger feilet") }
        Files.write(utdata.resolve("$malOgSakstype.pdf"), nyPdf)
        val nyResultat = sammenlignOrd(forventet, PdfTekst.ord(nyPdf))

        val tillatt = Ramme.tillatteOrd(bestilling)
        val uventetNy = nyResultat.ekstra.filterNot { it in tillatt }

        val feil = buildList {
            if (nyResultat.mangler.isNotEmpty()) add("ny PDF mangler: ${nyResultat.mangler}")
            if (uventetNy.isNotEmpty()) add("ny PDF har uventet tekst: $uventetNy")
        }
        if (feil.isNotEmpty()) throw AssertionError("$malOgSakstype:\n" + feil.joinToString("\n"))
    }
}
