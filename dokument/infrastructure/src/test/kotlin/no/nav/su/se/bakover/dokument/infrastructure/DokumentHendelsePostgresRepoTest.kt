package no.nav.su.se.bakover.dokument.infrastructure

import dokument.domain.Dokument
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

class DokumentHendelsePostgresRepoTest {

    @Test
    fun hentVedtaksbrevdatoForSakOgVedtakId() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)
            val(sak, _, _, vedtak, hendelser) = testDataHelper.tilbakekreving.persisterIverksattTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(januar(2021)),
            )
            // TODO jah: Ikke ideelt og måtte bygge opp dette innholdet selv.
            val relaterteHendelse = hendelser.last().hendelseId
            testDataHelper.dokumentHendelse.persisterDokumentHendelse(
                sakId = sak.id,
                relaterteHendelse = relaterteHendelse,
                generertDokumentJson = """{
                     "personalia": {
                       "dato": "2021-02-01"
                     }
                    }
                """.trimIndent(),
                dokumentMetdata = Dokument.Metadata(
                    sakId = sak.id,
                    vedtakId = vedtak.id,
                ),
            )
            testDataHelper.dokumentHendelseRepo.hentVedtaksbrevdatoForSakOgVedtakId(
                sakId = sak.id,
                vedtakId = vedtak.id,
            ) shouldBe 1.februar(2021)
        }
    }
}
