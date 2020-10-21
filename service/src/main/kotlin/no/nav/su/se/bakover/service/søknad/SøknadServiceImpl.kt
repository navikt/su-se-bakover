package no.nav.su.se.bakover.service.søknad
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadServiceImpl(
    private val søknadRepo: SøknadRepo,
    private val sakService: SakService,
    private val sakFactory: SakFactory,
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val personOppslag: PersonOppslag,
    private val oppgaveClient: OppgaveClient
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
            log.error("Personen har et nyere fødselsnummer i PDL enn det som var oppgitt.")
        }

        val sak: Sak = sakService.hentSak(fnr).fold(
            {
                log.info("Ny søknad: Fant ikke sak for fødselsnummmer. Oppretter ny søknad og ny sak.")
                val nySak = sakFactory.nySak(fnr, søknadInnhold).also {
                    sakService.opprettSak(it)
                }
                nySak.toSak()
            },
            {
                log.info("Ny søknad: Fant eksisterende sak for fødselsnummmer. Oppretter ny søknad på eksisterende sak.")
                val søknad = Søknad(
                    sakId = it.id,
                    søknadInnhold = søknadInnhold
                )
                søknadRepo.opprettSøknad(søknad)
                it
            }
        )
        opprettJournalpostOgOppgave(sak.id, person, søknadInnhold)
        return sak.right()
    }

    private fun opprettJournalpostOgOppgave(sakId: UUID, person: Person, søknadInnhold: SøknadInnhold) {
        // TODO jah: Lagre stegene på søknaden etterhvert som de blir utført, og kanskje et admin-kall som kan utføre de stegene som mangler.
        // TODO jah: Burde kanskje innføre en multi-respons-type som responderer med de stegene som er utført og de som ikke er utført.
        pdfGenerator.genererPdf(søknadInnhold).fold(
            {
                log.error("Ny søknad: Kunne ikke generere PDF. Originalfeil: $it")
            },
            { pdfByteArray ->
                log.info("Ny søknad: Generert PDF ok.")
                dokArkiv.opprettJournalpost(
                    Journalpost.Søknadspost(
                        søknadInnhold = søknadInnhold,
                        pdf = pdfByteArray,
                        sakId = sakId.toString(),
                        person = person
                    )
                ).fold(
                    {
                        log.error("Ny søknad: Kunne ikke opprette journalpost. Originalfeil: $it")
                    },
                    { journalpostId ->
                        log.info("Ny søknad: Opprettet journalpost ok.")
                        oppgaveClient.opprettOppgave(
                            OppgaveConfig.Saksbehandling(
                                journalpostId = journalpostId,
                                sakId = sakId.toString(),
                                aktørId = person.ident.aktørId
                            )

                        ).mapLeft {
                            log.error("Ny søknad: Kunne ikke opprette oppgave. Originalfeil: $it")
                        }.map {
                            log.info("Ny søknad: Opprettet oppgave ok.")
                        }
                    }
                )
            }
        )
    }

    override fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad> {
        return søknadRepo.hentSøknad(søknadId)?.right() ?: KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
    }

    private fun lukkSøknad(
        søknadId: UUID,
        saksbehandler: Saksbehandler,
        begrunnelse: String,
        loggtema: String
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        val søknad = hentSøknad(søknadId).getOrElse {
            log.info("$loggtema: Fant ikke søknad")
            return KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
        }
        if (søknad.lukket != null) {
            log.info("$loggtema: Prøver å lukke en allerede trukket søknad")
            return KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknadId)) {
            log.info("$loggtema: Kan ikke lukke søknad. Finnes en behandling")
            return KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
        }

        søknadRepo.lukkSøknad(
            søknadId = søknadId,
            lukket = Søknad.Lukket.Trukket(
                tidspunkt = Tidspunkt.now(),
                saksbehandler = saksbehandler,
                begrunnelse = begrunnelse
            )
        )
        return sakService.hentSak(søknad.sakId).orNull()!!.right()
    }

    override fun trekkSøknad(
        søknadId: UUID,
        saksbehandler: Saksbehandler,
        begrunnelse: String
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        return lukkSøknad(
            søknadId = søknadId,
            saksbehandler = saksbehandler,
            begrunnelse = begrunnelse,
            loggtema = "Trekking av søknad"
        )
    }
}
