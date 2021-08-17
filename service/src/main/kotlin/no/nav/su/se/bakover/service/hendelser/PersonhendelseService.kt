package no.nav.su.se.bakover.service.hendelser

import no.nav.su.se.bakover.database.hendelse.PersonhendelseRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * TODO jah/Aryan: Neste PR. Lag oppgave dersom det er en uførehendelse.
 * oppgavetype - vurder konsekvens om ytelse
 * 1 uke frist på oppgaven
 * se till att saksnummer kommer in i beskrivelsen. 4815
 */
class PersonhendelseService(
    private val sakRepo: SakRepo,
    private val personhendelseRepo: PersonhendelseRepo,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun prosesserNyHendelse(personhendelse: Personhendelse.Ny) {
        val eksisterendeSakId =
            sakRepo.hentSakIdForIdenter(personhendelse.personidenter) ?: return Unit.also {
                log.debug("Personhendelse ikke knyttet til sak: Ignorerer ${personhendelse.hendelse} med id ${personhendelse.hendelseId} og endringstype ${personhendelse.endringstype}")
            }
        log.info("Personhendelse for sak id $eksisterendeSakId: Persisterer ${personhendelse.hendelse} med hendelsesid ${personhendelse.hendelseId} og endringstype ${personhendelse.endringstype}")
        personhendelseRepo.lagre(
            personhendelse = personhendelse,
            id = UUID.randomUUID(),
            sakId = eksisterendeSakId,
        )
    }
}
