package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.søknadsbehandlingSimulert
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class SøknadsbehandlingServiceAttesteringTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val søknadId = UUID.randomUUID()
    private val oppgaveId = OppgaveId("o")
    private val fnr = Fnr.generer()
    private val nyOppgaveId = OppgaveId("999")
    private val aktørId = AktørId("12345")
    private val periode = år(2021)
    private val stønadsperiode = Stønadsperiode.create(periode, "")
    private val simulertBehandling = Søknadsbehandling.Simulert(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
            id = søknadId,
            opprettet = fixedTidspunkt,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = JournalpostId("j"),
        ),
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "NAVN",
            datoBeregnet = idag(fixedClock),
            nettoBeløp = 191500,
            periodeList = listOf(),
        ),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        oppgaveId = oppgaveId,
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
        attesteringer = Attesteringshistorikk.empty(),
        avkorting = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
    )

    private val saksbehandler = NavIdentBruker.Saksbehandler("Z12345")

    @Test
    fun `sjekk at vi sender inn riktig oppgaveId ved lukking av oppgave ved attestering`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val eventObserver: EventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver,
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(
                simulertBehandling.id,
                saksbehandler,
                "",
            ),
        )

        val expected = Søknadsbehandling.TilAttestering.Innvilget(
            id = simulertBehandling.id,
            opprettet = simulertBehandling.opprettet,
            behandlingsinformasjon = simulertBehandling.behandlingsinformasjon,
            søknad = simulertBehandling.søknad,
            beregning = simulertBehandling.beregning,
            simulering = simulertBehandling.simulering,
            sakId = simulertBehandling.sakId,
            saksnummer = simulertBehandling.saksnummer,
            fnr = simulertBehandling.fnr,
            oppgaveId = nyOppgaveId,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            stønadsperiode = simulertBehandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
        )

        actual shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
            verify(personServiceMock).hentAktørId(fnr)
            verify(oppgaveServiceMock).opprettOppgave(
                config = OppgaveConfig.AttesterSøknadsbehandling(
                    søknadId = søknadId,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                ),
            )
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(expected), anyOrNull())
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verify(eventObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingTilAttestering(expected)
                },
            )
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `svarer med feil dersom man ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val personServiceMock: PersonService = mock()
        val oppgaveServiceMock: OppgaveService = mock()
        val eventObserver: EventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver,
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(
                simulertBehandling.id,
                saksbehandler,
                "",
            ),
        )

        actual shouldBe SøknadsbehandlingService.KunneIkkeSendeTilAttestering.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)

        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `svarer med feil dersom man ikke finner aktørid for person`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val eventObserver: EventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver,
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(
                simulertBehandling.id,
                saksbehandler,
                "",
            ),
        )

        actual shouldBe SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()

        verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
        verify(personServiceMock).hentAktørId(simulertBehandling.fnr)

        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `svarer med feil dersom man ikke får til å opprette oppgave til attestant`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }
        val eventObserver: EventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver,
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(
                simulertBehandling.id,
                saksbehandler,
                "",
            ),
        )

        actual shouldBe SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()

        verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
        verify(personServiceMock).hentAktørId(simulertBehandling.fnr)
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.AttesterSøknadsbehandling(
                    søknadId = simulertBehandling.søknad.id,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                )
            },
        )

        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `sender til attestering selv om lukking av eksisterende oppgave feiler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }

        val eventObserver: EventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver,
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(simulertBehandling.id, saksbehandler, ""),
        )

        val expected = Søknadsbehandling.TilAttestering.Innvilget(
            id = simulertBehandling.id,
            opprettet = simulertBehandling.opprettet,
            behandlingsinformasjon = simulertBehandling.behandlingsinformasjon,
            søknad = simulertBehandling.søknad,
            beregning = simulertBehandling.beregning,
            simulering = simulertBehandling.simulering,
            sakId = simulertBehandling.sakId,
            saksnummer = simulertBehandling.saksnummer,
            fnr = simulertBehandling.fnr,
            oppgaveId = nyOppgaveId,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            stønadsperiode = simulertBehandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
        )

        actual shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
            verify(personServiceMock).hentAktørId(fnr)
            verify(oppgaveServiceMock).opprettOppgave(
                config = OppgaveConfig.AttesterSøknadsbehandling(
                    søknadId = søknadId,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                ),
            )
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(expected), anyOrNull())
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verify(eventObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingTilAttestering(expected)
                },
            )
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `får ikke sendt til attestering dersom det eksisterer revurderinger som avventer kravgrunnlag`() {
        val søknadsbehandling = søknadsbehandlingSimulert().second
        val mock = mock<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>()

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn søknadsbehandling
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn listOf(mock)
            },
        ).let {
            it.søknadsbehandlingService.sendTilAttestering(
                SøknadsbehandlingService.SendTilAttesteringRequest(
                    behandlingId = søknadsbehandling.id,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "nei",
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeSendeTilAttestering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
        }
    }
}
