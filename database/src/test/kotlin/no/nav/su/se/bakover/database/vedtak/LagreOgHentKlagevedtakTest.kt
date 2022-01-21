package no.nav.su.se.bakover.database.vedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import org.junit.jupiter.api.Test

internal class LagreOgHentKlagevedtakTest {
    @Test
    fun `kan lagre og hente klagevedtak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo
            val vedtak = testDataHelper.vedtakForIverksattAvvistKlage()

            dataSource.withSession {
                vedtakRepo.hent(vedtak.id, it) shouldBe vedtak
            }
        }
    }
}
