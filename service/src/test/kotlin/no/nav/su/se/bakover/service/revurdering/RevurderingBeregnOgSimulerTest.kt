package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class RevurderingBeregnOgSimulerTest {

    @Test
    fun `legger ved feilmeldinger for tilfeller som ikke støttes`() {
        val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = tilRevurdering.periode,
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 10,
            opprettet = fixedTidspunkt,
        )

        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = tilRevurdering.periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = RevurderingTestUtils.revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = listOf(
                    lagFradragsgrunnlag(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 150500.0,
                        periode = tilRevurdering.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = tilRevurdering.periode,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
                Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Avslag,
                            grunnlag = uføregrunnlag,
                            periode = tilRevurdering.periode,
                            begrunnelse = "ok2k",
                        ),
                    ),
                ),
                formue = Vilkår.Formue.IkkeVurdert,
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )

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
                    fraOgMed = tilRevurdering.periode.fraOgMed,
                    tilOgMed = tilRevurdering.periode.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20000,
                    uføregrad = Uføregrad.parse(50),
                ),
            )
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerOpphør(any(), any(), any()) } doReturn simulertUtbetaling.right()
            on { hentUtbetalinger(any()) } doReturn listOf(utbetalingMock)
        }

        val response = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        ).getOrHandle { fail("Skulle returnert en instans av ${RevurderingOgFeilmeldingerResponse::class}") }

        response.feilmeldinger shouldBe listOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        )
    }

    @Test
    fun `beregnOgSimuler - kan beregne og simulere`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = saksnummer,
            stønadsperiode = stønadsperiode2021,
        )

        val grunnlagsdataOgVilkårsvurderinger =
            sak.hentGjeldendeVilkårOgGrunnlag(stønadsperiode2021.periode, fixedClock)

        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            sakOgVedtakSomKanRevurderes = sak to vedtak,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.copy(
                grunnlagsdata = grunnlagsdataEnsligMedFradrag(
                    fradragsgrunnlag = nonEmptyListOf(
                        fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 10000.0),
                    ),
                ),
            ),
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

        val actual = RevurderingTestUtils.createRevurderingService(
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
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.revurdering }, anyOrNull())
        }
        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `beregnOgSimuler - kan ikke beregne og simulere en revurdering som er til attestering`() {
        val revurderingTilAttestering = RevurderingTilAttestering.Innvilget(
            id = revurderingId,
            periode = RevurderingTestUtils.periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            beregning = mock(),
            simulering = mock(),
            oppgaveId = mock(),
            fritekstTilBrev = "",
            revurderingsårsak = RevurderingTestUtils.revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurderingTilAttestering
        }

        val result = RevurderingTestUtils.createRevurderingService(
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

        val actual = RevurderingTestUtils.createRevurderingService(
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
                    fraOgMed = RevurderingTestUtils.periodeNesteMånedOgTreMånederFram.fraOgMed,
                    tilOgMed = RevurderingTestUtils.periodeNesteMånedOgTreMånederFram.tilOgMed,
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

        val revurderingService = RevurderingTestUtils.createRevurderingService(
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
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.revurdering }, anyOrNull())
        }

        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
    }
}
