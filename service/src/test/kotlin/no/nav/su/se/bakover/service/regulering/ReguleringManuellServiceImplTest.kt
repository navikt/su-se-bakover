package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt0
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.nyReguleringssupplementFor
import no.nav.su.se.bakover.test.nyReguleringssupplementInnholdPerType
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.stansetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.utbetalingsRequest
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.Utbetalingsrequest
import java.time.Clock
import java.util.UUID

internal class ReguleringManuellServiceImplTest {

    private val periodeMaiDes = Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.desember(2021))

    @Test
    fun `manuell behandling happy case`() {
        val supplement = Reguleringssupplement(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            supplement = listOf(
                nyReguleringssupplementFor(
                    fnr = fnr,
                    innhold = arrayOf(nyReguleringssupplementInnholdPerType(kategori = Fradragstype.OffentligPensjon.kategori)),
                ),
            ),
            originalCsv = "",
        )
        val (sak, regulering) = innvilgetSøknadsbehandlingMedÅpenRegulering(
            regulerFraOgMed = mai(2021),
            supplement = supplement,
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

        val reguleringService = lagReguleringManuellServiceImpl(sak)

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
            FradragFactory.nyUføreFradrag(
                forventetInntekt = 0,
                periode = periodeMaiDes,
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

        val reguleringService = lagReguleringManuellServiceImpl(sak)

        reguleringService.regulerManuelt(
            reguleringId = regulering.id,
            uføregrunnlag = listOf(uføregrunnlagForventetInntekt0(periode = periodeMaiDes)),
            fradrag = listOf(offentligPensjonGrunnlag(8100.0, periodeMaiDes)),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres.left()
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
}

/**
 * @param scrambleUtbetaling Endrer utbetalingslinjene på saken slik at de ikke lenger matcher gjeldendeVedtaksdata. Da tvinger vi fram en ny beregning som har andre beløp enn tidligere utbetalinger.
 */
private fun lagReguleringManuellServiceImpl(
    sak: Sak,
    lagFeilutbetaling: Boolean = false,
    scrambleUtbetaling: Boolean = true,
    clock: Clock = tikkendeFixedClock(),
): ReguleringManuellServiceImpl {
    val sakMedEndringer = if (scrambleUtbetaling) {
        sak.copy(
            // Endrer utbetalingene for å trigge behov for regulering (hvis ikke vil vi ikke ha beregningsdiff)
            utbetalinger = Utbetalinger(
                oversendtUtbetalingMedKvittering(
                    beregning = beregning(fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt1000())),
                    clock = clock,
                ),
            ),
            // hack det til og snik inn masse fradrag i grunnlaget til saken slik at vi  får fremprovisert en feilutbetaling ved simulering
            vedtakListe = if (lagFeilutbetaling) {
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
            },
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
        on { hentForSakId(any(), any()) } doReturn sakMedEndringer.reguleringer
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
    val reguleringService = ReguleringServiceImpl(
        reguleringRepo = reguleringRepo,
        utbetalingService = utbetalingService,
        vedtakService = vedtakService,
        sessionFactory = sessionFactory,
        clock = clock,
    )
    return ReguleringManuellServiceImpl(
        sakService = mock {
            on { hentSakIdSaksnummerOgFnrForAlleSaker() } doReturn listOf(sakMedEndringer.info())
            on { hentSak(any<UUID>()) } doReturn sakMedEndringer.right()
        },
        satsFactory = satsFactoryTestPåDato(),
        reguleringRepo = reguleringRepo,
        clock = clock,
        reguleringService = reguleringService,
    )
}
