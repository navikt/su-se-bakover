package no.nav.su.se.bakover.domain.kontrollsamtale

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.erMindreEnnEnMånedSenere
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class Kontrollsamtale(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val innkallingsdato: LocalDate,
    val status: Kontrollsamtalestatus,
    val frist: LocalDate = regnUtFristFraInnkallingsdato(innkallingsdato),
    val dokumentId: UUID?,
) {

    fun settInnkalt(dokumentId: UUID): Either<UgyldigStatusovergang, Kontrollsamtale> {
        if (this.status != Kontrollsamtalestatus.PLANLAGT_INNKALLING) return UgyldigStatusovergang.left()
        return this.copy(
            status = Kontrollsamtalestatus.INNKALT,
            dokumentId = dokumentId
        ).right()
    }

    fun annuller(): Either<UgyldigStatusovergang, Kontrollsamtale> =
        if (this.status == Kontrollsamtalestatus.PLANLAGT_INNKALLING || this.status == Kontrollsamtalestatus.INNKALT) {
            this.copy(status = Kontrollsamtalestatus.ANNULLERT).right()
        } else {
            UgyldigStatusovergang.left()
        }

    fun endreDato(innkallingsdato: LocalDate): Either<UgyldigStatusovergang, Kontrollsamtale> {
        if (this.status != Kontrollsamtalestatus.PLANLAGT_INNKALLING) return UgyldigStatusovergang.left()
        return this.copy(innkallingsdato = innkallingsdato, frist = regnUtFristFraInnkallingsdato(innkallingsdato)).right()
    }

    companion object {
        fun opprettNyKontrollsamtaleFraVedtak(
            vedtak: VedtakSomKanRevurderes.EndringIYtelse,
            clock: Clock,
        ): Either<SkalIkkeOppretteKontrollsamtale, Kontrollsamtale> =
            regnUtInnkallingsdato(vedtak.periode, vedtak.opprettet.toLocalDate(zoneIdOslo), clock).rightIfNotNull {
                SkalIkkeOppretteKontrollsamtale
            }.map {
                Kontrollsamtale(
                    sakId = vedtak.behandling.sakId,
                    innkallingsdato = it,
                    status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
                    dokumentId = null,
                    opprettet = Tidspunkt.now(clock)
                )
            }

        fun opprettNyKontrollsamtale(
            sakId: UUID,
            innkallingsdato: LocalDate,
            clock: Clock,
        ) = Kontrollsamtale(
            sakId = sakId,
            innkallingsdato = innkallingsdato,
            status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
            dokumentId = null,
            opprettet = Tidspunkt.now(clock),
        )

        fun opprettNyKontrollsamtale(
            gjeldendeStønadsperiode: Periode,
            sakId: UUID,
            clock: Clock,
        ): Either<SkalIkkeOppretteKontrollsamtale, Kontrollsamtale> =
            regnUtInnkallingsdatoOm4Mnd(gjeldendeStønadsperiode.tilOgMed, LocalDate.now(clock)).rightIfNotNull {
                SkalIkkeOppretteKontrollsamtale
            }.map {
                Kontrollsamtale(
                    sakId = sakId,
                    innkallingsdato = it,
                    status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
                    dokumentId = null,
                    opprettet = Tidspunkt.now(clock),
                )
            }
    }
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

object UgyldigStatusovergang
object SkalIkkeOppretteKontrollsamtale
