package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattOpphør
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import statistikk.domain.StønadstatistikkMåned
import vedtak.domain.Vedtak
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class StønadStatistikkIT(private val dataSource: DataSource) {

    @Test
    fun `skal kun hente etterspurt måned`() {
        val mai = YearMonth.of(2025, 5)
        val juni = YearMonth.of(2025, 6)
        val juli = YearMonth.of(2025, 7)

        val testDataHelper = TestDataHelper(dataSource)

        val vedtakRepo = mockVedtakRepoForMåned(
            juni,
            listOf(
                lagVedtakAvslag(mai, juni),
                lagVedtakInnvilget(saksnummer = 2123L, mai, juni),
                lagVedtakInnvilget(saksnummer = 2321L, juli, juli),
            ),
        )

        val stønadStatistikkRepo = testDataHelper.stønadStatistikkRepo
        val service = StønadStatistikkJobServiceImpl(
            stønadStatistikkRepo = stønadStatistikkRepo,
            vedtakRepo = vedtakRepo,
            sessionFactory = testDataHelper.sessionFactory,
            clock = fixedClock,
        )

        service.lagMånedligStønadstatistikk(juni)
        val result = stønadStatistikkRepo.hentStatistikkForMåned(juni)

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

    @Test
    fun `skal kun bruke nyligste vedtak til månedlig statistikk`() {
        val mai = YearMonth.of(2025, 5)
        val juni = YearMonth.of(2025, 6)

        val testDataHelper = TestDataHelper(dataSource)

        val sakId = 2123L
        val vedtakEn = lagVedtakInnvilget(sakId, mai, juni)
        val vedtakTo = lagVedtakOpphør(sakId, juni, juni)
        val vedtakRepo = mockVedtakRepoForMåned(juni, listOf(vedtakEn, vedtakTo))

        val stønadStatistikkRepo = testDataHelper.stønadStatistikkRepo
        val service = StønadStatistikkJobServiceImpl(
            stønadStatistikkRepo = stønadStatistikkRepo,
            vedtakRepo = vedtakRepo,
            clock = fixedClock,
            sessionFactory = testDataHelper.sessionFactory,
        )

        service.lagMånedligStønadstatistikk(juni)
        val result = stønadStatistikkRepo.hentStatistikkForMåned(juni)

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

    @Test
    fun `skal kun hente opphør hvis opphøret er på angitt dato`() {
        val mai = YearMonth.of(2025, 5)
        val juni = YearMonth.of(2025, 6)
        val juli = YearMonth.of(2025, 7)

        val testDataHelper = TestDataHelper(dataSource)

        val sakId = 2123L
        val vedtakEn = lagVedtakInnvilget(sakId, mai, juli)
        val vedtakTo = lagVedtakOpphør(sakId, juni, juli)
        val vedtakRepo = mockVedtakRepoForMåned(juli, listOf(vedtakEn, vedtakTo))

        val stønadStatistikkRepo = testDataHelper.stønadStatistikkRepo
        val service = StønadStatistikkJobServiceImpl(
            stønadStatistikkRepo = stønadStatistikkRepo,
            vedtakRepo = vedtakRepo,
            clock = fixedClock,
            sessionFactory = testDataHelper.sessionFactory,
        )

        service.lagMånedligStønadstatistikk(juli)
        val result = stønadStatistikkRepo.hentStatistikkForMåned(juli)

        result.size shouldBe 0
    }

    @Test
    fun `skal lage statistikk flere måneder samtidig`() {
        val fraOgMed = YearMonth.of(2024, 11)
        val tilOgMed = YearMonth.of(2025, 2)

        val testDataHelper = TestDataHelper(dataSource)
        testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
            stønadsperiode = stønadsperiode(fraOgMed, tilOgMed),
        )
        testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
            stønadsperiode = stønadsperiode(fraOgMed.plusMonths(1), tilOgMed),
        )
        testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
            stønadsperiode = stønadsperiode(fraOgMed, tilOgMed.minusMonths(1)),
        )

        val stønadStatistikkRepo = testDataHelper.stønadStatistikkRepo
        val service = StønadStatistikkJobServiceImpl(
            stønadStatistikkRepo = stønadStatistikkRepo,
            vedtakRepo = testDataHelper.vedtakRepo,
            clock = fixedClock,
            sessionFactory = testDataHelper.sessionFactory,
        )
        service.lagStatistikkForFlereMåneder(fraOgMed, tilOgMed)

        val result = testDataHelper.stønadStatistikkRepo.hentStatistikkForPeriode(fraOgMed, tilOgMed)
        result.size shouldBe 10 // fire for vedtak en og tre for vedtak to og tre
        result.filter { it.måned == YearMonth.of(2024, 11) }.size shouldBe 2
        result.filter { it.måned == YearMonth.of(2024, 12) }.size shouldBe 3
        result.filter { it.måned == YearMonth.of(2025, 1) }.size shouldBe 3
        result.filter { it.måned == YearMonth.of(2025, 2) }.size shouldBe 2
    }

    @Test
    fun `skal lage statistikk etter revurderinger`() {
        val testDataHelper = TestDataHelper(dataSource)
        val stønadStatistikkRepo = testDataHelper.stønadStatistikkRepo

        val fraOgMed = YearMonth.of(2024, 11)
        val tilOgMed = YearMonth.of(2025, 2)

        val (sak, søknadVedtak) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
            stønadsperiode = stønadsperiode(fraOgMed, tilOgMed),
        )

        StønadStatistikkJobServiceImpl(
            stønadStatistikkRepo = stønadStatistikkRepo,
            vedtakRepo = testDataHelper.vedtakRepo,
            clock = fixedClock,
            sessionFactory = testDataHelper.sessionFactory,
        ).lagStatistikkForFlereMåneder(fraOgMed, tilOgMed)

        val statistikkFørRevurdering = stønadStatistikkRepo.hentStatistikkForPeriode(fraOgMed, tilOgMed)
        statistikkFørRevurdering.size shouldBe 4
        statistikkFørRevurdering.forEach {
            it.vedtaksdato shouldBe søknadVedtak.opprettet.toLocalDate(zoneIdOslo)
        }

        val revurderingTid = fixedClock.plus(10L, ChronoUnit.DAYS)
        val (_, _, _, revurderingVedtak) = testDataHelper.persisterIverksattRevurdering(
            clock = revurderingTid,
            stønadsperiode = stønadsperiode(fraOgMed.plusMonths(2), tilOgMed),
            sakOgVedtak = sak to søknadVedtak,
        )
        StønadStatistikkJobServiceImpl(
            stønadStatistikkRepo = stønadStatistikkRepo,
            vedtakRepo = testDataHelper.vedtakRepo,
            clock = revurderingTid,
            sessionFactory = testDataHelper.sessionFactory,
        ).lagStatistikkForFlereMåneder(fraOgMed, tilOgMed)
        val statistikkEtterRevurdering = stønadStatistikkRepo.hentStatistikkForPeriode(fraOgMed, tilOgMed)
        statistikkEtterRevurdering.size shouldBe 8
        statistikkEtterRevurdering.filter { it.vedtaksdato == søknadVedtak.opprettet.toLocalDate(zoneIdOslo) }.let {
            it.size shouldBe 6
            it.groupBy { it.tekniskTid.toLocalDateTime(zoneIdOslo) }.keys.size shouldBe 2
        }
        statistikkEtterRevurdering.filter { it.vedtaksdato == revurderingVedtak.opprettet.toLocalDate(zoneIdOslo) }.size shouldBe 2
    }

    @Test
    fun `stønadjobb genererer statistikk for forrige måned, sender til bigquery og kjører ikke dobbelt`() {
        // Klokke satt til juli 2025 slik at "forrige måned" (now-1) blir juni 2025.
        val clock = fixedClockAt(1.juli(2025))
        val juni = YearMonth.of(2025, 6)

        val testDataHelper = TestDataHelper(dataSource)

        val vedtakRepo = mockVedtakRepoForMåned(
            juni,
            listOf(
                lagVedtakInnvilget(saksnummer = 2123L, juni, juni, sakId = UUID.randomUUID()),
                lagVedtakInnvilget(saksnummer = 2321L, juni, juni, sakId = UUID.randomUUID()),
            ),
        )

        val bigQueryBatcher = mutableListOf<List<StønadstatistikkMåned>>()
        val stønadStatistikkRepo = testDataHelper.stønadStatistikkRepo
        val service = StønadStatistikkJobServiceImpl(
            stønadStatistikkRepo = stønadStatistikkRepo,
            vedtakRepo = vedtakRepo,
            sessionFactory = testDataHelper.sessionFactory,
            clock = clock,
            sendTilBigQuery = { bigQueryBatcher.add(it) },
        )

        service.lagMånedligStønadstatistikk()

        val persistert = stønadStatistikkRepo.hentStatistikkForMåned(juni)
        persistert.size shouldBe 2
        // Alt som er persistert skal ha blitt sendt til bigquery.
        bigQueryBatcher.flatten().map { it.id }.toSet() shouldBe persistert.map { it.id }.toSet()

        // Andre kjøring skal ikke generere eller sende noe på nytt (harKjørt-guard).
        service.lagMånedligStønadstatistikk()
        stønadStatistikkRepo.hentStatistikkForMåned(juni).size shouldBe 2
        bigQueryBatcher.flatten().map { it.id }.toSet() shouldBe persistert.map { it.id }.toSet()
    }

    @Test
    fun `batching gir samme resultat som én batch, men sender flere porsjoner til bigquery`() {
        // Klokke satt til juli 2025 slik at "forrige måned" (now-1) blir juni 2025.
        val clock = fixedClockAt(1.juli(2025))
        val juni = YearMonth.of(2025, 6)

        val testDataHelper = TestDataHelper(dataSource)

        val alleVedtak = listOf(
            lagVedtakInnvilget(saksnummer = 2021L, juni, juni, sakId = UUID.randomUUID()),
            lagVedtakInnvilget(saksnummer = 2022L, juni, juni, sakId = UUID.randomUUID()),
            lagVedtakInnvilget(saksnummer = 2023L, juni, juni, sakId = UUID.randomUUID()),
        )
        val sakIder = alleVedtak.map { it.behandling.sakId }
        val vedtakRepo = mock<VedtakRepo> {
            on { hentSakIderForMåned(Måned.fra(juni)) } doReturn sakIder
            on { hentVedtakForMånedForSaker(any(), any(), anyOrNull()) } doAnswer { invocation ->
                val saker = invocation.getArgument<List<UUID>>(1)
                alleVedtak.filter { it.behandling.sakId in saker }
            }
        }

        val bigQueryBatcher = mutableListOf<List<StønadstatistikkMåned>>()
        val stønadStatistikkRepo = testDataHelper.stønadStatistikkRepo
        val service = StønadStatistikkJobServiceImpl(
            stønadStatistikkRepo = stønadStatistikkRepo,
            vedtakRepo = vedtakRepo,
            sessionFactory = testDataHelper.sessionFactory,
            clock = clock,
            batchStørrelse = 1,
            sendTilBigQuery = { bigQueryBatcher.add(it) },
        )

        service.lagMånedligStønadstatistikk()

        // batchStørrelse=1 og tre saker → tre separate oversendelser til bigquery.
        bigQueryBatcher.size shouldBe 3
        bigQueryBatcher.forEach { it.size shouldBe 1 }

        val persistert = stønadStatistikkRepo.hentStatistikkForMåned(juni)
        persistert.size shouldBe 3
        // Summen av alle porsjonene sendt til bigquery = alt som er persistert (identisk med én stor batch).
        bigQueryBatcher.flatten().map { it.id }.toSet() shouldBe persistert.map { it.id }.toSet()
    }

    companion object {
        /**
         * Mocker [VedtakRepo] slik at den nye batching-flyten (henter sakIder, deretter vedtak per
         * sak) returnerer akkurat [vedtak] for [måned] – tilsvarende det den gamle
         * `hentVedtakForMåned` returnerte. Brukes av testene som mocker vedtakskilden.
         */
        private fun mockVedtakRepoForMåned(måned: YearMonth, vedtak: List<Vedtak>): VedtakRepo {
            val sakIder = vedtak.map { it.behandling.sakId }.distinct()
            return mock {
                on { hentSakIderForMåned(Måned.fra(måned)) } doReturn sakIder
                on { hentVedtakForMånedForSaker(Måned.fra(måned), sakIder) } doReturn vedtak
            }
        }

        private fun lagVedtakInnvilget(
            saksnummer: Long,
            fom: YearMonth,
            tom: YearMonth,
            sakId: UUID = no.nav.su.se.bakover.test.sakId,
        ): VedtakInnvilgetSøknadsbehandling {
            return vedtakSøknadsbehandlingIverksattInnvilget(
                saksnummer = Saksnummer(saksnummer),
                sakId = sakId,
                stønadsperiode = stønadsperiode(fom, tom),
            ).second
        }

        private fun lagVedtakAvslag(
            fom: YearMonth,
            tom: YearMonth,
        ): VedtakAvslagBeregning {
            return vedtakSøknadsbehandlingIverksattAvslagMedBeregning(
                stønadsperiode = stønadsperiode(fom, tom),
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

        private fun stønadsperiode(fom: YearMonth, tom: YearMonth) =
            Stønadsperiode.create(Periode.create(fom.atDay(1), tom.atEndOfMonth()))
    }
}
