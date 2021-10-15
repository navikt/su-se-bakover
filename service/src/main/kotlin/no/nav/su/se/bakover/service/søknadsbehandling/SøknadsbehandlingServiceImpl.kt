package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.singleOrThrow
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInneforBehandlingsperioden
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.KunneIkkeLeggeTilFradragsgrunnlag.PeriodeMangler
import no.nav.su.se.bakover.domain.søknadsbehandling.forsøkStatusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.hentSøknadstypeFor
import no.nav.su.se.bakover.domain.søknadsbehandling.medFritekstTilBrev
import no.nav.su.se.bakover.domain.søknadsbehandling.statusovergang
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
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
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingRequest
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

internal class SøknadsbehandlingServiceImpl(
    private val søknadService: SøknadService,
    private val søknadRepo: SøknadRepo,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val behandlingMetrics: BehandlingMetrics,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val brevService: BrevService,
    private val opprettVedtakssnapshotService: OpprettVedtakssnapshotService,
    private val clock: Clock,
    private val vedtakRepo: VedtakRepo,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val vilkårsvurderingService: VilkårsvurderingService,
    private val grunnlagService: GrunnlagService,
    private val sakService: SakService,
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

        val åpneSøknadsbehandlinger = søknadsbehandlingRepo.hentForSak(søknad.sakId).filterNot { it.erIverksatt }
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

    override fun vilkårsvurder(request: SøknadsbehandlingService.VilkårsvurderRequest): Either<SøknadsbehandlingService.KunneIkkeVilkårsvurdere, Søknadsbehandling.Vilkårsvurdert> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FantIkkeBehandling.left()

        request.behandlingsinformasjon.formue?.epsVerdier?.let {
            when (søknadsbehandling.grunnlagsdata.bosituasjon.firstOrNull()) {
                is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen,
                is Grunnlag.Bosituasjon.Fullstendig.Enslig,
                is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps,
                null,
                -> {
                    return SøknadsbehandlingService.KunneIkkeVilkårsvurdere.HarIkkeEktefelle.left()
                }
                else -> {
                }
            }
        }

        return statusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilVilkårsvurdert(request.behandlingsinformasjon),
        ).let { vilkårsvurdert ->
            søknadsbehandlingRepo.lagre(vilkårsvurdert)
            vilkårsvurdert.right()
        }
    }

    override fun beregn(request: SøknadsbehandlingService.BeregnRequest): Either<KunneIkkeBeregne, Søknadsbehandling.Beregnet> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeBeregne.FantIkkeBehandling.left()

        return statusovergang(
            søknadsbehandling = søknadsbehandling,
            statusovergang = Statusovergang.TilBeregnet {
                BeregningStrategyFactory().beregn(
                    grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger(
                        grunnlagsdata = søknadsbehandling.grunnlagsdata,
                        vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger,
                    ),
                    beregningsPeriode = søknadsbehandling.periode, begrunnelse = request.begrunnelse,
                )
            },
        ).let {
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

        val søknadstype =
            søknadsbehandlingRepo.hentForSak(søknadsbehandling.sakId).hentSøknadstypeFor(søknadsbehandling.id).getOrHandle {
                return SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeAvgjøreOmFørstegangEllerNyPeriode.left()
            }

        val nyOppgaveId: OppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.AttesterSøknadsbehandling(
                søknadId = søknadsbehandling.søknad.id,
                aktørId = aktørId,
                søknadstype = søknadstype,
                // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
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
            val søknadstype =
                søknadsbehandlingRepo.hentForSak(søknadsbehandling.sakId).hentSøknadstypeFor(søknadsbehandling.id)
            val nyOppgaveId = oppgaveService.opprettOppgave(
                OppgaveConfig.NySøknad(
                    journalpostId = journalpostId,
                    søknadId = underkjent.søknad.id,
                    søknadstype = søknadstype.getOrHandle {
                        return SøknadsbehandlingService.KunneIkkeUnderkjenne.KunneIkkeAvgjøreOmFørstegangEllerNyPeriode.left()
                    },
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
            ) {
                utbetalingService.utbetal(
                    sakId = it.sakId,
                    attestant = request.attestering.attestant,
                    beregning = it.beregning,
                    simulering = it.simulering,
                    uføregrunnlag = it.vilkårsvurderinger.uføre.grunnlag,
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

                    log.info("Iverksatt innvilgelse for behandling ${iverksattBehandling.id}")
                    opprettVedtakssnapshotService.opprettVedtak(
                        vedtakssnapshot = Vedtakssnapshot.Innvilgelse.createFromBehandling(
                            iverksattBehandling,
                            utbetaling!!,
                        ),
                    )
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

                    val dokument = lagDokumentForDistribusjon(vedtak)
                        .getOrHandle { return it.left() }

                    // TODO jm: skriker etter en transaksjon
                    // TODO jm: sjekk om vi allerede har distribuert?
                    søknadsbehandlingRepo.lagre(iverksattBehandling)
                    vedtakRepo.lagre(vedtak)
                    brevService.lagreDokument(dokument)

                    log.info("Iverksatt avslag for behandling: ${iverksattBehandling.id}, vedtak: ${vedtak.id}")
                    opprettVedtakssnapshotService.opprettVedtak(
                        vedtakssnapshot = Vedtakssnapshot.Avslag.createFromBehandling(
                            søknadsbehandling = iverksattBehandling,
                            avslagsgrunner = iverksattBehandling.avslagsgrunner,
                        ),
                    )
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

    private fun lagBrevRequestVisitor() =
        LagBrevRequestVisitor(
            hentPerson = { fnr ->
                personService.hentPerson(fnr)
                    .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson }
            },
            hentNavn = { ident ->
                hentNavnForNavIdent(ident)
                    .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant }
            },
            hentGjeldendeUtbetaling = { sakId, forDato ->
                utbetalingService.hentGjeldendeUtbetaling(sakId, forDato)
                    .bimap(
                        { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling },
                        { it.beløp },
                    )
            },
            clock = clock,
        )

    override fun brev(request: SøknadsbehandlingService.BrevRequest): Either<SøknadsbehandlingService.KunneIkkeLageBrev, ByteArray> {
        val visitor = lagBrevRequestVisitor().apply {
            val behandling = when (request) {
                is SøknadsbehandlingService.BrevRequest.MedFritekst ->
                    request.behandling.medFritekstTilBrev(request.fritekst)
                is SøknadsbehandlingService.BrevRequest.UtenFritekst ->
                    request.behandling
            }

            behandling.accept(this)
        }

        return visitor.brevRequest
            .mapLeft {
                when (it) {
                    LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> {
                        SøknadsbehandlingService.KunneIkkeLageBrev.FikkIkkeHentetSaksbehandlerEllerAttestant
                    }
                    LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson -> {
                        SøknadsbehandlingService.KunneIkkeLageBrev.FantIkkePerson
                    }
                    LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling -> {
                        SøknadsbehandlingService.KunneIkkeLageBrev.KunneIkkeFinneGjeldendeUtbetaling
                    }
                }
            }.flatMap {
                brevService.lagBrev(it)
                    .mapLeft { SøknadsbehandlingService.KunneIkkeLageBrev.KunneIkkeLagePDF }
            }
    }

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<MicrosoftGraphApiOppslagFeil, String> {
        return microsoftGraphApiClient.hentNavnForNavIdent(navIdent)
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
            ),
        ).mapLeft {
            SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereStønadsperiode(it)
        }.map {
            søknadsbehandlingRepo.lagre(it)
            vilkårsvurderingService.lagre(
                behandlingId = it.id,
                vilkårsvurderinger = it.vilkårsvurderinger,
            )
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

    override fun leggTilUføregrunnlag(
        request: LeggTilUførevurderingRequest,
    ): Either<KunneIkkeLeggeTilGrunnlag, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling.left()

        if (søknadsbehandling is Søknadsbehandling.Iverksatt || søknadsbehandling is Søknadsbehandling.TilAttestering)
            return KunneIkkeLeggeTilGrunnlag.UgyldigTilstand(
                søknadsbehandling::class,
                Søknadsbehandling.Vilkårsvurdert::class,
            ).left()

        val vilkår = request.toVilkår(søknadsbehandling.periode, clock).getOrHandle {
            return when (it) {
                LeggTilUførevurderingRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> KunneIkkeLeggeTilGrunnlag.UføregradOgForventetInntektMangler.left()
                LeggTilUførevurderingRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> KunneIkkeLeggeTilGrunnlag.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                LeggTilUførevurderingRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> KunneIkkeLeggeTilGrunnlag.OverlappendeVurderingsperioder.left()
                LeggTilUførevurderingRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> KunneIkkeLeggeTilGrunnlag.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden.left()
            }
        }
        // TODO midliertidig til behandlingsinformasjon er borte
        val grunnlag = (vilkår as? Vilkår.Uførhet.Vurdert)?.grunnlag?.firstOrNull()
        return vilkårsvurder(
            SøknadsbehandlingService.VilkårsvurderRequest(
                behandlingId = søknadsbehandling.id,
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon.copy(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        status = when (vilkår.resultat) {
                            Resultat.Avslag -> Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt
                            Resultat.Innvilget -> Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt
                            Resultat.Uavklart -> Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling
                        },
                        uføregrad = grunnlag?.uføregrad?.value,
                        forventetInntekt = grunnlag?.forventetInntekt,
                        begrunnelse = request.begrunnelse,
                    ),
                ),
            ),
        ).mapLeft {
            KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling
        }.map {
            // TODO jah: Legg til Søknadsbehandling.leggTilUføre(...) som for Revurdering og persister Søknadsbehandlingen som returnerers. Da slipper man og det ekstra hent(...) kallet.
            vilkårsvurderingService.lagre(
                it.id,
                Vilkårsvurderinger(uføre = vilkår),
            )
            søknadsbehandlingRepo.hent(søknadsbehandling.id)!!
        }
    }

    override fun leggTilBosituasjonEpsgrunnlag(request: LeggTilBosituasjonEpsRequest): Either<KunneIkkeLeggeTilBosituasjonEpsGrunnlag, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling.left()

        if (søknadsbehandling is Søknadsbehandling.Iverksatt || søknadsbehandling is Søknadsbehandling.TilAttestering)
            return KunneIkkeLeggeTilBosituasjonEpsGrunnlag.UgyldigTilstand(
                søknadsbehandling::class,
                Søknadsbehandling.Vilkårsvurdert::class,
            ).left()

        val bosituasjon = request.toBosituasjon(søknadsbehandling.periode, clock) {
            personService.hentPerson(it)
        }.getOrHandle { return it.left() }

        return vilkårsvurder(
            SøknadsbehandlingService.VilkårsvurderRequest(
                behandlingId = søknadsbehandling.id,
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon.oppdaterBosituasjonOgEktefelle(
                    bosituasjon = bosituasjon,
                ) {
                    personService.hentPerson(it)
                }.getOrHandle { return KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl.left() },
            ),
        ).mapLeft {
            return KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling.left()
        }.map {
            // TODO jah: Legg til Søknadsbehandling.leggTilBosituasjonEpsgrunnlag(...) som for Revurdering og persister Søknadsbehandlingen som returnerers. Da slipper man og det ekstra hent(...) kallet.
            grunnlagService.lagreBosituasjongrunnlag(behandlingId = request.behandlingId, listOf(bosituasjon))
            return when (it) {
                is Søknadsbehandling.Vilkårsvurdert.Avslag -> it.copy(
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        bosituasjon = listOf(
                            bosituasjon,
                        ),
                        fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
                    ).getOrHandle {
                        return KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KunneIkkeEndreBosituasjonEpsGrunnlag(it).left()
                    },
                )
                is Søknadsbehandling.Vilkårsvurdert.Innvilget -> it.copy(
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        bosituasjon = listOf(bosituasjon),
                        fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
                    ).getOrHandle {
                        return KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KunneIkkeEndreBosituasjonEpsGrunnlag(it).left()
                    },
                )
                is Søknadsbehandling.Vilkårsvurdert.Uavklart -> it.copy(
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        bosituasjon = listOf(bosituasjon),
                        fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
                    ).getOrHandle {
                        return KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KunneIkkeEndreBosituasjonEpsGrunnlag(it).left()
                    },
                )
            }.right()
        }
    }

    override fun fullførBosituasjongrunnlag(request: FullførBosituasjonRequest): Either<KunneIkkeFullføreBosituasjonGrunnlag, Søknadsbehandling> {
        val søknadsbehandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling.left()

        if (søknadsbehandling is Søknadsbehandling.Iverksatt || søknadsbehandling is Søknadsbehandling.TilAttestering)
            return KunneIkkeFullføreBosituasjonGrunnlag.UgyldigTilstand(
                søknadsbehandling::class,
                Søknadsbehandling.Vilkårsvurdert::class,
            ).left()

        val bosituasjon =
            request.toBosituasjon(søknadsbehandling.grunnlagsdata.bosituasjon.singleOrThrow(), clock).getOrHandle {
                return KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeLagreBosituasjon.left()
            }

        return vilkårsvurder(
            SøknadsbehandlingService.VilkårsvurderRequest(
                behandlingId = søknadsbehandling.id,
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon.oppdaterBosituasjonOgEktefelle(
                    bosituasjon = bosituasjon,
                ) {
                    personService.hentPerson(it)
                }.getOrHandle { return KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeHentePersonIPdl.left() },
            ),
        ).mapLeft {
            return KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling.left()
        }.map {
            // TODO jah: Legg til Søknadsbehandling.fullførBosituasjongrunnlag(...) som for Revurdering og persister Søknadsbehandlingen som returnerers. Da slipper man og det ekstra hent(...) kallet.
            grunnlagService.lagreBosituasjongrunnlag(behandlingId = request.behandlingId, listOf(bosituasjon))
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

    private fun lagDokumentForDistribusjon(vedtak: Vedtak): Either<KunneIkkeIverksette.KunneIkkeGenerereVedtaksbrev, Dokument.MedMetadata> =
        lagBrevRequestVisitor()
            .let { visitor ->
                vedtak.accept(visitor)
                visitor.brevRequest
            }
            .mapLeft { KunneIkkeIverksette.KunneIkkeGenerereVedtaksbrev }
            .flatMap { req ->
                req.tilDokument {
                    brevService.lagBrev(req)
                        .mapLeft { LagBrevRequest.KunneIkkeGenererePdf }
                }.mapLeft { KunneIkkeIverksette.KunneIkkeGenerereVedtaksbrev }
            }
            .map {
                it.leggTilMetadata(
                    Dokument.Metadata(
                        sakId = vedtak.behandling.sakId,
                        søknadId = null,
                        vedtakId = vedtak.id,
                        revurderingId = null,
                        bestillBrev = true,
                    ),
                )
            }

    override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling> {
        val behandling = søknadsbehandlingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        val oppdatertBehandling = behandling.leggTilFradragsgrunnlag(request.fradragsgrunnlag).getOrHandle {
            return when (it) {
                GrunnlagetMåVæreInneforBehandlingsperioden -> KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden.left()
                IkkeLovÅLeggeTilFradragIDenneStatusen -> KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                    fra = behandling::class,
                    til = Søknadsbehandling.Vilkårsvurdert.Innvilget::class,
                ).left()
                PeriodeMangler -> KunneIkkeLeggeTilFradragsgrunnlag.PeriodeMangler.left()
                is Søknadsbehandling.KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag -> KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(
                    it.feil,
                ).left()
            }
        }

        grunnlagService.lagreFradragsgrunnlag(behandling.id, request.fradragsgrunnlag)
        søknadsbehandlingRepo.lagre(oppdatertBehandling)

        return oppdatertBehandling.right()
    }

    override fun lukk(lukketSøknadbehandling: LukketSøknadsbehandling, sessionContext: SessionContext) {
        søknadsbehandlingRepo.lagre(lukketSøknadbehandling, sessionContext)
    }

    override fun lagre(avslag: AvslagManglendeDokumentasjon) {
        return søknadsbehandlingRepo.lagreAvslagManglendeDokumentasjon(avslag)
    }
}
