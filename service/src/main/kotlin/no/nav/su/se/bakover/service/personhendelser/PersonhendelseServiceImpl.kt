package no.nav.su.se.bakover.service.personhendelser

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
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

    override fun prosesserNyHendelse(fraOgMed: Måned, personhendelse: Personhendelse.IkkeTilknyttetSak) {
        prosesserNyHendelseForBruker(fraOgMed, personhendelse, true)
        prosesserNyHendelseForEps(fraOgMed, personhendelse, true)
    }

    private fun prosesserNyHendelseForBruker(
        fraOgMedEllerSenere: Måned = Måned.now(clock),
        personhendelse: Personhendelse.IkkeTilknyttetSak,
        isLiveRun: Boolean,
    ): PersonhendelseresultatBruker {
        val fødselsnumre = personhendelse.metadata.personidenter.mapNotNull { Fnr.tryCreate(it) }
        val vedtaksammendragForSak = vedtakService.hentForBrukerFødselsnumreOgFraOgMedMåned(
            fødselsnumre = fødselsnumre,
            fraOgMed = fraOgMedEllerSenere,
        ).singleOrNull()
        if (vedtaksammendragForSak == null) {
            return PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenSakEllerVedtak(
                identer = personhendelse.metadata.personidenter,
            )
        }

        return prosesserVedtaksammendragForSak(
            vedtaksammendragForSak = vedtaksammendragForSak,
            fraOgMedEllerSenere = fraOgMedEllerSenere,
            personhendelse = personhendelse,
            isLiveRun = isLiveRun,
            gjelderEps = false,
        ).let {
            it.fold(
                {
                    PersonhendelseresultatBruker.IkkeRelevantHendelseForBruker.IngenAktiveVedtak(
                        saksnummer = vedtaksammendragForSak.saksnummer,
                        fnr = vedtaksammendragForSak.fødselsnummer,
                        identer = personhendelse.metadata.personidenter,
                    )
                },
                {
                    PersonhendelseresultatBruker.TreffPåBruker(
                        saksnummer = vedtaksammendragForSak.saksnummer,
                        fnr = vedtaksammendragForSak.fødselsnummer,
                        identer = personhendelse.metadata.personidenter,
                    )
                },
            )
        }
    }

    private fun prosesserNyHendelseForEps(
        fraOgMedEllerSenere: Måned = Måned.now(clock),
        personhendelse: Personhendelse.IkkeTilknyttetSak,
        isLiveRun: Boolean,
    ): PersonhendelseresultatEps {
        val fødselsnumre = personhendelse.metadata.personidenter.mapNotNull { Fnr.tryCreate(it) }
        val vedtaksammendragForSaker =
            vedtakService.hentForEpsFødselsnumreOgFraOgMedMåned(fødselsnumre, fraOgMedEllerSenere)
        if (vedtaksammendragForSaker.isEmpty()) {
            return PersonhendelseresultatEps.IkkeTreffPåEps(identer = personhendelse.metadata.personidenter)
        }
        return vedtaksammendragForSaker.map { vedtaksammendragForSak ->
            prosesserVedtaksammendragForSak(
                vedtaksammendragForSak,
                fraOgMedEllerSenere,
                personhendelse,
                isLiveRun,
                gjelderEps = true,
            ) to vedtaksammendragForSak
        }.map { (result, vedtaksammendragForSak) ->
            result.fold(
                {
                    PersonhendelseresultatEps.TreffPåEps.IkkeAktivtVedtak(
                        brukersSaksnummer = vedtaksammendragForSak.saksnummer,
                        brukersFnr = vedtaksammendragForSak.fødselsnummer,
                        identer = personhendelse.metadata.personidenter,
                    )
                },
                {
                    PersonhendelseresultatEps.TreffPåEps.AktivtVedtak(
                        brukersSaksnummer = vedtaksammendragForSak.saksnummer,
                        brukersFnr = vedtaksammendragForSak.fødselsnummer,
                        identer = personhendelse.metadata.personidenter,
                    )
                },
            )
        }.let {
            PersonhendelseresultatEps.TreffPåEnEllerFlereEps(it)
        }
    }

    private fun prosesserVedtaksammendragForSak(
        vedtaksammendragForSak: VedtaksammendragForSak,
        fraOgMedEllerSenere: Måned,
        personhendelse: Personhendelse.IkkeTilknyttetSak,
        isLiveRun: Boolean,
        gjelderEps: Boolean,
    ): Either<Unit, Unit> {
        return when {
            !vedtaksammendragForSak.erInnvilgetForMånedEllerSenere(fraOgMedEllerSenere) -> Unit.left()
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

    override fun dryRunPersonhendelser(
        fraOgMed: Måned,
        personhendelser: List<Personhendelse.IkkeTilknyttetSak>,
    ): DryrunResult {
        log.info("Starterdry run for personhendelser. Antall hendelser: ${personhendelser.size}. FraOgMed måned: $fraOgMed")
        return personhendelser.fold(DryrunResult.empty()) { acc, element ->
            val firstRes = prosesserNyHendelseForBruker(fraOgMed, element, false)
            val secondRes = prosesserNyHendelseForEps(fraOgMed, element, false)
            acc.leggTilHendelse(firstRes, secondRes)
        }.also {
            log.info("Dry run resultat for personhendelser: $it")
            sikkerLogg.info("Dry run resultat for personhendelser: ${it.toSikkerloggString()}")
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
                        personhendelser.map { "${it.id}->${it.antallFeiledeForsøk + 1}" }.joinToString(", ")
                    }]",
                )
                personhendelseRepo.inkrementerAntallFeiledeForsøk(personhendelser)
            }
    }
}
