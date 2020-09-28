package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje

internal object UtbetalingInternalRepo {
    fun hentUtbetalingInternal(utbetalingId: UUID30, session: Session): Utbetaling? =
        "select * from utbetaling where id = :id".hent(
            mapOf(
                "id" to utbetalingId
            ),
            session
        ) { it.toUtbetaling(session) }

    fun hentUtbetalinger(oppdragId: UUID30, session: Session) =
        "select * from utbetaling where oppdragId=:oppdragId".hentListe(
            mapOf("oppdragId" to oppdragId.toString()),
            session
        ) {
            it.toUtbetaling(session)
        }.toMutableList()

    fun hentUtbetalingslinjer(utbetalingId: UUID30, session: Session): List<Utbetalingslinje> =
        "select * from utbetalingslinje where utbetalingId=:utbetalingId".hentListe(
            mapOf("utbetalingId" to utbetalingId.toString()),
            session
        ) {
            it.toUtbetalingslinje()
        }
}
