package no.nav.su.se.bakover.utenlandsopphold.application

import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsoppholdRepo
import java.time.Clock

class RegistrerUtenlandsoppholdService(
    private val sakRepo: SakRepo,
    private val registertUtenlandsoppholdRepo: RegistrertUtenlandsoppholdRepo,
    private val clock: Clock,
) {
    fun registrer(
        command: RegistrerUtenlandsoppholdCommand,
    ): List<RegistrertUtenlandsopphold> {
        return sakRepo.hentSak(command.sakId)!!.registrerUtenlandsopphold(command.toUtenlandsopphold(clock))
            .also { (sak, registrertUtenlandsopphold) ->
                registertUtenlandsoppholdRepo.lagre(sak.id, registrertUtenlandsopphold)
            }.first.utenlandsopphold
    }
}
