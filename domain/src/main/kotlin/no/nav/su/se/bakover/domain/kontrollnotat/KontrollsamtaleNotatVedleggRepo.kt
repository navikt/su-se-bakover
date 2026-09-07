package no.nav.su.se.bakover.domain.kontrollnotat

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface KontrollsamtaleNotatVedleggRepo {
    fun lagre(
        vedlegg: KontrollsamtaleNotatVedlegg,
        sessionContext: SessionContext? = null,
    )

    fun hentForKontrollsamtale(
        kontrollsamtaleId: UUID,
        sessionContext: SessionContext? = null,
    ): List<KontrollsamtaleNotatVedlegg>

    fun slett(
        vedleggId: UUID,
        sessionContext: SessionContext? = null,
    )
}
