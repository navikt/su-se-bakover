package no.nav.su.se.bakover.database.utbetaling

import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import javax.sql.DataSource

internal class UtbetalingPostgresRepo(
    private val dataSource: DataSource
) : UtbetalingRepo {
    override fun hentUtbetaling(utbetalingId: UUID30): Utbetaling? =
        using(sessionOf(dataSource)) { session -> UtbetalingInternalRepo.hentUtbetalingInternal(utbetalingId, session) }

    override fun oppdaterMedKvittering(utbetalingId: UUID30, kvittering: Kvittering): Utbetaling {
        "update utbetaling set kvittering = to_json(:kvittering::json) where id = :id".oppdatering(
            mapOf(
                "id" to utbetalingId,
                "kvittering" to objectMapper.writeValueAsString(kvittering)
            )
        )
        return hentUtbetaling(utbetalingId)!!
    }

    private fun String.oppdatering(params: Map<String, Any?>) {
        using(sessionOf(dataSource)) {
            this.oppdatering(params, it)
        }
    }
}
