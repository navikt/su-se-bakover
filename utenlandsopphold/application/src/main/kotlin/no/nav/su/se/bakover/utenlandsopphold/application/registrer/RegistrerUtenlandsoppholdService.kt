package no.nav.su.se.bakover.utenlandsopphold.application.registrer

import arrow.core.Either
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.registrerUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.KunneIkkeRegistereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdCommand
import java.time.Clock

class RegistrerUtenlandsoppholdService(
    private val sakRepo: SakRepo,
    private val utenlandsoppholdRepo: UtenlandsoppholdRepo,
    private val clock: Clock,
) {
    fun registrer(
        command: RegistrerUtenlandsoppholdCommand,
    ): Either<KunneIkkeRegistereUtenlandsopphold, List<RegistrertUtenlandsopphold>> {
        return sakRepo.hentSak(command.sakId)!!.registrerUtenlandsopphold(
            command = command,
            clock = clock,
        ).map { (sak, hendelse) ->
            utenlandsoppholdRepo.lagre(hendelse)
            sak.utenlandsopphold
        }
    }
}
