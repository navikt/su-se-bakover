package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakType
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.beregning.TestBeregningSomGirOpphør
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.aktørId
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.beregningMock
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.createRevurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.fnr
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periode
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingId
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.sak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.sakId
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.saksnummer
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.søknadsbehandlingVedtak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

internal class RevurderingServiceImplTest {
    @Test
    fun `oppretter en revurdering`() {
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
            personService = personServiceMock,
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.getFraOgMed(),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        ).getOrHandle { throw RuntimeException("Skal ikke kunne skje") }

        inOrder(sakServiceMock, personServiceMock, oppgaveServiceMock, revurderingRepoMock) {
            verify(sakServiceMock).hentSak(sakId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = null,
                    )
                },
            )
            verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual.right() })
        }

        verifyNoMoreInteractions(sakServiceMock, personServiceMock, oppgaveServiceMock, revurderingRepoMock)
    }

    @Test
    fun `kan ikke revurdere når det ikke eksisterer vedtak`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.copy(
                vedtakListe = emptyList(),
            ).right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.getFraOgMed(),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
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
            utbetalinger = sak.utbetalinger,
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.getFraOgMed(),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `oppretter ikke en revurdering hvis perioden er i samme måned`() {
        val actual = createRevurderingService().opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = 1.januar(2021),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.KanIkkeRevurdereInneværendeMånedEllerTidligere.left()
    }

    @Test
    fun `kan beregne og simulere`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
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
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = søknadsbehandlingVedtak.beregning.getPeriode(),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).getOrHandle { throw Exception("Vi skal få tilbake en revurdering") }
        if (actual !is SimulertRevurdering) throw RuntimeException("Skal returnere en simulert revurdering")

        inOrder(revurderingRepoMock, utbetalingServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(utbetalingServiceMock).simulerUtbetaling(
                sakId = argThat { it shouldBe sakId },
                saksbehandler = argThat { it shouldBe saksbehandler },
                beregning = argThat { it shouldBe actual.beregning },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `kan ikke beregne og simulere en revurdering som er til attestering`() {
        val revurderingTilAttestering = RevurderingTilAttestering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            beregning = mock(),
            simulering = mock(),
            oppgaveId = mock(),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurderingTilAttestering
        }

        val result = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            fradrag = listOf(),
        )
        result shouldBe KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand(
            RevurderingTilAttestering.Innvilget::class,
            SimulertRevurdering::class,
        )
            .left()

        verify(revurderingRepoMock).hent(revurderingId)
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil når simulering feiler`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = søknadsbehandlingVedtak.beregning.getPeriode(),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        actual shouldBe KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet.left()

        inOrder(revurderingRepoMock) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `sender til attestering`() {
        val simulertRevurdering = mock<SimulertRevurdering> {
            on { fnr } doReturn fnr
            on { saksnummer } doReturn saksnummer
            on { id } doReturn revurderingId
            on { periode } doReturn periode
            on { tilRevurdering } doReturn søknadsbehandlingVedtak
            on { opprettet } doReturn Tidspunkt.EPOCH
            on { beregning } doReturn mock()
            on { simulering } doReturn mock()
            on { tilAttestering(any(), any(), any()) } doReturn mock()
            on { oppgaveId } doReturn OppgaveId("oppgaveid")
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val eventObserver: EventObserver = mock()

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).apply { addObserver(eventObserver) }

        val actual = revurderingService.sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ).getOrHandle { throw RuntimeException("Skal ikke kunne skje") }

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(revurderingRepoMock).hentEventuellTidligereAttestering(argThat { it shouldBe revurderingId })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.AttesterRevurdering(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = null,
                    )
                },
            )
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe simulertRevurdering.oppgaveId })
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
            verify(eventObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.RevurderingStatistikk.RevurderingTilAttestering(
                        actual as RevurderingTilAttestering,
                    )
                },
            )
        }

        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `sender ikke til attestering hvis revurdering er ikke simulert`() {
        val opprettetRevurdering = mock<OpprettetRevurdering>()

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val result = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        result shouldBe KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
            OpprettetRevurdering::class,
            RevurderingTilAttestering::class,
        ).left()

        verify(revurderingRepoMock).hent(revurderingId)
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `sender ikke til attestering hvis henting av aktørId feiler`() {
        val simulertRevurdering = mock<SimulertRevurdering> {
            on { fnr } doReturn fnr
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId.left()

        inOrder(revurderingRepoMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verifyNoMoreInteractions(revurderingRepoMock)
        }
    }

    @Test
    fun `sender ikke til attestering hvis oppretting av oppgave feiler`() {
        val simulertRevurdering = mock<SimulertRevurdering> {
            on { fnr } doReturn fnr
            on { saksnummer } doReturn saksnummer
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn mock()
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
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave.left()

        inOrder(revurderingRepoMock, personServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(revurderingRepoMock).hentEventuellTidligereAttestering(revurderingId)

            verifyNoMoreInteractions(revurderingRepoMock, personServiceMock)
        }
    }

    @Test
    fun `iverksetter endring av ytelse`() {
        val attestant = NavIdentBruker.Attestant("attestant")

        val testsimulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.now(),
            nettoBeløp = 0,
            periodeList = listOf(),
        )
        val utbetalingId = UUID30.randomUUID()
        val iverksattRevurdering = IverksattRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregning,
            simulering = testsimulering,
            attestering = Attestering.Iverksatt(attestant),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )
        val revurderingTilAttestering = RevurderingTilAttestering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregning,
            simulering = testsimulering,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }

        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.UtenKvittering> {
            on { id } doReturn utbetalingId
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn utbetalingMock.right()
        }
        val vedtakRepoMock = mock<VedtakRepo>()
        val eventObserver: EventObserver = mock()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
        ).apply { addObserver(eventObserver) }.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        ) shouldBe iverksattRevurdering.right()
        inOrder(
            revurderingRepoMock,
            utbetalingMock,
            utbetalingServiceMock,
            vedtakRepoMock,
            eventObserver,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe revurderingTilAttestering.sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe revurderingTilAttestering.beregning },
                simulering = argThat { it shouldBe revurderingTilAttestering.simulering },
            )
            verify(utbetalingMock, times(2)).id
            verify(vedtakRepoMock).lagre(
                argThat {
                    it should beOfType<Vedtak.EndringIYtelse>()
                    it.vedtakType shouldBe VedtakType.ENDRING
                },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe iverksattRevurdering })
            verify(eventObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.RevurderingStatistikk.RevurderingIverksatt(iverksattRevurdering)
                },
            )
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
            utbetalingServiceMock,
            utbetalingMock,
        )
    }

    @Test
    fun `iverksetter opphør av ytelse`() {
        val revurderingTilAttestering = RevurderingTilAttestering.Opphørt(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregningSomGirOpphør,
            simulering = mock(),
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )
        val attestant = NavIdentBruker.Attestant("ATTT")

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }
        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.UtenKvittering>() {
            on { id } doReturn UUID30.randomUUID()
        }
        val utbetalingServiceMock = mock<UtbetalingService>() {
            on { opphør(any(), any(), any(), any()) } doReturn utbetalingMock.right()
        }
        val vedtakRepoMock = mock<VedtakRepo>()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
        ).iverksett(
            revurderingId,
            attestant,
        )

        inOrder(
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(utbetalingServiceMock).opphør(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                simulering = argThat { it shouldBe revurderingTilAttestering.simulering },
                opphørsdato = argThat { it shouldBe revurderingTilAttestering.beregning.getPeriode().getFraOgMed() },
            )
            verify(vedtakRepoMock).lagre(
                argThat {
                    it should beOfType<Vedtak.EndringIYtelse>()
                    it.vedtakType shouldBe VedtakType.OPPHØR
                },
            )
            verify(revurderingRepoMock).lagre(any())
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
        )
    }

    @Test
    fun `underkjenner en revurdering`() {
        val tilAttestering = RevurderingTilAttestering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            beregning = beregningMock,
            simulering = mock(),
            oppgaveId = OppgaveId("oppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )

        val attestering = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant(navIdent = "123"),
            grunn = Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
            kommentar = "pls math",
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn tilAttestering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val nyOppgaveId = OppgaveId("nyOppgaveId")
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val eventObserver: EventObserver = mock()

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).apply { addObserver(eventObserver) }

        val actual = revurderingService.underkjenn(
            revurderingId = revurderingId,
            attestering = attestering,
        ).getOrHandle { throw RuntimeException("Skal ikke kunne skje") }

        actual shouldBe tilAttestering.underkjenn(
            attestering, nyOppgaveId,
        )

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = saksbehandler,
                    )
                },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe tilAttestering.oppgaveId })

            verify(eventObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.RevurderingStatistikk.RevurderingUnderkjent(actual)
                },
            )
        }

        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `kan beregne og simuler underkjent revurdering på nytt`() {
        val attestering = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant(navIdent = "123"),
            grunn = Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
            kommentar = "pls math",
        )
        val underkjentRevurdering = UnderkjentRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            beregning = beregningMock,
            simulering = mock(),
            oppgaveId = OppgaveId("oppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            attestering = attestering,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn underkjentRevurdering
        }

        val simulertUtbetaling = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn simulertUtbetaling.right()
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        )

        val actual = revurderingService.beregnOgSimuler(
            underkjentRevurdering.id,
            saksbehandler,
            listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 4000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).getOrElse { throw RuntimeException("Noe gikk galt") }
        if (actual !is SimulertRevurdering.Innvilget) throw RuntimeException("Skal returnere en simulert revurdering")

        inOrder(revurderingRepoMock, utbetalingServiceMock) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(utbetalingServiceMock).simulerUtbetaling(
                sakId = argThat { it shouldBe sakId },
                saksbehandler = argThat { it shouldBe saksbehandler },
                beregning = argThat { it shouldBe actual.beregning },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }

        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
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
        }

        val vedtakMock = mock<Vedtak.EndringIYtelse> {
            on { behandling } doReturn behandlingMock
            on { beregning } doReturn mock()
            on { behandlingsinformasjon } doReturn behandlingsinformasjonMock
        }

        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = vedtakMock,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "Mr Test",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )

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
            brevService = brevServiceMock,
        ).lagBrevutkast(
            revurderingId = revurderingId,
            fritekst = "",
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
                            attestantNavn = "-",
                            revurdertBeregning = simulertRevurdering.beregning,
                            fritekst = "",
                            harEktefelle = false,
                        )
                },
            )
        }

        verifyNoMoreInteractions(
            revurderingRepoMock,
            personServiceMock,
            personServiceMock,
            microsoftGraphApiClientMock,
            brevServiceMock,
        )
    }

    @Test
    fun `får feil når vi ikke kan hente person`() {
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            oppgaveId = OppgaveId("oppgaveid"),
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "Mr Test",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )

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
            fritekst = "",
        )

        actual shouldBe KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson.left()

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

        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            oppgaveId = OppgaveId("oppgaveid"),
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "Mr Test",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )

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
            fritekst = "",
        )

        actual shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()

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

        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            oppgaveId = OppgaveId("oppgaveid"),
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "Mr Test",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )

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
            brevService = brevServiceMock,
        ).lagBrevutkast(
            revurderingId = revurderingId,
            fritekst = "",
        )

        actual shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast.left()

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
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            createRevurderingService(
                revurderingRepo = revurderingRepoMock,
            ).lagBrevutkast(
                revurderingId = revurderingId,
                fritekst = "",
            )
        }

        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `kan ikke lage brev med status beregnet`() {
        val beregnetRevurdering = BeregnetRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn beregnetRevurdering
        }

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            createRevurderingService(
                revurderingRepo = revurderingRepoMock,
            ).lagBrevutkast(
                revurderingId = revurderingId,
                fritekst = "",
            )
        }

        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        verifyNoMoreInteractions(revurderingRepoMock)
    }
}
