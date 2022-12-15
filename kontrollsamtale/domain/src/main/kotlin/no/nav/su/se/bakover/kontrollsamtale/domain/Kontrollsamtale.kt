package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.erFørsteDagIMåned
import no.nav.su.se.bakover.common.erMindreEnnEnMånedSenere
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.util.UUID

data class Kontrollsamtale(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val innkallingsdato: LocalDate,
    val status: Kontrollsamtalestatus,
    val frist: LocalDate = regnUtFristFraInnkallingsdato(innkallingsdato),
    val dokumentId: UUID?,
    val journalpostIdKontrollnotat: JournalpostId?,
) {

    fun settInnkalt(dokumentId: UUID): Either<UgyldigStatusovergang, Kontrollsamtale> {
        if (this.status != Kontrollsamtalestatus.PLANLAGT_INNKALLING) return UgyldigStatusovergang.left()
        return this.copy(
            status = Kontrollsamtalestatus.INNKALT,
            dokumentId = dokumentId,
        ).right()
    }

    fun annuller(): Either<UgyldigStatusovergang, Kontrollsamtale> {
        return when (this.status) {
            Kontrollsamtalestatus.PLANLAGT_INNKALLING,
            // TODO jah: Dersom vi har kalt inn en bruker med brev og annullerer, så bør vi kanskje sende et nytt brev som forklarer dette.
            //  Logikken finnes kanskje en annen plass nå, men bør bo nærmere domenet.
            Kontrollsamtalestatus.INNKALT,
            -> this.copy(status = Kontrollsamtalestatus.ANNULLERT).right()
            Kontrollsamtalestatus.GJENNOMFØRT,
            // TODO jah: idempotent? Kan kanskje bare returnere `this`her? Evt. en custom left som wrapper this. Eller en Ior.
            Kontrollsamtalestatus.ANNULLERT,
            // TODO jah: I praksis vil kanskje en kontrollsamtale kunne være både IKKE_MØTT_INNEN_FRIST og annullert?
            Kontrollsamtalestatus.IKKE_MØTT_INNEN_FRIST,
            -> UgyldigStatusovergang.left()
        }
    }

    // TODO jm: det føles som det burde være noen restriksjoner her?
    fun endreDato(innkallingsdato: LocalDate): Either<KunneIkkeEndreDato, Kontrollsamtale> {
        if (this.status != Kontrollsamtalestatus.PLANLAGT_INNKALLING) return KunneIkkeEndreDato.UgyldigStatusovergang.left()
        if (!innkallingsdato.erFørsteDagIMåned()) return KunneIkkeEndreDato.DatoErIkkeFørsteIMåned.left()
        return this.copy(innkallingsdato = innkallingsdato, frist = regnUtFristFraInnkallingsdato(innkallingsdato)).right()
    }

    fun settGjennomført(journalpostId: JournalpostId): Either<UgyldigStatusovergang, Kontrollsamtale> {
        return if (status == Kontrollsamtalestatus.INNKALT) {
            copy(
                status = Kontrollsamtalestatus.GJENNOMFØRT,
                journalpostIdKontrollnotat = journalpostId,
            ).right()
        } else {
            UgyldigStatusovergang.left()
        }
    }

    fun settIkkeMøttInnenFrist(): Either<UgyldigStatusovergang, Kontrollsamtale> {
        return if (status == Kontrollsamtalestatus.INNKALT) {
            copy(status = Kontrollsamtalestatus.IKKE_MØTT_INNEN_FRIST).right()
        } else {
            UgyldigStatusovergang.left()
        }
    }

    sealed interface KunneIkkeEndreDato {
        object UgyldigStatusovergang : KunneIkkeEndreDato
        object DatoErIkkeFørsteIMåned : KunneIkkeEndreDato
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
                    opprettet = Tidspunkt.now(clock),
                    journalpostIdKontrollnotat = null,
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
            journalpostIdKontrollnotat = null,
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
                    journalpostIdKontrollnotat = null,
                )
            }
    }
}

internal fun regnUtFristFraInnkallingsdato(innkallingsdato: LocalDate): LocalDate {
    return if (innkallingsdato.month == Month.NOVEMBER) {
        innkallingsdato.withDayOfMonth(25)
    } else {
        innkallingsdato.endOfMonth()
    }
}

internal fun regnUtInnkallingsdato(periode: Periode, vedtaksdato: LocalDate, clock: Clock): LocalDate? {
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
    } else if (fourMonthsDate.isAfter(today)) {
        when {
            stønadsslutt.erMindreEnnEnMånedSenere(fourMonthsDate) -> null
            fourMonthsDate.erMindreEnnEnMånedSenere(vedtaksdato.endOfMonth()) -> {
                val fiveMonthsDate = fourMonthsDate.plusMonths(1)
                if (stønadsslutt.erMindreEnnEnMånedSenere(fiveMonthsDate)) null else fiveMonthsDate
            }
            else -> fourMonthsDate
        }
    } else {
        null
    }
}

internal fun regnUtInnkallingsdatoOm4Mnd(stønadsslutt: LocalDate, fraDato: LocalDate): LocalDate? {
    val innkallingsdato = fraDato.startOfMonth().plusMonths(4)
    return if (stønadsslutt.erMindreEnnEnMånedSenere(innkallingsdato.endOfMonth())) null else innkallingsdato
}

object UgyldigStatusovergang
object SkalIkkeOppretteKontrollsamtale
