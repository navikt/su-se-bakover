package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk

interface SakStatistikkBigQueryGateway {
    fun hentAntallRaderPerSekvensId(sekvensIder: Set<Long>): Map<Long, Long>
    fun deleteExactlyOrVerifyMissing(sekvensIder: Set<Long>)
    fun writeToBigQuery(data: List<SakStatistikk>)
    fun verifyExactlyOnce(sekvensIder: Set<Long>)
}
