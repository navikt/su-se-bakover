package no.nav.su.se.bakover.dokument.infrastructure.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

class GenerertDokumentHendelseDbJsonTest {

    @Test
    fun `ser des`() {
        GenerertDokumentHendelseDbJson(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            distribusjonstype = DistribusjonstypeDbJson.VEDTAK,
            distribusjonstidspunkt = DistribusjonstidspunktDbJson.KJERNETID,
            tittel = "tittel",
            generertDokumentJson = """{"personalia": {"dato": "2021-01-01"}}""",
            relaterteHendelse = UUID.randomUUID().toString(),
            dokumentMeta = DokumentMetaDataDbJson(
                sakId = UUID.randomUUID(),
                tilbakekrevingsbehandlingId = UUID.randomUUID(),
                vedtakId = UUID.randomUUID(),
                journalpostId = null,
                brevbestillingsId = null,
            ),
            skalSendeBrev = true,
        ).also {
            val json = serialize(it)
            val deserialized = deserialize<GenerertDokumentHendelseDbJson>(json)
            it shouldBe deserialized
        }
    }
}
