package no.nav.su.se.bakover.database.statistikk

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.statistikk.StønadMånedStatistikkRepo
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkMåned
import java.time.YearMonth

class StønadMånedStatistikkRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : StønadMånedStatistikkRepo {
    override fun hentHendelserForMåned(måned: YearMonth): List<StønadstatistikkDto> {
        TODO("Not yet implemented")
    }

    override fun lagreOppsummertMåned(statistikk: List<StønadstatistikkMåned>) {
        TODO("Not yet implemented")
    }
}
