package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.DryRunNyttGrunnbeløp
import no.nav.su.se.bakover.domain.regulering.EksterntBeløpSomFradragstype
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.KunneIkkeBehandleRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.RegulertBeløp
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import satser.domain.supplerendestønad.grunnbeløpsendringer
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.Utbetalingsrequest
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

internal class ReguleringAutomatiskServiceImplTest {

    @Test
    fun `regulerer alle saker`() {
        val clock = tikkendeFixedClock()
        val sak = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
        ).first
        val reguleringService = lagReguleringAutomatiskServiceImpl(
            sak = sak,
            clock = clock,
        )

        reguleringService.startAutomatiskRegulering(mai(2021)).let {
            it.size shouldBe 1
            it.first().shouldBeRight()
        }
    }

    @Test
    fun `henter eksterne reguleringer i batcher på maks 50 saker`() {
        val antallSaker = 101
        val clock = tikkendeFixedClock()
        val sak = vedtakSøknadsbehandlingIverksattInnvilget(clock = clock).first

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

        val sakerPerId = (1..antallSaker).associate { _ ->
            val sakId = UUID.randomUUID()
            sakId to sak.copy(
                id = sakId,
                fnr = Fnr.generer(),
            )
        }
        val alleSaker = sakerPerId.values.map { it.info() }

        val reguleringRepo = mock<ReguleringRepo> {
            on { hent(any()) } doReturn sak.reguleringer.firstOrNull()
            alleSaker.forEach {
                val sak = sakerPerId[it.sakId]!!
                on { hentForSakId(it.sakId) } doReturn sak.reguleringer
            }
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
        }
        val reguleringKjøringRepo = mock<ReguleringKjøringRepo>()
        val utbetalingService = mock<UtbetalingService> {
            on { simulerUtbetaling(any()) } doAnswer { invocation ->
                simulerUtbetaling(
                    utbetalingerPåSak = sak.utbetalinger,
                    utbetalingForSimulering = (invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering),
                )
            }
            on { klargjørUtbetaling(any(), any()) } doReturn nyUtbetaling.right()
        }
        val vedtakService = mock<VedtakService>()
        val vedtakRepo = mock<VedtakRepo> {
            on { hentVedtakSomKanRevurderesForSak(any()) } doReturn sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
        }
        val sessionFactory = TestSessionFactory()
        val satsFactory = satsFactoryTestPåDato()
        val reguleringService = ReguleringServiceImpl(
            reguleringRepo = reguleringRepo,
            utbetalingService = utbetalingService,
            vedtakService = vedtakService,
            sessionFactory = sessionFactory,
            satsFactory = satsFactory,
            clock = clock,
        )

        val sakService = mock<SakService> {
            on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn alleSaker
            on { hentSak(any<UUID>()) } doAnswer { invocation ->
                val sakId = invocation.getArgument<UUID>(0)
                sakerPerId.getValue(sakId).right()
            }
        }
        val reguleringerFraPesysService = mock<ReguleringerFraPesysService> {
            on { hentReguleringer(any()) } doAnswer { invocation ->
                val parameter = invocation.getArgument<HentReguleringerPesysParameter>(0)
                parameter.brukereMedEps.map { brukerMedEps ->
                    EksterntRegulerteBeløp(
                        brukerFnr = brukerMedEps.fnr,
                        beløpBruker = listOf(
                            RegulertBeløp(
                                fnr = brukerMedEps.fnr,
                                fradragstype = EksterntBeløpSomFradragstype.Alderspensjon,
                                førRegulering = BigDecimal.ZERO.setScale(2),
                                etterRegulering = BigDecimal.ZERO.setScale(2),
                            ),
                        ),
                        beløpEps = emptyList(),
                    ).right()
                }
            }
        }

        val service = ReguleringAutomatiskServiceImpl(
            reguleringRepo = reguleringRepo,
            sakService = sakService,
            vedtakRepo = vedtakRepo,
            clock = clock,
            satsFactory = satsFactory,
            reguleringService = reguleringService,
            statistikkService = mock(),
            sessionFactory = sessionFactory,
            reguleringKjøringRepo = reguleringKjøringRepo,
            reguleringerFraPesysService = reguleringerFraPesysService,
            aapReguleringerService = mock {
                on { hentReguleringer(any()) } doAnswer { invocation ->
                    val parameter = invocation.getArgument<HentReguleringerPesysParameter>(0)
                    parameter.brukereMedEps.map { brukerMedEps ->
                        EksterntRegulerteBeløp(
                            brukerFnr = brukerMedEps.fnr,
                            beløpBruker = emptyList(),
                            beløpEps = emptyList(),
                        ).right()
                    }
                }
            },
        )

        val resultater = service.startAutomatiskRegulering(mai(2021))

        resultater.size shouldBe antallSaker
        val sakerPerKall = argumentCaptor<HentReguleringerPesysParameter>()
        verify(reguleringerFraPesysService, times(3)).hentReguleringer(sakerPerKall.capture())
        sakerPerKall.allValues.map { it.brukereMedEps.size } shouldBe listOf(
            50,
            50,
            1,
        )
        sakerPerKall.allValues.all { (it.brukereMedEps.size) <= 50 } shouldBe true
    }

    @Nested
    inner class UtledRegulertypeTest {
        private val fradraget = Fradragsgrunnlag.create(
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.OffentligPensjon,
                månedsbeløp = 8000.0,
                periode = år(2021),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
        private val reguleringService = lagReguleringAutomatiskServiceImpl(
            vedtakSøknadsbehandlingIverksattInnvilget(
                customGrunnlag = grunnlagsdataEnsligUtenFradrag(
                    fradragsgrunnlag = listOf(fradraget),
                ).let { listOf(it.bosituasjon, it.fradragsgrunnlag) }.flatten(),
            ).first,
            beløp = 12000,
        )

        @Test
        fun `behandlinger som ikke har OffentligPensjon eller NAVytelserTilLivsopphold blir automatisk regulert`() {
            val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
            val reguleringService = lagReguleringAutomatiskServiceImpl(sak)

            reguleringService.startAutomatiskRegulering(mai(2021))
                .first().getOrFail().reguleringstype shouldBe Reguleringstype.AUTOMATISK
        }

        @Test
        fun `fradraget OffentligPensjon gir manuell pga den må justeres ved g-endring & henting fra kilde`() {
            reguleringService.startAutomatiskRegulering(mai(2021)).single()
                .getOrFail().reguleringstype shouldBe Reguleringstype.MANUELL(
                setOf(
                    ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag(
                        fradragskategori = fradraget.fradragstype.kategori,
                        fradragTilhører = fradraget.tilhører,
                    ),
                ),
            )
        }

        @Test
        fun `Stans må håndteres manuelt`() {
            val stansAvYtelse = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().first
            val reguleringService = lagReguleringAutomatiskServiceImpl(stansAvYtelse)

            reguleringService.startAutomatiskRegulering(mai(2021)).single()
                .getOrFail().reguleringstype shouldBe Reguleringstype.MANUELL(
                setOf(ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset("Saken er midlertidig stanset")),
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

            val reguleringService = lagReguleringAutomatiskServiceImpl(sak = revurdertSak, clock = clock)

            reguleringService.startAutomatiskRegulering(mai(2021))
                .first().leftOrNull().let {
                    it as BleIkkeRegulert.SkalIkkeRegulere
                    it.saksnummer shouldBe sakOgVedtak.first.saksnummer
                    it.feil shouldBe Sak.KanIkkeRegulere.FinnesIngenVedtakSomKanRevurderesForValgtPeriode
                }
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

            val reguleringService = lagReguleringAutomatiskServiceImpl(revurdertSak)

            val regulering =
                reguleringService.startAutomatiskRegulering(mai(2021)).first()
                    .getOrFail()
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
            val reguleringService = lagReguleringAutomatiskServiceImpl(sak)

            val regulering =
                reguleringService.startAutomatiskRegulering(mai(2021)).first()
                    .getOrFail()
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
            val reguleringService = lagReguleringAutomatiskServiceImpl(sak)

            reguleringService.startAutomatiskRegulering(mai(2021))
                .first().leftOrNull().let {
                    it as BleIkkeRegulert.SkalIkkeRegulere
                    it.saksnummer shouldBe sak.saksnummer
                    it.feil shouldBe Sak.KanIkkeRegulere.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig
                }
        }

        @Test
        fun `en simulering med feilutbetalinger skal gå gjennom automatisk`() {
            val revurdertSak =
                vedtakSøknadsbehandlingIverksattInnvilget(stønadsperiode = Stønadsperiode.create(år(2021))).first

            val reguleringService = lagReguleringAutomatiskServiceImpl(revurdertSak, lagFeilutbetaling = true, beløp = 10500)

            reguleringService.startAutomatiskRegulering(mai(2021)).let {
                it.size shouldBe 1
                it.first().getOrFail().erIverksatt shouldBe true
            }
        }
    }

    @Nested
    inner class PeriodeTester {

        @Test
        fun `skal kunne regulere selv om reguleringsdato er langt etter vedtaksperioden`() {
            val sak = vedtakSøknadsbehandlingIverksattInnvilget(stønadsperiode = Stønadsperiode.create(år(2021))).first
            val reguleringService = lagReguleringAutomatiskServiceImpl(sak)

            reguleringService.startAutomatiskRegulering(mai(2023)).let {
                it.size shouldBe 1
                it.first().leftOrNull().let { feil ->
                    feil as BleIkkeRegulert.SkalIkkeRegulere
                    feil.saksnummer shouldBe sak.saksnummer
                    feil.feil shouldBe Sak.KanIkkeRegulere.FinnesIngenVedtakSomKanRevurderesForValgtPeriode
                }
            }
        }

        @Test
        fun `reguleringen kan ikke starte tidligere enn reguleringsdatoen`() {
            val sak = vedtakSøknadsbehandlingIverksattInnvilget(stønadsperiode = Stønadsperiode.create(år(2021))).first
            val reguleringService = lagReguleringAutomatiskServiceImpl(sak)

            val regulering =
                reguleringService.startAutomatiskRegulering(mai(2021)).first()
                    .getOrFail()
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
            val reguleringService = lagReguleringAutomatiskServiceImpl(sak)

            val regulering =
                reguleringService.startAutomatiskRegulering(mai(2021)).first()
                    .getOrFail()
            regulering.periode.fraOgMed shouldBe 1.juni(2021)
        }
    }

    @Test
    fun `regulering går til manuell dersom simulering eller utbetaling kaster exception`() {
        val clock = tikkendeFixedClock()
        val sak = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
        ).first.copy(
            // Endrer utbetalingene for å trigge behov for regulering (hvis ikke vil vi ikke ha beregningsdiff)
            utbetalinger = Utbetalinger(
                // "De fleste utbetalingsoperasjoner krever at alle utbetalinger er oversendt og vi har mottatt en OK-kvittering..."
                // dersom vi ikke har kvittering vil trigge typen exception som nevnt over
                oversendtUtbetalingUtenKvittering(
                    beregning = beregning(fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt1000())),
                    clock = clock,
                    beløp = 20000,
                ),
            ),
        )
        val vedtakRepo = mock<VedtakRepo> {
            on { hentVedtakSomKanRevurderesForSak(sak.id) } doReturn sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
        }
        val reguleringService = lagReguleringAutomatiskServiceImpl(sak = sak, scrambleUtbetaling = false, clock = clock, vedtakRepo = vedtakRepo)

        reguleringService.startAutomatiskRegulering(mai(2021)).let {
            it.size shouldBe 1
            it.first().leftOrNull().let { feil ->
                feil as BleIkkeRegulert.KunneIkkeBehandleAutomatisk
                feil.saksnummer shouldBe sak.saksnummer
                feil.feil shouldBe KunneIkkeBehandleRegulering.KunneIkkeSimulere
            }
        }
    }

    @Test
    fun `gjør ingen sideeffekter ved dry run av eksisterende grunnbeløp`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget().first.copy(
            utbetalinger = Utbetalinger(
                oversendtUtbetalingMedKvittering(
                    beløp = 21000,
                ),
            ),
        )

        val reguleringRepo = mock<ReguleringRepo> {
            on { hentForSakId(sak.id) } doReturn sak.reguleringer
        }
        val sakService = mock<SakService> {
            on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn listOf(sak.info())
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val utbetalingService = mock<UtbetalingService> {
            on { simulerUtbetaling(any()) } doAnswer { invocation ->
                simulerUtbetaling(
                    utbetalingerPåSak = sak.utbetalinger,
                    utbetalingForSimulering = (invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering),
                )
            }
        }
        val vedtakMock = mock<VedtakService> {}
        val vedtakRepo = mock<VedtakRepo> {
            on { hentVedtakSomKanRevurderesForSak(sak.id) } doReturn sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
        }
        val sessionMock = mock<SessionFactory> {}
        val satsFactory = satsFactoryTestPåDato(25.mai(2021))
        val clock = fixedClockAt(25.mai(2021))
        val reguleringService = ReguleringServiceImpl(
            reguleringRepo = reguleringRepo,
            utbetalingService = utbetalingService,
            vedtakService = vedtakMock,
            sessionFactory = sessionMock,
            satsFactory = satsFactory,
            clock = clock,
        )

        ReguleringAutomatiskServiceImpl(
            reguleringRepo = reguleringRepo,
            reguleringService = reguleringService,
            sakService = sakService,
            vedtakRepo = vedtakRepo,
            satsFactory = satsFactory,
            clock = clock,
            statistikkService = mock(),
            sessionFactory = sessionMock,
            reguleringKjøringRepo = mock(),
            reguleringerFraPesysService = mock {
                on { hentReguleringer(any()) } doReturn
                    listOf(
                        EksterntRegulerteBeløp(
                            brukerFnr = sak.fnr,
                            beløpBruker = listOf(
                                RegulertBeløp(
                                    fnr = sak.fnr,
                                    fradragstype = EksterntBeløpSomFradragstype.Alderspensjon,
                                    førRegulering = BigDecimal.ZERO.setScale(2),
                                    etterRegulering = BigDecimal.ZERO.setScale(2),
                                ),
                            ),
                            beløpEps = emptyList(),
                        ).right(),
                    )
            },
            aapReguleringerService = mock {
                on { hentReguleringer(any()) } doReturn listOf(EksterntRegulerteBeløp(brukerFnr = sak.fnr, beløpBruker = emptyList(), beløpEps = emptyList()).right())
            },

        ).startAutomatiskReguleringForInnsyn(
            StartAutomatiskReguleringForInnsynCommand(
                gjeldendeSatsFra = 25.mai(2021),
                startDatoRegulering = mai(2021),
                dryRunNyttGrunnbeløp = null,
                lagreManuelle = false,
            ),
        )

        verify(sakService).hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
        verify(sakService).hentSak(argShouldBe(sak.id))
        verify(utbetalingService).simulerUtbetaling(any())

        verifyNoMoreInteractions(sakService)
        verifyNoMoreInteractions(utbetalingService)
        verifyNoMoreInteractions(vedtakMock)
        verifyNoInteractions(sessionMock)
    }

    @Test
    fun `gjør ingen sideeffekter ved dry run der vi legger inn et nytt test grunnbeløp`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget().first.copy(
            utbetalinger = Utbetalinger(
                oversendtUtbetalingMedKvittering(
                    beløp = 21000,
                ),
            ),
        )

        val reguleringRepo = mock<ReguleringRepo> {
            on { hentForSakId(sak.id) } doReturn sak.reguleringer
        }
        val sakService = mock<SakService> {
            on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn listOf(sak.info())
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val utbetalingService = mock<UtbetalingService> {
            on { simulerUtbetaling(any()) } doAnswer { invocation ->
                simulerUtbetaling(
                    utbetalingerPåSak = sak.utbetalinger,
                    utbetalingForSimulering = (invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering),
                )
            }
        }
        val vedtakMock = mock<VedtakService> {}
        val vedtakRepo = mock<VedtakRepo> {
            on { hentVedtakSomKanRevurderesForSak(sak.id) } doReturn sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
        }
        val sessionMock = mock<SessionFactory> {}
        val clock = fixedClockAt(25.mai(2021))

        val satsFactory = SatsFactoryForSupplerendeStønad(
            grunnbeløpsendringer = grunnbeløpsendringer.filter { it.virkningstidspunkt.year < 2021 }.toNonEmptyList(),
        ).gjeldende(1.mai(2020))

        val reguleringService = ReguleringServiceImpl(
            reguleringRepo = reguleringRepo,
            utbetalingService = utbetalingService,
            vedtakService = vedtakMock,
            sessionFactory = sessionMock,
            satsFactory = satsFactory,
            clock = clock,
        )

        ReguleringAutomatiskServiceImpl(
            reguleringRepo = reguleringRepo,
            sakService = sakService,
            vedtakRepo = vedtakRepo,
            satsFactory = satsFactory,
            reguleringService = reguleringService,
            statistikkService = mock(),
            sessionFactory = sessionMock,
            clock = clock,
            reguleringKjøringRepo = mock(),
            reguleringerFraPesysService = mock {
                on { hentReguleringer(any()) } doReturn
                    listOf(
                        EksterntRegulerteBeløp(
                            brukerFnr = sak.fnr,
                            beløpBruker = listOf(
                                RegulertBeløp(
                                    fnr = sak.fnr,
                                    fradragstype = EksterntBeløpSomFradragstype.Alderspensjon,
                                    førRegulering = BigDecimal.ZERO.setScale(2),
                                    etterRegulering = BigDecimal.ZERO.setScale(2),
                                ),
                            ),
                            beløpEps = emptyList(),
                        ).right(),
                    )
            },
            aapReguleringerService = mock {
                on { hentReguleringer(any()) } doReturn listOf(EksterntRegulerteBeløp(brukerFnr = sak.fnr, beløpBruker = emptyList(), beløpEps = emptyList()).right())
            },
        ).startAutomatiskReguleringForInnsyn(
            StartAutomatiskReguleringForInnsynCommand(
                gjeldendeSatsFra = 25.mai(2021),
                startDatoRegulering = mai(2021),
                overrideableGrunnbeløpsendringer = grunnbeløpsendringer.filter { it.virkningstidspunkt.year < 2021 }
                    .toNonEmptyList(),
                dryRunNyttGrunnbeløp = DryRunNyttGrunnbeløp(
                    virkningstidspunkt = 1.mai(2021),
                    ikrafttredelse = 21.mai(2021),
                    omregningsfaktor = BigDecimal(1.049807),
                    grunnbeløp = 106399,
                ),
                lagreManuelle = false,
            ),
        )

        verify(sakService).hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
        verify(sakService).hentSak(argShouldBe(sak.id))
        verify(utbetalingService).simulerUtbetaling(any())

        verifyNoMoreInteractions(sakService)
        verifyNoMoreInteractions(utbetalingService)
        verifyNoMoreInteractions(vedtakMock)
        verifyNoInteractions(sessionMock)
    }

    /**
     * @param scrambleUtbetaling Endrer utbetalingslinjene på saken slik at de ikke lenger matcher gjeldendeVedtaksdata. Da tvinger vi fram en ny beregning som har andre beløp enn tidligere utbetalinger.
     */
    private fun lagReguleringAutomatiskServiceImpl(
        sak: Sak,
        lagFeilutbetaling: Boolean = false,
        scrambleUtbetaling: Boolean = true,
        clock: Clock = tikkendeFixedClock(),
        beløp: Int = 20000,
        vedtakRepo: VedtakRepo = mock {
            on { hentVedtakSomKanRevurderesForSak(any()) } doAnswer {
                sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
            }
        },
    ): ReguleringAutomatiskServiceImpl {
        val vedtaksliste = if (lagFeilutbetaling) {
            listOf(
                (sak.vedtakListe.first() as VedtakInnvilgetSøknadsbehandling).let { vedtak ->
                    VedtakInnvilgetSøknadsbehandling.createFromPersistence(
                        id = vedtak.id,
                        opprettet = vedtak.opprettet,
                        behandling = vedtak.behandling.let {
                            it.copy(
                                grunnlagsdataOgVilkårsvurderinger = it.grunnlagsdataOgVilkårsvurderinger.oppdaterFradragsgrunnlag(
                                    fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 10000.0)),
                                ),
                            )
                        },
                        saksbehandler = vedtak.saksbehandler,
                        attestant = vedtak.attestant,
                        periode = vedtak.periode,
                        beregning = vedtak.beregning,
                        simulering = vedtak.simulering,
                        utbetalingId = vedtak.utbetalingId,
                        dokumenttilstand = vedtak.dokumenttilstand,
                    )
                },
            )
        } else {
            sak.vedtakListe
        }
        val sakMedEndringer = if (scrambleUtbetaling) {
            sak.copy(
                // Endrer utbetalingene for å trigge behov for regulering (hvis ikke vil vi ikke ha beregningsdiff)
                utbetalinger = Utbetalinger(
                    oversendtUtbetalingMedKvittering(
                        beregning = beregning(fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt1000())),
                        clock = clock,
                        beløp = beløp,
                    ),
                ),
                // hack det til og snik inn masse fradrag i grunnlaget til saken slik at vi  får fremprovisert en feilutbetaling ved simulering
                vedtakListe = vedtaksliste,
            )
        } else {
            sak
        }

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
        val reguleringRepo = mock<ReguleringRepo> {
            on { hent(any()) } doReturn sakMedEndringer.reguleringer.firstOrNull()
            on { hentForSakId(sakMedEndringer.id) } doReturn sakMedEndringer.reguleringer
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
        }
        val utbetalingService = mock<UtbetalingService> { service ->
            doAnswer { invocation ->
                simulerUtbetaling(
                    utbetalingerPåSak = sakMedEndringer.utbetalinger,
                    utbetalingForSimulering = (invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering),
                )
            }.whenever(service).simulerUtbetaling(any())
            on { klargjørUtbetaling(any(), any()) } doReturn nyUtbetaling.right()
        }
        val vedtakService = mock<VedtakService>()
        val sessionFactory = TestSessionFactory()
        val satsFactory = satsFactoryTestPåDato()
        val reguleringService = ReguleringServiceImpl(
            reguleringRepo = reguleringRepo,
            utbetalingService = utbetalingService,
            vedtakService = vedtakService,
            sessionFactory = sessionFactory,
            satsFactory = satsFactory,
            clock = clock,
        )
        return ReguleringAutomatiskServiceImpl(
            sakService = mock {
                on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn listOf(sakMedEndringer.info())
                on { hentSak(any<UUID>()) } doReturn sakMedEndringer.right()
            },
            satsFactory = satsFactory,
            reguleringRepo = reguleringRepo,
            vedtakRepo = if (lagFeilutbetaling) {
                mock {
                    on { hentVedtakSomKanRevurderesForSak(any()) } doAnswer {
                        vedtaksliste.filterIsInstance<VedtakSomKanRevurderes>()
                    }
                }
            } else {
                vedtakRepo
            },
            clock = clock,
            reguleringService = reguleringService,
            statistikkService = mock(),
            sessionFactory = sessionFactory,
            reguleringKjøringRepo = mock(),
            reguleringerFraPesysService = mock {
                on { hentReguleringer(any()) } doReturn
                    listOf(
                        EksterntRegulerteBeløp(
                            brukerFnr = sak.fnr,
                            beløpBruker = listOf(
                                RegulertBeløp(
                                    fnr = sak.fnr,
                                    fradragstype = EksterntBeløpSomFradragstype.Alderspensjon,
                                    førRegulering = BigDecimal.ZERO.setScale(2),
                                    etterRegulering = BigDecimal.ZERO.setScale(2),
                                ),
                            ),
                            beløpEps = emptyList(),
                        ).right(),
                    )
            },
            aapReguleringerService = mock {
                on { hentReguleringer(any()) } doReturn listOf(EksterntRegulerteBeløp(brukerFnr = sak.fnr, beløpBruker = emptyList(), beløpEps = emptyList()).right())
            },
        )
    }

    @Test
    fun `slår sammen pesys og aap for samme bruker-fnr`() {
        val bruker = HentReguleringerPesysParameter.BrukerMedEps(
            fnr = fnr,
            sakstype = Sakstype.UFØRE,
            fradragstyperBruker = emptySet(),
            eps = null,
            fradragstyperEps = emptySet(),
        )
        val pesys = listOf(
            EksterntRegulerteBeløp(
                brukerFnr = fnr,
                beløpBruker = listOf(
                    RegulertBeløp(
                        fnr = fnr,
                        fradragstype = EksterntBeløpSomFradragstype.Alderspensjon,
                        førRegulering = BigDecimal("100.00"),
                        etterRegulering = BigDecimal("110.00"),
                    ),
                ),
                beløpEps = emptyList(),
            ).right(),
        )
        val aap = listOf(
            EksterntRegulerteBeløp(
                brukerFnr = fnr,
                beløpBruker = listOf(
                    RegulertBeløp(
                        fnr = fnr,
                        fradragstype = EksterntBeløpSomFradragstype.Arbeidsavklaringspenger,
                        førRegulering = BigDecimal("200.00"),
                        etterRegulering = BigDecimal("210.00"),
                    ),
                ),
                beløpEps = emptyList(),
            ).right(),
        )

        val resultat = slåSammenEksterneReguleringer(
            brukereMedEps = listOf(bruker),
            fraPesys = pesys,
            fraAap = aap,
        ).single().shouldBeRight()

        resultat.beløpBruker.map { it.fradragstype } shouldBe listOf(
            EksterntBeløpSomFradragstype.Alderspensjon,
            EksterntBeløpSomFradragstype.Arbeidsavklaringspenger,
        )
    }

    @Test
    fun `feiler tydelig dersom pesys og aap mangler forventede brukere`() {
        val bruker = HentReguleringerPesysParameter.BrukerMedEps(
            fnr = fnr,
            sakstype = Sakstype.UFØRE,
            fradragstyperBruker = emptySet(),
            eps = null,
            fradragstyperEps = emptySet(),
        )
        val pesys = listOf(
            EksterntRegulerteBeløp(
                brukerFnr = fnr,
                beløpBruker = emptyList(),
                beløpEps = emptyList(),
            ).right(),
        )

        val feil = runCatching {
            slåSammenEksterneReguleringer(
                brukereMedEps = listOf(bruker),
                fraPesys = pesys,
                fraAap = emptyList(),
            )
        }.exceptionOrNull()

        (feil is IllegalArgumentException) shouldBe true
    }

    @Test
    fun `slår sammen feil fra pesys og aap for samme bruker`() {
        val bruker = HentReguleringerPesysParameter.BrukerMedEps(
            fnr = fnr,
            sakstype = Sakstype.UFØRE,
            fradragstyperBruker = emptySet(),
            eps = null,
            fradragstyperEps = emptySet(),
        )

        val resultat = slåSammenEksterneReguleringer(
            brukereMedEps = listOf(bruker),
            fraPesys = listOf(
                no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker(
                    fnr = fnr,
                    alleFeil = listOf(no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering.IngenPeriodeFraPesys),
                ).left(),
            ),
            fraAap = listOf(
                no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker(
                    fnr = fnr,
                    alleFeil = listOf(no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering.KunneIkkeHenteAap),
                ).left(),
            ),
        ).single().fold(
            ifLeft = { it },
            ifRight = { error("Forventet left ved sammenslåing av feil fra Pesys og AAP") },
        )

        resultat.alleFeil shouldBe listOf(
            no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering.IngenPeriodeFraPesys,
            no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering.KunneIkkeHenteAap,
        )
    }
}
