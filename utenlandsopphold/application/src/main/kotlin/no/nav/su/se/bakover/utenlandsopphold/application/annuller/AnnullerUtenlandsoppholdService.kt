package no.nav.su.se.bakover.utenlandsopphold.application.annuller

import arrow.core.Either
import no.nav.su.se.bakover.common.audit.application.AuditLogger
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.annullerUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.KunneIkkeAnnullereUtenlandsopphold

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
            personService.sjekkTilgangTilPerson(it.fnr).tapLeft {
                throw IllegalArgumentException("Tilgangssjekk feilet ved annullering av utenlandsopphold. Underliggende feil: $it")
            }
        }.annullerUtenlandsopphold(
            command = command,
            utenlandsoppholdHendelser = utenlandsoppholdRepo.hentForSakId(command.sakId),
        ).map { (sak, hendelse, auditHendelse) ->
            utenlandsoppholdRepo.lagre(hendelse)
            auditLogger.log(auditHendelse)
            sak.utenlandsopphold
        }
    }
}
