package no.nav.su.se.bakover.client.journalfør

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.DokumentVariant
import org.junit.jupiter.api.Test

internal class JournalpostCommandTest {
    @Test
    fun `dokumentvariant inneholder korrekte verdier`() {
        DokumentVariant.ArkivPDF("doc").let {
            it.filtype shouldBe "PDFA"
            it.fysiskDokument shouldBe "doc"
            it.variantformat shouldBe "ARKIV"
        }

        DokumentVariant.OriginalJson("doc").let {
            it.filtype shouldBe "JSON"
            it.fysiskDokument shouldBe "doc"
            it.variantformat shouldBe "ORIGINAL"
        }
    }
}
