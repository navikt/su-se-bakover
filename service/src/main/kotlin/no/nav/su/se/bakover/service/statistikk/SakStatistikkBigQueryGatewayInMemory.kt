package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import java.util.concurrent.ConcurrentHashMap

class SakStatistikkBigQueryGatewayInMemory : SakStatistikkBigQueryGateway {
    private val rader = ConcurrentHashMap<Long, SakStatistikk>()

    override fun hentAntallRaderPerSekvensId(sekvensIder: Set<Long>): Map<Long, Long> =
        sekvensIder.filter(rader::containsKey).associateWith { 1L }

    override fun deleteExactlyOrVerifyMissing(sekvensIder: Set<Long>) {
        sekvensIder.forEach { rader.remove(it) }
    }

    override fun writeToBigQuery(data: List<SakStatistikk>) {
        data.forEach { rader[it.getSekvensId().longValueExact()] = it }
    }

    override fun verifyExactlyOnce(sekvensIder: Set<Long>) {
        check(sekvensIder.all(rader::containsKey)) {
            "In-memory BigQuery mangler forventede sekvens-ID-er"
        }
    }
}
