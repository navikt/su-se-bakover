package no.nav.su.se.bakover.service.personhendelser

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.tilInnvilgetForMånedEllerSenere
import no.nav.su.se.bakover.service.vedtak.VedtakService
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class PersonhendelseService(
    private val sakRepo: SakRepo,
    private val personhendelseRepo: PersonhendelseRepo,
    private val vedtakService: VedtakService,
    private val oppgaveServiceImpl: OppgaveService,
    private val personService: PersonService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun prosesserNyHendelse(personhendelse: Personhendelse.IkkeTilknyttetSak) {
        val fødselsnumre = personhendelse.metadata.personidenter.mapNotNull { Fnr.tryCreate(it) }
        val fraOgMedEllerSenere = Måned.now(clock)
        val eksisterendeSakIdOgNummer = vedtakService.hentForFødselsnumreOgFraOgMedMåned(
            fødselsnumre = fødselsnumre,
            fraOgMed = fraOgMedEllerSenere,
        ).tilInnvilgetForMånedEllerSenere(fraOgMedEllerSenere).sakInfo.filter {
            fødselsnumre.contains(it.fnr)
        }.ifEmpty {
            return Unit.also {
                sikkerLogg.debug("Forkaster personhendelse som ikke er knyttet til aktiv/løpende sak: $personhendelse")
            }
        }.single()
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
                    ).map { oppgaveResponse ->
                        log.info("Opprettet oppgave for personhendelse med id: ${personhendelse.id}")
                        personhendelseRepo.lagre(
                            personhendelse.tilSendtTilOppgave(oppgaveResponse.oppgaveId),
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
