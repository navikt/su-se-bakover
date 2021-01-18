package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.slf4j.LoggerFactory

class FerdigstillIverksettingService(
    private val behandlingRepo: BehandlingRepo,
    private val oppgaveService: OppgaveService,
    private val behandlingMetrics: BehandlingMetrics,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val journalførIverksettingService: JournalførIverksettingService,
    private val personService: PersonService,
    private val distribuerIverksettingsbrevService: DistribuerIverksettingsbrevService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    sealed class KunneIkkeFerdigstilleInnvilgelse {
        object BehandlingManglerSaksbehandler : KunneIkkeFerdigstilleInnvilgelse()
        object BehandlingManglerAttestant : KunneIkkeFerdigstilleInnvilgelse()
        object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeOppretteJournalpost : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeDistribuereBrev : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeOppretteOppgave : KunneIkkeFerdigstilleInnvilgelse()
    }

    fun ferdigstillInnvilgelse(utbetalingId: UUID30) {
        val behandling = behandlingRepo.hentBehandlingForUtbetaling(utbetalingId)
            ?: return Unit.also { log.error("Kunne ikke ferdigstille innvilgelse - fant ikke behandling for utbetaling $utbetalingId") }

        val person = personService.hentPerson(behandling.fnr).getOrHandle {
            log.error("Kunne ikke ferdigstille innvilgelse - fant ikke person for saksnr ${behandling.saksnummer}")
            return
        }

        ferdigstillInnvilgelse(behandling, person)
    }

    private fun ferdigstillInnvilgelse(
        behandling: Behandling,
        person: Person
    ): Either<KunneIkkeFerdigstilleInnvilgelse, Unit> {
        val journalføringOgBrevResultat = opprettJournalpostOgBrevbestilling(behandling, person)
        val oppgaveResultat = opprettOppgave(behandling)

        return journalføringOgBrevResultat.flatMap { oppgaveResultat }
    }

    private fun opprettOppgave(behandling: Behandling): Either<KunneIkkeFerdigstilleInnvilgelse, Unit> {
        return oppgaveService.lukkOppgave(behandling.oppgaveId())
            .mapLeft {
                log.error("Kunne ikke lukke oppgave ved innvilgelse for behandling ${behandling.id}. Dette må gjøres manuelt.")
                return KunneIkkeFerdigstilleInnvilgelse.KunneIkkeOppretteOppgave.left()
            }
            .map {
                log.info("Lukket oppgave ${behandling.oppgaveId()} ved innvilgelse for behandling ${behandling.id}")
                // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
            }
    }

    fun opprettManglendeJournalpostOgBrevdistribusjon(): OpprettManglendeJournalpostOgBrevdistribusjonResultat {
        return OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = opprettManglendeJournalposteringer(),
            brevbestillingsresultat = opprettManglendeBrevbestillinger(),
        )
    }

    private fun opprettManglendeJournalposteringer() =
        behandlingRepo.hentIverksatteBehandlingerUtenJournalposteringer().map { behandling ->
            if (behandling.iverksattJournalpostId() != null) {
                return@map KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    grunn = "Kunne ikke opprette journalpost for iverksetting siden den allerede eksisterer"
                ).left()
            }
            if (behandling.status() != Behandling.BehandlingsStatus.IVERKSATT_INNVILGET) {
                return@map KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    grunn = "Kunne ikke opprette journalpost for status ${behandling.status()}"
                ).left()
            }
            val saksbehandlerNavn =
                hentNavnForNavIdent(behandling.saksbehandler()!!).getOrHandle {
                    return@map KunneIkkeOppretteJournalpostForIverksetting(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        grunn = "Kunne ikke hente saksbehandlers navn"
                    ).left()
                }
            val attestantNavn =
                hentNavnForNavIdent(behandling.attestering()!!.attestant).getOrHandle {
                    return@map KunneIkkeOppretteJournalpostForIverksetting(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        grunn = "Kunne ikke hente attestants navn"
                    ).left()
                }
            val person = personService.hentPerson(behandling.fnr).getOrElse {
                return@map KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    grunn = "Kunne ikke hente person"
                ).left()
            }
            return@map opprettJournalpostForInnvilgelse(
                behandling = behandling,
                person = person,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
            ).mapLeft {
                KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    grunn = "Kunne ikke opprette journalpost mot eksternt system"
                )
            }
        }

    private fun opprettManglendeBrevbestillinger(): List<Either<KunneIkkeBestilleBrev, BestiltBrev>> {
        return behandlingRepo.hentIverksatteBehandlingerUtenBrevbestillinger().map { behandling ->
            val journalpostId = behandling.iverksattJournalpostId() ?: return@map KunneIkkeBestilleBrev(
                sakId = behandling.sakId,
                behandlingId = behandling.id,
                journalpostId = null,
                grunn = "Kunne ikke opprette brevbestilling siden iverksattJournalpostId er null."
            ).left()

            if (behandling.iverksattBrevbestillingId() != null) {
                return@map KunneIkkeBestilleBrev(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    journalpostId = journalpostId,
                    grunn = "Kunne ikke opprette brevbestilling siden den allerde eksisterer"
                ).left()
            }

            if (listOf(
                    Behandling.BehandlingsStatus.IVERKSATT_INNVILGET,
                    Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
                ).none { it == behandling.status() }
            ) {
                return@map KunneIkkeBestilleBrev(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    journalpostId = journalpostId,
                    grunn = "Kunne ikke bestille brev for status ${behandling.status()}"
                ).left()
            }
            return@map distribuerIverksettingsbrevService.distribuerBrev(
                behandling = behandling,
            ).mapLeft {
                KunneIkkeBestilleBrev(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    journalpostId = journalpostId,
                    grunn = "Kunne ikke bestille brev"
                )
            }.map {
                BestiltBrev(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    journalpostId = journalpostId,
                    brevbestillingId = it.iverksattBrevbestillingId()!!
                )
            }
        }
    }

    private fun opprettJournalpostOgBrevbestilling(
        behandling: Behandling,
        person: Person
    ): Either<KunneIkkeFerdigstilleInnvilgelse, Unit> {
        val saksbehandlerNavn =
            behandling.saksbehandler()?.let { hentNavnForNavIdent(it).getOrHandle { return it.left() } }
                ?: return KunneIkkeFerdigstilleInnvilgelse.BehandlingManglerSaksbehandler.left()
        val attestantNavn =
            behandling.attestering()?.let { hentNavnForNavIdent(it.attestant).getOrHandle { return it.left() } }
                ?: return KunneIkkeFerdigstilleInnvilgelse.BehandlingManglerAttestant.left()

        return opprettJournalpostForInnvilgelse(
            behandling = behandling,
            person = person,
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn
        )
            .mapLeft {
                log.error("Journalføring av iverksettingsbrev feilet for behandling ${behandling.id}. Dette må gjøres manuelt.")
                it
            }
            .flatMap {
                log.info("Journalført iverksettingsbrev $it for behandling ${behandling.id}")
                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT)
                distribuerIverksettingsbrevService.distribuerBrev(behandling)
                    .mapLeft { KunneIkkeFerdigstilleInnvilgelse.KunneIkkeDistribuereBrev }
                    .map {
                        behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                    }
            }
    }

    private fun opprettJournalpostForInnvilgelse(
        behandling: Behandling,
        person: Person,
        saksbehandlerNavn: String,
        attestantNavn: String,
    ): Either<KunneIkkeFerdigstilleInnvilgelse, OpprettetJournalpostForIverksetting> {

        return journalførIverksettingService.opprettJournalpost(
            behandling,
            LagBrevRequest.InnvilgetVedtak(
                person = person,
                behandling = behandling,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn
            )
        ).mapLeft {
            KunneIkkeFerdigstilleInnvilgelse.KunneIkkeOppretteJournalpost
        }.map {
            OpprettetJournalpostForIverksetting(
                sakId = behandling.sakId,
                behandlingId = behandling.id,
                journalpostId = it
            )
        }
    }

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .mapLeft { KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant }
            .map { it.displayName }
    }
}
