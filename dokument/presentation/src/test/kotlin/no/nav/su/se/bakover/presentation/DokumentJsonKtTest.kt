package no.nav.su.se.bakover.presentation

import dokument.domain.Dokument
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.presentation.web.toJson
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
            generertDokument = PdfA("dokumentet".toByteArray()),
            generertDokumentJson = "{}",
        )

        JSONAssert.assertEquals(expected, dokument.toJson(), true)
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
            generertDokument = PdfA("dokumentet".toByteArray()),
            generertDokumentJson = "{}",
        )

        JSONAssert.assertEquals(expected, listOf(dokument).toJson(), true)
    }
}
