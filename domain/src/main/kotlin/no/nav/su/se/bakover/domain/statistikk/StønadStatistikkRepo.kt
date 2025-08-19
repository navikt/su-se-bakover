package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.person.Fnr
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkMåned
import java.time.YearMonth

interface StønadStatistikkRepo {
    fun lagreStønadStatistikk(dto: StønadstatistikkDto)
    fun hentHendelserForFnr(fnr: Fnr): List<StønadstatistikkDto>
}

interface StønadMånedStatistikkRepo {
    fun hentHendelserForMåned(måned: YearMonth): List<StønadstatistikkDto>
    fun lagreOppsummertMåned(statistikk: List<StønadstatistikkMåned>)
}
