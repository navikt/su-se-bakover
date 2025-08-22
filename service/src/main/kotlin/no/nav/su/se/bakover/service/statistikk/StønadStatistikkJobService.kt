package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkRepo
import java.time.YearMonth

interface StønadStatistikkJobService {
    fun lagMånedligStønadstatistikk()
}

class StønadStatistikkJobServiceImpl(
    private val stønadStatistikkRepo: StønadStatistikkRepo,
) : StønadStatistikkJobService {

    override fun lagMånedligStønadstatistikk() {
        val måned = YearMonth.now().minusMonths(1)
        // TODO egen tabell som sier om jobb er kjørt istedenfor å utlede fra statistikk tabell?
        val harKjørt = stønadStatistikkRepo.hentMånedStatistikk(måned).isNotEmpty()
        if (!harKjørt) {
            stønadStatistikkRepo.hentOgLagreStatistikkForMåned(måned)
        }
    }
}
