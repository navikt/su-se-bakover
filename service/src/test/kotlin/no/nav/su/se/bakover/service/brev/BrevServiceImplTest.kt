package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.dokdistfordeling.KunneIkkeBestilleDistribusjon
import no.nav.su.se.bakover.client.pdf.KunneIkkeGenererePdf
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.Distribusjonstidspunkt
import no.nav.su.se.bakover.domain.brev.Distribusjonstype
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.FantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagMedBeregning
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
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
        )
        private val distribusjonstype = Distribusjonstype.VIKTIG
        private val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID
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
    fun `distribuerer brev`() {
        val dockdistMock = mock<DokDistFordeling> {
            on {
                bestillDistribusjon(
                    JournalpostId("journalpostId"),
                    distribusjonstype,
                    distribusjonstidspunkt,
                )
            } doReturn BrevbestillingId("en bestillings id").right()
        }

        ServiceOgMocks(
            dokDistFordeling = dockdistMock,
        ).brevService.distribuerBrev(
            JournalpostId("journalpostId"),
            distribusjonstype,
            distribusjonstidspunkt,
        ) shouldBe BrevbestillingId("en bestillings id").right()

        verify(dockdistMock).bestillDistribusjon(
            JournalpostId("journalpostId"),
            distribusjonstype,
            distribusjonstidspunkt,
        )
    }

    @Test
    fun `journalfør dokument - finner ikke sak`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
        }
        val dokumentdistribusjon = dokumentdistribusjon()

        ServiceOgMocks(
            sakService = sakServiceMock,
        ).let {
            it.brevService.journalførDokument(dokumentdistribusjon) shouldBe KunneIkkeJournalføreDokument.KunneIkkeFinneSak.left()
            verify(sakServiceMock).hentSak(dokumentdistribusjon.dokument.metadata.sakId)
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `journalfør dokument - finner ikke person`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak().right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val dokumentdistribusjon = dokumentdistribusjon()

        ServiceOgMocks(
            sakService = sakServiceMock,
            personService = personServiceMock,
        ).let {
            it.brevService.journalførDokument(dokumentdistribusjon) shouldBe KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left()
            verify(sakServiceMock).hentSak(dokumentdistribusjon.dokument.metadata.sakId)
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `journalfør dokument - feil ved journalføring`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak().right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val dokarkivMock = mock<DokArkiv> {
            on { opprettJournalpost(any()) } doReturn ClientError(500, "kek").left()
        }

        val dokumentdistribusjon = dokumentdistribusjon()

        ServiceOgMocks(
            sakService = sakServiceMock,
            personService = personServiceMock,
            dokArkiv = dokarkivMock,
        ).let {
            it.brevService.journalførDokument(dokumentdistribusjon) shouldBe KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost.left()
            verify(sakServiceMock).hentSak(dokumentdistribusjon.dokument.metadata.sakId)
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            verify(dokarkivMock).opprettJournalpost(any())
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `journalfør dokument - dokument allerede journalført`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak().right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("done")))

        ServiceOgMocks(
            sakService = sakServiceMock,
            personService = personServiceMock,
        ).let {
            it.brevService.journalførDokument(dokumentdistribusjon) shouldBe dokumentdistribusjon.right()
            verify(sakServiceMock).hentSak(dokumentdistribusjon.dokument.metadata.sakId)
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `journalfør dokument - happy`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak().right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val dokarkivMock = mock<DokArkiv> {
            on { opprettJournalpost(any()) } doReturn JournalpostId("happy").right()
        }

        val dokumentRepoMock = mock<DokumentRepo>()

        val dokumentdistribusjon = dokumentdistribusjon()

        val expected = dokumentdistribusjon
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("happy")))

        ServiceOgMocks(
            sakService = sakServiceMock,
            personService = personServiceMock,
            dokArkiv = dokarkivMock,
            dokumentRepo = dokumentRepoMock,
        ).let {
            it.brevService.journalførDokument(dokumentdistribusjon) shouldBe expected.right()
            verify(sakServiceMock).hentSak(dokumentdistribusjon.dokument.metadata.sakId)
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            verify(dokarkivMock).opprettJournalpost(any())
            verify(dokumentRepoMock).oppdaterDokumentdistribusjon(expected)
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `distribuer dokument - ikke journalført`() {
        val dokumentdistribusjon = dokumentdistribusjon()

        ServiceOgMocks().let {
            it.brevService.distribuerDokument(dokumentdistribusjon) shouldBe KunneIkkeBestilleBrevForDokument.MåJournalføresFørst.left()
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `distribuer dokument - allerede distribuert`() {
        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    JournalpostId("very"),
                    BrevbestillingId("happy"),
                ),
            )

        ServiceOgMocks().let {
            it.brevService.distribuerDokument(dokumentdistribusjon) shouldBe dokumentdistribusjon.right()
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `distribuer dokument - feil ved bestilling av brev`() {
        val dokDistMock = mock<DokDistFordeling> {
            on { bestillDistribusjon(any(), any(), any()) } doReturn KunneIkkeBestilleDistribusjon.left()
        }

        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("sad")))

        ServiceOgMocks(
            dokDistFordeling = dokDistMock,
        ).let {
            it.brevService.distribuerDokument(dokumentdistribusjon) shouldBe KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev.left()
            verify(dokDistMock).bestillDistribusjon(JournalpostId("sad"), Distribusjonstype.VEDTAK, distribusjonstidspunkt)
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `distribuer dokument - happy`() {
        val dokDistMock = mock<DokDistFordeling> {
            on { bestillDistribusjon(any(), any(), any()) } doReturn BrevbestillingId("happy").right()
        }

        val dokumentRepoMock = mock<DokumentRepo>()

        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("very")))

        val expected = dokumentdistribusjon
            .copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    JournalpostId("very"),
                    BrevbestillingId("happy"),
                ),
            )

        ServiceOgMocks(
            dokDistFordeling = dokDistMock,
            dokumentRepo = dokumentRepoMock,
        ).let {
            it.brevService.distribuerDokument(dokumentdistribusjon) shouldBe expected.right()
            verify(dokDistMock).bestillDistribusjon(JournalpostId("very"), Distribusjonstype.VEDTAK, distribusjonstidspunkt)
            verify(dokumentRepoMock).oppdaterDokumentdistribusjon(expected)
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `henter dokumenter for ulike typer id-er`() {
        val sakId = UUID.randomUUID()
        val vedtakId = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val revurderingId = UUID.randomUUID()
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

        val dokumentRepoMock = mock<DokumentRepo> {
            on { hentForSak(sakId) } doReturn listOf(sakDokument)
            on { hentForSak(randomId) } doReturn emptyList()
            on { hentForVedtak(vedtakId) } doReturn listOf(vedtakDokument)
            on { hentForVedtak(randomId) } doReturn emptyList()
            on { hentForSøknad(søknadId) } doReturn listOf(søknadDokument)
            on { hentForSøknad(randomId) } doReturn emptyList()
            on { hentForRevurdering(revurderingId) } doReturn listOf(revurderingDokument)
            on { hentForRevurdering(randomId) } doReturn emptyList()
        }

        val service = ServiceOgMocks(
            dokumentRepo = dokumentRepoMock,
        ).brevService

        service.hentDokumenterFor(HentDokumenterForIdType.Sak(sakId)) shouldBe listOf(
            sakDokument,
        )
        service.hentDokumenterFor(HentDokumenterForIdType.Sak(randomId)) shouldBe emptyList()
        service.hentDokumenterFor(HentDokumenterForIdType.Vedtak(vedtakId)) shouldBe listOf(
            vedtakDokument,
        )
        service.hentDokumenterFor(HentDokumenterForIdType.Vedtak(randomId)) shouldBe emptyList()
        service.hentDokumenterFor(HentDokumenterForIdType.Søknad(søknadId)) shouldBe listOf(
            søknadDokument,
        )
        service.hentDokumenterFor(HentDokumenterForIdType.Søknad(randomId)) shouldBe emptyList()
        service.hentDokumenterFor(HentDokumenterForIdType.Revurdering(revurderingId)) shouldBe listOf(
            revurderingDokument,
        )
        service.hentDokumenterFor(HentDokumenterForIdType.Revurdering(randomId)) shouldBe emptyList()
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

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `klarer ikke å finne gjeldende utbetaling`() {
        val (sak, vedtak) = iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak()
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<IdentClient> {
            on { hentNavnForNavIdent(any()) } doReturnConsecutively listOf(
                "Kåre Kropp".right(),
                "Suveren Severin".right(),
            )
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { hentGjeldendeUtbetaling(any(), any()) } doReturn FantIkkeGjeldendeUtbetaling.left()
        }

        ServiceOgMocks(
            personService = personServiceMock,
            identClient = microsoftGraphApiOppslagMock,
            utbetalingService = utbetalingServiceMock,
            clock = fixedClock,
        ).let {
            it.brevService.lagDokument(vedtak) shouldBe KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling.left()
            verify(it.personService).hentPersonMedSystembruker(vedtak.fnr)
            verify(it.identClient).hentNavnForNavIdent(vedtak.saksbehandler)
            verify(it.identClient).hentNavnForNavIdent(vedtak.attesteringer.hentSisteAttestering().attestant)
            verify(it.utbetalingService).hentGjeldendeUtbetaling(sak.id, vedtak.opprettet.toLocalDate(zoneIdOslo))
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `svarer med feil der som generering av pdf feiler`() {
        val (sak, vedtak) = iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak()
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<IdentClient> {
            on { hentNavnForNavIdent(any()) } doReturnConsecutively listOf(
                "Kåre Kropp".right(),
                "Suveren Severin".right(),
            )
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { hentGjeldendeUtbetaling(any(), any()) } doReturn UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = vedtak.periode,
                beløp = 5999,
            ).right()
        }

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<BrevInnhold>()) } doReturn KunneIkkeGenererePdf.left()
        }

        ServiceOgMocks(
            personService = personServiceMock,
            identClient = microsoftGraphApiOppslagMock,
            utbetalingService = utbetalingServiceMock,
            pdfGenerator = pdfGeneratorMock,
            clock = fixedClock,
        ).let {
            it.brevService.lagDokument(vedtak) shouldBe KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
            verify(it.personService).hentPersonMedSystembruker(vedtak.fnr)
            verify(it.identClient).hentNavnForNavIdent(vedtak.saksbehandler)
            verify(it.identClient).hentNavnForNavIdent(vedtak.attesteringer.hentSisteAttestering().attestant)
            verify(it.utbetalingService).hentGjeldendeUtbetaling(sak.id, vedtak.opprettet.toLocalDate(zoneIdOslo))
            verify(it.pdfGenerator).genererPdf(any<BrevInnhold.VedtakIngenEndring>())
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
        override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<LagBrevRequest.KunneIkkeGenererePdf, ByteArray>): Either<LagBrevRequest.KunneIkkeGenererePdf, Dokument.UtenMetadata> {
            return genererDokument(genererPdf).map {
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
        val dokArkiv: DokArkiv = mock(),
        val dokDistFordeling: DokDistFordeling = mock(),
        val dokumentRepo: DokumentRepo = mock(),
        val sakService: SakService = mock(),
        val personService: PersonService = mock(),
        val sessionFactory: SessionFactory = mock(),
        val identClient: IdentClient = mock(),
        val utbetalingService: UtbetalingService = mock(),
        val clock: Clock = mock(),

    ) {
        val brevService = BrevServiceImpl(
            pdfGenerator = pdfGenerator,
            dokArkiv = dokArkiv,
            dokDistFordeling = dokDistFordeling,
            dokumentRepo = dokumentRepo,
            sakService = sakService,
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
                dokArkiv,
                dokDistFordeling,
                dokumentRepo,
                sakService,
                personService,
                sessionFactory,
                identClient,
                utbetalingService,
            )
        }
    }

    private fun dokumentdistribusjon(): Dokumentdistribusjon = Dokumentdistribusjon(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        dokument = Dokument.MedMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = "{}",
            metadata = Dokument.Metadata(
                sakId = UUID.randomUUID(),
                bestillBrev = true,
            ),
        ),
        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
    )

    private fun sak(): Sak = Sak(
        id = UUID.randomUUID(),
        saksnummer = Saksnummer(9999),
        opprettet = fixedTidspunkt,
        fnr = fnr,
        søknader = listOf(),
        søknadsbehandlinger = listOf(),
        utbetalinger = listOf(),
        revurderinger = listOf(),
        vedtakListe = listOf(),
        type = Sakstype.UFØRE
    )
}
