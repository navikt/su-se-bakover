package no.nav.su.se.bakover.service.behandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.UnderkjentHandlinger.LUKKET_OPPGAVE
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.UnderkjentHandlinger.OPPRETTET_OPPGAVE
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.UnderkjentHandlinger.PERSISTERT
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import org.junit.jupiter.api.Test
import java.util.UUID

class UnderkjennBehandlingTest {
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(0)
    private val søknadId = UUID.randomUUID()
    private val fnr = FnrGenerator.random()
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    private val nyOppgaveId = OppgaveId("999")
    private val aktørId = AktørId("12345")
    private val underkjennelse = Attestering.Underkjent.Underkjennelse(
        grunn = Attestering.Underkjent.Underkjennelse.Grunn.ANDRE_FORHOLD,
        kommentar = "begrunnelse"
    )
    private val attestant = NavIdentBruker.Attestant("a")
    private val saksbehandler = NavIdentBruker.Saksbehandler("s")

    private val beregning = TestBeregning

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(),
        nettoBeløp = 191500,
        periodeList = listOf()
    )

    private val innvilgetBehandlingTilAttestering = BehandlingFactory(mock()).createBehandling(
        søknad = Søknad.Journalført.MedOppgave(
            id = søknadId,
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = journalpostId
        ),
        beregning = beregning,
        simulering = simulering,
        status = Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
        saksbehandler = saksbehandler,
        attestering = null,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        oppgaveId = oppgaveId
    )

    private val oppgaveConfig = OppgaveConfig.Saksbehandling(
        journalpostId = journalpostId,
        søknadId = søknadId,
        aktørId = aktørId,
        tilordnetRessurs = saksbehandler
    )

    @Test
    fun `Fant ikke behandling`() {
        val behandling: Behandling = innvilgetBehandlingTilAttestering.copy()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn null
        }

        val personOppslagMock = mock<PersonOppslag>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        val actual = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            hendelsesloggRepo = hendelsesloggRepoMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).underkjenn(
            behandlingId = behandling.id,
            attestant = attestant,
            underkjennelse = underkjennelse
        )

        actual shouldBe KunneIkkeUnderkjenneBehandling.FantIkkeBehandling.left()

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }

    @Test
    fun `Feil behandlingsstatus`() {
        val behandling: Behandling =
            innvilgetBehandlingTilAttestering.copy(status = Behandling.BehandlingsStatus.SIMULERT)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock = mock<PersonOppslag>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        shouldThrow<Behandling.TilstandException> {
            BehandlingTestUtils.createService(
                behandlingRepo = behandlingRepoMock,
                personOppslag = personOppslagMock,
                oppgaveService = oppgaveServiceMock,
                behandlingMetrics = behandlingMetricsMock,
                hendelsesloggRepo = hendelsesloggRepoMock,
                microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
            ).underkjenn(
                behandlingId = behandling.id,
                attestant = attestant,
                underkjennelse = underkjennelse
            )
        }.msg shouldContain "for state: SIMULERT"

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }

    @Test
    fun `attestant kan ikke være den samme som saksbehandler`() {
        val behandling: Behandling =
            innvilgetBehandlingTilAttestering.copy()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val attestantSomErLikSaksbehandler = NavIdentBruker.Attestant(saksbehandler.navIdent)

        val personOppslagMock = mock<PersonOppslag>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        val actual = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            hendelsesloggRepo = hendelsesloggRepoMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).underkjenn(
            behandlingId = behandling.id,
            attestant = attestantSomErLikSaksbehandler,
            underkjennelse = underkjennelse
        )

        actual shouldBe KunneIkkeUnderkjenneBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }

    @Test
    fun `Feiler å underkjenne dersom vi ikke fikk aktør id`() {
        val behandling: Behandling = innvilgetBehandlingTilAttestering.copy()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock = mock<PersonOppslag> {
            on { aktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        val actual = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            hendelsesloggRepo = hendelsesloggRepoMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).underkjenn(
            behandlingId = behandling.id,
            attestant = attestant,
            underkjennelse = underkjennelse
        )

        actual shouldBe KunneIkkeUnderkjenneBehandling.FantIkkeAktørId.left()

        inOrder(behandlingRepoMock, personOppslagMock) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personOppslagMock).aktørId(argThat { it shouldBe fnr })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }

    @Test
    fun `Klarer ikke opprette oppgave`() {
        val behandling: Behandling = innvilgetBehandlingTilAttestering.copy()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock = mock<PersonOppslag> {
            on { aktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgave(any()) } doReturn Unit.right()
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        val actual = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            hendelsesloggRepo = hendelsesloggRepoMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).underkjenn(
            behandlingId = behandling.id,
            attestant = attestant,
            underkjennelse = underkjennelse
        )

        actual shouldBe KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave.left()

        inOrder(behandlingRepoMock, personOppslagMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personOppslagMock).aktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(argThat { it shouldBe oppgaveConfig })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }

    @Test
    fun `Underkjenner selvom vi ikke klarer lukke oppgave`() {
        val behandling: Behandling = innvilgetBehandlingTilAttestering.copy()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock = mock<PersonOppslag> {
            on { aktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        val actual = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            hendelsesloggRepo = hendelsesloggRepoMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).underkjenn(
            behandlingId = behandling.id,
            attestant = attestant,
            underkjennelse = underkjennelse
        )

        actual shouldBe behandling.copy(
            status = Behandling.BehandlingsStatus.SIMULERT,
            attestering = Attestering.Underkjent(
                attestant,
                Attestering.Underkjent.Underkjennelse(
                    grunn = underkjennelse.grunn,
                    kommentar = underkjennelse.kommentar
                )
            )
        ).right()

        inOrder(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        ) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personOppslagMock).aktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe oppgaveConfig
                }
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter(OPPRETTET_OPPGAVE)
            verify(behandlingRepoMock).oppdaterAttestering(
                behandlingId = argThat { it shouldBe innvilgetBehandlingTilAttestering.id },
                attestering = argThat {
                    it shouldBe Attestering.Underkjent(
                        attestant,
                        underkjennelse
                    )
                }
            )
            verify(behandlingRepoMock).oppdaterOppgaveId(
                argThat { it shouldBe behandling.id },
                argThat { it shouldBe nyOppgaveId }
            )
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandlingId = argThat { it shouldBe innvilgetBehandlingTilAttestering.id },
                status = argThat { it shouldBe Behandling.BehandlingsStatus.SIMULERT }
            )
            verify(hendelsesloggRepoMock).oppdaterHendelseslogg(
                argThat {
                    it shouldBe Hendelseslogg(
                        id = innvilgetBehandlingTilAttestering.id.toString(),
                        hendelser = mutableListOf(
                            UnderkjentAttestering(
                                attestant.navIdent,
                                underkjennelse.kommentar,
                                it.hendelser()[0].tidspunkt
                            )
                        )
                    )
                }
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter(argThat { it shouldBe PERSISTERT })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }

    @Test
    fun `underkjenner behandling`() {
        val behandling: Behandling = innvilgetBehandlingTilAttestering.copy()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock: PersonOppslag = mock {
            on { aktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        val actual = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            hendelsesloggRepo = hendelsesloggRepoMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).underkjenn(
            behandlingId = behandling.id,
            attestant = attestant,
            underkjennelse = underkjennelse
        )

        actual shouldBe behandling.copy(
            status = Behandling.BehandlingsStatus.SIMULERT,
            attestering = Attestering.Underkjent(
                attestant,
                Attestering.Underkjent.Underkjennelse(
                    grunn = underkjennelse.grunn,
                    kommentar = underkjennelse.kommentar
                )
            )
        ).right()

        inOrder(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        ) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personOppslagMock).aktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe oppgaveConfig
                }
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter(OPPRETTET_OPPGAVE)

            verify(behandlingRepoMock).oppdaterAttestering(
                argThat { it shouldBe behandling.id },
                argThat { it shouldBe Attestering.Underkjent(attestant, underkjennelse) },
            )
            verify(behandlingRepoMock).oppdaterOppgaveId(
                argThat { it shouldBe behandling.id },
                argThat { it shouldBe nyOppgaveId }
            )
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandlingId = argThat { it shouldBe innvilgetBehandlingTilAttestering.id },
                status = argThat { it shouldBe Behandling.BehandlingsStatus.SIMULERT }
            )
            verify(hendelsesloggRepoMock).oppdaterHendelseslogg(
                argThat {
                    it shouldBe Hendelseslogg(
                        id = innvilgetBehandlingTilAttestering.id.toString(),
                        hendelser = mutableListOf(
                            UnderkjentAttestering(
                                attestant.navIdent,
                                underkjennelse.kommentar,
                                it.hendelser()[0].tidspunkt
                            )
                        )
                    )
                }
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter(PERSISTERT)
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
            verify(behandlingMetricsMock).incrementUnderkjentCounter(LUKKET_OPPGAVE)
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }
}
