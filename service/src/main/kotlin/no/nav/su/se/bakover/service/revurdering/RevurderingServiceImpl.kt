package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.fradrag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilVedtaksbrevvalg
import no.nav.su.se.bakover.domain.revurdering.LeggTilVedtaksbrevvalg
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.attestering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.attestering.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneRevurdering
import no.nav.su.se.bakover.domain.revurdering.beregning.VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForAvsluttingAvRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.LeggTilBrevvalgRequest
import no.nav.su.se.bakover.domain.revurdering.brev.lagDokumentKommando
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.iverksettRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.oppdater.oppdaterRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.AnnullerKontrollsamtaleVedOpphørService
import no.nav.su.se.bakover.domain.revurdering.opphør.IdentifiserRevurderingsopphørSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.tilbakekreving.KunneIkkeOppdatereTilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.revurdering.tilbakekreving.OppdaterTilbakekrevingsbehandlingRequest
import no.nav.su.se.bakover.domain.revurdering.tilbakekreving.oppdaterTilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.revurdering.underkjenn.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.domain.revurdering.varsel.Varselmelding
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonerRequest
import no.nav.su.se.bakover.domain.revurdering.vilkår.formue.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.fradag.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.uføre.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.domain.revurdering.vilkår.utenlandsopphold.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.domain.sak.SakService
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
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.KunneIkkeLeggetilLovligOppholdVilkårForRevurdering
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.domain.vilkår.oppmøte.KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.pensjon.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class RevurderingServiceImpl(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val brevService: BrevService,
    private val clock: Clock,
    private val vedtakRepo: VedtakRepo,
    private val annullerKontrollsamtaleService: AnnullerKontrollsamtaleVedOpphørService,
    private val sessionFactory: SessionFactory,
    private val formuegrenserFactory: FormuegrenserFactory,
    private val sakService: SakService,
    private val tilbakekrevingService: TilbakekrevingService,
    private val satsFactory: SatsFactory,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
) : RevurderingService {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

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
        return sakService.hentSak(command.sakId).getOrNull()!!
            .opprettRevurdering(
                command = command,
                clock = clock,
            ).map {
                val oppgaveId = personService.hentAktørId(it.fnr).getOrElse {
                    return KunneIkkeOppretteRevurdering.FantIkkeAktørId(it).left()
                }.let { aktørId ->
                    oppgaveService.opprettOppgave(
                        it.oppgaveConfig(aktørId),
                    ).getOrElse {
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
            hent(request.behandlingId).getOrElse { return KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling.left() }

        val uførevilkår = request.toVilkår(
            behandlingsperiode = revurdering.periode,
            clock = clock,
        ).getOrElse {
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
            hent(request.behandlingId).getOrElse { return KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left() }

        val utenlandsoppholdVilkår = request.tilVilkår(clock).getOrElse {
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

    override fun leggTilLovligOppholdVilkår(
        request: LeggTilLovligOppholdRequest,
    ): Either<KunneIkkeLeggetilLovligOppholdVilkårForRevurdering, RevurderingOgFeilmeldingerResponse> {
        val revurdering =
            hent(request.behandlingId).getOrElse { return KunneIkkeLeggetilLovligOppholdVilkårForRevurdering.FantIkkeBehandling.left() }

        val vilkår = request.toVilkår(clock)
            .getOrElse {
                return KunneIkkeLeggetilLovligOppholdVilkårForRevurdering.UgyldigLovligOppholdVilkår(it).left()
            }

        return revurdering.oppdaterLovligOppholdOgMarkerSomVurdert(vilkår).mapLeft {
            KunneIkkeLeggetilLovligOppholdVilkårForRevurdering.Domenefeil(it)
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
            hent(request.behandlingId).getOrElse { return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left() }

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

    override fun leggTilPersonligOppmøteVilkår(request: LeggTilPersonligOppmøteVilkårRequest): Either<KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering, RevurderingOgFeilmeldingerResponse> {
        return hent(request.behandlingId).mapLeft {
            KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering.FantIkkeBehandling
        }.flatMap { revurdering ->
            revurdering.oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(request.vilkår).mapLeft {
                KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering.Underliggende(it)
            }.map {
                revurderingRepo.lagre(it)
                identifiserFeilOgLagResponse(it)
            }
        }
    }

    override fun leggTilBosituasjongrunnlag(request: LeggTilBosituasjonerRequest): Either<KunneIkkeLeggeTilBosituasjongrunnlag, RevurderingOgFeilmeldingerResponse> {
        val revurdering =
            hent(request.behandlingId).getOrElse { return KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling.left() }

        val bosituasjongrunnlag = request.toDomain(
            clock = clock,
        ) {
            personService.hentPerson(it)
        }.getOrElse {
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
            hent(request.behandlingId).getOrElse { return KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering.left() }

        // TODO("flere_satser mulig å gjøre noe for å unngå casting?")
        @Suppress("UNCHECKED_CAST")
        val bosituasjon =
            revurdering.grunnlagsdata.bosituasjon as List<Grunnlag.Bosituasjon.Fullstendig>

        val vilkår = request.toDomain(bosituasjon, revurdering.periode, formuegrenserFactory).getOrElse {
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
        command: OppdaterRevurderingCommand,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return sakService.hentSakForRevurdering(command.revurderingId)
            .oppdaterRevurdering(
                command = command,
                clock = clock,
            ).map {
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
                ).getOrElse {
                    throw IllegalStateException("Fant ikke gjeldende vedtaksdata for sak:${originalRevurdering.sakId}")
                }
                val beregnetRevurdering = originalRevurdering.beregn(
                    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                    clock = clock,
                    gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                    satsFactory = satsFactory,
                ).getOrElse {
                    return when (it) {
                        is KunneIkkeBeregneRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden -> {
                            KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden
                        }

                        is KunneIkkeBeregneRevurdering.UgyldigBeregningsgrunnlag -> {
                            KunneIkkeBeregneOgSimulereRevurdering.UgyldigBeregningsgrunnlag(it.reason)
                        }

                        KunneIkkeBeregneRevurdering.KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps -> {
                            KunneIkkeBeregneOgSimulereRevurdering.KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps
                        }

                        KunneIkkeBeregneRevurdering.AvkortingErUfullstendig -> {
                            KunneIkkeBeregneOgSimulereRevurdering.AvkortingErUfullstendig
                        }

                        KunneIkkeBeregneRevurdering.OpphørAvYtelseSomSkalAvkortes -> {
                            KunneIkkeBeregneOgSimulereRevurdering.OpphørAvYtelseSomSkalAvkortes
                        }
                    }.left()
                }

                val potensielleVarsel = listOf(
                    (
                        eksisterendeUtbetalinger.isNotEmpty() &&
                            !VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
                                eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                                nyBeregning = beregnetRevurdering.beregning,
                            ).resultat &&
                            !(beregnetRevurdering is BeregnetRevurdering.Opphørt && beregnetRevurdering.opphørSkyldesVilkår())
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
        utførtAv: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeForhåndsvarsle, Revurdering> {
        val revurdering = hent(revurderingId).getOrElse { return KunneIkkeForhåndsvarsle.FantIkkeRevurdering.left() }
        kanSendesTilAttestering(revurdering).getOrElse {
            return KunneIkkeForhåndsvarsle.Attestering(it).left()
        }
        return revurdering.lagForhåndsvarsel(
            fritekst = fritekst,
            utførtAv = utførtAv,
        ).mapLeft {
            KunneIkkeForhåndsvarsle.UgyldigTilstand
        }.flatMap { brevCommand ->
            brevService.lagDokument(brevCommand).mapLeft {
                KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument(it)
            }
        }.flatMap { dokumentUtenMetadata ->
            Either.catch {
                sessionFactory.withTransactionContext { tx ->
                    brevService.lagreDokument(
                        dokument = dokumentUtenMetadata.leggTilMetadata(
                            Dokument.Metadata(
                                sakId = revurdering.sakId,
                                revurderingId = revurdering.id,
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
                    ).onLeft {
                        log.info("Hopper over å oppdatere oppgave for revurdering ${revurdering.id}. Dette vil uansett dukke opp som en journalpost i Gosys.")
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

    private class KunneIkkeOppdatereOppgave : RuntimeException()

    private fun prøvÅOppdatereOppgaveEtterViHarSendtForhåndsvarsel(
        revurderingId: UUID,
        oppgaveId: OppgaveId,
    ): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit> {
        return oppgaveService.oppdaterOppgave(
            oppgaveId = oppgaveId,
            beskrivelse = "Forhåndsvarsel er sendt.",
        ).onLeft {
            log.error("Kunne ikke oppdatere oppgave $oppgaveId for revurdering $revurderingId med informasjon om at forhåndsvarsel er sendt")
        }.onRight {
            log.info("Oppdatert oppgave $oppgaveId for revurdering $revurderingId  med informasjon om at forhåndsvarsel er sendt")
        }
    }

    override fun lagBrevutkastForForhåndsvarsling(
        revurderingId: UUID,
        utførtAv: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, PdfA> {
        return hent(revurderingId).mapLeft {
            KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering
        }.flatMap { revurdering ->
            revurdering.lagForhåndsvarsel(
                utførtAv = utførtAv,
                fritekst = fritekst,
            ).mapLeft {
                KunneIkkeLageBrevutkastForRevurdering.UgyldigTilstand
            }.flatMap {
                brevService.lagDokument(it).mapLeft {
                    KunneIkkeLageBrevutkastForRevurdering.KunneIkkeGenererePdf(it)
                }.map { it.generertDokument }
            }
        }
    }

    override fun oppdaterTilbakekrevingsbehandling(request: OppdaterTilbakekrevingsbehandlingRequest): Either<KunneIkkeOppdatereTilbakekrevingsbehandling, Revurdering> {
        val revurdering =
            hent(request.revurderingId).getOrElse { throw IllegalArgumentException("Fant ikke revurdering ${request.revurderingId}") }

        return revurdering.oppdaterTilbakekrevingsbehandling(request, clock).onRight {
            revurderingRepo.lagre(it)
        }
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
        kanSendesTilAttestering(revurdering).getOrElse {
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
            ).getOrElse {
                return KunneIkkeSendeRevurderingTilAttestering.FeilInnvilget(it).left()
            }.let {
                Pair(it, StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget(it))
            }

            is SimulertRevurdering.Opphørt -> revurdering.tilAttestering(
                oppgaveId,
                saksbehandler,
            ).getOrElse {
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

    override fun leggTilBrevvalg(request: LeggTilBrevvalgRequest): Either<KunneIkkeLeggeTilVedtaksbrevvalg, Revurdering> {
        return hentEllerKast(request.revurderingId)
            .let {
                it as? LeggTilVedtaksbrevvalg
                    ?: return KunneIkkeLeggeTilVedtaksbrevvalg.UgyldigTilstand(it::class).left()
            }
            .let {
                it.leggTilBrevvalg(request.toDomain()).right()
                    .onRight { revurderingRepo.lagre(it) }
            }
    }

    override fun lagBrevutkastForRevurdering(
        revurderingId: UUID,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, PdfA> {
        return hent(revurderingId)
            .mapLeft { KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering }
            .flatMap { revurdering ->
                brevService.lagDokument(revurdering.lagDokumentKommando(satsFactory = satsFactory, clock = clock))
                    .mapLeft {
                        KunneIkkeLageBrevutkastForRevurdering.KunneIkkeGenererePdf(it)
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
            lagDokument = brevService::lagDokument,
            satsFactory = satsFactory,
        ).flatMap {
            it.ferdigstillIverksettelseITransaksjon(
                sessionFactory = sessionFactory,
                klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
                lagreVedtak = vedtakRepo::lagreITransaksjon,
                lagreRevurdering = revurderingRepo::lagre,
                statistikkObservers = { observers },
                annullerKontrollsamtale = { sakId, tx ->
                    annullerKontrollsamtaleService.annuller(sakId, tx)
                },
                lagreDokument = brevService::lagreDokument,
                lukkOppgave = ferdigstillVedtakService::lukkOppgaveMedBruker,
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
            hent(revurderingId).getOrElse { return KunneIkkeUnderkjenneRevurdering.FantIkkeRevurdering.left() }

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
                }.getOrElse {
                    return KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse(it).left()
                }
            }

            is StansAvYtelseRevurdering -> {
                if (brevvalg != null) return KunneIkkeAvslutteRevurdering.BrevvalgIkkeTillatt.left()
                revurdering.avslutt(begrunnelse, Tidspunkt.now(clock)).map {
                    it to it.skalSendeAvslutningsbrev()
                }.getOrElse {
                    return KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse(it).left()
                }
            }

            is Revurdering -> revurdering.avslutt(begrunnelse, brevvalg, Tidspunkt.now(clock)).map {
                it to it.skalSendeAvslutningsbrev()
            }.getOrElse {
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
            brevService.lagDokument(avsluttetRevurdering.lagDokumentKommando(satsFactory = satsFactory, clock = clock))
                .mapLeft {
                    return KunneIkkeAvslutteRevurdering.KunneIkkeLageDokument.left()
                }.map { dokument ->
                    val dokumentMedMetaData = dokument.leggTilMetadata(
                        metadata = Dokument.Metadata(
                            sakId = revurdering.sakId,
                            revurderingId = revurdering.id,
                        ),
                    )
                    sessionFactory.withTransactionContext {
                        brevService.lagreDokument(dokumentMedMetaData, it)
                        revurderingRepo.lagre(avsluttetRevurdering, it)
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
        fritekst: String,
    ): Either<KunneIkkeLageBrevutkastForAvsluttingAvRevurdering, Pair<Fnr, PdfA>> {
        val revurdering = hent(revurderingId)
            .getOrElse { return KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.FantIkkeRevurdering.left() }

        // Lager en midlertidig avsluttet revurdering for å konstruere brevet - denne skal ikke lagres
        val avsluttetRevurdering = revurdering.avslutt(
            begrunnelse = "",
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(fritekst),
            tidspunktAvsluttet = Tidspunkt.now(clock),
        ).getOrElse {
            return KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeAvslutteRevurdering(it).left()
        }

        return brevService.lagDokument(avsluttetRevurdering.lagDokumentKommando(satsFactory, clock)).mapLeft {
            KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeLageDokument(it)
        }.map {
            Pair(avsluttetRevurdering.fnr, it.generertDokument)
        }
    }

    private fun hentEllerKast(id: UUID): Revurdering {
        return hent(id).getOrElse { throw IllegalArgumentException("Fant ikke revurdering med id $id") }
    }

    private fun hent(id: UUID): Either<KunneIkkeHenteRevurdering, Revurdering> {
        return revurderingRepo.hent(id)
            ?.let { if (it is Revurdering) it.right() else KunneIkkeHenteRevurdering.IkkeInstansAvRevurdering.left() }
            ?: KunneIkkeHenteRevurdering.FantIkkeRevurdering.left()
    }

    sealed class KunneIkkeHenteRevurdering {
        data object IkkeInstansAvRevurdering : KunneIkkeHenteRevurdering()
        data object FantIkkeRevurdering : KunneIkkeHenteRevurdering()
    }
}
