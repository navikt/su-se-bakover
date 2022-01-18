package no.nav.su.se.bakover.domain.kontrollsamtale

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.erMindreEnnEnMånedSenere
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
    val frist: LocalDate = regnUtFristFraInnkallingsdato(innkallingsdato),
    val dokumentId: UUID?,
) {
    fun oppdater(
        id: UUID = this.id,
        opprettet: Tidspunkt = this.opprettet,
        sakId: UUID = this.sakId,
        innkallingsdato: LocalDate = this.innkallingsdato,
        status: Kontrollsamtalestatus = this.status,
        frist: LocalDate = this.frist,
        dokumentId: UUID? = this.dokumentId,
    ): Kontrollsamtale = Kontrollsamtale(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        innkallingsdato = innkallingsdato,
        status = status,
        frist = frist,
        dokumentId = dokumentId
    )
}

enum class Kontrollsamtalestatus(val value: String) {
    PLANLAGT_INNKALLING("PLANLAGT_INNKALLING"),
    INNKALT("INNKALT"),
    GJENNOMFØRT("GJENNOMFØRT"),
    ANNULLERT("ANNULLERT"),
}

fun regnUtFristFraInnkallingsdato(innkallingsdato: LocalDate): LocalDate = innkallingsdato.endOfMonth()

fun regnUtInnkallingsdato(periode: Periode, vedtaksdato: LocalDate, clock: Clock): LocalDate? {
    val stønadsstart = periode.fraOgMed
    val stønadsslutt = periode.tilOgMed
    val fourMonthsDate = stønadsstart.plusMonths(4).startOfMonth()
    val eightMonthsDate = fourMonthsDate.plusMonths(4)
    val today = LocalDate.now(clock)

    return if (today.between(fourMonthsDate, eightMonthsDate.plusDays(1))) {
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
