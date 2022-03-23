package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.persistertVariant
import no.nav.su.se.bakover.database.withMigratedDb
import org.junit.jupiter.api.Test

internal class ReguleringPostgresRepoTest {

    @Test
    fun `hent reguleringer som ikke er iverksatt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val regulering = testDataHelper.persisterReguleringOpprettet()
            testDataHelper.persisterReguleringOpprettetIverksatt()

            val hentRegulering = repo.hentReguleringerSomIkkeErIverksatt()

            hentRegulering.size shouldBe 1
            hentRegulering.first() shouldBe regulering.persistertVariant()
        }
    }

    @Test
    fun `hent reguleringer for en sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val regulering = testDataHelper.persisterReguleringOpprettet()
            val hentRegulering = repo.hentForSakId(regulering.sakId)

            hentRegulering.size shouldBe 1
            hentRegulering.first() shouldBe regulering.persistertVariant()
        }
    }

    @Test
    fun `lagre og hent en regulering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.reguleringRepo

            val regulering = testDataHelper.persisterReguleringOpprettet()
            val hentRegulering = repo.hent(regulering.id)

            hentRegulering shouldBe regulering.persistertVariant()
        }
    }
}
