package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.Reguleringer
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.utbetaling.utbetalingerNy
import no.nav.su.se.bakover.test.utbetaling.utbetalingerOpphør
import no.nav.su.se.bakover.test.utbetaling.utbetalingerReaktivering
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.domain.utbetaling.Utbetalinger

internal class HentVedtaksdataForOmregningAlderTest {
    @Test
    fun `utelater saker med stans i kjøringsmåneden`() {
        val sak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = fixedClock,
        ).first
        val måned = mai(2021)
        val forventet = BleIkkeOmregnetAlder.TrengerIkkeOmregne.StansetSak(sak.saksnummer).left()

        hentVedtaksdata(sak).hent(listOf(sak.info()), måned) shouldBe listOf(forventet)
        forventet.tilReguleringsresultat().utfall shouldBe Reguleringsresultat.Utfall.IKKE_LOEPENDE
    }

    @Test
    fun `fremtidig stans hindrer ikke vurdering av løpende sak i kjøringsmåneden`() {
        val periode = år(2021)
        val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = fixedClock,
            stønadsperiode = Stønadsperiode.create(periode),
        )
        val sak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = fixedClock,
            periode = Periode.create(juni(2021).fraOgMed, periode.tilOgMed),
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
        ).first
        val måned = januar(2021)

        hentVedtaksdata(sak).hent(listOf(sak.info()), måned) shouldBe listOf(
            BleIkkeOmregnetAlder.TrengerIkkeOmregne.HarIkkeAlderspensjonFradrag(sak.saksnummer).left(),
        )
    }

    @Test
    fun `utelater opphør i kjøringsmåneden før vedtaksgrunnlaget vurderes`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget(clock = fixedClock).first
        val måned = mai(2021)
        val utbetalinger = utbetalingerOpphør(
            sakId = sak.id,
            nyPeriode = år(2021),
            opphørsperiode = måned,
        )

        hentVedtaksdata(sak, utbetalinger).hent(listOf(sak.info()), måned) shouldBe listOf(
            BleIkkeOmregnetAlder.TrengerIkkeOmregne.IkkeLøpendeSak(sak.saksnummer).left(),
        )
    }

    @Test
    fun `utelater saker uten utbetaling i kjøringsmåneden`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget(clock = fixedClock).first
        val måned = mai(2021)
        val utbetalinger = utbetalingerNy(sakId = sak.id, periode = måned.minusMonths(1))

        hentVedtaksdata(sak, utbetalinger).hent(listOf(sak.info()), måned) shouldBe listOf(
            BleIkkeOmregnetAlder.TrengerIkkeOmregne.IkkeLøpendeSak(sak.saksnummer).left(),
        )
    }

    @Test
    fun `reaktivering i kjøringsmåneden går videre til fradragsvurderingen`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget(clock = fixedClock).first
        val måned = mai(2021)
        val utbetalinger = utbetalingerReaktivering(
            sakId = sak.id,
            nyPeriode = år(2021),
            stansFraOgMed = måned.minusMonths(1).fraOgMed,
            reaktiveringFraOgMed = måned.fraOgMed,
        )

        hentVedtaksdata(sak, utbetalinger).hent(listOf(sak.info()), måned) shouldBe listOf(
            BleIkkeOmregnetAlder.TrengerIkkeOmregne.HarIkkeAlderspensjonFradrag(sak.saksnummer).left(),
        )
    }

    @Test
    fun `beholder vurderingen av fradrag for saker uten stans`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget(clock = fixedClock).first
        val måned = mai(2021)

        hentVedtaksdata(sak).hent(listOf(sak.info()), måned) shouldBe listOf(
            BleIkkeOmregnetAlder.TrengerIkkeOmregne.HarIkkeAlderspensjonFradrag(sak.saksnummer).left(),
        )
    }

    private fun hentVedtaksdata(
        sak: Sak,
        utbetalinger: Utbetalinger = sak.utbetalinger,
    ): HentVedtaksdataForOmregningAlder {
        val vedtakRepo = mock<VedtakRepo> {
            on { hentVedtakSomKanRevurderesForSakFraOgMed(any(), any(), isNull()) } doReturn
                sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
        }
        val reguleringService = mock<ReguleringServiceImpl> {
            on { hentReguleringerForSak(sak.id) } doReturn Reguleringer.empty(sak.id)
            on { hentUtbetalinger(sak.id) } doReturn utbetalinger
        }
        return HentVedtaksdataForOmregningAlder(vedtakRepo, reguleringService, fixedClock)
    }
}
