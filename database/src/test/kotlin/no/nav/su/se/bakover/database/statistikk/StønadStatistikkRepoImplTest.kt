package no.nav.su.se.bakover.database.statistikk

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.YearMonth
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class StønadStatistikkRepoImplTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer og henter alle felter uten tap`() {
        val repo = TestDataHelper(dataSource).stønadStatistikkRepo
        val april = lagStønadstatistikkRad(måned = YearMonth.of(2026, 4))
        val mai = lagStønadstatistikkRad(måned = YearMonth.of(2026, 5))

        repo.lagreMånedStatistikk(listOf(april, mai))

        repo.hentStatistikkForMåned(april.måned) shouldContainExactly listOf(april)
        repo.hentStatistikkForPeriode(april.måned, mai.måned) shouldContainExactly listOf(april, mai)
    }

    @Test
    fun `flytter usendte rader gjennom BigQuery-markeringen`() {
        val repo = TestDataHelper(dataSource).stønadStatistikkRepo
        val første = lagStønadstatistikkRad()
        val andre = lagStønadstatistikkRad(sakId = første.sakId)
        repo.lagreMånedStatistikk(listOf(første, andre))

        repo.hentUsendtSakIderForMåned(første.måned) shouldContainExactly listOf(første.sakId)
        repo.hentUsendtForMånedForSaker(
            måned = første.måned,
            sakIder = listOf(første.sakId),
        ).toSet() shouldBe setOf(første, andre)

        repo.markerSomSendt(listOf(første.id))

        repo.hentUsendtForMånedForSaker(
            måned = første.måned,
            sakIder = listOf(første.sakId),
        ) shouldContainExactly listOf(andre)

        repo.markerSomSendt(listOf(andre.id))
        repo.hentUsendtSakIderForMåned(første.måned) shouldBe emptyList()
    }

    @Test
    fun `lagrer rad og genereringsmarkør atomisk`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.stønadStatistikkRepo
        val rad = lagStønadstatistikkRad()

        repo.harStatistikkForMåned(rad.måned) shouldBe false

        assertThrows<ForventetRollback> {
            testDataHelper.sessionFactory.withTransactionContext { tx ->
                repo.lagreMånedStatistikk(rad, tx)
                repo.markerMånedGenerert(rad.måned, tx)
                throw ForventetRollback()
            }
        }

        repo.hentStatistikkForMåned(rad.måned) shouldBe emptyList()
        repo.harStatistikkForMåned(rad.måned) shouldBe false

        testDataHelper.sessionFactory.withTransactionContext { tx ->
            repo.lagreMånedStatistikk(rad, tx)
            repo.markerMånedGenerert(rad.måned, tx)
        }
        repo.markerMånedGenerert(rad.måned)

        repo.hentStatistikkForMåned(rad.måned) shouldContainExactly listOf(rad)
        repo.harStatistikkForMåned(rad.måned) shouldBe true
    }

    private class ForventetRollback : RuntimeException()
}
