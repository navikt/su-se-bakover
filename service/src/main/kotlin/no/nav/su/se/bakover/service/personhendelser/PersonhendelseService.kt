package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.database.hendelse.PersonhendelseRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
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
    private val oppgaveServiceImpl: OppgaveService,
    private val personService: PersonService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun prosesserNyHendelse(personhendelse: Personhendelse.IkkeTilknyttetSak) {
        val eksisterendeSakIdOgNummer =
            sakRepo.hentSakIdOgNummerForIdenter(personhendelse.metadata.personidenter) ?: return Unit.also {
                log.debug("Personhendelse ikke knyttet til sak: Ignorerer ${personhendelse.hendelse} med hendelsesid ${personhendelse.metadata.hendelseId}, offset ${personhendelse.metadata.offset}, partisjon ${personhendelse.metadata.partisjon} og endringstype ${personhendelse.endringstype}")
                sikkerLogg.debug("Personhendelse ikke knyttet til sak: $personhendelse")
            }
        log.info("Personhendelse for sak id ${ eksisterendeSakIdOgNummer.sakId }: Persisterer ${personhendelse.hendelse} med hendelsesid ${personhendelse.metadata.hendelseId}, offset ${personhendelse.metadata.offset}, partisjon ${personhendelse.metadata.partisjon} og endringstype ${personhendelse.endringstype}")
        sikkerLogg.debug("Personhendelse for sak: $personhendelse")
        personhendelseRepo.lagre(
            personhendelse = personhendelse.tilknyttSak(UUID.randomUUID(), eksisterendeSakIdOgNummer),
        )
    }

    fun opprettOppgaverForPersonhendelser() = personhendelseRepo.hentPersonhendelserUtenOppgave().forEach { personhendelse ->
        val sak = sakRepo.hentSak(personhendelse.sakId)!!

        personService.hentAktørIdMedSystembruker(sak.fnr).fold(
            ifLeft = { log.error("Fant ikke person for personhendelse med id: ${personhendelse.id}") },
            ifRight = { aktørId ->
                oppgaveServiceImpl.opprettOppgave(
                    OppgaveConfig.Personhendelse(
                        saksnummer = personhendelse.saksnummer,
                        beskrivelse = OppgavebeskrivelseMapper.map(personhendelse.hendelse),
                        aktørId = aktørId,
                    )
                ).map { oppgaveId ->
                    personhendelseRepo.lagre(
                        personhendelse.tilSendtTilOppgave(oppgaveId)
                    )
                }
            }
        )
    }
}
