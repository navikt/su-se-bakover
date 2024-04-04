package no.nav.su.se.bakover.service.personhendelser

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
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
    ): PersonhendelseresultatBruker {
        val fødselsnumre = personhendelse.metadata.personidenter.mapNotNull { Fnr.tryCreate(it) }
        val fraOgMedEllerSenere = Måned.now(clock)
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
        personhendelse: Personhendelse.IkkeTilknyttetSak,
        isLiveRun: Boolean,
    ): PersonhendelseresultatEps {
        val fødselsnumre = personhendelse.metadata.personidenter.mapNotNull { Fnr.tryCreate(it) }
        val fraOgMedEllerSenere = Måned.now(clock)
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

    sealed interface PersonhendelseresultatBruker {
        fun ikkeTreff(): Boolean = this is IkkeRelevantHendelseForBruker

        fun unikeSaksnummer(): List<Saksnummer> = when (this) {
            is IkkeRelevantHendelseForBruker -> emptyList()
            is TreffPåBruker -> listOf(this.saksnummer)
        }

        sealed interface IkkeRelevantHendelseForBruker : PersonhendelseresultatBruker {
            val identer: List<String>

            /** Enten har vi ikke en sak, eller så har ikke den saken vedtak av typen søknad, endring, opphør. */
            data class IngenSakEllerVedtak(override val identer: List<String>) : IkkeRelevantHendelseForBruker

            /** Vi har en sak med vedtak av typen søknad, endring, opphør; men ingen av disse var aktive etter fraOgMed dato */
            data class IngenAktiveVedtak(override val identer: List<String>, val saksnummer: Saksnummer, val fnr: Fnr) :
                IkkeRelevantHendelseForBruker
        }

        data class TreffPåBruker(val saksnummer: Saksnummer, val fnr: Fnr, val identer: List<String>) :
            PersonhendelseresultatBruker
    }

    sealed interface PersonhendelseresultatEps {
        fun ikkeTreff(): Boolean

        fun unikeSaksnummer(): List<Saksnummer> = when (this) {
            is IkkeTreffPåEps -> emptyList()
            is TreffPåEnEllerFlereEps -> this.treff.map { it.brukersSaksnummer }
        }.distinct().sortedBy { it.nummer }

        data class IkkeTreffPåEps(val identer: List<String>) : PersonhendelseresultatEps {
            override fun ikkeTreff(): Boolean = true
        }

        data class TreffPåEnEllerFlereEps(val treff: List<TreffPåEps>) : PersonhendelseresultatEps {
            override fun ikkeTreff(): Boolean = treff.all { it is TreffPåEps.IkkeAktivtVedtak }
        }

        sealed interface TreffPåEps {
            val brukersSaksnummer: Saksnummer
            val brukersFnr: Fnr
            val identer: List<String>

            data class AktivtVedtak(
                override val brukersSaksnummer: Saksnummer,
                override val brukersFnr: Fnr,
                override val identer: List<String>,
            ) : TreffPåEps

            data class IkkeAktivtVedtak(
                override val brukersSaksnummer: Saksnummer,
                override val brukersFnr: Fnr,
                override val identer: List<String>,
            ) : TreffPåEps
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
        return personhendelser.fold(DryrunResult.empty()) { acc, element ->
            val firstRes = prosesserNyHendelseForBruker(element, false)
            val secondRes = prosesserNyHendelseForEps(element, false)
            acc.leggTilHendelse(firstRes, secondRes)
        }.also {
            log.info("Dry run for personhendelser: $it")
            sikkerLogg.info("Dry run for personhendelser: ${it.toSikkerloggString()}")
        }
    }

    data class DryrunResult(
        val perHendelse: List<DryRunResultPerHendelse>,
    ) {
        companion object {
            fun empty() = DryrunResult(emptyList())
        }

        fun leggTilHendelse(resultatBruker: PersonhendelseresultatBruker, resultatEps: PersonhendelseresultatEps) =
            DryrunResult(perHendelse + DryRunResultPerHendelse(resultatBruker, resultatEps))

        val antallForkastet: Int by lazy { perHendelse.count { it.ikkeTreff() } }
        val antallBruker: Int by lazy { perHendelse.count { it.resultatBruker is PersonhendelseresultatBruker.TreffPåBruker } }
        val antallEps: Int by lazy { perHendelse.count { it.resultatEps is PersonhendelseresultatEps.TreffPåEnEllerFlereEps } }
        val antallOppgaver: Int by lazy {
            oppgaver.size
        }

        val forkastet: List<DryRunResultPerHendelse> by lazy { perHendelse.filter { it.ikkeTreff() } }
        val bruker: List<PersonhendelseresultatBruker> by lazy { perHendelse.map { it.resultatBruker } }
        val eps: List<PersonhendelseresultatEps> by lazy { perHendelse.map { it.resultatEps } }
        val oppgaver: List<Saksnummer> by lazy {
            (bruker.flatMap { it.unikeSaksnummer() } + eps.flatMap { it.unikeSaksnummer() }).distinct().sortedBy { it.nummer }
        }

        data class DryRunResultPerHendelse(
            val resultatBruker: PersonhendelseresultatBruker,
            val resultatEps: PersonhendelseresultatEps,
        ) {
            fun ikkeTreff(): Boolean = resultatBruker.ikkeTreff() && resultatEps.ikkeTreff()
        }

        override fun toString() =
            "DryrunResult(antallForkastet=$antallForkastet, antallBruker=$antallBruker, antallEps=$antallEps, antallOppgaver=$antallOppgaver). Se sikkerlogg for mer detaljer"

        fun toSikkerloggString(): String =
            "DryrunResult(antallForkastet=$antallForkastet, antallBruker=$antallBruker, antallEps=$antallEps, antallOppgaver=$antallOppgaver). Forkastet: $forkastet, Bruker: $bruker, Eps: $eps, Oppgaver: $oppgaver"
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
