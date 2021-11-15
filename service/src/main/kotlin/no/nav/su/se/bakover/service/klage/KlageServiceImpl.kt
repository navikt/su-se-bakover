package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.database.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.Klage
import java.time.Clock

class KlageServiceImpl(private val klageRepo: KlageRepo, val clock: Clock) : KlageService {
    override fun opprettKlage(klage: Klage): Either<KunneIkkeOppretteKlage, Klage> {
        klageRepo.opprett(klage)

        return klage.right()
    }
}
