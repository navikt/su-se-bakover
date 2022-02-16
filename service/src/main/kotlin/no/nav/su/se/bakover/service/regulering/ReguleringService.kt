package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.time.LocalDate
import java.util.UUID

data class SakSomKanReguleres(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val reguleringType: ReguleringType,
)

data class SakerSomKanReguleres(
    val saker: List<SakSomKanReguleres>,
)

interface ReguleringService {
    fun hentAlleSakerSomKanReguleres(fraDato: LocalDate?): SakerSomKanReguleres
}
