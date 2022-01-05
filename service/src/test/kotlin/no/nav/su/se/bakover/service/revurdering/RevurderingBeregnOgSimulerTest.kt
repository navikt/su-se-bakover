package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nyUtbetalingSimulert
import no.nav.su.se.bakover.test.opphørUtbetalingSimulert
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.utlandsoppholdAvslag
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RevurderingBeregnOgSimulerTest {

    @Test
    fun `beregnOgSimuler - kan beregne og simulere`() {
        val (sak, opprettetRevurdering) = opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                bosituasjongrunnlagEnslig(),
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER
                ),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any(), any(), any()) } doReturn nyUtbetalingSimulert(
                    sakOgBehandling = sak to opprettetRevurdering,
                    beregning = opprettetRevurdering.beregn(
                        eksisterendeUtbetalinger = sak.utbetalinger,
                        clock = fixedClock,
                        avkortingsgrunnlag = emptyList(),
                    ).getOrFail().beregning,
                    clock = fixedClock,
                ).right()
                on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn sak.kopierGjeldendeVedtaksdata(
                    opprettetRevurdering.periode.fraOgMed,
                    fixedClock,
                ).getOrFail().right()
            },
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.revurderingService.beregnOgSimuler(
                revurderingId = opprettetRevurdering.id,
                saksbehandler = saksbehandler,
            ).getOrFail()

            actual.let {
                it.revurdering shouldBe beOfType<SimulertRevurdering.Innvilget>()
                it.feilmeldinger shouldBe emptyList()
            }
            inOrder(
                *serviceAndMocks.all(),
            ) {
                verify(serviceAndMocks.revurderingRepo).hent(any())
                verify(serviceAndMocks.utbetalingService).hentUtbetalinger(sakId)
                verify(serviceAndMocks.vedtakService).kopierGjeldendeVedtaksdata(
                    argThat { it shouldBe sakId },
                    argThat { it shouldBe opprettetRevurdering.periode.fraOgMed },
                )
                verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                    sakId = argThat { it shouldBe sakId },
                    saksbehandler = argThat { it shouldBe saksbehandler },
                    beregning = argThat { it shouldBe (actual.revurdering as SimulertRevurdering).beregning },
                    uføregrunnlag = argThat { it shouldBe opprettetRevurdering.vilkårsvurderinger.uføre.grunnlag },
                )
                verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual.revurdering })
                verify(serviceAndMocks.grunnlagService).lagreFradragsgrunnlag(
                    argThat { it shouldBe opprettetRevurdering.id },
                    argThat { it shouldBe opprettetRevurdering.grunnlagsdata.fradragsgrunnlag },
                )
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `legger ved feilmeldinger for tilfeller som ikke støttes`() {
        val (sak, opprettet) = opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 150500.0),
            ),
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettet
            },
            utbetalingService = mock {
                on { simulerOpphør(any(), any(), any()) } doReturn opphørUtbetalingSimulert(
                    sakOgBehandling = sak to opprettet,
                    opphørsdato = opprettet.periode.fraOgMed,
                    clock = fixedClock,
                ).right()
                on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn sak.kopierGjeldendeVedtaksdata(
                    opprettet.periode.fraOgMed,
                    fixedClock,
                ).getOrFail().right()
            },
        ).let {
            val response = it.revurderingService.beregnOgSimuler(
                revurderingId = opprettet.id,
                saksbehandler = saksbehandler,
            ).getOrFail()

            response.feilmeldinger shouldBe listOf(
                RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
            )
        }
    }

    @Test
    fun `beregnOgSimuler - kan ikke beregne og simulere en revurdering som er til attestering`() {
        val (_, tilAttestring) = revurderingTilAttestering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                )
            )
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn tilAttestring
            },
        ).let {
            val response = it.revurderingService.beregnOgSimuler(
                revurderingId = tilAttestring.id,
                saksbehandler = saksbehandler,
            )
            response shouldBe KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand(
                RevurderingTilAttestering.Innvilget::class,
                SimulertRevurdering::class,
            ).left()

            verify(it.revurderingRepo).hent(tilAttestring.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `beregnOgSimuler - får feil når simulering feiler`() {
        val (sak, beregnet) = beregnetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                )
            )
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn beregnet
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any(), any(), any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
                on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = beregnet.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail().right()
            },
        ).let {
            val response = it.revurderingService.beregnOgSimuler(
                revurderingId = beregnet.id,
                saksbehandler = saksbehandler,
            )

            response shouldBe KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL)
                .left()

            verify(it.utbetalingService).simulerUtbetaling(
                sakId = sakId,
                saksbehandler = saksbehandler,
                beregning = beregnet.beregning,
                uføregrunnlag = beregnet.vilkårsvurderinger.uføre.grunnlag,
            )
        }
    }

    @Test
    fun `beregnOgSimuler - kan beregne og simuler underkjent revurdering på nytt`() {
        val (sak, underkjent) = underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak()

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn underkjent
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any(), any(), any()) } doReturn nyUtbetalingSimulert(
                    sakOgBehandling = sak to underkjent,
                    beregning = underkjent.beregning,
                    clock = fixedClock,
                ).right()
                on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = underkjent.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail().right()
            },
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.revurderingService.beregnOgSimuler(
                underkjent.id,
                saksbehandler,
            ).getOrFail()

            actual.let {
                it.revurdering shouldBe beOfType<SimulertRevurdering.Innvilget>()
                it.feilmeldinger shouldBe emptyList()
            }

            inOrder(
                *serviceAndMocks.all(),
            ) {
                verify(serviceAndMocks.revurderingRepo).hent(argThat { it shouldBe underkjent.id })
                verify(serviceAndMocks.utbetalingService).hentUtbetalinger(sakId)
                verify(serviceAndMocks.vedtakService).kopierGjeldendeVedtaksdata(sakId, underkjent.periode.fraOgMed)
                verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                    sakId = argThat { it shouldBe sakId },
                    saksbehandler = argThat { it shouldBe saksbehandler },
                    beregning = argThat { it shouldBe (actual.revurdering as SimulertRevurdering).beregning },
                    uføregrunnlag = argThat { it shouldBe underkjent.vilkårsvurderinger.uføre.grunnlag },
                )
                verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual.revurdering })
                verify(serviceAndMocks.grunnlagService).lagreFradragsgrunnlag(
                    argThat { it shouldBe underkjent.id },
                    argThat { it shouldBe underkjent.grunnlagsdata.fradragsgrunnlag },
                )
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `uavklarte vilkår kaster exception`() {
        val (sak, opprettet) = opprettetRevurdering(
            vilkårOverrides = listOf(
                Vilkår.Uførhet.IkkeVurdert,
            ),
        )

        assertThrows<IllegalStateException> {
            RevurderingServiceMocks(
                revurderingRepo = mock {
                    on { hent(any()) } doReturn opprettet
                },
                utbetalingService = mock {
                    on { simulerUtbetaling(any(), any(), any(), any()) } doReturn simulertUtbetaling().right()
                    on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
                },
                vedtakService = mock {
                    on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn sak.kopierGjeldendeVedtaksdata(
                        fraOgMed = opprettet.periode.fraOgMed,
                        clock = fixedClock,
                    ).getOrFail().right()
                },
            ).revurderingService.beregnOgSimuler(
                revurderingId = opprettet.id,
                saksbehandler = NavIdentBruker.Saksbehandler("s1"),
            )
        }.also {
            it.message shouldContain "Et vurdert vilkår i revurdering kan ikke være uavklart"
        }
    }

    @Test
    fun `hvis vilkår ikke er oppfylt, fører revurderingen til et opphør`() {
        val (sak, opprettet) = opprettetRevurdering(
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettet
            },
            utbetalingService = mock {
                on { simulerOpphør(any(), any(), any()) } doReturn opphørUtbetalingSimulert(
                    sakOgBehandling = sak to opprettet,
                    opphørsdato = opprettet.periode.fraOgMed,
                    clock = fixedClock,
                ).right()
                on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = opprettet.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail().right()
            },
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.revurderingService.beregnOgSimuler(
                revurderingId = opprettet.id,
                saksbehandler = NavIdentBruker.Saksbehandler("s1"),
            ).getOrFail().revurdering

            actual shouldBe beOfType<SimulertRevurdering.Opphørt>()

            inOrder(
                *serviceAndMocks.all(),
            ) {
                verify(serviceAndMocks.revurderingRepo).hent(opprettet.id)
                verify(serviceAndMocks.utbetalingService).hentUtbetalinger(sakId)
                verify(serviceAndMocks.vedtakService).kopierGjeldendeVedtaksdata(sakId, opprettet.periode.fraOgMed)
                verify(serviceAndMocks.utbetalingService).simulerOpphør(
                    sakId = argThat { it shouldBe sakId },
                    saksbehandler = argThat { it shouldBe NavIdentBruker.Saksbehandler("s1") },
                    opphørsdato = argThat { it shouldBe opprettet.periode.fraOgMed },
                )
                verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual })
                verify(serviceAndMocks.grunnlagService).lagreFradragsgrunnlag(
                    argThat { it shouldBe opprettet.id },
                    argThat { it shouldBe opprettet.grunnlagsdata.fradragsgrunnlag },
                )
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `tar med gjeldende grunnlag for avkorting for revurderingsperioden ved beregning`() {
        /**
         * default beløp satt av [no.nav.su.se.bakover.test.utbetalingslinje]
         * avkorting som følge av revurderingsperiode forut for [no.nav.su.se.bakover.test.nåtidForSimuleringStub] (fører til feilutbetaling)
         * */
        val expectedAvkorting = 2 * 21989

        val tikkendeKlokke = TikkendeKlokke()

        val stønadsperiode1 = stønadsperiode2021
        val (sakEtterInnvilgelse1, innvilget1) = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = saksnummer,
            stønadsperiode = stønadsperiode1,
            clock = tikkendeKlokke,
        )

        val revurderingsperiode1 = Periode.create(1.mai(2021), 31.desember(2021))
        val (sakEtterRevurdering1, revurdering1) = vedtakRevurdering(
            revurderingsperiode = revurderingsperiode1,
            sakOgVedtakSomKanRevurderes = sakEtterInnvilgelse1 to innvilget1,
            vilkårOverrides = listOf(
                utlandsoppholdAvslag(
                    periode = revurderingsperiode1,
                ),
            ),
            clock = tikkendeKlokke,
        )

        val stønadsperiode2 = Stønadsperiode.create(Periode.create(1.juli(2021), 31.desember(2021)), "baluba")
        val (sakEtterInnvilgelse2, innvilget2) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = stønadsperiode2,
            avkorting = (revurdering1 as Vedtak.EndringIYtelse.OpphørtRevurdering).behandling.avkortingsvarsel,
            clock = tikkendeKlokke,
        ).let { (sak, ny) ->
            // legg til ny søknadsbehandling på eksisterende sak
            sakEtterRevurdering1.copy(
                søknadsbehandlinger = sakEtterRevurdering1.søknadsbehandlinger + ny.behandling.copy(),
                vedtakListe = sakEtterRevurdering1.vedtakListe + ny,
                utbetalinger = sakEtterRevurdering1.utbetalinger + sak.utbetalinger,
            ) to ny
        }

        val revurderingsperiode2 = stønadsperiode2.periode
        val (sakEtterRevurdering2, revurdering2) = opprettetRevurdering(
            revurderingsperiode = revurderingsperiode2,
            sakOgVedtakSomKanRevurderes = sakEtterInnvilgelse2 to innvilget2,
            clock = tikkendeKlokke,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurdering2
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any(), any(), any()) } doReturn simulertUtbetaling().right()
                on { hentUtbetalinger(any()) } doReturn sakEtterRevurdering2.utbetalinger
            },
            vedtakService = mock {
                on {
                    kopierGjeldendeVedtaksdata(sakId = any(), fraOgMed = any())
                } doReturn sakEtterRevurdering2.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurderingsperiode2.fraOgMed,
                    clock = tikkendeKlokke,
                ).getOrFail().right()
            },
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.revurderingService.beregnOgSimuler(
                revurderingId = revurdering2.id,
                saksbehandler = saksbehandler,
            ).getOrFail().revurdering

            (actual as BeregnetRevurdering.IngenEndring).let { simulert ->
                simulert.grunnlagsdata.fradragsgrunnlag.filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe expectedAvkorting
                simulert.beregning.getFradrag().filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe expectedAvkorting
            }
        }
    }
}
