package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.Saksnummer
import java.time.LocalDate
import java.util.UUID

data class SakSomKanReguleres(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val type: String,
)

data class SakerSomKanReguleres(
    val saker: List<SakSomKanReguleres>,
)

object KanIkkeHenteSaker

interface ReguleringService {
    fun hentAlleSakerSomKanReguleres(fraDato: LocalDate): Either<KanIkkeHenteSaker, SakerSomKanReguleres>
    // fun hentAlleSakerSomKanReguleresAutomatisk(fraDato: LocalDate): Either<KanIkkeHenteSaker, List<Saksnummer>>
    // fun hentAlleSakerSomKanReguleresManuelt(fraDato: LocalDate): Either<KanIkkeHenteSaker, List<Saksnummer>>
}
