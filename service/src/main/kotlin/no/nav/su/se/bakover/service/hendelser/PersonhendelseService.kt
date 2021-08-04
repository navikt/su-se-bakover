package no.nav.su.se.bakover.service.hendelser

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.hendelse.HendelseRepo
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.PdlHendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory

// gjelder - ufør_flykning
// oppgavetype - vurder konsekvens om ytelse
// 1 uke frist på oppgaven
// se till att saksnummer kommer in i beskrivelsen
// 4815

class PersonhendelseService(
    private val personRepo: PersonRepo,
    private val hendelseRepo: HendelseRepo
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun prosesserNyMelding(pdlHendelse: PdlHendelse, melding: String) {
        val eksisterendeSaksnummer = hentEksisterendeSaksnummer(pdlHendelse) ?: return

        log.info("Prosserer melding med offset: ${pdlHendelse.offset}, opplysningstype: $pdlHendelse")
        hendelseRepo.lagre(pdlHendelse, eksisterendeSaksnummer, melding)
    }

    private fun hentEksisterendeSaksnummer(pdlHendelse: PdlHendelse): Saksnummer? =
        personRepo.hentSaksnummerForIdenter(pdlHendelse.personidenter)

    /*
    private fun lagRevurderingsOppgave(saksnummer: Saksnummer, aktørId: AktørId): Either<KunneIkkeLageRevurderingsoppgave, Unit> {
        oppgaveService.opprettOppgave(
            OppgaveConfig.VurderKonsekvensForYtelse(
                saksnummer = saksnummer,
                aktørId = aktørId,
                beskrivelse = "lolz",
            )
        ).getOrHandle {
            return KunneIkkeLageRevurderingsoppgave.KallMotOppgaveFeilet.left()
        }

        return Unit.right()
    }
     */
}

sealed class KunneIkkeLageRevurderingsoppgave {
    object FantIkkeSak : KunneIkkeLageRevurderingsoppgave()
    object KunneIkkeHentePerson : KunneIkkeLageRevurderingsoppgave()
    //object KallMotOppgaveFeilet : KunneIkkeLageRevurderingsoppgave()
}
