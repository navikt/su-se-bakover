package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.UnderkjentHandlinger
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class BehandlingServiceImpl(
    private val behandlingRepo: BehandlingRepo,
    private val hendelsesloggRepo: HendelsesloggRepo,
    private val utbetalingService: UtbetalingService,
    private val oppgaveService: OppgaveService,
    private val søknadService: SøknadService,
    private val søknadRepo: SøknadRepo, // TODO use services or repos? probably services
    private val personService: PersonService,
    private val brevService: BrevService,
    private val behandlingMetrics: BehandlingMetrics,
    private val clock: Clock,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val iverksettBehandlingService: IverksettBehandlingService,
) : BehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    override fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)?.right() ?: FantIkkeBehandling.left()
    }

    override fun underkjenn(
        behandlingId: UUID,
        attestering: Attestering.Underkjent
    ): Either<KunneIkkeUnderkjenneBehandling, Behandling> {
        return hentBehandling(behandlingId).mapLeft {
            log.info("Kunne ikke underkjenne ukjent behandling $behandlingId")
            KunneIkkeUnderkjenneBehandling.FantIkkeBehandling
        }.flatMap { behandling ->
            behandling.underkjenn(attestering)
                .mapLeft {
                    log.warn("Kunne ikke underkjenne behandling siden attestant og saksbehandler var samme person")
                    KunneIkkeUnderkjenneBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                }
                .map {
                    val aktørId: AktørId = personService.hentAktørId(behandling.fnr).getOrElse {
                        log.error("Kunne ikke underkjenne behandling; fant ikke aktør id")
                        return KunneIkkeUnderkjenneBehandling.FantIkkeAktørId.left()
                    }

                    val journalpostId: JournalpostId = behandling.søknad.journalpostId
                    val eksisterendeOppgaveId = behandling.oppgaveId()
                    val nyOppgaveId = oppgaveService.opprettOppgave(
                        OppgaveConfig.Saksbehandling(
                            journalpostId = journalpostId,
                            søknadId = behandling.søknad.id,
                            aktørId = aktørId,
                            tilordnetRessurs = behandling.saksbehandler()
                        )
                    ).getOrElse {
                        log.error("Behandling $behandlingId ble ikke underkjent. Klarte ikke opprette behandlingsoppgave")
                        return@underkjenn KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave.left()
                    }.also {
                        behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.OPPRETTET_OPPGAVE)
                    }
                    behandling.oppdaterOppgaveId(nyOppgaveId)
                    behandlingRepo.oppdaterAttestering(behandlingId, attestering)
                    behandlingRepo.oppdaterOppgaveId(behandling.id, nyOppgaveId)
                    behandlingRepo.oppdaterBehandlingStatus(it.id, it.status())
                    log.info("Behandling $behandlingId ble underkjent. Opprettet behandlingsoppgave $nyOppgaveId")
                    hendelsesloggRepo.oppdaterHendelseslogg(it.hendelseslogg)
                    behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.PERSISTERT)
                    oppgaveService.lukkOppgave(eksisterendeOppgaveId)
                        .mapLeft {
                            log.error("Kunne ikke lukke attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen. Dette må gjøres manuelt.")
                        }.map {
                            log.info("Lukket attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen")
                            behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.LUKKET_OPPGAVE)
                        }
                    behandling.also {
                        observers.forEach { observer -> observer.handle(Event.Statistikk.BehandlingAttesteringUnderkjent(it)) }
                    }
                }
        }
    }

    // TODO need to define responsibilities for domain and services.
    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Either<KunneIkkeOppdatereBehandlingsinformasjon, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeOppdatereBehandlingsinformasjon.FantIkkeBehandling.left()

        return behandling.oppdaterBehandlingsinformasjon(
            saksbehandler,
            behandlingsinformasjon
        ) // invoke first to perform state-check
            .mapLeft {
                KunneIkkeOppdatereBehandlingsinformasjon.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            }
            .map {
                behandlingRepo.slettBeregning(behandlingId)
                behandlingRepo.oppdaterBehandlingsinformasjon(behandlingId, it.behandlingsinformasjon())
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun opprettBeregning(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeBeregne, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeBeregne.FantIkkeBehandling.left()

        return behandling.opprettBeregning(
            saksbehandler,
            fraOgMed,
            tilOgMed,
            fradrag
        ) // invoke first to perform state-check
            .mapLeft {
                KunneIkkeBeregne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            }
            .map {
                behandlingRepo.leggTilBeregning(it.id, it.beregning()!!)
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun simuler(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeSimulereBehandling, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeSimulereBehandling.FantIkkeBehandling.left()

        return behandling.leggTilSimulering(saksbehandler) {
            utbetalingService.simulerUtbetaling(behandling.sakId, saksbehandler, behandling.beregning()!!)
                .map { it.simulering }.orNull()
        }.mapLeft {
            when (it) {
                Behandling.KunneIkkeLeggeTilSimulering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeSimulereBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                Behandling.KunneIkkeLeggeTilSimulering.KunneIkkeSimulere -> KunneIkkeSimulereBehandling.KunneIkkeSimulere
            }
        }.map { simulertBehandling ->
            behandlingRepo.leggTilSimulering(behandlingId, simulertBehandling.simulering()!!)
            behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
            behandlingRepo.hentBehandling(behandlingId)!!
        }
    }

    // TODO need to define responsibilities for domain and services.
    override fun sendTilAttestering(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeTilAttestering, Behandling> {

        val behandlingTilAttestering: Behandling =
            behandlingRepo.hentBehandling(behandlingId).rightIfNotNull {
                return KunneIkkeSendeTilAttestering.FantIkkeBehandling.left()
            }.flatMap {
                it.sendTilAttestering(saksbehandler)
            }.getOrElse {
                return KunneIkkeSendeTilAttestering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }

        val aktørId = personService.hentAktørId(behandlingTilAttestering.fnr).getOrElse {
            log.error("Fant ikke aktør-id med for fødselsnummer : ${behandlingTilAttestering.fnr}")
            return KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()
        }

        val eksisterendeOppgaveId: OppgaveId = behandlingTilAttestering.oppgaveId()

        val nyOppgaveId: OppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.Attestering(
                behandlingTilAttestering.søknad.id,
                aktørId = aktørId,
                // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
                tilordnetRessurs = behandlingTilAttestering.attestering()?.attestant
            )
        ).getOrElse {
            log.error("Kunne ikke opprette Attesteringsoppgave. Avbryter handlingen.")
            return KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
        }.also {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.OPPRETTET_OPPGAVE)
        }
        behandlingTilAttestering.oppdaterOppgaveId(nyOppgaveId)
        behandlingRepo.oppdaterOppgaveId(behandlingTilAttestering.id, nyOppgaveId)
        behandlingRepo.settSaksbehandler(behandlingId, saksbehandler)
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandlingTilAttestering.status())
        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.PERSISTERT)

        oppgaveService.lukkOppgave(eksisterendeOppgaveId).map {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.LUKKET_OPPGAVE)
        }.mapLeft {
            log.error("Klarte ikke å lukke oppgave. kall til oppgave for oppgaveId ${behandlingTilAttestering.oppgaveId()} feilet")
        }
        return behandlingTilAttestering.let {
            observers.forEach { observer -> observer.handle(Event.Statistikk.BehandlingTilAttestering(it)) }
            it.right()
        }
    }

    override fun iverksett(
        behandlingId: UUID,
        attestant: NavIdentBruker.Attestant
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {
        return iverksettBehandlingService.iverksett(behandlingId, attestant)
    }

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<MicrosoftGraphApiOppslagFeil, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .map { it.displayName }
    }

    override fun opprettSøknadsbehandling(
        søknadId: UUID
    ): Either<KunneIkkeOppretteSøknadsbehandling, Behandling> {
        val søknad = søknadService.hentSøknad(søknadId).getOrElse {
            return KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad.left()
        }
        if (søknad is Søknad.Lukket) {
            return KunneIkkeOppretteSøknadsbehandling.SøknadErLukket.left()
        }
        if (søknad !is Søknad.Journalført.MedOppgave) {
            // TODO Prøv å opprette oppgaven hvis den mangler? (systembruker blir kanskje mest riktig?)
            return KunneIkkeOppretteSøknadsbehandling.SøknadManglerOppgave.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknad.id)) {
            // Dersom man legger til avslutting av behandlinger, må denne spørringa spesifiseres.
            return KunneIkkeOppretteSøknadsbehandling.SøknadHarAlleredeBehandling.left()
        }
        val nySøknadsbehandling = NySøknadsbehandling(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = søknad.sakId,
            søknadId = søknad.id,
            oppgaveId = søknad.oppgaveId
        )
        behandlingRepo.opprettSøknadsbehandling(
            nySøknadsbehandling
        )

        return behandlingRepo.hentBehandling(nySøknadsbehandling.id)!!.let {
            observers.forEach { observer -> observer.handle(Event.Statistikk.BehandlingOpprettet(it)) }
            it.right()
        }
    }

    override fun lagBrevutkast(behandlingId: UUID): Either<KunneIkkeLageBrevutkast, ByteArray> {
        return hentBehandling(behandlingId)
            .mapLeft { KunneIkkeLageBrevutkast.FantIkkeBehandling }
            .flatMap { behandling ->
                val attestantNavn = behandling.attestering()?.let {
                    hentNavnForNavIdent(it.attestant)
                        .getOrHandle { return KunneIkkeLageBrevutkast.FikkIkkeHentetSaksbehandlerEllerAttestant.left() }
                }
                val saksbehandlerNavn = behandling.saksbehandler()?.let {
                    hentNavnForNavIdent(it)
                        .getOrHandle { return KunneIkkeLageBrevutkast.FikkIkkeHentetSaksbehandlerEllerAttestant.left() }
                }
                personService.hentPerson(behandling.fnr)
                    .mapLeft {
                        KunneIkkeLageBrevutkast.FantIkkePerson
                    }.flatMap { person ->
                        lagBrevRequestForBrevutkast(
                            person = person,
                            behandling = behandling,
                            saksbehandlerNavn = saksbehandlerNavn,
                            attestantNavn = attestantNavn
                        ).flatMap {
                            brevService.lagBrev(it)
                                .mapLeft { KunneIkkeLageBrevutkast.KunneIkkeLageBrev }
                        }
                    }
            }
    }

    private fun lagBrevRequestForBrevutkast(
        person: Person,
        behandling: Behandling,
        saksbehandlerNavn: String?,
        attestantNavn: String?
    ): Either<KunneIkkeLageBrevutkast, LagBrevRequest> {
        if (behandling.erInnvilget()) {
            return LagBrevRequest.InnvilgetVedtak(
                person = person,
                behandling = behandling,
                saksbehandlerNavn = saksbehandlerNavn ?: "-",
                attestantNavn = attestantNavn ?: "-"
            ).right()
        }
        if (behandling.erAvslag()) {
            return AvslagBrevRequest(
                person,
                Avslag(
                    opprettet = Tidspunkt.now(clock),
                    avslagsgrunner = behandling.utledAvslagsgrunner(),
                    harEktefelle = behandling.behandlingsinformasjon().harEktefelle(),
                    beregning = behandling.beregning()
                ),
                saksbehandlerNavn = saksbehandlerNavn ?: "-",
                attestantNavn = attestantNavn ?: "-"
            ).right()
        }
        return KunneIkkeLageBrevutkast.KanIkkeLageBrevutkastForStatus(behandling.status()).left()
    }
}
