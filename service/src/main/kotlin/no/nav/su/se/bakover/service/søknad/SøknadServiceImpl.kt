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
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
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

    override fun nySøknad(søknadInnhold: SøknadInnhold): Sak {
        val sak: Sak = sakService.hentSak(søknadInnhold.personopplysninger.fnr).getOrHandle {
            log.info("Ny søknad: Fant ikke sak for fødselsnummmer. Oppretter ny sak.")
            sakFactory.nySak(søknadInnhold.personopplysninger.fnr)
        }
        val søknad = Søknad(
            sakId = sak.id,
            søknadInnhold = søknadInnhold
        )
        opprettJournalpostOgOppgave(sak.id, søknad)
        sakService.opprettSak(sak)
        søknadRepo.opprettSøknad(søknad) // TODO: Denne kan merges inn i sakService.opprettSak så vi kan gjøre det i en transaksjon som med oppdrag
        return sak
    }

    private fun opprettJournalpostOgOppgave(sakId: UUID, søknad: Søknad) {
        pdfGenerator.genererPdf(søknad.søknadInnhold).fold(
            {
                log.error("$it")
            },
            { pdfByteArray ->
                val fnr = søknad.søknadInnhold.personopplysninger.fnr
                dokArkiv.opprettJournalpost(
                    Journalpost.Søknadspost(
                        person = personOppslag.person(fnr).getOrElse {
                            log.error("Fant ikke person med gitt fødselsnummer")
                            throw RuntimeException("Kunne ikke finne person")
                        },
                        søknadInnhold = søknad.søknadInnhold,
                        pdf = pdfByteArray,
                        sakId = sakId.toString()
                    )
                ).fold(
                    {
                        log.error("$it")
                    },
                    { journalpostId ->
                        val aktørId: AktørId = personOppslag.aktørId(fnr).getOrElse {
                            log.error("Fant ikke aktør-id med gitt fødselsnummer")
                            throw RuntimeException("Kunne ikke finne aktørid")
                        }
                        oppgaveClient.opprettOppgave(
                            OppgaveConfig.Saksbehandling(
                                journalpostId = journalpostId,
                                sakId = sakId.toString(),
                                aktørId = aktørId
                            )
                        ).mapLeft {
                            log.error("$it")
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
