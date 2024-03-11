package no.nav.su.se.bakover.utenlandsopphold.application.korriger

import arrow.core.Either
import dokument.domain.journalføring.QueryJournalpostClient
import no.nav.su.se.bakover.common.audit.AuditLogger
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.korrigerUtenlandsopphold
import person.domain.PersonService
import vilkår.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import vilkår.utenlandsopphold.domain.UtenlandsoppholdRepo
import vilkår.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdCommand
import vilkår.utenlandsopphold.domain.korriger.KunneIkkeKorrigereUtenlandsopphold

class KorrigerUtenlandsoppholdService(
    private val sakRepo: SakRepo,
    private val utenlandsoppholdRepo: UtenlandsoppholdRepo,
    private val queryJournalpostClient: QueryJournalpostClient,
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
            queryJournalpostClient.erTilknyttetSak(j, s)
        }.map { (sak, hendelse, auditHendelse) ->
            utenlandsoppholdRepo.lagre(hendelse, command.toMetadata())
            auditLogger.log(auditHendelse)
            sak.utenlandsopphold
        }
    }
}
