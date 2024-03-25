package no.nav.su.se.bakover.service.personhendelser

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.tilInnvilgetForMånedEllerSenere
import no.nav.su.se.bakover.vedtak.application.VedtakService
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
            personhendelse = personhendelse.tilknyttSak(UUID.randomUUID(), eksisterendeSakIdOgNummer, Tidspunkt.now(clock)),
        )
    }

    fun opprettOppgaverForPersonhendelser() {
        val personhendelser = personhendelseRepo.hentPersonhendelserUtenOppgave()
        personhendelser.groupBy { it.sakId }
            .forEach loop@{ (sakId, personhendelser) ->
                val sak = sakRepo.hentSak(sakId)
                if (sak == null) {
                    log.error("Fant ikke sak for personhendelser med id'er: ${personhendelser.map { it.id }}")
                    return@loop // continue
                }
                opprettOppgaveForSak(sak, personhendelser.toNonEmptyList())
            }
    }

    private fun opprettOppgaveForSak(
        sak: Sak,
        personhendelser: NonEmptyList<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>,
    ) {
        val personhendelseIder = personhendelser.map { it.id }

        personService.hentAktørIdMedSystembruker(sak.fnr).fold(
            ifLeft = { log.error("Fant ikke person for personhendelser med id'er: $personhendelseIder") },
            ifRight = { aktørId ->
                oppgaveServiceImpl.opprettOppgaveMedSystembruker(
                    OppgaveConfig.Personhendelse(
                        saksnummer = sak.saksnummer,
                        personhendelse = personhendelser.toNonEmptySet(),
                        aktørId = aktørId,
                        clock = clock,
                    ),
                ).map { oppgaveResponse ->
                    log.info("Opprettet oppgave for personhendelser med id'er: $personhendelseIder")
                    personhendelser.map { it.tilSendtTilOppgave(oppgaveResponse.oppgaveId) }
                        .let { personhendelseRepo.lagre(it) }
                }
                    .mapLeft {
                        log.error(
                            "Kunne ikke opprette oppgave for personhendelser med id'er: $personhendelseIder. Antall feilede forsøk på settet: [${
                                personhendelser.map { "${it.id}->${it.antallFeiledeForsøk + 1}" }.joinToString { ", " }
                            }]",
                        )
                        personhendelseRepo.inkrementerAntallFeiledeForsøk(personhendelser)
                    }
            },
        )
    }
}
