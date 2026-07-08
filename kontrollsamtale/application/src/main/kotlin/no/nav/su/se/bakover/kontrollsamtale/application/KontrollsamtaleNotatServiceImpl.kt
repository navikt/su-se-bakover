package no.nav.su.se.bakover.kontrollsamtale.application

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleNotat
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleNotatRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleNotatService
import java.util.UUID

class KontrollsamtaleNotatServiceImpl(
    private val repository: KontrollsamtaleNotatRepo,
) : KontrollsamtaleNotatService {
    override fun lagre(
        sakId: UUID,
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        sessionContext: SessionContext?,
    ) {
        repository.lagre(
            kontrollsamtaleNotat = kontrollsamtaleNotat,
            sakId = sakId,
        )
    }
}
