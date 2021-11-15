package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import no.nav.su.se.bakover.domain.klage.Klage

interface KlageService {
    fun opprettKlage(klage: Klage): Either<KunneIkkeOppretteKlage, Klage>
}

sealed class KunneIkkeOppretteKlage {
    object FantIkkeSak
}
