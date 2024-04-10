package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.tid.endOfMonth
import no.nav.su.se.bakover.common.domain.tid.erFørsteDagIMåned
import no.nav.su.se.bakover.common.domain.tid.erMindreEnnEnMånedSenere
import no.nav.su.se.bakover.common.domain.tid.max
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
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

    sealed interface KunneIkkeEndreDato {
        data object UgyldigStatusovergang : KunneIkkeEndreDato
        data object DatoErIkkeFørsteIMåned : KunneIkkeEndreDato
    }

    companion object {
        fun opprettNyKontrollsamtaleFraVedtak(
            vedtak: VedtakInnvilgetSøknadsbehandling,
            clock: Clock,
        ): Either<SkalIkkeOppretteKontrollsamtale, Kontrollsamtale> =
            regnUtInnkallingsdato(
                stønadsperiode = vedtak.periode,
                today = LocalDate.now(clock),
                mottattSøknadDato = vedtak.behandling.søknad.mottaksdato,
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

/**
 * Merk at today alltid vil være etter vedtaksdato, så vi trenger ikke ta høyde for den.
 */
internal fun regnUtInnkallingsdato(
    stønadsperiode: Periode,
    mottattSøknadDato: LocalDate,
    today: LocalDate,
): LocalDate? {
    if (stønadsperiode.getAntallMåneder() < 5) return null
    val stønadsstart = stønadsperiode.fraOgMed
    val stønadsslutt = stønadsperiode.tilOgMed
    val førsteInnkallingBasertPåStønadsstart = stønadsstart.plusMonths(4).startOfMonth()
    val førsteInnkallingBasertPåMottattSøknad = mottattSøknadDato.plusMonths(4).startOfMonth()
    val tidligsteInnkallingsdato =
        max(førsteInnkallingBasertPåMottattSøknad, førsteInnkallingBasertPåStønadsstart, today).let {
            if (it.dayOfMonth > 1) {
                it.plusMonths(1).startOfMonth()
            } else {
                it
            }
        }
    val sisteInnkallingsdato = stønadsslutt.startOfMonth().minusMonths(2)
    if (tidligsteInnkallingsdato.isAfter(sisteInnkallingsdato)) return null
    return tidligsteInnkallingsdato
}

internal fun regnUtInnkallingsdatoOm4Mnd(stønadsslutt: LocalDate, fraDato: LocalDate): LocalDate? {
    val innkallingsdato = fraDato.startOfMonth().plusMonths(4)
    return if (stønadsslutt.erMindreEnnEnMånedSenere(innkallingsdato.endOfMonth())) null else innkallingsdato
}

data object UgyldigStatusovergang
data object SkalIkkeOppretteKontrollsamtale
