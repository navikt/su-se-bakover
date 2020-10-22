package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

internal class SøknadKanLukkes(
    private val søknadRepo: SøknadRepo
) {
    fun kanLukkes(søknadId: UUID): Either<KunneIkkeLukkeSøknad, Søknad> {
        val søknad = søknadRepo.hentSøknad(søknadId) ?: return KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
        return when {
            søknad.erLukket() -> KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
            søknadRepo.harSøknadPåbegyntBehandling(søknadId) -> KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
            else -> søknad.right()
        }
    }
}
