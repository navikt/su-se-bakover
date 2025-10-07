package no.nav.su.se.bakover.domain.statistikk

import statistikk.domain.StønadstatistikkMåned
import java.time.YearMonth

interface StønadStatistikkRepo {
    fun lagreMånedStatistikk(månedStatistikk: StønadstatistikkMåned)
    fun hentMånedStatistikk(måned: YearMonth): List<StønadstatistikkMåned>
}
