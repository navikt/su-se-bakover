package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.statistikk.StønadRepo
import statistikk.domain.StønadstatistikkDto

class StønadService(
    private val stønadRepo: StønadRepo,
) {
    fun lagreHendelse(dto: StønadstatistikkDto) {
        stønadRepo.lagreHendelse(dto)
    }
}
