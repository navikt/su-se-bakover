package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.persistence.TransactionContext
import statistikk.domain.StønadstatistikkMåned
import java.time.YearMonth

interface StønadStatistikkRepo {
    fun lagreMånedStatistikk(månedStatistikk: StønadstatistikkMåned, tx: TransactionContext? = null)
    fun hentStatistikkForMåned(måned: YearMonth): List<StønadstatistikkMåned>
    fun hentStatistikkForPeriode(fraOgMed: YearMonth, tilOgMed: YearMonth): List<StønadstatistikkMåned>
}
