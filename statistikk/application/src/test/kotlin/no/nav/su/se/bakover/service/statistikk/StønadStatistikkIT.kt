package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattOpphør
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.YearMonth

internal class StønadStatistikkIT {

    @Test
    fun `skal kun hente etterspurt måned`() {
        withMigratedDb { dataSource ->
            val mai = YearMonth.of(2025, 5)
            val juni = YearMonth.of(2025, 6)
            val juli = YearMonth.of(2025, 7)

            val testDataHelper = TestDataHelper(dataSource)

            val vedtakRepo = mock<VedtakRepo> {
                on { hentVedtakForMåned(Måned.fra(juni)) } doReturn listOf(
                    lagVedtakAvslag(mai, juni),
                    lagVedtakInnvilget(saksnummer = 2123L, mai, juni),
                    lagVedtakInnvilget(saksnummer = 2321L, juli, juli),
                )
            }

            val stønadStatistikkRepo = testDataHelper.stønadStatistikkRepo
            val service = StønadStatistikkJobServiceImpl(
                stønadStatistikkRepo = stønadStatistikkRepo,
                vedtakRepo = vedtakRepo,
            )

            service.lagMånedligStønadstatistikk(fixedClock, juni)
            val result = stønadStatistikkRepo.hentMånedStatistikk(juni)

            result.size shouldBe 1
            with(result.first()) {
                måned shouldBe juni
                utbetales shouldBe 20946L
                fradragSum shouldBe 0
                forventetInntekt shouldBe 0
                forventetInntektEps shouldBe null
                uføregrad shouldBe 100
            }
        }
    }

    @Test
    fun `skal kun bruke nyligste vedtak til månedlig statistikk`() {
        withMigratedDb { dataSource ->
            val mai = YearMonth.of(2025, 5)
            val juni = YearMonth.of(2025, 6)

            val testDataHelper = TestDataHelper(dataSource)

            val sakId = 2123L
            val vedtakEn = lagVedtakInnvilget(sakId, mai, juni)
            val vedtakTo = lagVedtakOpphør(sakId, juni, juni)
            val vedtakRepo =
                mock<VedtakRepo> { on { hentVedtakForMåned(Måned.fra(juni)) } doReturn listOf(vedtakEn, vedtakTo) }

            val stønadStatistikkRepo = testDataHelper.stønadStatistikkRepo
            val service = StønadStatistikkJobServiceImpl(
                stønadStatistikkRepo = stønadStatistikkRepo,
                vedtakRepo = vedtakRepo,
            )

            service.lagMånedligStønadstatistikk(fixedClock, juni)
            val result = stønadStatistikkRepo.hentMånedStatistikk(juni)

            result.size shouldBe 1
            with(result.first()) {
                måned shouldBe juni
                utbetales shouldBe null
                fradragSum shouldBe null
                forventetInntekt shouldBe null
                forventetInntektEps shouldBe null
                uføregrad shouldBe null
            }
        }
    }

    companion object {
        private fun lagVedtakInnvilget(
            saksnummer: Long,
            fom: YearMonth,
            tom: YearMonth,
        ): VedtakInnvilgetSøknadsbehandling {
            return vedtakSøknadsbehandlingIverksattInnvilget(
                saksnummer = Saksnummer(saksnummer),
                stønadsperiode = Stønadsperiode.Companion.create(Periode.create(fom.atDay(1), tom.atEndOfMonth())),
            ).second
        }

        private fun lagVedtakAvslag(
            fom: YearMonth,
            tom: YearMonth,
        ): VedtakAvslagBeregning {
            return vedtakSøknadsbehandlingIverksattAvslagMedBeregning(
                stønadsperiode = Stønadsperiode.Companion.create(Periode.create(fom.atDay(1), tom.atEndOfMonth())),
            ).second
        }

        private fun lagVedtakOpphør(
            saksnummer: Long,
            fom: YearMonth,
            tom: YearMonth,
        ): VedtakOpphørMedUtbetaling {
            val periode = Periode.create(fom.atDay(1), tom.atEndOfMonth())
            return vedtakRevurderingIverksattOpphør(
                saksnummer = Saksnummer(saksnummer),
                stønadsperiode = Stønadsperiode.Companion.create(periode),
                revurderingsperiode = periode,
            ).second
        }
    }
}
