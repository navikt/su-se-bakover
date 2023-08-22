package no.nav.su.se.bakover.utenlandsopphold.application.korriger

import arrow.core.Either
import no.nav.su.se.bakover.common.audit.AuditLogger
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.korrigerUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.domain.korriger.KunneIkkeKorrigereUtenlandsopphold

class KorrigerUtenlandsoppholdService(
    private val sakRepo: SakRepo,
    private val utenlandsoppholdRepo: UtenlandsoppholdRepo,
    private val journalpostClient: JournalpostClient,
    private val auditLogger: AuditLogger,
    private val personService: PersonService,
) {
    fun korriger(
        command: KorrigerUtenlandsoppholdCommand,
    ): Either<KunneIkkeKorrigereUtenlandsopphold, RegistrerteUtenlandsopphold> {
        return sakRepo.hentSak(command.sakId)!!.also {
            personService.sjekkTilgangTilPerson(it.fnr).onLeft {
                throw IllegalArgumentException("Tilgangssjekk feilet ved korringering av utenlandsopphold. Underliggende feil: $it")
            }
        }.korrigerUtenlandsopphold(
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
