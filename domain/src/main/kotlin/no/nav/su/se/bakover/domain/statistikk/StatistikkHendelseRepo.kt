package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.person.Fnr
import statistikk.domain.StønadstatistikkDto

interface StatistikkHendelseRepo {
    fun lagreHendelse(dto: StønadstatistikkDto)
    fun hentHendelserForFnr(fnr: Fnr): List<StønadstatistikkDto>
}
