package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import java.util.UUID

internal class SakServiceImpl(
    private val sakRepo: SakRepo
) : SakService {
    override fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(sakId)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSak(fnr: Fnr): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(fnr)?.right() ?: FantIkkeSak.left()
    }

    override fun opprettSak(sak: NySak) {
        return sakRepo.opprettSak(sak)
    }
}
