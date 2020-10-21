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
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.brev.PdfTemplate
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.brev.BrevService
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
    private val oppgaveClient: OppgaveClient,
    private val brevService: BrevService,
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
        pdfGenerator.genererPdf(objectMapper.writeValueAsString(søknadInnhold), PdfTemplate.Søknad).fold(
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

    override fun lukkSøknad(
        søknadId: UUID,
        lukketSøknad: Søknad.Lukket
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        return trekkSøknad(
            søknadId = søknadId,
            loggtema = "Trekking av søknad",
            lukketSøknad = lukketSøknad
        )
    }

    private fun trekkSøknad(
        søknadId: UUID,
        loggtema: String,
        lukketSøknad: Søknad.Lukket
    ): Either<KunneIkkeLukkeSøknad, Sak> {
        val søknad = hentSøknad(søknadId).getOrElse {
            log.error("$loggtema: Fant ikke søknad")
            return KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
        }
        if (søknad.lukket != null) {
            log.error("$loggtema: Prøver å lukke en allerede trukket søknad")
            return KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknadId)) {
            log.error("$loggtema: Kan ikke lukke søknad. Finnes en behandling")
            return KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
        }

        return brevService.journalførLukketSøknadOgSendBrev(
            sakId = søknad.sakId,
            søknad = søknad,
            lukketSøknad = lukketSøknad
        ).fold(
            ifLeft = {
                log.error("$loggtema: Kunne ikke sende brev for å lukke søknad")
                KunneIkkeLukkeSøknad.KunneIkkeSendeBrev.left()
            },
            ifRight = {
                log.info("Bestilt distribusjon av brev for trukket søknad. Bestillings-id: $it")
                søknadRepo.lukkSøknad(
                    søknadId = søknadId,
                    lukket = lukketSøknad
                )
                log.info("Trukket søknad $søknadId")
                return sakService.hentSak(søknad.sakId).mapLeft {
                    return KunneIkkeLukkeSøknad.KunneIkkeSendeBrev.left()
                }
            }
        )
    }

    override fun lagLukketSøknadBrevutkast(
        søknadId: UUID,
        lukketSøknad: Søknad.Lukket
    ): Either<KunneIkkeLageBrevutkast, ByteArray> {
        val søknad = hentSøknad(søknadId).getOrElse {
            log.error("Lukket brevutkast: Fant ikke søknad")
            return KunneIkkeLageBrevutkast.FantIkkeSøknad.left()
        }

        val person = hentPersonFraFnr(søknad.søknadInnhold.personopplysninger.fnr).fold(
            { return KunneIkkeLageBrevutkast.FeilVedHentingAvPerson.left() },
            { it }
        )

        val lukketSøknadBrevinnhold = LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
            person = person,
            søknad = søknad,
            lukketSøknad = lukketSøknad
        )
        return brevService.lagBrev(lukketSøknadBrevinnhold)
            .mapLeft { KunneIkkeLageBrevutkast.FeilVedGenereringAvBrevutkast }
    }

    private fun hentPersonFraFnr(fnr: Fnr) = personOppslag.person(fnr)
        .mapLeft {
            log.error("Fant ikke person i eksternt system basert på sakens fødselsnummer.")
            it
        }.map {
            log.info("Hentet person fra eksternt system OK")
            it
        }
}
