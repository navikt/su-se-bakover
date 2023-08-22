package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import arrow.core.Nel
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.journalpost.ErTilknyttetSak
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkeTilknytningTilSak
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelser
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.KunneIkkeAnnullereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.korriger.KunneIkkeKorrigereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.KunneIkkeRegistereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import org.slf4j.LoggerFactory

/**
 * Utenlandsopphold har tre typer hendelser:
 * 1. _registrere_ - Første utenlandsoppholdhendelse. Kan ikke overlappe med andre ikke-annullerte utenlandsopphold.
 * 2. _korrigere_ - Dersom man har registrert noe feil, kan man sende en korrigeringhendelse som vil overskrive den forrige hendelsen helt. Kan ikke overlappe med andre ikke-annullerte utenlandsopphold. Kan korrigere et vilkårlig antall ganger.
 * 3. _annullere_ - Dersom det aldri skulle vært registrert et utenlandsopphold, kan man annullere den. Antall dager blir ikke lenger tatt med i totalen. Perioden hendelsen gjaldt for er nå "åpen" igjen. Denne hendelsen kan ikke bli korrigert eller angret. Dersom det ikke var riktig og annullere, må man registrere en ny hendelse.
 */
fun Sak.registrerUtenlandsopphold(
    command: RegistrerUtenlandsoppholdCommand,
    utenlandsoppholdHendelser: UtenlandsoppholdHendelser,
    validerJournalpost: suspend (JournalpostId, Saksnummer) -> Either<KunneIkkeSjekkeTilknytningTilSak, ErTilknyttetSak>,
): Either<KunneIkkeRegistereUtenlandsopphold, Triple<Sak, RegistrerUtenlandsoppholdHendelse, AuditLogEvent>> {
    if (versjon != command.klientensSisteSaksversjon) {
        return KunneIkkeRegistereUtenlandsopphold.UtdatertSaksversjon.left()
    }
    validerJournalposter(
        journalposter = command.journalposter,
        validerJournalpost = validerJournalpost,
    ).onLeft {
        return KunneIkkeRegistereUtenlandsopphold.KunneIkkeValidereJournalposter(it).left()
    }
    val nesteVersjon = versjon.inc()
    return utenlandsoppholdHendelser.registrer(
        command = command,
        nesteVersjon = nesteVersjon,
    ).map {
        val hendelse = it.last() as RegistrerUtenlandsoppholdHendelse
        Triple(
            this.copy(
                utenlandsopphold = it.currentState,
                versjon = nesteVersjon,
            ),
            hendelse,
            hendelse.toAuditEvent(fnr),
        )
    }
}

/**
 * Utenlandsopphold har tre typer hendelser:
 * 1. _registrere_ - Første utenlandsoppholdhendelse. Kan ikke overlappe med andre ikke-annullerte utenlandsopphold.
 * 2. _korrigere_ - Dersom man har registrert noe feil, kan man sende en korrigeringhendelse som vil overskrive den forrige hendelsen helt. Kan ikke overlappe med andre ikke-annullerte utenlandsopphold. Kan korrigere et vilkårlig antall ganger.
 * 3. _annullere_ - Dersom det aldri skulle vært registrert et utenlandsopphold, kan man annullere den. Antall dager blir ikke lenger tatt med i totalen. Perioden hendelsen gjaldt for er nå "åpen" igjen. Denne hendelsen kan ikke bli korrigert eller angret. Dersom det ikke var riktig og annullere, må man registrere en ny hendelse.
 */
fun Sak.korrigerUtenlandsopphold(
    command: KorrigerUtenlandsoppholdCommand,
    utenlandsoppholdHendelser: UtenlandsoppholdHendelser,
    validerJournalpost: suspend (JournalpostId, Saksnummer) -> Either<KunneIkkeSjekkeTilknytningTilSak, ErTilknyttetSak>,
): Either<KunneIkkeKorrigereUtenlandsopphold, Triple<Sak, KorrigerUtenlandsoppholdHendelse, AuditLogEvent>> {
    if (versjon != command.klientensSisteSaksversjon) {
        return KunneIkkeKorrigereUtenlandsopphold.UtdatertSaksversjon.left()
    }
    val tidligereValiderteJournalposter = utenlandsopphold.flatMap { it.journalposter }.toSet()
    validerJournalposter(command.journalposter.minus(tidligereValiderteJournalposter), validerJournalpost).onLeft {
        return KunneIkkeKorrigereUtenlandsopphold.KunneIkkeBekrefteJournalposter(it).left()
    }
    val nesteVersjon = versjon.inc()
    return utenlandsoppholdHendelser.korriger(
        command = command,
        nesteVersjon = nesteVersjon,
    ).map {
        val hendelse = it.last() as KorrigerUtenlandsoppholdHendelse
        Triple(
            this.copy(
                utenlandsopphold = it.currentState,
                versjon = nesteVersjon,
            ),
            hendelse,
            hendelse.toAuditEvent(fnr),
        )
    }
}

/**
 * Utenlandsopphold har tre typer hendelser:
 * 1. _registrere_ - Første utenlandsoppholdhendelse. Kan ikke overlappe med andre ikke-annullerte utenlandsopphold.
 * 2. _korrigere_ - Dersom man har registrert noe feil, kan man sende en korrigeringhendelse som vil overskrive den forrige hendelsen helt. Kan ikke overlappe med andre ikke-annullerte utenlandsopphold. Kan korrigere et vilkårlig antall ganger.
 * 3. _annullere_ - Dersom det aldri skulle vært registrert et utenlandsopphold, kan man annullere den. Antall dager blir ikke lenger tatt med i totalen. Perioden hendelsen gjaldt for er nå "åpen" igjen. Denne hendelsen kan ikke bli korrigert eller angret. Dersom det ikke var riktig og annullere, må man registrere en ny hendelse.
 */
fun Sak.annullerUtenlandsopphold(
    command: AnnullerUtenlandsoppholdCommand,
    utenlandsoppholdHendelser: UtenlandsoppholdHendelser,
): Either<KunneIkkeAnnullereUtenlandsopphold, Triple<Sak, AnnullerUtenlandsoppholdHendelse, AuditLogEvent>> {
    if (versjon != command.klientensSisteSaksversjon) {
        return KunneIkkeAnnullereUtenlandsopphold.UtdatertSaksversjon.left()
    }
    val nesteVersjon = versjon.inc()
    return utenlandsoppholdHendelser.annuller(
        command = command,
        nesteVersjon = nesteVersjon,
    ).map {
        val hendelse = it.last() as AnnullerUtenlandsoppholdHendelse
        Triple(
            this.copy(
                utenlandsopphold = it.currentState,
                versjon = nesteVersjon,
            ),
            hendelse,
            hendelse.toAuditEvent(fnr),
        )
    }
}

private val log = LoggerFactory.getLogger("UtenlandsoppholdCommands")
private fun Sak.validerJournalposter(
    journalposter: List<JournalpostId>,
    validerJournalpost: suspend (JournalpostId, Saksnummer) -> Either<KunneIkkeSjekkeTilknytningTilSak, ErTilknyttetSak>,
): Either<Nel<JournalpostId>, Unit> {
    return runBlocking {
        journalposter.map {
            async {
                Pair(it, validerJournalpost(it, saksnummer))
            }
        }.awaitAll().mapNotNull {
            when (val r = it.second) {
                is Either.Left -> it.first.also {
                    if (r.swap().getOrNull()!! !is KunneIkkeSjekkeTilknytningTilSak.FantIkkeJournalpost) {
                        log.error("Kunne ikke validere journalpost til utenlandsopphold for sakId $id og journalpostId $it. Underliggende feil: ${r.value}")
                    }
                }

                is Either.Right -> when (r.value) {
                    ErTilknyttetSak.Ja -> null
                    ErTilknyttetSak.Nei -> it.first
                }
            }
        }.let {
            when {
                it.isEmpty() -> Unit.right()
                else -> it.toNonEmptyList().left()
            }
        }
    }
}
