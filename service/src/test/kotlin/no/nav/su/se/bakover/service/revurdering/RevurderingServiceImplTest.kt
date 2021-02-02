package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.RevurderingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.BeregnetRevurdering
import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.behandling.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class RevurderingServiceImplTest {
    private val sakId: UUID = UUID.randomUUID()
    private val dagensDato = LocalDate.now().let {
        LocalDate.of(
            it.year,
            it.month,
            1
        )
    }
    private val nesteMåned =
        LocalDate.of(
            dagensDato.year,
            dagensDato.month.plus(1),
            1
        )
    private val periode = Periode.create(
        fraOgMed = nesteMåned,
        tilOgMed = nesteMåned.let {
            val treMånederFramITid = it.plusMonths(3)
            LocalDate.of(
                treMånederFramITid.year,
                treMånederFramITid.month,
                treMånederFramITid.lengthOfMonth()
            )
        }
    )
    private val saksbehandler = NavIdentBruker.Saksbehandler("Sak S. behandler")
    private val behandlingFactory: BehandlingFactory = BehandlingFactory(mock(), Clock.systemUTC())
    private val saksnummer = Saksnummer(nummer = 12345676)
    private val fnr = FnrGenerator.random()
    private val revurderingId = UUID.randomUUID()
    private val aktørId = AktørId("aktørId")
    private val beregningMock = mock<Beregning> {
        on { getPeriode() } doReturn Periode.create(
            fraOgMed = dagensDato,
            tilOgMed = dagensDato.let {
                LocalDate.of(
                    it.year + 1,
                    it.month,
                    it.lengthOfMonth()
                )
            }
        )
    }

    val behandling = behandlingFactory.createBehandling(
        id = mock(),
        søknad = mock(),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().copy(
            bosituasjon = Behandlingsinformasjon.Bosituasjon(
                epsAlder = null,
                delerBolig = false,
                ektemakeEllerSamboerUførFlyktning = null,
                begrunnelse = null
            )
        ),
        status = Behandling.BehandlingsStatus.IVERKSATT_INNVILGET,
        saksbehandler = saksbehandler,
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("Attes T. Ant")),
        sakId = sakId,
        saksnummer = BehandlingTestUtils.saksnummer,
        fnr = fnr,
        oppgaveId = mock(),
        beregning = beregningMock
    )
    val sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        søknader = listOf(),
        behandlinger = listOf(behandling),
        utbetalinger = listOf()
    )

    @Test
    fun `oppretter en revurdering`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { lagre(any()) }.doNothing()
        }
        val actual = createRevurderingService(
            sakService = sakServiceMock,
            revurderingRepo = revurderingRepoMock
        ).opprettRevurdering(
            sakId = sakId,
            periode = periode,
            saksbehandler = saksbehandler
        )

        inOrder(sakServiceMock, revurderingRepoMock) {
            verify(sakServiceMock).hentSak(sakId)
            verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual })
        }

        verifyNoMoreInteractions(sakServiceMock, revurderingRepoMock)
    }

    @Test
    fun `kan ikke revurdere når ingen behandlinger er IVERKSATT_INNVILGET`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.copy(
                behandlinger = listOf(
                    behandling.copy(
                        status = Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET
                    )
                )
            ).right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock
        ).opprettRevurdering(
            sakId = sakId,
            periode = periode,
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeRevurdere.FantIngentingSomKanRevurderes.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `kan ikke revurdere når stønadsperioden ikke inneholder revurderingsperioden`() {

        val beregningMock = mock<Beregning> {
            on { getPeriode() } doReturn Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.desember(2021))
        }
        val behandling = behandlingFactory.createBehandling(
            id = mock(),
            søknad = mock(),
            status = Behandling.BehandlingsStatus.IVERKSATT_INNVILGET,
            saksbehandler = saksbehandler,
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("Attes T. Ant")),
            sakId = sakId,
            saksnummer = BehandlingTestUtils.saksnummer,
            fnr = fnr,
            oppgaveId = mock(),
            beregning = beregningMock
        )
        val sak = Sak(
            id = sakId,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(),
            fnr = fnr,
            søknader = listOf(),
            behandlinger = listOf(behandling),
            utbetalinger = listOf()
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock
        ).opprettRevurdering(
            sakId = sakId,
            periode = periode,
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeRevurdere.FantIngentingSomKanRevurderes.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `oppretter ikke en revurdering hvis perioden er i samme måned`() {
        val actual = createRevurderingService().opprettRevurdering(
            sakId = sakId,
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeRevurdere.KanIkkeRevurdereInneværendeMånedEllerTidligere.left()
    }

    @Test
    fun `kan beregne og simulere`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = behandling,
            saksbehandler = saksbehandler,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }
        val simulertUtbetaling = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn simulertUtbetaling.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            fradrag = listOf()
        ).getOrHandle {
            throw Exception("Vi skal få tilbake en simulert revurdering")
        }

        inOrder(revurderingRepoMock, utbetalingServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(utbetalingServiceMock).simulerUtbetaling(
                sakId = argThat { it shouldBe sakId },
                saksbehandler = argThat { it shouldBe saksbehandler },
                beregning = argThat { it shouldBe actual.beregning }
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `kan ikke beregne og simulere en revurdering som er til attestering`() {
        val revurderingTilAttestering = RevurderingTilAttestering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = behandling,
            saksbehandler = saksbehandler,
            beregning = mock(),
            simulering = mock(),
            oppgaveId = mock(),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurderingTilAttestering
        }

        assertThrows<RuntimeException> {
            createRevurderingService(
                revurderingRepo = revurderingRepoMock,
            ).beregnOgSimuler(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fradrag = listOf()
            )
        }

        verify(revurderingRepoMock).hent(revurderingId)
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil når simulering feiler`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = behandling,
            saksbehandler = saksbehandler,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            fradrag = listOf()
        )

        actual shouldBe KunneIkkeRevurdere.SimuleringFeilet.left()

        inOrder(revurderingRepoMock) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `sender til attestering`() {
        val søknadMock = mock<Søknad.Journalført.MedOppgave> {
            on { id } doReturn UUID.randomUUID()
        }

        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn fnr
            on { søknad } doReturn søknadMock
        }

        val simulertRevurdering = mock<SimulertRevurdering> {
            on { id } doReturn revurderingId
            on { periode } doReturn periode
            on { tilRevurdering } doReturn behandlingMock
            on { opprettet } doReturn Tidspunkt.EPOCH
            on { beregning } doReturn mock()
            on { simulering } doReturn mock()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock
        ).sendTilAttestering(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        )

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Attestering(
                        søknadId = simulertRevurdering.tilRevurdering.søknad.id,
                        aktørId = aktørId,
                        tilordnetRessurs = null
                    )
                }
            )
            verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual })
        }

        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `sender ikke til attestering hvis revurdering er ikke simulert`() {
        val opprettetRevurdering = mock<OpprettetRevurdering>()

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        assertThrows<java.lang.RuntimeException> {
            createRevurderingService(
                revurderingRepo = revurderingRepoMock
            ).sendTilAttestering(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
            )
        }

        verify(revurderingRepoMock).hent(revurderingId)
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `sender ikke til attestering hvis henting av aktørId feiler`() {
        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn fnr
        }
        val simulertRevurdering = mock<SimulertRevurdering> {
            on { tilRevurdering } doReturn behandlingMock
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock
        ).sendTilAttestering(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        )

        actual shouldBe KunneIkkeRevurdere.KunneIkkeFinneAktørId.left()

        inOrder(revurderingRepoMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verifyNoMoreInteractions(revurderingRepoMock)
        }
    }

    @Test
    fun `sender ikke til attestering hvis oppretting av oppgave feiler`() {
        val søknadMock = mock<Søknad.Journalført.MedOppgave> {
            on { id } doReturn UUID.randomUUID()
        }
        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn fnr
            on { søknad } doReturn søknadMock
        }
        val simulertRevurdering = mock<SimulertRevurdering> {
            on { tilRevurdering } doReturn behandlingMock
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock
        ).sendTilAttestering(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        )

        actual shouldBe KunneIkkeRevurdere.KunneIkkeOppretteOppgave.left()

        inOrder(revurderingRepoMock, personServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verifyNoMoreInteractions(revurderingRepoMock, personServiceMock)
        }
    }

    @Test
    fun `kan lage brev`() {
        val person = mock<Person>()
        val brevPdf = "".toByteArray()

        val behandlingsinformasjonMock = mock<Behandlingsinformasjon> {
            on { harEktefelle() } doReturn false
        }

        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn fnr
            on { beregning() } doReturn mock()
            on { behandlingsinformasjon() } doReturn behandlingsinformasjonMock
        }

        val simulertRevurdering = mock<SimulertRevurdering> {
            on { id } doReturn revurderingId
            on { tilRevurdering } doReturn behandlingMock
            on { beregning } doReturn mock()
            on { saksbehandler } doReturn saksbehandler
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn brevPdf.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
            brevService = brevServiceMock
        ).lagBrevutkast(
            revurderingId = revurderingId,
            fritekst = null
        )

        actual shouldBe brevPdf.right()

        inOrder(revurderingRepoMock, personServiceMock, microsoftGraphApiClientMock, brevServiceMock) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(microsoftGraphApiClientMock).hentNavnForNavIdent(argThat { "$it" shouldBe saksbehandler.navIdent })
            verify(brevServiceMock).lagBrev(
                argThat {
                    it shouldBe
                        LagBrevRequest.Revurdering.Inntekt(
                            person = person,
                            saksbehandlerNavn = saksbehandler.navIdent,
                            revurdertBeregning = simulertRevurdering.beregning,
                            fritekst = null,
                            vedtattBeregning = behandlingMock.beregning()!!,
                            harEktefelle = false
                        )
                }
            )
        }

        verifyNoMoreInteractions(
            revurderingRepoMock,
            personServiceMock,
            personServiceMock,
            microsoftGraphApiClientMock,
            brevServiceMock
        )
    }

    @Test
    fun `får feil når vi ikke kan hente person`() {
        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn fnr
        }

        val simulertRevurdering = mock<SimulertRevurdering> {
            on { id } doReturn revurderingId
            on { tilRevurdering } doReturn behandlingMock
            on { beregning } doReturn mock()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
        ).lagBrevutkast(
            revurderingId = revurderingId,
            fritekst = null
        )

        actual shouldBe KunneIkkeRevurdere.FantIkkePerson.left()

        inOrder(revurderingRepoMock, personServiceMock) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        }

        verifyNoMoreInteractions(
            revurderingRepoMock,
        )
    }

    @Test
    fun `får feil når vi ikke kan hente saksbehandler navn`() {
        val person = mock<Person>()

        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn fnr
        }

        val simulertRevurdering = mock<SimulertRevurdering> {
            on { id } doReturn revurderingId
            on { tilRevurdering } doReturn behandlingMock
            on { saksbehandler } doReturn saksbehandler
            on { beregning } doReturn mock()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiClient> {
            on { hentNavnForNavIdent(any()) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
        ).lagBrevutkast(
            revurderingId = revurderingId,
            fritekst = null
        )

        actual shouldBe KunneIkkeRevurdere.MicrosoftApiGraphFeil.left()

        inOrder(revurderingRepoMock, personServiceMock, microsoftGraphApiClientMock) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
        }

        verifyNoMoreInteractions(
            revurderingRepoMock,
            personServiceMock,
            personServiceMock,
        )
    }

    @Test
    fun `får feil når vi ikke kan lage brev`() {
        val person = mock<Person>()

        val behandlingsinformasjonMock = mock<Behandlingsinformasjon> {
            on { harEktefelle() } doReturn false
        }

        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn fnr
            on { beregning() } doReturn mock()
            on { behandlingsinformasjon() } doReturn behandlingsinformasjonMock
        }

        val simulertRevurdering = mock<SimulertRevurdering> {
            on { id } doReturn revurderingId
            on { tilRevurdering } doReturn behandlingMock
            on { beregning } doReturn mock()
            on { saksbehandler } doReturn saksbehandler
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
            brevService = brevServiceMock
        ).lagBrevutkast(
            revurderingId = revurderingId,
            fritekst = null
        )

        actual shouldBe KunneIkkeRevurdere.KunneIkkeLageBrevutkast.left()

        inOrder(revurderingRepoMock, personServiceMock, microsoftGraphApiClientMock) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(microsoftGraphApiClientMock).hentNavnForNavIdent(argThat { "$it" shouldBe saksbehandler.navIdent })
        }

        verifyNoMoreInteractions(
            revurderingRepoMock,
            personServiceMock,
            personServiceMock,
            microsoftGraphApiClientMock,
        )
    }

    @Test
    fun `kan ikke lage brev med status opprettet`() {
        val opprettetRevurdering = mock<OpprettetRevurdering>()
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock
        ).lagBrevutkast(
            revurderingId = revurderingId,
            fritekst = null
        )

        actual shouldBe KunneIkkeRevurdere.KunneIkkeLageBrevutkast.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `kan ikke lage brev med status beregnet`() {
        val beregnetRevurdering = mock<BeregnetRevurdering>()
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn beregnetRevurdering
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock
        ).lagBrevutkast(
            revurderingId = revurderingId,
            fritekst = null
        )

        actual shouldBe KunneIkkeRevurdere.KunneIkkeLageBrevutkast.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    private fun createRevurderingService(
        sakService: SakService = mock(),
        utbetalingService: UtbetalingService = mock(),
        revurderingRepo: RevurderingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        microsoftGraphApiClient: MicrosoftGraphApiClient = mock(),
        brevService: BrevService = mock()
    ) =
        RevurderingServiceImpl(
            sakService = sakService,
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            microsoftGraphApiClient = microsoftGraphApiClient,
            brevService = brevService
        )
}
