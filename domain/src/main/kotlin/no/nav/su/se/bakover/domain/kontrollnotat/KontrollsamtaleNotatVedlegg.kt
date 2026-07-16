package no.nav.su.se.bakover.domain.kontrollnotat

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

data class KontrollsamtaleNotatVedlegg(
    val id: UUID,
    val kontrollsamtaleNotatId: UUID,
    val filnavn: String,
    val mimeType: String,
    val innhold: ByteArray,
    val opprettet: Tidspunkt,
)
