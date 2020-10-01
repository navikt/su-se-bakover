package no.nav.su.se.bakover.database.sak

import kotliquery.using
import no.nav.su.se.bakover.database.sak.SakRepoInternal.hentSakInternal
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.domain.Fnr
import java.util.UUID
import javax.sql.DataSource

internal class SakPostgresRepo(
    private val dataSource: DataSource
) : SakRepo {
    override fun hentSak(sakId: UUID) = using(sessionOf(dataSource)) { hentSakInternal(sakId, it) }
    override fun hentSak(fnr: Fnr) = using(sessionOf(dataSource)) { hentSakInternal(fnr, it) }
}
