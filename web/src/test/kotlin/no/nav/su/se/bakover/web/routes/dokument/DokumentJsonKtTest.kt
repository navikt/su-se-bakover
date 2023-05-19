package no.nav.su.se.bakover.web.routes.dokument

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.dokument.Dokument
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class DokumentJsonKtTest {
    @Test
    fun `serialiserer json`() {
        val expected = """
            {
                "id": "5ea19f74-7363-47ee-a8d7-5abf3a261817",
                "opprettet": "1970-01-01T00:00:00Z",
                "tittel": "vedtak om json",
                "dokument": "ZG9rdW1lbnRldA==",
                "journalført": false,
                "brevErBestilt": false
            }
        """.trimIndent()

        val dokument = Dokument.UtenMetadata.Vedtak(
            id = UUID.fromString("5ea19f74-7363-47ee-a8d7-5abf3a261817"),
            opprettet = Tidspunkt.EPOCH,
            tittel = "vedtak om json",
            generertDokument = "dokumentet".toByteArray(),
            generertDokumentJson = "{}",
        )

        JSONAssert.assertEquals(expected, serialize(dokument.toJson()), true)
    }

    @Test
    fun `serialiserer liste json`() {
        val expected = """
               [
                {
                    "id": "5ea19f74-7363-47ee-a8d7-5abf3a261817",
                    "opprettet": "1970-01-01T00:00:00Z",
                    "tittel": "vedtak om json",
                    "dokument": "ZG9rdW1lbnRldA==",
                    "journalført": false,
                    "brevErBestilt": false
                }
               ]
        """.trimIndent()

        val dokument = Dokument.UtenMetadata.Vedtak(
            id = UUID.fromString("5ea19f74-7363-47ee-a8d7-5abf3a261817"),
            opprettet = Tidspunkt.EPOCH,
            tittel = "vedtak om json",
            generertDokument = "dokumentet".toByteArray(),
            generertDokumentJson = "{}",
        )

        JSONAssert.assertEquals(expected, serialize(listOf(dokument).toJson()), true)
    }
}
