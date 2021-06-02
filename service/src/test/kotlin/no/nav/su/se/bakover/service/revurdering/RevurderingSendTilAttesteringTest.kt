package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregningSomGirOpphør
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.junit.jupiter.api.Test

class RevurderingSendTilAttesteringTest {

    @Test
    fun `sender til attestering`() {
        val simulertRevurdering = RevurderingTestUtils.simulertRevurderingInnvilget

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(RevurderingTestUtils.revurderingId) } doReturn simulertRevurdering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn RevurderingTestUtils.aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val eventObserver: EventObserver = mock()

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).apply { addObserver(eventObserver) }

        val actual = revurderingService.sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = RevurderingTestUtils.revurderingId,
                saksbehandler = RevurderingTestUtils.saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ).getOrHandle { throw RuntimeException("Skal ikke kunne skje") }

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe RevurderingTestUtils.revurderingId })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe RevurderingTestUtils.fnr })
            verify(revurderingRepoMock).hentEventuellTidligereAttestering(argThat { it shouldBe RevurderingTestUtils.revurderingId })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.AttesterRevurdering(
                        saksnummer = RevurderingTestUtils.saksnummer,
                        aktørId = RevurderingTestUtils.aktørId,
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
            on { hent(RevurderingTestUtils.revurderingId) } doReturn opprettetRevurdering
        }

        val result = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = RevurderingTestUtils.revurderingId,
                saksbehandler = RevurderingTestUtils.saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        result shouldBe KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
            OpprettetRevurdering::class,
            RevurderingTilAttestering::class,
        ).left()

        verify(revurderingRepoMock).hent(RevurderingTestUtils.revurderingId)
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `sender ikke til attestering hvis henting av aktørId feiler`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(RevurderingTestUtils.revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = RevurderingTestUtils.revurderingId,
                saksbehandler = RevurderingTestUtils.saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId.left()

        inOrder(revurderingRepoMock) {
            verify(revurderingRepoMock).hent(RevurderingTestUtils.revurderingId)
            verifyNoMoreInteractions(revurderingRepoMock)
        }
    }

    @Test
    fun `sender ikke til attestering hvis oppretting av oppgave feiler`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(RevurderingTestUtils.revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn RevurderingTestUtils.aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveFeil.KunneIkkeOppretteOppgave.left()
        }

        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = RevurderingTestUtils.revurderingId,
                saksbehandler = RevurderingTestUtils.saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave.left()

        inOrder(revurderingRepoMock, personServiceMock) {
            verify(revurderingRepoMock).hent(RevurderingTestUtils.revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe RevurderingTestUtils.fnr })
            verify(revurderingRepoMock).hentEventuellTidligereAttestering(RevurderingTestUtils.revurderingId)

            verifyNoMoreInteractions(revurderingRepoMock, personServiceMock)
        }
    }

    @Test
    fun `kan ikke sende revurdering med simulert feilutbetaling til attestering`() {
        val simuleringMock = mock<Simulering> {
            on { harFeilutbetalinger() } doReturn true
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(RevurderingTestUtils.revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget.copy(
                simulering = simuleringMock,
            )
        }

        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = RevurderingTestUtils.revurderingId,
                saksbehandler = RevurderingTestUtils.saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left()

        verify(revurderingRepoMock, never()).lagre(any())
    }

    @Test
    fun `kan ikke sende revurdering med utfall som ikke støttes til attestering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(RevurderingTestUtils.revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget.copy(
                beregning = TestBeregningSomGirOpphør,
            )
        }

        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = RevurderingTestUtils.revurderingId,
                saksbehandler = RevurderingTestUtils.saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(
            listOf(
                RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
                RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
            ),
        ).left()

        verify(revurderingRepoMock, never()).lagre(any())
    }

    @Test
    fun `kan ikke sende revurdering med utfall som ikke støttes til attestering - simulering har feilubtbetaling`() {
        val simuleringMock = mock<Simulering> {
            on { harFeilutbetalinger() } doReturn true
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(RevurderingTestUtils.revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget.copy(
                simulering = simuleringMock,
            )
        }

        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = RevurderingTestUtils.revurderingId,
                saksbehandler = RevurderingTestUtils.saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left()

        verify(revurderingRepoMock, never()).lagre(any())
    }
}
