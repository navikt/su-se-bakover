package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val sakFactory: SakFactory,
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val personOppslag: PersonOppslag,
    private val oppgaveService: OppgaveService,
    private val søknadMetrics: SøknadMetrics
) : SøknadService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun nySøknad(søknadInnhold: SøknadInnhold): Either<KunneIkkeOppretteSøknad, Sak> {

        val fnr: Fnr = søknadInnhold.personopplysninger.fnr

        val person = personOppslag.person(fnr).getOrHandle {
            // Dette bør ikke skje i normal flyt, siden vi allerede har slått opp personen i PDL før vi begynte søknaden.
            log.error("Ny søknad: Fant ikke person med gitt fødselsnummer. Originalfeil: $it")
            return KunneIkkeOppretteSøknad.FantIkkePerson.left()
        }

        if (person.ident.fnr != fnr) {
            // TODO jah: Dersom disse ikke er like, bruker vi søknadens fnr alle steder bortsett fra i journalføringa, som bruker fnr fra PDL.
            // Bør vi returnere Left her? Og heller sjekke dette bedre når man slår opp fødselsnummer ved starten av søknaden?
            log.error("Personen har et nyere fødselsnummer i PDL enn det som var oppgitt.")
        }

        val (sak: Sak, søknad: Søknad) = sakService.hentSak(fnr).fold(
            {
                log.info("Ny søknad: Fant ikke sak for fødselsnummmer. Oppretter ny søknad og ny sak.")
                val nySak = sakFactory.nySak(fnr, søknadInnhold).also {
                    sakService.opprettSak(it)
                }
                Pair(nySak.toSak(), nySak.søknad)
            },
            {
                log.info("Ny søknad: Fant eksisterende sak for fødselsnummmer. Oppretter ny søknad på eksisterende sak.")
                val søknad = Søknad(
                    sakId = it.id,
                    søknadInnhold = søknadInnhold
                )
                søknadRepo.opprettSøknad(søknad)

                Pair(it.copy(søknader = (it.søknader() + søknad).toMutableList()), søknad)
            }
        )
        // Ved å gjøre increment først, kan vi lage en alert dersom vi får mismatch på dette.
        søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.PERSISTERT)
        opprettJournalpostOgOppgave(sak.id, person, søknad)
        return sak.right()
    }

    private fun opprettJournalpostOgOppgave(sakId: UUID, person: Person, søknad: Søknad) {
        // TODO jah: Lagre stegene på søknaden etterhvert som de blir utført, og kanskje et admin-kall som kan utføre de stegene som mangler.
        // TODO jah: Burde kanskje innføre en multi-respons-type som responderer med de stegene som er utført og de som ikke er utført.
        pdfGenerator.genererPdf(søknad.søknadInnhold).fold(
            {
                log.error("Ny søknad: Kunne ikke generere PDF. Originalfeil: $it")
            },
            { pdfByteArray ->
                log.info("Ny søknad: Generert PDF ok.")
                dokArkiv.opprettJournalpost(
                    Journalpost.Søknadspost(
                        søknadInnhold = søknad.søknadInnhold,
                        pdf = pdfByteArray,
                        sakId = sakId.toString(),
                        person = person
                    )
                ).fold(
                    {
                        log.error("Ny søknad: Kunne ikke opprette journalpost. Originalfeil: $it")
                    },
                    { journalpostId ->
                        log.info("Ny søknad: Opprettet journalpost med id $journalpostId")
                        søknadRepo.oppdaterjournalpostId(søknad.id, journalpostId)
                        søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.JOURNALFØRT)
                        oppgaveService.opprettOppgave(
                            OppgaveConfig.Saksbehandling(
                                journalpostId = journalpostId,
                                sakId = sakId.toString(),
                                aktørId = person.ident.aktørId
                            )
                        ).mapLeft {
                            log.error("Ny søknad: Kunne ikke opprette oppgave. Originalfeil: $it")
                        }.map { oppgaveId ->
                            log.info("Ny søknad: Opprettet oppgave med id $oppgaveId.")
                            søknadRepo.oppdaterOppgaveId(søknad.id, oppgaveId)
                            søknadMetrics.incrementNyCounter(SøknadMetrics.NyHandlinger.OPPRETTET_OPPGAVE)
                        }
                    }
                )
            }
        )
    }

    override fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
    }
}
