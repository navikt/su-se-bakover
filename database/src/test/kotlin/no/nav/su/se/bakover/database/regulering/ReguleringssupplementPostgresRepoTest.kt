package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.test.nyReguleringssupplement
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

class ReguleringssupplementPostgresRepoTest {

    @Test
    fun `lagrer tom supplement`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.reguleringRepo.lagre(Reguleringssupplement.empty())
            testDataHelper.sessionFactory.withSession {
                """select * from reguleringssupplement""".hent(emptyMap(), it) {
                    val supplement = deserializeList<ReguleringssupplementForJson>(it.string("supplement"))
                    supplement.size shouldBe 0
                }
            }
        }
    }

    @Test
    fun `lagrer supplement med innhold`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.reguleringRepo.lagre(nyReguleringssupplement())
            testDataHelper.sessionFactory.withSession {
                """select * from reguleringssupplement""".hent(emptyMap(), it) {
                    val supplement = deserializeList<ReguleringssupplementForJson>(it.string("supplement"))
                    supplement.size shouldBe 1
                }
            }
        }
    }
}
