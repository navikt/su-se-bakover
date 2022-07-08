package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IdentifiserRevurderingsopphørSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.revurdering.VurderOmVilkårGirOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.erKlarForAttestering
import no.nav.su.se.bakover.domain.revurdering.harSendtForhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.medFritekst
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.Event.Statistikk.RevurderingStatistikk.RevurderingAvsluttet
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggetilLovligOppholdVilkår
import no.nav.su.se.bakover.service.vilkår.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.util.UUID

internal class RevurderingServiceImpl(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val identClient: IdentClient,
    private val brevService: BrevService,
    private val clock: Clock,
    private val vedtakRepo: VedtakRepo,
    private val vedtakService: VedtakService,
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val sessionFactory: SessionFactory,
    private val formuegrenserFactory: FormuegrenserFactory,
    private val sakService: SakService,
    private val avkortingsvarselRepo: AvkortingsvarselRepo,
    private val toggleService: ToggleService,
    private val tilbakekrevingService: TilbakekrevingService,
    private val satsFactory: SatsFactory,
) : RevurderingService {
    private val stansAvYtelseService = StansAvYtelseService(
        utbetalingService = utbetalingService,
        revurderingRepo = revurderingRepo,
        vedtakService = vedtakService,
        sakService = sakService,
        clock = clock,
    )

    private val gjenopptakAvYtelseService = GjenopptakAvYtelseService(
        utbetalingService = utbetalingService,
        revurderingRepo = revurderingRepo,
        clock = clock,
        vedtakRepo = vedtakRepo,
        vedtakService = vedtakService,
        sakService = sakService,
    )

    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
        gjenopptakAvYtelseService.addObserver(observer)
        stansAvYtelseService.addObserver(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    override fun hentRevurdering(revurderingId: UUID): AbstraktRevurdering? {
        return revurderingRepo.hent(revurderingId)
    }

    override fun stansAvYtelse(
        request: StansYtelseRequest,
    ): Either<KunneIkkeStanseYtelse, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
        return stansAvYtelseService.stansAvYtelse(request)
    }

    override fun iverksettStansAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteStansYtelse, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
        return stansAvYtelseService.iverksettStansAvYtelse(revurderingId, attestant)
    }

    override fun gjenopptaYtelse(request: GjenopptaYtelseRequest): Either<KunneIkkeGjenopptaYtelse, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
        return gjenopptakAvYtelseService.gjenopptaYtelse(request)
    }

    override fun iverksettGjenopptakAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteGjenopptakAvYtelse, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
        return gjenopptakAvYtelseService.iverksettGjenopptakAvYtelse(revurderingId, attestant)
    }

    override fun opprettRevurdering(
        opprettRevurderingRequest: OpprettRevurderingRequest,
    ): Either<KunneIkkeOppretteRevurdering, OpprettetRevurdering> {
        val revurderingsårsak = opprettRevurderingRequest.revurderingsårsak.getOrHandle {
            return when (it) {
                Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigBegrunnelse -> KunneIkkeOppretteRevurdering.UgyldigBegrunnelse
                Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak -> KunneIkkeOppretteRevurdering.UgyldigÅrsak
            }.left()
        }
        val informasjonSomRevurderes =
            InformasjonSomRevurderes.tryCreate(opprettRevurderingRequest.informasjonSomRevurderes)
                .getOrHandle { return KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left() }

        val gjeldendeVedtaksdata: GjeldendeVedtaksdata = vedtakService.kopierGjeldendeVedtaksdata(
            sakId = opprettRevurderingRequest.sakId,
            fraOgMed = opprettRevurderingRequest.fraOgMed,
        ).getOrHandle {
            return when (it) {
                KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak -> KunneIkkeOppretteRevurdering.FantIkkeSak
                KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak -> KunneIkkeOppretteRevurdering.FantIngenVedtakSomKanRevurderes
                is KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode -> KunneIkkeOppretteRevurdering.UgyldigPeriode(it.cause)
            }.left()
        }.also {
            if (!it.tidslinjeForVedtakErSammenhengende()) return KunneIkkeOppretteRevurdering.TidslinjeForVedtakErIkkeKontinuerlig.left()
        }

        when (val r = VurderOmVilkårGirOpphørVedRevurdering(gjeldendeVedtaksdata.vilkårsvurderinger).resultat) {
            is OpphørVedRevurdering.Ja -> {
                if (!informasjonSomRevurderes.harValgtFormue() && r.opphørsgrunner.contains(Opphørsgrunn.FORMUE)) {
                    return KunneIkkeOppretteRevurdering.FormueSomFørerTilOpphørMåRevurderes.left()
                }
                if (!informasjonSomRevurderes.harValgtUtenlandsopphold() && r.opphørsgrunner.contains(Opphørsgrunn.UTENLANDSOPPHOLD)) {
                    return KunneIkkeOppretteRevurdering.UtenlandsoppholdSomFørerTilOpphørMåRevurderes.left()
                }
            }
            is OpphørVedRevurdering.Nei -> {
                // noop
            }
        }

        val gjeldendeVedtakPåFraOgMedDato =
            gjeldendeVedtaksdata.gjeldendeVedtakPåDato(opprettRevurderingRequest.fraOgMed)
                ?: return KunneIkkeOppretteRevurdering.FantIngenVedtakSomKanRevurderes.left()

        val aktørId = personService.hentAktørId(gjeldendeVedtakPåFraOgMedDato.behandling.fnr).getOrElse {
            log.error("Fant ikke aktør-id")
            return KunneIkkeOppretteRevurdering.FantIkkeAktørId.left()
        }

        val uteståendeAvkorting = hentUteståendeAvkorting(opprettRevurderingRequest.sakId).let {
            kontrollerPeriodeForUteståendeAvkorting(gjeldendeVedtaksdata.periodeFørsteTilOgMedSeneste(), it)
                .getOrHandle { feil -> return feil.left() }
        }

        // Oppgaven skal egentligen ikke opprettes her. Den burde egentligen komma utifra melding av endring, som skal føres til revurdering.
        return oppgaveService.opprettOppgave(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = gjeldendeVedtakPåFraOgMedDato.behandling.saksnummer,
                aktørId = aktørId,
                tilordnetRessurs = null,
                clock = clock,
            ),
        ).mapLeft {
            KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave
        }.map { oppgaveId ->
            OpprettetRevurdering(
                periode = gjeldendeVedtaksdata.vedtaksperioder().minsteAntallSammenhengendePerioder().single(),
                opprettet = Tidspunkt.now(clock),
                tilRevurdering = gjeldendeVedtakPåFraOgMedDato.id,
                saksbehandler = opprettRevurderingRequest.saksbehandler,
                oppgaveId = oppgaveId,
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = if (revurderingsårsak.årsak == REGULER_GRUNNBELØP) Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles else null,
                grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = uteståendeAvkorting,
                sakinfo = gjeldendeVedtakPåFraOgMedDato.sakinfo(),
            ).also {
                revurderingRepo.lagre(it)

                observers.forEach { observer ->
                    observer.handle(
                        Event.Statistikk.RevurderingStatistikk.RevurderingOpprettet(
                            it,
                        ),
                    )
                }
            }
        }
    }

    private fun hentUteståendeAvkorting(sakId: UUID): AvkortingVedRevurdering.Uhåndtert {
        // TODO jah: Bør flyttes til sak
        return when (val utestående = avkortingsvarselRepo.hentUtestående(sakId)) {
            is Avkortingsvarsel.Ingen -> {
                AvkortingVedRevurdering.Uhåndtert.IngenUtestående
            }
            is Avkortingsvarsel.Utenlandsopphold.Annullert -> {
                AvkortingVedRevurdering.Uhåndtert.IngenUtestående
            }
            is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
                AvkortingVedRevurdering.Uhåndtert.IngenUtestående
            }
            is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
                AvkortingVedRevurdering.Uhåndtert.IngenUtestående
            }
            is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
                AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(utestående)
            }
        }
    }

    private fun kontrollerPeriodeForUteståendeAvkorting(
        revurderingsperiode: Periode,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
    ): Either<KunneIkkeOppretteRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode, AvkortingVedRevurdering.Uhåndtert> {
        return when (avkorting) {
            is AvkortingVedRevurdering.Uhåndtert.IngenUtestående -> {
                avkorting.right()
            }
            is AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere -> {
                throw IllegalStateException("Denne situasjone kan ikke oppstå")
            }
            is AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> {
                if (!revurderingsperiode.inneholder(avkorting.avkortingsvarsel.periode())) {
                    return KunneIkkeOppretteRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
                        periode = avkorting.avkortingsvarsel.periode(),
                    ).left()
                } else {
                    avkorting.right()
                }
            }
        }
    }

    override fun leggTilUførevilkår(
        request: LeggTilUførevurderingerRequest,
    ): Either<KunneIkkeLeggeTilUføreVilkår, RevurderingOgFeilmeldingerResponse> {
        val revurdering = hent(request.behandlingId)
            .getOrHandle { return KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling.left() }

        val uførevilkår = request.toVilkår(
            behandlingsperiode = revurdering.periode,
            clock = clock,
        ).getOrHandle {
            return KunneIkkeLeggeTilUføreVilkår.UgyldigInput(it).left()
        }
        return revurdering.oppdaterUføreOgMarkerSomVurdert(uførevilkår).mapLeft {
            KunneIkkeLeggeTilUføreVilkår.UgyldigTilstand(fra = it.fra, til = it.til)
        }.map {
            revurderingRepo.lagre(it)
            identifiserFeilOgLagResponse(it)
        }
    }

    override fun leggTilUtenlandsopphold(
        request: LeggTilFlereUtenlandsoppholdRequest,
    ): Either<KunneIkkeLeggeTilUtenlandsopphold, RevurderingOgFeilmeldingerResponse> {
        val revurdering = hent(request.behandlingId)
            .getOrHandle { return KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left() }

        val utenlandsoppholdVilkår = request.tilVilkår(clock).getOrHandle {
            return it.tilService()
        }

        return revurdering.oppdaterUtenlandsoppholdOgMarkerSomVurdert(utenlandsoppholdVilkår)
            .mapLeft {
                it.tilService()
            }.map {
                revurderingRepo.lagre(it)
                identifiserFeilOgLagResponse(it)
            }
    }

    private fun LeggTilFlereUtenlandsoppholdRequest.UgyldigUtenlandsopphold.tilService(): Either<KunneIkkeLeggeTilUtenlandsopphold, Nothing> {
        return when (this) {
            LeggTilFlereUtenlandsoppholdRequest.UgyldigUtenlandsopphold.OverlappendeVurderingsperioder -> {
                KunneIkkeLeggeTilUtenlandsopphold.OverlappendeVurderingsperioder.left()
            }
            LeggTilFlereUtenlandsoppholdRequest.UgyldigUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                KunneIkkeLeggeTilUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }
        }
    }

    private fun Revurdering.KunneIkkeLeggeTilUtenlandsopphold.tilService(): KunneIkkeLeggeTilUtenlandsopphold {
        return when (this) {
            is Revurdering.KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand -> {
                KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(fra = this.fra, til = this.til)
            }
            Revurdering.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode -> {
                KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode
            }
            Revurdering.KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat -> {
                KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat
            }
            Revurdering.KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden -> {
                KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden
            }
        }
    }

    override fun leggTilOpplysningspliktVilkår(request: LeggTilOpplysningspliktRequest.Revurdering): Either<KunneIkkeLeggeTilOpplysningsplikt, RevurderingOgFeilmeldingerResponse> {
        return hent(request.behandlingId).mapLeft {
            KunneIkkeLeggeTilOpplysningsplikt.FantIkkeBehandling
        }.flatMap { revurdering ->
            revurdering.oppdaterOpplysningspliktOgMarkerSomVurdert(request.vilkår)
                .mapLeft {
                    KunneIkkeLeggeTilOpplysningsplikt.Revurdering(it)
                }
                .map {
                    revurderingRepo.lagre(it)
                    identifiserFeilOgLagResponse(it)
                }
        }
    }

    override fun leggTilPensjonsVilkår(request: LeggTilPensjonsVilkårRequest): Either<KunneIkkeLeggeTilPensjonsVilkår, RevurderingOgFeilmeldingerResponse> {
        return hent(request.behandlingId).mapLeft {
            KunneIkkeLeggeTilPensjonsVilkår.FantIkkeBehandling
        }.flatMap { revurdering ->
            revurdering.oppdaterPensjonsvilkårOgMarkerSomVurdert(request.vilkår)
                .mapLeft {
                    KunneIkkeLeggeTilPensjonsVilkår.Revurdering(it)
                }
                .map {
                    revurderingRepo.lagre(it)
                    identifiserFeilOgLagResponse(it)
                }
        }
    }

    override fun leggTilLovligOppholdVilkår(request: LeggTilLovligOppholdRequest): Either<KunneIkkeLeggetilLovligOppholdVilkår, RevurderingOgFeilmeldingerResponse> {
        val revurdering =
            hent(request.behandlingId).getOrHandle { return KunneIkkeLeggetilLovligOppholdVilkår.FantIkkeBehandling.left() }

        val vilkår = request.toVilkår(clock)
            .getOrHandle { return KunneIkkeLeggetilLovligOppholdVilkår.UgyldigLovligOppholdVilkår(it).left() }

        return revurdering.oppdaterLovligOppholdOgMarkerSomVurdert(vilkår).mapLeft {
            KunneIkkeLeggetilLovligOppholdVilkår.FeilVedSøknadsbehandling(it)
        }.map {
            revurderingRepo.lagre(it)
            identifiserFeilOgLagResponse(it)
        }
    }

    override fun leggTilFlyktningVilkår(request: LeggTilFlyktningVilkårRequest): Either<KunneIkkeLeggeTilFlyktningVilkår, RevurderingOgFeilmeldingerResponse> {
        return hent(request.behandlingId).mapLeft {
            KunneIkkeLeggeTilFlyktningVilkår.FantIkkeBehandling
        }.flatMap { revurdering ->
            revurdering.oppdaterFlyktningvilkårOgMarkerSomVurdert(request.vilkår)
                .mapLeft {
                    KunneIkkeLeggeTilFlyktningVilkår.Revurdering(it)
                }
                .map {
                    revurderingRepo.lagre(it)
                    identifiserFeilOgLagResponse(it)
                }
        }
    }

    override fun leggTilFastOppholdINorgeVilkår(request: LeggTilFastOppholdINorgeRequest): Either<KunneIkkeLeggeFastOppholdINorgeVilkår, RevurderingOgFeilmeldingerResponse> {
        return hent(request.behandlingId).mapLeft {
            KunneIkkeLeggeFastOppholdINorgeVilkår.FantIkkeBehandling
        }.flatMap { revurdering ->
            revurdering.oppdaterFastOpphodINorgeOgMarkerSomVurdert(request.vilkår)
                .mapLeft {
                    KunneIkkeLeggeFastOppholdINorgeVilkår.Revurdering(it)
                }
                .map {
                    revurderingRepo.lagre(it)
                    identifiserFeilOgLagResponse(it)
                }
        }
    }

    override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, RevurderingOgFeilmeldingerResponse> {
        val revurdering = hent(request.behandlingId)
            .getOrHandle { return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left() }

        return revurdering.oppdaterFradragOgMarkerSomVurdert(request.fradragsgrunnlag).mapLeft {
            when (it) {
                is Revurdering.KunneIkkeLeggeTilFradrag.Valideringsfeil -> KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(
                    it.feil,
                )
                is Revurdering.KunneIkkeLeggeTilFradrag.UgyldigTilstand -> KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                    fra = it.fra,
                    til = it.til,
                )
            }
        }.map {
            revurderingRepo.lagre(it)
            identifiserFeilOgLagResponse(it)
        }
    }

    override fun leggTilBosituasjongrunnlag(request: LeggTilBosituasjonerRequest): Either<KunneIkkeLeggeTilBosituasjongrunnlag, RevurderingOgFeilmeldingerResponse> {
        val revurdering = hent(request.revurderingId)
            .getOrHandle { return KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling.left() }

        val bosituasjongrunnlag = request.toDomain(
            clock = clock,
        ) {
            personService.hentPerson(it)
        }.getOrHandle {
            return it.left()
        }

        return revurdering.oppdaterBosituasjonOgMarkerSomVurdert(bosituasjongrunnlag)
            .mapLeft {
                KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeLeggeTilBosituasjon(it)
            }.map {
                revurderingRepo.lagre(it)
                identifiserFeilOgLagResponse(it)
            }
    }

    override fun leggTilFormuegrunnlag(
        request: LeggTilFormuevilkårRequest,
    ): Either<KunneIkkeLeggeTilFormuegrunnlag, RevurderingOgFeilmeldingerResponse> {
        val revurdering = hent(request.behandlingId)
            .getOrHandle { return KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering.left() }

        // TODO("flere_satser mulig å gjøre noe for å unngå casting?")
        @Suppress("UNCHECKED_CAST")
        val bosituasjon = revurdering.grunnlagsdata.bosituasjon as List<Grunnlag.Bosituasjon.Fullstendig>

        val vilkår = request.toDomain(bosituasjon, revurdering.periode, clock, formuegrenserFactory).getOrHandle {
            return KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet(it).left()
        }
        return revurdering.oppdaterFormueOgMarkerSomVurdert(vilkår)
            .mapLeft {
                when (it) {
                    is Revurdering.KunneIkkeLeggeTilFormue.Konsistenssjekk -> {
                        KunneIkkeLeggeTilFormuegrunnlag.Konsistenssjekk(it.feil)
                    }
                    is Revurdering.KunneIkkeLeggeTilFormue.UgyldigTilstand -> {
                        KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand(it.fra, it.til)
                    }
                }
            }.map {
                revurderingRepo.lagre(it)
                identifiserFeilOgLagResponse(it)
            }
    }

    private fun identifiserFeilOgLagResponse(revurdering: Revurdering): RevurderingOgFeilmeldingerResponse {
        val sak = sakService.hentSakForRevurdering(revurderingId = revurdering.id)
        val gjeldendeMånedsberegninger = sak.hentGjeldendeMånedsberegninger(
            periode = revurdering.periode,
            clock = clock,
        )
        val feilmeldinger = when (revurdering) {
            is OpprettetRevurdering -> {
                identifiserUtfallSomIkkeStøttes(
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    periode = revurdering.periode,
                ).swap().getOrElse { emptySet() }
            }
            is BeregnetRevurdering -> {
                identifiserUtfallSomIkkeStøttes(
                    revurderingsperiode = revurdering.periode,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    gjeldendeMånedsberegninger = gjeldendeMånedsberegninger,
                    nyBeregning = revurdering.beregning,
                ).swap().getOrElse { emptySet() }
            }
            is SimulertRevurdering -> {
                identifiserUtfallSomIkkeStøttes(
                    revurderingsperiode = revurdering.periode,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    gjeldendeMånedsberegninger = gjeldendeMånedsberegninger,
                    nyBeregning = revurdering.beregning,
                ).swap().getOrElse { emptySet() }
            }
            else -> throw IllegalStateException("Skal ikke kunne lage en RevurderingOgFeilmeldingerResponse fra ${revurdering::class}")
        }

        return RevurderingOgFeilmeldingerResponse(revurdering, feilmeldinger.toList())
    }

    override fun oppdaterRevurdering(
        oppdaterRevurderingRequest: OppdaterRevurderingRequest,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        val revurderingsårsak = oppdaterRevurderingRequest.revurderingsårsak.getOrHandle {
            return when (it) {
                Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigBegrunnelse -> KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse
                Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak -> KunneIkkeOppdatereRevurdering.UgyldigÅrsak
            }.left()
        }
        val revurdering = hent(oppdaterRevurderingRequest.revurderingId)
            .getOrHandle { return KunneIkkeOppdatereRevurdering.FantIkkeRevurdering.left() }

        // TODO jah: Flytt sjekker som dette inn i domenet
        if (revurdering.forhåndsvarsel.harSendtForhåndsvarsel()) {
            return KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet.left()
        }

        val informasjonSomRevurderes =
            InformasjonSomRevurderes.tryCreate(oppdaterRevurderingRequest.informasjonSomRevurderes)
                .getOrHandle { return KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left() }

        val gjeldendeVedtaksdata = vedtakService.kopierGjeldendeVedtaksdata(
            sakId = revurdering.sakId,
            fraOgMed = oppdaterRevurderingRequest.fraOgMed,
        ).getOrHandle {
            return when (it) {
                KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak -> KunneIkkeOppdatereRevurdering.FantIkkeSak
                KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak -> KunneIkkeOppdatereRevurdering.FantIngenVedtakSomKanRevurderes
                is KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode -> KunneIkkeOppdatereRevurdering.UgyldigPeriode(
                    it.cause,
                )
            }.left()
        }.also {
            if (!it.tidslinjeForVedtakErSammenhengende()) return KunneIkkeOppdatereRevurdering.TidslinjeForVedtakErIkkeKontinuerlig.left()
        }

        when (val r = VurderOmVilkårGirOpphørVedRevurdering(gjeldendeVedtaksdata.vilkårsvurderinger).resultat) {
            is OpphørVedRevurdering.Ja -> {
                if (!informasjonSomRevurderes.harValgtFormue() && r.opphørsgrunner.contains(Opphørsgrunn.FORMUE)) {
                    return KunneIkkeOppdatereRevurdering.FormueSomFørerTilOpphørMåRevurderes.left()
                }
                if (!informasjonSomRevurderes.harValgtUtenlandsopphold() && r.opphørsgrunner.contains(Opphørsgrunn.UTENLANDSOPPHOLD)) {
                    return KunneIkkeOppdatereRevurdering.UtenlandsoppholdSomFørerTilOpphørMåRevurderes.left()
                }
            }
            is OpphørVedRevurdering.Nei -> {
                // noop
            }
        }

        val gjeldendeVedtakPåFraOgMedDato =
            gjeldendeVedtaksdata.gjeldendeVedtakPåDato(oppdaterRevurderingRequest.fraOgMed)?.id
                ?: return KunneIkkeOppdatereRevurdering.FantIngenVedtakSomKanRevurderes.left()

        val avkorting = hentUteståendeAvkorting(revurdering.sakId).let {
            kontrollerPeriodeForUteståendeAvkorting(gjeldendeVedtaksdata.periodeFørsteTilOgMedSeneste(), it)
                .getOrHandle {
                    return KunneIkkeOppdatereRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
                        periode = it.periode,
                    ).left()
                }
        }

        val periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode()

        return when (revurdering) {
            is OpprettetRevurdering -> revurdering.oppdater(
                periode = periode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                tilRevurdering = gjeldendeVedtakPåFraOgMedDato,
                avkorting = avkorting,
            ).right()
            is BeregnetRevurdering -> revurdering.oppdater(
                periode = periode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                tilRevurdering = gjeldendeVedtakPåFraOgMedDato,
                avkorting = avkorting,
            ).right()
            is SimulertRevurdering -> revurdering.oppdater(
                periode = periode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                tilRevurdering = gjeldendeVedtakPåFraOgMedDato,
                avkorting = avkorting,
            ).right()
            is UnderkjentRevurdering -> revurdering.oppdater(
                periode = periode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                tilRevurdering = gjeldendeVedtakPåFraOgMedDato,
                avkorting = avkorting,
            ).right()
            else -> KunneIkkeOppdatereRevurdering.UgyldigTilstand(
                revurdering::class,
                OpprettetRevurdering::class,
            ).left()
        }.map {
            revurderingRepo.lagre(it)
            it
        }
    }

    override fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, RevurderingOgFeilmeldingerResponse> {
        val sak = sakService.hentSakForRevurdering(revurderingId)
        val originalRevurdering = sak.revurderinger.single { it.id == revurderingId } as Revurdering

        return when (originalRevurdering) {
            is BeregnetRevurdering, is OpprettetRevurdering, is SimulertRevurdering, is UnderkjentRevurdering -> {
                val eksisterendeUtbetalinger = sak.utbetalinger
                val gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = originalRevurdering.periode.fraOgMed,
                    clock = clock,
                ).getOrHandle {
                    throw IllegalStateException("Fant ikke gjeldende vedtaksdata for sak:${originalRevurdering.sakId}")
                }
                val beregnetRevurdering = originalRevurdering.beregn(
                    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                    clock = clock,
                    gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                    satsFactory = satsFactory,
                ).getOrHandle {
                    return when (it) {
                        is Revurdering.KunneIkkeBeregneRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden -> {
                            KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden
                        }
                        is Revurdering.KunneIkkeBeregneRevurdering.UgyldigBeregningsgrunnlag -> {
                            KunneIkkeBeregneOgSimulereRevurdering.UgyldigBeregningsgrunnlag(it.reason)
                        }
                        Revurdering.KunneIkkeBeregneRevurdering.KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps -> {
                            KunneIkkeBeregneOgSimulereRevurdering.KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps
                        }
                        Revurdering.KunneIkkeBeregneRevurdering.AvkortingErUfullstendig -> {
                            KunneIkkeBeregneOgSimulereRevurdering.AvkortingErUfullstendig
                        }
                        Revurdering.KunneIkkeBeregneRevurdering.OpphørAvYtelseSomSkalAvkortes -> {
                            KunneIkkeBeregneOgSimulereRevurdering.OpphørAvYtelseSomSkalAvkortes
                        }
                    }.left()
                }

                val potensielleVarsel = listOf(
                    (
                        !VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
                            eksisterendeUtbetalinger = eksisterendeUtbetalinger.flatMap { it.utbetalingslinjer },
                            nyBeregning = beregnetRevurdering.beregning,
                        ).resultat && !(beregnetRevurdering is BeregnetRevurdering.Opphørt && beregnetRevurdering.opphørSkyldesVilkår())
                        ) to Varselmelding.BeløpsendringUnder10Prosent,
                    gjeldendeVedtaksdata.let { gammel ->
                        (
                            gammel.grunnlagsdata.bosituasjon.any { it.harEPS() } &&
                                beregnetRevurdering.grunnlagsdata.bosituasjon.none { it.harEPS() }
                            ) to Varselmelding.FradragOgFormueForEPSErFjernet
                    },
                )

                when (beregnetRevurdering) {
                    is BeregnetRevurdering.IngenEndring -> {
                        revurderingRepo.lagre(beregnetRevurdering)
                        identifiserFeilOgLagResponse(beregnetRevurdering)
                            .leggTil(potensielleVarsel)
                            .right()
                    }
                    is BeregnetRevurdering.Innvilget -> {
                        utbetalingService.simulerUtbetaling(
                            request = SimulerUtbetalingRequest.NyUtbetaling.Uføre(
                                sakId = beregnetRevurdering.sakId,
                                saksbehandler = saksbehandler,
                                beregning = beregnetRevurdering.beregning,
                                uføregrunnlag = beregnetRevurdering.vilkårsvurderinger.uføreVilkår().fold(
                                    {
                                        TODO("vilkårsvurdering_alder utbetaling av alder ikke implementert")
                                    },
                                    {
                                        it.grunnlag
                                    },
                                ),
                                utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                            ),
                        ).mapLeft {
                            KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(it)
                        }.map {
                            beregnetRevurdering.toSimulert(
                                simulering = it.simulering,
                                clock = clock,
                            ).let { simulert ->
                                revurderingRepo.lagre(simulert)
                                identifiserFeilOgLagResponse(simulert)
                                    .leggTil(potensielleVarsel)
                            }
                        }
                    }
                    is BeregnetRevurdering.Opphørt -> {
                        // TODO er tanken at vi skal oppdatere saksbehandler her? Det kan se ut som vi har tenkt det, men aldri fullført.
                        beregnetRevurdering.toSimulert { sakId, _, opphørsdato ->
                            utbetalingService.simulerOpphør(
                                request = SimulerUtbetalingRequest.Opphør(
                                    sakId = sakId,
                                    saksbehandler = saksbehandler,
                                    opphørsdato = opphørsdato,
                                ),
                            )
                        }.mapLeft { KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(it) }
                            .map { simulert ->
                                revurderingRepo.lagre(simulert)
                                identifiserFeilOgLagResponse(simulert)
                                    .leggTil(potensielleVarsel)
                            }
                    }
                }
            }
            else -> return KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand(
                originalRevurdering::class,
                SimulertRevurdering::class,
            ).left()
        }
    }

    private fun identifiserUtfallSomIkkeStøttes(
        revurderingsperiode: Periode,
        vilkårsvurderinger: Vilkårsvurderinger,
        gjeldendeMånedsberegninger: List<Månedsberegning>,
        nyBeregning: Beregning,
    ) = IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
        revurderingsperiode = revurderingsperiode,
        vilkårsvurderinger = vilkårsvurderinger,
        gjeldendeMånedsberegninger = gjeldendeMånedsberegninger,
        nyBeregning = nyBeregning,
        clock = clock,
    ).resultat

    private fun identifiserUtfallSomIkkeStøttes(
        vilkårsvurderinger: Vilkårsvurderinger,
        periode: Periode,
    ) = IdentifiserRevurderingsopphørSomIkkeStøttes.UtenBeregning(
        vilkårsvurderinger = vilkårsvurderinger,
        periode = periode,
    ).resultat

    override fun lagreOgSendForhåndsvarsel(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        forhåndsvarselhandling: Forhåndsvarselhandling,
        fritekst: String,
    ): Either<KunneIkkeForhåndsvarsle, Revurdering> {
        val revurdering = hent(revurderingId).getOrHandle { return KunneIkkeForhåndsvarsle.FantIkkeRevurdering.left() }
        if (revurdering !is SimulertRevurdering) return KunneIkkeForhåndsvarsle.MåVæreITilstandenSimulert(
            revurdering::class,
        ).left()
        kanSendesTilAttestering(revurdering).getOrHandle {
            return KunneIkkeForhåndsvarsle.Attestering(it).left()
        }
        return when (forhåndsvarselhandling) {
            Forhåndsvarselhandling.INGEN_FORHÅNDSVARSEL -> {
                revurdering.ikkeSendForhåndsvarsel()
                    .mapLeft {
                        KunneIkkeForhåndsvarsle.UgyldigTilstandsovergangForForhåndsvarsling
                    }.tap {
                        revurderingRepo.lagre(it)
                    }
            }
            Forhåndsvarselhandling.FORHÅNDSVARSLE -> {
                hentPersonOgSaksbehandlerNavn(
                    fnr = revurdering.fnr,
                    saksbehandler = saksbehandler,
                ).mapLeft {
                    when (it) {
                        KunneIkkeHentePersonEllerSaksbehandlerNavn.FantIkkePerson -> {
                            KunneIkkeForhåndsvarsle.FantIkkePerson
                        }
                        KunneIkkeHentePersonEllerSaksbehandlerNavn.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> {
                            KunneIkkeForhåndsvarsle.KunneIkkeHenteNavnForSaksbehandler
                        }
                    }
                }.flatMap { (person, saksbehandlerNavn) ->
                    revurdering.lagForhåndsvarsel(
                        person = person,
                        saksbehandlerNavn = saksbehandlerNavn,
                        fritekst = fritekst,
                        clock = clock,
                    ).mapLeft {
                        KunneIkkeForhåndsvarsle.UgyldigTilstandsovergangForForhåndsvarsling
                    }.flatMap { forhåndsvarselBrev ->
                        forhåndsvarselBrev.tilDokument {
                            brevService.lagBrev(it)
                                .mapLeft {
                                    LagBrevRequest.KunneIkkeGenererePdf
                                }
                        }.mapLeft {
                            KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument
                        }.flatMap { dokumentUtenMetadata ->
                            revurdering.markerForhåndsvarselSomSendt()
                                .mapLeft {
                                    KunneIkkeForhåndsvarsle.UgyldigTilstandsovergangForForhåndsvarsling
                                }
                                .flatMap { simulertRevurdering ->
                                    Either.catch {
                                        sessionFactory.withTransactionContext { tx ->
                                            brevService.lagreDokument(
                                                dokument = dokumentUtenMetadata.leggTilMetadata(
                                                    Dokument.Metadata(
                                                        sakId = simulertRevurdering.sakId,
                                                        revurderingId = simulertRevurdering.id,
                                                        bestillBrev = true,
                                                    ),
                                                ),
                                                transactionContext = tx,
                                            )
                                            revurderingRepo.lagre(
                                                revurdering = simulertRevurdering,
                                                transactionContext = tx,
                                            )
                                            prøvÅOppdatereOppgaveEtterViHarSendtForhåndsvarsel(
                                                revurderingId = simulertRevurdering.id,
                                                oppgaveId = simulertRevurdering.oppgaveId,
                                            ).tapLeft {
                                                throw KunneIkkeOppdatereOppgave()
                                            }
                                            log.info("Forhåndsvarsel sendt for revurdering ${simulertRevurdering.id}")
                                            simulertRevurdering
                                        }
                                    }.mapLeft {
                                        if (it is KunneIkkeOppdatereOppgave) {
                                            KunneIkkeForhåndsvarsle.KunneIkkeOppdatereOppgave
                                        } else {
                                            throw it
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    private class KunneIkkeOppdatereOppgave : RuntimeException()

    private fun prøvÅOppdatereOppgaveEtterViHarSendtForhåndsvarsel(
        revurderingId: UUID,
        oppgaveId: OppgaveId,
    ): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit> {
        return oppgaveService.oppdaterOppgave(
            oppgaveId = oppgaveId,
            beskrivelse = "Forhåndsvarsel er sendt.",
        ).tapLeft {
            log.error("Kunne ikke oppdatere oppgave $oppgaveId for revurdering $revurderingId med informasjon om at forhåndsvarsel er sendt")
        }.tap {
            log.info("Oppdatert oppgave $oppgaveId for revurdering $revurderingId  med informasjon om at forhåndsvarsel er sendt")
        }
    }

    override fun lagBrevutkastForForhåndsvarsling(
        revurderingId: UUID,
        fritekst: String,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
        return hent(revurderingId)
            .mapLeft {
                KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering
            }
            .flatMap { revurdering ->
                if (revurdering !is SimulertRevurdering) return KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast.left()

                hentPersonOgSaksbehandlerNavn(
                    fnr = revurdering.fnr,
                    saksbehandler = revurdering.saksbehandler,
                ).mapLeft {
                    when (it) {
                        KunneIkkeHentePersonEllerSaksbehandlerNavn.FantIkkePerson -> {
                            KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson
                        }
                        KunneIkkeHentePersonEllerSaksbehandlerNavn.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> {
                            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                        }
                    }
                }.flatMap { (person, saksbehandlerNavn) ->
                    revurdering.lagForhåndsvarsel(
                        person = person,
                        saksbehandlerNavn = saksbehandlerNavn,
                        fritekst = fritekst,
                        clock = clock,
                    ).mapLeft {
                        KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast
                    }.flatMap {
                        brevService.lagBrev(it).mapLeft {
                            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast
                        }
                    }
                }
            }
    }

    override fun oppdaterTilbakekrevingsbehandling(request: OppdaterTilbakekrevingsbehandlingRequest): Either<KunneIkkeOppdatereTilbakekrevingsbehandling, SimulertRevurdering> {
        val revurdering = hent(request.revurderingId)
            .getOrHandle { return KunneIkkeOppdatereTilbakekrevingsbehandling.FantIkkeRevurdering.left() }

        if (revurdering !is SimulertRevurdering) {
            return KunneIkkeOppdatereTilbakekrevingsbehandling.UgyldigTilstand(fra = revurdering::class).left()
        }

        val ikkeAvgjort = IkkeAvgjort(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = revurdering.sakId,
            revurderingId = revurdering.id,
            periode = revurdering.periode,
        )

        val oppdatert = revurdering.oppdaterTilbakekrevingsbehandling(
            when (request.avgjørelse) {
                OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.TILBAKEKREV -> {
                    ikkeAvgjort.tilbakekrev()
                }
                OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.IKKE_TILBAKEKREV -> {
                    ikkeAvgjort.ikkeTilbakekrev()
                }
            },
        )

        revurderingRepo.lagre(oppdatert)

        return oppdatert.right()
    }

    override fun sendTilAttestering(
        request: SendTilAttesteringRequest,
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering> {
        return hent(request.revurderingId)
            .mapLeft { return KunneIkkeSendeRevurderingTilAttestering.FantIkkeRevurdering.left() }
            .flatMap {
                sendTilAttestering(
                    revurdering = it,
                    saksbehandler = request.saksbehandler,
                    fritekstTilBrev = request.fritekstTilBrev,
                    skalFøreTilBrevutsending = request.skalFøreTilBrevutsending,
                )
            }
    }

    private fun sendTilAttestering(
        revurdering: Revurdering,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekstTilBrev: String,
        skalFøreTilBrevutsending: Boolean,
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering> {
        kanSendesTilAttestering(revurdering).getOrHandle {
            return it.left()
        }

        if (!(revurdering is BeregnetRevurdering.IngenEndring || revurdering.forhåndsvarsel.erKlarForAttestering())) {
            return KunneIkkeSendeRevurderingTilAttestering.ManglerBeslutningPåForhåndsvarsel.left()
        }

        val aktørId = personService.hentAktørId(revurdering.fnr).getOrElse {
            log.error("Fant ikke aktør-id")
            return KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId.left()
        }

        val tilordnetRessurs = revurdering.attesteringer.lastOrNull()?.attestant

        val oppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.AttesterRevurdering(
                saksnummer = revurdering.saksnummer,
                aktørId = aktørId,
                // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
                tilordnetRessurs = tilordnetRessurs,
                clock = clock,
            ),
        ).getOrElse {
            log.error("Kunne ikke opprette Attesteringsoppgave. Avbryter handlingen.")
            return KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave.left()
        }

        oppgaveService.lukkOppgave(revurdering.oppgaveId).mapLeft {
            log.error("Kunne ikke lukke oppgaven med id ${revurdering.oppgaveId}, knyttet til revurderingen. Oppgaven må lukkes manuelt.")
        }

        // TODO endre rekkefølge slik at vi ikke lager/lukker oppgaver før vi har vært innom domenemodellen
        val tilAttestering = when (revurdering) {
            is BeregnetRevurdering.IngenEndring -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
                fritekstTilBrev,
                skalFøreTilBrevutsending,
            )
            is SimulertRevurdering.Innvilget -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
                fritekstTilBrev,
            ).getOrHandle {
                return when (it) {
                    SimulertRevurdering.KunneIkkeSendeInnvilgetRevurderingTilAttestering.ForhåndsvarslingErIkkeFerdigbehandlet -> {
                        KunneIkkeSendeRevurderingTilAttestering.ForhåndsvarslingErIkkeFerdigbehandlet
                    }
                    SimulertRevurdering.KunneIkkeSendeInnvilgetRevurderingTilAttestering.TilbakekrevingsbehandlingErIkkeFullstendig -> {
                        KunneIkkeSendeRevurderingTilAttestering.TilbakekrevingsbehandlingErIkkeFullstendig
                    }
                }.left()
            }
            is SimulertRevurdering.Opphørt -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
                fritekstTilBrev,
            ).getOrHandle {
                return when (it) {
                    SimulertRevurdering.Opphørt.KanIkkeSendeOpphørtRevurderingTilAttestering.ForhåndsvarslingErIkkeFerdigbehandlet -> {
                        KunneIkkeSendeRevurderingTilAttestering.ForhåndsvarslingErIkkeFerdigbehandlet
                    }
                    SimulertRevurdering.Opphørt.KanIkkeSendeOpphørtRevurderingTilAttestering.KanIkkeSendeEnOpphørtGReguleringTilAttestering -> {
                        KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør
                    }
                    SimulertRevurdering.Opphørt.KanIkkeSendeOpphørtRevurderingTilAttestering.TilbakekrevingsbehandlingErIkkeFullstendig -> {
                        KunneIkkeSendeRevurderingTilAttestering.TilbakekrevingsbehandlingErIkkeFullstendig
                    }
                }.left()
            }
            is UnderkjentRevurdering.IngenEndring -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
                fritekstTilBrev,
                skalFøreTilBrevutsending,
            )
            is UnderkjentRevurdering.Opphørt -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
                fritekstTilBrev,
            ).getOrElse {
                return KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør.left()
            }
            is UnderkjentRevurdering.Innvilget -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
                fritekstTilBrev,
            )
            else -> return KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
                revurdering::class,
                RevurderingTilAttestering::class,
            ).left()
        }

        revurderingRepo.lagre(tilAttestering)
        observers.forEach { observer ->
            observer.handle(
                Event.Statistikk.RevurderingStatistikk.RevurderingTilAttestering(
                    tilAttestering,
                ),
            )
        }
        return tilAttestering.right()
    }

    private fun kanSendesTilAttestering(revurdering: Revurdering): Either<KunneIkkeSendeRevurderingTilAttestering, Unit> {
        val sak = sakService.hentSakForRevurdering(revurderingId = revurdering.id)
        val gjeldendeMånedsberegninger = sak.hentGjeldendeMånedsberegninger(
            periode = revurdering.periode,
            clock = clock,
        )

        tilbakekrevingService.hentAvventerKravgrunnlag(revurdering.sakId)
            .ifNotEmpty {
                return KunneIkkeSendeRevurderingTilAttestering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving(
                    revurderingId = this.first().avgjort.revurderingId,
                ).left()
            }

        return when (revurdering) {
            is BeregnetRevurdering.IngenEndring -> {
                identifiserUtfallSomIkkeStøttes(
                    revurderingsperiode = revurdering.periode,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    gjeldendeMånedsberegninger = gjeldendeMånedsberegninger,
                    nyBeregning = revurdering.beregning,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }
            }
            is SimulertRevurdering -> {
                identifiserUtfallSomIkkeStøttes(
                    revurderingsperiode = revurdering.periode,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    gjeldendeMånedsberegninger = gjeldendeMånedsberegninger,
                    nyBeregning = revurdering.beregning,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }
            }
            is UnderkjentRevurdering.Innvilget -> {
                identifiserUtfallSomIkkeStøttes(
                    revurderingsperiode = revurdering.periode,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    gjeldendeMånedsberegninger = gjeldendeMånedsberegninger,
                    nyBeregning = revurdering.beregning,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }
            }
            is UnderkjentRevurdering.Opphørt -> {
                identifiserUtfallSomIkkeStøttes(
                    revurderingsperiode = revurdering.periode,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    gjeldendeMånedsberegninger = gjeldendeMånedsberegninger,
                    nyBeregning = revurdering.beregning,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }
            }
            is UnderkjentRevurdering.IngenEndring -> {
                identifiserUtfallSomIkkeStøttes(
                    revurderingsperiode = revurdering.periode,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    gjeldendeMånedsberegninger = gjeldendeMånedsberegninger,
                    nyBeregning = revurdering.beregning,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }
            }
            else -> KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
                fra = revurdering::class,
                til = RevurderingTilAttestering::class,
            ).left()
        }
    }

    override fun lagBrevutkastForRevurdering(
        revurderingId: UUID,
        fritekst: String?,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
        val revurdering = hent(revurderingId)
            .getOrHandle { return KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering.left() }

        val revurderingMedPotensiellFritekst = if (fritekst != null) {
            revurdering.medFritekst(fritekst)
        } else {
            revurdering
        }

        return brevService.lagDokument(revurderingMedPotensiellFritekst)
            .mapLeft {
                when (it) {
                    KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeFinneGjeldendeUtbetaling
                    KunneIkkeLageDokument.KunneIkkeGenererePDF -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast
                    KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                    KunneIkkeLageDokument.KunneIkkeHentePerson -> KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson
                }
            }
            .map {
                it.generertDokument
            }
    }

    override fun iverksett(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
        val revurdering =
            revurderingRepo.hent(revurderingId) ?: return KunneIkkeIverksetteRevurdering.FantIkkeRevurdering.left()
        if (revurdering !is RevurderingTilAttestering) return KunneIkkeIverksetteRevurdering.UgyldigTilstand(
            revurdering::class,
            IverksattRevurdering::class,
        ).left()

        return when (revurdering) {
            is RevurderingTilAttestering.IngenEndring -> {
                log.error("Revudere til INGEN_ENDRING er ikke lov. SakId: ${revurdering.sakId}")
                KunneIkkeIverksetteRevurdering.IngenEndringErIkkeGyldig.left()
            }
            is RevurderingTilAttestering.Innvilget ->
                revurdering.tilIverksatt(
                    attestant = attestant,
                    hentOpprinneligAvkorting = { avkortingid -> avkortingsvarselRepo.hent(avkortingid) },
                    clock = clock,
                ).mapLeft {
                    when (it) {
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen -> KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarBlittAnnullertAvEnAnnen -> KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen
                        is RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale -> KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(
                            it.utbetalingFeilet,
                        )
                    }
                }.flatMap { iverksattRevurdering ->
                    utbetalingService.verifiserOgSimulerUtbetaling(
                        request = UtbetalRequest.NyUtbetaling(
                            request = SimulerUtbetalingRequest.NyUtbetaling.Uføre(
                                sakId = revurdering.sakId,
                                saksbehandler = attestant,
                                beregning = revurdering.beregning,
                                uføregrunnlag = revurdering.vilkårsvurderinger.uføreVilkår().fold(
                                    {
                                        TODO("vilkårsvurdering_alder utbetaling av alder ikke implementert")
                                    },
                                    {
                                        it.grunnlag
                                    },
                                ),
                                utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                            ),
                            simulering = revurdering.simulering,
                        ),
                    ).mapLeft { KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(it) }
                        .flatMap { utbetaling ->
                            val vedtak = VedtakSomKanRevurderes.from(iverksattRevurdering, utbetaling.id, clock)
                            Either.catch {
                                sessionFactory.withTransactionContext { tx ->
                                    // OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake. Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
                                    // Det er også viktig at publiseringen av utbetalingen er det siste som skjer i blokka. Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. statistikk.
                                    utbetalingService.lagreUtbetaling(utbetaling, tx)
                                    vedtakRepo.lagre(vedtak, tx)
                                    revurderingRepo.lagre(iverksattRevurdering, tx)
                                    utbetalingService.publiserUtbetaling(utbetaling).mapLeft { feil ->
                                        throw IverksettTransactionException(
                                            "Kunne ikke publisere utbetaling på køen. Underliggende feil: $feil.",
                                            KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(feil),
                                        )
                                    }
                                    vedtak
                                }
                            }.mapLeft {
                                when (it) {
                                    is IverksettTransactionException -> it.feil
                                    else -> KunneIkkeIverksetteRevurdering.LagringFeilet
                                }
                            }
                        }.map { Pair(iverksattRevurdering, it) }
                }
            is RevurderingTilAttestering.Opphørt ->
                revurdering.tilIverksatt(
                    attestant = attestant,
                    clock = clock,
                    hentOpprinneligAvkorting = { avkortingid -> avkortingsvarselRepo.hent(avkortingid) },
                ).mapLeft {
                    when (it) {
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen -> KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarBlittAnnullertAvEnAnnen -> KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen
                        is RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale -> KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(
                            it.utbetalingFeilet,
                        )
                    }
                }.flatMap { iverksattRevurdering ->
                    utbetalingService.verifiserOgSimulerOpphør(
                        request = UtbetalRequest.Opphør(
                            request = SimulerUtbetalingRequest.Opphør(
                                sakId = iverksattRevurdering.sakId,
                                saksbehandler = attestant,
                                opphørsdato = revurdering.opphørsdatoForUtbetalinger,
                            ),
                            simulering = iverksattRevurdering.simulering,
                        ),
                    ).mapLeft {
                        KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(it)
                    }.flatMap { utbetaling ->
                        val opphørtVedtak = VedtakSomKanRevurderes.from(
                            revurdering = iverksattRevurdering, utbetalingId = utbetaling.id,
                            clock = clock,
                        )
                        Either.catch {
                            sessionFactory.withTransactionContext { tx ->
                                // OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake. Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
                                // Det er også viktig at publiseringen av utbetalingen er det siste som skjer i blokka. Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. statistikk.
                                utbetalingService.lagreUtbetaling(utbetaling, tx)
                                vedtakRepo.lagre(opphørtVedtak, tx)
                                kontrollsamtaleService.annullerKontrollsamtale(opphørtVedtak.behandling.sakId, tx)
                                    .mapLeft { feil ->
                                        throw IverksettTransactionException(
                                            "Kunne ikke annullere kontrollsamtale. Underliggende feil: $feil.",
                                            KunneIkkeIverksetteRevurdering.KunneIkkeAnnulereKontrollsamtale,
                                        )
                                    }
                                revurderingRepo.lagre(iverksattRevurdering, tx)
                                utbetalingService.publiserUtbetaling(utbetaling).mapLeft { feil ->
                                    throw IverksettTransactionException(
                                        "Kunne ikke publisere utbetaling på køen. Underliggende feil: $feil.",
                                        KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(feil),
                                    )
                                }
                                opphørtVedtak
                            }
                        }.mapLeft {
                            log.error(
                                "Kunne ikke iverksette revurdering for sak ${iverksattRevurdering.sakId} og søknadsbehandling ${iverksattRevurdering.id}.",
                                it,
                            )
                            when (it) {
                                is IverksettTransactionException -> it.feil
                                else -> KunneIkkeIverksetteRevurdering.LagringFeilet
                            }
                        }
                    }.map {
                        Pair(iverksattRevurdering, it)
                    }
                }
        }.map {
            Either.catch {
                observers.forEach { observer ->
                    observer.handle(Event.Statistikk.RevurderingStatistikk.RevurderingIverksatt(it.first))
                    observer.handle(Event.Statistikk.Vedtaksstatistikk(it.second))
                }
            }.mapLeft { e ->
                log.error(
                    "Kunne ikke sende statistikk etter vi iverksatte revurdering. Dette er kun en sideeffekt og påvirker ikke saksbehandlingen.",
                    e,
                )
            }
            it.first
        }
    }

    private data class IverksettTransactionException(
        override val message: String,
        val feil: KunneIkkeIverksetteRevurdering,
    ) : RuntimeException(message)

    override fun underkjenn(
        revurderingId: UUID,
        attestering: Attestering.Underkjent,
    ): Either<KunneIkkeUnderkjenneRevurdering, UnderkjentRevurdering> {
        val revurdering = hent(revurderingId)
            .getOrHandle { return KunneIkkeUnderkjenneRevurdering.FantIkkeRevurdering.left() }

        if (revurdering !is RevurderingTilAttestering) {
            return KunneIkkeUnderkjenneRevurdering.UgyldigTilstand(
                revurdering::class,
                RevurderingTilAttestering::class,
            ).left()
        }

        if (revurdering.saksbehandler.navIdent == attestering.attestant.navIdent) {
            return KunneIkkeUnderkjenneRevurdering.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
        }

        val aktørId = personService.hentAktørId(revurdering.fnr).getOrElse {
            log.error("Fant ikke aktør-id for revurdering: ${revurdering.id}")
            return KunneIkkeUnderkjenneRevurdering.FantIkkeAktørId.left()
        }

        val nyOppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = revurdering.saksnummer,
                aktørId = aktørId,
                tilordnetRessurs = revurdering.saksbehandler,
                clock = clock,
            ),
        ).getOrElse {
            log.error("revurdering ${revurdering.id} ble ikke underkjent. Klarte ikke opprette behandlingsoppgave")
            return@underkjenn KunneIkkeUnderkjenneRevurdering.KunneIkkeOppretteOppgave.left()
        }

        val underkjent = revurdering.underkjenn(attestering, nyOppgaveId)

        revurderingRepo.lagre(underkjent)

        val eksisterendeOppgaveId = revurdering.oppgaveId

        oppgaveService.lukkOppgave(eksisterendeOppgaveId)
            .mapLeft {
                log.error("Kunne ikke lukke attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av revurdering. Dette må gjøres manuelt.")
            }.map {
                log.info("Lukket attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av revurdering")
            }

        observers.forEach { observer ->
            observer.handle(
                Event.Statistikk.RevurderingStatistikk.RevurderingUnderkjent(underkjent),
            )
        }

        return underkjent.right()
    }

    override fun fortsettEtterForhåndsvarsling(request: FortsettEtterForhåndsvarslingRequest): Either<FortsettEtterForhåndsvarselFeil, Revurdering> {
        return Either.fromNullable(revurderingRepo.hent(request.revurderingId))
            .mapLeft { FortsettEtterForhåndsvarselFeil.FantIkkeRevurdering }
            .flatMap { revurdering ->
                (revurdering as? SimulertRevurdering)?.right()
                    ?: Either.Left(FortsettEtterForhåndsvarselFeil.MåVæreEnSimulertRevurdering)
            }
            .flatMap { simulertRevurdering ->
                when (request) {
                    is FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger -> {
                        simulertRevurdering.prøvOvergangTilFortsettMedSammeGrunnlag(request.begrunnelse).mapLeft {
                            FortsettEtterForhåndsvarselFeil.UgyldigTilstandsovergang(it.fra, it.til)
                        }.flatMap { simulertRevurderingMedOppdatertForhåndsvarsel ->
                            // sendTilAttestering(...) persisterer forhåndsvarselet
                            sendTilAttestering(
                                revurdering = simulertRevurderingMedOppdatertForhåndsvarsel,
                                saksbehandler = request.saksbehandler,
                                fritekstTilBrev = request.fritekstTilBrev,
                                skalFøreTilBrevutsending = true,
                            ).mapLeft { FortsettEtterForhåndsvarselFeil.Attestering(it) }
                        }
                    }
                    is FortsettEtterForhåndsvarslingRequest.FortsettMedAndreOpplysninger -> {
                        simulertRevurdering.prøvOvergangTilEndreGrunnlaget(request.begrunnelse).mapLeft {
                            FortsettEtterForhåndsvarselFeil.UgyldigTilstandsovergang(it.fra, it.til)
                        }.tap { simulertRevurderingMedOppdatertForhåndsvarsel ->
                            revurderingRepo.lagre(simulertRevurderingMedOppdatertForhåndsvarsel)
                        }
                    }
                    is FortsettEtterForhåndsvarslingRequest.AvsluttUtenEndringer -> {
                        simulertRevurdering.prøvOvergangTilAvsluttet(request.begrunnelse).mapLeft {
                            FortsettEtterForhåndsvarselFeil.UgyldigTilstandsovergang(it.fra, it.til)
                        }.flatMap { simulertRevurderingMedOppdatertForhåndsvarsel ->
                            // avsluttRevurdering(...) persisterer forhåndsvarselet
                            avsluttRevurdering(
                                revurdering = simulertRevurderingMedOppdatertForhåndsvarsel,
                                begrunnelse = request.begrunnelse,
                                fritekst = request.fritekstTilBrev,
                            ).mapLeft {
                                FortsettEtterForhåndsvarselFeil.KunneIkkeAvslutteRevurdering(it)
                            }.map { it as Revurdering }
                        }
                    }
                }
            }
    }

    override fun avsluttRevurdering(
        revurderingId: UUID,
        begrunnelse: String,
        fritekst: String?,
    ): Either<KunneIkkeAvslutteRevurdering, AbstraktRevurdering> {
        return revurderingRepo.hent(revurderingId)?.let {
            avsluttRevurdering(
                revurdering = it,
                begrunnelse = begrunnelse,
                fritekst = fritekst,
            )
        } ?: return KunneIkkeAvslutteRevurdering.FantIkkeRevurdering.left()
    }

    /**
     * Denne kan ikke returnere [KunneIkkeAvslutteRevurdering.FantIkkeRevurdering]
     */
    private fun avsluttRevurdering(
        revurdering: AbstraktRevurdering,
        begrunnelse: String,
        fritekst: String?,
    ): Either<KunneIkkeAvslutteRevurdering, AbstraktRevurdering> {

        val (avsluttetRevurdering, skalSendeAvslutningsbrev) = when (revurdering) {
            is GjenopptaYtelseRevurdering -> revurdering.avslutt(begrunnelse, Tidspunkt.now(clock)).map {
                it to it.skalSendeAvslutningsbrev()
            }.getOrHandle {
                return KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse(it).left()
            }
            is StansAvYtelseRevurdering -> revurdering.avslutt(begrunnelse, Tidspunkt.now(clock)).map {
                it to it.skalSendeAvslutningsbrev()
            }.getOrHandle {
                return KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse(it).left()
            }
            is Revurdering -> revurdering.avslutt(begrunnelse, fritekst, Tidspunkt.now(clock)).map {
                it to it.skalSendeAvslutningsbrev()
            }.getOrHandle {
                return KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetRevurdering(it).left()
            }
        }

        if (avsluttetRevurdering is Revurdering) {
            oppgaveService.lukkOppgave(avsluttetRevurdering.oppgaveId)
                .mapLeft {
                    log.error("Kunne ikke lukke oppgave ${avsluttetRevurdering.oppgaveId} ved avslutting av revurdering. Dette må gjøres manuelt.")
                }.map {
                    log.info("Lukket oppgave ${avsluttetRevurdering.oppgaveId} ved avslutting av revurdering.")
                }
        }

        val resultat = if (avsluttetRevurdering is Revurdering && skalSendeAvslutningsbrev) {
            brevService.lagDokument(avsluttetRevurdering).mapLeft {
                return KunneIkkeAvslutteRevurdering.KunneIkkeLageDokument.left()
            }.map { dokument ->
                val dokumentMedMetaData = dokument.leggTilMetadata(
                    metadata = Dokument.Metadata(
                        sakId = revurdering.sakId,
                        revurderingId = revurdering.id,
                        bestillBrev = true,
                    ),
                )
                sessionFactory.withTransactionContext {
                    brevService.lagreDokument(dokumentMedMetaData)
                    revurderingRepo.lagre(avsluttetRevurdering)
                }
            }
            avsluttetRevurdering.right()
        } else {
            revurderingRepo.lagre(avsluttetRevurdering)
            avsluttetRevurdering.right()
        }
        val event: Event? = when (val result = resultat.getOrElse { null }) {
            is AvsluttetRevurdering -> RevurderingAvsluttet(result)
            is GjenopptaYtelseRevurdering.AvsluttetGjenoppta -> Event.Statistikk.RevurderingStatistikk.Gjenoppta(result)
            is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> Event.Statistikk.RevurderingStatistikk.Stans(result)
            else -> null
        }
        event?.let {
            observers.forEach { observer ->
                observer.handle(it)
            }
        }

        return resultat
    }

    override fun lagBrevutkastForAvslutting(
        revurderingId: UUID,
        fritekst: String?,
    ): Either<KunneIkkeLageBrevutkastForAvsluttingAvRevurdering, Pair<Fnr, ByteArray>> {
        val revurdering = hent(revurderingId)
            .getOrHandle { return KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.FantIkkeRevurdering.left() }

        if (!revurdering.forhåndsvarsel.harSendtForhåndsvarsel()) {
            return KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.RevurderingenErIkkeForhåndsvarslet.left()
        }

        // Lager en midlertidig avsluttet revurdering for å konstruere brevet - denne skal ikke lagres
        val avsluttetRevurdering = revurdering.avslutt(
            begrunnelse = "",
            fritekst = fritekst,
            tidspunktAvsluttet = Tidspunkt.now(clock),
        ).getOrHandle {
            return KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeLageBrevutkast.left()
        }

        return brevService.lagDokument(avsluttetRevurdering)
            .mapLeft {
                when (it) {
                    KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeFinneGjeldendeUtbetaling.left()
                    KunneIkkeLageDokument.KunneIkkeGenererePDF -> KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeGenererePDF.left()
                    KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
                    KunneIkkeLageDokument.KunneIkkeHentePerson -> KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.FantIkkePerson
                }
                return KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeLageBrevutkast.left()
            }
            .map {
                return Pair(avsluttetRevurdering.fnr, it.generertDokument).right()
            }
    }

    private fun hentPersonOgSaksbehandlerNavn(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeHentePersonEllerSaksbehandlerNavn, Pair<Person, String>> {
        val person = personService.hentPerson(fnr).getOrElse {
            log.error("Fant ikke person for fnr: $fnr")
            return KunneIkkeHentePersonEllerSaksbehandlerNavn.FantIkkePerson.left()
        }

        val saksbehandlerNavn = identClient.hentNavnForNavIdent(saksbehandler).getOrElse {
            log.error("Fant ikke saksbehandlernavn for saksbehandler: $saksbehandler")
            return KunneIkkeHentePersonEllerSaksbehandlerNavn.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
        }

        return Pair(person, saksbehandlerNavn).right()
    }

    private fun hent(id: UUID): Either<KunneIkkeHenteRevurdering, Revurdering> {
        return revurderingRepo.hent(id)
            ?.let { if (it is Revurdering) it.right() else KunneIkkeHenteRevurdering.IkkeInstansAvRevurdering.left() }
            ?: KunneIkkeHenteRevurdering.FantIkkeRevurdering.left()
    }

    sealed class KunneIkkeHenteRevurdering {
        object IkkeInstansAvRevurdering : KunneIkkeHenteRevurdering()
        object FantIkkeRevurdering : KunneIkkeHenteRevurdering()
    }
}
