package no.nav.su.se.bakover.utenlandsopphold.application.annuller

import arrow.core.Either
import no.nav.su.se.bakover.domain.sak.HentSakRepo
import no.nav.su.se.bakover.domain.sak.annullerUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.KunneIkkeAnnullereUtenlandsopphold

class AnnullerUtenlandsoppholdService(
    private val sakRepo: HentSakRepo,
    private val utenlandsoppholdRepo: UtenlandsoppholdRepo,
) {
    fun annuller(
        command: AnnullerUtenlandsoppholdCommand,
    ): Either<KunneIkkeAnnullereUtenlandsopphold, RegistrerteUtenlandsopphold> {
        return sakRepo.hentSak(command.sakId)!!.annullerUtenlandsopphold(
            command = command,
            utenlandsoppholdHendelser = utenlandsoppholdRepo.hentForSakId(command.sakId),
        ).map { (sak, hendelse) ->
            utenlandsoppholdRepo.lagre(hendelse)
            sak.utenlandsopphold
        }
    }
}
