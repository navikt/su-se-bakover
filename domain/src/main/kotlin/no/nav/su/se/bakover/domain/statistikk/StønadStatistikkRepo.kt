package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.person.Fnr
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkMåned
import java.time.YearMonth

interface StønadStatistikkRepo {
    fun lagreStønadStatistikk(dto: StønadstatistikkDto)
    fun hentHendelserForFnr(fnr: Fnr): List<StønadstatistikkDto>
    fun hentStatistikkForMåned(måned: YearMonth): List<StønadstatistikkDto>
}

interface StønadMånedStatistikkRepo {
    fun hentMånedStatistikk(måned: YearMonth): List<StønadstatistikkMåned>
    fun lagreMånedStatistikk(månedStatistikk: StønadstatistikkMåned)
}
