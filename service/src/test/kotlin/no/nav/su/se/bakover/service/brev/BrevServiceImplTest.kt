package no.nav.su.se.bakover.service.brev

import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.FantIkkeDokument
import dokument.domain.brev.HentDokumenterForIdType
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.client.KunneIkkeGenererePdf
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfGenerator
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand
import no.nav.su.se.bakover.domain.brev.jsonRequest.FritekstPdfInnhold
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.dokumentMedMetadataInformasjonAnnet
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagMedBeregning
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.IdentClient
import person.domain.KunneIkkeHenteNavnForNavIdent
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import person.domain.PersonService
import økonomi.application.utbetaling.UtbetalingService
import java.time.Clock
import java.time.Year
import java.util.UUID

internal class BrevServiceImplTest {

    private companion object {
        private val fnr = Fnr(fnr = "12345678901")
        private val person = Person(
            ident = Ident(
                fnr = fnr,
                aktørId = AktørId(aktørId = "123"),
            ),
            navn = Person.Navn(fornavn = "Tore", mellomnavn = null, etternavn = "Strømøy"),
            fødsel = Person.Fødsel.MedFødselsår(
                år = Year.of(1956),
            ),
        )
    }

    @Test
    fun `happy case`() {
        val pdf = pdfATom()

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<PdfInnhold>()) } doReturn pdf.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }
        val identClientMock = mock<IdentClient> {
            on { hentNavnForNavIdent(any()) } doReturn "testname".right()
        }

        val dokumentCommand = fritekstDokumentCommand()
        val serviceOgMocks = ServiceOgMocks(
            pdfGenerator = pdfGeneratorMock,
            personService = personServiceMock,
            identClient = identClientMock,
        )
        val actual = serviceOgMocks.brevService.lagDokument(dokumentCommand)
            .getOrFail() as Dokument.UtenMetadata.Informasjon.Annet
        actual.generertDokument shouldBe pdf
        actual.tittel shouldBe dokumentCommand.brevTittel
        actual.generertDokumentJson shouldBe """{"personalia":{"dato":"01.01.2021","fødselsnummer":"${dokumentCommand.fødselsnummer}","fornavn":"Tore","etternavn":"Strømøy","saksnummer":12345676},"saksbehandlerNavn":"testname","tittel":"En tittel","fritekst":"Litt fritekst","sakstype":"UFØRE","erAldersbrev":false}"""

        verify(pdfGeneratorMock).genererPdf(
            argThat<PdfInnhold> {
                it shouldBe FritekstPdfInnhold(
                    personalia = PersonaliaPdfInnhold(
                        dato = "01.01.2021",
                        fødselsnummer = dokumentCommand.fødselsnummer.toString(),
                        fornavn = person.navn.fornavn,
                        etternavn = person.navn.etternavn,
                        saksnummer = dokumentCommand.saksnummer.nummer,
                    ),
                    saksbehandlerNavn = "testname",
                    tittel = dokumentCommand.brevTittel,
                    fritekst = dokumentCommand.fritekst,
                )
            },
        )
        verify(personServiceMock).hentPersonMedSystembruker(dokumentCommand.fødselsnummer)
        verify(identClientMock).hentNavnForNavIdent(saksbehandler)

        serviceOgMocks.verifyNoMoreInteraction()
    }

    private fun fritekstDokumentCommand() = FritekstDokumentCommand(
        fødselsnummer = Fnr.generer(),
        saksnummer = saksnummer,
        saksbehandler = saksbehandler,
        brevTittel = "En tittel",
        fritekst = "Litt fritekst",
    )

    @Test
    fun `lager ikke brev når pdf-generator kall failer`() {
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<PdfInnhold>()) } doReturn KunneIkkeGenererePdf.left()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }
        val identClientMock = mock<IdentClient> {
            on { hentNavnForNavIdent(any()) } doReturn "testname".right()
        }

        val dokumentCommand = fritekstDokumentCommand()
        ServiceOgMocks(
            pdfGenerator = pdfGeneratorMock,
            personService = personServiceMock,
            identClient = identClientMock,
        ).let {
            it.brevService.lagDokument(dokumentCommand) shouldBe KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
            // Disse testes i happy case
            verify(pdfGeneratorMock).genererPdf(any<PdfInnhold>())
            verify(personServiceMock).hentPersonMedSystembruker(any())
            verify(identClientMock).hentNavnForNavIdent(any())
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `henter dokumenter for ulike typer id-er`() {
        val sakId = UUID.randomUUID()
        val vedtakId = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val revurderingId = UUID.randomUUID()
        val klageId = KlageId.generer()
        val randomId = UUID.randomUUID()

        val sakDokument = lagDokument(Dokument.Metadata(sakId = sakId))
        val vedtakDokument = lagDokument(Dokument.Metadata(sakId = sakId, vedtakId = vedtakId))
        val søknadDokument = lagDokument(Dokument.Metadata(sakId = sakId, søknadId = søknadId))
        val revurderingDokument = lagDokument(Dokument.Metadata(sakId = sakId, revurderingId = revurderingId))
        val klageDokument = lagDokument(Dokument.Metadata(sakId = sakId, klageId = klageId.value))

        val dokumentRepoMock = mock<DokumentRepo> {
            on { hentForSak(sakId) } doReturn listOf(sakDokument)
            on { hentForSak(randomId) } doReturn emptyList()
            on { hentForVedtak(vedtakId) } doReturn listOf(vedtakDokument)
            on { hentForVedtak(randomId) } doReturn emptyList()
            on { hentForSøknad(søknadId) } doReturn listOf(søknadDokument)
            on { hentForSøknad(randomId) } doReturn emptyList()
            on { hentForRevurdering(revurderingId) } doReturn listOf(revurderingDokument)
            on { hentForKlage(klageId.value) } doReturn listOf(klageDokument)
            on { hentForRevurdering(randomId) } doReturn emptyList()
        }

        val service = ServiceOgMocks(
            dokumentRepo = dokumentRepoMock,
        ).brevService

        service.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForSak(sakId)) shouldBe listOf(
            sakDokument,
        )
        service.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForSak(randomId)) shouldBe emptyList()
        service.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtakId)) shouldBe listOf(
            vedtakDokument,
        )
        service.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(randomId)) shouldBe emptyList()
        service.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForSøknad(søknadId)) shouldBe listOf(
            søknadDokument,
        )
        service.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForSøknad(randomId)) shouldBe emptyList()
        service.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForRevurdering(revurderingId)) shouldBe listOf(
            revurderingDokument,
        )
        service.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForRevurdering(randomId)) shouldBe emptyList()
        service.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForKlage(klageId.value)) shouldBe listOf(
            klageDokument,
        )
    }

    @Test
    fun `personservice finner ikke personen`() {
        val vedtak = vedtakSøknadsbehandlingIverksattAvslagMedBeregning().second
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        ServiceOgMocks(
            personService = personServiceMock,
        ).let {
            it.brevService.lagDokument(
                vedtak.behandling.lagBrevCommand(
                    satsFactory = satsFactoryTestPåDato(),
                ),
            ) shouldBe KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left()
        }
    }

    @Test
    fun `identClient klarer ikke hente navnet`() {
        val vedtak = vedtakSøknadsbehandlingIverksattAvslagMedBeregning().second
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<IdentClient> {
            on { hentNavnForNavIdent(any()) } doReturn KunneIkkeHenteNavnForNavIdent.KallTilMicrosoftGraphApiFeilet.left()
        }

        ServiceOgMocks(
            personService = personServiceMock,
            identClient = microsoftGraphApiOppslagMock,
        ).let {
            it.brevService.lagDokument(
                vedtak.behandling.lagBrevCommand(
                    satsFactory = satsFactoryTestPåDato(),
                ),
            ) shouldBe KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left()
            verify(it.personService).hentPersonMedSystembruker(vedtak.behandling.fnr)
            verify(it.identClient).hentNavnForNavIdent(vedtak.behandling.saksbehandler)
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `henter dokument med id`() {
        val dokumentId = UUID.randomUUID()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokument(any()) } doReturn dokumentMedMetadataInformasjonAnnet()
        }
        ServiceOgMocks(
            dokumentRepo = dokumentRepo,
        ).let {
            val actual = it.brevService.hentDokument(dokumentId)
            actual.shouldBeRight()
            verify(it.dokumentRepo).hentDokument(argThat { it shouldBe dokumentId })
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `left dersom dokument med id ikke finnes`() {
        val dokumentId = UUID.randomUUID()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokument(any()) } doReturn null
        }
        ServiceOgMocks(
            dokumentRepo = dokumentRepo,
        ).let {
            val actual = it.brevService.hentDokument(dokumentId)
            actual shouldBe FantIkkeDokument.left()
            verify(it.dokumentRepo).hentDokument(argThat { it shouldBe dokumentId })
            it.verifyNoMoreInteraction()
        }
    }

    private fun lagDokument(metadata: Dokument.Metadata): Dokument.MedMetadata.Vedtak {
        val utenMetadata = Dokument.UtenMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            tittel = "tittel",
            generertDokument = pdfATom(),
            generertDokumentJson = "{}",
        )
        return utenMetadata.leggTilMetadata(metadata, distribueringsadresse = null)
    }

    data object DummyPdfInnhold : PdfInnhold {
        override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.AvslagsVedtak
    }

    private data class ServiceOgMocks(
        val pdfGenerator: PdfGenerator = mock(),
        val dokumentRepo: DokumentRepo = mock(),
        val personService: PersonService = mock(),
        val sessionFactory: SessionFactory = mock(),
        val identClient: IdentClient = mock(),
        val utbetalingService: UtbetalingService = mock(),
        val clock: Clock = fixedClock,
    ) {
        val brevService = BrevServiceImpl(
            pdfGenerator = pdfGenerator,
            dokumentRepo = dokumentRepo,
            personService = personService,
            identClient = identClient,
            clock = clock,
        )

        fun verifyNoMoreInteraction() {
            verifyNoMoreInteractions(
                pdfGenerator,
                dokumentRepo,
                personService,
                sessionFactory,
                identClient,
                utbetalingService,
            )
        }
    }
}
