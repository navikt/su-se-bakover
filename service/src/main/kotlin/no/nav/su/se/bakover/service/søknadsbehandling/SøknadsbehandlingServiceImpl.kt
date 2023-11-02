package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.krympTilØvreGrense
import no.nav.su.se.bakover.common.tid.toRange
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.fradrag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigEpsOrNull
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonerRequest
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.KanBeregnes
import no.nav.su.se.bakover.domain.søknadsbehandling.KanOppdatereFradragsgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.KanOppdaterePeriodeBosituasjonVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.KanSendesTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.KanSimuleres
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.BeregnRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.FantIkkeBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.HentRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeBeregne
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.OppdaterStønadsperiodeRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.OpprettRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.SimulerRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.UnderkjennRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.BrevutkastForSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.KunneIkkeGenerereBrevutkastForSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.genererBrevutkastForSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.simuler.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.domain.vilkår.fastopphold.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.fastopphold.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.domain.vilkår.flyktning.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.KunneIkkeLeggeTilInstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.domain.vilkår.oppmøte.KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.pensjon.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock
import java.time.Year
import java.util.UUID
import kotlin.reflect.KClass

class SøknadsbehandlingServiceImpl(
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val behandlingMetrics: BehandlingMetrics,
    private val brevService: BrevService,
    private val clock: Clock,
    private val sakService: SakService,
    private val formuegrenserFactory: FormuegrenserFactory,
    private val satsFactory: SatsFactory,
    private val skatteService: SkatteService,
    private val sessionFactory: SessionFactory,
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
        val sak: Sak = sakService.hentSakForSøknadsbehandling(request.behandlingId)

        val søknadsbehandling: KanBeregnes = sak.hentSøknadsbehandling(request.behandlingId)
            .getOrElse { return KunneIkkeBeregne.FantIkkeBehandling.left() }
            .let { it as? KanBeregnes ?: return KunneIkkeBeregne.UgyldigTilstand(it::class).left() }

        return søknadsbehandling.beregn(
            nySaksbehandler = request.saksbehandler,
            begrunnelse = request.begrunnelse,
            clock = clock,
            satsFactory = satsFactory,
        ).mapLeft { feil ->
            when (feil) {
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
        val behandlingId = request.behandlingId
        val sak = sakService.hentSakForSøknadsbehandling(behandlingId)

        val søknadsbehandling = sak.hentSøknadsbehandling(behandlingId)
            .getOrElse { throw IllegalArgumentException("Fant ikke Søknadsbehandling med id $behandlingId") }.let {
                it as? KanSimuleres ?: return KunneIkkeSimulereBehandling.UgyldigTilstand(it::class).left()
            }

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
        }.onRight {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun sendTilAttestering(
        request: SendTilAttesteringRequest,
    ): Either<KunneIkkeSendeSøknadsbehandlingTilAttestering, SøknadsbehandlingTilAttestering> {
        val behandlingId = request.behandlingId
        val søknadsbehandlingSomKanSendesTilAttestering: KanSendesTilAttestering =
            (
                søknadsbehandlingRepo.hent(behandlingId)
                    ?: throw IllegalArgumentException("Søknadsbehandling send til attestering: Fant ikke søknadsbehandling med id $behandlingId. Avbryter handlingen.")
                ).let {
                it as? KanSendesTilAttestering
                    ?: return KunneIkkeSendeSøknadsbehandlingTilAttestering.UgyldigTilstand(
                        it::class,
                    ).left()
            }
        return søknadsbehandlingSomKanSendesTilAttestering.tilAttestering(
            saksbehandler = request.saksbehandler,
            fritekstTilBrev = request.fritekstTilBrev,
            clock = clock,
        ).map { søknadsbehandlingTilAttestering ->
            val aktørId = personService.hentAktørId(søknadsbehandlingTilAttestering.fnr).getOrElse {
                log.error("Søknadsbehandling send til attestering: Fant ikke aktør-id knyttet til fødselsnummer for søknadsbehandling $behandlingId. Avbryter handlingen.")
                return KunneIkkeSendeSøknadsbehandlingTilAttestering.KunneIkkeFinneAktørId.left()
            }
            val eksisterendeOppgaveId: OppgaveId = søknadsbehandlingTilAttestering.oppgaveId

            val tilordnetRessurs: NavIdentBruker.Attestant? =
                søknadsbehandlingTilAttestering.attesteringer.lastOrNull()?.attestant

            val nyOppgaveId: OppgaveId = oppgaveService.opprettOppgave(
                OppgaveConfig.AttesterSøknadsbehandling(
                    søknadId = søknadsbehandlingTilAttestering.søknad.id,
                    aktørId = aktørId,
                    tilordnetRessurs = tilordnetRessurs,
                    clock = clock,
                ),
            ).getOrElse {
                log.error("Søknadsbehandling send til attestering: Kunne ikke opprette Attesteringsoppgave for søknadsbehandling $behandlingId. Avbryter handlingen.")
                return KunneIkkeSendeSøknadsbehandlingTilAttestering.KunneIkkeOppretteOppgave.left()
            }
            val søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev =
                søknadsbehandlingTilAttestering.nyOppgaveId(nyOppgaveId)

            søknadsbehandlingRepo.lagre(søknadsbehandlingMedNyOppgaveIdOgFritekstTilBrev)

            oppgaveService.lukkOppgave(eksisterendeOppgaveId).map {
                behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.LUKKET_OPPGAVE)
            }.mapLeft {
                log.error("Søknadsbehandling send til attestering: Klarte ikke å lukke oppgave $eksisterendeOppgaveId for søknadsbehandling $behandlingId. Dette er kun best-effort og oppgaven må lukkes manuelt.")
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
    }

    override fun underkjenn(
        request: UnderkjennRequest,
    ): Either<KunneIkkeUnderkjenneSøknadsbehandling, UnderkjentSøknadsbehandling> {
        val søknadsbehandling = (
            søknadsbehandlingRepo.hent(request.behandlingId)
                ?: return KunneIkkeUnderkjenneSøknadsbehandling.FantIkkeBehandling.left()
            ).let {
            it as? SøknadsbehandlingTilAttestering ?: return KunneIkkeUnderkjenneSøknadsbehandling.UgyldigTilstand(
                it::class,
            ).left()
        }
        return søknadsbehandling.tilUnderkjent(request.attestering).map { underkjent ->
            val aktørId = personService.hentAktørId(underkjent.fnr).getOrElse {
                log.error("Fant ikke aktør-id for sak: ${underkjent.id}")
                return KunneIkkeUnderkjenneSøknadsbehandling.FantIkkeAktørId.left()
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
                return@underkjenn KunneIkkeUnderkjenneSøknadsbehandling.KunneIkkeOppretteOppgave.left()
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

    override fun genererBrevutkast(
        command: BrevutkastForSøknadsbehandlingCommand,
    ): Either<KunneIkkeGenerereBrevutkastForSøknadsbehandling, Pair<PdfA, Fnr>> {
        return genererBrevutkastForSøknadsbehandling(
            command = command,
            hentSøknadsbehandling = søknadsbehandlingRepo::hent,
            lagDokument = brevService::lagDokument,
            satsFactory = satsFactory,
        )
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
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            ugyldigTilstandError = { fra, til -> KunneIkkeLeggeTilUføreVilkår.UgyldigTilstand(fra, til) },
            fantIkkeBehandlingError = { KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling },
        ).getOrElse { return it.left() }

        val vilkår = request.toVilkår(
            behandlingsperiode = søknadsbehandling.periode,
            clock = clock,
        ).getOrElse {
            return KunneIkkeLeggeTilUføreVilkår.UgyldigInput(it).left()
        }

        val vilkårsvurdert = søknadsbehandling.leggTilUførevilkår(saksbehandler, vilkår).getOrElse {
            return KunneIkkeLeggeTilUføreVilkår.Domenefeil(it).left()
        }

        søknadsbehandlingRepo.lagre(vilkårsvurdert)
        return vilkårsvurdert.right()
    }

    override fun leggTilLovligOpphold(
        request: LeggTilLovligOppholdRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling, Søknadsbehandling> {
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            // TODO jah: Mangler Left.
            ugyldigTilstandError = { _, _ -> null },
            fantIkkeBehandlingError = { KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling.FantIkkeBehandling },
        ).getOrElse { return it.left() }

        val vilkår = request.toVilkår(clock).getOrElse {
            return KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling.UgyldigLovligOppholdVilkår(it).left()
        }

        return søknadsbehandling.leggTilLovligOpphold(
            lovligOppholdVilkår = vilkår,
            saksbehandler = saksbehandler,
        ).mapLeft {
            KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling.FeilVedSøknadsbehandling(it)
        }.onRight {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun leggTilFamiliegjenforeningvilkår(
        request: LeggTilFamiliegjenforeningRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFamiliegjenforeningVilkårService, Søknadsbehandling> {
        val søknadsbehandling: KanOppdaterePeriodeBosituasjonVilkår = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            ugyldigTilstandError = { fra, til ->
                KunneIkkeLeggeTilFamiliegjenforeningVilkårService.UgyldigTilstand(
                    fra,
                    til,
                )
            },
            fantIkkeBehandlingError = { KunneIkkeLeggeTilFamiliegjenforeningVilkårService.FantIkkeBehandling },
        ).getOrElse { return it.left() }

        val familiegjenforeningVilkår = request.toVilkår(
            clock = clock,
            stønadsperiode = søknadsbehandling.stønadsperiode?.periode,
        ).getOrElse {
            return KunneIkkeLeggeTilFamiliegjenforeningVilkårService.UgyldigFamiliegjenforeningVilkårService(it).left()
        }
        familiegjenforeningVilkår.vurderingsperioder.single()

        return søknadsbehandling.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkår,
            saksbehandler = saksbehandler,
        ).mapLeft {
            KunneIkkeLeggeTilFamiliegjenforeningVilkårService.Domenefeil(it)
        }.onRight {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun leggTilFradragsgrunnlag(
        request: LeggTilFradragsgrunnlagRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling> {
        val søknadsbehandling: KanOppdatereFradragsgrunnlag =
            when (val s = søknadsbehandlingRepo.hent(request.behandlingId)) {
                is Søknadsbehandling -> (s as? KanOppdatereFradragsgrunnlag)
                    ?: return KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                        fra = s::class,
                        til = VilkårsvurdertSøknadsbehandling.Innvilget::class,
                    ).left()

                null -> return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()
            }

        val oppdatertBehandling =
            søknadsbehandling.oppdaterFradragsgrunnlag(saksbehandler, request.fradragsgrunnlag, clock)
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
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            ugyldigTilstandError = { fra, til -> KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(fra, til) },
            fantIkkeBehandlingError = { KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling },
        ).getOrElse { return it.left() }

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

        val vilkårsvurdert = søknadsbehandling.leggTilUtenlandsopphold(saksbehandler, vilkår).getOrElse {
            return KunneIkkeLeggeTilUtenlandsopphold.Domenefeil(it).left()
        }

        søknadsbehandlingRepo.lagre(vilkårsvurdert)
        return vilkårsvurdert.right()
    }

    override fun leggTilOpplysningspliktVilkår(
        request: LeggTilOpplysningspliktRequest.Søknadsbehandling,
    ): Either<KunneIkkeLeggeTilOpplysningsplikt, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            // TODO jah: Mangler Left.
            ugyldigTilstandError = { _, _ -> null },
            fantIkkeBehandlingError = { KunneIkkeLeggeTilOpplysningsplikt.FantIkkeBehandling },
        ).getOrElse { return it.left() }

        return søknadsbehandling.leggTilOpplysningspliktVilkår(
            saksbehandler = request.saksbehandler,
            vilkår = request.vilkår,
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
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            // TODO jah: Mangler Left.
            ugyldigTilstandError = { _, _ -> null },
            fantIkkeBehandlingError = { KunneIkkeLeggeTilPensjonsVilkår.FantIkkeBehandling },
        ).getOrElse { return it.left() }

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
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            // TODO jah: Mangler Left.
            ugyldigTilstandError = { _, _ -> null },
            fantIkkeBehandlingError = { KunneIkkeLeggeTilFlyktningVilkår.FantIkkeBehandling },
        ).getOrElse { return it.left() }

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
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            // TODO jah: Mangler Left.
            ugyldigTilstandError = { _, _ -> null },
            fantIkkeBehandlingError = { KunneIkkeLeggeFastOppholdINorgeVilkår.FantIkkeBehandling },
        ).getOrElse { return it.left() }

        return søknadsbehandling.leggTilFastOppholdINorgeVilkår(saksbehandler, request.vilkår).mapLeft {
            KunneIkkeLeggeFastOppholdINorgeVilkår.Søknadsbehandling(it)
        }.onRight {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun leggTilPersonligOppmøteVilkår(
        request: LeggTilPersonligOppmøteVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            // TODO jah: Mangler Left.
            ugyldigTilstandError = { _, _ -> null },
            fantIkkeBehandlingError = { KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling.FantIkkeBehandling },
        ).getOrElse { return it.left() }

        return søknadsbehandling.leggTilPersonligOppmøteVilkår(saksbehandler, request.vilkår).mapLeft {
            KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling.Underliggende(it)
        }.onRight {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    override fun leggTilFormuevilkår(
        request: LeggTilFormuevilkårRequest,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår, Søknadsbehandling> {
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            ugyldigTilstandError = { fra, til ->
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.UgyldigTilstand(
                    fra,
                    til,
                )
            },
            // TODO jah: Mangler Left.
            fantIkkeBehandlingError = { null },
        ).getOrElse { return it.left() }

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
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            // TODO jah: Mangler Left.
            ugyldigTilstandError = { _, _ -> null },
            fantIkkeBehandlingError = { KunneIkkeLeggeTilInstitusjonsoppholdVilkår.FantIkkeBehandling },
        ).getOrElse { return it.left() }

        return søknadsbehandling.leggTilInstitusjonsoppholdVilkår(saksbehandler, request.vilkår).mapLeft {
            KunneIkkeLeggeTilInstitusjonsoppholdVilkår.Søknadsbehandling(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            it
        }
    }

    override fun leggTilBosituasjongrunnlag(
        request: LeggTilBosituasjonerRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            ugyldigTilstandError = { fra, _ ->
                // TODO jah: Bruker Revurdering sin type. Burde lage en egen for søknadsbehandling. Kan service/domain bruke den samme?
                KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeLeggeTilGrunnlag(
                    KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.UgyldigTilstand(
                        fra = fra,
                        til = VilkårsvurdertSøknadsbehandling::class,
                    ),
                )
            },
            fantIkkeBehandlingError = { KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling },
        ).getOrElse { return it.left() }

        val bosituasjon =
            if (request.bosituasjoner.size > 1) {
                throw IllegalArgumentException("Forventer kun 1 bosituasjon element ved søknadsbehandling")
            } else {
                request.bosituasjoner.first().toDomain(clock = clock, hentPerson = personService::hentPerson)
                    .getOrElse { return it.left() }
            }

        return søknadsbehandling.oppdaterBosituasjon(
            saksbehandler = saksbehandler,
            bosituasjon = bosituasjon,
        ).mapLeft {
            KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeLeggeTilGrunnlag(it)
        }.map {
            sessionFactory.withTransactionContext { tx ->
                søknadsbehandlingRepo.lagre(it, tx)
            }
            it
        }
    }

    override fun leggTilEksternSkattegrunnlag(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(behandlingId)
            ?: throw IllegalStateException("Fant ikke behandling $behandlingId")

        return søknadsbehandling.leggTilSkatt(
            EksterneGrunnlagSkatt.Hentet(
                søkers = søknadsbehandling.hentSkattegrunnlagForSøker(
                    saksbehandler,
                    skatteService::hentSamletSkattegrunnlagForÅr,
                    clock,
                ),
                eps = søknadsbehandling.hentSkattegrunnlagForEps(
                    saksbehandler,
                    skatteService::hentSamletSkattegrunnlagForÅr,
                    clock,
                ),
            ),
        ).onRight { søknadsbehandlingRepo.lagre(it) }
    }

    private inline fun <reified T> hentKanOppdaterePeriodeGrunnlagVilkår(
        søknadsbehandlingRepo: SøknadsbehandlingRepo,
        søknadsbehandlingId: UUID,
        ugyldigTilstandError: (KClass<out Søknadsbehandling>, KClass<KanOppdaterePeriodeBosituasjonVilkår>) -> T?,
        fantIkkeBehandlingError: () -> T?,
    ): Either<T, KanOppdaterePeriodeBosituasjonVilkår> {
        return when (val s = søknadsbehandlingRepo.hent(søknadsbehandlingId)) {
            is Søknadsbehandling -> when (val k = (s as? KanOppdaterePeriodeBosituasjonVilkår)) {
                null -> ugyldigTilstandError(s::class, KanOppdaterePeriodeBosituasjonVilkår::class).let {
                    it
                        ?: throw IllegalStateException("Kan ikke legge til lovlig opphold for behandling $søknadsbehandlingId, siden den er i feil tilstand: ${s::class.simpleName}")
                }.left()

                else -> k.right()
            }

            null -> fantIkkeBehandlingError().let {
                it
                    ?: throw IllegalArgumentException("Kan ikke legge til lovlig opphold for behandling $søknadsbehandlingId; fant ikke behandlingen.")
            }.left()
        }
    }

    private fun Søknadsbehandling.hentSkattegrunnlagForSøker(
        saksbehandler: NavIdentBruker.Saksbehandler,
        samletSkattegrunnlag: (Fnr, NavIdentBruker.Saksbehandler, YearRange) -> Skattegrunnlag,
        clock: Clock,
    ): Skattegrunnlag = samletSkattegrunnlag(fnr, saksbehandler, getYearRangeForSkatt(clock))

    private fun Søknadsbehandling.hentSkattegrunnlagForEps(
        saksbehandler: NavIdentBruker.Saksbehandler,
        samletSkattegrunnlag: (Fnr, NavIdentBruker.Saksbehandler, YearRange) -> Skattegrunnlag,
        clock: Clock,
    ): Skattegrunnlag? = if (grunnlagsdata.bosituasjon.harEPS()) {
        samletSkattegrunnlag(
            grunnlagsdata.bosituasjon.singleFullstendigEpsOrNull()!!.fnr,
            saksbehandler,
            getYearRangeForSkatt(clock),
        )
    } else {
        null
    }

    private fun Søknadsbehandling.getYearRangeForSkatt(clock: Clock): YearRange {
        return Year.now(clock).minusYears(1).let {
            stønadsperiode?.toYearRange()?.krympTilØvreGrense(it) ?: it.toRange()
        }
    }
}
