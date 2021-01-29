package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Statusovergang
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.behandling.forsøkStatusovergang
import no.nav.su.se.bakover.domain.behandling.statusovergang
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.behandling.IverksettSaksbehandlingService
import no.nav.su.se.bakover.service.behandling.KunneIkkeBeregne
import no.nav.su.se.bakover.service.behandling.KunneIkkeIverksetteBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppdatereBehandlingsinformasjon
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppretteSøknadsbehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.behandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeUnderkjenneBehandling
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.slf4j.LoggerFactory
import java.util.UUID

data class OpprettSøknadsbehandlingRequest(
    val søknadId: UUID
)

data class OppdaterSøknadsbehandlingsinformasjonRequest(
    val behandlingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val behandlingsinformasjon: Behandlingsinformasjon
)

data class OpprettBeregningRequest(
    val behandlingId: UUID,
    val periode: Periode,
    val fradrag: List<Fradrag>
)

data class OpprettSimuleringRequest(
    val behandlingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler
)

data class SendTilAttesteringRequest(
    val behandlingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler
)

data class UnderkjennSøknadsbehandlingRequest(
    val behandlingId: UUID,
    val attestering: Attestering.Underkjent
)

data class IverksettSøknadsbehandlingRequest(
    val behandlingId: UUID,
    val attestering: Attestering
)

interface SaksbehandlingService {
    fun opprett(request: OpprettSøknadsbehandlingRequest): Either<KunneIkkeOppretteSøknadsbehandling, Søknadsbehandling>
    fun vilkårsvurder(request: OppdaterSøknadsbehandlingsinformasjonRequest): Either<KunneIkkeOppdatereBehandlingsinformasjon, Søknadsbehandling>
    fun beregn(request: OpprettBeregningRequest): Either<KunneIkkeBeregne, Søknadsbehandling>
    fun simuler(request: OpprettSimuleringRequest): Either<KunneIkkeSimulereBehandling, Søknadsbehandling>
    fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, Søknadsbehandling>
    fun underkjenn(request: UnderkjennSøknadsbehandlingRequest): Either<KunneIkkeUnderkjenneBehandling, Søknadsbehandling>
    fun iverksett(request: IverksettSøknadsbehandlingRequest): Either<KunneIkkeIverksetteBehandling, Søknadsbehandling>
}

class SaksbehandlingServiceImpl(
    private val søknadService: SøknadService,
    private val søknadRepo: SøknadRepo,
    private val saksbehandlingRepo: SaksbehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val iverksettSaksbehandlingService: IverksettSaksbehandlingService,
    private val behandlingMetrics: BehandlingMetrics,
    private val beregningService: BeregningService,
) : SaksbehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    override fun opprett(request: OpprettSøknadsbehandlingRequest): Either<KunneIkkeOppretteSøknadsbehandling, Søknadsbehandling> {
        val søknad = søknadService.hentSøknad(request.søknadId).getOrElse {
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

        val opprettet = Søknadsbehandling.Opprettet(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = søknad.sakId,
            // Denne blir ikke persistert i databasen, men joines inn ved select fra sak
            saksnummer = Saksnummer(-1),
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
            fnr = søknad.søknadInnhold.personopplysninger.fnr
        )

        saksbehandlingRepo.lagre(opprettet)

        // Må hente fra db for å få joinet med saksnummer.
        return saksbehandlingRepo.hent(opprettet.id)!!.let {
            observers.forEach { observer -> observer.handle(Event.Statistikk.SøknadsbehandlingOpprettet(it)) }
            it.right()
        }
    }

    override fun vilkårsvurder(request: OppdaterSøknadsbehandlingsinformasjonRequest): Either<KunneIkkeOppdatereBehandlingsinformasjon, Søknadsbehandling> {
        val saksbehandling = saksbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeOppdatereBehandlingsinformasjon.FantIkkeBehandling.left()
        return statusovergang(
            søknadsbehandling = saksbehandling,
            statusovergang = Statusovergang.TilVilkårsvurdert(request.behandlingsinformasjon)
        ).let {
            saksbehandlingRepo.lagre(it)
            it.right()
        }
    }

    override fun beregn(request: OpprettBeregningRequest): Either<KunneIkkeBeregne, Søknadsbehandling> {
        val saksbehandling = saksbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeBeregne.FantIkkeBehandling.left()

        return statusovergang(
            søknadsbehandling = saksbehandling,
            statusovergang = Statusovergang.TilBeregnet {
                beregningService.beregn(saksbehandling, request.periode, request.fradrag)
            }
        ).let {
            saksbehandlingRepo.lagre(it)
            it.right()
        }
    }

    override fun simuler(request: OpprettSimuleringRequest): Either<KunneIkkeSimulereBehandling, Søknadsbehandling> {
        val saksbehandling = saksbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeSimulereBehandling.FantIkkeBehandling.left()
        return forsøkStatusovergang(
            søknadsbehandling = saksbehandling,
            statusovergang = Statusovergang.TilSimulert { beregning ->
                utbetalingService.simulerUtbetaling(saksbehandling.sakId, request.saksbehandler, beregning)
                    .mapLeft {
                        Statusovergang.KunneIkkeSimulereBehandling
                    }.map {
                        it.simulering
                    }
            }
        ).mapLeft {
            KunneIkkeSimulereBehandling.KunneIkkeSimulere
        }.map {
            saksbehandlingRepo.lagre(it)
            it
        }
    }

    override fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, Søknadsbehandling> {
        val søknadsbehandling = saksbehandlingRepo.hent(request.behandlingId)?.let {
            statusovergang(
                søknadsbehandling = it,
                statusovergang = Statusovergang.TilAttestering(request.saksbehandler)
            )
        } ?: return KunneIkkeSendeTilAttestering.FantIkkeBehandling.left()

        val aktørId = personService.hentAktørId(søknadsbehandling.fnr).getOrElse {
            log.error("Fant ikke aktør-id med for fødselsnummer : ${søknadsbehandling.fnr}")
            return KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()
        }
        val eksisterendeOppgaveId: OppgaveId = søknadsbehandling.oppgaveId

        val tilordnetRessurs: NavIdentBruker.Attestant? =
            saksbehandlingRepo.hentEventuellTidligereAttestering(søknadsbehandling.id)?.attestant

        val nyOppgaveId: OppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.Attestering(
                søknadsbehandling.søknad.id,
                aktørId = aktørId,
                // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
                tilordnetRessurs = tilordnetRessurs
            )
        ).getOrElse {
            log.error("Kunne ikke opprette Attesteringsoppgave. Avbryter handlingen.")
            return KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
        }

        val søknadsbehandlingMedNyOppgaveId = søknadsbehandling.nyOppgaveId(nyOppgaveId)

        saksbehandlingRepo.lagre(søknadsbehandlingMedNyOppgaveId)

        oppgaveService.lukkOppgave(eksisterendeOppgaveId).map {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.LUKKET_OPPGAVE)
        }.mapLeft {
            log.error("Klarte ikke å lukke oppgave. kall til oppgave for oppgaveId ${søknadsbehandling.oppgaveId} feilet")
        }

        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.OPPRETTET_OPPGAVE)
        return søknadsbehandlingMedNyOppgaveId.right()
    }

    override fun underkjenn(request: UnderkjennSøknadsbehandlingRequest): Either<KunneIkkeUnderkjenneBehandling, Søknadsbehandling> {
        val søknadsbehandling = saksbehandlingRepo.hent(request.behandlingId)?.let {
            statusovergang(
                søknadsbehandling = it,
                statusovergang = Statusovergang.TilUnderkjent(request.attestering)
            )
        } ?: return KunneIkkeUnderkjenneBehandling.FantIkkeBehandling.left()

        // TODO attestant kan ikke være saksbehandler
        val aktørId = personService.hentAktørId(søknadsbehandling.fnr).getOrElse {
            log.error("Fant ikke aktør-id med for fødselsnummer : ${søknadsbehandling.fnr}")
            return KunneIkkeUnderkjenneBehandling.FantIkkeAktørId.left()
        }

        val journalpostId: JournalpostId = søknadsbehandling.søknad.journalpostId
        val eksisterendeOppgaveId = søknadsbehandling.oppgaveId
        val nyOppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.Saksbehandling(
                journalpostId = journalpostId,
                søknadId = søknadsbehandling.søknad.id,
                aktørId = aktørId,
                tilordnetRessurs = søknadsbehandling.saksbehandler
            )
        ).getOrElse {
            log.error("Behandling ${søknadsbehandling.id} ble ikke underkjent. Klarte ikke opprette behandlingsoppgave")
            return@underkjenn KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave.left()
        }.also {
            behandlingMetrics.incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.OPPRETTET_OPPGAVE)
        }

        val søknadsbehandlingMedNyOppgaveId = søknadsbehandling.nyOppgaveId(nyOppgaveId)

        saksbehandlingRepo.lagre(søknadsbehandlingMedNyOppgaveId)
        log.info("Behandling ${søknadsbehandling.id} ble underkjent. Opprettet behandlingsoppgave $nyOppgaveId")

        oppgaveService.lukkOppgave(eksisterendeOppgaveId)
            .mapLeft {
                log.error("Kunne ikke lukke attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen. Dette må gjøres manuelt.")
            }.map {
                log.info("Lukket attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen")
                behandlingMetrics.incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.LUKKET_OPPGAVE)
            }
        søknadsbehandlingMedNyOppgaveId.also {
            observers.forEach { observer -> observer.handle(Event.Statistikk.SøknadsbehandlingUnderkjent(it)) }
        }

        return søknadsbehandlingMedNyOppgaveId.right()
    }

    override fun iverksett(request: IverksettSøknadsbehandlingRequest): Either<KunneIkkeIverksetteBehandling, Søknadsbehandling> {
        val søknadsbehandling = saksbehandlingRepo.hent(request.behandlingId)?.let {
            statusovergang(
                søknadsbehandling = it,
                statusovergang = Statusovergang.TilIverksatt(request.attestering)
            )
        } ?: return KunneIkkeIverksetteBehandling.FantIkkeBehandling.left()

        return iverksettSaksbehandlingService.iverksett(søknadsbehandling)
    }
}
