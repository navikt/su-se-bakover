package no.nav.su.se.bakover.service.oppdrag

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import java.util.UUID

interface OppdragService {
    fun hentOppdrag(sakId: UUID): Either<FantIkkeOppdrag, Oppdrag>
}

object FantIkkeOppdrag
