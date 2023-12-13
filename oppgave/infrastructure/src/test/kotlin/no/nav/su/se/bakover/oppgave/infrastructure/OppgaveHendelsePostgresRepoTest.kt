package no.nav.su.se.bakover.oppgave.infrastructure

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

class OppgaveHendelsePostgresRepoTest {

    @Test
    fun `kan lagre en oppgave hendelse & henter alle for en sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val expected = testDataHelper.persisterOppgaveHendelse()

            testDataHelper.sessionFactory.withSessionContext {
                testDataHelper.oppgaveHendelseRepo.hentForSak(expected.sakId) shouldBe listOf(expected)
            }
        }
    }

    @Test
    fun testHentHendelseForRelatert() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val expected = testDataHelper.persisterOppgaveHendelse()

            testDataHelper.sessionFactory.withSessionContext {
                testDataHelper.oppgaveHendelseRepo.hentHendelseForRelatert(expected.relaterteHendelser.single(), expected.sakId) shouldBe expected
            }
        }
    }
}
