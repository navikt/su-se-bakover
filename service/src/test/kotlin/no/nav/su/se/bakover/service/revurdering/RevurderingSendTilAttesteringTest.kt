package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.formueGrunnlagUtenEps0Innvilget
import no.nav.su.se.bakover.test.formueGrunnlagUtenEpsAvslått
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttUføreOgAndreInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerInnvilgetRevurdering
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.temporal.ChronoUnit

class RevurderingSendTilAttesteringTest {

    @Test
    fun `sender til attestering`() {
        val simulertRevurdering = RevurderingTestUtils.simulertRevurderingInnvilget

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

        val revurderingService = RevurderingTestUtils.createRevurderingService(
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
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val result = RevurderingTestUtils.createRevurderingService(
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
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val actual = RevurderingTestUtils.createRevurderingService(
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
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
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
    fun `kan ikke sende revurdering med simulert feilutbetaling til attestering`() {
        val simuleringMock = mock<Simulering> {
            on { harFeilutbetalinger() } doReturn true
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget.copy(
                simulering = simuleringMock,
            )
        }

        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left()

        verify(revurderingRepoMock, never()).lagre(any())
    }

    @Test
    fun `formueopphør må være fra første måned`() {
        val stønadsperiode = RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
        val revurderingsperiode = stønadsperiode.periode
        val revurderingRepoMock = mock<RevurderingRepo> {
            val førsteUførevurderingsperiode = Periode.create(
                fraOgMed = revurderingsperiode.fraOgMed,
                tilOgMed = revurderingsperiode.fraOgMed.endOfMonth(),
            )
            val andreUførevurderingsperiode = Periode.create(
                fraOgMed = revurderingsperiode.fraOgMed.plus(1, ChronoUnit.MONTHS),
                tilOgMed = revurderingsperiode.tilOgMed,
            )

            val vilkårsvurderinger = vilkårsvurderingerInnvilgetRevurdering(
                periode = revurderingsperiode,
                formue = Vilkår.Formue.Vurdert.createFromGrunnlag(
                    grunnlag = nonEmptyListOf(
                        formueGrunnlagUtenEps0Innvilget(
                            periode = førsteUførevurderingsperiode,
                            bosituasjon = grunnlagsdataEnsligUtenFradrag().bosituasjon.singleFullstendigOrThrow(),
                        ),
                        formueGrunnlagUtenEpsAvslått(
                            periode = andreUførevurderingsperiode,
                            bosituasjon = grunnlagsdataEnsligUtenFradrag().bosituasjon.singleFullstendigOrThrow(),
                        ),
                    ),
                ),
            )
            val simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak =
                simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                    stønadsperiode = stønadsperiode,
                    revurderingsperiode = revurderingsperiode,
                    grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger(
                        grunnlagsdataEnsligUtenFradrag(periode = revurderingsperiode),
                        vilkårsvurderinger,
                    ),
                ).second
            on { hent(revurderingId) } doReturn simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
        }

        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(
            listOf(
                RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
            ),
        ).left()

        verify(revurderingRepoMock, never()).lagre(any())
    }

    @Test
    fun `uføreopphør kan ikke gjøres i kombinasjon med fradragsendringer`() {
        val stønadsperiode = RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
        val revurderingsperiode = stønadsperiode.periode
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                stønadsperiode = stønadsperiode,
                revurderingsperiode = revurderingsperiode,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataEnsligMedFradrag(periode = revurderingsperiode).let {
                    GrunnlagsdataOgVilkårsvurderinger(
                        it,
                        vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(
                            periode = stønadsperiode.periode,
                            bosituasjon = it.bosituasjon.singleFullstendigOrThrow(),
                        ),
                    )
                },
            ).second
        }
        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(
            listOf(
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
            on { hent(revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget.copy(
                simulering = simuleringMock,
            )
        }

        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left()

        verify(revurderingRepoMock, never()).lagre(any())
    }
}
