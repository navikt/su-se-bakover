package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class PersonhendelseService(
    private val sakRepo: SakRepo,
    private val personhendelseRepo: PersonhendelseRepo,
    private val oppgaveServiceImpl: OppgaveService,
    private val personService: PersonService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun prosesserNyHendelse(personhendelse: Personhendelse.IkkeTilknyttetSak) {
        val eksisterendeSakIdOgNummer =
            sakRepo.hentSakInfoForIdenter(personhendelse.metadata.personidenter) ?: return Unit.also {
                sikkerLogg.debug("Personhendelse ikke knyttet til sak: $personhendelse")
            }
        sikkerLogg.debug("Personhendelse for sak: $personhendelse")
        personhendelseRepo.lagre(
            personhendelse = personhendelse.tilknyttSak(UUID.randomUUID(), eksisterendeSakIdOgNummer),
        )
    }

    /*
    TODO ai 02.09: Kan vurdere på sikt å lage kun en oppgave for flere personhendelser for samme bruker,
        f.eks hvis endringstypen er ANNULERT eller KORRIGERT.
    */
    fun opprettOppgaverForPersonhendelser() =
        personhendelseRepo.hentPersonhendelserUtenOppgave().forEach loop@{ personhendelse ->
            val sak = sakRepo.hentSak(personhendelse.sakId)
            if (sak == null) {
                log.error("Fant ikke sak for personhendelse med id: ${personhendelse.id}")
                return@loop // continue
            }

            personService.hentAktørIdMedSystembruker(sak.fnr).fold(
                ifLeft = { log.error("Fant ikke person for personhendelse med id: ${personhendelse.id}") },
                ifRight = { aktørId ->
                    oppgaveServiceImpl.opprettOppgaveMedSystembruker(
                        OppgaveConfig.Personhendelse(
                            saksnummer = personhendelse.saksnummer,
                            personhendelsestype = personhendelse.hendelse,
                            aktørId = aktørId,
                            clock = clock,
                        ),
                    ).map { oppgaveId ->
                        log.info("Opprettet oppgave for personhendelse med id: ${personhendelse.id}")
                        personhendelseRepo.lagre(
                            personhendelse.tilSendtTilOppgave(oppgaveId),
                        )
                    }
                        .mapLeft {
                            log.error("Kunne ikke opprette oppgave for personhendelse med id: ${personhendelse.id}. Antall feilede forsøk: ${personhendelse.antallFeiledeForsøk + 1}")
                            personhendelseRepo.inkrementerAntallFeiledeForsøk(
                                personhendelse,
                            )
                        }
                },
            )
        }
}
