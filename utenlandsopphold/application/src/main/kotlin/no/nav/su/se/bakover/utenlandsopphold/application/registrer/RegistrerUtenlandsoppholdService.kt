package no.nav.su.se.bakover.utenlandsopphold.application.registrer

import arrow.core.Either
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.registrerUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.KunneIkkeRegistereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdCommand

class RegistrerUtenlandsoppholdService(
    private val sakRepo: SakRepo,
    private val utenlandsoppholdRepo: UtenlandsoppholdRepo,
    private val journalpostClient: JournalpostClient,
) {
    fun registrer(
        command: RegistrerUtenlandsoppholdCommand,
    ): Either<KunneIkkeRegistereUtenlandsopphold, RegistrerteUtenlandsopphold> {
        return sakRepo.hentSak(command.sakId)!!.registrerUtenlandsopphold(
            command = command,
            utenlandsoppholdHendelser = utenlandsoppholdRepo.hentForSakId(command.sakId),
        ) { j: JournalpostId, s: Saksnummer ->
            journalpostClient.erTilknyttetSak(j, s)
        }.map { (sak, hendelse) ->
            utenlandsoppholdRepo.lagre(hendelse)
            sak.utenlandsopphold
        }
    }
}
