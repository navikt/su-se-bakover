package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.BehandlingType
import java.time.LocalDate
import java.util.UUID

data class SakSomKanReguleres(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val type: BehandlingType,
)

data class SakerSomKanReguleres(
    val saker: List<SakSomKanReguleres>,
)

object KanIkkeHenteSaker

interface ReguleringService {
    fun hentAlleSakerSomKanReguleres(fraDato: LocalDate): Either<KanIkkeHenteSaker, SakerSomKanReguleres>
}
