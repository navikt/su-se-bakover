package no.nav.su.se.bakover.utenlandsopphold.application

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerUtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import java.time.Clock

class RegistrerUtenlandsoppholdService(
    private val sakRepo: SakRepo,
    private val registertUtenlandsoppholdRepo: RegistrerUtenlandsoppholdRepo,
    private val clock: Clock,
) {
    fun registrer(
        command: RegistrerUtenlandsoppholdCommand,
    ): Either<Sak.OverlappendePeriode, List<RegistrertUtenlandsopphold>> {
        return sakRepo.hentSak(command.sakId)!!.registrerUtenlandsopphold(command, clock).map { (sak, hendelse) ->
            registertUtenlandsoppholdRepo.lagre(hendelse)
            sak.utenlandsopphold
        }
    }
}
