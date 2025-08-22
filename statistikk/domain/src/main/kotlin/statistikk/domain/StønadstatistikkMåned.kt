package statistikk.domain

import no.nav.su.se.bakover.common.person.Fnr
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class StønadstatistikkMåned(
    val id: UUID,
    val måned: YearMonth,
    val vedtaksdato: LocalDate,
    val personnummer: Fnr,
    val gjeldendeStonadUtbetalingsstart: LocalDate,
    val gjeldendeStonadUtbetalingsstopp: LocalDate,
    // val månedsbeløp: StønadstatistikkDto.Månedsbeløp,
)
