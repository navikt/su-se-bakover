package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import java.util.UUID

interface SakService {
    fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak>
}

object FantIkkeSak
