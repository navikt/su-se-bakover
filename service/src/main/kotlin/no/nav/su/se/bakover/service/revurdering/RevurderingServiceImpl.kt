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
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.erKlarForAttestering
import no.nav.su.se.bakover.domain.revurdering.medFritekst
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
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
    private val grunnlagService: GrunnlagService,
    private val vilkårsvurderingService: VilkårsvurderingService,
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
    ): Either<KunneIkkeOppretteRevurdering, Revurdering> {
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

        val vilkårsvurderinger = vilkårsvurderingService.opprettVilkårsvurderinger(sak.id, periode)
        val grunnlag = when (val vilkårsvurderingUføre = vilkårsvurderinger.uføre) {
            Vilkår.IkkeVurdert.Uførhet -> Grunnlagsdata(uføregrunnlag = emptyList())
            is Vilkår.Vurdert.Uførhet -> Grunnlagsdata(uføregrunnlag = vilkårsvurderingUføre.grunnlag)
        }

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
                grunnlagsdata = grunnlag,
                vilkårsvurderinger = vilkårsvurderinger,
            ).also {
                revurderingRepo.lagre(it)

                vilkårsvurderingService.lagre(
                    behandlingId = it.id,
                    vilkårsvurderinger = it.vilkårsvurderinger,
                )

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
        revurderingId: UUID,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ): Either<KunneIkkeLeggeTilGrunnlag, LeggTilUføregrunnlagResponse> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling.left()

        if (revurdering is RevurderingTilAttestering || revurdering is IverksattRevurdering)
            return KunneIkkeLeggeTilGrunnlag.UgyldigStatus.left()

        if (uføregrunnlag.size != 1) {
            throw IllegalArgumentException("Flere perioder med forskjellig IEU støttes ikke enda")
        }

        val oppdatertBehandlingsinformasjon = revurdering.oppdaterBehandlingsinformasjon(
            revurdering.behandlingsinformasjon.copy(
                uførhet = revurdering.behandlingsinformasjon.uførhet!!.copy(
                    forventetInntekt = uføregrunnlag.first().forventetInntekt,
                    uføregrad = uføregrunnlag.first().uføregrad.value,
                ),
            ),
        )

        revurderingRepo.lagre(oppdatertBehandlingsinformasjon)

        vilkårsvurderingService.lagre(
            behandlingId = oppdatertBehandlingsinformasjon.id,
            vilkårsvurderinger = oppdatertBehandlingsinformasjon.vilkårsvurderinger.copy(
                uføre = Vilkår.Vurdert.Uførhet(
                    vurderingsperioder = listOf(
                        Vurderingsperiode.Manuell(
                            resultat = Resultat.Innvilget,
                            grunnlag = Grunnlag.Uføregrunnlag(
                                periode = oppdatertBehandlingsinformasjon.periode,
                                uføregrad = uføregrunnlag.first().uføregrad,
                                forventetInntekt = uføregrunnlag.first().forventetInntekt,
                            ),
                            periode = oppdatertBehandlingsinformasjon.periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        )

        val updated = revurderingRepo.hent(oppdatertBehandlingsinformasjon.id)!!

        return LeggTilUføregrunnlagResponse(
            revurdering = updated,
            simulerEndretGrunnlagsdata = grunnlagService.simulerEndretGrunnlagsdata(
                sakId = updated.sakId,
                periode = updated.periode,
                endring = updated.grunnlagsdata,
            ),
        ).right()
    }

    override fun hentUføregrunnlag(revurderingId: UUID): Either<KunneIkkeHenteGrunnlag, GrunnlagService.SimulerEndretGrunnlagsdata> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeHenteGrunnlag.FantIkkeBehandling.left()
        return grunnlagService.simulerEndretGrunnlagsdata(
            sakId = revurdering.sakId,
            periode = revurdering.periode,
            endring = revurdering.grunnlagsdata,
        ).right()
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

        return when (revurdering) {
            is OpprettetRevurdering -> revurdering.oppdater(nyPeriode, revurderingsårsak).right()
            is BeregnetRevurdering -> revurdering.oppdater(nyPeriode, revurderingsårsak).right()
            is SimulertRevurdering -> revurdering.oppdater(nyPeriode, revurderingsårsak).right()
            is UnderkjentRevurdering -> revurdering.oppdater(nyPeriode, revurderingsårsak).right()
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
        fradrag: List<Fradrag>,
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, Revurdering> {
        return when (val originalRevurdering = revurderingRepo.hent(revurderingId)) {
            is BeregnetRevurdering, is OpprettetRevurdering, is SimulertRevurdering, is UnderkjentRevurdering -> {
                when (originalRevurdering.vilkårsvurderinger.resultat) {
                    Resultat.Avslag -> {
                        val opphør = BeregnetRevurdering.Opphørt(
                            tilRevurdering = originalRevurdering.tilRevurdering,
                            id = originalRevurdering.id,
                            periode = originalRevurdering.periode,
                            opprettet = originalRevurdering.opprettet,
                            beregning = originalRevurdering.tilRevurdering.beregning,
                            saksbehandler = saksbehandler,
                            oppgaveId = originalRevurdering.oppgaveId,
                            fritekstTilBrev = originalRevurdering.fritekstTilBrev,
                            revurderingsårsak = originalRevurdering.revurderingsårsak,
                            forhåndsvarsel = originalRevurdering.forhåndsvarsel,
                            behandlingsinformasjon = originalRevurdering.behandlingsinformasjon,
                            grunnlagsdata = originalRevurdering.grunnlagsdata,
                            vilkårsvurderinger = originalRevurdering.vilkårsvurderinger,
                        )
                        utbetalingService.simulerOpphør(
                            sakId = opphør.sakId,
                            saksbehandler = opphør.saksbehandler,
                            opphørsdato = opphør.periode.fraOgMed,
                        ).mapLeft {
                            KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet
                        }.map {
                            val simulert = opphør.toSimulert(it.simulering)
                            revurderingRepo.lagre(simulert)
                            simulert
                        }
                    }
                    Resultat.Innvilget -> {
                        when (
                            val beregnetRevurdering = originalRevurdering.beregn(fradrag)
                                .getOrHandle {
                                    return when (it) {
                                        is Revurdering.KunneIkkeBeregneRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden -> KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden
                                        is Revurdering.KunneIkkeBeregneRevurdering.UfullstendigBehandlingsinformasjon -> KunneIkkeBeregneOgSimulereRevurdering.UfullstendigBehandlingsinformasjon
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
                    Resultat.Uavklart -> return KunneIkkeBeregneOgSimulereRevurdering.UfullstendigVilkårsvurdering.left()
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

        val brevRequest = LagBrevRequest.Forhåndsvarsel(
            person = person, fritekst = fritekst,
        )

        return brevService.lagBrev(brevRequest)
            .mapLeft { KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast }
    }

    override fun sendTilAttestering(
        request: SendTilAttesteringRequest,
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering> {
        val revurdering = revurderingRepo.hent(request.revurderingId)
            ?: return KunneIkkeSendeRevurderingTilAttestering.FantIkkeRevurdering.left()

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
                    Either.left(FortsettEtterForhåndsvarselFeil.RevurderingErIkkeIRiktigTilstand)
                } else {
                    when (val forhåndsvarsel = it.forhåndsvarsel) {
                        null ->
                            Either.left(FortsettEtterForhåndsvarselFeil.RevurderingErIkkeForhåndsvarslet)
                        is Forhåndsvarsel.SkalForhåndsvarsles.Besluttet ->
                            Either.left(FortsettEtterForhåndsvarselFeil.AlleredeBesluttet)
                        is Forhåndsvarsel.IngenForhåndsvarsel ->
                            Either.left(FortsettEtterForhåndsvarselFeil.AlleredeBesluttet)
                        is Forhåndsvarsel.SkalForhåndsvarsles.Sendt ->
                            Either.right(Pair(it, forhåndsvarsel))
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
                        Either.right(revurdering)
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
        val aktørId = personService.hentAktørId(revurdering.fnr).getOrElse {
            log.error("Fant ikke aktør-id for revurdering: ${revurdering.id}")
            return KunneIkkeForhåndsvarsle.FantIkkeAktørId.left()
        }

        val person = personService.hentPerson(revurdering.fnr).getOrElse {
            log.error("Fant ikke person for revurdering: ${revurdering.id}")
            return KunneIkkeForhåndsvarsle.FantIkkePerson.left()
        }

        brevService.journalførBrev(
            request = LagBrevRequest.Forhåndsvarsel(
                person = person,
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
        oppgaveService.opprettOppgave(
            OppgaveConfig.Forhåndsvarsling(
                saksnummer = revurdering.saksnummer,
                aktørId = aktørId,
                tilordnetRessurs = null,
            ),
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
