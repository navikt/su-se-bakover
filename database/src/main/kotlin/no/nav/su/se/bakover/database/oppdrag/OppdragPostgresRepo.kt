package no.nav.su.se.bakover.database.oppdrag

import no.nav.su.se.bakover.database.oppdrag.OppdragRepoInternal.hentOppdragForSak
import no.nav.su.se.bakover.database.withSession
import java.util.UUID
import javax.sql.DataSource

internal class OppdragPostgresRepo(
    private val dataSource: DataSource
) : OppdragRepo {
    override fun hentOppdrag(sakId: UUID) = dataSource.withSession { hentOppdragForSak(sakId, it) }
}
