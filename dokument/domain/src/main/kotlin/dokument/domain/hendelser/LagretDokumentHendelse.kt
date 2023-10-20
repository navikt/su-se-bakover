package dokument.domain.hendelser

import dokument.domain.DokumentMedMetadataUtenFil

sealed interface LagretDokumentHendelse : DokumentHendelse {
    val dokumentUtenFil: DokumentMedMetadataUtenFil
}
