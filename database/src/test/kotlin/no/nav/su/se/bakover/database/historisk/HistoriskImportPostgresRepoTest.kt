package no.nav.su.se.bakover.database.historisk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.historisk.HistoriskImport
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataSide
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class HistoriskImportPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer rader og checkpoint atomisk og kan fullføre importen`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = HistoriskImportPostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics)
        val tabellnavn = "INFOTRYGD_SUQ.T_VEDTAK"
        val import = repo.opprettImport(
            listOf(
                NyHistoriskTabellimport(
                    tabellnavn = tabellnavn,
                    forventetAntall = 2,
                    kolonner = listOf("ID", "BELOP"),
                ),
            ),
        )

        repo.lagreSide(
            HistoriskRådataSide(
                importId = import.id,
                tabellnavn = tabellnavn,
                side = 0,
                nesteIterator = "side-2",
                rader = listOf(mapOf("ID" to "1", "BELOP" to null)),
            ),
        )

        repo.hentPågåendeImport() shouldBe HistoriskImport(
            id = import.id,
            status = HistoriskImport.Status.PÅGÅR,
            tabeller = listOf(
                HistoriskImport.Tabell(
                    tabellnavn = tabellnavn,
                    status = HistoriskImport.Status.PÅGÅR,
                    forventetAntall = 2,
                    importertAntall = 1,
                    nesteIterator = "side-2",
                    nesteSide = 1,
                    kolonner = listOf("ID", "BELOP"),
                ),
            ),
        )

        repo.lagreSide(
            HistoriskRådataSide(
                importId = import.id,
                tabellnavn = tabellnavn,
                side = 1,
                nesteIterator = null,
                rader = listOf(mapOf("ID" to "2", "BELOP" to "1234")),
            ),
        )
        repo.fullførImport(import.id)

        repo.hentPågåendeImport() shouldBe null
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT data ->> 'ID', data -> 'BELOP' FROM historisk_import_rad ORDER BY side, radnummer",
            ).use { statement ->
                statement.executeQuery().use { result ->
                    result.next() shouldBe true
                    result.getString(1) shouldBe "1"
                    result.getString(2) shouldBe "null"
                    result.next() shouldBe true
                    result.getString(1) shouldBe "2"
                    result.getString(2) shouldBe "\"1234\""
                    result.next() shouldBe false
                }
            }
        }
    }
}
