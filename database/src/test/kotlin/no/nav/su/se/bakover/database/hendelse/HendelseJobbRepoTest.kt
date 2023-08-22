package no.nav.su.se.bakover.database.hendelse

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import java.util.UUID

class HendelseJobbRepoTest {

    @Test
    fun `lagrer en hendelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.persisterInstJobbHendelse()
            hentAlle(testDataHelper.sessionFactory.newSessionContext()).size shouldBe 1
        }
    }

    @Test
    fun `lagrer flere hendelser`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.persisterFlereInstJobbHendelser()
            hentAlle(testDataHelper.sessionFactory.newSessionContext()).size shouldBe 2
        }
    }

    @Test
    fun `henter sak-id og hendelses id'er for en gitt jobbnavn og hendelses type`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val lagret = testDataHelper.persisterInstJobbHendelse()
            val hentet = testDataHelper.hendelseJobbRepo.hentSakIdOgHendelseIderForNavnOgType(
                "INSTITUSJON",
                "INSTITUSJONSOPPHOLD",
            )
            hentet shouldBe mapOf(lagret.sakId to listOf(lagret.hendelseId))
        }
    }

    private fun hentAlle(tx: SessionContext): List<Triple<UUID, HendelseId, String>> {
        return tx.withSession {
            """
                select * from hendelse_jobb
            """.trimIndent().hentListe(emptyMap(), it) {
                Triple(it.uuid("id"), HendelseId.fromUUID(it.uuid("hendelseId")), it.string("jobbNavn"))
            }
        }
    }
}
