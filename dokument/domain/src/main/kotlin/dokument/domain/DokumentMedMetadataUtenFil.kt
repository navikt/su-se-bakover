package dokument.domain

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * TODO - vi burde på mest mulig holde [Dokument.MedMetadata] og denne 1-1, uten at denne bryr seg om PDF'en
 *
 * Hensikten med denne klassen er å ha et Dokument objekt, som kan lett flyte gjennom systemet, uten å måtte dra en stor blob (byteArrayet) - Per dags dato, brukes den for hendelser
 * Paramsene i denne klassen skal være en kopi av det originale dokumentet, men der PDFen er strippet ut.
 */
data class DokumentMedMetadataUtenFil(
    val id: UUID,
    val opprettet: Tidspunkt,
    val tittel: String,
    val metadata: Dokument.Metadata,
    val distribusjonstype: Distribusjonstype,
    val distribusjonstidspunkt: Distribusjonstidspunkt,
    // TODO - denne kan sikkert sikkert flyttes til HendelseFil sammen med bytearrayet
    val generertDokumentJson: String,
) {

    fun toDokumentMedMetadata(pdf: PdfA): Dokument.MedMetadata = when (distribusjonstype) {
        Distribusjonstype.VEDTAK -> Dokument.MedMetadata.Vedtak(
            utenMetadata = toDokumentUtenMetadata(pdf),
            metadata = metadata,
        )

        Distribusjonstype.VIKTIG -> Dokument.MedMetadata.Informasjon.Viktig(
            utenMetadata = toDokumentUtenMetadata(pdf),
            metadata = metadata,
        )

        Distribusjonstype.ANNET -> Dokument.MedMetadata.Informasjon.Annet(
            utenMetadata = toDokumentUtenMetadata(pdf),
            metadata = metadata,
        )
    }

    private fun toDokumentUtenMetadata(pdf: PdfA): Dokument.UtenMetadata = when (distribusjonstype) {
        Distribusjonstype.VEDTAK -> Dokument.UtenMetadata.Vedtak(
            id = id,
            opprettet = opprettet,
            tittel = tittel,
            generertDokument = pdf,
            generertDokumentJson = generertDokumentJson,
        )

        Distribusjonstype.VIKTIG -> Dokument.UtenMetadata.Informasjon.Viktig(
            id = id,
            opprettet = opprettet,
            tittel = tittel,
            generertDokument = pdf,
            generertDokumentJson = generertDokumentJson,
        )

        Distribusjonstype.ANNET -> Dokument.UtenMetadata.Informasjon.Annet(
            id = id,
            opprettet = opprettet,
            tittel = tittel,
            generertDokument = pdf,
            generertDokumentJson = generertDokumentJson,
        )
    }

    companion object {
        /**
         * Bevarer dataen fra det originale dokumentet, uten selve PDF'en
         */
        fun Dokument.MedMetadata.tilDokumentUtenFil(): DokumentMedMetadataUtenFil {
            return DokumentMedMetadataUtenFil(
                id = this.id,
                opprettet = this.opprettet,
                tittel = this.tittel,
                metadata = this.metadata,
                distribusjonstype = this.distribusjonstype,
                distribusjonstidspunkt = this.distribusjonstidspunkt,
                generertDokumentJson = this.generertDokumentJson,
            )
        }
    }
}
