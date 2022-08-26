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
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.nyUtbetalingSimulert
import no.nav.su.se.bakover.test.opphørUtbetalingSimulert
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.simulertUtbetalingOpphør
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadinnhold
import no.nav.su.se.bakover.test.underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class RevurderingBeregnOgSimulerTest {

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

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                on { simulerOpphør(any()) } doReturn opphørUtbetalingSimulert(
                    sakOgBehandling = sak to opprettet,
                    opphørsdato = opprettet.periode.fraOgMed,
                    clock = fixedClock,
                ).right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        )

        val response = serviceAndMocks.revurderingService.beregnOgSimuler(
            revurderingId = opprettet.id,
            saksbehandler = saksbehandler,
        ).getOrFail()

        response.feilmeldinger shouldBe listOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        )
    }

    @Test
    fun `legger ved varsel dersom beløpsendring er mindre enn 10 prosent av gjeldende utbetaling`() {
        val (sak, revurdering) = opprettetRevurdering()

        RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn nyUtbetalingSimulert(
                    sakOgBehandling = sak to revurdering,
                    beregning = revurdering.beregn(
                        eksisterendeUtbetalinger = sak.utbetalinger,
                        clock = fixedClock,
                        gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                            fraOgMed = revurdering.periode.fraOgMed,
                            clock = fixedClock,
                        ).getOrFail(),
                        satsFactory = satsFactoryTestPåDato(),
                    ).getOrFail().beregning,
                    clock = fixedClock,
                ).right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).let {
            val response = it.revurderingService.beregnOgSimuler(
                revurderingId = revurdering.id,
                saksbehandler = saksbehandler,
            ).getOrFail()

            response.revurdering shouldBe beOfType<SimulertRevurdering.Innvilget>()
            response.feilmeldinger shouldBe emptyList()
            response.varselmeldinger shouldBe listOf(Varselmelding.BeløpsendringUnder10Prosent)
        }
    }

    @Test
    fun `legger ikke ved varsel dersom beløpsendring er mindre enn 10 prosent av gjeldende utbetaling, men opphør pga vilkår`() {
        val (sak, revurdering) = opprettetRevurdering(
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(
                    periode = år(2021),
                ),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                on { simulerOpphør(any()) } doReturn simulertUtbetalingOpphør(
                    opphørsdato = revurdering.periode.fraOgMed,
                    eksisterendeUtbetalinger = sak.utbetalinger,
                ).getOrFail().right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).let {
            val response = it.revurderingService.beregnOgSimuler(
                revurderingId = revurdering.id,
                saksbehandler = saksbehandler,
            ).getOrFail()

            response.revurdering shouldBe beOfType<SimulertRevurdering.Opphørt>()
            response.feilmeldinger shouldBe emptyList()
            response.varselmeldinger shouldBe emptyList()
        }
    }

    @Test
    fun `beregnOgSimuler - kan beregne og simulere`() {
        val (sak, opprettetRevurdering) = opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                bosituasjongrunnlagEnslig(),
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn nyUtbetalingSimulert(
                    sakOgBehandling = sak to opprettetRevurdering,
                    beregning = opprettetRevurdering.beregn(
                        eksisterendeUtbetalinger = sak.utbetalinger,
                        clock = fixedClock,
                        gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                            fraOgMed = opprettetRevurdering.periode.fraOgMed,
                            clock = fixedClock,
                        ).getOrFail(),
                        satsFactory = satsFactoryTestPåDato(),
                    ).getOrFail().beregning,
                    clock = fixedClock,
                ).right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        )
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
            verify(serviceAndMocks.sakService).hentSakForRevurdering(any())
            verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                request = argThat {
                    it shouldBe SimulerUtbetalingRequest.NyUtbetaling.Uføre(
                        sakId = sakId,
                        saksbehandler = saksbehandler,
                        beregning = (actual.revurdering as SimulertRevurdering).beregning,
                        uføregrunnlag = opprettetRevurdering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
                        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                    )
                },
            )
            verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
            verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual.revurdering }, anyOrNull())
            verify(serviceAndMocks.sakService).hentSakForRevurdering(any())
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `beregnOgSimuler - kan ikke beregne og simulere en revurdering som er til attestering`() {
        val (sak, tilAttestring) = revurderingTilAttestering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock(),
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
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

            verify(it.sakService).hentSakForRevurdering(tilAttestring.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `beregnOgSimuler - får feil når simulering feiler`() {
        val (sak, beregnet) = beregnetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).let {
            val response = it.revurderingService.beregnOgSimuler(
                revurderingId = beregnet.id,
                saksbehandler = saksbehandler,
            )

            response shouldBe KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL)
                .left()

            verify(it.utbetalingService).simulerUtbetaling(
                request = argThat {
                    it shouldBe SimulerUtbetalingRequest.NyUtbetaling.Uføre(
                        sakId = sakId,
                        saksbehandler = saksbehandler,
                        beregning = beregnet.beregning,
                        uføregrunnlag = beregnet.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
                        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                    )
                },
            )
        }
    }

    @Test
    fun `beregnOgSimuler - kan beregne og simuler underkjent revurdering på nytt`() {
        val (sak, underkjent) = underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak()

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn underkjent
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn nyUtbetalingSimulert(
                    sakOgBehandling = sak to underkjent,
                    beregning = underkjent.beregning,
                    clock = fixedClock,
                ).right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        )

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
            verify(serviceAndMocks.sakService).hentSakForRevurdering(argThat { it shouldBe underkjent.id })
            verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                request = argThat {
                    it shouldBe SimulerUtbetalingRequest.NyUtbetaling.Uføre(
                        sakId = sakId,
                        saksbehandler = saksbehandler,
                        beregning = (actual.revurdering as SimulertRevurdering).beregning,
                        uføregrunnlag = underkjent.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
                        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                    )
                },
            )
            verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
            verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual.revurdering }, anyOrNull())
            verify(serviceAndMocks.sakService).hentSakForRevurdering(argThat { it shouldBe underkjent.id })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `uavklarte vilkår kaster exception`() {
        val (sak, opprettet) = opprettetRevurdering(
            vilkårOverrides = listOf(
                UføreVilkår.IkkeVurdert,
            ),
        )

        assertThrows<IllegalStateException> {
            RevurderingServiceMocks(
                revurderingRepo = mock(),
                utbetalingService = mock {
                    on { simulerUtbetaling(any()) } doReturn simulertUtbetaling().right()
                },
                sakService = mock {
                    on { hentSakForRevurdering(any()) } doReturn sak
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

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                on { simulerOpphør(any()) } doReturn opphørUtbetalingSimulert(
                    sakOgBehandling = sak to opprettet,
                    opphørsdato = opprettet.periode.fraOgMed,
                    clock = fixedClock,
                ).right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        )
        val actual = serviceAndMocks.revurderingService.beregnOgSimuler(
            revurderingId = opprettet.id,
            saksbehandler = NavIdentBruker.Saksbehandler("s1"),
        ).getOrFail().revurdering

        actual shouldBe beOfType<SimulertRevurdering.Opphørt>()

        inOrder(
            *serviceAndMocks.all(),
        ) {
            verify(serviceAndMocks.sakService).hentSakForRevurdering(opprettet.id)
            verify(serviceAndMocks.utbetalingService).simulerOpphør(
                argThat {
                    it shouldBe SimulerUtbetalingRequest.Opphør(
                        sakId = sakId,
                        saksbehandler = NavIdentBruker.Saksbehandler("s1"),
                        opphørsdato = opprettet.periode.fraOgMed,
                    )
                },
            )
            verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
            verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
            verify(serviceAndMocks.sakService).hentSakForRevurdering(any())
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `tar med gjeldende grunnlag for avkorting for revurderingsperioden ved beregning`() {
        val expectedAvkorting = 2 * 20946

        val tikkendeKlokke = TikkendeKlokke()

        val stønadsperiode1 = stønadsperiode2021
        val (sakEtterInnvilgelse1, _, innvilget1) = iverksattSøknadsbehandlingUføre(
            stønadsperiode = stønadsperiode1,
            clock = tikkendeKlokke,
        )

        innvilget1.harPågåendeAvkorting() shouldBe false
        innvilget1.harIdentifisertBehovForFremtidigAvkorting() shouldBe false

        val revurderingsperiode1 = Periode.create(1.mai(2021), 31.desember(2021))
        val (sakEtterRevurdering1, revurdering1) = vedtakRevurdering(
            revurderingsperiode = revurderingsperiode1,
            sakOgVedtakSomKanRevurderes = sakEtterInnvilgelse1 to innvilget1 as VedtakSomKanRevurderes,
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    periode = revurderingsperiode1,
                ),
            ),
            clock = tikkendeKlokke,
        )

        revurdering1.harPågåendeAvkorting() shouldBe false
        revurdering1.harIdentifisertBehovForFremtidigAvkorting() shouldBe true

        val stønadsperiode2 = Stønadsperiode.create(Periode.create(1.juli(2021), 31.desember(2021)))
        val uteståendeAvkorting =
            (((revurdering1 as VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering).behandling.avkorting) as AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel).avkortingsvarsel

        val (sakEtterInnvilgelse2, _, innvilget2) = iverksattSøknadsbehandlingUføre(
            sakOgSøknad = sakEtterRevurdering1 to nySøknadJournalførtMedOppgave(
                clock = tikkendeKlokke,
                sakId = sakEtterRevurdering1.id,
                søknadInnhold = søknadinnhold(
                    fnr = sakEtterRevurdering1.fnr,
                ),
            ),
            stønadsperiode = stønadsperiode2,
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(uteståendeAvkorting),
            clock = tikkendeKlokke,
        )

        innvilget2.harPågåendeAvkorting() shouldBe true
        innvilget2.harIdentifisertBehovForFremtidigAvkorting() shouldBe false

        val revurderingsperiode2 = stønadsperiode2.periode
        val (sakEtterRevurdering2, revurdering2) = opprettetRevurdering(
            revurderingsperiode = revurderingsperiode2,
            sakOgVedtakSomKanRevurderes = sakEtterInnvilgelse2 to innvilget2 as VedtakSomKanRevurderes,
            clock = tikkendeKlokke,
        )

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn simulertUtbetaling().right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sakEtterRevurdering2
            },
        )

        val actual =
            serviceAndMocks.revurderingService.beregnOgSimuler(revurdering2.id, saksbehandler).getOrFail().revurdering

        (actual as SimulertRevurdering.Innvilget).let { simulert ->
            simulert.grunnlagsdata.fradragsgrunnlag.filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                .sumOf { it.månedsbeløp } shouldBe expectedAvkorting
            simulert.beregning.getFradrag().filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                .sumOf { it.månedsbeløp } shouldBe expectedAvkorting
        }
    }
}
