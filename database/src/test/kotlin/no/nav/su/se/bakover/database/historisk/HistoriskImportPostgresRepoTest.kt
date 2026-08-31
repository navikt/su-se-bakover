package no.nav.su.se.bakover.database.historisk

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.domain.historisk.HistoriskImport
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataSide
import no.nav.su.se.bakover.domain.historisk.InfotrygdTabeller
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import no.nav.su.se.bakover.domain.historisk.SlettImportResultat
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
        val tabellnavn = InfotrygdTabeller.T_VEDTAK
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
            opprettet = import.opprettet,
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

        val leser = HistoriskRådataPostgresLeser(testDataHelper.sessionFactory, testDataHelper.dbMetrics)
        leser.hentReferansetabell(import.id, tabellnavn) shouldBe listOf(
            mapOf("ID" to "1", "BELOP" to null),
            mapOf("ID" to "2", "BELOP" to "1234"),
        )
    }

    @Test
    fun `tom side fullfører tabellen når forventet antall rader allerede er lagret`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = HistoriskImportPostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics)
        val tabellnavn = InfotrygdTabeller.T_VEDTAK
        val import = repo.opprettImport(
            listOf(NyHistoriskTabellimport(tabellnavn = tabellnavn, forventetAntall = 1, kolonner = listOf("ID"))),
        )

        repo.lagreSide(
            HistoriskRådataSide(
                importId = import.id,
                tabellnavn = tabellnavn,
                side = 0,
                nesteIterator = "siste-side",
                rader = listOf(mapOf("ID" to "1")),
            ),
        ).status shouldBe HistoriskImport.Status.PÅGÅR

        val fullførtTabell = repo.lagreSide(
            HistoriskRådataSide(
                importId = import.id,
                tabellnavn = tabellnavn,
                side = 1,
                nesteIterator = "siste-side",
                rader = emptyList(),
            ),
        )

        fullførtTabell.status shouldBe HistoriskImport.Status.FULLFØRT
        fullførtTabell.importertAntall shouldBe 1
        fullførtTabell.nesteIterator shouldBe null
        fullførtTabell.nesteSide shouldBe 2

        repo.fullførImport(import.id)
        repo.hentPågåendeImport() shouldBe null
    }

    @Test
    fun `sletter fullført import med alle rader og tabeller over flere batcher`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = HistoriskImportPostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics)
        val tabellnavn = InfotrygdTabeller.T_VEDTAK
        val antallRader = 10_001L

        val import = repo.opprettImport(
            listOf(
                NyHistoriskTabellimport(
                    tabellnavn = tabellnavn,
                    forventetAntall = antallRader,
                    kolonner = listOf("ID"),
                ),
            ),
        )
        repo.lagreSide(
            HistoriskRådataSide(
                importId = import.id,
                tabellnavn = tabellnavn,
                side = 0,
                nesteIterator = null,
                rader = (1..antallRader).map { mapOf("ID" to it.toString()) },
            ),
        )
        repo.fullførImport(import.id)

        repo.slettImport(import.id) shouldBe SlettImportResultat.SLETTET

        repo.hentAlleImporter() shouldBe emptyList()
    }

    @Test
    fun `kan ikke slette pågående import`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = HistoriskImportPostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics)
        val tabellnavn = InfotrygdTabeller.T_VEDTAK

        val import = repo.opprettImport(
            listOf(NyHistoriskTabellimport(tabellnavn = tabellnavn, forventetAntall = 1, kolonner = listOf("ID"))),
        )

        repo.slettImport(import.id) shouldBe SlettImportResultat.PÅGÅR

        repo.hentPågåendeImport() shouldNotBe null
    }

    @Test
    fun `hentAlleImporter returnerer kun fullførte og feilede importer med radsummer`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = HistoriskImportPostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics)
        val tabellnavn = InfotrygdTabeller.T_VEDTAK

        val import = repo.opprettImport(
            listOf(NyHistoriskTabellimport(tabellnavn = tabellnavn, forventetAntall = 2, kolonner = listOf("ID"))),
        )
        repo.lagreSide(
            HistoriskRådataSide(
                importId = import.id,
                tabellnavn = tabellnavn,
                side = 0,
                nesteIterator = null,
                rader = listOf(mapOf("ID" to "1"), mapOf("ID" to "2")),
            ),
        )
        repo.fullførImport(import.id)

        val oversikt = repo.hentAlleImporter()

        oversikt shouldHaveSize 1
        oversikt.single().also { o ->
            o.id shouldBe import.id
            o.status shouldBe HistoriskImport.Status.FULLFØRT
            o.fullført shouldNotBe null
            o.feilbeskrivelse shouldBe null
            o.totaltForventetAntall shouldBe 2
            o.totaltImportertAntall shouldBe 2
            o.tabeller shouldHaveSize 1
            o.tabeller.single().also { t ->
                t.tabellnavn shouldBe tabellnavn
                t.forventetAntall shouldBe 2
                t.importertAntall shouldBe 2
            }
        }
    }

    @Test
    fun `hentAlleImporter inkluderer ikke pågående importer`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = HistoriskImportPostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics)

        repo.opprettImport(
            listOf(NyHistoriskTabellimport(tabellnavn = InfotrygdTabeller.T_VEDTAK, forventetAntall = 1, kolonner = listOf("ID"))),
        )

        repo.hentAlleImporter() shouldHaveSize 0
    }
}
