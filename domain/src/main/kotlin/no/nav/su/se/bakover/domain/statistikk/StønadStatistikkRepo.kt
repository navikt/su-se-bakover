package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkMåned
import java.time.YearMonth

interface StønadStatistikkRepo {
    fun lagreStønadStatistikk(dto: StønadstatistikkDto, sessionContext: SessionContext? = null)
    fun hentHendelserForFnr(fnr: Fnr): List<StønadstatistikkDto>
    fun lagreMånedStatistikk(månedStatistikk: StønadstatistikkMåned)
    fun hentMånedStatistikk(måned: YearMonth): List<StønadstatistikkMåned>
}
