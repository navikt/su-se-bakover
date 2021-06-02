package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Validator.valider
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KopierGjeldendeGrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.erKlarForAttestering
import no.nav.su.se.bakover.domain.revurdering.medFritekst
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class RevurderingServiceImpl(
    private val sakService: SakService,
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val brevService: BrevService,
    private val clock: Clock,
    internal val vedtakRepo: VedtakRepo,
    internal val ferdigstillVedtakService: FerdigstillVedtakService,
    private val vilkårsvurderingService: VilkårsvurderingService,
    private val grunnlagService: GrunnlagService,
) : RevurderingService {

    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    override fun hentRevurdering(revurderingId: UUID): Revurdering? =
        revurderingRepo.hent(revurderingId)

    override fun opprettRevurdering(
        opprettRevurderingRequest: OpprettRevurderingRequest,
    ): Either<KunneIkkeOppretteRevurdering, OpprettetRevurdering> {
        val revurderingsårsak = opprettRevurderingRequest.revurderingsårsak.getOrHandle {
            return when (it) {
                Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigBegrunnelse -> KunneIkkeOppretteRevurdering.UgyldigBegrunnelse
                Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak -> KunneIkkeOppretteRevurdering.UgyldigÅrsak
            }.left()
        }

        if (!kanOppretteEllerOppdatereRevurderingsPeriodeOgEllerÅrsak(
                revurderingsårsak,
                opprettRevurderingRequest.fraOgMed,
            )
        ) {
            return KunneIkkeOppretteRevurdering.PeriodeOgÅrsakKombinasjonErUgyldig.left()
        }
        val sak = sakService.hentSak(opprettRevurderingRequest.sakId).getOrElse {
            return KunneIkkeOppretteRevurdering.FantIkkeSak.left()
        }

        val tilRevurdering = sak.vedtakListe
            .filterIsInstance<Vedtak.EndringIYtelse>()
            .filter { opprettRevurderingRequest.fraOgMed.between(it.periode) }
            .maxByOrNull { it.opprettet.instant }
            ?: return KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes.left()

        val periode =
            Periode.tryCreate(opprettRevurderingRequest.fraOgMed, tilRevurdering.periode.tilOgMed).getOrHandle {
                return KunneIkkeOppretteRevurdering.UgyldigPeriode(it).left()
            }

        val aktørId = personService.hentAktørId(tilRevurdering.behandling.fnr).getOrElse {
            log.error("Fant ikke aktør-id")
            return KunneIkkeOppretteRevurdering.FantIkkeAktørId.left()
        }

        val gjeldendeGrunnlagsdataOgVilkårsvurderinger = KopierGjeldendeGrunnlagsdataOgVilkårsvurderinger(periode, sak.vedtakListe)
        val grunnlagsdata = gjeldendeGrunnlagsdataOgVilkårsvurderinger.grunnlagsdata.copy(
            bosituasjon = gjeldendeGrunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.singleOrNull() ?.let { listOf(it) } ?: emptyList()
        )

        val informasjonSomRevurderes = InformasjonSomRevurderes.tryCreate(opprettRevurderingRequest.informasjonSomRevurderes)
            .getOrHandle { return KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left() }

        // TODO ai 25.02.2021 - Oppgaven skal egentligen ikke opprettes her. Den burde egentligen komma utifra melding av endring, som skal føres til revurdering.
        return oppgaveService.opprettOppgave(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = tilRevurdering.behandling.saksnummer,
                aktørId = aktørId,
                tilordnetRessurs = null,
            ),
        ).mapLeft {
            KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave
        }.map { oppgaveId ->
            OpprettetRevurdering(
                periode = periode,
                tilRevurdering = tilRevurdering,
                saksbehandler = opprettRevurderingRequest.saksbehandler,
                oppgaveId = oppgaveId,
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
                opprettet = Tidspunkt.now(clock),
                forhåndsvarsel = if (revurderingsårsak.årsak == REGULER_GRUNNBELØP) Forhåndsvarsel.IngenForhåndsvarsel else null,
                behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = gjeldendeGrunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
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

    override fun leggTilUføregrunnlag(
        request: LeggTilUførevurderingerRequest,
    ): Either<KunneIkkeLeggeTilGrunnlag, LeggTilUføregrunnlagResponse> {
        val revurdering = revurderingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling.left()

        if (revurdering is RevurderingTilAttestering || revurdering is IverksattRevurdering)
            return KunneIkkeLeggeTilGrunnlag.UgyldigStatus.left()

        val uførevilkår = request.toVilkår(revurdering.periode).getOrHandle {
            return when (it) {
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> KunneIkkeLeggeTilGrunnlag.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> KunneIkkeLeggeTilGrunnlag.UføregradOgForventetInntektMangler.left()
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> KunneIkkeLeggeTilGrunnlag.OverlappendeVurderingsperioder.left()
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> KunneIkkeLeggeTilGrunnlag.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden.left()
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat -> KunneIkkeLeggeTilGrunnlag.AlleVurderingeneMåHaSammeResultat.left()
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.HeleBehandlingsperiodenMåHaVurderinger -> KunneIkkeLeggeTilGrunnlag.HeleBehandlingsperiodenMåHaVurderinger.left()
            }
        }

        // TODO jah: Vi trenger fremdeles behandlingsinformasjon for å utlede sats, så den må ligge inntil vi har flyttet den modellen/logikken til Vilkår
        val oppdatertBehandlingsinformasjon = revurdering.oppdaterBehandlingsinformasjon(
            revurdering.behandlingsinformasjon.copy(
                uførhet = uførevilkår.vurderingsperioder.firstOrNull()?.let {
                    Behandlingsinformasjon.Uførhet(
                        status = when (it.resultat) {
                            Resultat.Avslag -> Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt
                            Resultat.Innvilget -> Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt
                            Resultat.Uavklart -> Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling
                        },
                        uføregrad = it.grunnlag?.uføregrad?.value,
                        forventetInntekt = it.grunnlag?.forventetInntekt,
                        begrunnelse = it.begrunnelse,
                    )
                },
            ),
        ).also {
            revurderingRepo.lagre(
                it.copy(
                    informasjonSomRevurderes = it.informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Uførhet),
                ),
            )
        }
        vilkårsvurderingService.lagre(
            behandlingId = oppdatertBehandlingsinformasjon.id,
            vilkårsvurderinger = oppdatertBehandlingsinformasjon.vilkårsvurderinger.copy(
                uføre = uførevilkår,
            ),
        )

        return LeggTilUføregrunnlagResponse(
            revurdering = revurderingRepo.hent(oppdatertBehandlingsinformasjon.id)!!,
        ).right()
    }

    private fun Revurdering.ugyldigTilstandForåLeggeTilGrunnlag() = this is RevurderingTilAttestering || this is IverksattRevurdering

    override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, LeggTilFradragsgrunnlagResponse> {
        val revurdering = revurderingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        if (revurdering.ugyldigTilstandForåLeggeTilGrunnlag())
            return KunneIkkeLeggeTilFradragsgrunnlag.UgyldigStatus.left()

        request.fradragsrunnlag.valider(revurdering.periode).getOrHandle {
            return when (it) {
                Grunnlag.Fradragsgrunnlag.Validator.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag -> KunneIkkeLeggeTilFradragsgrunnlag.UgyldigFradragstypeForGrunnlag
                Grunnlag.Fradragsgrunnlag.Validator.UgyldigFradragsgrunnlag.UtenforBehandlingsperiode -> KunneIkkeLeggeTilFradragsgrunnlag.FradragsgrunnlagUtenforRevurderingsperiode
            }.left()
        }

        grunnlagService.lagreFradragsgrunnlag(
            behandlingId = revurdering.id,
            fradragsgrunnlag = request.fradragsrunnlag,
        )

        return LeggTilFradragsgrunnlagResponse(
            revurdering = revurderingRepo.hent(revurdering.id)!!,
        ).right()
    }

    override fun leggTilBosituasjongrunnlag(request: LeggTilBosituasjongrunnlagRequest): Either<KunneIkkeLeggeTilBosituasjongrunnlag, LeggTilBosituasjongrunnlagResponse> {
        val revurdering = revurderingRepo.hent(request.revurderingId)
            ?: return KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling.left()

        if ((request.epsFnr == null && request.delerBolig == null) || (request.epsFnr != null && request.delerBolig != null)) {
            return KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData.left()
        }

        val bosituasjongrunnlag =
            request.toDomain(
                periode = revurdering.periode,
                clock = clock,
            ) {
                personService.hentPerson(it)
            }.getOrHandle {
                return it.left()
            }

        grunnlagService.lagreBosituasjongrunnlag(revurdering.id, listOf(bosituasjongrunnlag))
        return LeggTilBosituasjongrunnlagResponse(
            revurdering = revurderingRepo.hent(request.revurderingId)!!,
        ).right()
    }

    override fun hentGjeldendeGrunnlagsdataOgVilkårsvurderinger(revurderingId: UUID): Either<KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger, HentGjeldendeGrunnlagsdataOgVilkårsvurderingerResponse> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger.FantIkkeBehandling.left()

        val sak = sakService.hentSak(revurdering.sakId).getOrHandle {
            return KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger.FantIkkeSak.left()
        }

        return KopierGjeldendeGrunnlagsdataOgVilkårsvurderinger(
            periode = revurdering.periode,
            vedtakListe = sak.vedtakListe,
        ).let {
            HentGjeldendeGrunnlagsdataOgVilkårsvurderingerResponse(
                it.grunnlagsdata,
                it.vilkårsvurderinger,
            )
        }.right()
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

        val revurdering = revurderingRepo.hent(oppdaterRevurderingRequest.revurderingId)
            ?: return KunneIkkeOppdatereRevurdering.FantIkkeRevurdering.left()

        if (revurdering.forhåndsvarsel is Forhåndsvarsel.SkalForhåndsvarsles) {
            return KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet.left()
        }

        if (!kanOppretteEllerOppdatereRevurderingsPeriodeOgEllerÅrsak(
                revurderingsårsak,
                oppdaterRevurderingRequest.fraOgMed,
            )
        ) {
            return KunneIkkeOppdatereRevurdering.PeriodeOgÅrsakKombinasjonErUgyldig.left()
        }

        val stønadsperiode = revurdering.tilRevurdering.periode
        val nyPeriode =
            Periode.tryCreate(oppdaterRevurderingRequest.fraOgMed, stønadsperiode.tilOgMed).getOrHandle {
                return KunneIkkeOppdatereRevurdering.UgyldigPeriode(it).left()
            }

        if (oppdaterRevurderingRequest.informasjonSomRevurderes.isEmpty()) {
            return KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
        }

        val sak = sakService.hentSak(revurdering.sakId).getOrElse {
            return KunneIkkeOppdatereRevurdering.FantIkkeSak.left()
        }

        val gjeldendeGrunnlagsdataOgVilkårsvurderinger = KopierGjeldendeGrunnlagsdataOgVilkårsvurderinger(nyPeriode, sak.vedtakListe)
        val grunnlagsdata = gjeldendeGrunnlagsdataOgVilkårsvurderinger.grunnlagsdata.copy(
            bosituasjon = gjeldendeGrunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.singleOrNull() ?.let { listOf(it) } ?: emptyList()
        )

        val informasjonSomRevurderes = InformasjonSomRevurderes.tryCreate(oppdaterRevurderingRequest.informasjonSomRevurderes)
            .getOrHandle { return KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left() }

        return when (revurdering) {
            is OpprettetRevurdering -> revurdering.oppdater(
                periode = nyPeriode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = gjeldendeGrunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ).right()
            is BeregnetRevurdering -> revurdering.oppdater(
                periode = nyPeriode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = gjeldendeGrunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ).right()
            is SimulertRevurdering -> revurdering.oppdater(
                periode = nyPeriode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = gjeldendeGrunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ).right()
            is UnderkjentRevurdering -> revurdering.oppdater(
                periode = nyPeriode,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = gjeldendeGrunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
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
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, Revurdering> {
        return when (val originalRevurdering = revurderingRepo.hent(revurderingId)) {
            is BeregnetRevurdering, is OpprettetRevurdering, is SimulertRevurdering, is UnderkjentRevurdering -> {
                when (
                    val beregnetRevurdering = originalRevurdering.beregn()
                        .getOrHandle {
                            return when (it) {
                                is Revurdering.KunneIkkeBeregneRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden -> KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden
                                is Revurdering.KunneIkkeBeregneRevurdering.UfullstendigBehandlingsinformasjon -> KunneIkkeBeregneOgSimulereRevurdering.UfullstendigBehandlingsinformasjon
                                is Revurdering.KunneIkkeBeregneRevurdering.UgyldigBeregningsgrunnlag -> KunneIkkeBeregneOgSimulereRevurdering.UgyldigBeregningsgrunnlag(
                                    it.reason,
                                )
                                is Revurdering.KunneIkkeBeregneRevurdering.UfullstendigVilkårsvurdering -> KunneIkkeBeregneOgSimulereRevurdering.UfullstendigVilkårsvurdering
                            }.left()
                        }
                ) {
                    is BeregnetRevurdering.IngenEndring -> {
                        revurderingRepo.lagre(beregnetRevurdering)
                        beregnetRevurdering.right()
                    }
                    is BeregnetRevurdering.Innvilget -> {
                        utbetalingService.simulerUtbetaling(
                            sakId = beregnetRevurdering.sakId,
                            saksbehandler = saksbehandler,
                            beregning = beregnetRevurdering.beregning,
                        ).mapLeft {
                            KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet
                        }.map {
                            val simulert = beregnetRevurdering.toSimulert(it.simulering)
                            revurderingRepo.lagre(simulert)
                            simulert
                        }
                    }

                    is BeregnetRevurdering.Opphørt -> {
                        utbetalingService.simulerOpphør(
                            sakId = beregnetRevurdering.sakId,
                            saksbehandler = saksbehandler,
                            opphørsdato = beregnetRevurdering.periode.fraOgMed,
                        ).mapLeft {
                            KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet
                        }.map {
                            val simulert = beregnetRevurdering.toSimulert(it.simulering)
                            revurderingRepo.lagre(simulert)
                            simulert
                        }
                    }
                }
            }
            null -> return KunneIkkeBeregneOgSimulereRevurdering.FantIkkeRevurdering.left()
            else -> return KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand(
                originalRevurdering::class,
                SimulertRevurdering::class,
            ).left()
        }
    }

    override fun forhåndsvarsleEllerSendTilAttestering(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        revurderingshandling: Revurderingshandling,
        fritekst: String,
    ): Either<KunneIkkeForhåndsvarsle, Revurdering> {
        val revurdering = revurderingRepo.hent(revurderingId)
        if (revurdering?.forhåndsvarsel != null) {
            return KunneIkkeForhåndsvarsle.AlleredeForhåndsvarslet.left()
        }
        return when (revurdering) {
            is SimulertRevurdering -> {
                if (revurderingshandling == Revurderingshandling.FORHÅNDSVARSLE) {
                    return sendForhåndsvarsling(revurdering, fritekst)
                }
                lagreForhåndsvarsling(revurdering, Forhåndsvarsel.IngenForhåndsvarsel)
                return sendTilAttestering(
                    SendTilAttesteringRequest(
                        revurderingId = revurderingId,
                        saksbehandler = saksbehandler,
                        fritekstTilBrev = fritekst,
                        skalFøreTilBrevutsending = true,
                    ),
                ).mapLeft {
                    KunneIkkeForhåndsvarsle.Attestering(it)
                }
            }
            null -> KunneIkkeForhåndsvarsle.FantIkkeRevurdering.left()
            else -> KunneIkkeForhåndsvarsle.UgyldigTilstand(
                revurdering::class,
                SimulertRevurdering::class,
            ).left()
        }
    }

    override fun lagBrevutkastForForhåndsvarsling(
        revurderingId: UUID,
        fritekst: String,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering.left()

        val person = personService.hentPerson(revurdering.fnr).getOrElse {
            log.error("Fant ikke person for revurdering: ${revurdering.id}")
            return KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson.left()
        }

        val saksbehandlerNavn = microsoftGraphApiClient.hentNavnForNavIdent(revurdering.saksbehandler).getOrElse {
            log.error("Fant ikke saksbehandlernavn for saksbehandler: ${revurdering.saksbehandler}")
            return KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
        }

        val brevRequest = LagBrevRequest.Forhåndsvarsel(
            person = person,
            fritekst = fritekst,
            saksbehandlerNavn = saksbehandlerNavn,
        )

        return brevService.lagBrev(brevRequest)
            .mapLeft { KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast }
    }

    override fun sendTilAttestering(
        request: SendTilAttesteringRequest,
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering> {
        val revurdering = revurderingRepo.hent(request.revurderingId)
            ?: return KunneIkkeSendeRevurderingTilAttestering.FantIkkeRevurdering.left()

        if (revurdering is SimulertRevurdering && revurdering.harSimuleringFeilutbetaling()) {
            return KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left()
        }

        if (!(revurdering is SimulertRevurdering || revurdering is UnderkjentRevurdering || revurdering is BeregnetRevurdering.IngenEndring)) {
            return KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
                revurdering::class,
                RevurderingTilAttestering::class,
            ).left()
        }

        if (!(revurdering is BeregnetRevurdering.IngenEndring || revurdering.forhåndsvarsel.erKlarForAttestering())) {
            return KunneIkkeSendeRevurderingTilAttestering.ManglerBeslutningPåForhåndsvarsel.left()
        }

        val aktørId = personService.hentAktørId(revurdering.fnr).getOrElse {
            log.error("Fant ikke aktør-id")
            return KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId.left()
        }

        val tilordnetRessurs = revurderingRepo.hentEventuellTidligereAttestering(request.revurderingId)?.attestant

        val oppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.AttesterRevurdering(
                saksnummer = revurdering.saksnummer,
                aktørId = aktørId,
                // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
                tilordnetRessurs = tilordnetRessurs,
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
                request.saksbehandler,
                request.fritekstTilBrev,
                if (revurdering.revurderingsårsak.årsak == REGULER_GRUNNBELØP) false else request.skalFøreTilBrevutsending,
            )
            is SimulertRevurdering.Innvilget -> revurdering.tilAttestering(
                oppgaveId,
                request.saksbehandler,
                request.fritekstTilBrev,
                revurdering.forhåndsvarsel!!,
            )
            is SimulertRevurdering.Opphørt -> revurdering.tilAttestering(
                oppgaveId,
                request.saksbehandler,
                revurdering.forhåndsvarsel!!,
                request.fritekstTilBrev,
            ).getOrElse {
                return KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør.left()
            }
            is UnderkjentRevurdering.IngenEndring -> revurdering.tilAttestering(
                oppgaveId,
                request.saksbehandler,
                request.fritekstTilBrev,
                if (revurdering.revurderingsårsak.årsak == REGULER_GRUNNBELØP) false else request.skalFøreTilBrevutsending,
            )
            is UnderkjentRevurdering.Opphørt -> revurdering.tilAttestering(
                oppgaveId,
                request.saksbehandler,
                request.fritekstTilBrev,
            ).getOrElse {
                return KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør.left()
            }
            is UnderkjentRevurdering.Innvilget -> revurdering.tilAttestering(
                oppgaveId,
                request.saksbehandler,
                request.fritekstTilBrev,
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

    override fun lagBrevutkast(
        revurderingId: UUID,
        fritekst: String,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
        return hentBrevutkast(revurderingId, fritekst)
    }

    override fun hentBrevutkast(revurderingId: UUID): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
        return hentBrevutkast(revurderingId, null)
    }

    private fun hentBrevutkast(
        revurderingId: UUID,
        fritekst: String?,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering.left()

        return LagBrevRequestVisitor(
            hentPerson = { fnr ->
                personService.hentPerson(fnr)
                    .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson }
            },
            hentNavn = { ident ->
                microsoftGraphApiClient.hentNavnForNavIdent(ident)
                    .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant }
            },
            clock = clock,
        ).let {
            val r = if (fritekst != null) {
                revurdering.medFritekst(fritekst)
            } else {
                revurdering
            }
            r.accept(it)
            it.brevRequest
        }.mapLeft {
            when (it) {
                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson -> KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson
            }
        }.flatMap {
            brevService.lagBrev(it).mapLeft { KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast }
        }
    }

    override fun iverksett(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
        var utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering? = null

        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeIverksetteRevurdering.FantIkkeRevurdering.left()

        return when (revurdering) {
            is RevurderingTilAttestering -> {
                val iverksattRevurdering = when (revurdering) {
                    is RevurderingTilAttestering.IngenEndring -> {

                        revurdering.tilIverksatt(attestant)
                            .map { iverksattRevurdering ->
                                if (revurdering.skalFøreTilBrevutsending) {
                                    ferdigstillVedtakService.journalførOgLagre(Vedtak.from(iverksattRevurdering, clock))
                                        .map { journalførtVedtak ->
                                            ferdigstillVedtakService.distribuerOgLagre(journalførtVedtak).mapLeft {
                                                KunneIkkeIverksetteRevurdering.KunneIkkeDistribuereBrev
                                            }
                                        }.mapLeft {
                                            KunneIkkeIverksetteRevurdering.KunneIkkeJournaleføreBrev
                                        }
                                } else {
                                    vedtakRepo.lagre(Vedtak.from(iverksattRevurdering, clock))
                                }
                                iverksattRevurdering
                            }
                    }
                    is RevurderingTilAttestering.Innvilget -> {
                        revurdering.tilIverksatt(attestant) {
                            utbetalingService.utbetal(
                                sakId = revurdering.sakId,
                                beregning = revurdering.beregning,
                                simulering = revurdering.simulering,
                                attestant = attestant,
                            ).mapLeft {
                                when (it) {
                                    KunneIkkeUtbetale.KunneIkkeSimulere -> RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.KunneIkkeSimulere
                                    KunneIkkeUtbetale.Protokollfeil -> RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.Protokollfeil
                                    KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                                }
                            }.map {
                                // Dersom vi skal unngå denne hacken må Iverksatt.Innvilget innholde denne istedenfor kun IDen
                                utbetaling = it
                                it.id
                            }
                        }.map {
                            vedtakRepo.lagre(Vedtak.from(it, utbetaling!!.id))
                            it
                        }
                    }
                    is RevurderingTilAttestering.Opphørt -> {
                        revurdering.tilIverksatt(attestant) {
                            utbetalingService.opphør(
                                sakId = revurdering.sakId,
                                attestant = attestant,
                                opphørsdato = revurdering.periode.fraOgMed,
                                simulering = revurdering.simulering,
                            ).mapLeft {
                                when (it) {
                                    KunneIkkeUtbetale.KunneIkkeSimulere -> RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.KunneIkkeSimulere
                                    KunneIkkeUtbetale.Protokollfeil -> RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.Protokollfeil
                                    KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                                }
                            }.map {
                                // Dersom vi skal unngå denne hacken må Iverksatt.Innvilget innholde denne istedenfor kun IDen
                                utbetaling = it
                                it.id
                            }
                        }.map {
                            vedtakRepo.lagre(Vedtak.from(it, utbetaling!!.id))
                            it
                        }
                    }
                }.getOrHandle {
                    return when (it) {
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.KunneIkkeSimulere -> KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulere
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.Protokollfeil -> KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulere
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale
                    }.left()
                }

                revurderingRepo.lagre(iverksattRevurdering)
                observers.forEach { observer ->
                    observer.handle(
                        Event.Statistikk.RevurderingStatistikk.RevurderingIverksatt(iverksattRevurdering),
                    )
                }
                return iverksattRevurdering.right()
            }
            else -> KunneIkkeIverksetteRevurdering.UgyldigTilstand(revurdering::class, IverksattRevurdering::class)
                .left()
        }
    }

    override fun underkjenn(
        revurderingId: UUID,
        attestering: Attestering.Underkjent,
    ): Either<KunneIkkeUnderkjenneRevurdering, UnderkjentRevurdering> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeUnderkjenneRevurdering.FantIkkeRevurdering.left()

        if (revurdering !is RevurderingTilAttestering) {
            return KunneIkkeUnderkjenneRevurdering.UgyldigTilstand(
                revurdering::class,
                RevurderingTilAttestering::class,
            ).left()
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
            .flatMap {
                if (it !is SimulertRevurdering) {
                    Either.Left(FortsettEtterForhåndsvarselFeil.RevurderingErIkkeIRiktigTilstand)
                } else {
                    when (val forhåndsvarsel = it.forhåndsvarsel) {
                        null ->
                            Either.Left(FortsettEtterForhåndsvarselFeil.RevurderingErIkkeForhåndsvarslet)
                        is Forhåndsvarsel.SkalForhåndsvarsles.Besluttet ->
                            Either.Left(FortsettEtterForhåndsvarselFeil.AlleredeBesluttet)
                        is Forhåndsvarsel.IngenForhåndsvarsel ->
                            Either.Left(FortsettEtterForhåndsvarselFeil.AlleredeBesluttet)
                        is Forhåndsvarsel.SkalForhåndsvarsles.Sendt ->
                            Either.Right(Pair(it, forhåndsvarsel))
                    }
                }
            }
            .map { (revurdering, eksisterendeForhåndsvarsel) ->
                val forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                    journalpostId = eksisterendeForhåndsvarsel.journalpostId,
                    brevbestillingId = eksisterendeForhåndsvarsel.brevbestillingId,
                    valg = utledBeslutningEtterForhåndsvarling(request),
                    begrunnelse = request.begrunnelse,
                )
                revurderingRepo.oppdaterForhåndsvarsel(
                    id = revurdering.id,
                    forhåndsvarsel = forhåndsvarsel,
                )

                revurdering
            }
            .flatMap { revurdering ->
                when (request) {
                    is FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger -> {
                        sendTilAttestering(
                            SendTilAttesteringRequest(
                                revurderingId = revurdering.id,
                                saksbehandler = request.saksbehandler,
                                fritekstTilBrev = request.fritekstTilBrev,
                                skalFøreTilBrevutsending = true,
                            ),
                        ).mapLeft { FortsettEtterForhåndsvarselFeil.Attestering(it) }
                    }
                    is FortsettEtterForhåndsvarslingRequest.FortsettMedAndreOpplysninger -> {
                        // Her er allerede revurderingen i riktig tilstand
                        Either.Right(revurdering)
                    }
                    is FortsettEtterForhåndsvarslingRequest.AvsluttUtenEndringer -> {
                        TODO("Not yet implemented")
                    }
                }
            }
    }

    private fun sendForhåndsvarsling(
        revurdering: SimulertRevurdering,
        fritekst: String,
    ): Either<KunneIkkeForhåndsvarsle, Revurdering> {
        val person = personService.hentPerson(revurdering.fnr).getOrElse {
            log.error("Fant ikke person for revurdering: ${revurdering.id}")
            return KunneIkkeForhåndsvarsle.FantIkkePerson.left()
        }

        val saksbehandlerNavn = microsoftGraphApiClient.hentNavnForNavIdent(revurdering.saksbehandler).getOrElse {
            log.error("Fant ikke saksbehandlernavn for saksbehandler: ${revurdering.saksbehandler}")
            return KunneIkkeForhåndsvarsle.KunneIkkeHenteNavnForSaksbehandler.left()
        }

        brevService.journalførBrev(
            request = LagBrevRequest.Forhåndsvarsel(
                person = person,
                saksbehandlerNavn = saksbehandlerNavn,
                fritekst = fritekst,
            ),
            saksnummer = revurdering.saksnummer,
        ).mapLeft {
            log.error("Kunne ikke forhåndsvarsle bruker ${revurdering.id} fordi journalføring feilet")
            return KunneIkkeForhåndsvarsle.KunneIkkeJournalføre.left()
        }.flatMap { journalpostId ->
            val forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt(journalpostId, null)

            revurdering.forhåndsvarsel = forhåndsvarsel

            brevService.distribuerBrev(journalpostId)
                .mapLeft {
                    revurderingRepo.lagre(revurdering)
                    log.error("Revurdering ${revurdering.id} med journalpostId $journalpostId. Det skjedde en feil ved brevbestilling som må følges opp manuelt")
                    return KunneIkkeForhåndsvarsle.KunneIkkeDistribuere.left()
                }
                .map { brevbestillingId ->
                    revurdering.forhåndsvarsel = forhåndsvarsel.copy(brevbestillingId = brevbestillingId)

                    revurderingRepo.lagre(revurdering)
                    log.info("Revurdering ${revurdering.id} med journalpostId $journalpostId og bestilt brev $brevbestillingId")

                    revurdering
                }
        }

        oppgaveService.oppdaterOppgave(
            oppgaveId = revurdering.oppgaveId,
            beskrivelse = "Forhåndsvarsel er sendt.",
        ).mapLeft {
            return KunneIkkeForhåndsvarsle.KunneIkkeOppretteOppgave.left()
        }

        return revurdering.right()
    }

    private fun kanOppretteEllerOppdatereRevurderingsPeriodeOgEllerÅrsak(
        revurderingsårsak: Revurderingsårsak,
        fraOgMed: LocalDate,
    ): Boolean {
        val dagensDato = LocalDate.now(clock)
        val startenAvForrigeKalenderMåned = dagensDato.minusMonths(1).startOfMonth()

        val regulererGVerdiTilbakeITid =
            revurderingsårsak.årsak == REGULER_GRUNNBELØP && !fraOgMed.isBefore(
                startenAvForrigeKalenderMåned,
            )

        if (regulererGVerdiTilbakeITid || fraOgMed.isAfter(dagensDato.endOfMonth())) {
            return true
        }
        return false
    }

    private fun lagreForhåndsvarsling(
        revurdering: SimulertRevurdering,
        forhåndsvarsel: Forhåndsvarsel,
    ): Revurdering {
        revurderingRepo.oppdaterForhåndsvarsel(revurdering.id, forhåndsvarsel)
        revurdering.forhåndsvarsel = forhåndsvarsel
        return revurdering
    }

    private fun utledBeslutningEtterForhåndsvarling(req: FortsettEtterForhåndsvarslingRequest) =
        when (req) {
            is FortsettEtterForhåndsvarslingRequest.AvsluttUtenEndringer -> BeslutningEtterForhåndsvarsling.AvsluttUtenEndringer
            is FortsettEtterForhåndsvarslingRequest.FortsettMedAndreOpplysninger -> BeslutningEtterForhåndsvarsling.FortsettMedAndreOpplysninger
            is FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger -> BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger
        }
}
