package no.nav.su.se.bakover.domain.statistikk

import statistikk.domain.StønadstatistikkDto

interface StønadRepo {
    fun lagreHendelse(dto: StønadstatistikkDto)
}
