package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.featuretoggle.ToggleClient
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val sakFactory: SakFactory,
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val søknadMetrics: SøknadMetrics,
    private val toggleService: ToggleClient,
    private val clock: Clock,
) : SøknadService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers = mutableListOf<StatistikkEventObserver>()

    fun addObserver(observer: StatistikkEventObserver) = observers.add(observer)

    fun getObservers(): List<StatistikkEventObserver> = observers.toList()

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

        val (saksnummer: Saksnummer, søknad: Søknad.Ny) = sakService.hentSakidOgSaksnummer(fnr).fold(
            {
                log.info("Ny søknad: Fant ikke sak for fødselsnummmer. Oppretter ny søknad og ny sak.")
                val nySak = sakFactory.nySakMedNySøknad(
                    fnr = fnr,
                    søknadInnhold = søknadsinnholdMedNyesteFødselsnummer,
                    innsendtAv = identBruker,
                ).also {
                    sakService.opprettSak(it)
                }
                val sakIdSaksnummerFnr = sakService.hentSakidOgSaksnummer(fnr)
                    .getOrElse { throw RuntimeException("Feil ved henting av sak") }
                Pair(sakIdSaksnummerFnr.saksnummer, nySak.søknad)
            },
            {
                log.info("Ny søknad: Fant eksisterende sak for fødselsnummmer. Oppretter ny søknad på eksisterende sak.")
                val søknad = Søknad.Ny(
                    sakId = it.sakId,
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    søknadInnhold = søknadsinnholdMedNyesteFødselsnummer,
                    innsendtAv = identBruker,
                )
                søknadRepo.opprettSøknad(søknad)

                Pair(it.saksnummer, søknad)
            },
        )
        // Ved å gjøre increment først, kan vi lage en alert dersom vi får mismatch på dette.
        søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.PERSISTERT)
        opprettJournalpostOgOppgave(saksnummer, person, søknad)
        observers.forEach { observer ->
            observer.handle(
                StatistikkEvent.Søknad.Mottatt(søknad, saksnummer),
            )
        }
        return Pair(saksnummer, søknad).right()
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
                sak.saksnummer,
                søknad,
                person,
            )
        }
    }

    private fun opprettManglendeOppgaver(): List<Either<KunneIkkeOppretteOppgave, Søknad.Journalført.MedOppgave>> {
        return søknadRepo.hentSøknaderMedJournalpostMenUtenOppgave().map { søknad ->
            // TODO jah: Legg på saksnummer på Søknad (dette innebærer å legge til en ny Opprettet 'tilstand')
            val sak = sakService.hentSak(søknad.sakId).getOrElse {
                log.error("Fant ikke sak med sakId ${søknad.sakId} - sannsynligvis dataintegritetsfeil i databasen.")
                return@map KunneIkkeOppretteOppgave(søknad.sakId, søknad.id, søknad.journalpostId, "Fant ikke sak").left()
            }
            val person = personService.hentPersonMedSystembruker(sak.fnr).getOrElse {
                log.error("Fant ikke person med sakId ${sak.id}.")
                return@map KunneIkkeOppretteOppgave(sak.id, søknad.id, søknad.journalpostId, "Fant ikke person").left()
            }
            opprettOppgave(
                søknad = søknad,
                person = person,
                opprettOppgave = oppgaveService::opprettOppgaveMedSystembruker,
            )
        }
    }

    private fun opprettJournalpostOgOppgave(
        saksnummer: Saksnummer,
        person: Person,
        søknad: Søknad.Ny,
    ) {
        // TODO jah: Burde kanskje innføre en multi-respons-type som responderer med de stegene som er utført og de som ikke er utført.
        opprettJournalpost(saksnummer, søknad, person).map { journalførtSøknad ->
            opprettOppgave(journalførtSøknad, person)
        }
    }

    private fun opprettJournalpost(
        saksnummer: Saksnummer,
        søknad: Søknad.Ny,
        person: Person,
    ): Either<KunneIkkeOppretteJournalpost, Søknad.Journalført.UtenOppgave> {
        val pdfByteArray = pdfGenerator.genererPdf(
            SøknadPdfInnhold.create(
                saksnummer = saksnummer,
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

        val journalpostId = dokArkiv.opprettJournalpost(
            Journalpost.Søknadspost.from(
                søknadInnhold = søknad.søknadInnhold,
                pdf = pdfByteArray,
                saksnummer = saksnummer,
                person = person,
            ),
        ).getOrElse {
            log.error("Ny søknad: Kunne ikke opprette journalpost. Originalfeil: $it")
            return KunneIkkeOppretteJournalpost(søknad.sakId, søknad.id, "Kunne ikke opprette journalpost").left()
        }

        return søknad.journalfør(journalpostId).also {
            søknadRepo.oppdaterjournalpostId(it)
            søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.JOURNALFØRT)
        }.right()
    }

    private fun opprettOppgave(
        søknad: Søknad.Journalført.UtenOppgave,
        person: Person,
        opprettOppgave: (oppgaveConfig: OppgaveConfig.Søknad) -> Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> = oppgaveService::opprettOppgave,
    ): Either<KunneIkkeOppretteOppgave, Søknad.Journalført.MedOppgave> {
        return opprettOppgave(
            OppgaveConfig.Søknad(
                journalpostId = søknad.journalpostId,
                søknadId = søknad.id,
                aktørId = person.ident.aktørId,
                clock = clock,
                tilordnetRessurs = null,
                sakstype = søknad.søknadInnhold.type(),
            ),
        ).mapLeft {
            log.error("Ny søknad: Kunne ikke opprette oppgave for sak ${søknad.sakId} og søknad ${søknad.id}. Originalfeil: $it")
            KunneIkkeOppretteOppgave(søknad.sakId, søknad.id, søknad.journalpostId, "Kunne ikke opprette oppgave")
        }.map { oppgaveId ->
            return søknad.medOppgave(oppgaveId).also {
                søknadRepo.oppdaterOppgaveId(it)
                søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.OPPRETTET_OPPGAVE)
            }.right()
        }
    }

    override fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: FantIkkeSøknad.left()
    }

    override fun hentSøknadPdf(søknadId: UUID): Either<KunneIkkeLageSøknadPdf, ByteArray> {
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

    private fun SøknadInnhold.kanSendeInnSøknad(): Boolean {
        return when (this) {
            is SøknadsinnholdAlder -> toggleService.isEnabled("supstonad.alder.innsending")
            is SøknadsinnholdUføre -> true
        }
    }
}
