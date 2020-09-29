package no.nav.su.se.bakover.database.oppdrag

import kotliquery.using
import no.nav.su.se.bakover.database.oppdrag.OppdragRepoInternal.hentOppdragForSak
import no.nav.su.se.bakover.database.sessionOf
import java.util.UUID
import javax.sql.DataSource

internal class OppdragPostgresRepo(
    private val dataSource: DataSource
) : OppdragRepo {
    override fun hentOppdrag(sakId: UUID) = using(sessionOf(dataSource)) { hentOppdragForSak(sakId, it) }
}
