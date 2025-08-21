package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.statistikk.StønadMånedStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkRepo
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.sisteStatistikkPerMåned
import java.time.YearMonth

class StønadStatistikkService(
    private val stønadStatistikkRepo: StønadStatistikkRepo,
    private val månedStatistikkRepo: StønadMånedStatistikkRepo,
) {

    fun lagreHendelse(dto: StønadstatistikkDto) {
        stønadStatistikkRepo.lagreStønadStatistikk(dto)
    }

    fun lagreMånedligStatistikk(måned: YearMonth) {
        stønadStatistikkRepo.hentStatistikkForMåned(måned).let {
            it.sisteStatistikkPerMåned(måned).forEach {
                // TODO assert at det kun er et månedsbeløp
                // TODO skal månedsbeløp dupliseres eller refereres til
                månedStatistikkRepo.lagreMånedStatistikk(it)
            }
        }
    }
}
