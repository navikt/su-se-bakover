package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doReturnConsecutively
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakType
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.person
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.beregning.TestBeregningSomGirOpphør
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.aktørId
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.beregningMock
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.createRevurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.fnr
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.opprettetRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periode
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingId
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.sakId
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.saksnummer
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.simulertRevurderingInnvilget
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.søknadsbehandlingVedtak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.uføregrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.vilkårsvurderinger
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.vurderingsperiodeUføre
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

internal class RevurderingServiceImplTest {

    @Test
    fun `kan beregne og simulere`() {
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periode,
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 10,
        )
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata(
                uføregrunnlag = listOf(uføregrunnlag),
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag(
                        fradrag = FradragFactory.ny(
                            type = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 10000.0,
                            periode = søknadsbehandlingVedtak.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Innvilget,
                            grunnlag = uføregrunnlag,
                            periode = periode,
                            begrunnelse = "ok2k",
                        ),
                    ),
                ),
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
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
        ).getOrHandle { throw Exception("Vi skal få tilbake en revurdering") }

        actual.let {
            it.revurdering shouldBe beOfType<SimulertRevurdering.Innvilget>()
            it.feilmeldinger shouldBe emptyList()
        }

        inOrder(
            revurderingRepoMock,
            utbetalingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(utbetalingServiceMock).simulerUtbetaling(
                sakId = argThat { it shouldBe sakId },
                saksbehandler = argThat { it shouldBe saksbehandler },
                beregning = argThat { it shouldBe (actual.revurdering as SimulertRevurdering).beregning },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.revurdering })
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
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurderingTilAttestering
        }

        val result = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
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
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periode,
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 10,
        )
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata(
                uføregrunnlag = listOf(uføregrunnlag),
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag(
                        fradrag = FradragFactory.ny(
                            type = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 10000.0,
                            periode = søknadsbehandlingVedtak.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Innvilget,
                            grunnlag = uføregrunnlag,
                            periode = periode,
                            begrunnelse = "ok2k",
                        ),
                    ),
                ),
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
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
        )

        actual shouldBe KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet.left()

        inOrder(revurderingRepoMock) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock)
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
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
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
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
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
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        val attestant = NavIdentBruker.Attestant("ATTT")

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }
        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.UtenKvittering> {
            on { id } doReturn UUID30.randomUUID()
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
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
                opphørsdato = argThat { it shouldBe revurderingTilAttestering.periode.fraOgMed },
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
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
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
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periode,
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 10,
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
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            attestering = attestering,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata(
                uføregrunnlag = listOf(uføregrunnlag),
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag(
                        fradrag = FradragFactory.ny(
                            type = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 4000.0,
                            periode = periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Innvilget,
                            grunnlag = uføregrunnlag,
                            periode = periode,
                            begrunnelse = "ok2k",
                        ),
                    ),
                ),
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
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
        ).getOrElse { throw RuntimeException("Noe gikk galt") }

        actual.let {
            it.revurdering shouldBe beOfType<SimulertRevurdering.Innvilget>()
            it.feilmeldinger shouldBe emptyList()
        }

        inOrder(revurderingRepoMock, utbetalingServiceMock) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(utbetalingServiceMock).simulerUtbetaling(
                sakId = argThat { it shouldBe sakId },
                saksbehandler = argThat { it shouldBe saksbehandler },
                beregning = argThat { it shouldBe (actual.revurdering as SimulertRevurdering).beregning },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.revurdering })
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
            forhåndsvarsel = null,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
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
                            forventetInntektStørreEnn0 = false,
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
        val simulertRevurdering = simulertRevurderingInnvilget

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

        val simulertRevurdering = simulertRevurderingInnvilget

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

        val simulertRevurdering = simulertRevurderingInnvilget

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
            forhåndsvarsel = null,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
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
            forhåndsvarsel = null,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
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

    @Test
    fun `forhåndsvarsler en simulert-revurdering`() {
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
            on { lagre(any()) }.doNothing()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val journalpostId = JournalpostId("journalpostId")
        val brevbestillingId = BrevbestillingId("brevbestillignsId")
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn journalpostId.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn Unit.right()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        val revurdering = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
        ).forhåndsvarsleEllerSendTilAttestering(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            revurderingshandling = Revurderingshandling.FORHÅNDSVARSLE,
            fritekst = "",
        ).getOrHandle { throw RuntimeException("Her skulle vi ha fått en revurdering") }

        revurdering.forhåndsvarsel shouldBe Forhåndsvarsel.SkalForhåndsvarsles.Sendt(journalpostId, brevbestillingId)

        inOrder(revurderingRepoMock, personServiceMock, brevServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe LagBrevRequest.Forhåndsvarsel(
                        person = person,
                        saksbehandlerNavn = "Sak S. behandler",
                        fritekst = "",
                    )
                },
                argThat { it shouldBe saksnummer },
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe journalpostId })
            verify(revurderingRepoMock).lagre(
                argThat { it shouldBe simulertRevurdering },
            )
            verify(oppgaveServiceMock).oppdaterOppgave(
                argThat {
                    it shouldBe OppgaveId("oppgaveid")
                },
                argThat {
                    it shouldBe "Forhåndsvarsel er sendt."
                },
            )
        }

        verifyNoMoreInteractions(
            revurderingRepoMock,
            personServiceMock,
            brevServiceMock,
            oppgaveServiceMock,
        )
    }

    @Test
    fun `forhåndsvarsler ikke en allerede forhåndsvarslet revurdering`() {
        val simulertRevurdering = simulertRevurderingInnvilget

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).forhåndsvarsleEllerSendTilAttestering(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            revurderingshandling = Revurderingshandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.AlleredeForhåndsvarslet.left()
    }

    private fun testForhåndsvarslerIkkeGittRevurdering(revurdering: Revurdering) {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurdering
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).forhåndsvarsleEllerSendTilAttestering(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            revurderingshandling = Revurderingshandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.UgyldigTilstand(
            revurdering::class,
            SimulertRevurdering::class,
        ).left()
    }

    @Test
    fun `forhåndsvarsler bare simulerte revurderinger`() {
        val opprettet = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        testForhåndsvarslerIkkeGittRevurdering(opprettet)

        val beregnet = BeregnetRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            beregning = TestBeregning,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        testForhåndsvarslerIkkeGittRevurdering(beregnet)
    }

    @Test
    fun `forhåndsvarsler men hentPerson failer`() {
        val simulertRevurdering = simulertRevurderingInnvilget.copy(
            forhåndsvarsel = null,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
        ).forhåndsvarsleEllerSendTilAttestering(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            revurderingshandling = Revurderingshandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.FantIkkePerson.left()
    }

    @Test
    fun `forhåndsvarsler men journalføring failer`() {
        val simulertRevurdering = simulertRevurderingInnvilget.copy(
            forhåndsvarsel = null,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
            on { hentPerson(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
        ).forhåndsvarsleEllerSendTilAttestering(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            revurderingshandling = Revurderingshandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.KunneIkkeJournalføre.left()
    }

    @Test
    fun `forhåndsvarsler men distribuering failer`() {
        val simulertRevurdering = simulertRevurderingInnvilget.copy(
            forhåndsvarsel = null,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
            on { hentPerson(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn JournalpostId("journalpostId").right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
        ).forhåndsvarsleEllerSendTilAttestering(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            revurderingshandling = Revurderingshandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.KunneIkkeDistribuere.left()
    }

    @Test
    fun `forhåndsvarsler men oppgave failer`() {
        val simulertRevurdering = simulertRevurderingInnvilget.copy(
            forhåndsvarsel = null,
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
            on { hentPerson(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn JournalpostId("journalpostId").right()
            on { distribuerBrev(any()) } doReturn BrevbestillingId("brevbeswtillignsId").right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn OppgaveFeil.KunneIkkeOppdatereOppgave.left()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
        ).forhåndsvarsleEllerSendTilAttestering(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            revurderingshandling = Revurderingshandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.KunneIkkeOppretteOppgave.left()
    }

    @Test
    fun `fortsetter etter forhåndsvarsling`() {
        val journalpostId = JournalpostId(
            "journalpostId",
        )
        val brevbestillingId = BrevbestillingId("brevbestillignsid")
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt(
                journalpostId,
                brevbestillingId,
            ),
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = mock {
                on { resultat } doReturn Resultat.Innvilget
            },
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturnConsecutively listOf(
                simulertRevurdering,
                simulertRevurdering.copy(
                    forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                        journalpostId = journalpostId,
                        brevbestillingId = brevbestillingId,
                        valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                        begrunnelse = "",
                    ),
                ),
            )
            on { hentEventuellTidligereAttestering(any()) } doReturn null
            on { lagre(any()) }.doNothing()
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

        val revurdering = revurderingService.fortsettEtterForhåndsvarsling(
            FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                begrunnelse = "",
                fritekstTilBrev = "",
            ),
        )

        verify(revurderingRepoMock).oppdaterForhåndsvarsel(any(), any())

        revurdering.map { it.forhåndsvarsel } shouldBeRight Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
            journalpostId = journalpostId,
            brevbestillingId = brevbestillingId,
            valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
            begrunnelse = "",
        )
    }

    @Test
    fun `beslutter ikke en allerede besluttet forhåndsvarsling`() {
        val journalpostId = JournalpostId(
            "journalpostId",
        )
        val brevbestillingId = BrevbestillingId("brevbestillignsid")
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                journalpostId,
                brevbestillingId,
                BeslutningEtterForhåndsvarsling.FortsettMedAndreOpplysninger,
                "",
            ),
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).fortsettEtterForhåndsvarsling(
            FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                begrunnelse = "",
                fritekstTilBrev = "",
            ),
        ) shouldBeLeft FortsettEtterForhåndsvarselFeil.AlleredeBesluttet
    }

    @Test
    fun `beslutter ikke en allerede besluttet forhåndsvarsling ingenEndring`() {
        val simulertRevurdering = simulertRevurderingInnvilget

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).fortsettEtterForhåndsvarsling(
            FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                begrunnelse = "",
                fritekstTilBrev = "",
            ),
        ) shouldBeLeft FortsettEtterForhåndsvarselFeil.AlleredeBesluttet
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom revurderingen ikke finnes`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).lagBrevutkastForForhåndsvarsling(
            UUID.randomUUID(),
            "fritekst til forhåndsvarsling",
        ) shouldBeLeft KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom vi ikke finner personen knyttet til revurderingen`() {
        val simulertRevurdering = simulertRevurderingInnvilget

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
        ).lagBrevutkastForForhåndsvarsling(
            UUID.randomUUID(),
            "fritekst til forhåndsvarsling",
        ) shouldBeLeft KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom vi klarer lage brevet`() {
        val simulertRevurdering = simulertRevurderingInnvilget
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
        ).lagBrevutkastForForhåndsvarsling(
            UUID.randomUUID(),
            "fritekst til forhåndsvarsling",
        ) shouldBeLeft KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast
    }

    @Test
    fun `hvis vilkår ikke er oppfylt, fører revurderingen til et opphør`() {
        val simulertUtbetalingMock = mock<Utbetaling.SimulertUtbetaling>() {
            on { simulering } doReturn mock()
        }
        val vilkårMedAvslag = vilkårsvurderinger.copy(
            uføre = Vilkår.Vurdert.Uførhet.create(
                vurderingsperioder = nonEmptyListOf(
                    vurderingsperiodeUføre.copy(
                        resultat = Resultat.Avslag,
                    ),
                ),
            ),
        )

        val revurdering = opprettetRevurdering.copy(
            vilkårsvurderinger = vilkårMedAvslag,
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurdering
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerOpphør(any(), any(), any()) } doReturn simulertUtbetalingMock.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = NavIdentBruker.Saksbehandler("s1"),
        ).orNull()!!.revurdering

        actual shouldBe beOfType<SimulertRevurdering.Opphørt>()

        inOrder(
            revurderingRepoMock,
            utbetalingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(utbetalingServiceMock).simulerOpphør(
                sakId = argThat { it shouldBe sakId },
                saksbehandler = argThat { it shouldBe NavIdentBruker.Saksbehandler("s1") },
                opphørsdato = argThat { it shouldBe revurdering.periode.fraOgMed },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `uavklarte vilkår kaster exception`() {
        val uavklarteVilkår = vilkårsvurderinger.copy(
            uføre = Vilkår.IkkeVurdert.Uførhet,
        )
        val revurdering = opprettetRevurdering.copy(
            vilkårsvurderinger = uavklarteVilkår,
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurdering
        }

        assertThrows<IllegalStateException> {
            createRevurderingService(
                revurderingRepo = revurderingRepoMock,
            ).beregnOgSimuler(
                revurderingId = revurderingId,
                saksbehandler = NavIdentBruker.Saksbehandler("s1"),
            )
        }
    }

    @Test
    fun `lagreFradrag happy case`() {
        val revurderingId = UUID.randomUUID()

        val revuderingMock = mock<OpprettetRevurdering> {
            on { id } doReturn revurderingId
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revuderingMock
        }

        val grunnlagServiceMock = mock<GrunnlagService> {
            on { lagreFradragsgrunnlag(any(), any()) }.doNothing()
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurderingId,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ).getOrHandle { throw Exception("k") }

        inOrder(
            revurderingRepoMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(
                argThat { it shouldBe revurderingId },
                argThat { it shouldBe request.fradragsrunnlag },
            )
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        }

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `lagreFradrag finner ikke revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurderingId,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ).shouldBeLeft(KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling)

        inOrder(
            revurderingRepoMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        }

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `lagreFradrag har en status som gjør at man ikke kan legge til fradrag`() {
        val revurderingId = UUID.randomUUID()

        val revuderingMock = mock<RevurderingTilAttestering> {
            on { id } doReturn revurderingId
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revuderingMock
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurderingId,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ).shouldBeLeft(KunneIkkeLeggeTilFradragsgrunnlag.UgyldigStatus)

        inOrder(
            revurderingRepoMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        }

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `validerer fradragsgrunnlag`() {
        val revurderingMock = mock<OpprettetRevurdering>() {
            on { periode } doReturn Periode.create(1.mai(2021), 31.desember(2021))
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingMock
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurderingId,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Kontantstøtte,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.juli(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ).shouldBeLeft(KunneIkkeLeggeTilFradragsgrunnlag.FradragsgrunnlagUtenforRevurderingsperiode)
    }
}
