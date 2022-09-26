package no.nav.su.se.bakover.database.vedtak

import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.test.klage.shouldBeEqualComparingPublicFieldsAndInterface
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import org.junit.jupiter.api.Test

internal class LagreOgHentKlagevedtakTest {
    @Test
    fun `kan lagre og hente klagevedtak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            val vedtak = testDataHelper.persisterVedtakForKlageIverksattAvvist()

            dataSource.withSession {
                (vedtakRepo.hent(vedtak.id, it) as Klagevedtak.Avvist).shouldBeEqualComparingPublicFieldsAndInterface(vedtak)
            }
        }
    }
}
