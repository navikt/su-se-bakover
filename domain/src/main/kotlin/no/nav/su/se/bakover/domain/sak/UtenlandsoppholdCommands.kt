package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.domain.Sak
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

/**
 * Utenlandsopphold har tre typer hendelser:
 * 1. _registrere_ - Første utenlandsoppholdhendelse. Kan ikke overlappe med andre ikke-annullerte utenlandsopphold.
 * 2. _korrigere_ - Dersom man har registrert noe feil, kan man sende en korrigeringhendelse som vil overskrive den forrige hendelsen helt. Kan ikke overlappe med andre ikke-annullerte utenlandsopphold. Kan korrigere et vilkårlig antall ganger.
 * 3. _annullere_ - Dersom det aldri skulle vært registrert et utenlandsopphold, kan man annullere den. Antall dager blir ikke lenger tatt med i totalen. Perioden hendelsen gjaldt for er nå "åpen" igjen. Denne hendelsen kan ikke bli korrigert eller angret. Dersom det ikke var riktig og annullere, må man registrere en ny hendelse.
 */
fun Sak.registrerUtenlandsopphold(
    command: RegistrerUtenlandsoppholdCommand,
    utenlandsoppholdHendelser: UtenlandsoppholdHendelser,
): Either<KunneIkkeRegistereUtenlandsopphold, Pair<Sak, RegistrerUtenlandsoppholdHendelse>> {
    if (versjon != command.klientensSisteSaksversjon) {
        return KunneIkkeRegistereUtenlandsopphold.UtdatertSaksversjon.left()
    }
    val nesteVersjon = versjon.inc()
    return utenlandsoppholdHendelser.registrer(
        command = command,
        nesteVersjon = nesteVersjon,
    ).map {
        Pair(
            this.copy(
                utenlandsopphold = it.currentState,
                versjon = nesteVersjon,
            ),
            it.last() as RegistrerUtenlandsoppholdHendelse,
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
): Either<KunneIkkeKorrigereUtenlandsopphold, Pair<Sak, KorrigerUtenlandsoppholdHendelse>> {
    if (versjon != command.klientensSisteSaksversjon) {
        return KunneIkkeKorrigereUtenlandsopphold.UtdatertSaksversjon.left()
    }
    val nesteVersjon = versjon.inc()
    return utenlandsoppholdHendelser.korriger(
        command = command,
        nesteVersjon = nesteVersjon,
    ).map {
        Pair(
            this.copy(
                utenlandsopphold = it.currentState,
                versjon = nesteVersjon,
            ),
            it.last() as KorrigerUtenlandsoppholdHendelse,
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
): Either<KunneIkkeAnnullereUtenlandsopphold, Pair<Sak, AnnullerUtenlandsoppholdHendelse>> {
    if (versjon != command.klientensSisteSaksversjon) {
        return KunneIkkeAnnullereUtenlandsopphold.UtdatertSaksversjon.left()
    }
    val nesteVersjon = versjon.inc()
    return utenlandsoppholdHendelser.annuller(
        command = command,
        nesteVersjon = nesteVersjon,
    ).map {
        Pair(
            this.copy(
                utenlandsopphold = it.currentState,
                versjon = nesteVersjon,
            ),
            it.last() as AnnullerUtenlandsoppholdHendelse,
        )
    }
}
