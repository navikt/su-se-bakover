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
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val sakFactory: SakFactory,
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val søknadMetrics: SøknadMetrics
) : SøknadService {
    private val log = LoggerFactory.getLogger(this::class.java)

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

        val (sak: Sak, søknad: Søknad) = sakService.hentSak(fnr).fold(
            {
                log.info("Ny søknad: Fant ikke sak for fødselsnummmer. Oppretter ny søknad og ny sak.")
                val nySak = sakFactory.nySak(fnr, søknadsinnholdMedNyesteFødselsnummer).also {
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
                    opprettet = Tidspunkt.now(),
                    søknadInnhold = søknadsinnholdMedNyesteFødselsnummer,
                )
                søknadRepo.opprettSøknad(søknad)

                Pair(it.copy(søknader = (it.søknader() + søknad).toMutableList()), søknad)
            }
        )
        // Ved å gjøre increment først, kan vi lage en alert dersom vi får mismatch på dette.
        søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.PERSISTERT)
        opprettJournalpostOgOppgave(sak.saksnummer, person, søknad)
        return Pair(sak.saksnummer, søknad).right()
    }

    fun opprettManglendeJournalpostOgOppgave() {
        søknadRepo.hentSøknaderUtenJournalpost()
    }

    private fun opprettManglendeJournalposter() {
    }

    private fun opprettJournalpostOgOppgave(saksnummer: Saksnummer, person: Person, søknad: Søknad) {
        // TODO jah: Burde kanskje innføre en multi-respons-type som responderer med de stegene som er utført og de som ikke er utført.
        opprettJournalpost(saksnummer, søknad, person)?.let { journalpostId ->
            opprettOppgave(journalpostId, søknad, person)
        }
    }

    private fun opprettJournalpost(
        saksnummer: Saksnummer,
        søknad: Søknad,
        person: Person
    ): JournalpostId? {
        val pdfByteArray = pdfGenerator.genererPdf(
            SøknadPdfInnhold(
                saksnummer = saksnummer,
                søknadsId = søknad.id,
                navn = person.navn,
                søknadOpprettet = søknad.opprettet.toLocalDate(zoneIdOslo).ddMMyyyy(),
                søknadInnhold = søknad.søknadInnhold
            )
        ).getOrHandle {
            log.error("Ny søknad: Kunne ikke generere PDF. Originalfeil: $it")
            return null
        }
        log.info("Ny søknad: Generert PDF ok.")

        val journalpostId = dokArkiv.opprettJournalpost(
            Journalpost.Søknadspost(
                søknadInnhold = søknad.søknadInnhold,
                pdf = pdfByteArray,
                saksnummer = saksnummer,
                person = person
            )
        ).getOrHandle {
            log.error("Ny søknad: Kunne ikke opprette journalpost. Originalfeil: $it")
            return null
        }
        log.info("Ny søknad: Opprettet journalpost med id $journalpostId")
        søknadRepo.oppdaterjournalpostId(søknad.id, journalpostId)
        søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.JOURNALFØRT)
        return journalpostId
    }

    private fun opprettOppgave(
        journalpostId: JournalpostId,
        søknad: Søknad,
        person: Person
    ) = oppgaveService.opprettOppgave(
        OppgaveConfig.Saksbehandling(
            journalpostId = journalpostId,
            søknadId = søknad.id,
            aktørId = person.ident.aktørId
        )
    ).mapLeft {
        log.error("Ny søknad: Kunne ikke opprette oppgave. Originalfeil: $it")
    }.map { oppgaveId ->
        log.info("Ny søknad: Opprettet oppgave med id $oppgaveId.")
        søknadRepo.oppdaterOppgaveId(søknad.id, oppgaveId)
        søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.OPPRETTET_OPPGAVE)
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
                            SøknadPdfInnhold(
                                saksnummer = sak.saksnummer,
                                søknadsId = søknad.id,
                                navn = person.navn,
                                søknadOpprettet = søknad.opprettet.toLocalDate(zoneIdOslo).ddMMyyyy(),
                                søknadInnhold = søknad.søknadInnhold
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
