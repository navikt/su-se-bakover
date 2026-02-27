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
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class PersonhendelseServiceImpl(
    private val sakRepo: SakRepo,
    private val personhendelseRepo: PersonhendelseRepo,
    private val personService: PersonService,
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
            .forEach loop@{ (sakId, personhendelser) ->
                val sak = sakRepo.hentSak(sakId)
                if (sak == null) {
                    log.error("Fant ikke sak for personhendelser med id'er: ${personhendelser.map { it.id }}")
                    return@loop // continue
                }
                opprettOppgaveForSak(sak, personhendelser.toNonEmptyList())
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
            is Personhendelse.Hendelse.Bostedsadresse -> vurderAdressehendelseMotPdl(personhendelse, Adressekategori.BOSTEDSADRESSE)
            is Personhendelse.Hendelse.Kontaktadresse -> vurderAdressehendelseMotPdl(personhendelse, Adressekategori.KONTAKTADRESSE)
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
                        gjelderEps = personhendelse.gjelderEps,
                        harBostedsadresseNå = null,
                        harKontaktadresseNå = null,
                    ),
                ),
            )
        }
    }

    private fun vurderAdressehendelseMotPdl(
        personhendelse: Personhendelse.TilknyttetSak.IkkeSendtTilOppgave,
        adressekategori: Adressekategori,
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
                        gjelderEps = personhendelse.gjelderEps,
                        harBostedsadresseNå = null,
                        harKontaktadresseNå = null,
                    ),
                ),
            )
        }

        return personService.hentPersonMedSystembruker(fnr).fold(
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
                                    gjelderEps = personhendelse.gjelderEps,
                                    harBostedsadresseNå = null,
                                    harKontaktadresseNå = null,
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
            ifRight = { person ->
                val harBostedsadresseNå = person.harAdressetype(BOSTEDSADRESSE_PDL_TYPE)
                val harKontaktadresseNå = person.harAdressetype(KONTAKTADRESSE_PDL_TYPE)
                /*
                 * PDL-gate for adressehendelser:
                 * - OPPRETTET/KORRIGERT er relevant kun hvis adressetypen finnes i gjeldende PDL-tilstand.
                 * - ANNULLERT/OPPHØRT er relevant kun hvis adressetypen ikke finnes i gjeldende PDL-tilstand.
                 *
                 * Hensikt:
                 * - filtrere bort forsinkede/out-of-order hendelser som ikke stemmer med "nåtilstand",
                 * - unngå oppgaveoppretting når PDL ikke støtter det hendelsen påstår nå.
                 *
                 * Merk: dette er en relevansvurdering mot nåtilstand, ikke en historisk før/etter-diff.
                 */
                val relevant = when (adressekategori) {
                    Adressekategori.BOSTEDSADRESSE -> when (personhendelse.endringstype) {
                        Personhendelse.Endringstype.OPPRETTET,
                        Personhendelse.Endringstype.KORRIGERT,
                        -> harBostedsadresseNå

                        Personhendelse.Endringstype.ANNULLERT,
                        Personhendelse.Endringstype.OPPHØRT,
                        -> !harBostedsadresseNå
                    }

                    Adressekategori.KONTAKTADRESSE -> when (personhendelse.endringstype) {
                        Personhendelse.Endringstype.OPPRETTET,
                        Personhendelse.Endringstype.KORRIGERT,
                        -> harKontaktadresseNå

                        Personhendelse.Endringstype.ANNULLERT,
                        Personhendelse.Endringstype.OPPHØRT,
                        -> !harKontaktadresseNå
                    }
                }

                val snapshot = PdlSnapshot(
                    fnr = person.ident.fnr.toString(),
                    harBostedsadresse = harBostedsadresseNå,
                    harKontaktadresse = harKontaktadresseNå,
                    adresser = person.adresse.orEmpty().map {
                        PdlAdresseSnapshot(
                            adressetype = it.adressetype,
                            adresseformat = it.adresseformat,
                            adresselinje = it.adresselinje,
                            postnummer = it.poststed?.postnummer,
                            landkode = it.landkode,
                        )
                    },
                )

                PersonhendelseRepo.PdlVurdering(
                    id = personhendelse.id,
                    relevant = relevant,
                    pdlSnapshot = serialize(snapshot),
                    pdlDiff = serialize(
                        PdlDiff(
                            hendelsestype = personhendelse.hendelse::class.simpleName ?: "Ukjent",
                            endringstype = personhendelse.endringstype.name,
                            relevant = relevant,
                            begrunnelse = when (adressekategori) {
                                Adressekategori.BOSTEDSADRESSE -> "Vurdert mot gjeldende bostedsadresse i PDL."
                                Adressekategori.KONTAKTADRESSE -> "Vurdert mot gjeldende kontaktadresse i PDL."
                            },
                            gjelderEps = personhendelse.gjelderEps,
                            harBostedsadresseNå = harBostedsadresseNå,
                            harKontaktadresseNå = harKontaktadresseNå,
                        ),
                    ),
                )
            },
        )
    }

    private fun Person.harAdressetype(adressetype: String): Boolean = adresse.orEmpty().any { it.adressetype == adressetype }

    private enum class Adressekategori {
        BOSTEDSADRESSE,
        KONTAKTADRESSE,
    }

    private data class PdlSnapshot(
        val fnr: String,
        val harBostedsadresse: Boolean,
        val harKontaktadresse: Boolean,
        val adresser: List<PdlAdresseSnapshot>,
    )

    private data class PdlAdresseSnapshot(
        val adressetype: String,
        val adresseformat: String,
        val adresselinje: String?,
        val postnummer: String?,
        val landkode: String?,
    )

    private data class PdlDiff(
        val hendelsestype: String,
        val endringstype: String,
        val relevant: Boolean,
        val begrunnelse: String,
        val gjelderEps: Boolean,
        val harBostedsadresseNå: Boolean?,
        val harKontaktadresseNå: Boolean?,
    )

    companion object {
        private const val BOSTEDSADRESSE_PDL_TYPE = "Bostedsadresse"
        private const val KONTAKTADRESSE_PDL_TYPE = "Kontaktadresse"
    }
}
