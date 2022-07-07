package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.singleOrThrow
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.forsøkStatusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.medFritekstTilBrev
import no.nav.su.se.bakover.domain.søknadsbehandling.statusovergang
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.service.revurdering.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.BeregnRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.BrevRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.FantIkkeBehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.HentRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.IverksettRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeBeregne
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService.FantIkkeBehandling.tilKunneIkkeLeggeTilFamiliegjenforeningVilkårService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeOpprette
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeVilkårsvurdere
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.OppdaterStønadsperiodeRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.OpprettRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.SendTilAttesteringRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.SimulerRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.UnderkjennRequest
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggetilLovligOppholdVilkår
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

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
    private val sakService: SakService,
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val sessionFactory: SessionFactory,
    private val avkortingsvarselRepo: AvkortingsvarselRepo,
    private val tilbakekrevingService: TilbakekrevingService,
    private val formuegrenserFactory: FormuegrenserFactory,
    private val satsFactory: SatsFactory,
) : SøknadsbehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    override fun opprett(request: OpprettRequest): Either<KunneIkkeOpprette, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
        val søknad = søknadService.hentSøknad(request.søknadId).getOrElse {
            return KunneIkkeOpprette.FantIkkeSøknad.left()
        }
        if (søknad is Søknad.Journalført.MedOppgave.Lukket) {
            return KunneIkkeOpprette.SøknadErLukket.left()
        }
        if (søknad !is Søknad.Journalført.MedOppgave) {
            // TODO Prøv å opprette oppgaven hvis den mangler? (systembruker blir kanskje mest riktig?)
            return KunneIkkeOpprette.SøknadManglerOppgave.left()
        }
        if (hentForSøknad(søknad.id) != null) {
            return KunneIkkeOpprette.SøknadHarAlleredeBehandling.left()
        }

        val åpneSøknadsbehandlinger = søknadsbehandlingRepo.hentForSak(søknad.sakId)
            .filterNot { it.erIverksatt }
            .filterNot { it.erLukket }

        if (åpneSøknadsbehandlinger.isNotEmpty()) {
            return KunneIkkeOpprette.HarAlleredeÅpenSøknadsbehandling.left()
        }

        val søknadsbehandlingId = UUID.randomUUID()

        val avkorting = hentUteståendeAvkorting(søknad.sakId)

        søknadsbehandlingRepo.lagreNySøknadsbehandling(
            NySøknadsbehandling(
                id = søknadsbehandlingId,
                opprettet = Tidspunkt.now(clock),
                sakId = søknad.sakId,
                søknad = søknad,
                oppgaveId = søknad.oppgaveId,
                fnr = søknad.søknadInnhold.personopplysninger.fnr,
                avkorting = avkorting.kanIkke(),
                sakstype = søknad.type,
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

    private fun hentUteståendeAvkorting(sakId: UUID): AvkortingVedSøknadsbehandling.Uhåndtert {
        return when (val utestående = avkortingsvarselRepo.hentUtestående(sakId)) {
            Avkortingsvarsel.Ingen -> {
                AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
            }
            is Avkortingsvarsel.Utenlandsopphold.Annullert -> {
                AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
            }
            is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
                AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
            }
            is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
                AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
            }
            is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
                AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(utestående)
            }
        }
    }

    /**
     * Vilkårsvurderer behandlingsinformasjon og tilhørende vilkår på søknadsbehandling.
     * Behandlingsinformasjon brukes fremdeles i route/database-lagene, men er tenkt migrert/sanert helt til Vilkårsvurderinger.
     */
    override fun vilkårsvurder(request: VilkårsvurderRequest): Either<KunneIkkeVilkårsvurdere, Søknadsbehandling.Vilkårsvurdert> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeVilkårsvurdere.FantIkkeBehandling.left()

        return søknadsbehandling.leggTilVilkårFraBehandlingsinformasjon(
            behandlingsinformasjon = request.behandlingsinformasjon,
            clock = clock,
        ).mapLeft {
            throw IllegalStateException(it.toString())
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, Søknadsbehandling.Beregnet> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeBeregne.FantIkkeBehandling.left()

        return søknadsbehandling.beregn(
            begrunnelse = request.begrunnelse,
            clock = clock,
            satsFactory = satsFactory,
        ).mapLeft { feil ->
            when (feil) {
                no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeBeregne.AvkortingErUfullstendig -> {
                    KunneIkkeBeregne.AvkortingErUfullstendig
                }
                is no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeBeregne.UgyldigTilstand -> {
                    KunneIkkeBeregne.UgyldigTilstand(feil.fra, feil.til)
                }
                is no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag -> {
                    KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag(feil.feil.toService())
                }
            }
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun simuler(request: SimulerRequest): Either<KunneIkkeSimulereBehandling, Søknadsbehandling.Simulert> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeSimulereBehandling.FantIkkeBehandling.left()

        return søknadsbehandling.simuler(
            saksbehandler = request.saksbehandler,
        ) {
            utbetalingService.simulerUtbetaling(it)
                .map { simulertUtbetaling -> simulertUtbetaling.simulering }
        }.mapLeft {
            KunneIkkeSimulereBehandling.KunneIkkeSimulere(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, Søknadsbehandling.TilAttestering> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)?.let {
            statusovergang(
                søknadsbehandling = it,
                statusovergang = Statusovergang.TilAttestering(request.saksbehandler, request.fritekstTilBrev),
            )
        } ?: return KunneIkkeSendeTilAttestering.FantIkkeBehandling.left()

        tilbakekrevingService.hentAvventerKravgrunnlag(søknadsbehandling.sakId)
            .ifNotEmpty {
                return KunneIkkeSendeTilAttestering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
            }

        val aktørId = personService.hentAktørId(søknadsbehandling.fnr).getOrElse {
            log.error("Fant ikke aktør-id med for fødselsnummer : ${søknadsbehandling.fnr}")
            return KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()
        }
        val eksisterendeOppgaveId: OppgaveId = søknadsbehandling.oppgaveId

        val tilordnetRessurs: NavIdentBruker.Attestant? = søknadsbehandling.attesteringer.lastOrNull()?.attestant

        val nyOppgaveId: OppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.AttesterSøknadsbehandling(
                søknadId = søknadsbehandling.søknad.id,
                aktørId = aktørId,
                tilordnetRessurs = tilordnetRessurs,
                clock = clock,
            ),
        ).getOrElse {
            log.error("Kunne ikke opprette Attesteringsoppgave. Avbryter handlingen.")
            return KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
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

    override fun underkjenn(request: UnderkjennRequest): Either<KunneIkkeUnderkjenne, Søknadsbehandling.Underkjent> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeUnderkjenne.FantIkkeBehandling.left()

        return forsøkStatusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilUnderkjent(request.attestering),
        ).mapLeft {
            KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
        }.map { underkjent ->
            val aktørId = personService.hentAktørId(underkjent.fnr).getOrElse {
                log.error("Fant ikke aktør-id for sak: ${underkjent.id}")
                return KunneIkkeUnderkjenne.FantIkkeAktørId.left()
            }

            val journalpostId: JournalpostId = underkjent.søknad.journalpostId
            val eksisterendeOppgaveId = underkjent.oppgaveId

            val nyOppgaveId = oppgaveService.opprettOppgave(
                OppgaveConfig.Søknad(
                    journalpostId = journalpostId,
                    søknadId = underkjent.søknad.id,
                    aktørId = aktørId,
                    tilordnetRessurs = underkjent.saksbehandler,
                    clock = clock,
                    sakstype = underkjent.søknad.søknadInnhold.type(),
                ),
            ).getOrElse {
                log.error("Behandling ${underkjent.id} ble ikke underkjent. Klarte ikke opprette behandlingsoppgave")
                return@underkjenn KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave.left()
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

    override fun iverksett(
        request: IverksettRequest,
    ): Either<KunneIkkeIverksette, Søknadsbehandling.Iverksatt> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeIverksette.FantIkkeBehandling.left()

        return forsøkStatusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilIverksatt(
                request.attestering,
                hentOpprinneligAvkorting = { avkortingid ->
                    avkortingsvarselRepo.hent(id = avkortingid)
                },
            ),
        ).map { iverksattBehandling ->
            when (iverksattBehandling) {
                is Søknadsbehandling.Iverksatt.Innvilget -> {
                    val utbetaling = utbetalingService.verifiserOgSimulerUtbetaling(
                        request = UtbetalRequest.NyUtbetaling(
                            request = iverksattBehandling.lagSimulerUtbetalingRequest(
                                saksbehandler = request.attestering.attestant,
                                beregning = iverksattBehandling.beregning,
                            ),
                            simulering = iverksattBehandling.simulering,
                        ),
                    ).getOrHandle { kunneIkkeUtbetale ->
                        log.error("Kunne ikke innvilge behandling ${søknadsbehandling.id} siden utbetaling feilet. Feiltype: $kunneIkkeUtbetale")
                        return KunneIkkeIverksette.KunneIkkeUtbetale(kunneIkkeUtbetale).left()
                    }

                    val vedtak = VedtakSomKanRevurderes.fromSøknadsbehandling(iverksattBehandling, utbetaling.id, clock)
                    Either.catch {
                        sessionFactory.withTransactionContext {
                            // OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake. Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
                            // Det er også viktig at publiseringen av utbetalingen er det siste som skjer i blokka. Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. statistikk.
                            søknadsbehandlingRepo.lagre(iverksattBehandling, it)
                            utbetalingService.lagreUtbetaling(utbetaling, it)
                            vedtakRepo.lagre(vedtak, it)
                            // Så fremt denne ikke kaster ønsker vi å gå igjennom med iverksettingen.
                            kontrollsamtaleService.opprettPlanlagtKontrollsamtale(vedtak, it)
                            utbetalingService.publiserUtbetaling(utbetaling).mapLeft { feil ->
                                throw IverksettTransactionException(
                                    "Kunne ikke publisere utbetaling på køen. Underliggende feil: $feil.",
                                    KunneIkkeIverksette.KunneIkkeUtbetale(feil),
                                )
                            }
                        }
                    }.mapLeft {
                        log.error(
                            "Kunne ikke iverksette søknadsbehandling for sak ${iverksattBehandling.sakId} og søknadsbehandling ${iverksattBehandling.id}.",
                            it,
                        )
                        return when (it) {
                            is IverksettTransactionException -> it.feil
                            else -> KunneIkkeIverksette.LagringFeilet
                        }.left()
                    }

                    log.info("Iverksatt innvilgelse for behandling ${iverksattBehandling.id}, vedtak: ${vedtak.id}")

                    behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)

                    Pair(iverksattBehandling, vedtak)
                }
                is Søknadsbehandling.Iverksatt.Avslag -> {
                    val vedtak: Avslagsvedtak = opprettAvslagsvedtak(iverksattBehandling)

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

                    Either.catch {
                        sessionFactory.withTransactionContext {
                            // OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake. Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
                            // Det er også viktig at publiseringen av utbetalingen er det siste som skjer i blokka. Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. statistikk.
                            søknadsbehandlingRepo.lagre(iverksattBehandling, it)
                            vedtakRepo.lagre(vedtak, it)
                            brevService.lagreDokument(dokument, it)
                        }
                    }.mapLeft {
                        log.error(
                            "Kunne ikke iverksette søknadsbehandling for sak ${iverksattBehandling.sakId} og søknadsbehandling ${iverksattBehandling.id}.",
                            it,
                        )
                        return KunneIkkeIverksette.LagringFeilet.left()
                    }
                    log.info("Iverksatt avslag for behandling: ${iverksattBehandling.id}, vedtak: ${vedtak.id}")

                    behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)

                    ferdigstillVedtakService.lukkOppgaveMedBruker(vedtak.behandling)
                        .mapLeft {
                            log.error("Lukking av oppgave for behandlingId: ${(vedtak.behandling as BehandlingMedOppgave).oppgaveId} feilet. Må ryddes opp manuelt.")
                        }

                    Pair(iverksattBehandling, vedtak)
                }
            }
        }.map {
            Either.catch {
                observers.forEach { observer ->
                    observer.handle(Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(it.first))
                    (it.second as? VedtakSomKanRevurderes.EndringIYtelse)?.let { v ->
                        observer.handle(Event.Statistikk.Vedtaksstatistikk(v))
                    }
                }
            }.mapLeft { e ->
                log.error(
                    "Kunne ikke sende statistikk etter vi iverksatte søknadsbehandling. Dette er kun en sideeffekt og påvirker ikke saksbehandlingen.",
                    e,
                )
            }
            it.first
        }
    }

    private data class IverksettTransactionException(
        override val message: String,
        val feil: KunneIkkeIverksette,
    ) : RuntimeException(message)

    private fun opprettAvslagsvedtak(iverksattBehandling: Søknadsbehandling.Iverksatt.Avslag): Avslagsvedtak =
        when (iverksattBehandling) {
            is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
                Avslagsvedtak.fromSøknadsbehandlingMedBeregning(
                    avslag = iverksattBehandling,
                    clock = clock,
                )
            }
            is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
                Avslagsvedtak.fromSøknadsbehandlingUtenBeregning(
                    avslag = iverksattBehandling,
                    clock = clock,
                )
            }
        }

    override fun brev(request: BrevRequest): Either<KunneIkkeLageBrev, ByteArray> {
        val behandling = when (request) {
            is BrevRequest.MedFritekst ->
                request.behandling.medFritekstTilBrev(request.fritekst)
            is BrevRequest.UtenFritekst ->
                request.behandling
        }

        return brevService.lagDokument(behandling)
            .mapLeft {
                when (it) {
                    KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeLageBrev.KunneIkkeFinneGjeldendeUtbetaling
                    KunneIkkeLageDokument.KunneIkkeGenererePDF -> KunneIkkeLageBrev.KunneIkkeLagePDF
                    KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageBrev.FikkIkkeHentetSaksbehandlerEllerAttestant
                    KunneIkkeLageDokument.KunneIkkeHentePerson -> KunneIkkeLageBrev.FantIkkePerson
                }
            }
            .map {
                it.generertDokument
            }
    }

    override fun hent(request: HentRequest): Either<FantIkkeBehandling, Søknadsbehandling> {
        return søknadsbehandlingRepo.hent(request.behandlingId)?.right()
            ?: FantIkkeBehandling.left()
    }

    override fun hentForSøknad(søknadId: UUID): Søknadsbehandling? {
        return søknadsbehandlingRepo.hentForSøknad(søknadId)
    }

    override fun oppdaterStønadsperiode(request: OppdaterStønadsperiodeRequest): Either<KunneIkkeOppdatereStønadsperiode, Søknadsbehandling> {
        val sak = sakService.hentSak(request.sakId)
            .getOrHandle { return KunneIkkeOppdatereStønadsperiode.FantIkkeSak.left() }

        return sak.oppdaterStønadsperiodeForSøknadsbehandling(
            søknadsbehandlingId = request.behandlingId,
            stønadsperiode = request.stønadsperiode,
            clock = clock,
            formuegrenserFactory = formuegrenserFactory,
        ).mapLeft {
            KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereStønadsperiode(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun leggTilUførevilkår(
        request: LeggTilUførevurderingerRequest,
    ): Either<KunneIkkeLeggeTilUføreVilkår, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling.left()

        val vilkår = request.toVilkår(
            behandlingsperiode = søknadsbehandling.periode,
            clock = clock,
        ).getOrHandle {
            return KunneIkkeLeggeTilUføreVilkår.UgyldigInput(it).left()
        }

        val vilkårsvurdert = søknadsbehandling.leggTilUførevilkår(vilkår, clock)
            .getOrHandle {
                return when (it) {
                    is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.UgyldigTilstand -> {
                        KunneIkkeLeggeTilUføreVilkår.UgyldigTilstand(fra = it.fra, til = it.til)
                    }
                    KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.VurderingsperiodeUtenforBehandlingsperiode -> {
                        KunneIkkeLeggeTilUføreVilkår.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden
                    }
                }.left()
            }

        søknadsbehandlingRepo.lagre(vilkårsvurdert)
        return vilkårsvurdert.right()
    }

    override fun leggTilLovligOpphold(request: LeggTilLovligOppholdRequest): Either<KunneIkkeLeggetilLovligOppholdVilkår, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggetilLovligOppholdVilkår.FantIkkeBehandling.left()

        val vilkår = request.toVilkår(clock).getOrHandle {
            return KunneIkkeLeggetilLovligOppholdVilkår.UgyldigLovligOppholdVilkår(it).left()
        }

        return søknadsbehandling.leggTilLovligOpphold(
            lovligOppholdVilkår = vilkår,
            clock = clock,
        ).mapLeft {
            KunneIkkeLeggetilLovligOppholdVilkår.FeilVedSøknadsbehandling(it)
        }.tap {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun leggTilFamiliegjenforeningvilkår(request: LeggTilFamiliegjenforeningRequest): Either<KunneIkkeLeggeTilFamiliegjenforeningVilkårService, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFamiliegjenforeningVilkårService.FantIkkeBehandling.left()

        val familiegjenforeningVilkår = request.toVilkår(
            clock = clock,
            stønadsperiode = søknadsbehandling.stønadsperiode?.periode,
        ).getOrHandle {
            return KunneIkkeLeggeTilFamiliegjenforeningVilkårService.UgyldigFamiliegjenforeningVilkårService(it)
                .left()
        }
        familiegjenforeningVilkår.vurderingsperioder.single()

        return søknadsbehandling.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkår,
            clock = clock,
        ).mapLeft {
            it.tilKunneIkkeLeggeTilFamiliegjenforeningVilkårService()
        }.tap {
            søknadsbehandlingRepo.lagre(it)
        }
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
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun fullførBosituasjongrunnlag(request: FullførBosituasjonRequest): Either<KunneIkkeFullføreBosituasjonGrunnlag, Søknadsbehandling.Vilkårsvurdert> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling.left()

        val bosituasjon =
            request.toBosituasjon(søknadsbehandling.grunnlagsdata.bosituasjon.singleOrThrow(), clock).getOrHandle {
                return KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeLagreBosituasjon.left()
            }

        return søknadsbehandling.oppdaterBosituasjon(bosituasjon, clock).mapLeft {
            KunneIkkeFullføreBosituasjonGrunnlag.KunneIkkeEndreBosituasjongrunnlag(it)
        }.tap {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling> {
        val behandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        /**
         *  I flere av funksjonene i denne fila bruker vi [Statusovergang] og [no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor] for å bestemme om det er en gyldig statusovergang, men i dette tilfellet bruker vi domenemodellen sin funksjon leggTilFradragsgrunnlag til dette.
         * Vi ønsker gradvis å gå over til sistenevnte måte å gjøre det på.
         */
        val oppdatertBehandling =
            behandling.leggTilFradragsgrunnlag(request.fradragsgrunnlag, clock).getOrHandle {
                return it.toService().left()
            }

        søknadsbehandlingRepo.lagre(oppdatertBehandling)

        return oppdatertBehandling.right()
    }

    private fun KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.toService(): KunneIkkeLeggeTilFradragsgrunnlag {
        return when (this) {
            GrunnlagetMåVæreInnenforBehandlingsperioden -> {
                KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden
            }
            is IkkeLovÅLeggeTilFradragIDenneStatusen -> {
                KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                    fra = this.status,
                    til = Søknadsbehandling.Vilkårsvurdert.Innvilget::class,
                )
            }
            is KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag -> {
                KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(this.feil)
            }
        }
    }

    override fun lukk(lukketSøknadbehandling: LukketSøknadsbehandling, tx: TransactionContext) {
        søknadsbehandlingRepo.lagre(lukketSøknadbehandling, tx)
    }

    override fun lagre(avslag: AvslagManglendeDokumentasjon, tx: TransactionContext) {
        return søknadsbehandlingRepo.lagreAvslagManglendeDokumentasjon(avslag, tx)
    }

    override fun leggTilUtenlandsopphold(request: LeggTilUtenlandsoppholdRequest): Either<KunneIkkeLeggeTilUtenlandsopphold, Søknadsbehandling.Vilkårsvurdert> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left()

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

    override fun leggTilOpplysningspliktVilkår(request: LeggTilOpplysningspliktRequest.Søknadsbehandling): Either<KunneIkkeLeggeTilOpplysningsplikt, Søknadsbehandling.Vilkårsvurdert> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilOpplysningsplikt.FantIkkeBehandling.left()

        return søknadsbehandling.leggTilOpplysningspliktVilkår(request.vilkår, clock)
            .mapLeft {
                KunneIkkeLeggeTilOpplysningsplikt.Søknadsbehandling(it)
            }.map {
                søknadsbehandlingRepo.lagre(it)
                it
            }
    }

    override fun leggTilPensjonsVilkår(request: LeggTilPensjonsVilkårRequest): Either<KunneIkkeLeggeTilPensjonsVilkår, Søknadsbehandling.Vilkårsvurdert> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilPensjonsVilkår.FantIkkeBehandling.left()

        return søknadsbehandling.leggTilPensjonsVilkår(request.vilkår, clock)
            .mapLeft {
                KunneIkkeLeggeTilPensjonsVilkår.Søknadsbehandling(it)
            }.map {
                søknadsbehandlingRepo.lagre(it)
                it
            }
    }

    override fun leggTilFlyktningVilkår(request: LeggTilFlyktningVilkårRequest): Either<KunneIkkeLeggeTilFlyktningVilkår, Søknadsbehandling.Vilkårsvurdert> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFlyktningVilkår.FantIkkeBehandling.left()

        return søknadsbehandling.leggTilFlyktningVilkår(request.vilkår, clock)
            .mapLeft {
                KunneIkkeLeggeTilFlyktningVilkår.Søknadsbehandling(it)
            }.map {
                søknadsbehandlingRepo.lagre(it)
                it
            }
    }

    override fun leggTilFormuevilkår(
        request: LeggTilFormuevilkårRequest,
    ): Either<KunneIkkeLeggeTilFormuegrunnlag, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFormuegrunnlag.FantIkkeSøknadsbehandling.left()

        return søknadsbehandling.leggTilFormuevilkår(
            vilkår = request.toDomain(
                bosituasjon = søknadsbehandling.grunnlagsdata.bosituasjon,
                behandlingsperiode = søknadsbehandling.periode,
                clock = clock,
                formuegrenserFactory = formuegrenserFactory,
            ).getOrHandle {
                return KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet(it).left()
            },
            clock = clock,
        ).tap {
            søknadsbehandlingRepo.lagre(it)
        }.mapLeft {
            KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeLeggeTilFormuegrunnlagTilSøknadsbehandling(it)
        }
    }

    private fun KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.tilService(): SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold {
        return when (this) {
            is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.IkkeLovÅLeggeTilUtenlandsoppholdIDenneStatusen -> {
                KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(
                    fra = this.fra,
                    til = this.til,
                )
            }
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode -> {
                KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode
            }
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat -> {
                KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat
            }
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.MåInneholdeKunEnVurderingsperiode -> {
                KunneIkkeLeggeTilUtenlandsopphold.MåInneholdeKunEnVurderingsperiode
            }
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden -> {
                KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden
            }
        }
    }
}
