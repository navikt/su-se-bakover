package no.nav.su.se.bakover.service.statistikk

import behandling.domain.Behandling
import no.nav.su.se.bakover.common.domain.auth.Kontekst
import no.nav.su.se.bakover.common.domain.statistikk.StatistikkKode6Kontekst
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import toBehandlingsstatistikkOverordnet
import java.time.Clock

class SakStatistikkService(
    private val sakStatistikkRepo: SakStatistikkRepo,
    private val clock: Clock,
    private val log: Logger = LoggerFactory.getLogger(SakStatistikkService::class.java),
) {
    fun lagre(
        hendelse: StatistikkEvent.Behandling,
        sessionContext: SessionContext?,
        kode6Kontekst: StatistikkKode6Kontekst? = Kontekst.get()?.statistikkKode6,
    ) {
        val behandling: Behandling = when (hendelse) {
            is StatistikkEvent.Behandling.Søknad -> hendelse.søknadsbehandling
            is StatistikkEvent.Behandling.Revurdering -> hendelse.revurdering
            is StatistikkEvent.Behandling.Klage -> hendelse.klage
            is StatistikkEvent.Behandling.Stans -> hendelse.revurdering
            is StatistikkEvent.Behandling.Gjenoppta -> hendelse.revurdering
            is StatistikkEvent.Behandling.Regulering -> hendelse.regulering
            is StatistikkEvent.Behandling.ReguleringOmregning -> hendelse.regulering
        }
        val harKode6 = kode6Kontekst?.harKode6(behandling.fnr) == true
        if (harKode6 && kode6Kontekst != null && !kode6Kontekst.harTilgangTilStrengtFortroligAdresse) {
            log.error("Sakstatistikk har kode 6, men brukeren mangler forventet gruppe for strengt fortrolig adresse")
        }
        val førsteLinje = sakStatistikkRepo.hentInitiellBehandlingsstatistikk(behandling.id, sessionContext)
        val statistikk = hendelse.toBehandlingsstatistikkOverordnet(clock, førsteLinje).let {
            if (harKode6) {
                it.copy(
                    opprettetAv = SKJERMET,
                    ansvarligBeslutter = it.ansvarligBeslutter?.let { SKJERMET },
                    ansvarligEnhet = VIKAFOSSEN_ENHET,
                )
            } else {
                it
            }
        }
        sakStatistikkRepo.lagreSakStatistikk(statistikk, sessionContext)
    }

    private companion object {
        const val SKJERMET = "SKJERMET"
        const val VIKAFOSSEN_ENHET = "2103"
    }
}
