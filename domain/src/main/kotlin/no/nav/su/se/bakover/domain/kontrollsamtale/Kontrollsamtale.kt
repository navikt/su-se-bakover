package no.nav.su.se.bakover.domain.kontrollsamtale

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class Kontrollsamtale(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = Tidspunkt.now(),
    val sakId: UUID,
    val innkallingsdato: LocalDate,
    val status: Kontrollsamtalestatus,
    val frist: LocalDate,
    val dokumentId: UUID?,
)

enum class Kontrollsamtalestatus(val value: String) {
    PLANLAGT_INNKALLING("PLANLAGT_INNKALLING"),
    INNKALT("INNKALT"),
    GJENNOMFØRT("GJENNOMFØRT"),
    ANNULLERT("ANNULLERT"),
}

fun regnUtFristFraInnkallingsdato(innkallingsdato: LocalDate): LocalDate = innkallingsdato.endOfMonth()

private fun LocalDate.erMindreEnnEnMånedSenere(localDate: LocalDate) = this.isBefore(localDate.plusMonths(1))

fun regnUtInnkallingsdato(periode: Periode, vedtaksdato: LocalDate, clock: Clock): LocalDate? {
    val stønadsstart = periode.fraOgMed
    val stønadsslutt = periode.tilOgMed
    val fourMonthsDate = stønadsstart.plusMonths(4).startOfMonth()
    val eightMonthsDate = fourMonthsDate.plusMonths(4)
    val today = LocalDate.now(clock)

    return if (eightMonthsDate.isAfter(today) && !fourMonthsDate.isAfter(today)) {
        when {
            stønadsslutt.erMindreEnnEnMånedSenere(eightMonthsDate) -> null
            eightMonthsDate.erMindreEnnEnMånedSenere(vedtaksdato.endOfMonth()) -> {
                val nineMonthsDate = eightMonthsDate.plusMonths(1)
                if (stønadsslutt.erMindreEnnEnMånedSenere(nineMonthsDate)) null else nineMonthsDate
            }
            else -> eightMonthsDate
        }
    } else if (fourMonthsDate.isAfter(today))
        when {
            stønadsslutt.erMindreEnnEnMånedSenere(fourMonthsDate) -> null
            fourMonthsDate.erMindreEnnEnMånedSenere(vedtaksdato.endOfMonth()) -> {
                val fiveMonthsDate = fourMonthsDate.plusMonths(1)
                if (stønadsslutt.erMindreEnnEnMånedSenere(fiveMonthsDate)) null else fiveMonthsDate
            }
            else -> fourMonthsDate
        }
    else null
}

fun regnUtInnkallingsdatoOm4Mnd(stønadsslutt: LocalDate, fraDato: LocalDate): LocalDate? {
    val innkallingsdato = fraDato.startOfMonth().plusMonths(4)
    return if (stønadsslutt.erMindreEnnEnMånedSenere(innkallingsdato.endOfMonth())) null else innkallingsdato
}
