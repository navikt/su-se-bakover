package no.nav.su.se.bakover.utenlandsopphold.application.oppdater

import arrow.core.Either
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.oppdaterUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.oppdater.KunneIkkeOppdatereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.oppdater.OppdaterUtenlandsoppholdCommand
import java.time.Clock

class OppdaterUtenlandsoppholdService(
    private val sakRepo: SakRepo,
    private val utenlandsoppholdRepo: UtenlandsoppholdRepo,
    private val clock: Clock,
) {
    fun oppdater(
        command: OppdaterUtenlandsoppholdCommand,
    ): Either<KunneIkkeOppdatereUtenlandsopphold, List<RegistrertUtenlandsopphold>> {
        val sak = sakRepo.hentSak(command.sakId)!!
        val forrigeHendelse = utenlandsoppholdRepo.hentSisteHendelse(command.sakId, command.utenlandsoppholdId)!!
        return sak.oppdaterUtenlandsopphold(
            command = command,
            forrigeHendelse = forrigeHendelse,
            clock = clock,
        ).map { (sak, hendelse) ->
            utenlandsoppholdRepo.lagre(hendelse)
            sak.utenlandsopphold
        }
    }
}
