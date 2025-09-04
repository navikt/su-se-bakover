package no.nav.su.se.bakover.database.statistikk

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import statistikk.domain.SakStatistikk
import java.util.UUID

class SakStatistikkRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : SakStatistikkRepo {
    override fun lagreSakStatistikk(behandlingstatistikk: SakStatistikk) {
        TODO()
    }

    override fun hentSakStatistikk(sakId: UUID): List<SakStatistikk> {
        TODO("Not yet implemented")
    }
}
