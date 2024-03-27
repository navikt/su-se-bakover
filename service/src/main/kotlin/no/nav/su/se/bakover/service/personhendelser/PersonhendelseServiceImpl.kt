package no.nav.su.se.bakover.service.personhendelser

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.split
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
import java.time.Clock
import java.util.UUID

class PersonhendelseServiceImpl(
    private val sakRepo: SakRepo,
    private val personhendelseRepo: PersonhendelseRepo,
    private val vedtakService: VedtakService,
    private val oppgaveServiceImpl: OppgaveService,
    private val clock: Clock,
) : PersonhendelseService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun prosesserNyHendelse(personhendelse: Personhendelse.IkkeTilknyttetSak) {
        prosesserNyHendelseForBruker(personhendelse, true)
        prosesserNyHendelseForEps(personhendelse, true)
    }

    private fun prosesserNyHendelseForBruker(
        personhendelse: Personhendelse.IkkeTilknyttetSak,
        isLiveRun: Boolean,
    ): Either<Unit, Unit> {
        val fødselsnumre = personhendelse.metadata.personidenter.mapNotNull { Fnr.tryCreate(it) }
        val fraOgMedEllerSenere = Måned.now(clock)
        val eksisterendeSakIdOgNummer = vedtakService.hentForFødselsnumreOgFraOgMedMåned(
            fødselsnumre = fødselsnumre,
            fraOgMed = fraOgMedEllerSenere,
        ).tilInnvilgetForMånedEllerSenere(fraOgMedEllerSenere).sakInfo.filter {
            fødselsnumre.contains(it.fnr)
        }.ifEmpty {
            return Unit.left().also {
                sikkerLogg.debug(
                    "Forkaster personhendelse (bruker) som ikke er knyttet til aktiv/løpende sak: {}",
                    personhendelse,
                )
            }
        }.single()
        sikkerLogg.debug("Personhendelse (bruker) for sak: {}", personhendelse)
        if (isLiveRun) {
            personhendelseRepo.lagre(
                personhendelse = personhendelse.tilknyttSak(
                    UUID.randomUUID(),
                    eksisterendeSakIdOgNummer,
                    gjelderEps = false,
                    Tidspunkt.now(clock),
                ),
            )
        }
        return Unit.right()
    }

    private fun prosesserNyHendelseForEps(
        personhendelse: Personhendelse.IkkeTilknyttetSak,
        isLiveRun: Boolean,
    ): Either<Unit, Unit> {
        val fødselsnumre = personhendelse.metadata.personidenter.mapNotNull { Fnr.tryCreate(it) }
        val fraOgMedEllerSenere = Måned.now(clock)
        sakRepo.hentSakInfoForEpsFnrFra(fødselsnumre, fraOgMedEllerSenere).forEach { sakInfo ->
            sikkerLogg.debug("Personhendelse (EPS) for sak: {}", personhendelse)
            if (isLiveRun) {
                personhendelseRepo.lagre(
                    personhendelse = personhendelse.tilknyttSak(UUID.randomUUID(), sakInfo, true, Tidspunkt.now(clock)),
                )
            }
            return Unit.right()
        }

        return Unit.left()
    }

    override fun opprettOppgaverForPersonhendelser() {
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

    override fun dryRunPersonhendelser(personhendelser: List<Personhendelse.IkkeTilknyttetSak>): Pair<List<Unit>, List<Unit>> {
        val seenFnr = mutableSetOf<String>()
        // Vi prøver emulere produksjonskoden som slår sammen personhendelser på samme sak.
        // Et mulig unntak vil være 2 som får SU som bor sammen. Da kan det hende vi får en ekstra oppgave på de.
        // Men dette vil kun være et godt nok estimat
        val filteredHendelser = personhendelser.filter { hendelse ->
            val identer = hendelse.metadata.personidenter
            val seenAny = identer.any { seenFnr.contains(it) }
            seenFnr.addAll(identer)
            !seenAny
        }

        val res = filteredHendelser.map {
            val firstRes = prosesserNyHendelseForBruker(it, false)
            val secondRes = prosesserNyHendelseForEps(it, false)

            if (firstRes.isRight() || secondRes.isRight()) Unit.right() else Unit.left()
        }

        return res.split().let {
            val forkastet = it.first
            val success = it.second
            log.info("Dry run for personhendelser: ${success.size} hadde blitt lagret og opprettet oppgave for, ${forkastet.size} hadde blitt forkastet")
            success to forkastet
        }
    }

    private fun opprettOppgaveForSak(
        sak: Sak,
        personhendelser: NonEmptyList<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>,
    ) {
        val personhendelseIder = personhendelser.map { it.id }

        oppgaveServiceImpl.opprettOppgaveMedSystembruker(
            OppgaveConfig.Personhendelse(
                saksnummer = sak.saksnummer,
                personhendelse = personhendelser.toNonEmptySet(),
                fnr = sak.fnr,
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
    }
}
