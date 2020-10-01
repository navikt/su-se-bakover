package no.nav.su.se.bakover.service.søknad

import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo
) : SøknadService {
    override fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        return søknadRepo.opprettSøknad(sakId, søknad)
    }
}
