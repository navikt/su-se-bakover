package no.nav.su.se.bakover.database.oppdrag

import kotliquery.Row
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuid30
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import java.util.UUID

internal object OppdragRepoInternal {
    fun hentOppdragForSak(sakId: UUID, session: Session) =
        "select * from oppdrag where sakId=:sakId".hent(mapOf("sakId" to sakId), session) {
            it.toOppdrag(session)
        }
}

fun Row.toOppdrag(session: Session): Oppdrag {
    val oppdragId = uuid30("id")
    return Oppdrag(
        id = oppdragId,
        opprettet = tidspunkt("opprettet"),
        sakId = uuid("sakId"),
        utbetalinger = UtbetalingInternalRepo.hentUtbetalinger(oppdragId, session)
    )
}
