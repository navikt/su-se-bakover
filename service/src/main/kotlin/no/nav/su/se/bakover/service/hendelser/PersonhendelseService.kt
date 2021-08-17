package no.nav.su.se.bakover.service.hendelser

import no.nav.su.se.bakover.database.hendelse.PersonhendelseRepo
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import org.slf4j.LoggerFactory

/**
 * TODO jah/Aryan: Neste PR. Lag oppgave dersom det er en uførehendelse.
 * oppgavetype - vurder konsekvens om ytelse
 * 1 uke frist på oppgaven
 * se till att saksnummer kommer in i beskrivelsen. 4815
 */
class PersonhendelseService(
    private val personRepo: PersonRepo,
    private val personhendelseRepo: PersonhendelseRepo,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun prosesserNyHendelse(personhendelse: Personhendelse.Ny) {
        val eksisterendeSaksnummer =
            personRepo.hentSaksnummerForIdenter(personhendelse.personidenter) ?: return Unit.also {
                log.debug("Personhendelse ikke knyttet til sak: Ignorerer ${personhendelse.hendelse} med id ${personhendelse.hendelseId} og endringstype ${personhendelse.endringstype}")
            }
        log.info("Personhendelse for sak $eksisterendeSaksnummer: Persisterer ${personhendelse.hendelse} med id ${personhendelse.hendelseId} og endringstype ${personhendelse.endringstype}")
        personhendelseRepo.lagre(personhendelse, eksisterendeSaksnummer)
    }
}
