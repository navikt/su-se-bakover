package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadsbehandlingServiceUnderkjennTest {
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(0)
    private val søknadId = UUID.randomUUID()
    private val fnr = FnrGenerator.random()
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    private val nyOppgaveId = OppgaveId("999")
    private val aktørId = AktørId("12345")
    private val saksbehandler = NavIdentBruker.Saksbehandler("s")
    private val stønadsperiode = ValgtStønadsperiode(Periode.create(1.januar(2021), 31.desember(2021)))

    private val underkjentAttestering = Attestering.Underkjent(
        attestant = NavIdentBruker.Attestant("a"),
        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
        kommentar = "begrunnelse"
    )

    private val innvilgetBehandlingTilAttestering = Søknadsbehandling.TilAttestering.Innvilget(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        søknad = Søknad.Journalført.MedOppgave(
            id = søknadId,
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = journalpostId
        ),
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "NAVN",
            datoBeregnet = idag(),
            nettoBeløp = 191500,
            periodeList = listOf()
        ),
        saksbehandler = saksbehandler,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        oppgaveId = oppgaveId,
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
    )

    private val oppgaveConfig = OppgaveConfig.Saksbehandling(
        journalpostId = journalpostId,
        søknadId = søknadId,
        aktørId = aktørId,
        tilordnetRessurs = saksbehandler
    )

    @Test
    fun `Fant ikke behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering
            )
        )

        actual shouldBe SøknadsbehandlingService.KunneIkkeUnderkjenne.FantIkkeBehandling.left()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }

    @Test
    fun `Feil behandlingsstatus`() {
        val behandling: Søknadsbehandling.Iverksatt.Innvilget = innvilgetBehandlingTilAttestering.tilIverksatt(
            Attestering.Iverksatt(
                NavIdentBruker.Attestant("attestant")
            )
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        shouldThrow<StatusovergangVisitor.UgyldigStatusovergangException> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
                oppgaveService = oppgaveServiceMock,
                personService = personServiceMock,
                behandlingMetrics = behandlingMetricsMock
            ).underkjenn(
                SøknadsbehandlingService.UnderkjennRequest(
                    behandlingId = behandling.id,
                    attestering = underkjentAttestering
                )
            )
        }.msg shouldContain "Ugyldig statusovergang"

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }

    @Test
    fun `attestant kan ikke være den samme som saksbehandler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val attestantSomErLikSaksbehandler = NavIdentBruker.Attestant(saksbehandler.navIdent)

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val observerMock: EventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            observer = observerMock
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = Attestering.Underkjent(
                    attestant = attestantSomErLikSaksbehandler,
                    grunn = underkjentAttestering.grunn,
                    kommentar = underkjentAttestering.kommentar
                )
            )
        )

        actual shouldBe SøknadsbehandlingService.KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
        verifyZeroInteractions(observerMock)
    }

    @Test
    fun `Feiler å underkjenne dersom vi ikke fikk aktør id`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering
            )
        )

        actual shouldBe SøknadsbehandlingService.KunneIkkeUnderkjenne.FantIkkeAktørId.left()

        inOrder(søknadsbehandlingRepoMock, personServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `Klarer ikke opprette oppgave`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering
            )
        )

        actual shouldBe SøknadsbehandlingService.KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave.left()

        inOrder(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(argThat { it shouldBe oppgaveConfig })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `Underkjenner selvom vi ikke klarer lukke oppgave`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val observerMock: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            observer = observerMock
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering
            )
        )

        val underkjentMedNyOppgaveIdOgAttestering = Søknadsbehandling.Underkjent.Innvilget(
            id = innvilgetBehandlingTilAttestering.id,
            opprettet = innvilgetBehandlingTilAttestering.opprettet,
            sakId = innvilgetBehandlingTilAttestering.sakId,
            saksnummer = innvilgetBehandlingTilAttestering.saksnummer,
            søknad = innvilgetBehandlingTilAttestering.søknad,
            oppgaveId = nyOppgaveId,
            behandlingsinformasjon = innvilgetBehandlingTilAttestering.behandlingsinformasjon,
            fnr = innvilgetBehandlingTilAttestering.fnr,
            beregning = innvilgetBehandlingTilAttestering.beregning,
            simulering = innvilgetBehandlingTilAttestering.simulering,
            saksbehandler = innvilgetBehandlingTilAttestering.saksbehandler,
            attestering = underkjentAttestering,
            fritekstTilBrev = "",
            stønadsperiode = innvilgetBehandlingTilAttestering.stønadsperiode,
        )

        actual shouldBe underkjentMedNyOppgaveIdOgAttestering.right()

        inOrder(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            observerMock
        ) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe oppgaveConfig
                }
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.OPPRETTET_OPPGAVE)

            verify(søknadsbehandlingRepoMock).lagre(argThat { it shouldBe underkjentMedNyOppgaveIdOgAttestering })

            verify(behandlingMetricsMock).incrementUnderkjentCounter(argThat { it shouldBe BehandlingMetrics.UnderkjentHandlinger.PERSISTERT })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
            verify(observerMock).handle(
                argThat {
                    it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingUnderkjent(underkjentMedNyOppgaveIdOgAttestering)
                }
            )
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `underkjenner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering
            )
        )

        val underkjentMedNyOppgaveIdOgAttestering = Søknadsbehandling.Underkjent.Innvilget(
            id = innvilgetBehandlingTilAttestering.id,
            opprettet = innvilgetBehandlingTilAttestering.opprettet,
            sakId = innvilgetBehandlingTilAttestering.sakId,
            saksnummer = innvilgetBehandlingTilAttestering.saksnummer,
            søknad = innvilgetBehandlingTilAttestering.søknad,
            oppgaveId = nyOppgaveId,
            behandlingsinformasjon = innvilgetBehandlingTilAttestering.behandlingsinformasjon,
            fnr = innvilgetBehandlingTilAttestering.fnr,
            beregning = innvilgetBehandlingTilAttestering.beregning,
            simulering = innvilgetBehandlingTilAttestering.simulering,
            saksbehandler = innvilgetBehandlingTilAttestering.saksbehandler,
            attestering = underkjentAttestering,
            fritekstTilBrev = "",
            stønadsperiode = innvilgetBehandlingTilAttestering.stønadsperiode,
        )

        actual shouldBe underkjentMedNyOppgaveIdOgAttestering.right()

        inOrder(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        ) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe oppgaveConfig
                }
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.OPPRETTET_OPPGAVE)
            verify(søknadsbehandlingRepoMock).lagre(underkjentMedNyOppgaveIdOgAttestering)
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.PERSISTERT)
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.LUKKET_OPPGAVE)
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }
}
