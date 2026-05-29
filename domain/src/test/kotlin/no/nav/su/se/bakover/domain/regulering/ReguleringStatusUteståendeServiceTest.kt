package no.nav.su.se.bakover.domain.regulering

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.bosituasjonBorMedAndreVoksne
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingAlder
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import satser.domain.Satskategori
import vedtak.domain.GrunnbeløpOgSatsbeløpPåVedtak
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.Utbetalinger
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.to

internal class ReguleringStatusUteståendeServiceTest {

    private val clock: Clock =
        TikkendeKlokke(Clock.fixed(Instant.parse("2025-06-15T10:15:30Z"), ZoneId.of("Europe/Oslo")))
    private val gammelClock: Clock =
        TikkendeKlokke(Clock.fixed(Instant.parse("2024-06-15T10:15:30Z"), ZoneId.of("Europe/Oslo")))

    @Test
    fun `returnerer ReguleringStatus for uføre og ordinær satskategori`() {
        val sessionFactory = TestSessionFactory()

        val saker = opprettTestSaker()

        val sakService = mock<SakService> {
            on { hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst() } doReturn saker.map { it.info() }
            saker.forEach { sak ->
                on { hentSak(sak.id) } doReturn sak.right()
            }
        }

        val utbetalingRepo = mock<UtbetalingRepo> {
            on { hentOversendteUtbetalingerForSakIder(saker.map { it.id }) } doReturn saker.associate { it.id to it.utbetalinger }
        }

        val vedtaksRepo = mock<VedtakRepo> {
            saker.forEach { sak ->
                on {
                    hentBeregninginfoTilVedtakPåDato(
                        sak.info(),
                        LocalDate.of(2025, 5, 1),
                        sessionFactory.newTransactionContext(),
                    )
                } doReturn sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().last { !it.erStans() }.let {
                    it.beregning!!.let { beregning ->
                        GrunnbeløpOgSatsbeløpPåVedtak(
                            benyttetGrunnbeløp = beregning.getMånedsberegninger().last()
                                .getBenyttetGrunnbeløp(),
                            benyttetSatsbeløp = beregning.getMånedsberegninger().last().getSatsbeløp(),
                            satskategori = beregning.getMånedsberegninger().last().getSats().name,
                            fraOgMed = it.periode.fraOgMed,
                        )
                    }
                }

                on {
                    hentVedtakSomKanRevurderesForSak(
                        sak.id,
                        sessionFactory.newTransactionContext(),
                    )
                } doReturn sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
            }
        }

        val reguleringStatusRepo = mock<ReguleringStatusUteståendeRepo>()

        val service = ReguleringStatusUteståendeService(
            sakService = sakService,
            utbetalingRepo = utbetalingRepo,
            satsFactory = satsFactoryTestPåDato(LocalDate.now(clock)),
            vedtakRepo = vedtaksRepo,
            reguleringStatusRepo = reguleringStatusRepo,
            sessionFactory = sessionFactory,
            clock = clock,
        )

        val result = service.produserStatusSisteGrunnbeløp(2025)

        with(result.sisteGrunnbeløpOgSatser) {
            grunnbeløp shouldBe 130160
            garantipensjonOrdinærMåned shouldBe 18687.333333333332
            garantipensjonHøyMåned shouldBe 20201.5
        }
        result.sakerMedUtebetalingIMai shouldBe 8
        result.sakerMedGammelG.size shouldBe 3

        with(result.sakerMedGammelG[0]) {
            saksnummer shouldBe Saksnummer(1234564)
            benyttetGrunnbeløp shouldBe null
            benyttetSatskategori shouldBe Satskategori.ORDINÆR
            benyttetSats shouldBe 18018.833333333332
        }
        with(result.sakerMedGammelG[1]) {
            saksnummer shouldBe Saksnummer(1234565)
            benyttetGrunnbeløp shouldBe 124028
            benyttetSatskategori shouldBe Satskategori.HØY
        }
        with(result.sakerMedGammelG[2]) {
            saksnummer shouldBe Saksnummer(1234566)
            benyttetGrunnbeløp shouldBe 124028
            benyttetSatskategori shouldBe Satskategori.HØY
        }
    }

    private fun opprettTestSaker(): List<Sak> {
        val periode = Periode.create(1.januar(2025), 31.desember(2025))
        val stønadsperiode = Stønadsperiode.create(periode)

        // Uføre med ordinær satskategori
        val sakUføreOrdinær = iverksattSøknadsbehandlingUføre(
            clock = clock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(12345670),
                fnr = Fnr.generer(),
                type = Sakstype.UFØRE,
            ),
            stønadsperiode = stønadsperiode,
            customGrunnlag = listOf(
                bosituasjonBorMedAndreVoksne(periode = periode),
            ),
            satsPåDato = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling, clock) }

        // Uføre med høy satskategori
        val sakUføreHøy = iverksattSøknadsbehandlingUføre(
            clock = clock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(12345671),
                fnr = Fnr.generer(),
                type = Sakstype.UFØRE,
            ),
            stønadsperiode = stønadsperiode,
            customGrunnlag = listOf(
                bosituasjongrunnlagEnslig(periode = periode),
            ),
            satsPåDato = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling, clock) }

        // Alder med ordinær satskategori
        val sakAlderOrdinær = iverksattSøknadsbehandlingAlder(
            clock = clock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(12345672),
                fnr = Fnr.generer(),
                type = Sakstype.ALDER,
            ),
            stønadsperiode = stønadsperiode,
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling, clock) }

        // Alder med høy satskategori
        val sakAlderHøy = iverksattSøknadsbehandlingAlder(
            clock = clock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(1234563),
                fnr = Fnr.generer(),
                type = Sakstype.ALDER,
            ),
            stønadsperiode = stønadsperiode,
            customGrunnlag = listOf(bosituasjongrunnlagEnslig(periode = periode)),
            satsPåDato = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling, clock) }

        val sakAlderGammelSats = iverksattSøknadsbehandlingAlder(
            clock = gammelClock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(1234564),
                fnr = Fnr.generer(),
                type = Sakstype.ALDER,
            ),
            stønadsperiode = stønadsperiode,
            satsPåDato = gammelClock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling, gammelClock) }

        val sakUføreGammelG = iverksattSøknadsbehandlingUføre(
            clock = gammelClock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(1234565),
                fnr = Fnr.generer(),
                type = Sakstype.UFØRE,
            ),
            stønadsperiode = stønadsperiode,
            customGrunnlag = listOf(
                bosituasjongrunnlagEnslig(periode = periode),
            ),
            satsPåDato = gammelClock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling, gammelClock) }

        val sakMedVedtakEtterMai = iverksattSøknadsbehandlingUføre(
            clock = gammelClock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(1234566),
                fnr = Fnr.generer(),
                type = Sakstype.UFØRE,
            ),
            stønadsperiode = Stønadsperiode.create(periode),
            customGrunnlag = listOf(bosituasjongrunnlagEnslig(periode = periode)),
            satsPåDato = gammelClock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling, gammelClock) }
            .let {
                iverksattRevurdering(
                    clock = clock,
                    saksnummer = it.saksnummer,
                    stønadsperiode = Stønadsperiode.create(periode),
                    revurderingsperiode = Periode.create(1.juni(2025), 31.desember(2025)),
                    grunnlagsdataOverrides = listOf(
                        bosituasjongrunnlagEnslig(
                            periode = Periode.create(1.juni(2025), 31.desember(2025)),
                        ),
                    ),
                    sakOgVedtakSomKanRevurderes = it to it.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
                        .single(),
                    satsPåDato = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
                ).let { (sakUtenUtbetaling, revurdering, _) -> sakUtenUtbetaling.leggTilUtbetaling(revurdering, clock) }
            }

        val sakStansRegulert = iverksattSøknadsbehandlingUføre(
            clock = clock,
            sakInfo = SakInfo(
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(1234567),
                fnr = Fnr.generer(),
                type = Sakstype.UFØRE,
            ),
            stønadsperiode = stønadsperiode,
            customGrunnlag = listOf(bosituasjongrunnlagEnslig(periode = periode)),
            satsPåDato = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate(),
        ).let { (sakUtenUtbetaling, behandling, _) -> sakUtenUtbetaling.leggTilUtbetaling(behandling, clock) }.let {
            vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                clock = clock,
                Periode.create(1.juni(2025), 31.desember(2025)),
                it to it.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().single(),
            ).first
        }

        return listOf(
            sakUføreOrdinær,
            sakUføreHøy,
            sakAlderOrdinær,
            sakAlderHøy,
            sakAlderGammelSats,
            sakUføreGammelG,
            sakMedVedtakEtterMai,
            sakStansRegulert,
        )
    }

    private fun Sak.leggTilUtbetaling(behandling: Søknadsbehandling, clock: Clock) = copy(
        utbetalinger = Utbetalinger(
            oversendtUtbetalingMedKvittering(
                clock = clock,
                periode = behandling.periode,
                beregning = behandling.beregning!!,
            ),
        ),
    )

    private fun Sak.leggTilUtbetaling(behandling: Revurdering, clock: Clock) = copy(
        utbetalinger = Utbetalinger(
            utbetalinger.plus(
                oversendtUtbetalingMedKvittering(
                    clock = clock,
                    periode = behandling.periode,
                    beregning = behandling.beregning!!,
                    eksisterendeUtbetalinger = utbetalinger,
                ),
            ),
        ),
    )
}
