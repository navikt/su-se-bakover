package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.statistikk.StatistikkHendelseRepo
import statistikk.domain.StønadstatistikkDto

class StatistikkService(
    private val statistikkHendelseRepo: StatistikkHendelseRepo,
) {
    fun lagreHendelse(dto: StønadstatistikkDto) {
        statistikkHendelseRepo.lagreHendelse(dto)
    }
}
