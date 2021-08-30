package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.database.hendelse.PersonhendelseRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.oppgave.OppgaveService
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
    private val oppgaveServiceImpl: OppgaveService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun prosesserNyHendelse(personhendelse: Personhendelse.Ny) {
        val eksisterendeSakId =
            sakRepo.hentSakIdForIdenter(personhendelse.personidenter) ?: return Unit.also {
                log.debug("Personhendelse ikke knyttet til sak: Ignorerer ${personhendelse.hendelse} med hendelsesid ${personhendelse.metadata.hendelseId}, offset ${personhendelse.metadata.offset}, partisjon ${personhendelse.metadata.partisjon} og endringstype ${personhendelse.endringstype}")
                sikkerLogg.debug("Personhendelse ikke knyttet til sak: $personhendelse")
            }
        log.info("Personhendelse for sak id $eksisterendeSakId: Persisterer ${personhendelse.hendelse} med hendelsesid ${personhendelse.metadata.hendelseId}, offset ${personhendelse.metadata.offset}, partisjon ${personhendelse.metadata.partisjon} og endringstype ${personhendelse.endringstype}")
        sikkerLogg.debug("Personhendelse for sak: $personhendelse")
        personhendelseRepo.lagre(
            personhendelse = personhendelse,
            id = UUID.randomUUID(),
            sakId = eksisterendeSakId,
        )
    }

    fun opprettOppgaverForPersonhendelser() = personhendelseRepo.hentPersonhendelserUtenOppgave().forEach { personhendelse ->
        oppgaveServiceImpl.opprettOppgave(
            OppgaveConfig.Personhendelse(
                saksnummer = personhendelse.saksnummer,
                beskrivelse = OppgavebeskrivelseMapper.map(personhendelse.hendelse),
                aktørId = personhendelse.gjeldendeAktørId,
            )
        ).map {
            personhendelseRepo.oppdaterOppgave(personhendelse.id, it)
        }
    }
}
