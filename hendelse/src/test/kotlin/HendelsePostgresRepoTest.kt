package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.hendelse.sakOpprettetHendelse
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

internal class HendelsePostgresRepoTest {

    @Test
    fun `kan lagre og hente sak opprettet hendelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.hendelsePostgresRepo

            testDataHelper.sessionFactory.withSessionContext {
                val (sak, _) = testDataHelper.persisterSakOgJournalførtSøknadUtenOppgave()
                val hendelse = sakOpprettetHendelse(sakId = sak.id, fnr = sak.fnr)
                repo.persisterHendelse(hendelse, it)
                repo.hentHendelserForSak(sak.id) shouldBe listOf(
                    hendelse,
                )
            }
        }
    }
}
