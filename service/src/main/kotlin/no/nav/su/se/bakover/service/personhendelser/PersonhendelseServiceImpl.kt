package no.nav.su.se.bakover.service.personhendelser

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
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
import person.domain.AdresseopplysningerMedMetadata
import person.domain.KunneIkkeHentePerson
import java.time.Clock
import java.util.UUID

class PersonhendelseServiceImpl(
    private val sakRepo: SakRepo,
    private val personhendelseRepo: PersonhendelseRepo,
    private val hentBostedsadresseMedMetadataForSystembruker: (Fnr) -> Either<KunneIkkeHentePerson, AdresseopplysningerMedMetadata>,
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

        oppgaveServiceImpl.opprettOppgaveMedSystembruker(
            OppgaveConfig.Personhendelse(
                saksnummer = sak.saksnummer,
                personhendelse = personhendelser.toNonEmptySet(),
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
            is Personhendelse.Hendelse.Kontaktadresse -> PersonhendelseRepo.PdlVurdering(
                id = personhendelse.id,
                relevant = true,
                pdlSnapshot = null,
                pdlDiff = serialize(
                    PdlDiff(
                        hendelsestype = personhendelse.hendelse::class.simpleName ?: "Ukjent",
                        endringstype = personhendelse.endringstype.name,
                        relevant = true,
                        begrunnelse = "PDL-gating brukes kun for bostedsadresse.",
                        reasons = listOf("Kontaktadresse vurderes uten PDL-gating."),
                        changedFields = emptyList(),
                        gjelderEps = personhendelse.gjelderEps,
                        harBostedsadresseNå = null,
                        harKontaktadresseNå = null,
                        korrelertPåGjeldendeForekomst = false,
                    ),
                ),
            )
            else -> PersonhendelseRepo.PdlVurdering(
                id = personhendelse.id,
                relevant = true,
                pdlSnapshot = null,
                pdlDiff = serialize(
                    PdlDiff(
                        hendelsestype = personhendelse.hendelse::class.simpleName ?: "Ukjent",
                        endringstype = personhendelse.endringstype.name,
                        relevant = true,
                        begrunnelse = "Hendelsetype vurderes uten PDL-gating.",
                        reasons = listOf("Hendelsetype vurderes uten PDL-gating."),
                        changedFields = emptyList(),
                        gjelderEps = personhendelse.gjelderEps,
                        harBostedsadresseNå = null,
                        harKontaktadresseNå = null,
                        korrelertPåGjeldendeForekomst = false,
                    ),
                ),
            )
        }
    }

    private fun vurderBostedsadressehendelseMotPdl(
        personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave,
    ): PersonhendelseRepo.PdlVurdering? {
        val fnr = personhendelse.metadata.personidenter.firstNotNullOfOrNull { Fnr.tryCreate(it) }
        if (fnr == null) {
            return PersonhendelseRepo.PdlVurdering(
                id = personhendelse.id,
                relevant = false,
                pdlSnapshot = null,
                pdlDiff = serialize(
                    PdlDiff(
                        hendelsestype = personhendelse.hendelse::class.simpleName ?: "Ukjent",
                        endringstype = personhendelse.endringstype.name,
                        relevant = false,
                        begrunnelse = "Mangler fnr i metadata.personidenter, kan ikke slå opp i PDL.",
                        reasons = listOf("Mangler fnr i metadata.personidenter, kan ikke slå opp i PDL."),
                        changedFields = emptyList(),
                        gjelderEps = personhendelse.gjelderEps,
                        harBostedsadresseNå = null,
                        harKontaktadresseNå = null,
                        korrelertPåGjeldendeForekomst = false,
                    ),
                ),
            )
        }

        return hentBostedsadresseMedMetadataForSystembruker(fnr).fold(
            ifLeft = { feil ->
                when (feil) {
                    KunneIkkeHentePerson.FantIkkePerson -> {
                        PersonhendelseRepo.PdlVurdering(
                            id = personhendelse.id,
                            relevant = false,
                            pdlSnapshot = null,
                            pdlDiff = serialize(
                                PdlDiff(
                                    hendelsestype = personhendelse.hendelse::class.simpleName ?: "Ukjent",
                                    endringstype = personhendelse.endringstype.name,
                                    relevant = false,
                                    begrunnelse = "Person ikke funnet i PDL.",
                                    reasons = listOf("Person ikke funnet i PDL."),
                                    changedFields = emptyList(),
                                    gjelderEps = personhendelse.gjelderEps,
                                    harBostedsadresseNå = null,
                                    harKontaktadresseNå = null,
                                    korrelertPåGjeldendeForekomst = false,
                                ),
                            ),
                        )
                    }

                    KunneIkkeHentePerson.IkkeTilgangTilPerson,
                    KunneIkkeHentePerson.Ukjent,
                    -> {
                        log.warn(
                            "Kunne ikke vurdere personhendelse {} mot PDL nå. Feil: {}. Hopper over for retry senere.",
                            personhendelse.id,
                            feil,
                        )
                        null
                    }
                }
            },
            ifRight = { adresseopplysninger ->
                val alleAdresser = adresseopplysninger.bostedsadresser
                val gjeldendeAdresser = alleAdresser.filter { !it.historisk }
                val gjeldendeAdresseForHendelse = gjeldendeAdresser.firstOrNull {
                    it.hendelseIder.contains(personhendelse.metadata.hendelseId)
                }

                val gjeldendeBostedsadresser = adresseopplysninger.bostedsadresser
                    .filter { !it.historisk }
                    .map { it.toPdlAdresseSnapshot() }
                val gjeldendeKontaktadresser = adresseopplysninger.kontaktadresser
                    .filter { !it.historisk }
                    .map { it.toPdlAdresseSnapshot() }
                val harBostedsadresseNå = gjeldendeBostedsadresser.isNotEmpty()
                val harKontaktadresseNå = gjeldendeKontaktadresser.isNotEmpty()

                val beslutning = vurderBostedsadressebeslutning(
                    endringstype = personhendelse.endringstype,
                    hendelseId = personhendelse.metadata.hendelseId,
                    tidligereHendelseId = personhendelse.metadata.tidligereHendelseId,
                    nåForekomst = gjeldendeAdresseForHendelse,
                    historiskeOgGjeldendeForekomster = alleAdresser,
                )

                val snapshot = PdlSnapshot(
                    fnr = fnr.toString(),
                    harBostedsadresse = harBostedsadresseNå,
                    harKontaktadresse = harKontaktadresseNå,
                    gjeldendeBostedsadresser = gjeldendeBostedsadresser,
                    gjeldendeKontaktadresser = gjeldendeKontaktadresser,
                )

                PersonhendelseRepo.PdlVurdering(
                    id = personhendelse.id,
                    relevant = beslutning.relevant,
                    pdlSnapshot = serialize(snapshot),
                    pdlDiff = serialize(
                        PdlDiff(
                            hendelsestype = personhendelse.hendelse::class.simpleName ?: "Ukjent",
                            endringstype = personhendelse.endringstype.name,
                            relevant = beslutning.relevant,
                            begrunnelse = beslutning.reasons.joinToString(" | "),
                            reasons = beslutning.reasons,
                            changedFields = beslutning.changedFields,
                            gjelderEps = personhendelse.gjelderEps,
                            harBostedsadresseNå = harBostedsadresseNå,
                            harKontaktadresseNå = harKontaktadresseNå,
                            korrelertPåGjeldendeForekomst = gjeldendeAdresseForHendelse != null,
                        ),
                    ),
                )
            },
        )
    }

    /**
     * Regler for bostedsadresse:
     * 1) Hendelsen må korreleres mot en gjeldende forekomst (metadata.historisk=false + hendelseId-match).
     * 2) OPPRETTET er relevant når korrelasjonen treffer gjeldende forekomst.
     * 3) KORRIGERT sammenligner før/etter kun på gateadresse(adressenavn+husnummer+husbokstav) og postnummer.
     * 4) Kosmetiske forskjeller i tekst (case/whitespace) ignoreres.
     * 5) OPPHØRT/ANNULLERT er ikke relevante for flytting.
     */
    private fun vurderBostedsadressebeslutning(
        endringstype: Personhendelse.Endringstype,
        hendelseId: String,
        tidligereHendelseId: String?,
        nåForekomst: AdresseopplysningerMedMetadata.Adresseopplysning?,
        historiskeOgGjeldendeForekomster: List<AdresseopplysningerMedMetadata.Adresseopplysning>,
    ): Adressebeslutning {
        if (nåForekomst == null) {
            return Adressebeslutning(
                relevant = false,
                reasons = listOf("HendelseId=$hendelseId finnes ikke på gjeldende adresseforekomst i PDL."),
            )
        }

        return when (endringstype) {
            Personhendelse.Endringstype.OPPRETTET ->
                Adressebeslutning(
                    relevant = true,
                    reasons = listOf("OPPRETTET korrelert mot gjeldende adresseforekomst."),
                )

            Personhendelse.Endringstype.KORRIGERT -> {
                if (tidligereHendelseId == null) {
                    return Adressebeslutning(
                        relevant = false,
                        reasons = listOf("KORRIGERT mangler tidligereHendelseId."),
                    )
                }

                val førFraPdl = historiskeOgGjeldendeForekomster.firstOrNull {
                    it.hendelseIder.contains(tidligereHendelseId)
                } ?: return Adressebeslutning(
                    relevant = false,
                    reasons = listOf("KORRIGERT mangler før-forekomst i PDL-historikk for tidligereHendelseId=$tidligereHendelseId."),
                )

                val changedFields = finnEndredeFelter(førFraPdl, nåForekomst)
                Adressebeslutning(
                    relevant = changedFields.isNotEmpty(),
                    reasons = listOf("KORRIGERT vurdert med før-forekomst fra PDL-historikk."),
                    changedFields = changedFields,
                )
            }

            Personhendelse.Endringstype.OPPHØRT,
            Personhendelse.Endringstype.ANNULLERT,
            -> Adressebeslutning(
                relevant = false,
                reasons = listOf("${endringstype.name} er ikke relevant for flytting/reell adresseendring."),
            )
        }
    }

    private data class PdlSnapshot(
        val fnr: String,
        val harBostedsadresse: Boolean,
        val harKontaktadresse: Boolean,
        val gjeldendeBostedsadresser: List<PdlAdresseSnapshot> = emptyList(),
        val gjeldendeKontaktadresser: List<PdlAdresseSnapshot> = emptyList(),
    )

    private data class PdlDiff(
        val hendelsestype: String,
        val endringstype: String,
        val relevant: Boolean,
        val begrunnelse: String,
        val reasons: List<String>,
        val changedFields: List<String>,
        val gjelderEps: Boolean,
        val harBostedsadresseNå: Boolean?,
        val harKontaktadresseNå: Boolean?,
        val korrelertPåGjeldendeForekomst: Boolean,
    )

    private data class Adressebeslutning(
        val relevant: Boolean,
        val reasons: List<String>,
        val changedFields: List<String> = emptyList(),
    )

    private data class PdlAdresseSnapshot(
        val gateadresse: String? = null,
        val postnummer: String? = null,
    )

    private fun AdresseopplysningerMedMetadata.Adresseopplysning.toPdlAdresseSnapshot(): PdlAdresseSnapshot {
        return PdlAdresseSnapshot(
            gateadresse = gateadresse,
            postnummer = postnummer,
        )
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

    companion object {
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
