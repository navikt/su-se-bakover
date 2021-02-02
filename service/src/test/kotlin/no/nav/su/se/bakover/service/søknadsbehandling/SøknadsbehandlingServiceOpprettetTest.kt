package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppretteSøknadsbehandling
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadsbehandlingServiceOpprettetTest {

    @Test
    fun `svarer med feil dersom vi ikke finner søknad`() {
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn FantIkkeSøknad.left()
        }

        val saksbehandlingRepo = mock<SaksbehandlingRepo>()

        val service = createService(
            søknadService = søknadServiceMock,
            behandlingRepo = saksbehandlingRepo
        )

        val søknadId = UUID.randomUUID()
        service.opprett(OpprettSøknadsbehandlingRequest((søknadId))) shouldBe KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad.left()

        verify(søknadServiceMock).hentSøknad(søknadId)
        verifyNoMoreInteractions(søknadServiceMock, saksbehandlingRepo)
    }

    @Test
    fun `svarer med feil dersom søknad allrede er lukket`() {
        val lukketSøknad = Søknad.Lukket(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = null,
            oppgaveId = null,
            lukketTidspunkt = Tidspunkt.now(),
            lukketAv = NavIdentBruker.Saksbehandler("sas"),
            lukketType = Søknad.Lukket.LukketType.BORTFALT,
            lukketJournalpostId = null,
            lukketBrevbestillingId = null
        )

        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn lukketSøknad.right()
        }

        val saksbehandlingRepo = mock<SaksbehandlingRepo>()

        val service = createService(
            søknadService = søknadServiceMock,
            behandlingRepo = saksbehandlingRepo
        )

        service.opprett(OpprettSøknadsbehandlingRequest((lukketSøknad.id))) shouldBe KunneIkkeOppretteSøknadsbehandling.SøknadErLukket.left()

        verify(søknadServiceMock).hentSøknad(lukketSøknad.id)
        verifyNoMoreInteractions(søknadServiceMock, saksbehandlingRepo)
    }

    @Test
    fun `svarer med feil dersom søknad ikke er journalført med oppgave`() {
        val utenJournalpostOgOppgave = Søknad.Ny(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build()
        )

        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn utenJournalpostOgOppgave.right()
        }

        val saksbehandlingRepo = mock<SaksbehandlingRepo>()

        val service = createService(
            søknadService = søknadServiceMock,
            behandlingRepo = saksbehandlingRepo
        )

        service.opprett(OpprettSøknadsbehandlingRequest((utenJournalpostOgOppgave.id))) shouldBe KunneIkkeOppretteSøknadsbehandling.SøknadManglerOppgave.left()

        verify(søknadServiceMock).hentSøknad(utenJournalpostOgOppgave.id)
        verifyNoMoreInteractions(søknadServiceMock, saksbehandlingRepo)
    }

    @Test
    fun `svarer med feil dersom søknad har påbegynt behandling`() {
        val søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId(value = "2"),
            oppgaveId = OppgaveId(value = "1")
        )
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadRepoMock = mock<SøknadRepo> {
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }

        val saksbehandlingRepo = mock<SaksbehandlingRepo>()

        val service = createService(
            søknadService = søknadServiceMock,
            behandlingRepo = saksbehandlingRepo,
            søknadRepo = søknadRepoMock
        )

        service.opprett(OpprettSøknadsbehandlingRequest((søknad.id))) shouldBe KunneIkkeOppretteSøknadsbehandling.SøknadHarAlleredeBehandling.left()

        verify(søknadServiceMock).hentSøknad(søknad.id)
        verify(søknadRepoMock).harSøknadPåbegyntBehandling(søknad.id)
        verifyNoMoreInteractions(søknadServiceMock, saksbehandlingRepo)
    }

    @Test
    fun `Oppretter behandling og publiserer event`() {
        val sakId = UUID.randomUUID()
        val søknadInnhold = SøknadInnholdTestdataBuilder.build()
        val fnr = søknadInnhold.personopplysninger.fnr
        val søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            søknadInnhold = søknadInnhold,
            journalpostId = JournalpostId(value = "2"),
            oppgaveId = OppgaveId(value = "1")
        )
        val expectedSøknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(), // blir ignorert eller overskrevet
            opprettet = Tidspunkt.EPOCH, // blir ignorert eller overskrevet
            sakId = sakId,
            saksnummer = Saksnummer(123),
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = fnr,
        )
        val søknadService: SøknadService = mock {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadRepo: SøknadRepo = mock {
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val saksbehandlingRepoMock: SaksbehandlingRepo = mock {
            on { lagre(any()) }.doNothing()
            on { hent(any()) } doReturn expectedSøknadsbehandling
        }
        val behandlingService = createService(
            utbetalingService = mock(),
            oppgaveService = mock(),
            søknadService = søknadService,
            søknadRepo = søknadRepo,
            personService = mock(),
            behandlingRepo = saksbehandlingRepoMock,
            iverksettBehandlingService = mock(),
            behandlingMetrics = mock(),
            beregningService = mock(),
        )
        val eventObserver: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }
        behandlingService.addObserver(eventObserver)

        behandlingService.opprett(OpprettSøknadsbehandlingRequest(søknad.id)).orNull()!!.shouldBeEqualToIgnoringFields(
            expectedSøknadsbehandling,
            Søknadsbehandling.Vilkårsvurdert.Uavklart::id,
            Søknadsbehandling.Vilkårsvurdert.Uavklart::opprettet,
        )
        verify(søknadService).hentSøknad(argThat { it shouldBe søknad.id })
        verify(søknadRepo).harSøknadPåbegyntBehandling(argThat { it shouldBe søknad.id })

        val persistertSøknadsbehandling = argumentCaptor<Søknadsbehandling.Vilkårsvurdert.Uavklart>()

        verify(saksbehandlingRepoMock).lagre(persistertSøknadsbehandling.capture())

        verify(saksbehandlingRepoMock).hent(
            argThat { it shouldBe persistertSøknadsbehandling.firstValue.id }
        )
        verify(eventObserver).handle(
            argThat {
                it shouldBe Event.Statistikk.SøknadsbehandlingOpprettet(
                    expectedSøknadsbehandling
                )
            }
        )
    }

    private fun createService(
        behandlingRepo: SaksbehandlingRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveService: OppgaveService = mock(),
        søknadService: SøknadService = mock(),
        søknadRepo: SøknadRepo = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        iverksettBehandlingService: IverksettSøknadsbehandlingService = mock(),
        observer: EventObserver = mock { on { handle(any()) }.doNothing() },
        beregningService: BeregningService = mock(),
    ) = SøknadsbehandlingServiceImpl(
        søknadService,
        søknadRepo,
        behandlingRepo,
        utbetalingService,
        personService,
        oppgaveService,
        iverksettBehandlingService,
        behandlingMetrics,
        beregningService,
    ).apply { addObserver(observer) }
}
