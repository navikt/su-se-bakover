package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.forsøkStatusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.statusovergang
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
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
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadsbehandlingServiceImpl(
    private val søknadService: SøknadService,
    private val søknadRepo: SøknadRepo,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val iverksettSøknadsbehandlingService: IverksettSøknadsbehandlingService,
    private val behandlingMetrics: BehandlingMetrics,
    private val beregningService: BeregningService,
    private val opprettVedtakssnapshotService: OpprettVedtakssnapshotService,
) : SøknadsbehandlingService {

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

        val opprettet = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = søknad.sakId,
            // Denne blir ikke persistert i databasen, men joines inn ved select fra sak
            saksnummer = Saksnummer(-1),
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
            fnr = søknad.søknadInnhold.personopplysninger.fnr,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
        )

        søknadsbehandlingRepo.lagre(opprettet)

        // Må hente fra db for å få joinet med saksnummer.
        return søknadsbehandlingRepo.hent(opprettet.id)!!.let {
            observers.forEach { observer -> observer.handle(Event.Statistikk.SøknadsbehandlingOpprettet(it)) }
            it.right()
        }
    }

    override fun vilkårsvurder(request: OppdaterSøknadsbehandlingsinformasjonRequest): Either<KunneIkkeOppdatereBehandlingsinformasjon, Søknadsbehandling> {
        val saksbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeOppdatereBehandlingsinformasjon.FantIkkeBehandling.left()
        return statusovergang(
            søknadsbehandling = saksbehandling,
            statusovergang = Statusovergang.TilVilkårsvurdert(request.behandlingsinformasjon)
        ).let {
            søknadsbehandlingRepo.lagre(it)
            it.right()
        }
    }

    override fun beregn(request: OpprettBeregningRequest): Either<KunneIkkeBeregne, Søknadsbehandling> {
        val saksbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeBeregne.FantIkkeBehandling.left()

        return statusovergang(
            søknadsbehandling = saksbehandling,
            statusovergang = Statusovergang.TilBeregnet {
                beregningService.beregn(saksbehandling, request.periode, request.fradrag)
            }
        ).let {
            søknadsbehandlingRepo.lagre(it)
            it.right()
        }
    }

    override fun simuler(request: OpprettSimuleringRequest): Either<KunneIkkeSimulereBehandling, Søknadsbehandling> {
        val saksbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
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
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)?.let {
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
            søknadsbehandlingRepo.hentEventuellTidligereAttestering(søknadsbehandling.id)?.attestant

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

        søknadsbehandlingRepo.lagre(søknadsbehandlingMedNyOppgaveId)

        oppgaveService.lukkOppgave(eksisterendeOppgaveId).map {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.LUKKET_OPPGAVE)
        }.mapLeft {
            log.error("Klarte ikke å lukke oppgave. kall til oppgave for oppgaveId ${søknadsbehandling.oppgaveId} feilet")
        }
        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.PERSISTERT)
        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.OPPRETTET_OPPGAVE)
        return søknadsbehandlingMedNyOppgaveId.let {
            observers.forEach { observer -> observer.handle(Event.Statistikk.SøknadsbehandlingTilAttestering(it)) }
            it.right()
        }
    }

    override fun underkjenn(request: UnderkjennSøknadsbehandlingRequest): Either<KunneIkkeUnderkjenneBehandling, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeUnderkjenneBehandling.FantIkkeBehandling.left()

        return forsøkStatusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilUnderkjent(request.attestering)
        ).mapLeft {
            KunneIkkeUnderkjenneBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
        }.map { underkjent ->
            val aktørId = personService.hentAktørId(underkjent.fnr).getOrElse {
                log.error("Fant ikke aktør-id for sak: ${underkjent.id}")
                return KunneIkkeUnderkjenneBehandling.FantIkkeAktørId.left()
            }

            val journalpostId: JournalpostId = underkjent.søknad.journalpostId
            val eksisterendeOppgaveId = underkjent.oppgaveId
            val nyOppgaveId = oppgaveService.opprettOppgave(
                OppgaveConfig.Saksbehandling(
                    journalpostId = journalpostId,
                    søknadId = underkjent.søknad.id,
                    aktørId = aktørId,
                    tilordnetRessurs = underkjent.saksbehandler
                )
            ).getOrElse {
                log.error("Behandling ${underkjent.id} ble ikke underkjent. Klarte ikke opprette behandlingsoppgave")
                return@underkjenn KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave.left()
            }.also {
                behandlingMetrics.incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.OPPRETTET_OPPGAVE)
            }

            val søknadsbehandlingMedNyOppgaveId = underkjent.nyOppgaveId(nyOppgaveId)

            søknadsbehandlingRepo.lagre(søknadsbehandlingMedNyOppgaveId)

            behandlingMetrics.incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.PERSISTERT)
            log.info("Behandling ${underkjent.id} ble underkjent. Opprettet behandlingsoppgave $nyOppgaveId")

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

            søknadsbehandlingMedNyOppgaveId
        }
    }

    override fun iverksett(request: IverksettSøknadsbehandlingRequest): Either<KunneIkkeIverksetteBehandling, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeIverksetteBehandling.FantIkkeBehandling.left()

        var utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering? = null
        return forsøkStatusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilIverksatt(
                attestering = request.attestering,
                innvilget = {
                    utbetalingService.utbetal(
                        sakId = it.sakId,
                        attestant = request.attestering.attestant,
                        beregning = it.beregning,
                        simulering = it.simulering
                    ).mapLeft {
                        log.error("Kunne ikke innvilge behandling ${søknadsbehandling.id} siden utbetaling feilet. Feiltype: $it")
                        when (it) {
                            KunneIkkeUtbetale.KunneIkkeSimulere -> Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.KunneIkkeKontrollsimulere
                            KunneIkkeUtbetale.Protokollfeil -> Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.TekniskFeil
                            KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                        }
                    }.map {
                        // Dersom vi skal unngå denne hacken må Iverksatt.Innvilget innholde denne istedenfor kun IDen
                        utbetaling = it
                        it.id
                    }
                },
                avslag = {
                    iverksettSøknadsbehandlingService.opprettJournalpostForAvslag(
                        it,
                        request.attestering.attestant
                    )
                }
            )
        ).mapLeft {
            IverksettStatusovergangFeilMapper.map(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            when (it) {
                is Søknadsbehandling.Iverksatt.Innvilget -> {
                    log.info("Iverksatt innvilgelse for behandling ${it.id}")
                    opprettVedtakssnapshotService.opprettVedtak(
                        vedtakssnapshot = Vedtakssnapshot.Innvilgelse.createFromBehandling(it, utbetaling!!)
                    )
                    behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)
                    it
                }
                is Søknadsbehandling.Iverksatt.Avslag -> {
                    log.info("Iverksatt avslag for behandling ${it.id}")
                    opprettVedtakssnapshotService.opprettVedtak(
                        vedtakssnapshot = Vedtakssnapshot.Avslag.createFromBehandling(it, it.avslagsgrunner)
                    )
                    behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)
                    iverksettSøknadsbehandlingService.distribuerBrevOgLukkOppgaveForAvslag(it)
                        .let { medPotensiellBrevbestillingId ->
                            søknadsbehandlingRepo.lagre(medPotensiellBrevbestillingId)
                            medPotensiellBrevbestillingId
                        }
                }
            }
        }
    }

    internal object IverksettStatusovergangFeilMapper {
        fun map(feil: Statusovergang.KunneIkkeIverksetteSøknadsbehandling) = when (feil) {
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre -> KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.KunneIkkeKontrollsimulere -> KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.TekniskFeil -> KunneIkkeIverksetteBehandling.KunneIkkeUtbetale
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.SaksbehandlerOgAttestantKanIkkeVæreSammePerson -> KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.FantIkkePerson -> KunneIkkeIverksetteBehandling.FantIkkePerson
        }
    }
}
