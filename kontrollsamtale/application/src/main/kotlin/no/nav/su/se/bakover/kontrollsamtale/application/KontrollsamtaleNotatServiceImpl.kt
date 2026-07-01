package no.nav.su.se.bakover.kontrollsamtale.application

import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleNotat
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleNotatRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleNotatService

class KontrollsamtaleNotatServiceImpl(
    private val repository: KontrollsamtaleNotatRepo,
) : KontrollsamtaleNotatService {
    override fun lagre(
        kontrollsamtaleNotat: KontrollsamtaleNotat,
    ) {
        repository.lagre(
            kontrollsamtaleNotat = kontrollsamtaleNotat,
        )
    }
}
