package no.nav.su.se.bakover.service.personhendelser

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
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
import no.nav.su.se.bakover.domain.vedtak.VedtaksammendragForSak
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
        sikkerLogg.debug("Personhendelse - sjekker match på bruker og EPS. Hendelse: {}", personhendelse)
        prosesserNyHendelseForBruker(personhendelse, true)
        prosesserNyHendelseForEps(personhendelse, true)
    }

    private fun prosesserNyHendelseForBruker(
        personhendelse: Personhendelse.IkkeTilknyttetSak,
        isLiveRun: Boolean,
    ): Either<Unit, Unit> {
        val fødselsnumre = personhendelse.metadata.personidenter.mapNotNull { Fnr.tryCreate(it) }
        val fraOgMedEllerSenere = Måned.now(clock)
        val vedtaksammendragForSak = vedtakService.hentForBrukerFødselsnumreOgFraOgMedMåned(
            fødselsnumre = fødselsnumre,
            fraOgMed = fraOgMedEllerSenere,
        ).singleOrNull()

        return prosesserVedtaksammendragForSak(
            vedtaksammendragForSak = vedtaksammendragForSak,
            fraOgMedEllerSenere = fraOgMedEllerSenere,
            personhendelse = personhendelse,
            isLiveRun = isLiveRun,
            gjelderEps = false,
        )
    }

    private fun prosesserNyHendelseForEps(
        personhendelse: Personhendelse.IkkeTilknyttetSak,
        isLiveRun: Boolean,
    ): List<Either<Unit, Unit>> {
        val fødselsnumre = personhendelse.metadata.personidenter.mapNotNull { Fnr.tryCreate(it) }
        val fraOgMedEllerSenere = Måned.now(clock)
        return vedtakService.hentForEpsFødselsnumreOgFraOgMedMåned(fødselsnumre, fraOgMedEllerSenere).map {
            prosesserVedtaksammendragForSak(it, fraOgMedEllerSenere, personhendelse, isLiveRun, gjelderEps = true)
        }
    }

    private fun prosesserVedtaksammendragForSak(
        vedtaksammendragForSak: VedtaksammendragForSak?,
        fraOgMedEllerSenere: Måned,
        personhendelse: Personhendelse.IkkeTilknyttetSak,
        isLiveRun: Boolean,
        gjelderEps: Boolean,
    ): Either<Unit, Unit> {
        return when {
            vedtaksammendragForSak == null || !vedtaksammendragForSak.erInnvilgetForMånedEllerSenere(fraOgMedEllerSenere) -> Unit.left()
                .also {
                    sikkerLogg.debug(
                        "Personhendelse - Forkaster hendelse som ikke er knyttet til aktiv/løpende sak. Hendelse: {}",
                        personhendelse,
                    )
                }

            else -> {
                sikkerLogg.info(
                    "Personhendelse - treff på saksnummer {}. isLiveRun: {}, Vedtakssammendrag: {}, Hendelse: {}",
                    vedtaksammendragForSak.saksnummer,
                    isLiveRun,
                    vedtaksammendragForSak,
                    personhendelse,
                )
                if (isLiveRun) {
                    personhendelseRepo.lagre(
                        personhendelse = personhendelse.tilknyttSak(
                            id = UUID.randomUUID(),
                            sakIdSaksnummerFnr = vedtaksammendragForSak.sakInfo(),
                            gjelderEps = gjelderEps,
                            opprettet = Tidspunkt.now(clock),
                        ),
                    )
                }
                Unit.right()
            }
        }
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

    override fun dryRunPersonhendelser(personhendelser: List<Personhendelse.IkkeTilknyttetSak>): DryrunResult {
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

        return filteredHendelser.fold(DryrunResult.empty()) { acc, element ->
            val firstRes = prosesserNyHendelseForBruker(element, false)
            val secondRes = prosesserNyHendelseForEps(element, false)
            when {
                firstRes.isLeft() && secondRes.all { it.isLeft() } -> acc.incForkastet()
                firstRes.isRight() -> acc.incBruker()
                else -> acc.plusEps(secondRes.mapNotNull { it.getOrNull() }.size)
            }
        }.also {
            log.info("Dry run for personhendelser: $it")
        }
    }

    data class DryrunResult(
        val treffBruker: Int,
        val treffEps: Int,
        val forkastet: Int,
    ) {
        companion object {
            fun empty() = DryrunResult(0, 0, 0)
        }
        fun incBruker() = copy(treffBruker = treffBruker + 1)
        fun incForkastet() = copy(forkastet = forkastet + 1)
        fun plusEps(eps: Int) = copy(treffEps = treffEps + eps)
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
