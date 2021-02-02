package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.slf4j.LoggerFactory

interface FerdigstillIverksettingService {
    fun hentBehandlingForUtbetaling(utbetalingId: UUID30): Søknadsbehandling.Iverksatt.Innvilget?

    fun ferdigstillInnvilgelse(
        behandling: Søknadsbehandling.Iverksatt.Innvilget
    )

    fun opprettManglendeJournalpostOgBrevdistribusjon(): OpprettManglendeJournalpostOgBrevdistribusjonResultat

    sealed class KunneIkkeFerdigstilleInnvilgelse {
        object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeOppretteJournalpost : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeDistribuereBrev : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeOppretteOppgave : KunneIkkeFerdigstilleInnvilgelse()
        object FantIkkePerson : KunneIkkeFerdigstilleInnvilgelse()
    }
}

class FerdigstillIverksettingServiceImpl(
    private val saksbehandlingRepo: SaksbehandlingRepo,
    private val oppgaveService: OppgaveService,
    private val behandlingMetrics: BehandlingMetrics,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val personService: PersonService,
    private val brevService: BrevService,
) : FerdigstillIverksettingService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentBehandlingForUtbetaling(utbetalingId: UUID30): Søknadsbehandling.Iverksatt.Innvilget? {
        return saksbehandlingRepo.hentBehandlingForUtbetaling(utbetalingId)
    }

    override fun ferdigstillInnvilgelse(
        behandling: Søknadsbehandling.Iverksatt.Innvilget
    ) {
        opprettJournalpostOgBrevbestillingForInnvilgelse(behandling)
        lukkOppgave(behandling)
    }

    private fun lukkOppgave(behandling: Søknadsbehandling): Either<FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse, Unit> {
        return oppgaveService.lukkOppgaveMedSystembruker(behandling.oppgaveId)
            .mapLeft {
                log.error("Kunne ikke lukke oppgave ved innvilgelse for behandling ${behandling.id}. Dette må gjøres manuelt.")
                return FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeOppretteOppgave.left()
            }
            .map {
                log.info("Lukket oppgave ${behandling.oppgaveId} ved innvilgelse for behandling ${behandling.id}")
                // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
            }
    }

    override fun opprettManglendeJournalpostOgBrevdistribusjon(): OpprettManglendeJournalpostOgBrevdistribusjonResultat {
        return OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = opprettManglendeJournalposteringer().map {
                it.map { behandling ->
                    OpprettetJournalpostForIverksetting(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        journalpostId = behandling.eksterneIverksettingsteg.journalpostId()!! // Skal egentlig ikke være i tilstanden VenterPåKvittering
                    )
                }
            },
            brevbestillingsresultat = opprettManglendeBrevbestillinger(),
        )
    }

    private fun opprettManglendeJournalposteringer(): List<Either<KunneIkkeOppretteJournalpostForIverksetting, Søknadsbehandling.Iverksatt.Innvilget>> {
        return saksbehandlingRepo.hentIverksatteBehandlingerUtenJournalposteringer().map { behandling ->

            if (behandling.eksterneIverksettingsteg !is Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.VenterPåKvittering) {
                return@map KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    grunn = "Kunne ikke opprette journalpost for iverksetting siden den allerede eksisterer"
                ).left()
            }
            return@map opprettJournalpostForInnvilgelse(
                behandling = behandling,
            ).mapLeft {
                KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    grunn = it.javaClass.simpleName
                )
            }
        }
    }

    private fun opprettManglendeBrevbestillinger(): List<Either<KunneIkkeBestilleBrev, BestiltBrev>> {
        return saksbehandlingRepo.hentIverksatteBehandlingerUtenBrevbestillinger().map { behandling ->
            when (behandling) {
                is Søknadsbehandling.Iverksatt.Avslag -> {
                    behandling.distribuerBrev { journalpostId ->
                        brevService.distribuerBrev(journalpostId).mapLeft {
                            Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                                journalpostId
                            )
                        }
                    }.map {
                        saksbehandlingRepo.lagre(it)
                        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
                        val e =
                            behandling.eksterneIverksettingsteg as Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.JournalførtOgDistribuertBrev
                        BestiltBrev(
                            sakId = behandling.sakId,
                            behandlingId = behandling.id,
                            journalpostId = e.journalpostId,
                            brevbestillingId = e.brevbestillingId,
                        )
                    }
                }
                is Søknadsbehandling.Iverksatt.Innvilget -> {
                    behandling.distribuerBrev { journalpostId ->
                        brevService.distribuerBrev(journalpostId).mapLeft {
                            Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                                journalpostId
                            )
                        }
                    }.map {
                        saksbehandlingRepo.lagre(it)
                        behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                        val e =
                            behandling.eksterneIverksettingsteg as Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.JournalførtOgDistribuertBrev

                        BestiltBrev(
                            sakId = behandling.sakId,
                            behandlingId = behandling.id,
                            journalpostId = e.journalpostId,
                            brevbestillingId = e.brevbestillingId,
                        )
                    }
                }
            }.mapLeft {
                when (it) {
                    is Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> KunneIkkeBestilleBrev(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        journalpostId = it.journalpostId,
                        grunn = it.javaClass.simpleName
                    )
                    is Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev -> KunneIkkeBestilleBrev(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        journalpostId = it.journalpostId,
                        grunn = it.javaClass.simpleName
                    )
                    is Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.MåJournalføresFørst -> KunneIkkeBestilleBrev(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        journalpostId = null,
                        grunn = it.javaClass.simpleName
                    )
                }
            }
        }
    }

    private fun opprettJournalpostOgBrevbestillingForInnvilgelse(
        behandling: Søknadsbehandling.Iverksatt.Innvilget
    ): Either<FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse, Unit> {

        return opprettJournalpostForInnvilgelse(behandling)
            .flatMap {
                it.distribuerBrev { journalpostId ->
                    brevService.distribuerBrev(journalpostId).mapLeft {
                        Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                            journalpostId
                        )
                    }
                }.map {
                    saksbehandlingRepo.lagre(it)
                    behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                }
                    .mapLeft { FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeDistribuereBrev }
            }
    }

    private fun opprettJournalpostForInnvilgelse(
        behandling: Søknadsbehandling.Iverksatt.Innvilget,
    ): Either<FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse, Søknadsbehandling.Iverksatt.Innvilget> {
        val saksbehandlerNavn =
            behandling.saksbehandler.let { hentNavnForNavIdent(it).getOrHandle { return it.left() } }
        val attestantNavn =
            behandling.attestering.let { hentNavnForNavIdent(it.attestant).getOrHandle { return it.left() } }
        val person = personService.hentPersonMedSystembruker(behandling.fnr).getOrHandle {
            log.error("Kunne ikke ferdigstille innvilgelse - fant ikke person for saksnr ${behandling.saksnummer}")
            return FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.FantIkkePerson.left()
        }

        return behandling.journalfør {
            brevService.journalførBrev(
                LagBrevRequest.InnvilgetVedtak(
                    person = person,
                    beregning = behandling.beregning,
                    behandlingsinformasjon = behandling.behandlingsinformasjon,
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = attestantNavn,
                ),
                behandling.saksnummer
            ).mapLeft { Søknadsbehandling.Iverksatt.KunneIkkeJournalføre.FeilVedJournalføring }
        }.mapLeft {
            when (it) {
                is Søknadsbehandling.Iverksatt.KunneIkkeJournalføre.AlleredeJournalført -> {
                    log.info("Behandlingen er allerede journalført med journalpostId ${it.journalpostId}")
                    return behandling.right()
                }
                is Søknadsbehandling.Iverksatt.KunneIkkeJournalføre.FeilVedJournalføring -> {
                    log.error("Journalføring av iverksettingsbrev feilet for behandling ${behandling.id}.")
                    FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeOppretteJournalpost
                }
            }
        }.map {
            saksbehandlingRepo.lagre(it)
            log.info("Journalført iverksettingsbrev $it for behandling ${behandling.id}")
            behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT)
            it
        }
    }

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .mapLeft { FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant }
            .map { it.displayName }
    }
}
