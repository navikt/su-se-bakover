package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.grunnlag.fradrag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.domain.grunnlag.singleOrThrow
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.BeregnRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.BrevRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.FantIkkeBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.HentRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeBeregne
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService.FantIkkeBehandling.tilKunneIkkeLeggeTilFamiliegjenforeningVilkårService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.OppdaterStønadsperiodeRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.OpprettRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.SimulerRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.UnderkjennRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.forsøkStatusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.medFritekstTilBrev
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.domain.vilkår.bosituasjon.FullførBosituasjonRequest
import no.nav.su.se.bakover.domain.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjonEpsGrunnlag
import no.nav.su.se.bakover.domain.vilkår.bosituasjon.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.domain.vilkår.fastopphold.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.fastopphold.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.domain.vilkår.flyktning.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.KunneIkkeLeggeTilInstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.KunneIkkeLeggetilLovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.domain.vilkår.oppmøte.KunneIkkeLeggeTilPersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.pensjon.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class SøknadsbehandlingServiceImpl(
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val behandlingMetrics: BehandlingMetrics,
    private val brevService: BrevService,
    private val clock: Clock,
    private val sakService: SakService,
    private val tilbakekrevingService: TilbakekrevingService,
    private val formuegrenserFactory: FormuegrenserFactory,
    private val satsFactory: SatsFactory,
) : SøknadsbehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<StatistikkEventObserver> = observers.toList()

    /**
     * Sideeffekter:
     * - søknadsbehandlingen persisteres.
     * - det sendes statistikk
     *
     * @param hentSak Mulighet for å sende med en funksjon som henter en sak, default er null, som gjør at saken hentes på nytt fra persisteringslaget basert på request.sakId.
     */
    override fun opprett(
        request: OpprettRequest,
        hentSak: (() -> Sak)?,
    ): Either<Sak.KunneIkkeOppretteSøknadsbehandling, Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart>> {
        val sakId = request.sakId
        val sak = hentSak?.let { it() } ?: sakService.hentSak(sakId)
            .getOrElse { throw IllegalArgumentException("Fant ikke sak $sakId") }

        require(sak.id == sakId) { "sak.id ${sak.id} må være lik request.sakId $sakId" }

        return sak.opprettNySøknadsbehandling(
            søknadId = request.søknadId,
            clock = clock,
            saksbehandler = request.saksbehandler,
        ).map { (sak, nySøknadsbehandling, uavklartSøknadsbehandling, statistikk) ->
            søknadsbehandlingRepo.lagreNySøknadsbehandling(nySøknadsbehandling)
            observers.notify(statistikk)
            Pair(sak, uavklartSøknadsbehandling)
        }
    }

    override fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, BeregnetSøknadsbehandling> {
        val søknadsbehandling =
            søknadsbehandlingRepo.hent(request.behandlingId) ?: return KunneIkkeBeregne.FantIkkeBehandling.left()

        return søknadsbehandling.beregn(
            nySaksbehandler = request.saksbehandler,
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

    override fun simuler(request: SimulerRequest): Either<KunneIkkeSimulereBehandling, SimulertSøknadsbehandling> {
        val sak = sakService.hentSakForSøknadsbehandling(request.behandlingId)

        val søknadsbehandling = sak.hentSøknadsbehandling(request.behandlingId)
            .getOrElse { return KunneIkkeSimulereBehandling.FantIkkeBehandling.left() }

        return søknadsbehandling.simuler(
            saksbehandler = request.saksbehandler,
            clock = clock,
        ) { beregning, uføregrunnlag ->
            sak.lagNyUtbetaling(
                saksbehandler = request.saksbehandler,
                beregning = beregning,
                clock = clock,
                utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                uføregrunnlag = uføregrunnlag,
            ).let {
                sak.simulerUtbetaling(
                    utbetalingForSimulering = it,
                    periode = beregning.periode,
                    simuler = utbetalingService::simulerUtbetaling,
                    kontrollerMotTidligereSimulering = null,
                )
            }.map { simulertUtbetaling ->
                simulertUtbetaling.simulering
            }
        }.mapLeft {
            KunneIkkeSimulereBehandling.KunneIkkeSimulere(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, SøknadsbehandlingTilAttestering> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)?.let {
            statusovergang(
                søknadsbehandling = it,
                statusovergang = Statusovergang.TilAttestering(request.saksbehandler, request.fritekstTilBrev, clock),
            )
        }
            ?: throw IllegalArgumentException("Søknadsbehandling send til attestering: Fant ikke søknadsbehandling ${request.behandlingId}")

        val aktørId = personService.hentAktørId(søknadsbehandling.fnr).getOrElse {
            log.error("Søknadsbehandling send til attestering: Fant ikke aktør-id knyttet til fødselsnummer for søknadsbehandling ${request.behandlingId}")
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
            log.error("Søknadsbehandling send til attestering: Kunne ikke opprette Attesteringsoppgave for søknadsbehandling ${request.behandlingId}. Avbryter handlingen.")
            return KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
        }

        val søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev =
            søknadsbehandling.nyOppgaveId(nyOppgaveId).medFritekstTilBrev(request.fritekstTilBrev)

        søknadsbehandlingRepo.lagre(søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev)

        oppgaveService.lukkOppgave(eksisterendeOppgaveId).map {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.LUKKET_OPPGAVE)
        }.mapLeft {
            log.error("Søknadsbehandling send til attestering: Klarte ikke å lukke oppgave ${søknadsbehandling.oppgaveId} for søknadsbehandling ${request.behandlingId}.")
        }
        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.PERSISTERT)
        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.OPPRETTET_OPPGAVE)
        when (søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev) {
            is SøknadsbehandlingTilAttestering.Avslag -> observers.notify(
                StatistikkEvent.Behandling.Søknad.TilAttestering.Avslag(
                    søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev,
                ),
            )

            is SøknadsbehandlingTilAttestering.Innvilget -> observers.notify(
                StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget(
                    søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev,
                ),
            )
        }
        return søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev.right()
    }

    override fun underkjenn(request: UnderkjennRequest): Either<KunneIkkeUnderkjenne, UnderkjentSøknadsbehandling> {
        val søknadsbehandling =
            søknadsbehandlingRepo.hent(request.behandlingId) ?: return KunneIkkeUnderkjenne.FantIkkeBehandling.left()

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

            oppgaveService.lukkOppgave(eksisterendeOppgaveId).mapLeft {
                log.error("Kunne ikke lukke attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen. Dette må gjøres manuelt.")
            }.map {
                log.info("Lukket attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen")
                behandlingMetrics.incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.LUKKET_OPPGAVE)
            }
            when (søknadsbehandlingMedNyOppgaveId) {
                is UnderkjentSøknadsbehandling.Avslag -> observers.notify(
                    StatistikkEvent.Behandling.Søknad.Underkjent.Avslag(
                        søknadsbehandlingMedNyOppgaveId,
                    ),
                )

                is UnderkjentSøknadsbehandling.Innvilget -> observers.notify(
                    StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget(
                        søknadsbehandlingMedNyOppgaveId,
                    ),
                )
            }
            søknadsbehandlingMedNyOppgaveId
        }
    }

    override fun brev(request: BrevRequest): Either<KunneIkkeLageDokument, ByteArray> {
        val behandling = when (request) {
            is BrevRequest.MedFritekst -> request.behandling.medFritekstTilBrev(request.fritekst)

            is BrevRequest.UtenFritekst -> request.behandling
        }

        return brevService.lagDokument(behandling).map { it.generertDokument }
    }

    override fun hent(request: HentRequest): Either<FantIkkeBehandling, Søknadsbehandling> {
        return søknadsbehandlingRepo.hent(request.behandlingId)?.right() ?: FantIkkeBehandling.left()
    }

    override fun hentForSøknad(søknadId: UUID): Søknadsbehandling? {
        return søknadsbehandlingRepo.hentForSøknad(søknadId)
    }

    override fun oppdaterStønadsperiode(
        request: OppdaterStønadsperiodeRequest,
    ): Either<Sak.KunneIkkeOppdatereStønadsperiode, VilkårsvurdertSøknadsbehandling> {
        val sak =
            sakService.hentSak(request.sakId)
                .getOrElse { throw IllegalArgumentException("Fant ikke sak ${request.sakId}") }

        return sak.oppdaterStønadsperiodeForSøknadsbehandling(
            søknadsbehandlingId = request.behandlingId,
            stønadsperiode = request.stønadsperiode,
            clock = clock,
            formuegrenserFactory = formuegrenserFactory,
            saksbehandler = request.saksbehandler,
            hentPerson = personService::hentPerson,
            saksbehandlersAvgjørelse = request.saksbehandlersAvgjørelse,
        ).map {
            søknadsbehandlingRepo.lagre(it.second)
            it.second
        }
    }

    override fun leggTilUførevilkår(
        request: LeggTilUførevurderingerRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilUføreVilkår, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling.left()

        val vilkår = request.toVilkår(
            behandlingsperiode = søknadsbehandling.periode,
            clock = clock,
        ).getOrElse {
            return KunneIkkeLeggeTilUføreVilkår.UgyldigInput(it).left()
        }

        val vilkårsvurdert = søknadsbehandling.leggTilUførevilkår(saksbehandler, vilkår, clock).getOrElse {
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

    override fun leggTilLovligOpphold(
        request: LeggTilLovligOppholdRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggetilLovligOppholdVilkår, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggetilLovligOppholdVilkår.FantIkkeBehandling.left()

        val vilkår = request.toVilkår(clock).getOrElse {
            return KunneIkkeLeggetilLovligOppholdVilkår.UgyldigLovligOppholdVilkår(it).left()
        }

        return søknadsbehandling.leggTilLovligOpphold(
            lovligOppholdVilkår = vilkår,
            saksbehandler = saksbehandler,
            clock = clock,
        ).mapLeft {
            KunneIkkeLeggetilLovligOppholdVilkår.FeilVedSøknadsbehandling(it)
        }.onRight {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun leggTilFamiliegjenforeningvilkår(
        request: LeggTilFamiliegjenforeningRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFamiliegjenforeningVilkårService, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFamiliegjenforeningVilkårService.FantIkkeBehandling.left()

        val familiegjenforeningVilkår = request.toVilkår(
            clock = clock,
            stønadsperiode = søknadsbehandling.stønadsperiode?.periode,
        ).getOrElse {
            return KunneIkkeLeggeTilFamiliegjenforeningVilkårService.UgyldigFamiliegjenforeningVilkårService(it).left()
        }
        familiegjenforeningVilkår.vurderingsperioder.single()

        return søknadsbehandling.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkår,
            saksbehandler = saksbehandler,
        ).mapLeft {
            it.tilKunneIkkeLeggeTilFamiliegjenforeningVilkårService()
        }.onRight {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun leggTilBosituasjonEpsgrunnlag(
        request: LeggTilBosituasjonEpsRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilBosituasjonEpsGrunnlag, Søknadsbehandling> {
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
        }.getOrElse {
            return it.left()
        }

        return søknadsbehandling.oppdaterBosituasjon(
            saksbehandler,
            bosituasjon,
            Søknadsbehandlingshendelse(
                tidspunkt = Tidspunkt.now(clock),
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.TattStillingTilEPS,
            ),
        ).mapLeft {
            KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KunneIkkeOppdatereBosituasjon(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun fullførBosituasjongrunnlag(
        request: FullførBosituasjonRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeFullføreBosituasjonGrunnlag, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling.left()

        val bosituasjon =
            request.toBosituasjon(søknadsbehandling.grunnlagsdata.bosituasjon.singleOrThrow(), clock).getOrElse {
                return KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeLagreBosituasjon.left()
            }

        return søknadsbehandling.oppdaterBosituasjon(
            saksbehandler,
            bosituasjon,
            Søknadsbehandlingshendelse(
                tidspunkt = Tidspunkt.now(clock),
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.FullførtBosituasjon,
            ),
        ).mapLeft {
            KunneIkkeFullføreBosituasjonGrunnlag.KunneIkkeEndreBosituasjongrunnlag(it)
        }.onRight {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun leggTilFradragsgrunnlag(
        request: LeggTilFradragsgrunnlagRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling> {
        val behandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        /**
         *  I flere av funksjonene i denne fila bruker vi [Statusovergang] og [no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor] for å bestemme om det er en gyldig statusovergang, men i dette tilfellet bruker vi domenemodellen sin funksjon leggTilFradragsgrunnlag til dette.
         * Vi ønsker gradvis å gå over til sistenevnte måte å gjøre det på.
         */
        val oppdatertBehandling =
            behandling.leggTilFradragsgrunnlagFraSaksbehandler(saksbehandler, request.fradragsgrunnlag, clock)
                .getOrElse {
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
                    til = VilkårsvurdertSøknadsbehandling.Innvilget::class,
                )
            }

            is KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag -> {
                KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(this.feil)
            }
        }
    }

    override fun persisterSøknadsbehandling(lukketSøknadbehandling: LukketSøknadsbehandling, tx: TransactionContext) {
        søknadsbehandlingRepo.lagre(lukketSøknadbehandling, tx)
    }

    override fun leggTilUtenlandsopphold(
        request: LeggTilFlereUtenlandsoppholdRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilUtenlandsopphold, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left()

        val vilkår = request.tilVilkår(clock).getOrElse {
            when (it) {
                LeggTilFlereUtenlandsoppholdRequest.UgyldigUtenlandsopphold.OverlappendeVurderingsperioder -> throw IllegalStateException(
                    "$it Skal ikke kunne forekomme for søknadsbehandling",
                )

                LeggTilFlereUtenlandsoppholdRequest.UgyldigUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig -> throw IllegalStateException(
                    "$it Skal ikke kunne forekomme for søknadsbehandling",
                )
            }
        }

        val vilkårsvurdert = søknadsbehandling.leggTilUtenlandsopphold(saksbehandler, vilkår, clock).getOrElse {
            return it.tilService().left()
        }

        søknadsbehandlingRepo.lagre(vilkårsvurdert)
        return vilkårsvurdert.right()
    }

    override fun leggTilOpplysningspliktVilkår(request: LeggTilOpplysningspliktRequest.Søknadsbehandling): Either<KunneIkkeLeggeTilOpplysningsplikt, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilOpplysningsplikt.FantIkkeBehandling.left()

        return søknadsbehandling.leggTilOpplysningspliktVilkårForSaksbehandler(
            request.saksbehandler,
            request.vilkår,
            clock,
        ).mapLeft {
            KunneIkkeLeggeTilOpplysningsplikt.Søknadsbehandling(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun leggTilPensjonsVilkår(
        request: LeggTilPensjonsVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilPensjonsVilkår, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilPensjonsVilkår.FantIkkeBehandling.left()

        return søknadsbehandling.leggTilPensjonsVilkår(request.vilkår, saksbehandler).mapLeft {
            KunneIkkeLeggeTilPensjonsVilkår.Søknadsbehandling(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun leggTilFlyktningVilkår(
        request: LeggTilFlyktningVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFlyktningVilkår, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFlyktningVilkår.FantIkkeBehandling.left()

        return søknadsbehandling.leggTilFlyktningVilkår(saksbehandler, request.vilkår, clock).mapLeft {
            KunneIkkeLeggeTilFlyktningVilkår.Søknadsbehandling(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun leggTilFastOppholdINorgeVilkår(
        request: LeggTilFastOppholdINorgeRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeFastOppholdINorgeVilkår, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeFastOppholdINorgeVilkår.FantIkkeBehandling.left()

        return søknadsbehandling.leggTilFastOppholdINorgeVilkår(saksbehandler, request.vilkår, clock).mapLeft {
            KunneIkkeLeggeFastOppholdINorgeVilkår.Søknadsbehandling(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun leggTilPersonligOppmøteVilkår(
        request: LeggTilPersonligOppmøteVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilPersonligOppmøteVilkår.FantIkkeBehandling.left()

        return søknadsbehandling.leggTilPersonligOppmøteVilkår(saksbehandler, request.vilkår, clock).mapLeft {
            KunneIkkeLeggeTilPersonligOppmøteVilkår.Søknadsbehandling(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun leggTilFormuevilkår(
        request: LeggTilFormuevilkårRequest,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: throw IllegalArgumentException("Fant ikke behandling med id ${request.behandlingId}")

        return søknadsbehandling.leggTilFormuegrunnlag(
            request = request,
            formuegrenserFactory = formuegrenserFactory,
        ).onRight {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun leggTilInstitusjonsoppholdVilkår(
        request: LeggTilInstitusjonsoppholdVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilInstitusjonsoppholdVilkår.FantIkkeBehandling.left()

        return søknadsbehandling.leggTilInstitusjonsoppholdVilkår(saksbehandler, request.vilkår, clock).mapLeft {
            KunneIkkeLeggeTilInstitusjonsoppholdVilkår.Søknadsbehandling(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    private fun KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.tilService(): KunneIkkeLeggeTilUtenlandsopphold {
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
