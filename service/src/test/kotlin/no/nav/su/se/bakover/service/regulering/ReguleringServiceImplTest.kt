package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.equality.FieldsEqualityCheckConfig
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.regulering.KunneIkkeFerdigstilleOgIverksette
import no.nav.su.se.bakover.domain.regulering.KunneIkkeOppretteRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt0
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.stansetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.UUID

// TODO refaktorer disse testene til å unngå "scrambling" og hacking, og i større grad bruke satsfactory til å trigge endringer.
internal class ReguleringServiceImplTest {

    val periodeMaiDes = Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.desember(2021))

    @Test
    fun `regulerer alle saker`() {
        val clock = tikkendeFixedClock()
        val sak = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
        ).first
        val reguleringService = lagReguleringServiceImpl(
            sak = sak,
            clock = clock,
        )

        reguleringService.startAutomatiskRegulering(mai(2021)).size shouldBe 1
    }

    @Nested
    inner class UtledRegulertypeTest {
        private val reguleringService = lagReguleringServiceImpl(
            vedtakSøknadsbehandlingIverksattInnvilget(
                customGrunnlag = grunnlagsdataEnsligUtenFradrag(
                    fradragsgrunnlag = listOf(
                        Grunnlag.Fradragsgrunnlag.create(
                            opprettet = fixedTidspunkt,
                            fradrag = FradragFactory.nyFradragsperiode(
                                fradragstype = Fradragstype.OffentligPensjon,
                                månedsbeløp = 8000.0,
                                periode = år(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                    ),
                ).let { listOf(it.bosituasjon, it.fradragsgrunnlag) }.flatten(),
            ).first,
        )

        @Test
        fun `behandlinger som ikke har OffentligPensjon eller NAVytelserTilLivsopphold blir automatisk regulert`() {
            val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
            val reguleringService = lagReguleringServiceImpl(sak)

            val regulering = reguleringService.startAutomatiskRegulering(mai(2021)).first().getOrFail()
            regulering.reguleringstype shouldBe Reguleringstype.AUTOMATISK
        }

        @Test
        fun `OffentligPensjon gir manuell`() {
            reguleringService.startAutomatiskRegulering(mai(2021)).single()
                .getOrFail().reguleringstype shouldBe Reguleringstype.MANUELL(
                setOf(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt),
            )
        }

        @Test
        fun `NAVytelserTilLivsopphold gir manuell`() {
            reguleringService.startAutomatiskRegulering(mai(2021)).single()
                .getOrFail().reguleringstype shouldBe Reguleringstype.MANUELL(
                setOf(ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt),
            )
        }

        @Test
        fun `Stans må håndteres manuelt`() {
            val stansAvYtelse = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().first
            val reguleringService = lagReguleringServiceImpl(stansAvYtelse)

            reguleringService.startAutomatiskRegulering(mai(2021)).single()
                .getOrFail().reguleringstype shouldBe Reguleringstype.MANUELL(
                setOf(ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset),
            )
        }

        @Test
        fun `En periode med hele perioden som opphør må behandles manuelt`() {
            val clock = TikkendeKlokke(fixedClock)
            val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(clock = clock)
            val revurdertSak = vedtakRevurdering(
                clock = clock,
                sakOgVedtakSomKanRevurderes = sakOgVedtak,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            ).first

            val reguleringService = lagReguleringServiceImpl(sak = revurdertSak, clock = clock)

            reguleringService.startAutomatiskRegulering(mai(2021))
                .first() shouldBe KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode)
                .left()
        }

        @Test
        fun `en behandling med delvis opphør i slutten av perioden skal reguleres automatisk`() {
            val revurdertSak = vedtakRevurdering(
                revurderingsperiode = Periode.create(1.september(2021), 31.desember(2021)),
                vilkårOverrides = listOf(
                    avslåttUførevilkårUtenGrunnlag(
                        periode = Periode.create(
                            1.september(2021),
                            31.desember(2021),
                        ),
                    ),
                ),
            ).first

            val reguleringService = lagReguleringServiceImpl(revurdertSak)

            val regulering = reguleringService.startAutomatiskRegulering(mai(2021)).first().getOrFail()
            regulering.reguleringstype shouldBe Reguleringstype.AUTOMATISK
            regulering.periode shouldBe Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.august(2021))
        }

        @Test
        fun `en behandling med delvis opphør i starten av perioden skal reguleres automatisk`() {
            val clock = TikkendeKlokke()
            val (sakEtterFørsteRevudering, vedtak) = vedtakRevurdering(
                clock = clock,
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                vilkårOverrides = listOf(
                    avslåttUførevilkårUtenGrunnlag(
                        periode = Periode.create(
                            1.mai(2021),
                            31.desember(2021),
                        ),
                    ),
                ),
            )

            val sak = vedtakRevurdering(
                clock = clock,
                revurderingsperiode = Periode.create(1.juni(2021), tilOgMed = 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterFørsteRevudering to vedtak,
                vilkårOverrides = listOf(
                    innvilgetUførevilkår(
                        periode = Periode.create(
                            1.juni(2021),
                            31.desember(2021),
                        ),
                    ),
                ),
            ).first
            val reguleringService = lagReguleringServiceImpl(sak)

            val regulering = reguleringService.startAutomatiskRegulering(mai(2021)).first().getOrFail()
            regulering.reguleringstype shouldBe Reguleringstype.AUTOMATISK
            regulering.periode shouldBe Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021))
        }

        @Test
        fun `en behandling med delvis opphør i midten av perioden skal ikke støttes`() {
            val clock = TikkendeKlokke()
            val (sakEtterFørsteRevudering, vedtak) = vedtakRevurdering(
                clock = clock,
                revurderingsperiode = Periode.create(1.juni(2021), 31.desember(2021)),
                vilkårOverrides = listOf(
                    avslåttUførevilkårUtenGrunnlag(
                        periode = Periode.create(
                            1.juni(2021),
                            31.desember(2021),
                        ),
                    ),
                ),
            )

            val sak = vedtakRevurdering(
                clock = clock,
                revurderingsperiode = Periode.create(1.august(2021), tilOgMed = 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterFørsteRevudering to vedtak,
                vilkårOverrides = listOf(
                    innvilgetUførevilkår(
                        periode = Periode.create(
                            1.august(2021),
                            tilOgMed = 31.desember(2021),
                        ),
                    ),
                ),
            ).first
            val reguleringService = lagReguleringServiceImpl(sak)

            reguleringService.startAutomatiskRegulering(mai(2021))
                .first() shouldBe KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(feil = Sak.KunneIkkeOppretteEllerOppdatereRegulering.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig)
                .left()
        }

        private fun offentligPensjonGrunnlag(beløp: Double, periode: Periode) =
            lagFradragsgrunnlag(
                id = UUID.randomUUID(),
                type = Fradragstype.OffentligPensjon,
                månedsbeløp = beløp,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )

        @Test
        fun `manuell behandling happy case`() {
            val (sak, regulering) = innvilgetSøknadsbehandlingMedÅpenRegulering(
                regulerFraOgMed = mai(2021),
                customGrunnlag = grunnlagsdataEnsligUtenFradrag(
                    periode = stønadsperiode2021.periode,
                    fradragsgrunnlag = listOf(
                        offentligPensjonGrunnlag(8000.0, år(2021)),
                    ),
                    bosituasjon = nonEmptyListOf(
                        bosituasjongrunnlagEnslig(
                            periode = stønadsperiode2021.periode,
                        ),
                    ),
                ).let { listOf(it.bosituasjon, it.fradragsgrunnlag) }.flatten(),
            )

            val reguleringService = lagReguleringServiceImpl(sak)

            val iverksattRegulering = reguleringService.regulerManuelt(
                reguleringId = regulering.id,
                uføregrunnlag = listOf(uføregrunnlagForventetInntekt0(periode = periodeMaiDes)),
                fradrag = listOf(offentligPensjonGrunnlag(5000.0, periodeMaiDes)),
                saksbehandler = saksbehandler,
            ).getOrFail()

            iverksattRegulering.beregning.getFradrag() shouldBe listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.OffentligPensjon,
                    månedsbeløp = 5000.0,
                    periode = periodeMaiDes,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periodeMaiDes,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            )
        }

        @Test
        fun `manuell behandling av stans skal ikke være lov`() {
            val tikkendeKlokke = TikkendeKlokke(fixedClock)
            val (sak, regulering) = stansetSøknadsbehandlingMedÅpenRegulering(
                regulerFraOgMed = mai(2021),
                clock = tikkendeKlokke,
            )

            val reguleringService = lagReguleringServiceImpl(sak)

            reguleringService.regulerManuelt(
                reguleringId = regulering.id,
                uføregrunnlag = listOf(uføregrunnlagForventetInntekt0(periode = periodeMaiDes)),
                fradrag = listOf(offentligPensjonGrunnlag(8100.0, periodeMaiDes)),
                saksbehandler = saksbehandler,
            ) shouldBe KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres.left()
        }

        @Test
        fun `en simulering med feilutbetalinger skal føre til manuell`() {
            val revurdertSak = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(år(2021)),
            ).first

            val reguleringService = lagReguleringServiceImpl(revurdertSak, lagFeilutbetaling = true)

            reguleringService.startAutomatiskRegulering(mai(2021)) shouldBe listOf(
                KunneIkkeOppretteRegulering.KunneIkkeRegulereAutomatisk(
                    KunneIkkeFerdigstilleOgIverksette.KanIkkeAutomatiskRegulereSomFørerTilFeilutbetaling,
                ).left(),
            )
        }
    }

    @Nested
    inner class PeriodeTester {

        @Test
        fun `skal kunne regulere selv om reguleringsdato er langt etter vedtaksperioden`() {
            val sak = vedtakSøknadsbehandlingIverksattInnvilget(stønadsperiode = Stønadsperiode.create(år(2021))).first
            val reguleringService = lagReguleringServiceImpl(sak)

            reguleringService.startAutomatiskRegulering(mai(2023)).let {
                it.size shouldBe 1
                it.first() shouldBe KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(feil = Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode)
                    .left()
            }
        }

        @Test
        fun `reguleringen kan ikke starte tidligere enn reguleringsdatoen`() {
            val sak = vedtakSøknadsbehandlingIverksattInnvilget(stønadsperiode = Stønadsperiode.create(år(2021))).first
            val reguleringService = lagReguleringServiceImpl(sak)

            val regulering = reguleringService.startAutomatiskRegulering(mai(2021)).first().getOrFail()
            regulering.periode.fraOgMed shouldBe 1.mai(2021)
        }

        @Test
        fun `reguleringen starter fra søknadsbehandlingens dato hvis den er etter reguleringsdatoen`() {
            val periodeEtterReguleringsdato = Periode.create(1.juni(2021), 31.desember(2021))
            val sak = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periodeEtterReguleringsdato,
                ),
            ).first
            val reguleringService = lagReguleringServiceImpl(sak)

            val regulering = reguleringService.startAutomatiskRegulering(mai(2021)).first().getOrFail()
            regulering.periode.fraOgMed shouldBe 1.juni(2021)
        }

        @Test
        fun `oppdaterer reguleringen hvis det finnes en åpen regulering allerede og reguleringsperioden er større`() {
            val sakOgVedtak = innvilgetSøknadsbehandlingMedÅpenRegulering(
                regulerFraOgMed = august(2021),
                /* Manuell regulering */
                customGrunnlag = grunnlagsdataEnsligUtenFradrag(
                    fradragsgrunnlag = listOf(
                        Grunnlag.Fradragsgrunnlag.create(
                            opprettet = fixedTidspunkt,
                            fradrag = FradragFactory.nyFradragsperiode(
                                fradragstype = Fradragstype.OffentligPensjon,
                                månedsbeløp = 8000.0,
                                periode = år(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                    ),
                ).let { listOf(it.bosituasjon, it.fradragsgrunnlag) }.flatten(),
            )
            val (sak, regulering) = sakOgVedtak

            val reguleringService = lagReguleringServiceImpl(sak)
            reguleringService.startAutomatiskRegulering(mai(2021)).first().getOrFail().let {
                it.shouldBeInstanceOf<OpprettetRegulering>()
                shouldBeEqualToComparingFields(
                    regulering,
                    FieldsEqualityCheckConfig(
                        propertiesToExclude = listOf(
                            OpprettetRegulering::grunnlagsdataOgVilkårsvurderinger,
                            OpprettetRegulering::opprettet,
                        ),
                    ),
                )

                it.periode.fraOgMed shouldBe 1.mai(2021)
                it.periode.tilOgMed shouldBe regulering.periode.tilOgMed

                it.grunnlagsdataOgVilkårsvurderinger.periode()?.fraOgMed shouldBe 1.mai(2021)
                it.grunnlagsdataOgVilkårsvurderinger.periode()?.tilOgMed shouldBe regulering.periode.tilOgMed

                it.opprettet shouldBe regulering.opprettet
            }
        }

        @Test
        fun `oppdaterer ikke reguleringen hvis det finnes en åpen regulering men reguleringsperioden er mindre`() {
            val sakOgVedtak = innvilgetSøknadsbehandlingMedÅpenRegulering(
                regulerFraOgMed = januar(2021),
                /* Manuell regulering */
                customGrunnlag = grunnlagsdataEnsligUtenFradrag(
                    fradragsgrunnlag = listOf(
                        Grunnlag.Fradragsgrunnlag.create(
                            opprettet = fixedTidspunkt,
                            fradrag = FradragFactory.nyFradragsperiode(
                                fradragstype = Fradragstype.OffentligPensjon,
                                månedsbeløp = 8000.0,
                                periode = år(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                    ),
                ).let { listOf(it.bosituasjon, it.fradragsgrunnlag) }.flatten(),
            )
            val (sak, regulering) = sakOgVedtak

            val reguleringService = lagReguleringServiceImpl(sak)
            reguleringService.startAutomatiskRegulering(mai(2021)).first().getOrFail().let {
                it.shouldBeInstanceOf<OpprettetRegulering>()
                it.shouldBeEqualToComparingFields(
                    regulering,
                    FieldsEqualityCheckConfig(
                        propertiesToExclude = listOf(
                            OpprettetRegulering::periode,
                            OpprettetRegulering::grunnlagsdataOgVilkårsvurderinger,
                            OpprettetRegulering::opprettet,
                        ),
                    ),
                )

                it.periode.fraOgMed shouldBe regulering.periode.fraOgMed
                it.periode.tilOgMed shouldBe regulering.periode.tilOgMed

                it.grunnlagsdataOgVilkårsvurderinger.periode()?.fraOgMed shouldBe regulering.periode.fraOgMed
                it.grunnlagsdataOgVilkårsvurderinger.periode()?.tilOgMed shouldBe regulering.periode.tilOgMed

                it.opprettet shouldBe regulering.opprettet
            }
        }
    }

    @Test
    fun `iverksatte reguleringer sender statistikk`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
        val eventObserverMock: StatistikkEventObserver = mock()
        lagReguleringServiceImpl(sak).apply {
            addObserver(eventObserverMock)
        }.startAutomatiskRegulering(mai(2021))

        verify(eventObserverMock).handle(argThat { it.shouldBeTypeOf<StatistikkEvent.Stønadsvedtak>() })
    }

    @Test
    fun `gjør ingen sideeffekter ved dry run`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget().first

        val reguleringMock = mock<ReguleringRepo> {}
        val sakRepo = mock<SakRepo> {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val utbetalingMock = mock<UtbetalingService> {}
        val vedtakMock = mock<VedtakService> {}
        val sessionMock = mock<SessionFactory> {}
        val tilbakekrevingMock = mock<TilbakekrevingService> {}

        ReguleringServiceImpl(
            reguleringRepo = reguleringMock,
            sakRepo = sakRepo,
            utbetalingService = utbetalingMock,
            vedtakService = vedtakMock,
            sessionFactory = sessionMock,
            tilbakekrevingService = tilbakekrevingMock,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).startAutomatiskRegulering(mai(2022))

        verifyNoInteractions(reguleringMock)
        verifyNoInteractions(utbetalingMock)
        verifyNoInteractions(vedtakMock)
        verifyNoInteractions(sessionMock)
        verifyNoInteractions(tilbakekrevingMock)
    }

    /**
     * @param scrambleUtbetaling Endrer utbetalingslinjene på saken slik at de ikke lenger matcher gjeldendeVedtaksdata. Da tvinger vi fram en ny beregning som har andre beløp enn tidligere utbetalinger.
     */
    private fun lagReguleringServiceImpl(
        sak: Sak,
        lagFeilutbetaling: Boolean = false,
        scrambleUtbetaling: Boolean = true,
        clock: Clock = tikkendeFixedClock(),
    ): ReguleringServiceImpl {
        val _sak = if (scrambleUtbetaling) {
            sak.copy(
                // Endrer utbetalingene for å trigge behov for regulering (hvis ikke vil vi ikke ha beregningsdiff)
                utbetalinger = Utbetalinger(
                    oversendtUtbetalingMedKvittering(
                        beregning = beregning(
                            fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt1000()),
                        ),
                        clock = clock,
                    ),
                ),
                // hack det til og snik inn masse fradrag i grunnlaget til saken slik at vi  får fremprovisert en feilutbetaling ved simulering
                vedtakListe = if (lagFeilutbetaling) {
                    listOf(
                        (sak.vedtakListe.first() as VedtakInnvilgetSøknadsbehandling).let { vedtak ->
                            vedtak.copy(
                                behandling = vedtak.behandling.let {
                                    it.copy(
                                        grunnlagsdataOgVilkårsvurderinger = it.grunnlagsdataOgVilkårsvurderinger.oppdaterFradragsgrunnlag(
                                            fradragsgrunnlag = listOf(
                                                fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 10000.0),
                                            ),
                                        ),
                                    )
                                },
                            )
                        },
                    )
                } else {
                    sak.vedtakListe
                },
            )
        } else {
            sak
        }

        print(lagFeilutbetaling)

        val nyUtbetaling = UtbetalingKlargjortForOversendelse(
            utbetaling = oversendtUtbetalingUtenKvittering(
                beregning = beregning(
                    fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt1000()),
                ),
                clock = clock,
            ),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            },
        )

        return ReguleringServiceImpl(
            reguleringRepo = mock {
                on { hent(any()) } doReturn _sak.reguleringer.firstOrNull()
                on { hentForSakId(any(), any()) } doReturn _sak.reguleringer
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            sakRepo = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn listOf(_sak.info())
                on { hentSak(any<UUID>()) } doReturn _sak
            },
            utbetalingService = mock { service ->
                doAnswer { invocation ->
                    simulerUtbetaling(
                        sak = _sak,
                        utbetaling = (invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering),
                    )
                }.whenever(service).simulerUtbetaling(any(), any())
                on { klargjørUtbetaling(any(), any()) } doReturn nyUtbetaling.right()
            },
            vedtakService = mock(),
            sessionFactory = TestSessionFactory(),
            clock = clock,
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            satsFactory = satsFactoryTestPåDato(),
        )
    }
}
