package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToComparingFieldsExcept
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.fradrag.F
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.sak.SakIdSaksnummerFnr
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.simulertFeilutbetaling
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class ReguleringServiceImplTest {

    @Test
    fun `regulerer alle saker`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
        val reguleringService = lagReguleringServiceImpl(sak)

        reguleringService.startRegulering(1.mai(2021)).size shouldBe 1
    }

    @Nested
    inner class UtledRegulertypeTest {
        private val reguleringService = lagReguleringServiceImpl(
            vedtakSøknadsbehandlingIverksattInnvilget(
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(
                    fradragsgrunnlag = listOf(
                        Grunnlag.Fradragsgrunnlag.create(
                            opprettet = fixedTidspunkt,
                            fradrag = FradragFactory.ny(
                                type = Fradragstype(F.OffentligPensjon),
                                månedsbeløp = 8000.0,
                                periode = periode2021,
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                    ),
                ),
            ).first,
        )

        @Test
        fun `behandlinger som ikke har OffentligPensjon eller NAVytelserTilLivsopphold blir automatisk regulert`() {
            val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
            val reguleringService = lagReguleringServiceImpl(sak)

            val regulering = reguleringService.startRegulering(1.mai(2021)).first().getOrFail()
            regulering.reguleringstype shouldBe Reguleringstype.AUTOMATISK
        }

        @Test
        fun `OffentligPensjon gir manuell`() {
            reguleringService.startRegulering(1.mai(2021)).single()
                .getOrFail().reguleringstype shouldBe Reguleringstype.MANUELL
        }

        @Test
        fun `NAVytelserTilLivsopphold gir manuell`() {
            reguleringService.startRegulering(1.mai(2021)).single()
                .getOrFail().reguleringstype shouldBe Reguleringstype.MANUELL
        }

        @Test
        fun `Stans må håndteres manuelt`() {
            val stansAvYtelse = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().first
            val reguleringService = lagReguleringServiceImpl(stansAvYtelse)

            reguleringService.startRegulering(1.mai(2021)).single()
                .getOrFail().reguleringstype shouldBe Reguleringstype.MANUELL
        }

        @Test
        fun `En periode med hele perioden som opphør må behandles manuelt`() {
            val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(clock = fixedClock)
            val revurdertSak = vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                sakOgVedtakSomKanRevurderes = sakOgVedtak,
                clock = fixedClock.plus(1, ChronoUnit.DAYS),
            ).first

            val reguleringService = lagReguleringServiceImpl(revurdertSak)

            reguleringService.startRegulering(1.mai(2021))
                .first() shouldBe KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(Sak.KunneIkkeOppretteEllerOppdatereRegulering.HelePeriodenErOpphør).left()
        }

        @Test
        fun `en behandling med delvis opphør skal reguleres manuelt`() {
            val revurdertSak =
                vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                    revurderingsperiode = Periode.create(
                        1.september(
                            2021,
                        ),
                        31.desember(2021),
                    ),
                ).first

            val reguleringService = lagReguleringServiceImpl(revurdertSak)

            val regulering = reguleringService.startRegulering(1.mai(2021)).first().getOrFail()
            regulering.reguleringstype shouldBe Reguleringstype.MANUELL
        }

        @Test
        fun `en simulering med feilutbetalinger skal føre til manuell`() {
            val revurdertSak =
                vedtakSøknadsbehandlingIverksattInnvilget(stønadsperiode = Stønadsperiode.create(periode2021)).first

            val reguleringService = lagReguleringServiceImpl(revurdertSak, simulertFeilutbetaling().right())

            reguleringService.startRegulering(1.mai(2021)) shouldBe listOf(
                KunneIkkeOppretteRegulering.KunneIkkeRegulereAutomatisk(
                    KunneIkkeRegulereAutomatisk.KanIkkeAutomatiskRegulereSomFørerTilFeilutbetaling,
                ).left(),
            )
        }
    }

    @Nested
    inner class PeriodeTester {
        @Test
        fun `reguleringen kan ikke starte tidligere enn reguleringsdatoen`() {
            val sak =
                vedtakSøknadsbehandlingIverksattInnvilget(stønadsperiode = Stønadsperiode.create(periode2021)).first
            val reguleringService = lagReguleringServiceImpl(sak)

            val regulering = reguleringService.startRegulering(1.mai(2021)).first().getOrFail()
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

            val regulering = reguleringService.startRegulering(1.mai(2021)).first().getOrFail()
            regulering.periode.fraOgMed shouldBe 1.juni(2021)
        }

        @Test
        fun `oppdaterer reguleringen hvis det finnes en åpen regulering allerede og reguleringsperioden er større`() {
            val sakOgVedtak = innvilgetSøknadsbehandlingMedÅpenRegulering(
                regulerFraOgMed = 1.august(2021),
                /* Manuell regulering */
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(
                    fradragsgrunnlag = listOf(
                        Grunnlag.Fradragsgrunnlag.create(
                            opprettet = fixedTidspunkt,
                            fradrag = FradragFactory.ny(
                                type = Fradragstype(F.OffentligPensjon),
                                månedsbeløp = 8000.0,
                                periode = periode2021,
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                    ),
                ),
            )
            val (sak, regulering) = sakOgVedtak

            val reguleringService = lagReguleringServiceImpl(sak)
            reguleringService.startRegulering(1.mai(2021)).first().getOrFail().let {
                it.shouldBeInstanceOf<Regulering.OpprettetRegulering>()
                it.shouldBeEqualToComparingFieldsExcept(
                    regulering,
                    Regulering.OpprettetRegulering::periode,
                    Regulering.OpprettetRegulering::grunnlagsdataOgVilkårsvurderinger,
                    Regulering.OpprettetRegulering::opprettet,
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
                regulerFraOgMed = 1.januar(2021),
                /* Manuell regulering */
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(
                    fradragsgrunnlag = listOf(
                        Grunnlag.Fradragsgrunnlag.create(
                            opprettet = fixedTidspunkt,
                            fradrag = FradragFactory.ny(
                                type = Fradragstype(F.OffentligPensjon),
                                månedsbeløp = 8000.0,
                                periode = periode2021,
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                    ),
                ),
            )
            val (sak, regulering) = sakOgVedtak

            val reguleringService = lagReguleringServiceImpl(sak)
            reguleringService.startRegulering(1.mai(2021)).first().getOrFail().let {
                it.shouldBeInstanceOf<Regulering.OpprettetRegulering>()
                it.shouldBeEqualToComparingFieldsExcept(
                    regulering,
                    Regulering.OpprettetRegulering::periode,
                    Regulering.OpprettetRegulering::grunnlagsdataOgVilkårsvurderinger,
                    Regulering.OpprettetRegulering::opprettet,
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
        val eventObserverMock: EventObserver = mock()
        lagReguleringServiceImpl(sak).apply {
            addObserver(eventObserverMock)
        }.startRegulering(1.mai(2021))

        verify(eventObserverMock).handle(argThat { it.shouldBeTypeOf<Event.Statistikk.Vedtaksstatistikk>() })
    }

    /**
     * @param scrambleUtbetaling Endrer utbetalingslinjene på saken slik at de ikke lenger matcher gjeldendeVedtaksdata. Da tvinger vi fram en ny beregning som har andre beløp enn tidligere utbetalinger.
     */
    private fun lagReguleringServiceImpl(
        sak: Sak,
        simulerUtbetaling: Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> = simulertUtbetaling().right(),
        scrambleUtbetaling: Boolean = true,
    ): ReguleringServiceImpl {
        val _sak = if (scrambleUtbetaling) sak.copy(
            // Endrer utbetalingene for å trigge behov for regulering (hvis ikke vil vi ikke ha beregningsdiff)
            utbetalinger = listOf(
                oversendtUtbetalingUtenKvittering(
                    beregning = beregning(
                        fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt1000()),
                    ),
                ),
            ),
        ) else sak
        val testData = lagTestdata(_sak)
        val utbetaling = oversendtUtbetalingUtenKvittering(
            beregning = beregning(
                fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt1000()),
            ),
        )

        return ReguleringServiceImpl(
            reguleringRepo = mock {
                on { hentForSakId(any(), any()) } doReturn _sak.reguleringer
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            sakRepo = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn listOf(
                    SakIdSaksnummerFnr(
                        sakId = testData.first.sakId,
                        saksnummer = testData.first.saksnummer,
                        fnr = testData.first.fnr,
                    ),
                )
                on { hentSak(any<UUID>()) } doReturn _sak
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn simulerUtbetaling
                on { verifiserOgSimulerUtbetaling(any()) } doReturn simulertUtbetaling().right()
                on { lagreUtbetaling(any(), any()) } doReturn utbetaling
                on { publiserUtbetaling(any()) } doReturn Utbetalingsrequest("").right()
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn testData.second.right()
            },
            sessionFactory = TestSessionFactory(),
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = fixedClock,
        )
    }

    private fun lagTestdata(
        sak: Sak,
        reguleringsdato: LocalDate = 1.mai(2021),
    ): Triple<SakIdSaksnummerFnr, GjeldendeVedtaksdata, UtbetalingslinjePåTidslinje?> {
        val søknadsbehandling = sak.søknadsbehandlinger.single()

        return Triple(
            SakIdSaksnummerFnr(
                sakId = søknadsbehandling.sakId,
                saksnummer = søknadsbehandling.saksnummer,
                fnr = søknadsbehandling.fnr,
            ),

            sak.kopierGjeldendeVedtaksdata(
                reguleringsdato,
                fixedClock,
            ).getOrFail(),
            sak.utbetalingstidslinje().gjeldendeForDato(reguleringsdato),
        )
    }
}
