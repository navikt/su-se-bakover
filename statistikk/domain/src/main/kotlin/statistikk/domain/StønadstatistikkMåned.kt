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
    val månedsbeløp: StønadstatistikkDto.Månedsbeløp,
)

fun List<StønadstatistikkDto>.sisteStatistikkPerMåned(måned: YearMonth): List<StønadstatistikkMåned> =
    groupBy { it.sakId }.values.map { statistikkLinjerForSak ->
        statistikkLinjerForSak
            .filter { it.vedtaksdato.month == måned.month }
            .maxBy { it.vedtaksdato }.let {
                StønadstatistikkMåned(
                    id = UUID.randomUUID(),
                    måned = måned,
                    vedtaksdato = it.vedtaksdato,
                    personnummer = it.personnummer,
                    månedsbeløp = it.månedsbeløp.single {
                        it.måned == måned.toString()
                    },
                )
            }
    }
