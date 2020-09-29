package no.nav.su.se.bakover.database.oppdrag

import kotliquery.Row
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuid30
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag

fun Row.toOppdrag(session: Session): Oppdrag {
    val oppdragId = uuid30("id")
    return Oppdrag(
        id = oppdragId,
        opprettet = tidspunkt("opprettet"),
        sakId = uuid("sakId"),
        utbetalinger = UtbetalingInternalRepo.hentUtbetalinger(oppdragId, session)
    )
}
