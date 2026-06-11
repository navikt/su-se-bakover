package no.nav.su.se.bakover.oppgave.infrastructure

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
class OppgaveHendelsePostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `kan lagre en oppgave hendelse & henter alle for en sak`() {
        val testDataHelper = TestDataHelper(dataSource)
        val expected = testDataHelper.persisterOppgaveHendelse()

        testDataHelper.sessionFactory.withSessionContext {
            testDataHelper.oppgaveHendelseRepo.hentForSak(expected.sakId) shouldBe listOf(expected)
        }
    }

    @Test
    fun testHentHendelseForRelatert() {
        val testDataHelper = TestDataHelper(dataSource)
        val expected = testDataHelper.persisterOppgaveHendelse()

        testDataHelper.sessionFactory.withSessionContext {
            testDataHelper.oppgaveHendelseRepo.hentHendelseForRelatert(expected.relaterteHendelser.single(), expected.sakId) shouldBe expected
        }
    }
}
