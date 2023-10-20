package dokument.domain.hendelser

import dokument.domain.DokumentMedMetadataUtenFil

sealed interface GenerertDokumentHendelse : DokumentHendelse {
    val dokumentUtenFil: DokumentMedMetadataUtenFil
}
