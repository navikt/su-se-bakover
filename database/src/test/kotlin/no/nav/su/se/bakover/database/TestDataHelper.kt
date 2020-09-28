package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import javax.sql.DataSource

internal class TestDataHelper(
    dataSource: DataSource = EmbeddedDatabase.instance()
) {
    private val repo = DatabaseRepo(dataSource)

    fun insertSak(fnr: Fnr) = repo.opprettSak(fnr)
    fun insertUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling = utbetaling.also { repo.opprettUtbetaling(oppdragId, utbetaling) }
    fun insertOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding) =
        repo.addOppdragsmelding(utbetalingId, oppdragsmelding)

    fun hentUtbetaling(utbetalingId: UUID30) = repo.hentUtbetaling(utbetalingId)
}
