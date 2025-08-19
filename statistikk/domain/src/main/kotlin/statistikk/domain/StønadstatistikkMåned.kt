package statistikk.domain

import no.nav.su.se.bakover.common.person.Fnr
import java.time.LocalDate
import java.time.YearMonth

data class StønadstatistikkMåned(
    val måned: YearMonth,
    val vedtaksdato: LocalDate,
    val personnummer: Fnr,
)

fun List<StønadstatistikkDto>.sisteStatistikkPerMåned(måned: YearMonth): List<StønadstatistikkMåned> =
    groupBy { it.sakId }.values.map { statistikkLinjerForSak ->
        statistikkLinjerForSak.maxBy { it.vedtaksdato }.let {
            StønadstatistikkMåned(
                måned = måned,
                vedtaksdato = it.vedtaksdato,
                personnummer = it.personnummer,
            )
        }
    }
