package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val sakFactory: SakFactory,
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val søknadMetrics: SøknadMetrics,
    private val clock: Clock,
) : SøknadService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers = mutableListOf<EventObserver>()

    fun addObserver(observer: EventObserver) = observers.add(observer)

    override fun nySøknad(søknadInnhold: SøknadInnhold): Either<KunneIkkeOppretteSøknad, Pair<Saksnummer, Søknad>> {
        val innsendtFødselsnummer: Fnr = søknadInnhold.personopplysninger.fnr

        val person = personService.hentPerson(innsendtFødselsnummer).getOrHandle {
            // Dette bør ikke skje i normal flyt, siden vi allerede har gjort en tilgangssjekk mot PDL (kode6/7).
            log.error("Ny søknad: Fant ikke person med gitt fødselsnummer. Originalfeil: $it")
            return KunneIkkeOppretteSøknad.FantIkkePerson.left()
        }
        val fnr = person.ident.fnr
        val søknadsinnholdMedNyesteFødselsnummer = søknadInnhold.copy(
            personopplysninger = søknadInnhold.personopplysninger.copy(
                // Ønsker alltid å bruke det nyeste fødselsnummeret
                fnr = fnr
            )
        )

        if (fnr != innsendtFødselsnummer) {
            log.error("Ny søknad: Personen har et nyere fødselsnummer i PDL enn det som ble sendt inn. Bruker det nyeste fødselsnummeret istedet. Personoppslaget burde ha returnert det nyeste fødselsnummeret og bør sjekkes opp.")
        }

        val (sak: Sak, søknad: Søknad.Ny) = sakService.hentSak(fnr).fold(
            {
                log.info("Ny søknad: Fant ikke sak for fødselsnummmer. Oppretter ny søknad og ny sak.")
                val nySak = sakFactory.nySakMedNySøknad(fnr, søknadsinnholdMedNyesteFødselsnummer).also {
                    sakService.opprettSak(it)
                }
                val opprettetSak = sakService.hentSak(fnr).getOrElse { throw RuntimeException("Feil ved henting av sak") }
                Pair(opprettetSak, nySak.søknad)
            },
            {
                log.info("Ny søknad: Fant eksisterende sak for fødselsnummmer. Oppretter ny søknad på eksisterende sak.")
                val søknad = Søknad.Ny(
                    sakId = it.id,
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    søknadInnhold = søknadsinnholdMedNyesteFødselsnummer,
                )
                søknadRepo.opprettSøknad(søknad)

                Pair(it.copy(søknader = (it.søknader + søknad)), søknad)
            }
        )
        // Ved å gjøre increment først, kan vi lage en alert dersom vi får mismatch på dette.
        søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.PERSISTERT)
        opprettJournalpostOgOppgave(sak, person, søknad)
        observers.forEach {
            observer ->
            observer.handle(
                Event.Statistikk.SøknadStatistikk.SøknadMottatt(søknad, sak.saksnummer)
            )
        }
        return Pair(sak.saksnummer, søknad).right()
    }

    override fun opprettManglendeJournalpostOgOppgave(): OpprettManglendeJournalpostOgOppgaveResultat {
        return OpprettManglendeJournalpostOgOppgaveResultat(
            journalpostResultat = opprettManglendeJournalposteringer(),
            oppgaveResultat = opprettManglendeOppgaver()
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
                person
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
                sak = sak,
                søknad = søknad,
                person = person,
                opprettOppgave = oppgaveService::opprettOppgaveMedSystembruker,
            )
        }
    }

    private fun opprettJournalpostOgOppgave(
        sak: Sak,
        person: Person,
        søknad: Søknad.Ny,
    ) {
        // TODO jah: Burde kanskje innføre en multi-respons-type som responderer med de stegene som er utført og de som ikke er utført.
        opprettJournalpost(sak.saksnummer, søknad, person).map { journalførtSøknad ->
            opprettOppgave(sak, journalførtSøknad, person)
        }
    }

    private fun opprettJournalpost(
        saksnummer: Saksnummer,
        søknad: Søknad.Ny,
        person: Person
    ): Either<KunneIkkeOppretteJournalpost, Søknad.Journalført.UtenOppgave> {
        val pdfByteArray = pdfGenerator.genererPdf(
            SøknadPdfInnhold.create(
                saksnummer = saksnummer,
                søknadsId = søknad.id,
                navn = person.navn,
                søknadOpprettet = søknad.opprettet,
                søknadInnhold = søknad.søknadInnhold,
                clock = clock,
            )
        ).getOrHandle {
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
        ).getOrHandle {
            log.error("Ny søknad: Kunne ikke opprette journalpost. Originalfeil: $it")
            return KunneIkkeOppretteJournalpost(søknad.sakId, søknad.id, "Kunne ikke opprette journalpost").left()
        }

        return søknad.journalfør(journalpostId).also {
            søknadRepo.oppdaterjournalpostId(it)
            søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.JOURNALFØRT)
        }.right()
    }

    private fun opprettOppgave(
        sak: Sak,
        søknad: Søknad.Journalført.UtenOppgave,
        person: Person,
        opprettOppgave: (oppgaveConfig: OppgaveConfig.NySøknad) -> Either<no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> = oppgaveService::opprettOppgave,
    ): Either<KunneIkkeOppretteOppgave, Søknad.Journalført.MedOppgave> {

        return opprettOppgave(
            OppgaveConfig.NySøknad(
                journalpostId = søknad.journalpostId,
                søknadId = søknad.id,
                aktørId = person.ident.aktørId,
                søknadstype = sak.hentSøknadstypeUtenBehandling().getOrHandle {
                    return KunneIkkeOppretteOppgave(søknad.sakId, søknad.id, søknad.journalpostId, it.toString()).left()
                },
            ),
        ).mapLeft {
            log.error("Ny søknad: Kunne ikke opprette oppgave. Originalfeil: $it")
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
                    personService.hentPerson(søknad.søknadInnhold.personopplysninger.fnr).mapLeft {
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
                            )
                        ).mapLeft {
                            log.error("Hent søknad-PDF: Kunne ikke generere PDF. Originalfeil: $it")
                            KunneIkkeLageSøknadPdf.KunneIkkeLagePdf
                        }
                    }
                }
            }
    }
}
