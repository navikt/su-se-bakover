package no.nav.su.se.bakover.database.hendelse

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import java.util.UUID

class HendelsekonsumenterRepoTest {

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
    fun `henter sakId og hendelse ider for hendelser som ikke har kjørt en action`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val lagret = testDataHelper.persisterInstitusjonsoppholdHendelse()
            val hentet = testDataHelper.hendelsekonsumenterRepo.hentUteståendeSakOgHendelsesIderForKonsumentOgType(
                HendelseskonsumentId("INSTITUSJON"),
                Hendelsestype("INSTITUSJONSOPPHOLD"),
            )
            hentet shouldBe mapOf(lagret.sakId to listOf(lagret.hendelseId))
        }
    }

    private fun hentAlle(tx: SessionContext): List<Triple<UUID, HendelseId, String>> {
        return tx.withSession {
            """
                select * from hendelse_konsument
            """.trimIndent().hentListe(emptyMap(), it) {
                Triple(it.uuid("id"), HendelseId.fromUUID(it.uuid("hendelseId")), it.string("konsumentId"))
            }
        }
    }
}
