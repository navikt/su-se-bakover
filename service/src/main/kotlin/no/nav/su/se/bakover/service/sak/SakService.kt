package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Restans
import java.util.UUID

interface SakService {
    fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak>
    fun hentSak(fnr: Fnr): Either<FantIkkeSak, Sak>
    fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak>
    fun opprettSak(sak: NySak)
    fun hentRestanserForAlleSaker(): List<Restans>
}

object FantIkkeSak
