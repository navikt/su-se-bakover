package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.SjekkOmGrunnlagErKonsistent
import no.nav.su.se.bakover.domain.grunnlag.harFlerEnnEnBosituasjonsperiode
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
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
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.Event.Statistikk.RevurderingStatistikk.RevurderingAvsluttet
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.service.vilkår.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import java.time.Clock
import java.time.LocalDate
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
    private val vilkårsvurderingService: VilkårsvurderingService,
    private val grunnlagService: GrunnlagService,
    private val vedtakService: VedtakService,
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val sessionFactory: SessionFactory,
    sakService: SakService,
    private val avkortingsvarselRepo: AvkortingsvarselRepo,
    private val toggleService: ToggleService,
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

        SjekkOmGrunnlagErKonsistent(
            formuegrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.formue.grunnlag,
            uføregrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.uføre.grunnlag,
            bosituasjongrunnlag = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon,
            fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
        ).resultat.getOrHandle {
            when {
                !informasjonSomRevurderes.harValgtBosituasjon() && it.contains(Konsistensproblem.Bosituasjon.Flere) -> {
                    return KunneIkkeOppretteRevurdering.BosituasjonMedFlerePerioderMåRevurderes.left()
                }
                !informasjonSomRevurderes.harValgtInntekt() && it.contains(Konsistensproblem.Bosituasjon.Flere) -> {
                    // Ref: fjernBosituasjonOgFradragHvisIkkeEntydig(...), vi sletter alle fradragene/inntektene dersom vi har flere enn 1 bosituasjon.
                    return KunneIkkeOppretteRevurdering.BosituasjonMedFlerePerioderMåRevurderes.left()
                }
                !informasjonSomRevurderes.harValgtFormue() && it.contains(Konsistensproblem.BosituasjonOgFormue.FlereBosituasjonerOgFormueForEPS) -> {
                    return KunneIkkeOppretteRevurdering.EpsFormueMedFlereBosituasjonsperioderMåRevurderes.left()
                }
            }
        }

        val (grunnlagsdata, vilkårsvurderinger) = fjernBosituasjonOgFradragHvisIkkeEntydig(
            gjeldendeVedtaksdata,
        )

        when (val r = VurderOmVilkårGirOpphørVedRevurdering(vilkårsvurderinger).resultat) {
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
            kontrollerPeriodeForUteståendeAvkorting(gjeldendeVedtaksdata.periode, it)
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
                periode = gjeldendeVedtaksdata.periode,
                tilRevurdering = gjeldendeVedtakPåFraOgMedDato,
                saksbehandler = opprettRevurderingRequest.saksbehandler,
                oppgaveId = oppgaveId,
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
                opprettet = Tidspunkt.now(clock),
                forhåndsvarsel = if (revurderingsårsak.årsak == REGULER_GRUNNBELØP) Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles else null,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = uteståendeAvkorting,
            ).also {
                revurderingRepo.lagre(it)

                vilkårsvurderingService.lagre(
                    behandlingId = it.id,
                    vilkårsvurderinger = it.vilkårsvurderinger,
                )

                grunnlagService.lagreFradragsgrunnlag(
                    behandlingId = it.id,
                    fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
                )

                grunnlagService.lagreBosituasjongrunnlag(it.id, it.grunnlagsdata.bosituasjon)

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

    private fun fjernBosituasjonOgFradragHvisIkkeEntydig(gjeldendeVedtaksdata: GjeldendeVedtaksdata): Pair<Grunnlagsdata, Vilkårsvurderinger.Revurdering> {
        val gjeldendeBosituasjon = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon

        // Dette kan oppstå når vi revurderer en revurdering. Da må vi vise eksisterende, men skal ikke preutfylle.
        val harFlerEnnEnBosituasjon = gjeldendeBosituasjon.harFlerEnnEnBosituasjonsperiode()

        return gjeldendeVedtaksdata.grunnlagsdata.copy(
            // Foreløpig støtter vi kun en aktiv bosituasjon, dersom det er fler, preutfyller vi ikke.
            bosituasjon = if (harFlerEnnEnBosituasjon) emptyList() else listOf(
                gjeldendeBosituasjon.singleFullstendigOrThrow(),
            ),
            fradragsgrunnlag = if (harFlerEnnEnBosituasjon) emptyList() else gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
        ) to gjeldendeVedtaksdata.vilkårsvurderinger
    }

    override fun leggTilUføregrunnlag(
        request: LeggTilUførevurderingerRequest,
    ): Either<KunneIkkeLeggeTilGrunnlag, RevurderingOgFeilmeldingerResponse> {
        val revurdering = hent(request.behandlingId)
            .getOrHandle { return KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling.left() }

        val uførevilkår = request.toVilkår(revurdering.periode, clock).getOrHandle {
            return when (it) {
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> KunneIkkeLeggeTilGrunnlag.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> KunneIkkeLeggeTilGrunnlag.UføregradOgForventetInntektMangler.left()
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> KunneIkkeLeggeTilGrunnlag.OverlappendeVurderingsperioder.left()
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> KunneIkkeLeggeTilGrunnlag.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden.left()
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat -> KunneIkkeLeggeTilGrunnlag.AlleVurderingeneMåHaSammeResultat.left()
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.HeleBehandlingsperiodenMåHaVurderinger -> KunneIkkeLeggeTilGrunnlag.HeleBehandlingsperiodenMåHaVurderinger.left()
            }
        }
        return revurdering.oppdaterUføreOgMarkerSomVurdert(uførevilkår).mapLeft {
            KunneIkkeLeggeTilGrunnlag.UgyldigTilstand(fra = it.fra, til = it.til)
        }.map {
            // TODO jah: Flytt denne inn i revurderingRepo.lagre
            vilkårsvurderingService.lagre(it.id, it.vilkårsvurderinger)
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
            // TODO jah: Flytt denne inn i revurderingRepo.lagre
            grunnlagService.lagreFradragsgrunnlag(it.id, it.grunnlagsdata.fradragsgrunnlag)
            revurderingRepo.lagre(it)
            identifiserFeilOgLagResponse(it)
        }
    }

    override fun leggTilBosituasjongrunnlag(request: LeggTilBosituasjongrunnlagRequest): Either<KunneIkkeLeggeTilBosituasjongrunnlag, RevurderingOgFeilmeldingerResponse> {
        val revurdering = hent(request.revurderingId)
            .getOrHandle { return KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling.left() }

        val bosituasjongrunnlag =
            request.toDomain(
                periode = revurdering.periode,
                clock = clock,
            ) {
                personService.hentPerson(it)
            }.getOrHandle {
                return it.left()
            }

        return revurdering.oppdaterBosituasjonOgMarkerSomVurdert(bosituasjongrunnlag).mapLeft {
            when (it) {
                is Revurdering.KunneIkkeLeggeTilBosituasjon.Valideringsfeil -> KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeEndreBosituasjongrunnlag(
                    it.feil,
                )
                is Revurdering.KunneIkkeLeggeTilBosituasjon.UgyldigTilstand -> KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigTilstand(
                    fra = it.fra,
                    til = it.til,
                )
            }
        }.map {
            // TODO jah: Flytt denne inn i revurderingRepo.lagre
            grunnlagService.lagreBosituasjongrunnlag(it.id, it.grunnlagsdata.bosituasjon)
            revurderingRepo.lagre(it)
            identifiserFeilOgLagResponse(it)
        }
    }

    override fun leggTilFormuegrunnlag(request: LeggTilFormuegrunnlagRequest): Either<KunneIkkeLeggeTilFormuegrunnlag, RevurderingOgFeilmeldingerResponse> {
        val revurdering = hent(request.revurderingId)
            .getOrHandle { return KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering.left() }

        val bosituasjon = revurdering.grunnlagsdata.bosituasjon.singleFullstendigOrThrow()

        val vilkår = request.toDomain(bosituasjon, revurdering.periode, clock).getOrHandle {
            return it.left()
        }
        return revurdering.oppdaterFormueOgMarkerSomVurdert(vilkår).mapLeft {
            KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand(fra = it.fra, til = it.til)
        }.map {
            // TODO jah: Flytt denne inn i revurderingRepo.lagre
            vilkårsvurderingService.lagre(it.id, it.vilkårsvurderinger)
            revurderingRepo.lagre(it)
            identifiserFeilOgLagResponse(it)
        }
    }

    private fun identifiserFeilOgLagResponse(revurdering: Revurdering): RevurderingOgFeilmeldingerResponse {

        val tidligereBeregning = when (val tilRevurdering = revurdering.tilRevurdering) {
            is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> {
                return RevurderingOgFeilmeldingerResponse(
                    revurdering = revurdering,
                    feilmeldinger = identifiserUtfallSomIkkeStøttes(
                        vilkårsvurderinger = revurdering.vilkårsvurderinger,
                        periode = revurdering.periode,
                    ).swap().getOrElse { emptySet() }.toList(),
                )
            }
            is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> tilRevurdering.beregning
            is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> tilRevurdering.beregning
            is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> tilRevurdering.beregning
            is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> {
                return RevurderingOgFeilmeldingerResponse(
                    revurdering = revurdering,
                    feilmeldinger = identifiserUtfallSomIkkeStøttes(
                        vilkårsvurderinger = revurdering.vilkårsvurderinger,
                        periode = revurdering.periode,
                    ).swap().getOrElse { emptySet() }.toList(),
                )
            }
            is VedtakSomKanRevurderes.IngenEndringIYtelse -> tilRevurdering.beregning
        }

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
                    tidligereBeregning = tidligereBeregning,
                    nyBeregning = revurdering.beregning,
                ).swap().getOrElse { emptySet() }
            }
            is SimulertRevurdering -> {
                identifiserUtfallSomIkkeStøttes(
                    revurderingsperiode = revurdering.periode,
                    vilkårsvurderinger = revurdering.vilkårsvurderinger,
                    tidligereBeregning = tidligereBeregning,
                    nyBeregning = revurdering.beregning,
                ).swap().getOrElse { emptySet() }
            }
            else -> throw IllegalStateException("Skal ikke kunne lage en RevurderingOgFeilmeldingerResponse fra ${revurdering::class}")
        }

        return RevurderingOgFeilmeldingerResponse(revurdering, feilmeldinger.toList())
    }

    override fun hentGjeldendeGrunnlagsdataOgVilkårsvurderinger(revurderingId: UUID): Either<KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger, HentGjeldendeGrunnlagsdataOgVilkårsvurderingerResponse> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger.FantIkkeBehandling.left()

        return vedtakService.kopierGjeldendeVedtaksdata(revurdering.sakId, revurdering.periode.fraOgMed)
            .mapLeft {
                when (it) {
                    KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak -> KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger.FantIkkeSak
                    KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak -> KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger.FantIngentingSomKanRevurderes
                    is KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode -> KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger.UgyldigPeriode(
                        it.cause,
                    )
                }
            }
            .map {
                HentGjeldendeGrunnlagsdataOgVilkårsvurderingerResponse(
                    it.grunnlagsdata,
                    it.vilkårsvurderinger,
                )
            }
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

        SjekkOmGrunnlagErKonsistent(
            formuegrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.formue.grunnlag,
            uføregrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.uføre.grunnlag,
            bosituasjongrunnlag = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon,
            fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
        ).resultat.getOrHandle {
            when {
                !informasjonSomRevurderes.harValgtBosituasjon() && it.contains(Konsistensproblem.Bosituasjon.Flere) -> {
                    return KunneIkkeOppdatereRevurdering.BosituasjonMedFlerePerioderMåRevurderes.left()
                }
                !informasjonSomRevurderes.harValgtInntekt() && it.contains(Konsistensproblem.Bosituasjon.Flere) -> {
                    // Ref: fjernBosituasjonOgFradragHvisIkkeEntydig(...), vi sletter alle fradragene/inntektene dersom vi har flere enn 1 bosituasjon.
                    return KunneIkkeOppdatereRevurdering.BosituasjonMedFlerePerioderMåRevurderes.left()
                }
                !informasjonSomRevurderes.harValgtFormue() && it.contains(Konsistensproblem.BosituasjonOgFormue.FlereBosituasjonerOgFormueForEPS) -> {
                    return KunneIkkeOppdatereRevurdering.EpsFormueMedFlereBosituasjonsperioderMåRevurderes.left()
                }
            }
        }

        val (grunnlagsdata, vilkårsvurderinger) = fjernBosituasjonOgFradragHvisIkkeEntydig(
            gjeldendeVedtaksdata,
        )

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
            gjeldendeVedtaksdata.gjeldendeVedtakPåDato(oppdaterRevurderingRequest.fraOgMed)
                ?: return KunneIkkeOppdatereRevurdering.FantIngenVedtakSomKanRevurderes.left()

        val avkorting = hentUteståendeAvkorting(revurdering.sakId).let {
            kontrollerPeriodeForUteståendeAvkorting(gjeldendeVedtaksdata.periode, it)
                .getOrHandle {
                    return KunneIkkeOppdatereRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
                        periode = it.periode,
                    ).left()
                }
        }

        return when (revurdering) {
            is OpprettetRevurdering -> revurdering.oppdater(
                periode = gjeldendeVedtaksdata.periode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                tilRevurdering = gjeldendeVedtakPåFraOgMedDato,
                avkorting = avkorting,
            ).right()
            is BeregnetRevurdering -> revurdering.oppdater(
                periode = gjeldendeVedtaksdata.periode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                tilRevurdering = gjeldendeVedtakPåFraOgMedDato,
                avkorting = avkorting,
            ).right()
            is SimulertRevurdering -> revurdering.oppdater(
                periode = gjeldendeVedtaksdata.periode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                tilRevurdering = gjeldendeVedtakPåFraOgMedDato,
                avkorting = avkorting,
            ).right()
            is UnderkjentRevurdering -> revurdering.oppdater(
                periode = gjeldendeVedtaksdata.periode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
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
            vilkårsvurderingService.lagre(it.id, it.vilkårsvurderinger)
            grunnlagService.lagreFradragsgrunnlag(it.id, it.grunnlagsdata.fradragsgrunnlag)
            grunnlagService.lagreBosituasjongrunnlag(it.id, it.grunnlagsdata.bosituasjon)
            it
        }
    }

    override fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, RevurderingOgFeilmeldingerResponse> {
        val originalRevurdering = hent(revurderingId)
            .getOrHandle { return KunneIkkeBeregneOgSimulereRevurdering.FantIkkeRevurdering.left() }

        return when (originalRevurdering) {
            is BeregnetRevurdering, is OpprettetRevurdering, is SimulertRevurdering, is UnderkjentRevurdering -> {
                val eksisterendeUtbetalinger = utbetalingService.hentUtbetalinger(originalRevurdering.sakId)
                val beregnetRevurdering = originalRevurdering.beregn(
                    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                    clock = clock,
                    gjeldendeVedtaksdata = vedtakService.kopierGjeldendeVedtaksdata(
                        sakId = originalRevurdering.sakId,
                        fraOgMed = originalRevurdering.periode.fraOgMed,
                    )
                        .getOrHandle { throw IllegalStateException("Fant ikke gjeldende vedtaksdata for sak:${originalRevurdering.sakId}") },
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

                val leggTilVarselForBeløpsendringUnder10Prosent =
                    !VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
                        eksisterendeUtbetalinger = eksisterendeUtbetalinger.flatMap { it.utbetalingslinjer },
                        nyBeregning = beregnetRevurdering.beregning,
                    ).resultat

                when (beregnetRevurdering) {
                    is BeregnetRevurdering.IngenEndring -> {
                        revurderingRepo.lagre(beregnetRevurdering)
                        // må lagre fradrag på nytt, siden eventuelle avkortinger legges til ved beregning
                        grunnlagService.lagreFradragsgrunnlag(
                            behandlingId = revurderingId,
                            fradragsgrunnlag = beregnetRevurdering.grunnlagsdata.fradragsgrunnlag,
                        )
                        when (leggTilVarselForBeløpsendringUnder10Prosent) {
                            true -> {
                                identifiserFeilOgLagResponse(beregnetRevurdering)
                                    .leggTil(Varselmelding.BeløpsendringUnder10Prosent)
                                    .right()
                            }
                            false -> {
                                identifiserFeilOgLagResponse(beregnetRevurdering)
                                    .right()
                            }
                        }
                    }
                    is BeregnetRevurdering.Innvilget -> {
                        utbetalingService.simulerUtbetaling(
                            request = SimulerUtbetalingRequest.NyUtbetaling(
                                sakId = beregnetRevurdering.sakId,
                                saksbehandler = saksbehandler,
                                beregning = beregnetRevurdering.beregning,
                                uføregrunnlag = beregnetRevurdering.vilkårsvurderinger.uføre.grunnlag,
                            ),
                        ).mapLeft {
                            KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(it)
                        }.map {
                            beregnetRevurdering.toSimulert(it.simulering).let { simulert ->
                                revurderingRepo.lagre(simulert)
                                // må lagre fradrag på nytt, siden eventuelle avkortinger legges til ved beregning
                                grunnlagService.lagreFradragsgrunnlag(
                                    behandlingId = revurderingId,
                                    fradragsgrunnlag = beregnetRevurdering.grunnlagsdata.fradragsgrunnlag,
                                )
                                when (leggTilVarselForBeløpsendringUnder10Prosent) {
                                    true -> {
                                        identifiserFeilOgLagResponse(simulert)
                                            .leggTil(Varselmelding.BeløpsendringUnder10Prosent)
                                    }
                                    false -> {
                                        identifiserFeilOgLagResponse(simulert)
                                    }
                                }
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
                                // må lagre fradrag på nytt, siden eventuelle avkortinger legges til ved beregning
                                grunnlagService.lagreFradragsgrunnlag(
                                    behandlingId = revurderingId,
                                    fradragsgrunnlag = beregnetRevurdering.grunnlagsdata.fradragsgrunnlag,
                                )
                                when (leggTilVarselForBeløpsendringUnder10Prosent) {
                                    true -> {
                                        identifiserFeilOgLagResponse(simulert)
                                            .leggTil(Varselmelding.BeløpsendringUnder10Prosent)
                                    }
                                    false -> {
                                        identifiserFeilOgLagResponse(simulert)
                                    }
                                }
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
        tidligereBeregning: Beregning,
        nyBeregning: Beregning,
    ) = IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
        revurderingsperiode = revurderingsperiode,
        vilkårsvurderinger = vilkårsvurderinger,
        tidligereBeregning = tidligereBeregning,
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
                revurdering.prøvOvergangTilSkalIkkeForhåndsvarsles().mapLeft {
                    KunneIkkeForhåndsvarsle.UgyldigTilstandsovergangForForhåndsvarsling
                }.tap {
                    revurderingRepo.lagre(it)
                }
            }
            Forhåndsvarselhandling.FORHÅNDSVARSLE -> {
                revurdering.prøvOvergangTilSendt().mapLeft {
                    KunneIkkeForhåndsvarsle.UgyldigTilstandsovergangForForhåndsvarsling
                }.flatMap { simulertRevurdering ->
                    Either.catch {
                        sessionFactory.withTransactionContext { transactionContext ->
                            skedulerForhåndsvarselsbrev(
                                revurderingId = simulertRevurdering.id,
                                sakId = simulertRevurdering.sakId,
                                fnr = simulertRevurdering.fnr,
                                saksbehandler = simulertRevurdering.saksbehandler,
                                fritekst = fritekst,
                                transactionContext = transactionContext,
                                saksnummer = simulertRevurdering.saksnummer,
                            ).map {
                                revurderingRepo.lagre(simulertRevurdering, transactionContext)
                                prøvÅOppdatereOppgaveEtterViHarSendtForhåndsvarsel(
                                    revurderingId = simulertRevurdering.id,
                                    oppgaveId = simulertRevurdering.oppgaveId,
                                ).tapLeft {
                                    throw KunneIkkeOppdatereOppgave()
                                }
                                log.info("Forhåndsvarsel sendt for revurdering ${simulertRevurdering.id}")
                                simulertRevurdering
                            }
                        }
                    }.mapLeft {
                        if (it is KunneIkkeOppdatereOppgave) {
                            KunneIkkeForhåndsvarsle.KunneIkkeOppdatereOppgave
                        } else {
                            throw it
                        }
                    }.flatten()
                }
            }
        }
    }

    private class KunneIkkeOppdatereOppgave : RuntimeException()

    private fun skedulerForhåndsvarselsbrev(
        revurderingId: UUID,
        sakId: UUID,
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
        transactionContext: TransactionContext,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeForhåndsvarsle, Unit> {
        val personOgSaksbehandlerNavn =
            hentPersonOgSaksbehandlerNavn(fnr, saksbehandler).getOrHandle {
                return when (it) {
                    KunneIkkeHentePersonEllerSaksbehandlerNavn.FantIkkePerson -> KunneIkkeForhåndsvarsle.FantIkkePerson.left()
                    KunneIkkeHentePersonEllerSaksbehandlerNavn.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeForhåndsvarsle.KunneIkkeHenteNavnForSaksbehandler.left()
                }
            }

        val dokument = LagBrevRequest.Forhåndsvarsel(
            person = personOgSaksbehandlerNavn.first,
            saksbehandlerNavn = personOgSaksbehandlerNavn.second,
            fritekst = fritekst,
            dagensDato = LocalDate.now(clock),
            saksnummer = saksnummer,
        ).tilDokument {
            brevService.lagBrev(it).mapLeft { LagBrevRequest.KunneIkkeGenererePdf }
        }.map {
            it.leggTilMetadata(
                metadata = Dokument.Metadata(
                    sakId = sakId,
                    revurderingId = revurderingId,
                    bestillBrev = true,
                ),
            )
        }.getOrHandle { return KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument.left() }
        brevService.lagreDokument(dokument, transactionContext)

        return Unit.right()
    }

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
        val revurdering = hent(revurderingId)
            .getOrHandle { return KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering.left() }

        val personOgSaksbehandlerNavn =
            hentPersonOgSaksbehandlerNavn(revurdering.fnr, revurdering.saksbehandler).getOrHandle {
                return when (it) {
                    KunneIkkeHentePersonEllerSaksbehandlerNavn.FantIkkePerson -> KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson.left()
                    KunneIkkeHentePersonEllerSaksbehandlerNavn.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
                }
            }

        val brevRequest = LagBrevRequest.Forhåndsvarsel(
            person = personOgSaksbehandlerNavn.first,
            fritekst = fritekst,
            saksbehandlerNavn = personOgSaksbehandlerNavn.second,
            dagensDato = LocalDate.now(clock),
            saksnummer = revurdering.saksnummer,
        )

        return brevService.lagBrev(brevRequest)
            .mapLeft { KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast }
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

        val tilAttestering = when (revurdering) {
            is BeregnetRevurdering.IngenEndring -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
                fritekstTilBrev,
                if (revurdering.revurderingsårsak.årsak == REGULER_GRUNNBELØP) false else skalFøreTilBrevutsending,
            )
            is SimulertRevurdering.Innvilget -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
                fritekstTilBrev,
            ).getOrHandle {
                return KunneIkkeSendeRevurderingTilAttestering.ForhåndsvarslingErIkkeFerdigbehandling.left()
            }
            is SimulertRevurdering.Opphørt -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
                fritekstTilBrev,
            ).getOrElse {
                return KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør.left()
            }
            is UnderkjentRevurdering.IngenEndring -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
                fritekstTilBrev,
                if (revurdering.revurderingsårsak.årsak == REGULER_GRUNNBELØP) false else skalFøreTilBrevutsending,
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

        val tidligereBeregning = when (val tilRevurdering = revurdering.tilRevurdering) {
            is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> {
                return identifiserUtfallSomIkkeStøttes(
                    revurdering.vilkårsvurderinger,
                    revurdering.periode,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }
            }
            is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> tilRevurdering.beregning
            is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> tilRevurdering.beregning
            is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> tilRevurdering.beregning
            is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> {
                return identifiserUtfallSomIkkeStøttes(
                    revurdering.vilkårsvurderinger,
                    revurdering.periode,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }
            }
            is VedtakSomKanRevurderes.IngenEndringIYtelse -> tilRevurdering.beregning
        }

        return when (revurdering) {
            is BeregnetRevurdering.IngenEndring -> {
                identifiserUtfallSomIkkeStøttes(
                    revurdering.periode,
                    revurdering.vilkårsvurderinger,
                    tidligereBeregning,
                    revurdering.beregning,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }
            }
            is SimulertRevurdering -> {
                identifiserUtfallSomIkkeStøttes(
                    revurdering.periode,
                    revurdering.vilkårsvurderinger,
                    tidligereBeregning,
                    revurdering.beregning,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }.flatMap {
                    if (!feilutbetalingTillatt() && revurdering.harSimuleringFeilutbetaling()) KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left() else Unit.right()
                }
            }
            is UnderkjentRevurdering.Innvilget -> {
                identifiserUtfallSomIkkeStøttes(
                    revurdering.periode,
                    revurdering.vilkårsvurderinger,
                    tidligereBeregning,
                    revurdering.beregning,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }.flatMap {
                    if (!feilutbetalingTillatt() && revurdering.harSimuleringFeilutbetaling()) KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left() else Unit.right()
                }
            }
            is UnderkjentRevurdering.Opphørt -> {
                identifiserUtfallSomIkkeStøttes(
                    revurdering.periode,
                    revurdering.vilkårsvurderinger,
                    tidligereBeregning,
                    revurdering.beregning,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }.flatMap {
                    if (!feilutbetalingTillatt() && revurdering.harSimuleringFeilutbetaling()) KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left() else Unit.right()
                }
            }
            is UnderkjentRevurdering.IngenEndring -> {
                identifiserUtfallSomIkkeStøttes(
                    revurdering.periode,
                    revurdering.vilkårsvurderinger,
                    tidligereBeregning,
                    revurdering.beregning,
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

    private fun feilutbetalingTillatt(): Boolean {
        return toggleService.isEnabled("supstonad.ufore.feilutbetaling")
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

    private fun lagDokument(vedtakIngenEndring: VedtakSomKanRevurderes.IngenEndringIYtelse): Either<KunneIkkeIverksetteRevurdering, Dokument.MedMetadata> =
        brevService.lagDokument(vedtakIngenEndring)
            .getOrHandle {
                return when (it) {
                    KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeIverksetteRevurdering.KunneIkkeFinneGjeldendeUtbetaling
                    KunneIkkeLageDokument.KunneIkkeGenererePDF -> KunneIkkeIverksetteRevurdering.KunneIkkeGenerereBrev
                    KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeIverksetteRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                    KunneIkkeLageDokument.KunneIkkeHentePerson -> KunneIkkeIverksetteRevurdering.FantIkkePerson
                }.left()
            }
            .leggTilMetadata(
                Dokument.Metadata(
                    sakId = vedtakIngenEndring.behandling.sakId,
                    vedtakId = vedtakIngenEndring.id,
                    bestillBrev = true,
                ),
            ).right()

    override fun iverksett(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
        val revurdering = revurderingRepo.hent(revurderingId) ?: return KunneIkkeIverksetteRevurdering.FantIkkeRevurdering.left()
        if (revurdering !is RevurderingTilAttestering) return KunneIkkeIverksetteRevurdering.UgyldigTilstand(revurdering::class, IverksattRevurdering::class).left()

        val iverksattRevurdering = when (revurdering) {
            is RevurderingTilAttestering.IngenEndring -> {
                revurdering.tilIverksatt(attestant, clock)
                    .map { iverksattRevurdering ->
                        Either.catch {
                            val vedtakIngenEndring = VedtakSomKanRevurderes.from(iverksattRevurdering, clock)
                            sessionFactory.withTransactionContext { tx ->
                                vedtakRepo.lagre(vedtakIngenEndring, tx)
                                revurderingRepo.lagre(iverksattRevurdering)

                                if (vedtakIngenEndring.skalSendeBrev()) {
                                    lagDokument(vedtakIngenEndring).map {
                                        brevService.lagreDokument(it, tx)
                                    }
                                }
                            }
                        }.mapLeft {
                            KunneIkkeIverksetteRevurdering.LagringFeilet
                        }

                        // TODO (CHM 18.01.21): Lukk attesteringsoppgave. For de andre to casene blir denne lukket i `ferdigstillVedtakEtterUtbetaling`
                        // P.t. vil ikke IngenEndring inntreffe, pga 10%-endringen, men må testes når denne evt. blir lagt tilbake
                        // oppgaveService.lukkOppgave(revurdering.oppgaveId)

                        iverksattRevurdering
                    }
            }
            is RevurderingTilAttestering.Innvilget -> {
                revurdering.validerTilIverksatt(
                    attestant = attestant,
                    hentOpprinneligAvkorting = { avkortingid -> avkortingsvarselRepo.hent(avkortingid) },
                    clock = clock
                ).map { iverksattRevurdering ->
                    utbetalingService.genererUtbetalingsRequest(
                        request = UtbetalRequest.NyUtbetaling(
                            request = SimulerUtbetalingRequest.NyUtbetaling(
                                sakId = revurdering.sakId,
                                saksbehandler = attestant, beregning = revurdering.beregning,

                                uføregrunnlag = revurdering.vilkårsvurderinger.uføre.grunnlag,
                            ),
                            simulering = revurdering.simulering,
                        ),
                    ).map { utbetaling ->
                        Either.catch {
                            sessionFactory.withTransactionContext { tx ->
                                val vedtak = VedtakSomKanRevurderes.from(iverksattRevurdering, utbetaling.id, clock)
                                vedtakRepo.lagre(vedtak, tx)
                                revurderingRepo.lagre(iverksattRevurdering, tx)
                                utbetalingService.lagreUtbetaling(utbetaling, tx)
                                utbetalingService.publiserUtbetaling(utbetaling).mapLeft { feil ->
                                    log.error("Kunne ikke publisere revurderingsutbetaling på køen. Ruller tilbake. SakId: ${iverksattRevurdering.sakId}", feil)
                                    throw RuntimeException("Publisering av revurderingsutbetaling på køen feilet. $feil")
                                }
                            }
                        }.mapLeft {
                            KunneIkkeIverksetteRevurdering.LagringFeilet
                        }
                    }
                    iverksattRevurdering
                }
            }
            is RevurderingTilAttestering.Opphørt -> {
                revurdering.validerTilIverksatt(
                    attestant = attestant,
                    clock = clock,
                    hentOpprinneligAvkorting = { avkortingid -> avkortingsvarselRepo.hent(avkortingid) }
                ).map { iverksattRevurdering ->
                    utbetalingService.genererOpphørsRequest(
                        request = UtbetalRequest.Opphør(
                            request = SimulerUtbetalingRequest.Opphør(
                                sakId = iverksattRevurdering.sakId,
                                saksbehandler = attestant,
                                opphørsdato = revurdering.opphørsdatoForUtbetalinger,
                            ),
                            simulering = iverksattRevurdering.simulering,
                        ),
                    ).map { utbetaling ->
                        Either.catch {
                            sessionFactory.withTransactionContext { tx ->
                                val opphørtVedtak =
                                    VedtakSomKanRevurderes.from(iverksattRevurdering, utbetaling.id, clock)
                                vedtakRepo.lagre(opphørtVedtak, tx)
                                kontrollsamtaleService.annullerKontrollsamtale(opphørtVedtak.behandling.sakId, tx)
                                revurderingRepo.lagre(iverksattRevurdering, tx)
                                utbetalingService.lagreUtbetaling(utbetaling, tx)
                                utbetalingService.publiserUtbetaling(utbetaling).mapLeft { feil ->
                                    log.error(
                                        "Kunne ikke publisere revurdering-opphør på køen. Ruller tilbake. SakId: ${iverksattRevurdering.sakId}",
                                        feil
                                    )
                                    throw RuntimeException("Publisering av revurdering-opphør på køen feilet. $feil")
                                }
                                observers.forEach { observer ->
                                    observer.handle(
                                        Event.Statistikk.Vedtaksstatistikk(opphørtVedtak),
                                    )
                                }
                            }
                        }.mapLeft {
                            KunneIkkeIverksetteRevurdering.LagringFeilet
                        }
                    }
                    iverksattRevurdering
                }
            }
        }.getOrHandle {
            return when (it) {
                RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                is RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale -> KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(
                    it.utbetalingFeilet,
                )
                RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen -> KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen
                RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarBlittAnnullertAvEnAnnen -> KunneIkkeIverksetteRevurdering.HarAlleredeBlittAnnullertAvEnAnnen
            }.left()
        }

        observers.forEach { observer ->
            observer.handle(
                Event.Statistikk.RevurderingStatistikk.RevurderingIverksatt(iverksattRevurdering),
            )
        }

        return iverksattRevurdering.right()
    }

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
