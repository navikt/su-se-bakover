package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.domain.fradrag.LeggTilFradragsgrunnlagRequest
import behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling
import behandling.søknadsbehandling.domain.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import behandling.søknadsbehandling.domain.bosituasjon.LeggTilBosituasjonerCommand
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.simulering.simulerUtbetaling
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.FeilVedHentingAvGjeldendeVedtaksdataForPeriode
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.hentSisteInnvilgetSøknadsbehandlingGrunnlagFiltrerVekkSøknadsbehandling
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.KanBeregnes
import no.nav.su.se.bakover.domain.søknadsbehandling.KanOppdatereFradragsgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.KanOppdaterePeriodeBosituasjonVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.KanReturneresFraAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.KanSendesTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.KanSimuleres
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.ReturnerSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
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
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.SøknadsbehandlingSkatt
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.retur.KunneIkkeReturnereSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.simuler.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
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
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import org.slf4j.LoggerFactory
import person.domain.PersonService
import satser.domain.SatsFactory
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.harEPS
import vilkår.bosituasjon.domain.grunnlag.singleFullstendigEpsOrNull
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.inntekt.domain.grunnlag.slåSammen
import vilkår.skatt.application.SkatteService
import vilkår.skatt.domain.Skattegrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

class SøknadsbehandlingServiceImpl(
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
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
     * - oppgave oppdateres med tildordnet ressurs (best effort)
     * @param hentSak Mulighet for å sende med en funksjon som henter en sak, default er null, som gjør at saken hentes på nytt fra persisteringslaget basert på request.sakId.
     */
    override fun opprett(
        request: OpprettRequest,
        hentSak: (() -> Sak)?,
    ): Either<KunneIkkeOppretteSøknadsbehandling, Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart>> {
        val sakId = request.sakId
        val sak = hentSak?.let { it() } ?: sakService.hentSak(sakId)
            .getOrElse { throw IllegalArgumentException("Fant ikke sak $sakId") }

        require(sak.id == sakId) { "sak.id ${sak.id} må være lik request.sakId $sakId" }

        return sak.opprettNySøknadsbehandling(
            søknadId = request.søknadId,
            clock = clock,
            saksbehandler = request.saksbehandler,
            oppdaterOppgave = { oppgaveId, saksbehandler ->
                oppgaveService.oppdaterOppgave(
                    oppgaveId = oppgaveId,
                    oppdaterOppgaveInfo = OppdaterOppgaveInfo(
                        beskrivelse = "Tilordnet oppgave til ${saksbehandler.navIdent}",
                        tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(saksbehandler.navIdent),
                    ),
                ).mapLeft {
                    when (it) {
                        is KunneIkkeOppdatereOppgave.OppgaveErFerdigstilt -> {
                            log.warn("Kunne ikke oppdatere oppgave $oppgaveId sakid: $sakId med tilordnet ressurs. Feilen var $it")
                        }
                        else -> {
                            log.error("Kunne ikke oppdatere oppgave $oppgaveId sakid: $sakId med tilordnet ressurs. Feilen var $it")
                        }
                    }
                }
            },
        ).map { (sak, uavklartSøknadsbehandling, statistikk) ->
            søknadsbehandlingRepo.lagre(uavklartSøknadsbehandling)
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
                is no.nav.su.se.bakover.domain.søknadsbehandling.beregn.KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag -> {
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
                simulerUtbetaling(
                    tidligereUtbetalinger = sak.utbetalinger,
                    utbetalingForSimulering = it,
                    simuler = utbetalingService::simulerUtbetaling,
                )
            }.map { simulertUtbetaling ->
                // TODO simulering jah: Returner simuleringsresultatet til saksbehandler.
                simulertUtbetaling.simulertUtbetaling.simulering
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
            oppgaveService.oppdaterOppgave(
                oppgaveId = søknadsbehandlingTilAttestering.oppgaveId,
                oppdaterOppgaveInfo = OppdaterOppgaveInfo(
                    beskrivelse = "Sendt til attestering",
                    oppgavetype = Oppgavetype.ATTESTERING,
                    tilordnetRessurs = søknadsbehandlingTilAttestering.attesteringer.lastOrNull()?.attestant?.navIdent?.let {
                        OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(it)
                    } ?: OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs,
                ),
            ).mapLeft {
                // gjør en best effort på å oppdatere oppgaven
                log.error("Søknadsbehandling send til attestering: Kunne ikke oppdatere oppgave ${søknadsbehandlingTilAttestering.oppgaveId} for søknadsbehandling $behandlingId. Feilen var $it")
            }
            søknadsbehandlingRepo.lagre(søknadsbehandlingTilAttestering)
            when (søknadsbehandlingTilAttestering) {
                is SøknadsbehandlingTilAttestering.Avslag -> observers.notify(
                    StatistikkEvent.Behandling.Søknad.TilAttestering.Avslag(søknadsbehandlingTilAttestering),
                )

                is SøknadsbehandlingTilAttestering.Innvilget -> observers.notify(
                    StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget(søknadsbehandlingTilAttestering),
                )
            }
            return søknadsbehandlingTilAttestering.right()
        }
    }

    override fun retur(
        request: SøknadsbehandlingService.ReturRequest
    ): Either<KunneIkkeReturnereSøknadsbehandling, KanReturneresFraAttestering> {
        val søknadsbehandling = (
            søknadsbehandlingRepo.hent(request.behandlingId)
                ?: return KunneIkkeReturnereSøknadsbehandling.FantIkkeBehandling.left()
            ).let {
                it as? SøknadsbehandlingTilAttestering ?: return KunneIkkeReturnereSøknadsbehandling.UgyldigTilstand(
                    it::class
                ).left()
            }
            if(request.saksbehandler === søknadsbehandling.saksbehandler) {
               return KunneIkkeReturnereSøknadsbehandling.FeilSaksbehandler.left()
            }
        return søknadsbehandling.tilRetur(request.attestering).let { retur ->
            oppgaveService.oppdaterOppgave(
                retur.oppgaveId,
                oppdaterOppgaveInfo = OppdaterOppgaveInfo(
                    beskrivelse = "Behandling har blitt returnert",
                    oppgavetype = Oppgavetype.BEHANDLE_SAK,
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(søknadsbehandling.saksbehandler.navIdent),
                ),
            ).map{
                log.info("Behandling ${retur.id} ble returnert. Oppgave ${retur.oppgaveId} ble oppdatert. Se sikkerlogg for response")
                sikkerLogg.info("Behandling ${retur.id} ble returnert. Oppgave ${retur.oppgaveId} ble oppdatert. oppgaveResponse: ${it.response}")
            }.mapLeft {
                log.error("Søknadsbehandling retur: Kunne ikke oppdatere oppgave ${retur.oppgaveId} for søknadsbehandling ${retur.id}. Feilen var $it")
            }
            søknadsbehandlingRepo.lagre(retur)
            retur.right()
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
            oppgaveService.oppdaterOppgave(
                underkjent.oppgaveId,
                oppdaterOppgaveInfo = OppdaterOppgaveInfo(
                    beskrivelse = "Behandling har blitt underkjent",
                    oppgavetype = Oppgavetype.BEHANDLE_SAK,
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(underkjent.saksbehandler.navIdent),
                ),
            ).map {
                log.info("Behandling ${underkjent.id} ble underkjent. Oppgave ${underkjent.oppgaveId} ble oppdatert. Se sikkerlogg for response")
                sikkerLogg.info("Behandling ${underkjent.id} ble underkjent. Oppgave ${underkjent.oppgaveId} ble oppdatert. oppgaveResponse: ${it.response}")
            }.mapLeft {
                // gjør en best effort på å oppdatere oppgaven
                log.error("Søknadsbehandling underkjenn: Kunne ikke oppdatere oppgave ${underkjent.oppgaveId} for søknadsbehandling ${underkjent.id}. Feilen var $it")
            }

            søknadsbehandlingRepo.lagre(underkjent)
            when (underkjent) {
                is UnderkjentSøknadsbehandling.Avslag -> observers.notify(
                    StatistikkEvent.Behandling.Søknad.Underkjent.Avslag(underkjent),
                )

                is UnderkjentSøknadsbehandling.Innvilget -> observers.notify(
                    StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget(underkjent),
                )
            }
            underkjent
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
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
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
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
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
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            ugyldigTilstandError = { fra, til ->
                KunneIkkeLeggeTilFamiliegjenforeningVilkårService.UgyldigTilstand(
                    fra,
                    til,
                )
            },
            fantIkkeBehandlingError = { KunneIkkeLeggeTilFamiliegjenforeningVilkårService.FantIkkeBehandling },
        ).getOrElse { return it.left() }

        val familiegjenforeningVilkår = request.toVilkår(clock = clock).getOrElse {
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
            when (val s = søknadsbehandlingRepo.hent(request.behandlingId as SøknadsbehandlingId)) {
                is Søknadsbehandling -> (s as? KanOppdatereFradragsgrunnlag)
                    ?: return KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                        fra = s::class,
                        til = VilkårsvurdertSøknadsbehandling.Innvilget::class,
                    ).left()

                null -> return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()
            }

        if (request.fradragsgrunnlag.size != request.fradragsgrunnlag.slåSammen(clock).size) {
            return KunneIkkeLeggeTilFradragsgrunnlag.FradrageneMåSlåsSammen.left()
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
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
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
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
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
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
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
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
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
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
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
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
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
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
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
        request: LeggTilBosituasjonerCommand,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, VilkårsvurdertSøknadsbehandling> {
        val søknadsbehandling = hentKanOppdaterePeriodeGrunnlagVilkår(
            søknadsbehandlingId = request.behandlingId as SøknadsbehandlingId,
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            ugyldigTilstandError = { fra, _ ->
                KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigTilstand(
                    fra = fra,
                    til = VilkårsvurdertSøknadsbehandling::class,
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
            KunneIkkeLeggeTilBosituasjongrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden
        }.map {
            sessionFactory.withTransactionContext { tx ->
                søknadsbehandlingRepo.lagre(it, tx)
            }
            it
        }
    }

    override fun oppdaterSkattegrunnlag(
        søknadsbehandlingSkatt: SøknadsbehandlingSkatt,
    ): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(søknadsbehandlingSkatt.behandlingId)
            ?: throw IllegalStateException("Fant ikke behandling ${søknadsbehandlingSkatt.behandlingId}")

        return søknadsbehandling.leggTilSkatt(
            EksterneGrunnlagSkatt.Hentet(
                søkers = skatteService.hentSamletSkattegrunnlagForÅr(søknadsbehandling.fnr, søknadsbehandling.saksbehandler, søknadsbehandlingSkatt.yearRange),
                eps = søknadsbehandling.hentSkattegrunnlagForEps(
                    søknadsbehandlingSkatt.saksbehandler,
                ) { fnr, saksbehandler ->
                    skatteService.hentSamletSkattegrunnlagForÅr(fnr, saksbehandler, søknadsbehandlingSkatt.yearRange)
                },
            ),
        ).onRight { søknadsbehandlingRepo.lagre(it) }
    }

    override fun lagre(søknadsbehandling: Søknadsbehandling) {
        søknadsbehandlingRepo.lagre(søknadsbehandling)
    }

    override fun hentSisteInnvilgetSøknadsbehandlingGrunnlagForSakFiltrerVekkSøknadsbehandling(
        sakId: UUID,
        søknadsbehandlingId: SøknadsbehandlingId,
    ): Either<FeilVedHentingAvGjeldendeVedtaksdataForPeriode, Pair<Periode, GrunnlagsdataOgVilkårsvurderinger>> {
        val sak = sakService.hentSak(sakId)
            .getOrElse { throw IllegalStateException("Fant ikke sak $sakId ved henting av gjeldende vedtaksdata for tidligere perioder") }

        return sak.hentSisteInnvilgetSøknadsbehandlingGrunnlagFiltrerVekkSøknadsbehandling(
            søknadsbehandlingId = søknadsbehandlingId,
            clock = clock,
        )
    }

    private inline fun <reified T> hentKanOppdaterePeriodeGrunnlagVilkår(
        søknadsbehandlingRepo: SøknadsbehandlingRepo,
        søknadsbehandlingId: SøknadsbehandlingId,
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

    private fun Søknadsbehandling.hentSkattegrunnlagForEps(
        saksbehandler: NavIdentBruker.Saksbehandler,
        samletSkattegrunnlag: (Fnr, NavIdentBruker.Saksbehandler) -> Skattegrunnlag,
    ): Skattegrunnlag? = if (grunnlagsdata.bosituasjon.harEPS()) {
        samletSkattegrunnlag(grunnlagsdata.bosituasjon.singleFullstendigEpsOrNull()!!.fnr, saksbehandler)
    } else {
        null
    }
}
