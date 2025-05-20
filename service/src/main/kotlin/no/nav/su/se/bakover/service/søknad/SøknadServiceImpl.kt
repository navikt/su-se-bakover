package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.journalføring.søknad.JournalførSøknadClient
import dokument.domain.journalføring.søknad.JournalførSøknadCommand
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfGenerator
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import org.slf4j.LoggerFactory
import person.domain.Person
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val sakFactory: SakFactory,
    private val pdfGenerator: PdfGenerator,
    private val journalførSøknadClient: JournalførSøknadClient,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val clock: Clock,
    private val kanSendeInnAlderssøknad: Boolean,
) : SøknadService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers = mutableListOf<StatistikkEventObserver>()

    fun addObserver(observer: StatistikkEventObserver) = observers.add(observer)

    fun getObservers(): List<StatistikkEventObserver> = observers.toList()

    private fun opprettSøknad(sakInfo: SakInfo, søknadInnhold: SøknadInnhold, identBruker: NavIdentBruker): Pair<SakInfo, Søknad.Ny> {
        val søknad = Søknad.Ny(
            sakId = sakInfo.sakId,
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            søknadInnhold = søknadInnhold,
            innsendtAv = identBruker,
        )
        søknadRepo.opprettSøknad(søknad)

        return Pair(sakInfo, søknad)
    }

    override fun nySøknad(
        søknadInnhold: SøknadInnhold,
        identBruker: NavIdentBruker,
    ): Either<KunneIkkeOppretteSøknad, Pair<Saksnummer, Søknad.Ny>> {
        val innsendtFødselsnummer: Fnr = søknadInnhold.personopplysninger.fnr

        if (!søknadInnhold.kanSendeInnSøknad()) {
            return KunneIkkeOppretteSøknad.SøknadsinnsendingIkkeTillatt.left()
        }

        val person = personService.hentPerson(innsendtFødselsnummer).getOrElse {
            // Dette bør ikke skje i normal flyt, siden vi allerede har gjort en tilgangssjekk mot PDL (kode6/7).
            log.error("Ny søknad: Fant ikke person med gitt fødselsnummer. Originalfeil: $it")
            return KunneIkkeOppretteSøknad.FantIkkePerson.left()
        }
        val fnr = person.ident.fnr
        val søknadsinnholdMedNyesteFødselsnummer = søknadInnhold.oppdaterFnr(fnr)

        if (fnr != innsendtFødselsnummer) {
            log.error("Ny søknad: Personen har et nyere fødselsnummer i PDL enn det som ble sendt inn. Bruker det nyeste fødselsnummeret istedet. Personoppslaget burde ha returnert det nyeste fødselsnummeret og bør sjekkes opp.")
        }

        // v1
        val lol: Either<Pair<SakInfo, Søknad.Ny>, Pair<SakInfo, Søknad.Ny>> = sakService.hentSakidOgSaksnummer(fnr)
            .mapLeft {
                log.info("Ny søknad: Fant ikke sak for fødselsnummmer. Oppretter ny søknad og ny sak.")
                val nySak = sakFactory.nySakMedNySøknad(
                    fnr = fnr,
                    søknadInnhold = søknadsinnholdMedNyesteFødselsnummer,
                    innsendtAv = identBruker,
                ).also {
                    sakService.opprettSak(it)
                }
                val sakinfo = sakService.hentSakInfo(nySak.id)
                    .getOrElse { throw RuntimeException("Feil ved henting av sak") }
                Pair(sakinfo, nySak.søknad)
            }
            .map { saker ->
                log.info("Ny søknad: Fant eksisterende sak for fødselsnummmer. Oppretter ny søknad på eksisterende sak.")
                Either.catch { saker.single() }.fold(
                    {
                        val sak = saker.find { it.type == søknadInnhold.type() } ?: return KunneIkkeOppretteSøknad.FeilSakstype.left()
                        opprettSøknad(sak, søknadInnhold, identBruker)
                    },
                    { sak ->
                        if (sak.type == søknadInnhold.type()) {
                            opprettSøknad(saker.first(), søknadInnhold, identBruker)
                        } else {
                            // opprett ny sak
                            log.info("Ny søknad: Fant ikke riktig sak for fødselsnummmer. Oppretter ny søknad og ny sak.")
                            val nySak = sakFactory.nySakMedNySøknad(
                                fnr = fnr,
                                søknadInnhold = søknadsinnholdMedNyesteFødselsnummer,
                                innsendtAv = identBruker,
                            ).also {
                                sakService.opprettSak(it)
                            }
                            val sakinfo = sakService.hentSakInfo(nySak.id)
                                .getOrElse { throw RuntimeException("Feil ved henting av sak") }
                            Pair(sakinfo, nySak.søknad)
                        }
                    },
                )
            }
        // v2
        val lolPair = sakService.hentSakidOgSaksnummer(fnr).fold(
            {
                log.info("Ny søknad: Fant ikke sak for fødselsnummmer. Oppretter ny søknad og ny sak.")
                val nySak = sakFactory.nySakMedNySøknad(
                    fnr = fnr,
                    søknadInnhold = søknadsinnholdMedNyesteFødselsnummer,
                    innsendtAv = identBruker,
                ).also {
                    sakService.opprettSak(it)
                }
                val sakinfo = sakService.hentSakInfo(nySak.id)
                    .getOrElse { throw RuntimeException("Feil ved henting av sak") }
                Pair(sakinfo, nySak.søknad)
            },
            { saker ->
                log.info("Ny søknad: Fant eksisterende sak for fødselsnummmer. Oppretter ny søknad på eksisterende sak.")
                val lol = Either.catch { saker.single() }.fold(
                    {
                        val sak = saker.find { it.type == søknadInnhold.type() } ?: return throw RuntimeException()
                        opprettSøknad(sak, søknadInnhold, identBruker)
                    },
                    { sak ->
                        if (sak.type == søknadInnhold.type()) {
                            opprettSøknad(saker.first(), søknadInnhold, identBruker)
                        } else {
                            // opprett ny sak
                            log.info("Ny søknad: Fant ikke riktig sak for fødselsnummmer. Oppretter ny søknad og ny sak.")
                            val nySak = sakFactory.nySakMedNySøknad(
                                fnr = fnr,
                                søknadInnhold = søknadsinnholdMedNyesteFødselsnummer,
                                innsendtAv = identBruker,
                            ).also {
                                sakService.opprettSak(it)
                            }
                            val sakinfo = sakService.hentSakInfo(nySak.id)
                                .getOrElse { throw RuntimeException("Feil ved henting av sak") }
                            Pair(sakinfo, nySak.søknad)
                        }
                    },
                )
            },
        )

        // val (sakInfo: SakInfo, søknad: Søknad.Ny) =
        opprettJournalpostOgOppgave(sakInfo, person, søknad)
        observers.forEach { observer ->
            observer.handle(
                StatistikkEvent.Søknad.Mottatt(søknad, sakInfo.saksnummer),
            )
        }
        return Pair(sakInfo.saksnummer, søknad).right()
    }

    override fun persisterSøknad(søknad: Søknad.Journalført.MedOppgave.Lukket, sessionContext: SessionContext) {
        søknadRepo.lukkSøknad(søknad, sessionContext)
    }

    override fun opprettManglendeJournalpostOgOppgave(): OpprettManglendeJournalpostOgOppgaveResultat {
        return OpprettManglendeJournalpostOgOppgaveResultat(
            journalpostResultat = opprettManglendeJournalposteringer(),
            oppgaveResultat = opprettManglendeOppgaver(),
        )
    }

    private fun opprettManglendeJournalposteringer(): List<Either<KunneIkkeOppretteJournalpost, Søknad.Journalført.UtenOppgave>> {
        return søknadRepo.hentSøknaderUtenJournalpost().map { søknad ->
            // TODO jah: Legg på saksnummer på Søknad (dette innebærer å legge til en ny Opprettet 'tilstand')
            val sak = sakService.hentSak(søknad.sakId).getOrElse {
                log.error("Fant ikke sak med sakId ${søknad.sakId} - sannsynligvis dataintegritetsfeil i databasen.")
                return@map KunneIkkeOppretteJournalpost(søknad.sakId, søknad.id, "Fant ikke sak").left()
            }
            val person = personService.hentPersonMedSystembruker(sak.fnr).getOrElse {
                log.error("Fant ikke person med sakId ${sak.id}.")
                return@map KunneIkkeOppretteJournalpost(sak.id, søknad.id, "Fant ikke person").left()
            }
            opprettJournalpost(
                sakInfo = sak.info(),
                søknad = søknad,
                person = person,
            )
        }
    }

    private fun opprettManglendeOppgaver(): List<Either<KunneIkkeOppretteOppgave, Søknad.Journalført.MedOppgave>> {
        return søknadRepo.hentSøknaderMedJournalpostMenUtenOppgave().map { søknad ->
            // TODO jah: Legg på saksnummer på Søknad (dette innebærer å legge til en ny Opprettet 'tilstand')
            val sak = sakService.hentSak(søknad.sakId).getOrElse {
                log.error("Fant ikke sak med sakId ${søknad.sakId} - sannsynligvis dataintegritetsfeil i databasen.")
                return@map KunneIkkeOppretteOppgave(
                    sakId = søknad.sakId,
                    søknadId = søknad.id,
                    journalpostId = søknad.journalpostId,
                    grunn = "Fant ikke sak",
                ).left()
            }
            opprettOppgave(
                søknad = søknad,
                fnr = sak.fnr,
                opprettOppgave = oppgaveService::opprettOppgaveMedSystembruker,
            )
        }
    }

    private fun opprettJournalpostOgOppgave(
        sakInfo: SakInfo,
        person: Person,
        søknad: Søknad.Ny,
    ) {
        // TODO jah: Burde kanskje innføre en multi-respons-type som responderer med de stegene som er utført og de som ikke er utført.
        opprettJournalpost(sakInfo, søknad, person).map { journalførtSøknad ->
            opprettOppgave(journalførtSøknad, sakInfo.fnr)
        }
    }

    private fun opprettJournalpost(
        sakInfo: SakInfo,
        søknad: Søknad.Ny,
        person: Person,
    ): Either<KunneIkkeOppretteJournalpost, Søknad.Journalført.UtenOppgave> {
        val pdf = pdfGenerator.genererPdf(
            SøknadPdfInnhold.create(
                saksnummer = sakInfo.saksnummer,
                søknadsId = søknad.id,
                navn = person.navn,
                søknadOpprettet = søknad.opprettet,
                søknadInnhold = søknad.søknadInnhold,
                clock = clock,
            ),
        ).getOrElse {
            log.error("Ny søknad: Kunne ikke generere PDF. Originalfeil: $it")
            return KunneIkkeOppretteJournalpost(søknad.sakId, søknad.id, "Kunne ikke generere PDF").left()
        }
        log.info("Ny søknad: Generert PDF ok.")

        val journalpostId = journalførSøknadClient.journalførSøknad(
            JournalførSøknadCommand(
                søknadInnholdJson = serialize(søknad.søknadInnhold),
                pdf = pdf,
                saksnummer = sakInfo.saksnummer,
                sakstype = sakInfo.type,
                datoDokument = Tidspunkt.now(clock),
                fnr = person.ident.fnr,
                navn = person.navn,
                // jah: Når vi journalfører søknader, så går vi utenom dokument-typen. Bruker da søknadsIDen istedenfor.
                internDokumentId = søknad.id,
            ),
        ).getOrElse {
            log.error("Ny søknad: Kunne ikke opprette journalpost. Originalfeil: $it")
            return KunneIkkeOppretteJournalpost(søknad.sakId, søknad.id, "Kunne ikke opprette journalpost").left()
        }

        return søknad.journalfør(journalpostId).also {
            søknadRepo.oppdaterjournalpostId(it)
        }.right()
    }

    private fun opprettOppgave(
        søknad: Søknad.Journalført.UtenOppgave,
        fnr: Fnr,
        opprettOppgave: (oppgaveConfig: OppgaveConfig.Søknad) -> Either<no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> = oppgaveService::opprettOppgave,
    ): Either<KunneIkkeOppretteOppgave, Søknad.Journalført.MedOppgave> {
        return opprettOppgave(
            OppgaveConfig.Søknad(
                journalpostId = søknad.journalpostId,
                søknadId = søknad.id,
                fnr = fnr,
                clock = clock,
                tilordnetRessurs = null,
                sakstype = søknad.søknadInnhold.type(),
            ),
        ).mapLeft {
            log.error("Ny søknad: Kunne ikke opprette oppgave for sak ${søknad.sakId} og søknad ${søknad.id}. Originalfeil: $it")
            KunneIkkeOppretteOppgave(søknad.sakId, søknad.id, søknad.journalpostId, "Kunne ikke opprette oppgave")
        }.map { oppgaveResponse ->
            return søknad.medOppgave(oppgaveResponse.oppgaveId).also {
                søknadRepo.oppdaterOppgaveId(it)
            }.right()
        }
    }

    override fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: FantIkkeSøknad.left()
    }

    override fun hentSøknadPdf(søknadId: UUID): Either<KunneIkkeLageSøknadPdf, PdfA> {
        return hentSøknad(søknadId).mapLeft {
            log.error("Hent søknad-PDF: Fant ikke søknad")
            return KunneIkkeLageSøknadPdf.FantIkkeSøknad.left()
        }
            .flatMap { søknad ->
                sakService.hentSak(søknad.sakId).mapLeft {
                    return KunneIkkeLageSøknadPdf.FantIkkeSak.left()
                }.flatMap { sak ->
                    personService.hentPerson(søknad.fnr).mapLeft {
                        log.error("Hent søknad-PDF: Fant ikke person")
                        return KunneIkkeLageSøknadPdf.FantIkkePerson.left()
                    }.flatMap { person ->
                        pdfGenerator.genererPdf(
                            SøknadPdfInnhold.create(
                                saksnummer = sak.saksnummer,
                                søknadsId = søknad.id,
                                navn = person.navn,
                                søknadOpprettet = søknad.opprettet,
                                søknadInnhold = søknad.søknadInnhold,
                                clock = clock,
                            ),
                        ).mapLeft {
                            log.error("Hent søknad-PDF: Kunne ikke generere PDF. Originalfeil: $it")
                            KunneIkkeLageSøknadPdf.KunneIkkeLagePdf
                        }
                    }
                }
            }
    }

    private fun SøknadInnhold.kanSendeInnSøknad(): Boolean = when (this) {
        is SøknadsinnholdAlder -> kanSendeInnAlderssøknad
        is SøknadsinnholdUføre -> true
    }
}
