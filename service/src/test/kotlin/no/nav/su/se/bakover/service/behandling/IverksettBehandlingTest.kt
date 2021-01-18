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
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.SIMULERT
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.InnvilgetHandlinger
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.attestant
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingId
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.fnr
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.person
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksnummer
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.tidspunkt
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import org.junit.jupiter.api.Test

internal class IverksettBehandlingTest {
    private val oppgaveId = OppgaveId("o")
    private val iverksattJournalpostId = JournalpostId("j")
    private val iverksattBrevbestillingId = BrevbestillingId("2")

    @Test
    fun `iverksett behandling finner ikke behandling`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn null
        }

        val personServiceMock = mock<PersonService>()

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val oppgaveServiceMock = mock<OppgaveService>()

        val utbetalingServiceMock = mock<UtbetalingService>()

        val opprettVedtakssnapshotServiceMock = mock<OpprettVedtakssnapshotService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock,
            opprettVedtakssnapshotService = opprettVedtakssnapshotServiceMock
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.FantIkkeBehandling.left()
        inOrder(behandlingRepoMock, distribuerIverksettingsbrevServiceMock, personServiceMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppgaveServiceMock,
            utbetalingServiceMock,
            opprettVedtakssnapshotServiceMock
        )
    }

    @Test
    fun `iverksett behandling finner ikke person`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val oppgaveServiceMock: OppgaveService = mock()

        val utbetalingServiceMock = mock<UtbetalingService>()

        val opprettVedtakssnapshotServiceMock = mock<OpprettVedtakssnapshotService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock,
            opprettVedtakssnapshotService = opprettVedtakssnapshotServiceMock
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.FantIkkePerson.left()
        inOrder(behandlingRepoMock, distribuerIverksettingsbrevServiceMock, personServiceMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppgaveServiceMock,
            utbetalingServiceMock,
            opprettVedtakssnapshotServiceMock
        )
    }

    @Test
    fun `iverksett behandling attesterer og saksbehandler kan ikke være samme person`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val oppgaveServiceMock: OppgaveService = mock()

        val utbetalingServiceMock = mock<UtbetalingService>()

        val opprettVedtakssnapshotServiceMock = mock<OpprettVedtakssnapshotService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock,
            opprettVedtakssnapshotService = opprettVedtakssnapshotServiceMock,
        ).iverksett(behandling.id, Attestant(behandling.saksbehandler()!!.navIdent))

        response shouldBe KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        inOrder(behandlingRepoMock, personServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppgaveServiceMock,
            utbetalingServiceMock,
            opprettVedtakssnapshotServiceMock
        )
    }

    @Test
    fun `iverksett behandling betaler ikke ut penger ved avslag`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService> {
            on { opprettJournalpost(any(), any()) } doReturn iverksattJournalpostId.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService> {
            on { distribuerBrev(any()) } doReturn behandling.copy(
                iverksattBrevbestillingId = iverksattBrevbestillingId,
            ).right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService>()

        val opprettVedtakssnapshotServiceMock = mock<OpprettVedtakssnapshotService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock,
            opprettVedtakssnapshotService = opprettVedtakssnapshotServiceMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
        ).iverksett(behandling.id, attestant)

        response shouldBe IverksattBehandling.UtenMangler(behandling).right()
        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppgaveServiceMock,
            opprettVedtakssnapshotServiceMock,
            journalførIverksettingServiceMock,
        ) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(journalførIverksettingServiceMock).opprettJournalpost(
                argThat { it shouldBe behandling },
                argThat {
                    it shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = tidspunkt,
                            avslagsgrunner = listOf(),
                            harEktefelle = false,
                            beregning = beregning

                        ),
                        attestantNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                        saksbehandlerNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName
                    )
                }
            )
            verify(behandlingRepoMock).oppdaterAttestering(behandling.id, Attestering.Iverksatt(attestant))
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandling.id,
                Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
            )
            verify(distribuerIverksettingsbrevServiceMock).distribuerBrev(
                behandling = argThat { it shouldBe behandling },
            )
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verify(opprettVedtakssnapshotServiceMock).opprettVedtak(
                argThat {
                    it shouldBe Vedtakssnapshot.Avslag(
                        id = it.id,
                        opprettet = it.opprettet,
                        behandling = behandling,
                        avslagsgrunner = listOf()
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppgaveServiceMock,
            utbetalingServiceMock,
            opprettVedtakssnapshotServiceMock
        )
    }

    @Test
    fun `iverksett behandling kaster exception ved feil tilstand`() {
        val behandling = mock<Behandling> {
            on { iverksett(any()) } doReturn this.mock.right()
            on { fnr } doReturn fnr
            on { status() } doReturn SIMULERT
        }
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val oppgaveServiceMock = mock<OppgaveService>()

        val utbetalingServiceMock = mock<UtbetalingService>()

        val opprettVedtakssnapshotServiceMock = mock<OpprettVedtakssnapshotService>()

        val exception = shouldThrow<Behandling.TilstandException> {
            val iverksett = createService(
                behandlingRepo = behandlingRepoMock,
                utbetalingService = utbetalingServiceMock,
                distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock,
                personService = personServiceMock,
                oppgaveService = oppgaveServiceMock,
                microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock,
                opprettVedtakssnapshotService = opprettVedtakssnapshotServiceMock
            ).iverksett(behandlingId, attestant)
            iverksett
        }

        exception.state shouldBe SIMULERT
        exception.message.shouldContain("Illegal operation")
        exception.operation.shouldContain("iverksett")

        inOrder(behandlingRepoMock, distribuerIverksettingsbrevServiceMock, personServiceMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandlingId)
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppgaveServiceMock,
            utbetalingServiceMock,
            opprettVedtakssnapshotServiceMock
        )
    }

    @Test
    fun `iverksett behandling betaler ut penger ved innvilgelse`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    any(), any(), any(), any()
                )
            } doReturn oversendtUtbetaling.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val opprettVedtakssnapshotServiceMock = mock<OpprettVedtakssnapshotService>()
        val observerMock: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock,
            opprettVedtakssnapshotService = opprettVedtakssnapshotServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock,
            observer = observerMock
        ).iverksett(behandling.id, attestant)

        response shouldBe IverksattBehandling.UtenMangler(behandling).right()

        inOrder(
            behandlingRepoMock,
            utbetalingServiceMock,
            personServiceMock,
            distribuerIverksettingsbrevServiceMock,
            behandlingMetricsMock,
            oppgaveServiceMock,
            opprettVedtakssnapshotServiceMock,
            observerMock
        ) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandling.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe behandling.sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering }
            )
            verify(behandlingRepoMock).leggTilUtbetaling(behandling.id, utbetalingForSimulering.id)
            verify(behandlingRepoMock).oppdaterAttestering(behandling.id, Attestering.Iverksatt(attestant))
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandling.id,
                Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
            )

            verify(opprettVedtakssnapshotServiceMock).opprettVedtak(
                argThat {
                    it shouldBe Vedtakssnapshot.Innvilgelse(
                        id = it.id,
                        opprettet = it.opprettet,
                        behandling = behandling,
                        utbetaling = oversendtUtbetaling
                    )
                }
            )
            verify(behandlingMetricsMock).incrementInnvilgetCounter(InnvilgetHandlinger.PERSISTERT)
            verify(observerMock).handle(
                argThat {
                    it shouldBe Event.Statistikk.BehandlingIverksatt(
                        IverksattBehandling.UtenMangler(
                            behandling
                        )
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            utbetalingServiceMock,
            personServiceMock,
            behandlingMetricsMock,
            oppgaveServiceMock,
            opprettVedtakssnapshotServiceMock,
            distribuerIverksettingsbrevServiceMock,
            observerMock
        )
    }

    @Test
    fun `iverksett behandling gir feilmelding ved inkonsistens i simuleringsresultat`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    behandling.sakId,
                    attestant,
                    beregning,
                    simulering
                )
            } doReturn KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
        }

        val opprettVedtakssnapshotServiceMock = mock<OpprettVedtakssnapshotService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock,
            opprettVedtakssnapshotService = opprettVedtakssnapshotServiceMock
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()

        inOrder(behandlingRepoMock, personServiceMock, utbetalingServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(utbetalingServiceMock).utbetal(behandling.sakId, attestant, beregning, simulering)
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            personServiceMock,
            utbetalingServiceMock,
            opprettVedtakssnapshotServiceMock
        )
    }

    @Test
    fun `iverksett behandling gir feilmelding dersom vi ikke får sendt utbetaling til oppdrag`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    sakId = argThat { it shouldBe behandling.sakId },
                    attestant = argThat { it shouldBe attestant },
                    beregning = argThat { it shouldBe beregning },
                    simulering = argThat { it shouldBe simulering }
                )
            } doReturn KunneIkkeUtbetale.Protokollfeil.left()
        }

        val opprettVedtakssnapshotServiceMock = mock<OpprettVedtakssnapshotService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.KunneIkkeUtbetale.left()

        inOrder(behandlingRepoMock, personServiceMock, utbetalingServiceMock, opprettVedtakssnapshotServiceMock) {
            verify(behandlingRepoMock).hentBehandling(
                argThat {
                    it shouldBe behandling.id
                }
            )
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe behandling.sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering }
            )
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            personServiceMock,
            utbetalingServiceMock,
            opprettVedtakssnapshotServiceMock
        )
    }

    private fun beregnetBehandling() = BehandlingTestUtils.createOpprettetBehandling()
        .copy(
            status = Behandling.BehandlingsStatus.BEREGNET_INNVILGET,
            saksbehandler = BehandlingTestUtils.saksbehandler,
            attestering = Attestering.Iverksatt(BehandlingTestUtils.attestant),
            oppgaveId = oppgaveId,
            beregning = beregning,
        )

    private fun behandlingTilAttestering(status: Behandling.BehandlingsStatus) = beregnetBehandling().copy(
        simulering = simulering,
        status = status,
        saksbehandler = saksbehandler
    )

    private val oppdragsmelding = Utbetalingsrequest(
        value = ""
    )

    private val beregning = TestBeregning

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(),
        nettoBeløp = 191500,
        periodeList = listOf()
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        sakId = sakId,
        saksnummer = saksnummer,
        utbetalingslinjer = listOf(),
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        behandler = attestant,
        avstemmingsnøkkel = Avstemmingsnøkkel()
    )

    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)

    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)

    private fun createService(
        behandlingRepo: BehandlingRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock,
        opprettVedtakssnapshotService: OpprettVedtakssnapshotService = mock(),
        observer: EventObserver = BehandlingTestUtils.observerMock,
        journalførIverksettingService: JournalførIverksettingService = mock(),
        distribuerIverksettingsbrevService: DistribuerIverksettingsbrevService = mock()
    ) = IverksettBehandlingService(
        behandlingRepo = behandlingRepo,
        utbetalingService = utbetalingService,
        oppgaveService = oppgaveService,
        personService = personService,
        behandlingMetrics = behandlingMetrics,
        clock = BehandlingTestUtils.fixedClock,
        microsoftGraphApiClient = microsoftGraphApiOppslag,
        opprettVedtakssnapshotService = opprettVedtakssnapshotService,
        journalførIverksettingService = journalførIverksettingService,
        distribuerIverksettingsbrevService = distribuerIverksettingsbrevService,
    ).apply { addObserver(observer) }
}
