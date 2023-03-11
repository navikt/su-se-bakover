package no.nav.su.se.bakover.database.vedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.Vedtaksammendrag
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.plus
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

internal class VedtakPostgresRepoTest {

    @Test
    fun `setter inn og henter vedtak for innvilget stønad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            dataSource.withSession {
                vedtakRepo.hentVedtakForIdOgSession(vedtak.id, it) shouldBe vedtak
            }
        }
    }

    @Test
    fun `setter inn og henter vedtak for avslått stønad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            val (sak, _, vedtak) = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning()

            dataSource.withSession {
                vedtakRepo.hentVedtakForIdOgSession(vedtak.id, it) shouldBe vedtak
                sak.vedtakListe.single() shouldBe vedtak
            }
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for søknadsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            dataSource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to vedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe vedtak.behandling.id.toString()
                        it.stringOrNull("revurderingId") shouldBe null
                    }
            }
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom revurdering og vedtak ved lagring av vedtak for revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, søknadsbehandlingVedtak) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
                .let { it.first to it.second }

            val (_, iverksattRevurdering, revurderingUtbetaling, revurderingVedtak) = iverksattRevurdering(
                sakOgVedtakSomKanRevurderes = sak to søknadsbehandlingVedtak,
            )
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)
            testDataHelper.utbetalingRepo.opprettUtbetaling(revurderingUtbetaling)
            testDataHelper.vedtakRepo.lagre(revurderingVedtak)

            dataSource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to revurderingVedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe null
                        it.stringOrNull("revurderingId") shouldBe iverksattRevurdering.id.toString()
                    }
            }
        }
    }

    @Test
    fun `hent alle aktive vedtak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(
                dataSource = dataSource,
                clock = fixedClock.plus(31, ChronoUnit.DAYS),
            )
            val vedtakRepo = testDataHelper.vedtakRepo
            // Persisterer et ikke-aktivt vedtak
            testDataHelper.persisterSøknadsbehandlingIverksatt { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    stønadsperiode = Stønadsperiode.create(januar(2021)),
                    sakOgSøknad = sak to søknad,
                )
            }
            val (_, _, vedtakSomErAktivt) = testDataHelper.persisterSøknadsbehandlingIverksatt { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    stønadsperiode = Stønadsperiode.create(Periode.create(1.februar(2021), 31.mars(2021))),
                    sakOgSøknad = sak to søknad,
                )
            }

            vedtakRepo.hentForMåned(februar(2021)).also {
                it.size shouldBe 1
                it.first() shouldBe Vedtaksammendrag(
                    opprettet = vedtakSomErAktivt.opprettet,
                    periode = vedtakSomErAktivt.periode,
                    fødselsnummer = vedtakSomErAktivt.behandling.fnr,
                    vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                    sakId = vedtakSomErAktivt.sakId,
                    saksnummer = vedtakSomErAktivt.saksnummer,
                )
            }
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for avslått søknadsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (_, søknadsbehandling, vedtak) = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning()

            dataSource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to vedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe søknadsbehandling.id.toString()
                        it.stringOrNull("revurderingId") shouldBe null
                    }
            }
        }
    }

    @Test
    fun `oppretter og henter vedtak for stans av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (_, vedtak) = testDataHelper.persisterIverksattStansOgVedtak()
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            testDataHelper.dataSource.withSession {
                vedtakRepo.hentVedtakForIdOgSession(vedtak.id, it) shouldBe vedtak
            }
        }
    }

    @Test
    fun `oppretter og henter vedtak for gjenopptak av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak = testDataHelper.persisterVedtakForGjenopptak()
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            testDataHelper.dataSource.withSession {
                vedtakRepo.hentVedtakForIdOgSession(vedtak.id, it) shouldBe vedtak
            }
        }
    }
}
