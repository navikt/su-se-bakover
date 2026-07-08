package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface KontrollsamtaleNotatRepo {
    fun lagre(
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        sakId: UUID,
        sessionContext: SessionContext? = null,
    )
}
