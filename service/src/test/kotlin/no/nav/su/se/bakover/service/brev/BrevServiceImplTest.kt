package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.pdf.KunneIkkeGenererePdf
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Ident
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.HentDokumenterForIdType
import no.nav.su.se.bakover.domain.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagMedBeregning
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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
    fun `lager brev`() {
        val pdf = "".toByteArray()

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<BrevInnhold>()) } doReturn pdf.right()
        }

        ServiceOgMocks(
            pdfGenerator = pdfGeneratorMock,
        ).brevService.lagBrev(DummyRequest) shouldBe pdf.right()

        verify(pdfGeneratorMock).genererPdf(DummyBrevInnhold)

        verifyNoMoreInteractions(pdfGeneratorMock)
    }

    @Test
    fun `lager ikke brev når pdf-generator kall failer`() {
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(DummyBrevInnhold) } doReturn KunneIkkeGenererePdf.left()
        }

        ServiceOgMocks(
            pdfGenerator = pdfGeneratorMock,
        ).brevService.lagBrev(DummyRequest) shouldBe KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        verify(pdfGeneratorMock).genererPdf(DummyBrevInnhold)
        verifyNoMoreInteractions(pdfGeneratorMock)
    }

    @Test
    fun `henter dokumenter for ulike typer id-er`() {
        val sakId = UUID.randomUUID()
        val vedtakId = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val revurderingId = UUID.randomUUID()
        val klageId = UUID.randomUUID()
        val randomId = UUID.randomUUID()

        val sakDokument =
            lagDokument(Dokument.Metadata(sakId = sakId, bestillBrev = false))
        val vedtakDokument =
            lagDokument(Dokument.Metadata(sakId = sakId, vedtakId = vedtakId, bestillBrev = false))
        val søknadDokument =
            lagDokument(Dokument.Metadata(sakId = sakId, søknadId = søknadId, bestillBrev = false))
        val revurderingDokument =
            lagDokument(
                Dokument.Metadata(sakId = sakId, revurderingId = revurderingId, bestillBrev = false),
            )
        val klageDokument = lagDokument(Dokument.Metadata(sakId = sakId, klageId = klageId, bestillBrev = false))

        val dokumentRepoMock = mock<DokumentRepo> {
            on { hentForSak(sakId) } doReturn listOf(sakDokument)
            on { hentForSak(randomId) } doReturn emptyList()
            on { hentForVedtak(vedtakId) } doReturn listOf(vedtakDokument)
            on { hentForVedtak(randomId) } doReturn emptyList()
            on { hentForSøknad(søknadId) } doReturn listOf(søknadDokument)
            on { hentForSøknad(randomId) } doReturn emptyList()
            on { hentForRevurdering(revurderingId) } doReturn listOf(revurderingDokument)
            on { hentForKlage(klageId) } doReturn listOf(klageDokument)
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
        service.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForKlage(klageId)) shouldBe listOf(klageDokument)
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
            it.brevService.lagDokument(vedtak) shouldBe KunneIkkeLageDokument.KunneIkkeHentePerson.left()
        }
    }

    @Test
    fun `microsoftGraphApiOppslag klarer ikke hente navnet`() {
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
            it.brevService.lagDokument(vedtak) shouldBe KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
            verify(it.personService).hentPersonMedSystembruker(vedtak.behandling.fnr)
            verify(it.identClient).hentNavnForNavIdent(vedtak.behandling.saksbehandler)
            it.verifyNoMoreInteraction()
        }
    }
    private fun lagDokument(metadata: Dokument.Metadata): Dokument.MedMetadata.Vedtak {
        val utenMetadata = Dokument.UtenMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = "{}",
        )
        return utenMetadata.leggTilMetadata(metadata)
    }

    object DummyRequest : LagBrevRequest {
        override val person: Person = BrevServiceImplTest.person
        override val brevInnhold: BrevInnhold = DummyBrevInnhold
        override val saksnummer: Saksnummer = Saksnummer(2021)
        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<LagBrevRequest.KunneIkkeGenererePdf, ByteArray>,
        ): Either<LagBrevRequest.KunneIkkeGenererePdf, Dokument.UtenMetadata> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }

        override val dagensDato = fixedLocalDate
    }

    object DummyBrevInnhold : BrevInnhold() {
        override val brevTemplate: BrevTemplate = BrevTemplate.AvslagsVedtak
    }

    private data class ServiceOgMocks(
        val pdfGenerator: PdfGenerator = mock(),
        val dokumentRepo: DokumentRepo = mock(),
        val personService: PersonService = mock(),
        val sessionFactory: SessionFactory = mock(),
        val identClient: IdentClient = mock(),
        val utbetalingService: UtbetalingService = mock(),
        val clock: Clock = mock(),
    ) {
        val brevService = BrevServiceImpl(
            pdfGenerator = pdfGenerator,
            dokumentRepo = dokumentRepo,
            personService = personService,
            sessionFactory = sessionFactory,
            microsoftGraphApiOppslag = identClient,
            utbetalingService = utbetalingService,
            clock = clock,
            satsFactory = satsFactoryTestPåDato(),
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
