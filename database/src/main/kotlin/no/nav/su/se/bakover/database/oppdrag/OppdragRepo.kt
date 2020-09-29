package no.nav.su.se.bakover.database.oppdrag

import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import java.util.UUID

interface OppdragRepo {
    fun hentOppdrag(sakId: UUID): Oppdrag?
}
