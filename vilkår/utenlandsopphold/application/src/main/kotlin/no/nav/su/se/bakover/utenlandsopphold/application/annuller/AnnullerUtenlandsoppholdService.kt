package no.nav.su.se.bakover.utenlandsopphold.application.annuller

import arrow.core.Either
import no.nav.su.se.bakover.common.audit.AuditLogger
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.annullerUtenlandsopphold
import person.domain.PersonService
import vilkår.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import vilkår.utenlandsopphold.domain.UtenlandsoppholdRepo
import vilkår.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdCommand
import vilkår.utenlandsopphold.domain.annuller.KunneIkkeAnnullereUtenlandsopphold

class AnnullerUtenlandsoppholdService(
    private val sakRepo: SakRepo,
    private val utenlandsoppholdRepo: UtenlandsoppholdRepo,
    private val auditLogger: AuditLogger,
    private val personService: PersonService,
) {
    fun annuller(
        command: AnnullerUtenlandsoppholdCommand,
    ): Either<KunneIkkeAnnullereUtenlandsopphold, RegistrerteUtenlandsopphold> {
        return sakRepo.hentSak(command.sakId)!!.also {
            personService.sjekkTilgangTilPerson(it.fnr).onLeft {
                throw IllegalArgumentException("Tilgangssjekk feilet ved annullering av utenlandsopphold. Underliggende feil: $it")
            }
        }.annullerUtenlandsopphold(
            command = command,
            utenlandsoppholdHendelser = utenlandsoppholdRepo.hentForSakId(command.sakId),
        ).map { (sak, hendelse, auditHendelse) ->
            utenlandsoppholdRepo.lagre(hendelse, command.toMetadata())
            auditLogger.log(auditHendelse)
            sak.utenlandsopphold
        }
    }
}
