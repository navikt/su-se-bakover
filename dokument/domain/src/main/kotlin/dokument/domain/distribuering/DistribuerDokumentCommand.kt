package dokument.domain.distribuering

import java.util.UUID

data class DistribuerDokumentCommand(
    val sakId: UUID,
    val dokumentId: UUID,
    val distribueringsadresse: Distribueringsadresse,
)
