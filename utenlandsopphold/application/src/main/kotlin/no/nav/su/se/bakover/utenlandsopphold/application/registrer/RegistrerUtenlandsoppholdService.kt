package no.nav.su.se.bakover.utenlandsopphold.application.registrer

import arrow.core.Either
import no.nav.su.se.bakover.common.audit.AuditLogger
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.registrerUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.KunneIkkeRegistereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdCommand

class RegistrerUtenlandsoppholdService(
    private val sakRepo: SakRepo,
    private val utenlandsoppholdRepo: UtenlandsoppholdRepo,
    private val journalpostClient: JournalpostClient,
    private val auditLogger: AuditLogger,
    private val personService: PersonService,
) {
    fun registrer(
        command: RegistrerUtenlandsoppholdCommand,
    ): Either<KunneIkkeRegistereUtenlandsopphold, RegistrerteUtenlandsopphold> {
        return sakRepo.hentSak(command.sakId)!!
            .also {
                personService.sjekkTilgangTilPerson(it.fnr).onLeft {
                    throw IllegalArgumentException("Tilgangssjekk feilet ved registrering av utenlandsopphold. Underliggende feil: $it")
                }
            }
            .registrerUtenlandsopphold(
                command = command,
                utenlandsoppholdHendelser = utenlandsoppholdRepo.hentForSakId(command.sakId),
            ) { j: JournalpostId, s: Saksnummer ->
                journalpostClient.erTilknyttetSak(j, s)
            }.map { (sak, hendelse, auditHendelse) ->
                utenlandsoppholdRepo.lagre(hendelse)
                auditLogger.log(auditHendelse)
                sak.utenlandsopphold
            }
    }
}
