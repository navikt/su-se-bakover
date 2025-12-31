package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import toBehandlingsstatistikkOverordnet
import java.time.Clock
import java.util.UUID

class SakStatistikkService(
    private val sakStatistikkRepo: SakStatistikkRepo,
    private val clock: Clock,
) {
    fun lagre(sakId: UUID, hendelse: StatistikkEvent.Behandling) {
        val førsteLinje = sakStatistikkRepo.hentInitiellBehandlingsstatistikk(sakId)
        val statistikk = hendelse.toBehandlingsstatistikkOverordnet(clock, førsteLinje)
        sakStatistikkRepo.lagreSakStatistikk(statistikk)
    }
}
