package dokument.domain

import dokument.domain.DokumentMedMetadataUtenFil.Companion.tilDokumentUtenFil
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.test.dokumentMedMetadataVedtak
import no.nav.su.se.bakover.test.dokumentUtenFil
import org.junit.jupiter.api.Test
import java.util.UUID

class DokumentMedMetadataUtenFilTest {
    @Test
    fun `dokument mappes til DokumentUtenFil korrekt`() {
        dokumentMedMetadataVedtak(
            vedtakId = UUID.randomUUID(),
        ).let {
            it.tilDokumentUtenFil() shouldBe DokumentMedMetadataUtenFil(
                id = it.id,
                opprettet = it.opprettet,
                tittel = it.tittel,
                metadata = it.metadata,
                distribusjonstype = it.distribusjonstype,
                distribusjonstidspunkt = it.distribusjonstidspunkt,
                generertDokumentJson = it.generertDokumentJson,
            )
        }
    }

    @Test
    fun `mapper til korrekt DokumentMedMetadata`() {
        val pdf = PdfA("content".toByteArray())

        dokumentUtenFil().let {
            it.toDokumentMedMetadata(pdf, null, null) shouldBe
                Dokument.MedMetadata.Vedtak(
                    utenMetadata = Dokument.UtenMetadata.Vedtak(
                        id = it.id,
                        opprettet = it.opprettet,
                        tittel = it.tittel,
                        generertDokument = PdfA("content".toByteArray()),
                        generertDokumentJson = it.generertDokumentJson,
                    ),
                    metadata = it.metadata,
                )
        }

        dokumentUtenFil(
            distribusjonstype = Distribusjonstype.VIKTIG,
        ).let {
            it.toDokumentMedMetadata(pdf, null, null) shouldBe
                Dokument.MedMetadata.Informasjon.Viktig(
                    utenMetadata = Dokument.UtenMetadata.Informasjon.Viktig(
                        id = it.id,
                        opprettet = it.opprettet,
                        tittel = it.tittel,
                        generertDokument = pdf,
                        generertDokumentJson = it.generertDokumentJson,
                    ),
                    metadata = it.metadata,
                )
        }

        dokumentUtenFil(
            distribusjonstype = Distribusjonstype.ANNET,
        ).let {
            it.toDokumentMedMetadata(pdf, null, null) shouldBe
                Dokument.MedMetadata.Informasjon.Annet(
                    utenMetadata = Dokument.UtenMetadata.Informasjon.Annet(
                        id = it.id,
                        opprettet = it.opprettet,
                        tittel = it.tittel,
                        generertDokument = PdfA("content".toByteArray()),
                        generertDokumentJson = it.generertDokumentJson,
                    ),
                    metadata = it.metadata,
                )
        }
    }
}
