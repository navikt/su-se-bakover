package no.nav.su.se.bakover.service.personhendelser

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.personhendelse.Fødselsnummerhendelse
import no.nav.su.se.bakover.domain.personhendelse.FødselsnummerhendelseRepo
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.personhendelse.PersonhendelseRepo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtaksammendragForSak
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.slf4j.LoggerFactory
import person.domain.AdresseopplysningerMedMetadata
import person.domain.KunneIkkeHentePerson
import person.domain.PersonOppslag
import java.time.Clock
import java.util.UUID

class PersonhendelseServiceImpl(
    private val sakRepo: SakRepo,
    private val personhendelseRepo: PersonhendelseRepo,
    private val fødselsnummerhendelseRepo: FødselsnummerhendelseRepo,
    private val personOppslag: PersonOppslag,
    private val vedtakService: VedtakService,
    private val oppgaveServiceImpl: OppgaveService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : PersonhendelseService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun prosesserNyHendelse(fraOgMed: Måned, personhendelse: Personhendelse.IkkeTilknyttetSak) {
        prosesserNyHendelseForBruker(fraOgMed, personhendelse, true)
        prosesserNyHendelseForEps(fraOgMed, personhendelse, true)
    }

    override fun lagreFødselsnummerhendelseForBerørteSaker(personidenter: List<String>) {
        personidenter
            .mapNotNull { Fnr.tryCreate(it) }
            .distinct()
            .flatMap { sakRepo.hentSakInfo(it) }
            .distinctBy { it.sakId }
            .forEach { fødselsnummerhendelseRepo.lagre(it.sakId) }
    }

    override fun oppdaterFødselsnummerForUbehandledeHendelser() {
        fødselsnummerhendelseRepo.hentUbehandlede().forEach { hendelse ->
            Either.catch {
                prosesserFødselsnummerhendelse(hendelse)
            }.onLeft {
                // Lar raden ligge ubehandlet – jobben plukker den opp igjen ved neste kjøring.
                log.warn("Kunne ikke prosessere fødselsnummerhendelse ${hendelse.id} for sak ${hendelse.sakId}. Forsøker igjen senere.", it)
            }
        }
    }

    private fun prosesserFødselsnummerhendelse(hendelse: Fødselsnummerhendelse) {
        val sakInfo = sakRepo.hentSakInfo(hendelse.sakId) ?: run {
            markerProsessert(hendelse.id)
            return
        }
        val gjeldendeFnr = personOppslag.personMedSystembruker(sakInfo.fnr, sakInfo.type)
            .getOrElse { error("Kunne ikke hente gjeldende fnr fra PDL for sak ${sakInfo.sakId}: $it") }
            .ident.fnr

        sessionFactory.withTransactionContext { tx ->
            if (gjeldendeFnr != sakInfo.fnr) {
                sakRepo.oppdaterFødselsnummer(
                    sakId = sakInfo.sakId,
                    gammeltFnr = sakInfo.fnr,
                    nyttFnr = gjeldendeFnr,
                    endretAv = NavIdentBruker.Saksbehandler.systembruker(),
                    endretTidspunkt = Tidspunkt.now(clock),
                    sessionContext = tx,
                )
            }
            fødselsnummerhendelseRepo.markerProsessert(
                id = hendelse.id,
                tidspunkt = Tidspunkt.now(clock),
                transactionContext = tx,
            )
        }
    }

    private fun markerProsessert(id: UUID) {
        sessionFactory.withTransactionContext { tx ->
            fødselsnummerhendelseRepo.markerProsessert(
                id = id,
                tidspunkt = Tidspunkt.now(clock),
                transactionContext = tx,
            )
        }
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
        vurderPersonhendelserMotPdl()

        val personhendelser = personhendelseRepo.hentPersonhendelserKlareForOppgave()
        personhendelser.groupBy { it.sakId }
            .forEach loop@{ (sakId, personhendelserPåSak) ->
                val sak = sakRepo.hentSak(sakId)
                if (sak == null) {
                    log.error("Fant ikke sak for personhendelser med id'er: ${personhendelserPåSak.map { it.id }}")
                    return@loop // continue
                }
                personhendelserPåSak.groupBy { it.grupperingsnøkkelForOppgave() }
                    .values
                    .forEach { personhendelser ->
                        opprettOppgaveForSak(sak, personhendelser.toNonEmptyList())
                    }
            }
    }

    // TODO: denne må vel kunne slettes? ingen kommentar om noe fornuftig som kan forklare det her
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
        val personhendelserMedPdlTreffadresse = leggTilPdlTreffadresseVedOppgaveopprettelse(personhendelser)

        oppgaveServiceImpl.opprettOppgaveMedSystembruker(
            OppgaveConfig.Personhendelse(
                saksnummer = sak.saksnummer,
                personhendelse = personhendelserMedPdlTreffadresse.toNonEmptySet(),
                fnr = sak.fnr,
                clock = clock,
                sakstype = sak.type,
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

    private fun vurderPersonhendelserMotPdl() {
        val personhendelser = personhendelseRepo.hentPersonhendelserUtenPdlVurdering()
        if (personhendelser.isEmpty()) return

        val vurderinger = personhendelser.mapNotNull { vurderPersonhendelseMotPdl(it) }
        if (vurderinger.isNotEmpty()) {
            personhendelseRepo.oppdaterPdlVurdering(vurderinger)
        }
    }

    private fun vurderPersonhendelseMotPdl(
        personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave,
    ): PersonhendelseRepo.PdlVurdering? {
        return when (personhendelse.hendelse) {
            is Personhendelse.Hendelse.Bostedsadresse -> vurderBostedsadressehendelseMotPdl(personhendelse)
            is Personhendelse.Hendelse.Kontaktadresse -> vurderingUtenPdlOppslag(
                personhendelse = personhendelse,
                hendelsestype = "Kontaktadresse",
                begrunnelse = "Kontaktadresse vurderes uten PDL-sjekk.",
            )
            is Personhendelse.Hendelse.Dødsfall,
            is Personhendelse.Hendelse.Sivilstand,
            is Personhendelse.Hendelse.UtflyttingFraNorge,
            -> vurderingUtenPdlOppslag(
                personhendelse = personhendelse,
                begrunnelse = "Hendelsetype vurderes uten PDL-sjekk.",
                hendelsestype = personhendelse.hendelse.javaClass.simpleName,
            )
        }
    }

    /**
     * Vurdering av bostedsadressehendelse mot PDL:
     * - [KunneIkkeHentePerson.FantIkkePerson]: vi lagrer en vurdering som ikke relevant (ingen retry).
     * - [KunneIkkeHentePerson.IkkeTilgangTilPerson] / [KunneIkkeHentePerson.Ukjent]:
     *   vi returnerer null, slik at hendelsen forblir `pdl_vurdert=false` og plukkes opp igjen
     *   i neste jobbkjøring (retry).
     * - Success: vi lagrer vanlig PDL-vurdering med diff.
     */
    private fun vurderBostedsadressehendelseMotPdl(
        personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave,
    ): PersonhendelseRepo.PdlVurdering? {
        val fnr = personhendelse.metadata.personidenter.firstNotNullOfOrNull { Fnr.tryCreate(it) }
        if (fnr == null) {
            return ikkeRelevantVurdering(
                personhendelse = personhendelse,
                begrunnelse = "Mangler fnr i metadata.personidenter, kan ikke slå opp i PDL.",
            )
        }

        return personOppslag.bostedsadresseMedMetadataForSystembruker(fnr).fold(
            ifLeft = { feil ->
                when (feil) {
                    KunneIkkeHentePerson.FantIkkePerson -> {
                        ikkeRelevantVurdering(
                            personhendelse = personhendelse,
                            begrunnelse = "Person ikke funnet i PDL.",
                        )
                    }

                    KunneIkkeHentePerson.IkkeTilgangTilPerson,
                    KunneIkkeHentePerson.Ukjent,
                    -> {
                        log.warn(
                            "Kunne ikke vurdere personhendelse {} mot PDL nå. Feil: {}. Lar hendelsen stå uvurdert for retry.",
                            personhendelse.id,
                            feil,
                        )
                        // Ikke persister vurdering nå -> blir hentet igjen via hentPersonhendelserUtenPdlVurdering().
                        null
                    }
                }
            },
            ifRight = { adresseopplysninger ->
                val alleAdresser = adresseopplysninger.bostedsadresser
                val matchendeAdresseForHendelse = alleAdresser.firstOrNull {
                    it.hendelseIder.contains(personhendelse.metadata.hendelseId)
                }
                val gjeldendeAdresseForHendelse = matchendeAdresseForHendelse?.takeIf { !it.historisk }
                val historiskAdresseForHendelse = matchendeAdresseForHendelse?.takeIf { it.historisk }

                val harBostedsadresseNå = adresseopplysninger.bostedsadresser.any { !it.historisk }
                val harKontaktadresseNå: Boolean? = null

                val beslutning = vurderBostedsadressebeslutning(
                    endringstype = personhendelse.endringstype,
                    hendelseId = personhendelse.metadata.hendelseId,
                    tidligereHendelseId = personhendelse.metadata.tidligereHendelseId,
                    matchendeForekomst = matchendeAdresseForHendelse,
                    historiskeOgGjeldendeForekomster = alleAdresser,
                )

                PersonhendelseRepo.PdlVurdering(
                    id = personhendelse.id,
                    relevant = beslutning.relevant,
                    pdlDiff = serialize(
                        PdlDiff(
                            hendelsestype = personhendelse.hendelse::class.simpleName ?: "Ukjent",
                            endringstype = personhendelse.endringstype.name,
                            relevant = beslutning.relevant,
                            begrunnelse = beslutning.grunnlag.joinToString(" | "),
                            reasons = beslutning.grunnlag,
                            changedFields = beslutning.changedFields,
                            gjelderEps = personhendelse.gjelderEps,
                            harBostedsadresseNå = harBostedsadresseNå,
                            harKontaktadresseNå = harKontaktadresseNå,
                            hendelseIdFunnetIPdl = matchendeAdresseForHendelse != null,
                            korrelertPåGjeldendeForekomst = gjeldendeAdresseForHendelse != null,
                            korrelertPåHistoriskForekomst = historiskAdresseForHendelse != null,
                            pdlTreffFolkeregistermetadata = matchendeAdresseForHendelse?.folkeregistermetadata,
                        ),
                    ),
                )
            },
        )
    }

    private fun vurderingUtenPdlOppslag(
        personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave,
        begrunnelse: String,
        hendelsestype: String,
    ): PersonhendelseRepo.PdlVurdering {
        return PersonhendelseRepo.PdlVurdering(
            id = personhendelse.id,
            relevant = true,
            pdlDiff = serialize(
                PdlDiff(
                    hendelsestype = hendelsestype,
                    endringstype = personhendelse.endringstype.name,
                    relevant = true,
                    begrunnelse = begrunnelse,
                    reasons = listOf(begrunnelse),
                    gjelderEps = personhendelse.gjelderEps,
                ),
            ),
        )
    }

    private fun ikkeRelevantVurdering(
        personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave,
        begrunnelse: String,
    ): PersonhendelseRepo.PdlVurdering {
        return PersonhendelseRepo.PdlVurdering(
            id = personhendelse.id,
            relevant = false,
            pdlDiff = serialize(
                PdlDiff(
                    hendelsestype = personhendelse.hendelse::class.simpleName ?: "Ukjent",
                    endringstype = personhendelse.endringstype.name,
                    relevant = false,
                    begrunnelse = begrunnelse,
                    reasons = listOf(begrunnelse),
                    gjelderEps = personhendelse.gjelderEps,
                ),
            ),
        )
    }

    /**
     * Regler for bostedsadresse:
     * 1) Hendelsen må korreleres mot en bostedsadresseforekomst (hendelseId-match).
     * 2) OPPRETTET er relevant ved treff, også når forekomsten er historisk (ikke gjeldende).
     * 3) KORRIGERT sammenligner før/etter kun på gateadresse(adressenavn+husnummer+husbokstav) og postnummer.
     * 4) Kosmetiske forskjeller i tekst (case/whitespace) ignoreres.
     * 5) OPPHØRT er kun relevant for gjeldende forekomst, eller historisk forekomst med gyldighetsår > 2020.
     * 6) ANNULLERT er ikke relevant for flytting/reell adresseendring.
     */
    private fun vurderBostedsadressebeslutning(
        endringstype: Personhendelse.Endringstype,
        hendelseId: String,
        tidligereHendelseId: String?,
        matchendeForekomst: AdresseopplysningerMedMetadata.Adresseopplysning?,
        historiskeOgGjeldendeForekomster: List<AdresseopplysningerMedMetadata.Adresseopplysning>,
    ): Adressebeslutning {
        if (matchendeForekomst == null) {
            return Adressebeslutning(
                relevant = false,
                grunnlag = listOf("HendelseId=$hendelseId finnes ikke på bostedsadresseforekomst i PDL."),
            )
        }

        return when (endringstype) {
            Personhendelse.Endringstype.OPPRETTET ->
                Adressebeslutning(
                    relevant = true,
                    grunnlag = listOf("opprettet"),
                )

            Personhendelse.Endringstype.KORRIGERT -> {
                if (tidligereHendelseId == null) {
                    return Adressebeslutning(
                        relevant = false,
                        grunnlag = listOf("KORRIGERT mangler tidligereHendelseId."),
                    )
                }

                val førFraPdl = historiskeOgGjeldendeForekomster.firstOrNull {
                    it.hendelseIder.contains(tidligereHendelseId)
                } ?: return Adressebeslutning(
                    relevant = false,
                    grunnlag = listOf("KORRIGERT -  mangler før-forekomst i PDL-historikk for tidligereHendelseId=$tidligereHendelseId. Kan ikke korrigere noe som ikke finnes"),
                )

                val changedFields = finnEndredeFelter(førFraPdl, matchendeForekomst)
                Adressebeslutning(
                    relevant = changedFields.isNotEmpty(),
                    grunnlag = listOf("KORRIGERT - vurdert med før-forekomst fra PDL-historikk."),
                    changedFields = changedFields,
                )
            }

            Personhendelse.Endringstype.OPPHØRT,
            -> {
                if (!matchendeForekomst.historisk) {
                    Adressebeslutning(
                        relevant = true,
                        grunnlag = listOf("${endringstype.name} på gjeldende bostedsadresse kan påvirke ytelse."),
                    )
                } else {
                    val gyldighetsaar = matchendeForekomst.folkeregistermetadata.gyldighetsaar()
                    val relevantHistoriskOpphoer = gyldighetsaar != null && gyldighetsaar > SU_INNFOERT_AAR
                    Adressebeslutning(
                        relevant = relevantHistoriskOpphoer,
                        grunnlag = listOf(
                            if (relevantHistoriskOpphoer) {
                                "${endringstype.name} på historisk bostedsadresse er relevant når gyldighetsår er etter $SU_INNFOERT_AAR (gyldighetsår=$gyldighetsaar)."
                            } else {
                                "${endringstype.name} på historisk bostedsadresse er ikke relevant når gyldighetsår er $SU_INNFOERT_AAR eller eldre/mangler (gyldighetsår=${gyldighetsaar ?: "ukjent"})."
                            },
                        ),
                    )
                }
            }
            Personhendelse.Endringstype.ANNULLERT,
            -> Adressebeslutning(
                relevant = false,
                grunnlag = listOf("${endringstype.name} er ikke relevant for flytting/reell adresseendring."),
            )
        }
    }

    private data class PdlDiff(
        val hendelsestype: String,
        val endringstype: String,
        val relevant: Boolean,
        val begrunnelse: String,
        val reasons: List<String> = emptyList(),
        val changedFields: List<String> = emptyList(),
        val gjelderEps: Boolean,
        val harBostedsadresseNå: Boolean? = null,
        val harKontaktadresseNå: Boolean? = null,
        val hendelseIdFunnetIPdl: Boolean? = null,
        val korrelertPåGjeldendeForekomst: Boolean = false,
        val korrelertPåHistoriskForekomst: Boolean = false,
        val pdlTreffFolkeregistermetadata: AdresseopplysningerMedMetadata.Folkeregistermetadata? = null,
    )

    private fun leggTilPdlTreffadresseVedOppgaveopprettelse(
        personhendelser: NonEmptyList<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>,
    ): NonEmptyList<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave> {
        val opplysningerPerFnr = mutableMapOf<Fnr, Either<KunneIkkeHentePerson, AdresseopplysningerMedMetadata>>()
        return personhendelser.map { personhendelse ->
            if (personhendelse.hendelse !is Personhendelse.Hendelse.Bostedsadresse) return@map personhendelse
            val pdlOppsummering = personhendelse.pdlOppsummering ?: return@map personhendelse
            if (!pdlOppsummering.pdlTreffAdresse.isNullOrBlank()) return@map personhendelse

            val fnr = personhendelse.metadata.personidenter.firstNotNullOfOrNull { Fnr.tryCreate(it) }
                ?: return@map personhendelse

            val opplysninger = opplysningerPerFnr.getOrPut(fnr) { personOppslag.bostedsadresseMedMetadataForSystembruker(fnr) }
            opplysninger.fold(
                ifLeft = { feil ->
                    log.warn(
                        "Kunne ikke hente PDL-treffadresse ved oppgaveopprettelse for personhendelse {}. Feil: {}.",
                        personhendelse.id,
                        feil,
                    )
                    personhendelse
                },
                ifRight = { adresseopplysninger ->
                    val treffadresse = adresseopplysninger.bostedsadresser.firstOrNull {
                        it.hendelseIder.contains(personhendelse.metadata.hendelseId)
                    }?.toOppgaveAdresse()

                    personhendelse.copy(
                        pdlOppsummering = pdlOppsummering.copy(
                            pdlTreffAdresse = treffadresse,
                        ),
                    )
                },
            )
        }.toNonEmptyList()
    }

    private data class Adressebeslutning(
        val relevant: Boolean,
        val grunnlag: List<String>,
        val changedFields: List<String> = emptyList(),
    )

    private fun AdresseopplysningerMedMetadata.Adresseopplysning.toOppgaveAdresse(): String? {
        val gate = gateadresse?.trim()?.takeUnless { it.isBlank() }
        val postnr = postnummer?.trim()?.takeUnless { it.isBlank() }
        return listOfNotNull(gate, postnr).joinToString(", ").ifBlank { null }
    }

    private fun finnEndredeFelter(
        før: AdresseopplysningerMedMetadata.Adresseopplysning,
        nå: AdresseopplysningerMedMetadata.Adresseopplysning,
    ): List<String> {
        val changedFields = mutableListOf<String>()
        if (før.gateadresse.normalizeText() != nå.gateadresse.normalizeText()) changedFields.add("gateadresse")
        if (før.postnummer.normalizePostnummer() != nå.postnummer.normalizePostnummer()) changedFields.add("postnummer")

        return changedFields
    }

    private fun String?.normalizeText(): String? {
        if (this == null) return null
        return trim()
            .replace(WHITESPACE_REGEX, " ")
            .lowercase()
            .ifBlank { null }
    }

    private fun String?.normalizePostnummer(): String? {
        if (this == null) return null
        return trim()
            .replace(WHITESPACE_REGEX, "")
            .ifBlank { null }
    }

    private fun AdresseopplysningerMedMetadata.Folkeregistermetadata?.gyldighetsaar(): Int? {
        return this?.gyldighetstidspunkt
            ?.take(4)
            ?.toIntOrNull()
    }

    companion object {
        private const val SU_INNFOERT_AAR = 2020
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }

    private fun Personhendelse.TilknyttetSak.IkkeSendtTilOppgave.grupperingsnøkkelForOppgave(): String {
        return when (hendelse) {
            is Personhendelse.Hendelse.Dødsfall -> "DODSFALL"
            is Personhendelse.Hendelse.Sivilstand -> "SIVILSTAND"
            is Personhendelse.Hendelse.UtflyttingFraNorge -> "UTFLYTTING_FRA_NORGE"
            is Personhendelse.Hendelse.Bostedsadresse -> "BOSTEDSADRESSE:$id"
            is Personhendelse.Hendelse.Kontaktadresse -> "KONTAKTADRESSE:$id"
        }
    }
}
