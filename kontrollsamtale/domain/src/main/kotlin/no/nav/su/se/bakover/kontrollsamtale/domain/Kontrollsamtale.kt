package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.tid.between
import no.nav.su.se.bakover.common.domain.tid.endOfMonth
import no.nav.su.se.bakover.common.domain.tid.erFørsteDagIMåned
import no.nav.su.se.bakover.common.domain.tid.erMindreEnnEnMånedSenere
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned.KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned.OppdaterInnkallingsmånedPåKontrollsamtaleCommand
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status.KunneIkkeOppdatereStatusPåKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status.OppdaterStatusPåKontrollsamtaleCommand
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.util.UUID

/**
 * @param dokumentId null inntil vi har generert og lagret brevet. Merk at journalføringen og distribusjonen av brevet skjer asynkront.
 * @param journalpostIdKontrollnotat null inntil kontrollnotatet er mottatt.
 */
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

    /**
     * Vi kan kun oppdatere innkallingsmåned mens vi er i tilstanden [Kontrollsamtalestatus.PLANLAGT_INNKALLING].
     */
    fun kanOppdatereInnkallingsmåned(): Boolean {
        return status == Kontrollsamtalestatus.PLANLAGT_INNKALLING
    }

    /**
     * Lovlige overganger for denne kontrollsamtalen som vi tillater at en saksbehandler oppdaterer.
     * Ment for at frontend skal slippe holde styr på dette.
     * Overgangen fra planlagt innkalling til innkalt gjøres av systemet.
     */
    fun lovligeOvergangerForSaksbehandler(): Set<Kontrollsamtalestatus> {
        return when (status) {
            Kontrollsamtalestatus.PLANLAGT_INNKALLING -> setOf(Kontrollsamtalestatus.ANNULLERT)
            Kontrollsamtalestatus.INNKALT -> setOf(
                Kontrollsamtalestatus.GJENNOMFØRT,
                Kontrollsamtalestatus.IKKE_MØTT_INNEN_FRIST,
                Kontrollsamtalestatus.ANNULLERT,
            )

            Kontrollsamtalestatus.GJENNOMFØRT,
            Kontrollsamtalestatus.ANNULLERT,
            Kontrollsamtalestatus.IKKE_MØTT_INNEN_FRIST,
            -> emptySet()
        }
    }

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

    fun oppdaterInnkallingsdato(innkallingsdato: LocalDate): Either<KunneIkkeOppdatereDato, Kontrollsamtale> {
        if (this.status != Kontrollsamtalestatus.PLANLAGT_INNKALLING) return KunneIkkeOppdatereDato.UgyldigStatusovergang.left()
        if (!innkallingsdato.erFørsteDagIMåned()) return KunneIkkeOppdatereDato.DatoErIkkeFørsteIMåned.left()
        return this.copy(innkallingsdato = innkallingsdato, frist = regnUtFristFraInnkallingsdato(innkallingsdato))
            .right()
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

    fun erAnnullert(): Boolean {
        return status == Kontrollsamtalestatus.ANNULLERT
    }

    fun erPlanlagtInnkalling(): Boolean {
        return status == Kontrollsamtalestatus.PLANLAGT_INNKALLING
    }

    fun oppdaterInnkallingsmåned(
        command: OppdaterInnkallingsmånedPåKontrollsamtaleCommand,
    ): Either<KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale, Kontrollsamtale> {
        if (this.erAnnullert()) {
            return KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale.KontrollsamtaleAnnullert(this.id).left()
        }
        if (!this.erPlanlagtInnkalling()) {
            return KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale.KanKunOppdatereInnkallingsmånedForPlanlagtInnkalling(
                this.id,
            ).left()
        }
        val nyInnkallingsdato = command.nyInnkallingsmåned.fraOgMed
        return this.copy(
            innkallingsdato = nyInnkallingsdato,
            frist = regnUtFristFraInnkallingsdato(nyInnkallingsdato),
        ).right()
    }

    fun oppdaterStatus(
        command: OppdaterStatusPåKontrollsamtaleCommand,
    ): Either<KunneIkkeOppdatereStatusPåKontrollsamtale, Kontrollsamtale> {
        return when (command.nyStatus) {
            is OppdaterStatusPåKontrollsamtaleCommand.OppdaterStatusTil.Gjennomført -> {
                this.settGjennomført(journalpostId = command.nyStatus.journalpostId).mapLeft {
                    KunneIkkeOppdatereStatusPåKontrollsamtale.UgyldigStatusovergang(
                        this.id,
                        lovligeOvergangerForSaksbehandler(),
                    )
                }
            }

            is OppdaterStatusPåKontrollsamtaleCommand.OppdaterStatusTil.IkkeMøttInnenFrist -> {
                this.settIkkeMøttInnenFrist().mapLeft {
                    KunneIkkeOppdatereStatusPåKontrollsamtale.UgyldigStatusovergang(
                        this.id,
                        lovligeOvergangerForSaksbehandler(),
                    )
                }
            }
        }
    }

    sealed interface KunneIkkeOppdatereDato {
        data object UgyldigStatusovergang : KunneIkkeOppdatereDato
        data object DatoErIkkeFørsteIMåned : KunneIkkeOppdatereDato
    }

    companion object {
        fun opprettNyKontrollsamtaleFraVedtak(
            vedtak: VedtakEndringIYtelse,
            clock: Clock,
        ): Either<SkalIkkeOppretteKontrollsamtale, Kontrollsamtale> =
            regnUtInnkallingsdato(
                vedtak.periode,
                vedtak.opprettet.toLocalDate(zoneIdOslo),
                clock,
            )?.let { innkallingsdato ->
                Kontrollsamtale(
                    sakId = vedtak.behandling.sakId,
                    innkallingsdato = innkallingsdato,
                    status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
                    dokumentId = null,
                    opprettet = Tidspunkt.now(clock),
                    journalpostIdKontrollnotat = null,
                ).right()
            } ?: SkalIkkeOppretteKontrollsamtale.left()

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
            regnUtInnkallingsdatoOm4Mnd(gjeldendeStønadsperiode.tilOgMed, LocalDate.now(clock))?.let {
                Kontrollsamtale(
                    sakId = sakId,
                    innkallingsdato = it,
                    status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
                    dokumentId = null,
                    opprettet = Tidspunkt.now(clock),
                    journalpostIdKontrollnotat = null,
                ).right()
            } ?: SkalIkkeOppretteKontrollsamtale.left()
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

data object UgyldigStatusovergang
data object SkalIkkeOppretteKontrollsamtale
