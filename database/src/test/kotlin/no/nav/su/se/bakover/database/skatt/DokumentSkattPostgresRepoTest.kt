package no.nav.su.se.bakover.database.skatt

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class DokumentSkattPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer en generert skattedokument `() {
        val testDataHelper = TestDataHelper(dataSource)
        val skattedokument = testDataHelper.persisterSkattedokumentGenerert()
        testDataHelper.databaseRepos.dokumentSkattRepo.hent(skattedokument.id) shouldBe skattedokument
    }

    @Test
    fun `lagrer en jouralført skattedokument `() {
        val testDataHelper = TestDataHelper(dataSource)
        val skattedokument = testDataHelper.persisterSkattedokumentJournalført()
        testDataHelper.databaseRepos.dokumentSkattRepo.hent(skattedokument.id) shouldBe skattedokument
    }

    @Test
    fun `henter dokumenter for journalføring`() {
        val testDataHelper = TestDataHelper(dataSource)
        val generert = testDataHelper.persisterSkattedokumentGenerert()
        testDataHelper.persisterSkattedokumentJournalført()
        testDataHelper.databaseRepos.dokumentSkattRepo.hentDokumenterForJournalføring().let {
            it.size shouldBe 1
            it.first() shouldBe generert
        }
    }
}
