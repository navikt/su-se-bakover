package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
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
import no.nav.su.se.bakover.domain.søknadsbehandling.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.forsøkStatusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.statusovergang
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.brev.BrevService
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
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val brevService: BrevService,
    private val opprettVedtakssnapshotService: OpprettVedtakssnapshotService,
) : SøknadsbehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    override fun opprett(request: SøknadsbehandlingService.OpprettRequest): Either<SøknadsbehandlingService.KunneIkkeOpprette, Søknadsbehandling> {
        val søknad = søknadService.hentSøknad(request.søknadId).getOrElse {
            return SøknadsbehandlingService.KunneIkkeOpprette.FantIkkeSøknad.left()
        }
        if (søknad is Søknad.Lukket) {
            return SøknadsbehandlingService.KunneIkkeOpprette.SøknadErLukket.left()
        }
        if (søknad !is Søknad.Journalført.MedOppgave) {
            // TODO Prøv å opprette oppgaven hvis den mangler? (systembruker blir kanskje mest riktig?)
            return SøknadsbehandlingService.KunneIkkeOpprette.SøknadManglerOppgave.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknad.id)) {
            // Dersom man legger til avslutting av behandlinger, må denne spørringa spesifiseres.
            return SøknadsbehandlingService.KunneIkkeOpprette.SøknadHarAlleredeBehandling.left()
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
            observers.forEach { observer ->
                observer.handle(
                    Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingOpprettet(
                        it as Søknadsbehandling.Vilkårsvurdert.Uavklart
                    )
                )
            }
            it.right()
        }
    }

    override fun vilkårsvurder(request: SøknadsbehandlingService.VilkårsvurderRequest): Either<SøknadsbehandlingService.KunneIkkeVilkårsvurdere, Søknadsbehandling> {
        val saksbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FantIkkeBehandling.left()
        return statusovergang(
            søknadsbehandling = saksbehandling,
            statusovergang = Statusovergang.TilVilkårsvurdert(request.behandlingsinformasjon)
        ).let {
            søknadsbehandlingRepo.lagre(it)
            it.right()
        }
    }

    override fun beregn(request: SøknadsbehandlingService.BeregnRequest): Either<SøknadsbehandlingService.KunneIkkeBeregne, Søknadsbehandling> {
        val saksbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeBeregne.FantIkkeBehandling.left()

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

    override fun simuler(request: SøknadsbehandlingService.SimulerRequest): Either<SøknadsbehandlingService.KunneIkkeSimulereBehandling, Søknadsbehandling> {
        val saksbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeSimulereBehandling.FantIkkeBehandling.left()
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
            SøknadsbehandlingService.KunneIkkeSimulereBehandling.KunneIkkeSimulere
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun sendTilAttestering(request: SøknadsbehandlingService.SendTilAttesteringRequest): Either<SøknadsbehandlingService.KunneIkkeSendeTilAttestering, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)?.let {
            statusovergang(
                søknadsbehandling = it,
                statusovergang = Statusovergang.TilAttestering(request.saksbehandler)
            )
        } ?: return SøknadsbehandlingService.KunneIkkeSendeTilAttestering.FantIkkeBehandling.left()

        val aktørId = personService.hentAktørId(søknadsbehandling.fnr).getOrElse {
            log.error("Fant ikke aktør-id med for fødselsnummer : ${søknadsbehandling.fnr}")
            return SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()
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
            return SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
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
            observers.forEach { observer ->
                observer.handle(
                    Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingTilAttestering(
                        it
                    )
                )
            }
            it.right()
        }
    }

    override fun underkjenn(request: SøknadsbehandlingService.UnderkjennRequest): Either<SøknadsbehandlingService.KunneIkkeUnderkjenne, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeUnderkjenne.FantIkkeBehandling.left()

        return forsøkStatusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilUnderkjent(request.attestering)
        ).mapLeft {
            SøknadsbehandlingService.KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
        }.map { underkjent ->
            val aktørId = personService.hentAktørId(underkjent.fnr).getOrElse {
                log.error("Fant ikke aktør-id for sak: ${underkjent.id}")
                return SøknadsbehandlingService.KunneIkkeUnderkjenne.FantIkkeAktørId.left()
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
                return@underkjenn SøknadsbehandlingService.KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave.left()
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
                observers.forEach { observer ->
                    observer.handle(
                        Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingUnderkjent(
                            it
                        )
                    )
                }
            }

            søknadsbehandlingMedNyOppgaveId
        }
    }

    override fun iverksett(request: SøknadsbehandlingService.IverksettRequest): Either<SøknadsbehandlingService.KunneIkkeIverksette, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeIverksette.FantIkkeBehandling.left()

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
            // TODO jah, jm: Flytt inn i FerdigstillIverksetting...
            observers.forEach { observer ->
                observer.handle(
                    Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(
                        it
                    )
                )
            }
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
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre -> SøknadsbehandlingService.KunneIkkeIverksette.KunneIkkeJournalføreBrev
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.KunneIkkeKontrollsimulere -> SøknadsbehandlingService.KunneIkkeIverksette.KunneIkkeKontrollsimulere
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> SøknadsbehandlingService.KunneIkkeIverksette.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.TekniskFeil -> SøknadsbehandlingService.KunneIkkeIverksette.KunneIkkeUtbetale
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.SaksbehandlerOgAttestantKanIkkeVæreSammePerson -> SøknadsbehandlingService.KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.FantIkkePerson -> SøknadsbehandlingService.KunneIkkeIverksette.FantIkkePerson
        }
    }

    override fun brev(request: SøknadsbehandlingService.BrevRequest): Either<SøknadsbehandlingService.KunneIkkeLageBrev, ByteArray> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeLageBrev.FantIkkeBehandling.left()

        val visitor = LagBrevRequestVisitor(
            hentPerson = { fnr ->
                personService.hentPerson(fnr)
                    .mapLeft { LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHentePerson }
            },
            hentNavn = { ident ->
                hentNavnForNavIdent(ident)
                    .mapLeft { LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant }
            }
        ).apply { søknadsbehandling.accept(this) }

        val brevRequest = visitor.brevRequest.getOrHandle {
            return when (it) {
                LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> {
                    SøknadsbehandlingService.KunneIkkeLageBrev.FikkIkkeHentetSaksbehandlerEllerAttestant.left()
                }
                LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHentePerson -> {
                    SøknadsbehandlingService.KunneIkkeLageBrev.FantIkkePerson.left()
                }
                is LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeLageBrevForStatus -> {
                    SøknadsbehandlingService.KunneIkkeLageBrev.KanIkkeLageBrevutkastForStatus(it.status).left()
                }
            }
        }

        return brevService.lagBrev(brevRequest)
            .mapLeft { SøknadsbehandlingService.KunneIkkeLageBrev.KunneIkkeLagePDF }
    }

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<MicrosoftGraphApiOppslagFeil, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .map { it.displayName }
    }

    override fun hent(request: SøknadsbehandlingService.HentRequest): Either<SøknadsbehandlingService.FantIkkeBehandling, Søknadsbehandling> {
        return søknadsbehandlingRepo.hent(request.behandlingId)?.right()
            ?: SøknadsbehandlingService.FantIkkeBehandling.left()
    }
}
