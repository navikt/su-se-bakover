package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.persistence.TransactionContext
import statistikk.domain.StønadstatistikkMåned
import java.time.YearMonth
import java.util.UUID

interface StønadStatistikkRepo {
    fun lagreMånedStatistikk(månedStatistikk: StønadstatistikkMåned, tx: TransactionContext? = null)
    fun lagreMånedStatistikk(månedStatistikk: List<StønadstatistikkMåned>, tx: TransactionContext? = null)
    fun hentStatistikkForMåned(måned: YearMonth): List<StønadstatistikkMåned>
    fun hentStatistikkForPeriode(fraOgMed: YearMonth, tilOgMed: YearMonth): List<StønadstatistikkMåned>

    /** Lettvekts-sjekk (count) på om det finnes statistikk for [måned]. Unngår å hydrere alle rader. */
    fun harStatistikkForMåned(måned: YearMonth): Boolean

    /**
     * Henter sakId-ene som har minst én rad for [måned] som enda ikke er sendt til BigQuery
     * (sendt_bigquery is null). Lettvekts (kun id-er) slik at draineren kan chunke og hente
     * radene per sak-batch – på samme måte som genereringen henter sakIder før vedtak.
     */
    fun hentUsendtSakIderForMåned(måned: YearMonth, tx: TransactionContext? = null): List<UUID>

    /**
     * Henter usendte rader (sendt_bigquery is null) for [måned] begrenset til [sakIder]. Brukes
     * sammen med [hentUsendtSakIderForMåned] for å sende statistikk i porsjoner.
     */
    fun hentUsendtForMånedForSaker(måned: YearMonth, sakIder: List<UUID>, tx: TransactionContext? = null): List<StønadstatistikkMåned>

    /**
     * Markerer radene med [ider] som sendt til BigQuery. Kalles først etter at en batch er bekreftet
     * sendt, slik at en feilet oversendelse etterlater radene som usendte og de plukkes opp på nytt
     * ved neste kjøring.
     */
    fun markerSomSendt(ider: List<UUID>, tx: TransactionContext? = null)
}
