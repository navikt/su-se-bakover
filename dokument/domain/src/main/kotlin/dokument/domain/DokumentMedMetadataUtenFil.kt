package dokument.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock
import java.util.UUID

/**
 * TODO - vi burde på mest mulig holde [Dokument.MedMetadata] og denne 1-1, uten at denne bryr seg om PDF'en
 */
data class DokumentMedMetadataUtenFil(
    val id: UUID,
    val opprettet: Tidspunkt,
    val tittel: String,
    val metadata: Dokument.Metadata,
    val distribusjonstype: Distribusjonstype,
    val distribusjonstidspunkt: Distribusjonstidspunkt,
    val generertDokumentJson: String,
) {
    companion object {
        fun Dokument.MedMetadata.tilDokumentUtenFil(clock: Clock): DokumentMedMetadataUtenFil {
            return DokumentMedMetadataUtenFil(
                id = this.id,
                opprettet = Tidspunkt.now(clock),
                tittel = this.tittel,
                metadata = this.metadata,
                distribusjonstype = this.distribusjonstype,
                distribusjonstidspunkt = this.distribusjonstidspunkt,
                generertDokumentJson = this.generertDokumentJson,
            )
        }
    }
}
