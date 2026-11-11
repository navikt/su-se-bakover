package no.nav.su.se.bakover.service.brev

import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeGenererePdf
import dokument.domain.PdfGenerator
import dokument.domain.PensjonPdfByggerKlient
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.bygger.GenerellData
import dokument.domain.generer.bygger.maler.forhåndsvarsel
import dokument.domain.generer.bygger.maler.fritekstDokument
import dokument.domain.generer.bygger.maler.innkallingKontrollsamtale
import dokument.domain.generer.bygger.maler.kontrollnotat
import dokument.domain.generer.bygger.maler.soknad
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forhåndsvarselPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.fritekstDokumentPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.innkallingKontrollsamtalePdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.kontrollnotatPdf
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import person.domain.Person
import java.io.ByteArrayOutputStream
import java.util.UUID

internal class PdfByggerServiceTest {

    @Test
    fun `velger riktig malbygger for støttede innholdsmodeller`() {
        val forventninger = listOf(
            kontrollnotatPdf to kontrollnotat(kontrollnotatPdf.dokumentMal.tittel, kontrollnotatPdf),
            forhåndsvarselPdf to forhåndsvarsel(forhåndsvarselPdf.dokumentMal.tittel, forhåndsvarselPdf),
            innkallingKontrollsamtalePdf to innkallingKontrollsamtale(
                innkallingKontrollsamtalePdf.dokumentMal.tittel,
                innkallingKontrollsamtalePdf,
            ),
            fritekstDokumentPdf to fritekstDokument(fritekstDokumentPdf.dokumentMal.tittel, fritekstDokumentPdf),
        )
        val pdf = PdfA(lagPdf(antallSider = 1))
        val klient = mock<PensjonPdfByggerKlient> {
            on { lagPdf(any()) } doReturn pdf.unsafeBytes().right()
        }
        val gammelKlient = mock<PdfGenerator>()
        val service = PdfByggerService(klient, gammelKlient, ApplicationConfig.RuntimeEnvironment.Test, fixedClock)

        forventninger.forEach { (innhold, forventetBestilling) ->
            service.genererPdf(innhold) shouldBe pdf.right()
            verify(klient).lagPdf(forventetBestilling)
        }
        verifyNoInteractions(gammelKlient)
    }

    @Test
    fun `velger søknadsbygger for konkret søknadsinnhold`() {
        val innhold = SøknadPdfInnhold.create(
            saksnummer = Saksnummer(2021),
            sakstype = Sakstype.UFØRE,
            søknadsId = UUID.randomUUID(),
            navn = Person.Navn("Test", null, "Testesen"),
            søknadOpprettet = fixedTidspunkt,
            søknadInnhold = søknadinnholdUføre(),
            clock = fixedClock,
        )
        val pdf = PdfA(lagPdf(antallSider = 1))
        val klient = mock<PensjonPdfByggerKlient> {
            on { lagPdf(any()) } doReturn pdf.unsafeBytes().right()
        }
        val gammelKlient = mock<PdfGenerator>()
        val service = PdfByggerService(klient, gammelKlient, ApplicationConfig.RuntimeEnvironment.Test, fixedClock)

        service.genererPdf(innhold) shouldBe pdf.right()

        verify(klient).lagPdf(soknad(GenerellData.parse(innhold.toJson())))
        verifyNoInteractions(gammelKlient)
    }

    @Test
    fun `aktivert mal med ukjent innholdstype returnerer Left`() {
        val klient = mock<PensjonPdfByggerKlient>()
        val gammelKlient = mock<PdfGenerator>()
        val service = PdfByggerService(klient, gammelKlient, ApplicationConfig.RuntimeEnvironment.Test, fixedClock)

        listOf(DokumentMal.Kontrollnotat, DokumentMal.Søknad).forEach { mal ->
            val innhold = object : PdfInnhold {
                override val dokumentMal = mal
                override val sakstype = Sakstype.UFØRE
            }
            service.genererPdf(innhold) shouldBe KunneIkkeGenererePdf.left()
        }

        verifyNoInteractions(klient, gammelKlient)
    }

    private val pdfInnhold = object : PdfInnhold {
        // Mal med både pdfByggerDev = false og pdfByggerProd = false
        override val dokumentMal = DokumentMal.VedtaksbrevTilbakekrevingsbehandling
        override val sakstype: Sakstype = Sakstype.UFØRE
    }

    @ParameterizedTest
    @EnumSource(ApplicationConfig.RuntimeEnvironment::class)
    fun `bruker gammel klient når malen ikke er aktivert for pdf-bygger`(runtimeEnvironment: ApplicationConfig.RuntimeEnvironment) {
        verifiserGammelKlientBrukes(runtimeEnvironment)
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationConfig.RuntimeEnvironment::class, names = ["Prod"])
    fun `bruker bare pdf-bygger når pdfByggerProd er true i prod`(runtimeEnvironment: ApplicationConfig.RuntimeEnvironment) {
        verifiserPdfByggerKlientBrukes(
            runtimeEnvironment = runtimeEnvironment,
            pdfByggerDev = false,
            pdfByggerProd = true,
            forventGammelKlient = false,
        )
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationConfig.RuntimeEnvironment::class, names = ["Test"])
    fun `bruker kun pdf-bygger når pdfByggerDev er true for unittester`(runtimeEnvironment: ApplicationConfig.RuntimeEnvironment) {
        verifiserPdfByggerKlientBrukes(
            runtimeEnvironment = runtimeEnvironment,
            pdfByggerDev = true,
            pdfByggerProd = false,
            forventGammelKlient = false,
        )
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationConfig.RuntimeEnvironment::class, names = ["Dev"])
    fun `bruker begge klienter når pdfByggerDev er true i dev`(runtimeEnvironment: ApplicationConfig.RuntimeEnvironment) {
        verifiserPdfByggerKlientBrukes(
            runtimeEnvironment = runtimeEnvironment,
            pdfByggerDev = true,
            pdfByggerProd = false,
            forventGammelKlient = true,
        )
    }

    @Test
    fun `bruker bare gammel klient lokalt når pdf-bygger ikke er slått på`() {
        kontrollnotatPdf.dokumentMal.pdfByggerDev shouldBe true
        val pdf = PdfA(lagPdf(antallSider = 1))
        val klient = mock<PensjonPdfByggerKlient>()
        val gammelKlient = mock<PdfGenerator> {
            on { genererPdf(any()) } doReturn pdf.right()
        }

        val resultat = PdfByggerService(
            klient = klient,
            gammelKlient = gammelKlient,
            runtimeEnv = ApplicationConfig.RuntimeEnvironment.Local,
            clock = fixedClock,
        ).genererPdf(kontrollnotatPdf)

        resultat shouldBe pdf.right()
        verify(gammelKlient).genererPdf(kontrollnotatPdf)
        verifyNoInteractions(klient)
    }

    @Test
    fun `bruker begge klienter lokalt når pdf-bygger er slått på`() {
        verifiserPdfByggerKlientBrukes(
            runtimeEnvironment = ApplicationConfig.RuntimeEnvironment.Local,
            pdfByggerDev = true,
            pdfByggerProd = false,
            forventGammelKlient = true,
            brukPdfByggerLokalt = true,
        )
    }

    @Test
    fun `bruker gammel klient lokalt når pdf-bygger er slått på men malen ikke er aktivert`() {
        verifiserGammelKlientBrukes(
            runtimeEnvironment = ApplicationConfig.RuntimeEnvironment.Local,
            brukPdfByggerLokalt = true,
        )
    }

    private fun verifiserPdfByggerKlientBrukes(
        runtimeEnvironment: ApplicationConfig.RuntimeEnvironment,
        pdfByggerDev: Boolean,
        pdfByggerProd: Boolean,
        forventGammelKlient: Boolean,
        brukPdfByggerLokalt: Boolean = false,
    ) {
        // Ingen ekte mal har pdfByggerProd = true ennå, så vi overstyrer flaggene i testen.
        val mal = spy(DokumentMal.Kontrollnotat)
        doReturn(pdfByggerDev).whenever(mal).pdfByggerDev
        doReturn(pdfByggerProd).whenever(mal).pdfByggerProd
        val innhold = spy(kontrollnotatPdf)
        doReturn(mal).whenever(innhold).dokumentMal
        val bestilling = kontrollnotat(mal.tittel, innhold)
        val bytes = lagPdf(antallSider = 1)
        val gammelPdf = PdfA(lagPdf(antallSider = 2))
        val klient = mock<PensjonPdfByggerKlient> {
            on { lagPdf(any()) } doReturn bytes.right()
        }
        val gammelKlient = mock<PdfGenerator> {
            on { genererPdf(any()) } doReturn gammelPdf.right()
        }

        val resultat = PdfByggerService(
            klient = klient,
            gammelKlient = gammelKlient,
            runtimeEnv = runtimeEnvironment,
            clock = fixedClock,
            brukPdfByggerLokalt = brukPdfByggerLokalt,
        ).genererPdf(innhold)

        verify(mal).lagBestilling(innhold)
        verify(klient).lagPdf(bestilling)
        if (forventGammelKlient) {
            verify(gammelKlient).genererPdf(innhold)
            Loader.loadPDF(resultat.getOrFail().unsafeBytes()).use { pdf ->
                pdf.numberOfPages shouldBe 3
            }
        } else {
            resultat shouldBe PdfA(bytes).right()
            verifyNoInteractions(gammelKlient)
        }
    }

    private fun lagPdf(antallSider: Int): ByteArray = PDDocument().use { dokument ->
        repeat(antallSider) { dokument.addPage(PDPage()) }
        ByteArrayOutputStream().use { output ->
            dokument.save(output)
            output.toByteArray()
        }
    }

    private fun verifiserGammelKlientBrukes(
        runtimeEnvironment: ApplicationConfig.RuntimeEnvironment,
        brukPdfByggerLokalt: Boolean = false,
    ) {
        pdfInnhold.dokumentMal.pdfByggerDev shouldBe false
        pdfInnhold.dokumentMal.pdfByggerProd shouldBe false

        val pdf = PdfA("gammel".toByteArray())
        val gammelKlient = mock<PdfGenerator> {
            on { genererPdf(any()) } doReturn pdf.right()
        }
        val klient = mock<PensjonPdfByggerKlient>()

        val resultat = PdfByggerService(
            klient = klient,
            gammelKlient = gammelKlient,
            runtimeEnv = runtimeEnvironment,
            clock = fixedClock,
            brukPdfByggerLokalt = brukPdfByggerLokalt,
        ).genererPdf(pdfInnhold)

        resultat shouldBe pdf.right()
        verify(gammelKlient).genererPdf(pdfInnhold)
        verifyNoInteractions(klient)
    }
}
