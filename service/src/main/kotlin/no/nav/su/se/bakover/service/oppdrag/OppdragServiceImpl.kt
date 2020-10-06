package no.nav.su.se.bakover.service.oppdrag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.oppdrag.OppdragRepo
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import java.util.UUID

internal class OppdragServiceImpl(
    private val repo: OppdragRepo
) : OppdragService {
    override fun hentOppdrag(sakId: UUID): Either<FantIkkeOppdrag, Oppdrag> {
        return repo.hentOppdrag(sakId)?.right() ?: FantIkkeOppdrag.left()
    }
}
