package no.nav.su.se.bakover.database.utbetaling

import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import javax.sql.DataSource

internal class UtbetalingPostgresRepo(
    private val dataSource: DataSource
) : UtbetalingRepo {
    override fun hentUtbetaling(utbetalingId: UUID30): Utbetaling? =
        using(sessionOf(dataSource)) { session -> UtbetalingInternalRepo.hentUtbetalingInternal(utbetalingId, session) }
}
