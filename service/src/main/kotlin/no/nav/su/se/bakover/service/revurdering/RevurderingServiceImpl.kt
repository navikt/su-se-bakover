package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.fradrag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IdentifiserRevurderingsopphørSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeHentePersonEllerSaksbehandlerNavn
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLageBrevutkastForAvsluttingAvRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilBrevvalg
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeOppdatereTilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.domain.revurdering.LeggTilBosituasjonerRequest
import no.nav.su.se.bakover.domain.revurdering.LeggTilBrevvalgRequest
import no.nav.su.se.bakover.domain.revurdering.OppdaterTilbakekrevingsbehandlingRequest
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.Varselmelding
import no.nav.su.se.bakover.domain.revurdering.VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingRequest
import no.nav.su.se.bakover.domain.revurdering.opphør.AnnullerKontrollsamtaleVedOpphørService
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.iverksett.iverksettRevurdering
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForOpphør
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
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
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.util.UUID

class RevurderingServiceImpl(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val identClient: IdentClient,
    private val brevService: BrevService,
    private val clock: Clock,
    private val vedtakRepo: VedtakRepo,
    private val annullerKontrollsamtaleService: AnnullerKontrollsamtaleVedOpphørService,
    private val sessionFactory: SessionFactory,
    private val formuegrenserFactory: FormuegrenserFactory,
    private val sakService: SakService,
    private val tilbakekrevingService: TilbakekrevingService,
    private val satsFactory: SatsFactory,
) : RevurderingService {

    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<StatistikkEventObserver> = observers.toList()

    override fun hentRevurdering(revurderingId: UUID): AbstraktRevurdering? {
        return revurderingRepo.hent(revurderingId)
    }

    override fun opprettRevurdering(
        command: OpprettRevurderingCommand,
    ): Either<KunneIkkeOppretteRevurdering, OpprettetRevurdering> {
        return sakService.hentSak(command.sakId).orNull()!!
            .opprettRevurdering(
                command = command,
                clock = clock,
            ).map {
                val oppgaveId = personService.hentAktørId(it.fnr).getOrHandle {
                    return KunneIkkeOppretteRevurdering.FantIkkeAktørId(it).left()
                }.let { aktørId ->
                    oppgaveService.opprettOppgave(
                        it.oppgaveConfig(aktørId),
                    ).getOrHandle {
                        return KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave(it).left()
                    }
                }
                it.leggTilOppgaveId(oppgaveId)
            }.map {
                revurderingRepo.lagre(it.opprettetRevurdering)
                observers.notify(it.statistikkHendelse)
                it.opprettetRevurdering
            }
    }

    override fun leggTilUførevilkår(
        request: LeggTilUførevurderingerRequest,
    ): Either<KunneIkkeLeggeTilUføreVilkår, RevurderingOgFeilmeldingerResponse> {
        val revurdering =
            hent(request.behandlingId).getOrHandle { return KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling.left() }

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
        val revurdering =
            hent(request.behandlingId).getOrHandle { return KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left() }

        val utenlandsoppholdVilkår = request.tilVilkår(clock).getOrHandle {
            return it.tilService()
        }

        return revurdering.oppdaterUtenlandsoppholdOgMarkerSomVurdert(utenlandsoppholdVilkår).mapLeft {
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
            revurdering.oppdaterOpplysningspliktOgMarkerSomVurdert(request.vilkår).mapLeft {
                KunneIkkeLeggeTilOpplysningsplikt.Revurdering(it)
            }.map {
                revurderingRepo.lagre(it)
                identifiserFeilOgLagResponse(it)
            }
        }
    }

    override fun leggTilPensjonsVilkår(request: LeggTilPensjonsVilkårRequest): Either<KunneIkkeLeggeTilPensjonsVilkår, RevurderingOgFeilmeldingerResponse> {
        return hent(request.behandlingId).mapLeft {
            KunneIkkeLeggeTilPensjonsVilkår.FantIkkeBehandling
        }.flatMap { revurdering ->
            revurdering.oppdaterPensjonsvilkårOgMarkerSomVurdert(request.vilkår).mapLeft {
                KunneIkkeLeggeTilPensjonsVilkår.Revurdering(it)
            }.map {
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
            revurdering.oppdaterFlyktningvilkårOgMarkerSomVurdert(request.vilkår).mapLeft {
                KunneIkkeLeggeTilFlyktningVilkår.Revurdering(it)
            }.map {
                revurderingRepo.lagre(it)
                identifiserFeilOgLagResponse(it)
            }
        }
    }

    override fun leggTilFastOppholdINorgeVilkår(request: LeggTilFastOppholdINorgeRequest): Either<KunneIkkeLeggeFastOppholdINorgeVilkår, RevurderingOgFeilmeldingerResponse> {
        return hent(request.behandlingId).mapLeft {
            KunneIkkeLeggeFastOppholdINorgeVilkår.FantIkkeBehandling
        }.flatMap { revurdering ->
            revurdering.oppdaterFastOppholdINorgeOgMarkerSomVurdert(request.vilkår).mapLeft {
                KunneIkkeLeggeFastOppholdINorgeVilkår.Revurdering(it)
            }.map {
                revurderingRepo.lagre(it)
                identifiserFeilOgLagResponse(it)
            }
        }
    }

    override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, RevurderingOgFeilmeldingerResponse> {
        val revurdering =
            hent(request.behandlingId).getOrHandle { return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left() }

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

    override fun leggTilPersonligOppmøteVilkår(request: LeggTilPersonligOppmøteVilkårRequest): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, RevurderingOgFeilmeldingerResponse> {
        return hent(request.behandlingId).mapLeft {
            KunneIkkeLeggeTilPersonligOppmøteVilkår.FantIkkeBehandling
        }.flatMap { revurdering ->
            revurdering.oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(request.vilkår).mapLeft {
                KunneIkkeLeggeTilPersonligOppmøteVilkår.Revurdering(it)
            }.map {
                revurderingRepo.lagre(it)
                identifiserFeilOgLagResponse(it)
            }
        }
    }

    override fun leggTilBosituasjongrunnlag(request: LeggTilBosituasjonerRequest): Either<KunneIkkeLeggeTilBosituasjongrunnlag, RevurderingOgFeilmeldingerResponse> {
        val revurdering =
            hent(request.revurderingId).getOrHandle { return KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling.left() }

        val bosituasjongrunnlag = request.toDomain(
            clock = clock,
        ) {
            personService.hentPerson(it)
        }.getOrHandle {
            return it.left()
        }

        return revurdering.oppdaterBosituasjonOgMarkerSomVurdert(bosituasjongrunnlag).mapLeft {
            KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeLeggeTilBosituasjon(it)
        }.map {
            revurderingRepo.lagre(it)
            identifiserFeilOgLagResponse(it)
        }
    }

    override fun leggTilFormuegrunnlag(
        request: LeggTilFormuevilkårRequest,
    ): Either<KunneIkkeLeggeTilFormuegrunnlag, RevurderingOgFeilmeldingerResponse> {
        val revurdering =
            hent(request.behandlingId).getOrHandle { return KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering.left() }

        // TODO("flere_satser mulig å gjøre noe for å unngå casting?")
        @Suppress("UNCHECKED_CAST")
        val bosituasjon =
            revurdering.grunnlagsdata.bosituasjon as List<Grunnlag.Bosituasjon.Fullstendig>

        val vilkår = request.toDomain(bosituasjon, revurdering.periode, clock, formuegrenserFactory).getOrHandle {
            return KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet(it).left()
        }
        return revurdering.oppdaterFormueOgMarkerSomVurdert(vilkår).mapLeft {
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

    override fun leggTilInstitusjonsoppholdVilkår(
        request: LeggTilInstitusjonsoppholdVilkårRequest,
    ): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, RevurderingOgFeilmeldingerResponse> {
        val revurdering =
            hent(request.behandlingId).getOrElse { return KunneIkkeLeggeTilInstitusjonsoppholdVilkår.FantIkkeBehandling.left() }

        return revurdering.oppdaterInstitusjonsoppholdOgMarkerSomVurdert(request.vilkår).mapLeft {
            KunneIkkeLeggeTilInstitusjonsoppholdVilkår.Revurdering(it)
        }.map {
            revurderingRepo.lagre(it)
            identifiserFeilOgLagResponse(it)
        }
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
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
        return sakService.hentSakForRevurdering(oppdaterRevurderingRequest.revurderingId)
            .oppdaterRevurdering(
                revurderingId = oppdaterRevurderingRequest.revurderingId,
                periode = oppdaterRevurderingRequest.periode,
                saksbehandler = oppdaterRevurderingRequest.saksbehandler,
                revurderingsårsak = oppdaterRevurderingRequest.revurderingsårsak.getOrHandle {
                    return when (it) {
                        Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigBegrunnelse -> {
                            KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse
                        }

                        Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak -> {
                            KunneIkkeOppdatereRevurdering.UgyldigÅrsak
                        }
                    }.left()
                },
                informasjonSomRevurderes = InformasjonSomRevurderes.tryCreate(
                    revurderingsteg = oppdaterRevurderingRequest.informasjonSomRevurderes,
                ).getOrHandle {
                    return KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
                },
                clock = clock,
            ).mapLeft {
                KunneIkkeOppdatereRevurdering.FeilVedOppdateringAvRevurdering(it)
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
                val gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
                    periode = originalRevurdering.periode,
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
                        (gammel.grunnlagsdata.bosituasjon.any { it.harEPS() } && beregnetRevurdering.grunnlagsdata.bosituasjon.none { it.harEPS() }) to Varselmelding.FradragOgFormueForEPSErFjernet
                    },
                )

                when (beregnetRevurdering) {
                    is BeregnetRevurdering.Innvilget -> {
                        beregnetRevurdering.simuler(
                            saksbehandler = saksbehandler,
                            clock = clock,
                            simuler = { beregning, uføregrunnlag ->
                                sak.lagNyUtbetaling(
                                    saksbehandler = saksbehandler,
                                    beregning = beregning,
                                    clock = clock,
                                    utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                                    uføregrunnlag = uføregrunnlag,
                                ).let {
                                    sak.simulerUtbetaling(
                                        utbetalingForSimulering = it,
                                        periode = beregnetRevurdering.periode,
                                        simuler = utbetalingService::simulerUtbetaling,
                                        kontrollerMotTidligereSimulering = null,
                                        clock = clock,
                                    ).map { simulertUtbetaling ->
                                        simulertUtbetaling.simulering
                                    }
                                }
                            },
                        ).mapLeft {
                            KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(it)
                        }.map { simulert ->
                            revurderingRepo.lagre(simulert)
                            identifiserFeilOgLagResponse(simulert).leggTil(potensielleVarsel)
                        }
                    }

                    is BeregnetRevurdering.Opphørt -> {
                        // TODO er tanken at vi skal oppdatere saksbehandler her? Det kan se ut som vi har tenkt det, men aldri fullført.
                        beregnetRevurdering.simuler(
                            saksbehandler = saksbehandler,
                            clock = clock,
                            simuler = { opphørsperiode: Periode, behandler: NavIdentBruker.Saksbehandler ->
                                sak.lagUtbetalingForOpphør(
                                    opphørsperiode = opphørsperiode,
                                    behandler = behandler,
                                    clock = clock,
                                ).let {
                                    sak.simulerUtbetaling(
                                        utbetalingForSimulering = it,
                                        periode = opphørsperiode,
                                        simuler = utbetalingService::simulerUtbetaling,
                                        kontrollerMotTidligereSimulering = beregnetRevurdering.simulering,
                                        clock = clock,
                                    )
                                }
                            },
                        ).mapLeft {
                            KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(it)
                        }.map { simulert ->
                            revurderingRepo.lagre(simulert)
                            identifiserFeilOgLagResponse(simulert).leggTil(potensielleVarsel)
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
        fritekst: String,
    ): Either<KunneIkkeForhåndsvarsle, Revurdering> {
        val revurdering = hent(revurderingId).getOrHandle { return KunneIkkeForhåndsvarsle.FantIkkeRevurdering.left() }
        kanSendesTilAttestering(revurdering).getOrHandle {
            return KunneIkkeForhåndsvarsle.Attestering(it).left()
        }
        return hentPersonOgSaksbehandlerNavn(
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
                KunneIkkeForhåndsvarsle.UgyldigTilstand
            }.flatMap { forhåndsvarselBrev ->
                forhåndsvarselBrev.tilDokument(clock) {
                    brevService.lagBrev(it).mapLeft {
                        LagBrevRequest.KunneIkkeGenererePdf
                    }
                }.mapLeft {
                    KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument
                }.flatMap { dokumentUtenMetadata ->
                    Either.catch {
                        sessionFactory.withTransactionContext { tx ->
                            brevService.lagreDokument(
                                dokument = dokumentUtenMetadata.leggTilMetadata(
                                    Dokument.Metadata(
                                        sakId = revurdering.sakId,
                                        revurderingId = revurdering.id,
                                        bestillBrev = true,
                                    ),
                                ),
                                transactionContext = tx,
                            )
                            revurderingRepo.lagre(
                                revurdering = revurdering,
                                transactionContext = tx,
                            )
                            prøvÅOppdatereOppgaveEtterViHarSendtForhåndsvarsel(
                                revurderingId = revurdering.id,
                                oppgaveId = revurdering.oppgaveId,
                            ).tapLeft {
                                throw KunneIkkeOppdatereOppgave()
                            }
                            log.info("Forhåndsvarsel sendt for revurdering ${revurdering.id}")
                            revurdering
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
        return hent(revurderingId).mapLeft {
            KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering
        }.flatMap { revurdering ->

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
                    KunneIkkeLageBrevutkastForRevurdering.UgyldigTilstand
                }.flatMap {
                    brevService.lagBrev(it).mapLeft {
                        KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast
                    }
                }
            }
        }
    }

    override fun oppdaterTilbakekrevingsbehandling(request: OppdaterTilbakekrevingsbehandlingRequest): Either<KunneIkkeOppdatereTilbakekrevingsbehandling, SimulertRevurdering> {
        val revurdering =
            hent(request.revurderingId).getOrHandle { return KunneIkkeOppdatereTilbakekrevingsbehandling.FantIkkeRevurdering.left() }

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
        return hent(request.revurderingId).mapLeft { return KunneIkkeSendeRevurderingTilAttestering.FantIkkeRevurdering.left() }
            .flatMap {
                sendTilAttestering(
                    revurdering = it,
                    saksbehandler = request.saksbehandler,
                )
            }
    }

    private fun sendTilAttestering(
        revurdering: Revurdering,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering> {
        kanSendesTilAttestering(revurdering).getOrHandle {
            return it.left()
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
        val (tilAttestering, statistikkhendelse) = when (revurdering) {
            is SimulertRevurdering.Innvilget -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
            ).getOrHandle {
                return KunneIkkeSendeRevurderingTilAttestering.FeilInnvilget(it).left()
            }.let {
                Pair(it, StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget(it))
            }

            is SimulertRevurdering.Opphørt -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
            ).getOrHandle {
                return KunneIkkeSendeRevurderingTilAttestering.FeilOpphørt(it).left()
            }.let {
                Pair(it, StatistikkEvent.Behandling.Revurdering.TilAttestering.Opphør(it))
            }

            is UnderkjentRevurdering.Opphørt -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
            ).getOrElse {
                return KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør.left()
            }.let {
                Pair(it, StatistikkEvent.Behandling.Revurdering.TilAttestering.Opphør(it))
            }

            is UnderkjentRevurdering.Innvilget -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
            ).let {
                Pair(it, StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget(it))
            }

            else -> return KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
                revurdering::class,
                RevurderingTilAttestering::class,
            ).left()
        }

        revurderingRepo.lagre(tilAttestering)
        statistikkhendelse.also {
            observers.notify(it)
        }
        return tilAttestering.right()
    }

    private fun kanSendesTilAttestering(revurdering: Revurdering): Either<KunneIkkeSendeRevurderingTilAttestering, Unit> {
        val sak = sakService.hentSakForRevurdering(revurderingId = revurdering.id)
        val gjeldendeMånedsberegninger = sak.hentGjeldendeMånedsberegninger(
            periode = revurdering.periode,
            clock = clock,
        )

        tilbakekrevingService.hentAvventerKravgrunnlag(revurdering.sakId).ifNotEmpty {
            return KunneIkkeSendeRevurderingTilAttestering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving(
                revurderingId = this.first().avgjort.revurderingId,
            ).left()
        }

        return when (revurdering) {
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
            else -> KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
                fra = revurdering::class,
                til = RevurderingTilAttestering::class,
            ).left()
        }
    }

    override fun leggTilBrevvalg(request: LeggTilBrevvalgRequest): Either<KunneIkkeLeggeTilBrevvalg, Revurdering> {
        return hent(request.revurderingId)
            .mapLeft { KunneIkkeLeggeTilBrevvalg.FantIkkeRevurdering }
            .flatMap {
                it.leggTilBrevvalg(request.toDomain())
                    .mapLeft { feil -> KunneIkkeLeggeTilBrevvalg.Feil(feil) }
                    .map { medBrevvalg ->
                        revurderingRepo.lagre(medBrevvalg)
                        medBrevvalg
                    }
            }
    }

    override fun lagBrevutkastForRevurdering(
        revurderingId: UUID,
        fritekst: String?,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
        return hent(revurderingId)
            .mapLeft { KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering }
            .flatMap { revurdering ->
                brevService.lagDokument(revurdering)
                    .mapLeft {
                        when (it) {
                            KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeFinneGjeldendeUtbetaling
                            KunneIkkeLageDokument.KunneIkkeGenererePDF -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast
                            KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                            KunneIkkeLageDokument.KunneIkkeHentePerson -> KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson
                            KunneIkkeLageDokument.DetSkalIkkeSendesBrev -> KunneIkkeLageBrevutkastForRevurdering.DetSkalIkkeSendesBrev
                        }
                    }
                    .map { it.generertDokument }
            }
    }

    override fun iverksett(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
        return sakService.hentSakForRevurdering(revurderingId).iverksettRevurdering(
            revurderingId = revurderingId,
            attestant = attestant,
            clock = clock,
            simuler = utbetalingService::simulerUtbetaling,
        ).mapLeft {
            KunneIkkeIverksetteRevurdering.FeilVedIverksettelse(it)
        }.flatMap {
            it.ferdigstillIverksettelseITransaksjon(
                sessionFactory = sessionFactory,
                klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
                lagreVedtak = vedtakRepo::lagreITransaksjon,
                lagreRevurdering = revurderingRepo::lagre,
                statistikkObservers = { observers },
                annullerKontrollsamtale = { sakId, tx ->
                    annullerKontrollsamtaleService.annuller(sakId, tx)
                },
            ).mapLeft {
                KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(it)
            }
        }
    }

    override fun underkjenn(
        revurderingId: UUID,
        attestering: Attestering.Underkjent,
    ): Either<KunneIkkeUnderkjenneRevurdering, UnderkjentRevurdering> {
        val revurdering =
            hent(revurderingId).getOrHandle { return KunneIkkeUnderkjenneRevurdering.FantIkkeRevurdering.left() }

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

        oppgaveService.lukkOppgave(eksisterendeOppgaveId).mapLeft {
            log.error("Kunne ikke lukke attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av revurdering $revurderingId. Dette må gjøres manuelt.")
        }.map {
            log.info("Lukket attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av revurdering $revurderingId")
        }

        when (underkjent) {
            is UnderkjentRevurdering.Innvilget -> observers.notify(
                StatistikkEvent.Behandling.Revurdering.Underkjent.Innvilget(underkjent),
            )

            is UnderkjentRevurdering.Opphørt -> observers.notify(
                StatistikkEvent.Behandling.Revurdering.Underkjent.Opphør(underkjent),
            )
        }

        return underkjent.right()
    }

    override fun avsluttRevurdering(
        revurderingId: UUID,
        begrunnelse: String,
        brevvalg: Brevvalg.SaksbehandlersValg?,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeAvslutteRevurdering, AbstraktRevurdering> {
        return revurderingRepo.hent(revurderingId)?.let {
            avsluttRevurdering(
                revurdering = it,
                begrunnelse = begrunnelse,
                brevvalg = brevvalg,
                saksbehandler = saksbehandler,
            )
        } ?: return KunneIkkeAvslutteRevurdering.FantIkkeRevurdering.left()
    }

    /**
     * Denne kan ikke returnere [KunneIkkeAvslutteRevurdering.FantIkkeRevurdering]
     *
     * @param brevvalg Kun dersom saksbehandler har forhåndsvarslet, må det tas et brevvalg. Dersom det ikke er forhåndsvarslet skal det ikke sendes brev.
     */
    private fun avsluttRevurdering(
        revurdering: AbstraktRevurdering,
        begrunnelse: String,
        brevvalg: Brevvalg.SaksbehandlersValg?,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeAvslutteRevurdering, AbstraktRevurdering> {
        val (avsluttetRevurdering, skalSendeAvslutningsbrev) = when (revurdering) {
            is GjenopptaYtelseRevurdering -> {
                if (brevvalg != null) return KunneIkkeAvslutteRevurdering.BrevvalgIkkeTillatt.left()
                revurdering.avslutt(begrunnelse, Tidspunkt.now(clock)).map {
                    it to it.skalSendeAvslutningsbrev()
                }.getOrHandle {
                    return KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse(it).left()
                }
            }

            is StansAvYtelseRevurdering -> {
                if (brevvalg != null) return KunneIkkeAvslutteRevurdering.BrevvalgIkkeTillatt.left()
                revurdering.avslutt(begrunnelse, Tidspunkt.now(clock)).map {
                    it to it.skalSendeAvslutningsbrev()
                }.getOrHandle {
                    return KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse(it).left()
                }
            }

            is Revurdering -> revurdering.avslutt(begrunnelse, brevvalg, Tidspunkt.now(clock)).map {
                it to it.skalSendeAvslutningsbrev()
            }.getOrHandle {
                return KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetRevurdering(it).left()
            }
        }

        if (avsluttetRevurdering is Revurdering) {
            oppgaveService.lukkOppgave(avsluttetRevurdering.oppgaveId).mapLeft {
                log.error("Kunne ikke lukke oppgave ${avsluttetRevurdering.oppgaveId} ved avslutting av revurdering ${revurdering.id}. Dette må gjøres manuelt.")
            }.map {
                log.info("Lukket oppgave ${avsluttetRevurdering.oppgaveId} ved avslutting av revurdering ${revurdering.id}..")
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
        val event: StatistikkEvent? = when (val result = resultat.getOrElse { null }) {
            is AvsluttetRevurdering -> StatistikkEvent.Behandling.Revurdering.Avsluttet(result, saksbehandler)
            is GjenopptaYtelseRevurdering.AvsluttetGjenoppta -> StatistikkEvent.Behandling.Gjenoppta.Avsluttet(result)
            is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> StatistikkEvent.Behandling.Stans.Avsluttet(result)
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

        // Lager en midlertidig avsluttet revurdering for å konstruere brevet - denne skal ikke lagres
        val avsluttetRevurdering = revurdering.avslutt(
            begrunnelse = "",
            brevvalg = fritekst?.let { Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(it) },
            tidspunktAvsluttet = Tidspunkt.now(clock),
        ).getOrHandle {
            return KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeLageBrevutkast.left()
        }

        return brevService.lagDokument(avsluttetRevurdering).mapLeft {
            when (it) {
                KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeFinneGjeldendeUtbetaling
                KunneIkkeLageDokument.KunneIkkeGenererePDF -> KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeGenererePDF
                KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                KunneIkkeLageDokument.KunneIkkeHentePerson -> KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.FantIkkePerson
                KunneIkkeLageDokument.DetSkalIkkeSendesBrev -> KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.DetSkalIkkeSendesBrev
            }
        }.map {
            Pair(avsluttetRevurdering.fnr, it.generertDokument)
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
