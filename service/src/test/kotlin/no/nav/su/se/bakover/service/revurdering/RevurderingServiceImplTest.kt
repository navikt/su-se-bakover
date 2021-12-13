package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.person
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.beregning.TestBeregningSomGirOpphør
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.beregning
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.createRevurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.simulertRevurderingInnvilget
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.avsluttetGjenopptakelseAvYtelseeFraIverksattSøknadsbehandlignsvedtak
import no.nav.su.se.bakover.test.avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksbehandlerNavn
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttUføreOgAndreInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID
import no.nav.su.se.bakover.service.brev.BrevService as BrevService1

internal class RevurderingServiceImplTest {

    @Test
    fun `beregnOgSimuler - kan beregne og simulere`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = saksnummer,
            stønadsperiode = stønadsperiode2021,
        )

        val grunnlagsdataOgVilkårsvurderinger = sak.hentGjeldendeVilkårOgGrunnlag(stønadsperiode2021.periode, fixedClock)

        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            sakOgVedtakSomKanRevurderes = sak to vedtak,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.copy(
                grunnlagsdata = grunnlagsdataEnsligMedFradrag(
                    fradragsgrunnlag = nonEmptyListOf(
                        fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 10000.0)
                    )
                )
            )
        ).second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }
        val simulertUtbetaling = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }
        val utbetalingMock = mock<Utbetaling> {
            on { utbetalingslinjer } doReturn nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = opprettetRevurdering.periode.fraOgMed,
                    tilOgMed = opprettetRevurdering.periode.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20000,
                    uføregrad = Uføregrad.parse(50),
                ),
            )
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any(), any()) } doReturn simulertUtbetaling.right()
            on { hentUtbetalinger(any()) } doReturn listOf(utbetalingMock)
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
            verify(utbetalingServiceMock).hentUtbetalinger(sakId)
            verify(utbetalingServiceMock).simulerUtbetaling(
                sakId = argThat { it shouldBe sakId },
                saksbehandler = argThat { it shouldBe saksbehandler },
                beregning = argThat { it shouldBe (actual.revurdering as SimulertRevurdering).beregning },
                uføregrunnlag = argThat { it shouldBe opprettetRevurdering.vilkårsvurderinger.uføre.grunnlag },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.revurdering })
        }
        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `beregnOgSimuler - kan ikke beregne og simulere en revurdering som er til attestering`() {
        val revurderingTilAttestering = RevurderingTilAttestering.Innvilget(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            beregning = mock(),
            simulering = mock(),
            oppgaveId = mock(),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
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
    fun `beregnOgSimuler - får feil når simulering feiler`() {
        val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second

        val beregnetRevurdering = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        val utbetalingMock = mock<Utbetaling> {
            on { utbetalingslinjer } doReturn nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = tilRevurdering.periode.fraOgMed,
                    tilOgMed = tilRevurdering.periode.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20000,
                    uføregrad = Uføregrad.parse(50),
                ),
            )
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any(), any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
            on { hentUtbetalinger(any()) } doReturn listOf(utbetalingMock)
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn beregnetRevurdering
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        )

        actual shouldBe KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL).left()

        inOrder(revurderingRepoMock) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `iverksett - iverksetter endring av ytelse`() {

        val testsimulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "",
            datoBeregnet = fixedLocalDate,
            nettoBeløp = 0,
            periodeList = listOf(),
        )
        val utbetalingId = UUID30.randomUUID()
        val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second
        val iverksattRevurdering = IverksattRevurdering.Innvilget(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregning,
            simulering = testsimulering,
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(attestant, fixedTidspunkt)),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        val revurderingTilAttestering = RevurderingTilAttestering.Innvilget(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregning,
            simulering = testsimulering,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }

        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.UtenKvittering> {
            on { id } doReturn utbetalingId
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any(), any()) } doReturn utbetalingMock.right()
        }
        val vedtakRepoMock = mock<VedtakRepo>()
        val eventObserver: EventObserver = mock()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
            clock = fixedClock,
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
                uføregrunnlag = argThat { it shouldBe emptyList() },
            )
            verify(utbetalingMock, times(2)).id
            verify(vedtakRepoMock).lagre(
                argThat {
                    it should beOfType<Vedtak.EndringIYtelse.InnvilgetRevurdering>()
                },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe iverksattRevurdering })
            verify(eventObserver, times(2)).handle(any())
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
            utbetalingServiceMock,
            utbetalingMock,
        )
    }

    @Test
    fun `iverksett - iverksetter opphør av ytelse`() {
        val revurderingTilAttestering = RevurderingTilAttestering.Opphørt(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregningSomGirOpphør,
            simulering = mock(),
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkortingsvarsel = Avkortingsvarsel.Ingen,
        )

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
                    it should beOfType<Vedtak.EndringIYtelse.OpphørtRevurdering>()
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
    fun `underkjenn - underkjenner en revurdering`() {
        val tilAttestering = RevurderingTilAttestering.Innvilget(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("oppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
        )

        val attestering = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant(navIdent = "123"),
            grunn = Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
            kommentar = "pls math",
            opprettet = Tidspunkt.EPOCH,
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
    fun `beregnOgSimuler - kan beregne og simuler underkjent revurdering på nytt`() {
        val underkjentRevurdering = underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn underkjentRevurdering
        }

        val simulertUtbetaling = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }
        val utbetalingMock = mock<Utbetaling> {
            on { utbetalingslinjer } doReturn nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                    tilOgMed = periodeNesteMånedOgTreMånederFram.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20000,
                    uføregrad = Uføregrad.parse(50),
                ),
            )
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any(), any()) } doReturn simulertUtbetaling.right()
            on { hentUtbetalinger(any()) } doReturn listOf(utbetalingMock)
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
            verify(utbetalingServiceMock).hentUtbetalinger(sakId)
            verify(utbetalingServiceMock).simulerUtbetaling(
                sakId = argThat { it shouldBe sakId },
                saksbehandler = argThat { it shouldBe saksbehandler },
                beregning = argThat { it shouldBe (actual.revurdering as SimulertRevurdering).beregning },
                uføregrunnlag = argThat { it shouldBe underkjentRevurdering.vilkårsvurderinger.uføre.grunnlag },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.revurdering })
        }

        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `lagBrevutkast - kan lage brev`() {
        val brevPdf = "".toByteArray()

        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn simulertRevurdering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn Dokument.UtenMetadata.Vedtak(
                    opprettet = fixedTidspunkt,
                    tittel = "tittel1",
                    generertDokument = brevPdf,
                    generertDokumentJson = "brev",
                ).right()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            ) shouldBe brevPdf.right()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(argThat { it shouldBe simulertRevurdering })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `lagBrevutkast - får feil når vi ikke kan hente person`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn simulertRevurderingInnvilget
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeHentePerson.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(argThat { it shouldBe simulertRevurderingInnvilget })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `får feil når vi ikke kan hente saksbehandler navn`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn simulertRevurderingInnvilget
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(argThat { it shouldBe simulertRevurderingInnvilget })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `får feil når vi ikke kan lage brev`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn simulertRevurderingInnvilget
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(argThat { it shouldBe simulertRevurderingInnvilget })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `kan ikke lage brev med status opprettet`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            RevurderingServiceMocks(
                revurderingRepo = mock {
                    on { hent(any()) } doReturn opprettetRevurdering
                },
                brevService = mock {
                    on { lagDokument(any()) } doThrow LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(
                        opprettetRevurdering::class,
                    )
                },
            ).let {
                it.revurderingService.lagBrevutkastForRevurdering(
                    revurderingId = revurderingId,
                    fritekst = "",
                )

                verify(it.revurderingRepo).hent(argThat { it shouldBe opprettetRevurdering.id })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `kan ikke lage brev med status beregnet`() {
        val beregnget = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            RevurderingServiceMocks(
                revurderingRepo = mock {
                    on { hent(any()) } doReturn beregnget
                },
                brevService = mock {
                    on { lagDokument(any()) } doThrow LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(beregnget::class)
                }
            ).revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            )
        }
    }

    @Test
    fun `forhåndsvarsler en simulert-revurdering`() {
        val simulertRevurdering = simulertRevurderingInnvilget

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService1> {
            on { lagBrev(any()) } doReturn "pdf".toByteArray().right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn Unit.right()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandlerNavn.right()
        }

        val revurdering = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ).getOrHandle { throw RuntimeException("Her skulle vi ha fått en revurdering") }

        revurdering.forhåndsvarsel shouldBe Forhåndsvarsel.SkalForhåndsvarsles.Sendt

        inOrder(
            revurderingRepoMock,
            personServiceMock,
            brevServiceMock,
            oppgaveServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(brevServiceMock).lagBrev(
                LagBrevRequest.Forhåndsvarsel(
                    person = person,
                    saksbehandlerNavn = saksbehandlerNavn,
                    fritekst = "",
                    dagensDato = fixedLocalDate,
                ),
            )
            verify(revurderingRepoMock).lagre(
                simulertRevurdering.copy(
                    forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
                ),
            )
            verify(brevServiceMock).lagreDokument(
                argThat {
                    it should beOfType<Dokument.MedMetadata.Informasjon>()
                    it.generertDokument shouldBe "pdf".toByteArray()
                    it.metadata shouldBe Dokument.Metadata(
                        sakId = revurdering.sakId,
                        revurderingId = revurdering.id,
                        bestillBrev = true,
                    )
                },
            )
            verify(oppgaveServiceMock).oppdaterOppgave(oppgaveIdRevurdering, "Forhåndsvarsel er sendt.")
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
        val simulertRevurdering = simulertRevurderingInnvilget.copy(
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.AlleredeForhåndsvarslet.left()
    }

    @Test
    fun `forhåndsvarsleEllerSendTilAttestering - kan endre fra ingen forhåndsvarsel til forhåndsvarsel`() {
        val simulertRevurdering = simulertRevurderingInnvilget.copy(
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService1> {
            on { lagBrev(any()) } doReturn "pdf".toByteArray().right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn Unit.right()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        val response = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        )

        response shouldBe simulertRevurdering.copy(
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
        ).right()

        verify(personServiceMock).hentPerson(any())
        verify(brevServiceMock).lagBrev(any())
        verify(revurderingRepoMock).lagre(any())
        verify(brevServiceMock).lagreDokument(any())
        verify(oppgaveServiceMock).oppdaterOppgave(any(), any())
    }

    @Test
    fun `får ikke sendt forhåndsvarsel dersom det ikke er mulig å sende behandlingen videre til attestering`() {
        val simuleringMock = mock<Simulering> {
            on { harFeilutbetalinger() } doReturn true
        }
        val simulertRevurdering = simulertRevurderingInnvilget.copy(
            simulering = simuleringMock,
            forhåndsvarsel = null,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.Attestering(
            KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke,
        ).left()
    }

    private fun testForhåndsvarslerIkkeGittRevurdering(revurdering: Revurdering) {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurdering
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
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
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        testForhåndsvarslerIkkeGittRevurdering(opprettet)

        val beregnet = BeregnetRevurdering.Innvilget(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            beregning = TestBeregning,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
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
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.FantIkkePerson.left()
    }

    @Test
    fun `forhåndsvarsel - generering av dokument feiler`() {
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

        val brevServiceMock = mock<BrevService1> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument.left()

        verify(revurderingRepoMock, never()).lagre(any())
        verify(brevServiceMock, never()).lagreDokument(any())
    }

    @Test
    fun `forhåndsvarsel - oppdatering av oppgave feiler`() {
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

        val brevServiceMock = mock<BrevService1> {
            on { lagBrev(any()) } doReturn "pdf".toByteArray().right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn OppgaveFeil.KunneIkkeOppdatereOppgave.left()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            microsoftGraphApiClient = microsoftGraphApiClientMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.KunneIkkeOppretteOppgave.left()

        verify(brevServiceMock).lagBrev(any())
        verify(revurderingRepoMock).lagre(
            simulertRevurdering.copy(
                forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
            ),
        )
        verify(brevServiceMock).lagreDokument(
            argThat {
                it should beOfType<Dokument.MedMetadata.Informasjon>()
                it.generertDokument shouldBe "pdf".toByteArray()
                it.metadata shouldBe Dokument.Metadata(
                    sakId = simulertRevurdering.sakId,
                    revurderingId = simulertRevurdering.id,
                    bestillBrev = true,
                )
            },
        )
    }

    @Test
    fun `fortsetter etter forhåndsvarsling`() {
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = fixedLocalDate,
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = mock {
                on { resultat } doReturn Vilkårsvurderingsresultat.Innvilget(emptySet())
            },
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturnConsecutively listOf(
                simulertRevurdering,
                simulertRevurdering.copy(
                    forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                        valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                        begrunnelse = "",
                    ),
                ),
            )
            on { hentEventuellTidligereAttestering(any()) } doReturn null
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

        revurdering.map { it.forhåndsvarsel } shouldBe Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
            valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
            begrunnelse = "",
        ).right()
    }

    @Test
    fun `avslutter en revurdering etter valg om hva som skal gjøres etter forhåndsvarsling`() {
        val simulert = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second.copy(
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulert
            doNothing().`when`(mock).oppdaterForhåndsvarsel(any(), any())
        }
        val brevServiceMock = mock<BrevService1> {
            on { lagDokument(any()) } doReturn Dokument.UtenMetadata.Informasjon(
                opprettet = fixedTidspunkt,
                tittel = "tittel1",
                generertDokument = "brev".toByteArray(),
                generertDokumentJson = "brev",
            ).right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }
        val sessionFactoryMock = TestSessionFactory()

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            sessionFactory = sessionFactoryMock,
        )

        val actual = revurderingService.fortsettEtterForhåndsvarsling(
            request = FortsettEtterForhåndsvarslingRequest.AvsluttUtenEndringer(
                revurderingId = simulert.id,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "navn"),
                begrunnelse = "b e g r u n n e l s e",
                fritekstTilBrev = null,
            ),
        )

        val expected = AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulert,
            begrunnelse = "b e g r u n n e l s e",
            fritekst = null,
            tidspunktAvsluttet = fixedTidspunkt,
        ).getOrFail()

        actual shouldBe expected.right()

        verify(revurderingRepoMock, times(2)).hent(argThat { it shouldBe simulert.id })
        verify(revurderingRepoMock).oppdaterForhåndsvarsel(
            argThat { it shouldBe simulert.id },
            argThat {
                it shouldBe Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                    valg = BeslutningEtterForhåndsvarsling.AvsluttUtenEndringer, begrunnelse = "b e g r u n n e l s e",
                )
            },
        )
        verify(oppgaveServiceMock).lukkOppgave(
            argThat { it shouldBe simulert.oppgaveId },
        )
        verify(brevServiceMock).lagDokument(
            argThat {
                it shouldBe expected
            },
        )
        verify(brevServiceMock).lagreDokument(
            argThat {
                it should beOfType<Dokument.MedMetadata.Informasjon>()
                it.generertDokument shouldBe "brev".toByteArray()
                it.metadata shouldBe Dokument.Metadata(
                    sakId = simulert.sakId,
                    revurderingId = simulert.id,
                    bestillBrev = true,
                )
            },
        )
        verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.getOrFail() })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `beslutter ikke en allerede besluttet forhåndsvarsling`() {
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = fixedLocalDate,
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                BeslutningEtterForhåndsvarsling.FortsettMedAndreOpplysninger,
                "",
            ),
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
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
        ) shouldBe FortsettEtterForhåndsvarselFeil.AlleredeBesluttet.left()
    }

    @Test
    fun `fortsettEtterForhåndsvarsling - beslutter ikke en allerede besluttet forhåndsvarsling ingenEndring`() {
        val simulertRevurdering = simulertRevurderingInnvilget
            .copy(forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel)

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
        ) shouldBe FortsettEtterForhåndsvarselFeil.AlleredeBesluttet.left()
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
        ) shouldBe KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering.left()
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
        ) shouldBe KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson.left()
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

        val brevServiceMock = mock<BrevService1> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }

        val microsoftGraphApiClientMock = mock<MicrosoftGraphApiOppslag> {
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
        ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast.left()
    }

    @Test
    fun `hvis vilkår ikke er oppfylt, fører revurderingen til et opphør`() {
        val simulertUtbetalingMock = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }

        val revurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger(
                grunnlagsdata = grunnlagsdataEnsligMedFradrag(),
                vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(),
            ),
        ).second
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurdering
        }
        val utbetalingMock = mock<Utbetaling> {
            on { utbetalingslinjer } doReturn nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                    tilOgMed = periodeNesteMånedOgTreMånederFram.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20000,
                    uføregrad = Uføregrad.parse(50),
                ),
            )
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerOpphør(any(), any(), any()) } doReturn simulertUtbetalingMock.right()
            on { hentUtbetalinger(any()) } doReturn listOf(utbetalingMock)
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
            verify(utbetalingServiceMock).hentUtbetalinger(sakId)
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
        val revurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.IkkeVurdert,
        ).second
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurdering
        }
        val utbetalingMock = mock<Utbetaling> {
            on { utbetalingslinjer } doReturn nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                    tilOgMed = periodeNesteMånedOgTreMånederFram.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20000,
                    uføregrad = Uføregrad.parse(50),
                ),
            )
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { hentUtbetalinger(any()) } doReturn listOf(utbetalingMock)
        }

        assertThrows<IllegalStateException> {
            createRevurderingService(
                revurderingRepo = revurderingRepoMock,
                utbetalingService = utbetalingServiceMock,
            ).beregnOgSimuler(
                revurderingId = revurderingId,
                saksbehandler = NavIdentBruker.Saksbehandler("s1"),
            )
        }
    }

    @Test
    fun `lagreFradrag happy case`() {
        val revurderingsperiode = Periode.create(1.januar(2021), 31.desember(2021))
        val eksisterendeRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn eksisterendeRevurdering
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = eksisterendeRevurdering.id,
            fradragsgrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 0.0,
                    periode = revurderingsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val actual = revurderingService.leggTilFradragsgrunnlag(
            request,
        ).getOrHandle { fail("Feilet med $it") }

        inOrder(
            revurderingRepoMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe eksisterendeRevurdering.id })

            verify(grunnlagServiceMock).lagreFradragsgrunnlag(
                argThat { it shouldBe eksisterendeRevurdering.id },
                argThat { it shouldBe request.fradragsgrunnlag },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.revurdering })
        }

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `leggTilFradragsgrunnlag - lagreFradrag finner ikke revurdering`() {
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
            fradragsgrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 0.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        inOrder(
            revurderingRepoMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        }

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `leggTilFradragsgrunnlag - lagreFradrag har en status som gjør at man ikke kan legge til fradrag`() {
        val eksisterendeRevurdering = tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn eksisterendeRevurdering
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = eksisterendeRevurdering.id,
            fradragsgrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 0.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe
            KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                fra = eksisterendeRevurdering::class,
                til = OpprettetRevurdering::class,
            ).left()

        inOrder(
            revurderingRepoMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe eksisterendeRevurdering.id })
        }

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `validerer fradragsgrunnlag`() {
        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = revurderingsperiode,
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger(
                vilkårsvurderinger = vilkårsvurderingerInnvilget(periode = revurderingsperiode),
                grunnlagsdata = Grunnlagsdata.create(
                    bosituasjon = listOf(
                        Grunnlag.Bosituasjon.Fullstendig.Enslig(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = revurderingsperiode,
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.second
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurderingId,
            fradragsgrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 0.0,
                    periode = revurderingsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.Kontantstøtte,
                    månedsbeløp = 0.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.juli(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(KunneIkkeLageGrunnlagsdata.FradragManglerBosituasjon)
            .left()
    }

    @Test
    fun `avslutter en revurdering som ikke skal bli forhåndsvarslet`() {
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = opprettetRevurdering.id,
            begrunnelse = "opprettet revurderingen med en feil",
            fritekst = null,
        )

        actual shouldBe AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = opprettetRevurdering,
            begrunnelse = "opprettet revurderingen med en feil",
            fritekst = null,
            tidspunktAvsluttet = fixedTidspunkt,
        )

        verify(revurderingRepoMock).hent(argThat { it shouldBe opprettetRevurdering.id })
        verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe opprettetRevurdering.oppgaveId })
        verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.getOrFail() })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `avslutter en revurdering som er blitt forhåndsvarslet (med fritekst)`() {
        val simulert = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second.copy(
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulert
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }
        val brevServiceMock = mock<BrevService1> {
            on { lagDokument(any()) } doReturn Dokument.UtenMetadata.Informasjon(
                opprettet = fixedTidspunkt,
                tittel = "tittel1",
                generertDokument = "brev".toByteArray(),
                generertDokumentJson = "brev",
            ).right()
        }
        val sessionFactoryMock = TestSessionFactory()

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            sessionFactory = sessionFactoryMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = simulert.id,
            begrunnelse = "opprettet revurderingen med en feil",
            fritekst = "en fri tekst",
        )

        actual shouldBe AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulert,
            begrunnelse = "opprettet revurderingen med en feil",
            fritekst = "en fri tekst",
            tidspunktAvsluttet = fixedTidspunkt,
        )

        verify(revurderingRepoMock).hent(argThat { it shouldBe simulert.id })
        verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe simulert.oppgaveId })
        verify(brevServiceMock).lagreDokument(
            argThat {
                it should beOfType<Dokument.MedMetadata.Informasjon>()
                it.generertDokument shouldBe "brev".toByteArray()
                it.metadata shouldBe Dokument.Metadata(
                    sakId = simulert.sakId,
                    revurderingId = simulert.id,
                    bestillBrev = true,
                )
            },
        )
        verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.getOrFail() })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en revurdering man ikke finner`() {
        val id = UUID.randomUUID()
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        revurderingService.avsluttRevurdering(
            revurderingId = id,
            begrunnelse = "hehe",
            fritekst = null,
        ) shouldBe KunneIkkeAvslutteRevurdering.FantIkkeRevurdering.left()

        verify(revurderingRepoMock).hent(argThat { it shouldBe id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom generering av brev feiler når man avslutter revurdering`() {
        val simulert = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second.copy(
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulert
        }
        val brevServiceMock = mock<BrevService1> {
            on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
        )

        revurderingService.avsluttRevurdering(
            revurderingId = simulert.id,
            begrunnelse = "hehe",
            fritekst = null,
        ) shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageDokument.left()

        verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe simulert.oppgaveId })
        verify(revurderingRepoMock).hent(argThat { it shouldBe simulert.id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `avslutter en stansAvYtelse-revurdering`() {
        val stansAvYtelse = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn stansAvYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = stansAvYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            fritekst = null,
        )

        actual shouldBe StansAvYtelseRevurdering.AvsluttetStansAvYtelse.tryCreate(
            stansAvYtelseRevurdering = stansAvYtelse,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            tidspunktAvsluttet = fixedTidspunkt,
        )

        verify(revurderingRepoMock).hent(argThat { it shouldBe stansAvYtelse.id })
        verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.getOrFail() })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en stansAvYtelse-revurdering som allerede er avsluttet`() {
        val avsluttetStansAvYtelse = avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak().second
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn avsluttetStansAvYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = avsluttetStansAvYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            fritekst = null,
        )

        actual shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse(StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse.RevurderingErAlleredeAvsluttet)
            .left()

        verify(revurderingRepoMock).hent(argThat { it shouldBe avsluttetStansAvYtelse.id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en stansAvYtelse-revurdering som er iverksatt`() {
        val iverksattStansAvYtelse = iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn iverksattStansAvYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = iverksattStansAvYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            fritekst = null,
        )

        actual shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse(StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse.RevurderingenErIverksatt)
            .left()

        verify(revurderingRepoMock).hent(argThat { it shouldBe iverksattStansAvYtelse.id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `avslutter en gjenoppta-revurdering`() {
        val gjenopptaYtelse = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn gjenopptaYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = gjenopptaYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            fritekst = null,
        )

        actual shouldBe GjenopptaYtelseRevurdering.AvsluttetGjenoppta.tryCreate(
            gjenopptakAvYtelseRevurdering = gjenopptaYtelse,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            tidspunktAvsluttet = fixedTidspunkt,
        )

        verify(revurderingRepoMock).hent(argThat { it shouldBe gjenopptaYtelse.id })
        verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.getOrFail() })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en gjenoppta-revurdering som er avlusttet`() {
        val gjenopptaYtelse = avsluttetGjenopptakelseAvYtelseeFraIverksattSøknadsbehandlignsvedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn gjenopptaYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = gjenopptaYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            fritekst = null,
        )

        actual shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse(GjenopptaYtelseRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingErAlleredeAvsluttet)
            .left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe gjenopptaYtelse.id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en gjenoppta-revurdering som er iverksatt`() {
        val gjenopptaYtelse = iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn gjenopptaYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = gjenopptaYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            fritekst = null,
        )

        actual shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse(GjenopptaYtelseRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingenErIverksatt)
            .left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe gjenopptaYtelse.id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }
}
