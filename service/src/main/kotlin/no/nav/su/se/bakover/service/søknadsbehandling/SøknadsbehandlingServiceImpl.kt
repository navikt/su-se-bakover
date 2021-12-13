package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.singleOrThrow
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInneforBehandlingsperioden
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.KunneIkkeLeggeTilFradragsgrunnlag.PeriodeMangler
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.forsøkStatusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.medFritekstTilBrev
import no.nav.su.se.bakover.domain.søknadsbehandling.statusovergang
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeBeregne
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID
import kotlin.math.abs

internal class SøknadsbehandlingServiceImpl(
    private val søknadService: SøknadService,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val behandlingMetrics: BehandlingMetrics,
    private val brevService: BrevService,
    private val clock: Clock,
    private val vedtakRepo: VedtakRepo,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val grunnlagService: GrunnlagService,
    private val sakService: SakService,
    private val avkortingsvarselRepo: AvkortingsvarselRepo,
) : SøknadsbehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    override fun opprett(request: SøknadsbehandlingService.OpprettRequest): Either<SøknadsbehandlingService.KunneIkkeOpprette, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
        val søknad = søknadService.hentSøknad(request.søknadId).getOrElse {
            return SøknadsbehandlingService.KunneIkkeOpprette.FantIkkeSøknad.left()
        }
        if (søknad is Søknad.Journalført.MedOppgave.Lukket) {
            return SøknadsbehandlingService.KunneIkkeOpprette.SøknadErLukket.left()
        }
        if (søknad !is Søknad.Journalført.MedOppgave) {
            // TODO Prøv å opprette oppgaven hvis den mangler? (systembruker blir kanskje mest riktig?)
            return SøknadsbehandlingService.KunneIkkeOpprette.SøknadManglerOppgave.left()
        }
        if (hentForSøknad(søknad.id) != null) {
            return SøknadsbehandlingService.KunneIkkeOpprette.SøknadHarAlleredeBehandling.left()
        }

        val åpneSøknadsbehandlinger =
            søknadsbehandlingRepo.hentForSak(søknad.sakId).filterNot { it.erIverksatt }.filterNot { it.erLukket }
        if (åpneSøknadsbehandlinger.isNotEmpty()) {
            return SøknadsbehandlingService.KunneIkkeOpprette.HarAlleredeÅpenSøknadsbehandling.left()
        }

        val søknadsbehandlingId = UUID.randomUUID()

        søknadsbehandlingRepo.lagreNySøknadsbehandling(
            NySøknadsbehandling(
                id = søknadsbehandlingId,
                opprettet = Tidspunkt.now(clock),
                sakId = søknad.sakId,
                søknad = søknad,
                oppgaveId = søknad.oppgaveId,
                fnr = søknad.søknadInnhold.personopplysninger.fnr,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            ),
        )

        // Må hente fra db for å få joinet med saksnummer.
        return (søknadsbehandlingRepo.hent(søknadsbehandlingId)!! as Søknadsbehandling.Vilkårsvurdert.Uavklart).let {
            observers.forEach { observer ->
                observer.handle(
                    Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingOpprettet(
                        it,
                    ),
                )
            }
            it.right()
        }
    }

    override fun vilkårsvurder(request: VilkårsvurderRequest): Either<SøknadsbehandlingService.KunneIkkeVilkårsvurdere, Søknadsbehandling.Vilkårsvurdert> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FantIkkeBehandling.left()

        val validertBehandlingsinformasjon = request.hentValidertBehandlingsinformasjon(
            søknadsbehandling.grunnlagsdata.bosituasjon.firstOrNull(),
        ).getOrHandle {
            return SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FeilVedValideringAvBehandlingsinformasjon(it).left()
        }

        return vilkårsvurder(søknadsbehandling, validertBehandlingsinformasjon)
    }

    private fun vilkårsvurder(
        søknadsbehandling: Søknadsbehandling,
        validertBehandlingsinformasjon: Behandlingsinformasjon,
    ): Either<SøknadsbehandlingService.KunneIkkeVilkårsvurdere, Søknadsbehandling.Vilkårsvurdert> {
        return statusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilVilkårsvurdert(validertBehandlingsinformasjon, clock),
        ).let { vilkårsvurdert ->
            søknadsbehandlingRepo.lagre(vilkårsvurdert)
            vilkårsvurdert.right()
        }
    }

    override fun beregn(request: SøknadsbehandlingService.BeregnRequest): Either<KunneIkkeBeregne, Søknadsbehandling.Beregnet> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeBeregne.FantIkkeBehandling.left()

        return søknadsbehandling.beregn(
            avkortingsvarsel = avkortingsvarselRepo.hentUteståendeAvkortinger(søknadsbehandling.sakId),
            begrunnelse = request.begrunnelse,
            clock = clock,
        ).getOrHandle {
            return when (it) {
                is Søknadsbehandling.KunneIkkeBeregne.UgyldigTilstand -> {
                    KunneIkkeBeregne.UgyldigTilstand(it.fra, it.til)
                }
                is Søknadsbehandling.KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag -> {
                    KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag(it.feil.toService())
                }
            }.left()
        }.let {
            grunnlagService.lagreFradragsgrunnlag(
                behandlingId = it.id,
                fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
            )
            søknadsbehandlingRepo.lagre(it)
            it.right()
        }
    }

    override fun simuler(request: SøknadsbehandlingService.SimulerRequest): Either<SøknadsbehandlingService.KunneIkkeSimulereBehandling, Søknadsbehandling.Simulert> {
        val saksbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeSimulereBehandling.FantIkkeBehandling.left()
        return forsøkStatusovergang(
            søknadsbehandling = saksbehandling,
            statusovergang = Statusovergang.TilSimulert { beregning ->
                utbetalingService.simulerUtbetaling(
                    saksbehandling.sakId,
                    request.saksbehandler,
                    beregning,
                    saksbehandling.vilkårsvurderinger.uføre.grunnlag,
                )
                    .map {
                        it.simulering
                    }
            },
        ).mapLeft {
            SøknadsbehandlingService.KunneIkkeSimulereBehandling.KunneIkkeSimulere(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun sendTilAttestering(request: SøknadsbehandlingService.SendTilAttesteringRequest): Either<SøknadsbehandlingService.KunneIkkeSendeTilAttestering, Søknadsbehandling.TilAttestering> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)?.let {
            statusovergang(
                søknadsbehandling = it,
                statusovergang = Statusovergang.TilAttestering(request.saksbehandler, request.fritekstTilBrev),
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
            OppgaveConfig.AttesterSøknadsbehandling(
                søknadId = søknadsbehandling.søknad.id,
                aktørId = aktørId,
                tilordnetRessurs = tilordnetRessurs,
            ),
        ).getOrElse {
            log.error("Kunne ikke opprette Attesteringsoppgave. Avbryter handlingen.")
            return SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
        }

        val søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev = søknadsbehandling
            .nyOppgaveId(nyOppgaveId)
            .medFritekstTilBrev(request.fritekstTilBrev)

        søknadsbehandlingRepo.lagre(søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev)

        oppgaveService.lukkOppgave(eksisterendeOppgaveId).map {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.LUKKET_OPPGAVE)
        }.mapLeft {
            log.error("Klarte ikke å lukke oppgave. kall til oppgave for oppgaveId ${søknadsbehandling.oppgaveId} feilet")
        }
        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.PERSISTERT)
        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.OPPRETTET_OPPGAVE)
        return søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev.let {
            observers.forEach { observer ->
                observer.handle(
                    Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingTilAttestering(
                        it,
                    ),
                )
            }
            it.right()
        }
    }

    override fun underkjenn(request: SøknadsbehandlingService.UnderkjennRequest): Either<SøknadsbehandlingService.KunneIkkeUnderkjenne, Søknadsbehandling.Underkjent> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeUnderkjenne.FantIkkeBehandling.left()

        return forsøkStatusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilUnderkjent(request.attestering),
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
                OppgaveConfig.Søknad(
                    journalpostId = journalpostId,
                    søknadId = underkjent.søknad.id,
                    aktørId = aktørId,
                    tilordnetRessurs = underkjent.saksbehandler,
                ),
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
                            it,
                        ),
                    )
                }
            }
            søknadsbehandlingMedNyOppgaveId
        }
    }

    override fun iverksett(request: SøknadsbehandlingService.IverksettRequest): Either<KunneIkkeIverksette, Søknadsbehandling.Iverksatt> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeIverksette.FantIkkeBehandling.left()

        var utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering? = null
        return forsøkStatusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilIverksatt(
                attestering = request.attestering,
            ) { tilAttestering ->

                // TODO finn en bedre måte å håndtrere dette på
                avkortingsvarselRepo.hentUteståendeAvkortinger(tilAttestering.sakId).let {
                    val beløpSkalAvkortes = it.sumOf { it.hentUtbetalteBeløp().sumOf { it.second } }
                    val fradragAvkorting = tilAttestering.beregning.getFradrag()
                        .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                        .sumOf { it.månedsbeløp }
                        .toInt()

                    check(abs(beløpSkalAvkortes) == abs(fradragAvkorting)) { "Beløp for avkorting og fradrag stemmer ikke overens!" }

                    it.forEach { avkortingsvarsel ->
                        avkortingsvarselRepo.lagre(avkortingsvarsel.avkortet(tilAttestering.id))
                    }
                }

                utbetalingService.utbetal(
                    sakId = tilAttestering.sakId,
                    attestant = request.attestering.attestant,
                    beregning = tilAttestering.beregning,
                    simulering = tilAttestering.simulering,
                    uføregrunnlag = tilAttestering.vilkårsvurderinger.uføre.grunnlag,
                ).mapLeft { kunneIkkeUtbetale ->
                    log.error("Kunne ikke innvilge behandling ${søknadsbehandling.id} siden utbetaling feilet. Feiltype: $kunneIkkeUtbetale")
                    KunneIkkeIverksette.KunneIkkeUtbetale(kunneIkkeUtbetale)
                }.map { utbetalingUtenKvittering ->
                    // Dersom vi skal unngå denne hacken må Iverksatt.Innvilget innholde denne istedenfor kun IDen
                    utbetaling = utbetalingUtenKvittering
                    utbetalingUtenKvittering.id
                }
            },
        ).map { iverksattBehandling ->
            when (iverksattBehandling) {
                is Søknadsbehandling.Iverksatt.Innvilget -> {
                    søknadsbehandlingRepo.lagre(iverksattBehandling)
                    val vedtak = Vedtak.fromSøknadsbehandling(iverksattBehandling, utbetaling!!.id, clock)
                    vedtakRepo.lagre(vedtak)

                    log.info("Iverksatt innvilgelse for behandling ${iverksattBehandling.id}, vedtak: ${vedtak.id}")

                    behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)

                    iverksattBehandling.also {
                        observers.forEach { observer ->
                            observer.handle(
                                Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(
                                    iverksattBehandling,
                                ),
                            )
                            observer.handle((Event.Statistikk.Vedtaksstatistikk(vedtak)))
                        }
                    }
                }
                is Søknadsbehandling.Iverksatt.Avslag -> {
                    val vedtak = opprettAvslagsvedtak(iverksattBehandling)

                    val dokument = brevService.lagDokument(vedtak)
                        .getOrHandle { return KunneIkkeIverksette.KunneIkkeGenerereVedtaksbrev.left() }
                        .leggTilMetadata(
                            Dokument.Metadata(
                                sakId = vedtak.behandling.sakId,
                                søknadId = null,
                                vedtakId = vedtak.id,
                                revurderingId = null,
                                bestillBrev = true,
                            ),
                        )

                    // TODO jm: skriker etter en transaksjon
                    // TODO jm: sjekk om vi allerede har distribuert?
                    søknadsbehandlingRepo.lagre(iverksattBehandling)
                    vedtakRepo.lagre(vedtak)
                    brevService.lagreDokument(dokument)

                    log.info("Iverksatt avslag for behandling: ${iverksattBehandling.id}, vedtak: ${vedtak.id}")

                    behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)

                    if (vedtak.behandling is BehandlingMedOppgave) {
                        ferdigstillVedtakService.lukkOppgaveMedBruker(vedtak)
                            .mapLeft {
                                log.error("Lukking av oppgave for behandlingId: ${(vedtak.behandling as BehandlingMedOppgave).oppgaveId} feilet. Må ryddes opp manuelt.")
                            }
                    }

                    iverksattBehandling.also {
                        observers.forEach { observer ->
                            observer.handle(
                                Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(
                                    iverksattBehandling,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun opprettAvslagsvedtak(iverksattBehandling: Søknadsbehandling.Iverksatt.Avslag): Vedtak.Avslag =
        when (iverksattBehandling) {
            is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
                Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(
                    avslag = iverksattBehandling,
                    clock = clock,
                )
            }
            is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
                Vedtak.Avslag.fromSøknadsbehandlingUtenBeregning(
                    avslag = iverksattBehandling,
                    clock = clock,
                )
            }
        }

    override fun brev(request: SøknadsbehandlingService.BrevRequest): Either<SøknadsbehandlingService.KunneIkkeLageBrev, ByteArray> {
        val behandling = when (request) {
            is SøknadsbehandlingService.BrevRequest.MedFritekst ->
                request.behandling.medFritekstTilBrev(request.fritekst)
            is SøknadsbehandlingService.BrevRequest.UtenFritekst ->
                request.behandling
        }

        return brevService.lagDokument(behandling)
            .mapLeft {
                when (it) {
                    KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling -> SøknadsbehandlingService.KunneIkkeLageBrev.KunneIkkeFinneGjeldendeUtbetaling
                    KunneIkkeLageDokument.KunneIkkeGenererePDF -> SøknadsbehandlingService.KunneIkkeLageBrev.KunneIkkeLagePDF
                    KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> SøknadsbehandlingService.KunneIkkeLageBrev.FikkIkkeHentetSaksbehandlerEllerAttestant
                    KunneIkkeLageDokument.KunneIkkeHentePerson -> SøknadsbehandlingService.KunneIkkeLageBrev.FantIkkePerson
                }
            }
            .map {
                it.generertDokument
            }
    }

    override fun hent(request: SøknadsbehandlingService.HentRequest): Either<SøknadsbehandlingService.FantIkkeBehandling, Søknadsbehandling> {
        return søknadsbehandlingRepo.hent(request.behandlingId)?.right()
            ?: SøknadsbehandlingService.FantIkkeBehandling.left()
    }

    override fun hentForSøknad(søknadId: UUID): Søknadsbehandling? {
        return søknadsbehandlingRepo.hentForSøknad(søknadId)
    }

    override fun oppdaterStønadsperiode(request: SøknadsbehandlingService.OppdaterStønadsperiodeRequest): Either<SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling.left()

        val sak = sakService.hentSak(søknadsbehandling.sakId)
            .getOrHandle { return SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeSak.left() }

        return forsøkStatusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.OppdaterStønadsperiode(
                oppdatertStønadsperiode = request.stønadsperiode,
                sak = sak,
                clock = clock,
            ),
        ).mapLeft {
            SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereStønadsperiode(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            grunnlagService.lagreBosituasjongrunnlag(
                behandlingId = it.id,
                bosituasjongrunnlag = it.grunnlagsdata.bosituasjon,
            )
            grunnlagService.lagreFradragsgrunnlag(
                behandlingId = it.id,
                fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
            )
            it
        }
    }

    override fun leggTilUførevilkår(
        request: LeggTilUførevilkårRequest,
    ): Either<KunneIkkeLeggeTilUføreVilkår, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling.left()

        val vilkår = request.toVilkår(clock).getOrHandle {
            return when (it) {
                LeggTilUførevilkårRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> {
                    KunneIkkeLeggeTilUføreVilkår.UføregradOgForventetInntektMangler
                }
                LeggTilUførevilkårRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                    KunneIkkeLeggeTilUføreVilkår.PeriodeForGrunnlagOgVurderingErForskjellig
                }
                LeggTilUførevilkårRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> {
                    KunneIkkeLeggeTilUføreVilkår.OverlappendeVurderingsperioder
                }
                LeggTilUførevilkårRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> {
                    KunneIkkeLeggeTilUføreVilkår.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden
                }
            }.left()
        }

        val vilkårsvurdert = søknadsbehandling.leggTilUførevilkår(vilkår, clock)
            .getOrHandle {
                return when (it) {
                    is Søknadsbehandling.KunneIkkeLeggeTilUførevilkår.UgyldigTilstand -> {
                        KunneIkkeLeggeTilUføreVilkår.UgyldigTilstand(fra = it.fra, til = it.til)
                    }
                    Søknadsbehandling.KunneIkkeLeggeTilUførevilkår.VurderingsperiodeUtenforBehandlingsperiode -> {
                        KunneIkkeLeggeTilUføreVilkår.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden
                    }
                }.left()
            }

        søknadsbehandlingRepo.lagre(vilkårsvurdert)
        return vilkårsvurdert.right()
    }

    override fun leggTilBosituasjonEpsgrunnlag(request: LeggTilBosituasjonEpsRequest): Either<KunneIkkeLeggeTilBosituasjonEpsGrunnlag, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling.left()

        val bosituasjon = request.toBosituasjon(søknadsbehandling.periode, clock) {
            personService.hentPerson(it).fold(
                { error ->
                    if (error is KunneIkkeHentePerson.IkkeTilgangTilPerson) {
                        true.right()
                    } else {
                        KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl.left()
                    }
                },
                {
                    true.right()
                },
            )
        }.getOrHandle {
            return it.left()
        }

        return søknadsbehandling.oppdaterBosituasjon(bosituasjon, clock).mapLeft {
            KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KunneIkkeOppdatereBosituasjon(it)
        }.map {
            // TODO jah: Legg til Søknadsbehandling.leggTilBosituasjonEpsgrunnlag(...) som for Revurdering og persister Søknadsbehandlingen som returnerers. Da slipper man og det ekstra hent(...) kallet.
            grunnlagService.lagreBosituasjongrunnlag(behandlingId = request.behandlingId, listOf(bosituasjon))
            grunnlagService.lagreFradragsgrunnlag(it.id, it.grunnlagsdata.fradragsgrunnlag)
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun fullførBosituasjongrunnlag(request: FullførBosituasjonRequest): Either<KunneIkkeFullføreBosituasjonGrunnlag, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling.left()

        val bosituasjon =
            request.toBosituasjon(søknadsbehandling.grunnlagsdata.bosituasjon.singleOrThrow(), clock).getOrHandle {
                return KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeLagreBosituasjon.left()
            }

        return vilkårsvurder(
            VilkårsvurderRequest(
                behandlingId = søknadsbehandling.id,
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon.copy(
                    formue = søknadsbehandling.behandlingsinformasjon.formue?.nullstillEpsFormueHvisIngenEps(bosituasjon),
                ),
            ),
        ).mapLeft {
            return KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling.left()
        }.map {
            // TODO jah: Legg til Søknadsbehandling.fullførBosituasjongrunnlag(...) som for Revurdering og persister Søknadsbehandlingen som returnerers. Da slipper man og det ekstra hent(...) kallet.
            grunnlagService.lagreBosituasjongrunnlag(behandlingId = request.behandlingId, listOf(bosituasjon))
            grunnlagService.lagreFradragsgrunnlag(it.id, it.grunnlagsdata.fradragsgrunnlag)
            return when (it) {
                is Søknadsbehandling.Vilkårsvurdert.Avslag -> it.copy(
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        bosituasjon = listOf(bosituasjon),
                        fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
                    ).getOrHandle {
                        return KunneIkkeFullføreBosituasjonGrunnlag.KunneIkkeEndreBosituasjongrunnlag(it).left()
                    },
                )
                is Søknadsbehandling.Vilkårsvurdert.Innvilget -> it.copy(
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        bosituasjon = listOf(bosituasjon),
                        fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
                    ).getOrHandle {
                        return KunneIkkeFullføreBosituasjonGrunnlag.KunneIkkeEndreBosituasjongrunnlag(it).left()
                    },
                )
                is Søknadsbehandling.Vilkårsvurdert.Uavklart -> it.copy(
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        bosituasjon = listOf(bosituasjon),
                        fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
                    ).getOrHandle {
                        return KunneIkkeFullføreBosituasjonGrunnlag.KunneIkkeEndreBosituasjongrunnlag(it).left()
                    },
                )
            }.right()
        }
    }

    override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling> {
        val behandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        /**
         *  I flere av funksjonene i denne fila bruker vi [Statusovergang] og [no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor] for å bestemme om det er en gyldig statusovergang, men i dette tilfellet bruker vi domenemodellen sin funksjon leggTilFradragsgrunnlag til dette.
         * Vi ønsker gradvis å gå over til sistenevnte måte å gjøre det på.
         */
        val oppdatertBehandling = behandling.leggTilFradragsgrunnlag(request.fradragsgrunnlag).getOrHandle {
            return it.toService().left()
        }

        grunnlagService.lagreFradragsgrunnlag(behandling.id, request.fradragsgrunnlag)
        søknadsbehandlingRepo.lagre(oppdatertBehandling)

        return oppdatertBehandling.right()
    }

    private fun Søknadsbehandling.KunneIkkeLeggeTilFradragsgrunnlag.toService(): KunneIkkeLeggeTilFradragsgrunnlag {
        return when (this) {
            GrunnlagetMåVæreInneforBehandlingsperioden -> {
                KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden
            }
            is IkkeLovÅLeggeTilFradragIDenneStatusen -> {
                KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                    fra = this.status,
                    til = Søknadsbehandling.Vilkårsvurdert.Innvilget::class,
                )
            }
            is Søknadsbehandling.KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag -> {
                KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(this.feil)
            }
            PeriodeMangler -> {
                KunneIkkeLeggeTilFradragsgrunnlag.PeriodeMangler
            }
        }
    }

    override fun lukk(lukketSøknadbehandling: LukketSøknadsbehandling, tx: TransactionContext) {
        søknadsbehandlingRepo.lagre(lukketSøknadbehandling, tx)
    }

    override fun lagre(avslag: AvslagManglendeDokumentasjon, tx: TransactionContext) {
        return søknadsbehandlingRepo.lagreAvslagManglendeDokumentasjon(avslag, tx)
    }

    override fun leggTilUtenlandsopphold(request: LeggTilUtenlandsoppholdRequest): Either<SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold, Søknadsbehandling.Vilkårsvurdert> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left()

        val vilkår = UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                request.tilVurderingsperiode(clock = clock).getOrHandle {
                    when (it) {
                        LeggTilUtenlandsoppholdRequest.UgyldigUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                            throw IllegalStateException("$it Skal ikke kunne forekomme for søknadsbehandling")
                        }
                    }
                },
            ),
        ).getOrHandle {
            when (it) {
                UtenlandsoppholdVilkår.Vurdert.UgyldigUtenlandsoppholdVilkår.OverlappendeVurderingsperioder -> {
                    throw IllegalStateException("$it Skal ikke kunne forekomme for søknadsbehandling")
                }
            }
        }

        val vilkårsvurdert = søknadsbehandling.leggTilUtenlandsopphold(vilkår, clock)
            .getOrHandle {
                return it.tilService().left()
            }

        søknadsbehandlingRepo.lagre(vilkårsvurdert)
        return vilkårsvurdert.right()
    }

    private fun Søknadsbehandling.KunneIkkeLeggeTilUtenlandsopphold.tilService(): SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold {
        return when (this) {
            is Søknadsbehandling.KunneIkkeLeggeTilUtenlandsopphold.IkkeLovÅLeggeTilUtenlandsoppholdIDenneStatusen -> {
                SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(
                    fra = this.fra,
                    til = this.til,
                )
            }
            Søknadsbehandling.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode -> {
                SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode
            }
            Søknadsbehandling.KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat -> {
                SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat
            }
            Søknadsbehandling.KunneIkkeLeggeTilUtenlandsopphold.MåInneholdeKunEnVurderingsperiode -> {
                SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.MåInneholdeKunEnVurderingsperiode
            }
            Søknadsbehandling.KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden -> {
                SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden
            }
        }
    }
}
