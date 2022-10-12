package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.oppdater.KunneIkkeOppdatereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.oppdater.OppdaterUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.domain.oppdater.OppdaterUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.KunneIkkeRegistereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import java.time.Clock

fun Sak.registrerUtenlandsopphold(
    command: RegistrerUtenlandsoppholdCommand,
    clock: Clock,
): Either<KunneIkkeRegistereUtenlandsopphold, Pair<Sak, RegistrerUtenlandsoppholdHendelse>> {
    if (utenlandsopphold.any { it.periode inneholder command.periode }) {
        return KunneIkkeRegistereUtenlandsopphold.OverlappendePeriode.left()
    }
    if (versjon != command.klientensSisteSaksversjon) {
        return KunneIkkeRegistereUtenlandsopphold.UtdatertSaksversjon.left()
    }
    val hendelse = command.toHendelse(
        forrigeVersjon = versjon,
        clock = clock,
    )
    return Pair(
        this.copy(utenlandsopphold = this.utenlandsopphold + hendelse.toRegistrertUtenlandsopphold()),
        hendelse,
    ).right()
}

fun Sak.oppdaterUtenlandsopphold(
    command: OppdaterUtenlandsoppholdCommand,
    forrigeHendelse: UtenlandsoppholdHendelse,
    clock: Clock,
): Either<KunneIkkeOppdatereUtenlandsopphold, Pair<Sak, OppdaterUtenlandsoppholdHendelse>> {
    if (utenlandsopphold.any { it.periode inneholder command.periode }) {
        return KunneIkkeOppdatereUtenlandsopphold.OverlappendePeriode.left()
    }
    if (versjon != command.klientensSisteSaksversjon) {
        return KunneIkkeOppdatereUtenlandsopphold.UtdatertSaksversjon.left()
    }
    val hendelse = command.toHendelse(
        forrigeHendelse = forrigeHendelse,
        forrigeVersjon = versjon,
        clock = clock,
    )
    return Pair(
        this.copy(utenlandsopphold = this.utenlandsopphold + hendelse.toRegistrertUtenlandsopphold()),
        hendelse,
    ).right()
}
