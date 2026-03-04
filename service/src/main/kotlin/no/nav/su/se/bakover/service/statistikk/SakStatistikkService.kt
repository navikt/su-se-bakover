package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import toBehandlingsstatistikkOverordnet
import java.time.Clock

class SakStatistikkService(
    private val sakStatistikkRepo: SakStatistikkRepo,
    private val clock: Clock,
) {
    fun lagre(hendelse: StatistikkEvent.Behandling, sessionContext: SessionContext?) {
        val behandlingsid = when (hendelse) {
            is StatistikkEvent.Behandling.Søknad -> hendelse.søknadsbehandling.id
            is StatistikkEvent.Behandling.Revurdering -> hendelse.revurdering.id
            is StatistikkEvent.Behandling.Klage -> hendelse.klage.id
            is StatistikkEvent.Behandling.Stans -> hendelse.revurdering.id
            is StatistikkEvent.Behandling.Gjenoppta -> hendelse.revurdering.id
            is StatistikkEvent.Behandling.Regulering -> hendelse.regulering.id
        }
        val førsteLinje = sakStatistikkRepo.hentInitiellBehandlingsstatistikk(behandlingsid, sessionContext)
        val statistikk = hendelse.toBehandlingsstatistikkOverordnet(clock, førsteLinje)
        sakStatistikkRepo.lagreSakStatistikk(statistikk, sessionContext)
    }
}
