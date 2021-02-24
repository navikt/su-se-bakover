package no.nav.su.se.bakover.service.revurdering

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
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class OpprettRevurderingServiceTest {

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
    private val saksnummer = Saksnummer(nummer = 12345676)
    private val fnr = FnrGenerator.random()
    private val aktørId = AktørId("aktørId")

    private fun createBeregningMock() = mock<Beregning> {
        on { getPeriode() } doReturn periode
        on { getMånedsberegninger() } doReturn periode.tilMånedsperioder()
            .map { MånedsberegningFactory.ny(it, Sats.HØY, listOf()) }
        on { getFradrag() } doReturn listOf()
        on { getSumYtelse() } doReturn periode.tilMånedsperioder()
            .sumBy { MånedsberegningFactory.ny(it, Sats.HØY, listOf()).getSumYtelse() }
    }

    private fun createInnvilgetBehandling() = Søknadsbehandling.Iverksatt.Innvilget(
        id = mock(),
        opprettet = mock(),
        søknad = mock(),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().copy(
            bosituasjon = Behandlingsinformasjon.Bosituasjon(
                epsAlder = null,
                delerBolig = false,
                ektemakeEllerSamboerUførFlyktning = null,
                begrunnelse = null
            )
        ),
        saksbehandler = saksbehandler,
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("Attes T. Ant")),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        oppgaveId = mock(),
        beregning = createBeregningMock(),
        simulering = mock(),
        utbetalingId = mock(),
    )

    private fun createSak() = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        søknader = listOf(),
        behandlinger = listOf(createInnvilgetBehandling()),
        utbetalinger = createUtbetalinger()
    )

    private fun createUtbetalinger(): List<Utbetaling> = listOf(
        mock {
            on { senesteDato() } doReturn periode.getTilOgMed()
            on { tidligsteDato() } doReturn periode.getFraOgMed()
        }
    )

    @Test
    fun `oppretter en revurdering`() {
        val sak = createSak()
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { lagre(any()) }.doNothing()
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock
        ).opprettRevurdering(
            sakId = sakId,
            fraOgMed = periode.getFraOgMed(),
            saksbehandler = saksbehandler
        ).orNull()!!

        actual shouldBe OpprettetRevurdering(
            id = actual.id,
            periode = periode,
            opprettet = actual.opprettet,
            tilRevurdering = sak.behandlinger.first() as Søknadsbehandling.Iverksatt.Innvilget,
            saksbehandler = saksbehandler,
        )
        inOrder(sakServiceMock, personServiceMock, oppgaveServiceMock, revurderingRepoMock) {
            verify(sakServiceMock).hentSak(sakId)
            verify(revurderingRepoMock).hentRevurderingForBehandling(argThat { it shouldBe actual.tilRevurdering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = null
                    )
                }
            )
            verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual.right() })
        }

        verifyNoMoreInteractions(sakServiceMock, personServiceMock, oppgaveServiceMock, revurderingRepoMock)
    }

    @Test
    fun `oppretter ikke en revurdering hvis perioden er i samme måned`() {
        val actual = createRevurderingService().opprettRevurdering(
            sakId = sakId,
            fraOgMed = 1.januar(2021),
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeOppretteRevurdering.KanIkkeRevurdereInneværendeMånedEllerTidligere.left()
    }

    @Test
    fun `fant ikke sak`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn FantIkkeSak.left()
        }
        val actual = createRevurderingService(
            sakService = sakServiceMock
        ).opprettRevurdering(
            sakId = sakId,
            fraOgMed = periode.getFraOgMed(),
            saksbehandler = saksbehandler
        )
        actual shouldBe KunneIkkeOppretteRevurdering.FantIkkeSak.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `kan ikke revurdere når ingen behandlinger er IVERKSATT_INNVILGET`() {
        val sak = createSak()
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.copy(
                behandlinger = listOf(
                    mock<Søknadsbehandling.Beregnet.Innvilget>(),
                    mock<Søknadsbehandling.Vilkårsvurdert.Uavklart>()
                )
            ).right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock
        ).opprettRevurdering(
            sakId = sakId,
            fraOgMed = periode.getFraOgMed(),
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `kan ikke revurdere når stønadsperioden ikke inneholder revurderingsperioden`() {

        val beregningMock = mock<Beregning> {
            on { getPeriode() } doReturn Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.desember(2021))
        }
        val behandling = mock<Søknadsbehandling.Iverksatt.Innvilget> {
            on { beregning } doReturn beregningMock
        }
        val sak = Sak(
            id = sakId,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(),
            fnr = fnr,
            søknader = listOf(),
            behandlinger = listOf(behandling),
            utbetalinger = createUtbetalinger()
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock
        ).opprettRevurdering(
            sakId = sakId,
            fraOgMed = periode.getFraOgMed(),
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `kan ikke revurdere når stønadsperioden overlapper flere aktive stønadsperioder`() {
        val beregningMock = mock<Beregning> {
            on { getPeriode() } doReturn periode
        }
        val behandling1 = mock<Søknadsbehandling.Iverksatt.Innvilget> {
            on { beregning } doReturn beregningMock
        }
        val behandling2 = mock<Søknadsbehandling.Iverksatt.Innvilget> {
            on { beregning } doReturn beregningMock
        }

        val sak = Sak(
            id = sakId,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(),
            fnr = fnr,
            søknader = listOf(),
            behandlinger = listOf(behandling1, behandling2),
            utbetalinger = createUtbetalinger()
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.februar(2021).startOfDay(zoneIdOslo).instant, zoneIdOslo)
        ).opprettRevurdering(
            sakId = sakId,
            fraOgMed = 1.juni(2021),
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeOppretteRevurdering.KanIkkeRevurderePerioderMedFlereAktiveStønadsperioder.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `kan ikke revurdere en periode med eksisterende revurdering`() {
        val sak = createSak()

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }
        val alleredeEksisterendeRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = sak.behandlinger.first() as Søknadsbehandling.Iverksatt.Innvilget,
            saksbehandler = saksbehandler,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hentRevurderingForBehandling(any()) } doReturn alleredeEksisterendeRevurdering
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.februar(2021).startOfDay(zoneIdOslo).instant, zoneIdOslo),
            revurderingRepo = revurderingRepoMock
        ).opprettRevurdering(
            sakId = sakId,
            fraOgMed = 1.juni(2021),
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeOppretteRevurdering.KanIkkeRevurdereEnPeriodeMedEksisterendeRevurdering.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `ugyldig periode`() {
        val sak = createSak()

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hentRevurderingForBehandling(any()) } doReturn null
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.februar(2021).startOfDay(zoneIdOslo).instant, zoneIdOslo),
            revurderingRepo = revurderingRepoMock
        ).opprettRevurdering(
            sakId = sakId,
            // tester at fraOgMed må starte på 1.
            fraOgMed = 2.juni(2021),
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeOppretteRevurdering.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
            .left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `fant ikke aktør id`() {
        val sak = createSak()

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hentRevurderingForBehandling(any()) } doReturn null
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.februar(2021).startOfDay(zoneIdOslo).instant, zoneIdOslo),
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
        ).opprettRevurdering(
            sakId = sakId,
            fraOgMed = 1.juni(2021),
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIkkeAktørid.left()
        verify(sakServiceMock).hentSak(sakId)
        verify(revurderingRepoMock).hentRevurderingForBehandling(argThat { it shouldBe sak.behandlinger[0].id })
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        verifyNoMoreInteractions(sakServiceMock, revurderingRepoMock, personServiceMock)
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        val sak = createSak()

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hentRevurderingForBehandling(any()) } doReturn null
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.februar(2021).startOfDay(zoneIdOslo).instant, zoneIdOslo),
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).opprettRevurdering(
            sakId = sakId,
            fraOgMed = 1.juni(2021),
            saksbehandler = saksbehandler
        )

        actual shouldBe KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave.left()
        verify(sakServiceMock).hentSak(sakId)
        verify(revurderingRepoMock).hentRevurderingForBehandling(argThat { it shouldBe sak.behandlinger[0].id })
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Revurderingsbehandling(
                    saksnummer = saksnummer,
                    aktørId = aktørId,
                    tilordnetRessurs = null
                )
            }
        )
        verifyNoMoreInteractions(sakServiceMock, revurderingRepoMock, personServiceMock)
    }

    private fun createRevurderingService(
        sakService: SakService = mock(),
        utbetalingService: UtbetalingService = mock(),
        revurderingRepo: RevurderingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        microsoftGraphApiClient: MicrosoftGraphApiClient = mock(),
        brevService: BrevService = mock(),
        clock: Clock = fixedClock
    ) =
        RevurderingServiceImpl(
            sakService = sakService,
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            microsoftGraphApiClient = microsoftGraphApiClient,
            brevService = brevService,
            clock = clock,
        )
}
