package no.nav.su.se.bakover.database.skatt

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

internal class DokumentSkattPostgresRepoTest {

    @Test
    fun `lagrer en generert skattedokument `() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val skattedokument = testDataHelper.persisterSkattedokumentGenerert()
            testDataHelper.databaseRepos.dokumentSkattRepo.hent(skattedokument.id) shouldBe skattedokument
        }
    }

    @Test
    fun `lagrer en jouralført skattedokument `() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val skattedokument = testDataHelper.persisterSkattedokumentJournalført()
            testDataHelper.databaseRepos.dokumentSkattRepo.hent(skattedokument.id) shouldBe skattedokument
        }
    }

    @Test
    fun `henter dokumenter for journalføring`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val generert = testDataHelper.persisterSkattedokumentGenerert()
            testDataHelper.databaseRepos.dokumentSkattRepo.hentDokumenterForJournalføring().let {
                it.size shouldBe 1
                it.first() shouldBe generert
            }
        }
    }
}
