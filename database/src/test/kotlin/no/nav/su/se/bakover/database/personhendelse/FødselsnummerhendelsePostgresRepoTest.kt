package no.nav.su.se.bakover.database.personhendelse

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class FødselsnummerhendelsePostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer og henter ubehandlede fødselsnummerhendelser`() {
        val testDataHelper = TestDataHelper(dataSource, clock = fixedClock)
        val repo = testDataHelper.databaseRepos.fødselsnummerhendelseRepo
        val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first

        repo.lagre(sak.id)

        val hendelser = repo.hentUbehandlede()
        hendelser.size shouldBe 1
        hendelser.single().sakId shouldBe sak.id
        hendelser.single().prosessert shouldBe null
    }

    @Test
    fun `markerer fødselsnummerhendelse som prosessert`() {
        val testDataHelper = TestDataHelper(dataSource, clock = fixedClock)
        val repo = testDataHelper.databaseRepos.fødselsnummerhendelseRepo
        val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
        repo.lagre(sak.id)
        val hendelse = repo.hentUbehandlede().single()

        testDataHelper.sessionFactory.withTransactionContext { tx ->
            repo.markerProsessert(id = hendelse.id, tidspunkt = fixedTidspunkt, transactionContext = tx)
        }

        repo.hentUbehandlede() shouldBe emptyList()
    }
}
