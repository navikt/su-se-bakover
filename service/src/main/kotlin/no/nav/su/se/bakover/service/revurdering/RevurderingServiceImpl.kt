package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.SjekkOmGrunnlagErKonsistent
import no.nav.su.se.bakover.domain.grunnlag.harFlerEnnEnBosituasjonsperiode
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.IdentifiserSaksbehandlingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.VurderOmVilkårGirOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.erKlarForAttestering
import no.nav.su.se.bakover.domain.revurdering.medFritekst
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import java.time.Clock
import java.util.UUID

internal class RevurderingServiceImpl(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val brevService: BrevService,
    private val clock: Clock,
    private val vedtakRepo: VedtakRepo,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val vilkårsvurderingService: VilkårsvurderingService,
    private val grunnlagService: GrunnlagService,
    private val vedtakService: VedtakService,
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
                !informasjonSomRevurderes.harValgtInntekt() && it.contains(Konsistensproblem.BosituasjonOgFradrag.FlereBosituasjonerOgFradragForEPS) -> {
                    return KunneIkkeOppretteRevurdering.EpsInntektMedFlereBosituasjonsperioderMåRevurderes.left()
                }
                !informasjonSomRevurderes.harValgtFormue() && it.contains(Konsistensproblem.BosituasjonOgFormue.FlereBosituasjonerOgFormueForEPS) -> {
                    return KunneIkkeOppretteRevurdering.EpsFormueMedFlereBosituasjonsperioderMåRevurderes.left()
                }
            }
        }

        val (grunnlagsdata, vilkårsvurderinger) = fjernBosituasjonHvisIkkeEntydig(
            gjeldendeVedtaksdata,

        )

        when (val r = VurderOmVilkårGirOpphørVedRevurdering(vilkårsvurderinger).resultat) {
            is OpphørVedRevurdering.Ja -> {
                if (!informasjonSomRevurderes.harValgtFormue() && r.opphørsgrunner.contains(Opphørsgrunn.FORMUE)) {
                    return KunneIkkeOppretteRevurdering.FormueSomFørerTilOpphørMåRevurderes.left()
                }
            }
            is OpphørVedRevurdering.Nei -> Unit
        }

        val gjeldendeVedtakPåFraOgMedDato =
            gjeldendeVedtaksdata.gjeldendeVedtakPåDato(opprettRevurderingRequest.fraOgMed)
                ?: return KunneIkkeOppretteRevurdering.FantIngenVedtakSomKanRevurderes.left()

        val aktørId = personService.hentAktørId(gjeldendeVedtakPåFraOgMedDato.behandling.fnr).getOrElse {
            log.error("Fant ikke aktør-id")
            return KunneIkkeOppretteRevurdering.FantIkkeAktørId.left()
        }

        // TODO ai 25.02.2021 - Oppgaven skal egentligen ikke opprettes her. Den burde egentligen komma utifra melding av endring, som skal føres til revurdering.
        return oppgaveService.opprettOppgave(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = gjeldendeVedtakPåFraOgMedDato.behandling.saksnummer,
                aktørId = aktørId,
                tilordnetRessurs = null,
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
                forhåndsvarsel = if (revurderingsårsak.årsak == REGULER_GRUNNBELØP) Forhåndsvarsel.IngenForhåndsvarsel else null,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = Attesteringshistorikk.empty(),
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

    private fun fjernBosituasjonHvisIkkeEntydig(gjeldendeVedtaksdata: GjeldendeVedtaksdata): Pair<Grunnlagsdata, Vilkårsvurderinger> {
        val gjeldendeBosituasjon = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon

        // Dette kan oppstå når vi revurderer en revurdering. Da må vi vise eksisterende, men skal ikke preutfylle.
        val harFlerEnnEnBosituasjon = gjeldendeBosituasjon.harFlerEnnEnBosituasjonsperiode()

        val bosituasjon = when (val b = gjeldendeBosituasjon.first()) {
            is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                id = b.id,
                opprettet = b.opprettet,
                periode = Periode.create(fraOgMed = b.periode.fraOgMed, tilOgMed = gjeldendeVedtaksdata.periode.tilOgMed),
                begrunnelse = b.begrunnelse,
            )
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                id = b.id,
                opprettet = b.opprettet,
                periode = Periode.create(fraOgMed = b.periode.fraOgMed, tilOgMed = gjeldendeVedtaksdata.periode.tilOgMed),
                begrunnelse = b.begrunnelse,
                fnr = b.fnr,
            )
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
                id = b.id,
                opprettet = b.opprettet,
                periode = Periode.create(fraOgMed = b.periode.fraOgMed, tilOgMed = gjeldendeVedtaksdata.periode.tilOgMed),
                begrunnelse = b.begrunnelse,
                fnr = b.fnr,
            )
            is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = b.id,
                opprettet = b.opprettet,
                periode = Periode.create(fraOgMed = b.periode.fraOgMed, tilOgMed = gjeldendeVedtaksdata.periode.tilOgMed),
                begrunnelse = b.begrunnelse,
                fnr = b.fnr,
            )
            is Grunnlag.Bosituasjon.Fullstendig.Enslig -> Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = b.id,
                opprettet = b.opprettet,
                periode = Periode.create(fraOgMed = b.periode.fraOgMed, tilOgMed = gjeldendeVedtaksdata.periode.tilOgMed),
                begrunnelse = b.begrunnelse,
            )
            is Grunnlag.Bosituasjon.Ufullstendig.HarEps -> throw IllegalStateException("Det skal ikke være mulig med Ufullstendige Bositusjoner her")
            is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps -> throw IllegalStateException("Det skal ikke være mulig med Ufullstendige Bositusjoner her")
        }

        return gjeldendeVedtaksdata.grunnlagsdata.copy(
            // Foreløpig støtter vi kun en aktiv bosituasjon, dersom det er fler, preutfyller vi ikke.
            bosituasjon = if (harFlerEnnEnBosituasjon) listOf(bosituasjon) else listOf(
                gjeldendeBosituasjon.singleFullstendigOrThrow(),
            ),
        ) to gjeldendeVedtaksdata.vilkårsvurderinger
    }

    override fun leggTilUføregrunnlag(
        request: LeggTilUførevurderingerRequest,
    ): Either<KunneIkkeLeggeTilGrunnlag, OpprettetRevurdering> {
        val revurdering = revurderingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling.left()

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
            KunneIkkeLeggeTilGrunnlag.UgyldigTilstand(
                revurdering::class,
                OpprettetRevurdering::class,
            )
        }.map {
            // TODO jah: Flytt denne inn i revurderingRepo.lagre
            vilkårsvurderingService.lagre(it.id, it.vilkårsvurderinger)
            revurderingRepo.lagre(it)
            it
        }
    }

    override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, OpprettetRevurdering> {
        val revurdering = revurderingRepo.hent(request.behandlingId)
            ?: return KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        return revurdering.oppdaterFradragOgMarkerSomVurdert(request.fradragsgrunnlag).mapLeft {
            KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                revurdering::class,
                OpprettetRevurdering::class,
            )
        }.map {
            // TODO jah: Flytt denne inn i revurderingRepo.lagre
            grunnlagService.lagreFradragsgrunnlag(it.id, it.grunnlagsdata.fradragsgrunnlag)
            revurderingRepo.lagre(it)
            it
        }
    }

    override fun leggTilBosituasjongrunnlag(request: LeggTilBosituasjongrunnlagRequest): Either<KunneIkkeLeggeTilBosituasjongrunnlag, OpprettetRevurdering> {
        val revurdering = revurderingRepo.hent(request.revurderingId)
            ?: return KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling.left()

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
            KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigTilstand(
                revurdering::class,
                OpprettetRevurdering::class,
            )
        }.map {
            // TODO jah: Flytt denne inn i revurderingRepo.lagre
            grunnlagService.lagreBosituasjongrunnlag(it.id, it.grunnlagsdata.bosituasjon)
            revurderingRepo.lagre(it)
            it
        }
    }

    override fun leggTilFormuegrunnlag(request: LeggTilFormuegrunnlagRequest): Either<KunneIkkeLeggeTilFormuegrunnlag, OpprettetRevurdering> {
        val revurdering = revurderingRepo.hent(request.revurderingId)
            ?: return KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering.left()

        val bosituasjon = revurdering.grunnlagsdata.bosituasjon.singleFullstendigOrThrow()

        val vilkår = request.toDomain(bosituasjon, revurdering.periode, clock).getOrHandle {
            return it.left()
        }
        return revurdering.oppdaterFormueOgMarkerSomVurdert(vilkår).mapLeft {
            KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand(
                revurdering::class,
                OpprettetRevurdering::class,
            )
        }.map {
            // TODO jah: Flytt denne inn i revurderingRepo.lagre
            vilkårsvurderingService.lagre(it.id, it.vilkårsvurderinger)
            revurderingRepo.lagre(it)
            it
        }
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
        val revurdering = revurderingRepo.hent(oppdaterRevurderingRequest.revurderingId)
            ?: return KunneIkkeOppdatereRevurdering.FantIkkeRevurdering.left()

        if (revurdering.forhåndsvarsel is Forhåndsvarsel.SkalForhåndsvarsles) {
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
                !informasjonSomRevurderes.harValgtInntekt() && it.contains(Konsistensproblem.BosituasjonOgFradrag.FlereBosituasjonerOgFradragForEPS) -> {
                    return KunneIkkeOppdatereRevurdering.EpsInntektMedFlereBosituasjonsperioderMåRevurderes.left()
                }
            }
        }

        val (grunnlagsdata, vilkårsvurderinger) = fjernBosituasjonHvisIkkeEntydig(
            gjeldendeVedtaksdata,
        )

        when (val r = VurderOmVilkårGirOpphørVedRevurdering(gjeldendeVedtaksdata.vilkårsvurderinger).resultat) {
            is OpphørVedRevurdering.Ja -> {
                if (!informasjonSomRevurderes.harValgtFormue() && r.opphørsgrunner.contains(Opphørsgrunn.FORMUE)) {
                    return KunneIkkeOppdatereRevurdering.FormueSomFørerTilOpphørMåRevurderes.left()
                }
            }
            is OpphørVedRevurdering.Nei -> Unit
        }

        val gjeldendeVedtakPåFraOgMedDato =
            gjeldendeVedtaksdata.gjeldendeVedtakPåDato(oppdaterRevurderingRequest.fraOgMed)
                ?: return KunneIkkeOppdatereRevurdering.FantIngenVedtakSomKanRevurderes.left()

        return when (revurdering) {
            is OpprettetRevurdering -> revurdering.oppdater(
                gjeldendeVedtaksdata.periode,
                revurderingsårsak,
                grunnlagsdata,
                vilkårsvurderinger,
                informasjonSomRevurderes,
                gjeldendeVedtakPåFraOgMedDato,
            ).right()
            is BeregnetRevurdering -> revurdering.oppdater(
                gjeldendeVedtaksdata.periode,
                revurderingsårsak,
                grunnlagsdata,
                vilkårsvurderinger,
                informasjonSomRevurderes,
                gjeldendeVedtakPåFraOgMedDato,
            ).right()
            is SimulertRevurdering -> revurdering.oppdater(
                gjeldendeVedtaksdata.periode,
                revurderingsårsak,
                grunnlagsdata,
                vilkårsvurderinger,
                informasjonSomRevurderes,
                gjeldendeVedtakPåFraOgMedDato,
            ).right()
            is UnderkjentRevurdering -> revurdering.oppdater(
                gjeldendeVedtaksdata.periode,
                revurderingsårsak,
                grunnlagsdata,
                vilkårsvurderinger,
                informasjonSomRevurderes,
                gjeldendeVedtakPåFraOgMedDato,
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
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, BeregnOgSimulerResponse> {
        return when (val originalRevurdering = revurderingRepo.hent(revurderingId)) {
            is BeregnetRevurdering, is OpprettetRevurdering, is SimulertRevurdering, is UnderkjentRevurdering -> {
                val eksisterendeUtbetalinger = utbetalingService.hentUtbetalinger(originalRevurdering.sakId)

                val beregnetRevurdering =
                    originalRevurdering.beregn(eksisterendeUtbetalinger).getOrHandle {
                        return when (it) {
                            is Revurdering.KunneIkkeBeregneRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden -> KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden
                            is Revurdering.KunneIkkeBeregneRevurdering.UgyldigBeregningsgrunnlag -> KunneIkkeBeregneOgSimulereRevurdering.UgyldigBeregningsgrunnlag(
                                it.reason,
                            )
                            Revurdering.KunneIkkeBeregneRevurdering.KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps -> KunneIkkeBeregneOgSimulereRevurdering.KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps
                        }.left()
                    }
                val feilmeldinger = identifiserUtfallSomIkkeStøttes(
                    vilkårsvurderinger = beregnetRevurdering.vilkårsvurderinger,
                    tidligereBeregning = beregnetRevurdering.tilRevurdering.beregning,
                    nyBeregning = beregnetRevurdering.beregning,
                ).fold(
                    ifLeft = { it },
                    ifRight = { emptySet() },
                ).toList()

                when (beregnetRevurdering) {
                    is BeregnetRevurdering.IngenEndring -> {
                        revurderingRepo.lagre(beregnetRevurdering)
                        BeregnOgSimulerResponse(beregnetRevurdering, feilmeldinger).right()
                    }
                    is BeregnetRevurdering.Innvilget -> {
                        utbetalingService.simulerUtbetaling(
                            sakId = beregnetRevurdering.sakId,
                            saksbehandler = saksbehandler,
                            beregning = beregnetRevurdering.beregning,
                        ).mapLeft {
                            KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(it)
                        }.map {
                            val simulert = beregnetRevurdering.toSimulert(it.simulering)
                            revurderingRepo.lagre(simulert)
                            BeregnOgSimulerResponse(simulert, feilmeldinger)
                        }
                    }

                    is BeregnetRevurdering.Opphørt -> {
                        utbetalingService.simulerOpphør(
                            sakId = beregnetRevurdering.sakId,
                            saksbehandler = saksbehandler,
                            opphørsdato = beregnetRevurdering.periode.fraOgMed,
                        ).mapLeft {
                            KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(it)
                        }.map {
                            val simulert = beregnetRevurdering.toSimulert(it.simulering)
                            revurderingRepo.lagre(simulert)
                            BeregnOgSimulerResponse(simulert, feilmeldinger)
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

    private fun identifiserUtfallSomIkkeStøttes(
        vilkårsvurderinger: Vilkårsvurderinger,
        tidligereBeregning: Beregning,
        nyBeregning: Beregning,
    ) = IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
        vilkårsvurderinger = vilkårsvurderinger,
        tidligereBeregning = tidligereBeregning,
        nyBeregning = nyBeregning,
    ).resultat

    override fun forhåndsvarsleEllerSendTilAttestering(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        revurderingshandling: Revurderingshandling,
        fritekst: String,
    ): Either<KunneIkkeForhåndsvarsle, Revurdering> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeForhåndsvarsle.FantIkkeRevurdering.left()

        when (revurdering) {
            is SimulertRevurdering -> {
                kanSendesTilAttestering(revurdering).getOrHandle {
                    return KunneIkkeForhåndsvarsle.Attestering(it).left()
                }
                val forhåndsvarselErSendt = revurdering.forhåndsvarsel is Forhåndsvarsel.SkalForhåndsvarsles

                if (forhåndsvarselErSendt) {
                    return KunneIkkeForhåndsvarsle.AlleredeForhåndsvarslet.left()
                } else {
                    return when (revurderingshandling) {
                        Revurderingshandling.SEND_TIL_ATTESTERING -> {
                            lagreForhåndsvarsling(revurdering, Forhåndsvarsel.IngenForhåndsvarsel)
                            sendTilAttestering(
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
                        Revurderingshandling.FORHÅNDSVARSLE -> {
                            sendForhåndsvarsling(revurdering, fritekst)
                        }
                    }
                }
            }
            else -> {
                return KunneIkkeForhåndsvarsle.UgyldigTilstand(revurdering::class, SimulertRevurdering::class).left()
            }
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

    private fun kanSendesTilAttestering(revurdering: Revurdering): Either<KunneIkkeSendeRevurderingTilAttestering, Unit> =
        when (revurdering) {
            is BeregnetRevurdering.IngenEndring -> {
                identifiserUtfallSomIkkeStøttes(
                    revurdering.vilkårsvurderinger,
                    revurdering.tilRevurdering.beregning,
                    revurdering.beregning,
                ).mapLeft {
                    KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                }
            }
            is SimulertRevurdering -> {
                identifiserUtfallSomIkkeStøttes(
                    revurdering.vilkårsvurderinger,
                    revurdering.tilRevurdering.beregning,
                    revurdering.beregning,
                )
                    .mapLeft {
                        KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                    }
                    .flatMap {
                        if (revurdering.harSimuleringFeilutbetaling()) KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left() else Unit.right()
                    }
            }
            is UnderkjentRevurdering.Innvilget -> {
                identifiserUtfallSomIkkeStøttes(
                    revurdering.vilkårsvurderinger,
                    revurdering.tilRevurdering.beregning,
                    revurdering.beregning,
                )
                    .mapLeft {
                        KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                    }
                    .flatMap {
                        if (revurdering.harSimuleringFeilutbetaling()) KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left() else Unit.right()
                    }
            }
            is UnderkjentRevurdering.Opphørt -> {
                identifiserUtfallSomIkkeStøttes(
                    revurdering.vilkårsvurderinger,
                    revurdering.tilRevurdering.beregning,
                    revurdering.beregning,
                )
                    .mapLeft {
                        KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(it.toList())
                    }
                    .flatMap {
                        if (revurdering.harSimuleringFeilutbetaling()) KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left() else Unit.right()
                    }
            }
            is UnderkjentRevurdering.IngenEndring -> {
                identifiserUtfallSomIkkeStøttes(
                    revurdering.vilkårsvurderinger,
                    revurdering.tilRevurdering.beregning,
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

        val revurderingMedPotensiellFritekst = if (fritekst != null) {
            revurdering.medFritekst(fritekst)
        } else {
            revurdering
        }

        return lagBrevRequest(revurderingMedPotensiellFritekst)
            .mapLeft {
                when (it) {
                    LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                    LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson -> KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson
                    LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeLageBrevutkastForRevurdering.KunneIkkeFinneGjeldendeUtbetaling
                }
            }.flatMap {
                brevService.lagBrev(it)
                    .mapLeft { KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast }
            }
    }

    private fun lagBrevRequest(visitable: Visitable<LagBrevRequestVisitor>): Either<LagBrevRequestVisitor.KunneIkkeLageBrevRequest, LagBrevRequest> {
        return LagBrevRequestVisitor(
            hentPerson = { fnr ->
                personService.hentPerson(fnr)
                    .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson }
            },
            hentNavn = { ident ->
                microsoftGraphApiClient.hentNavnForNavIdent(ident)
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
        ).let { visitor ->
            visitable.accept(visitor)
            visitor.brevRequest
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

                        revurdering.tilIverksatt(attestant, clock)
                            .map { iverksattRevurdering ->
                                val vedtakIngenEndring = Vedtak.from(iverksattRevurdering, clock)
                                if (vedtakIngenEndring.skalSendeBrev()) {
                                    val brevRequest = lagBrevRequest(vedtakIngenEndring)
                                        .getOrHandle {
                                            return when (it) {
                                                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeIverksetteRevurdering.KunneIkkeFinneGjeldendeUtbetaling
                                                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeIverksetteRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                                                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson -> KunneIkkeIverksetteRevurdering.FantIkkePerson
                                            }.left()
                                        }

                                    val dokument = brevRequest.tilDokument {
                                        brevService.lagBrev(it)
                                            .mapLeft { LagBrevRequest.KunneIkkeGenererePdf }
                                    }.getOrHandle {
                                        return KunneIkkeIverksetteRevurdering.KunneIkkeGenerereBrev.left()
                                    }.leggTilMetadata(
                                        Dokument.Metadata(
                                            sakId = vedtakIngenEndring.behandling.sakId,
                                            vedtakId = vedtakIngenEndring.id,
                                            bestillBrev = true,
                                        ),
                                    )
                                    vedtakRepo.lagre(vedtakIngenEndring)
                                    brevService.lagreDokument(dokument)
                                } else {
                                    vedtakRepo.lagre(vedtakIngenEndring)
                                }
                                iverksattRevurdering
                            }
                    }
                    is RevurderingTilAttestering.Innvilget -> {
                        revurdering.tilIverksatt(attestant, clock) {
                            utbetalingService.utbetal(
                                sakId = revurdering.sakId,
                                beregning = revurdering.beregning,
                                simulering = revurdering.simulering,
                                attestant = attestant,
                            ).mapLeft {
                                RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(it)
                            }.map {
                                // Dersom vi skal unngå denne hacken må Iverksatt.Innvilget innholde denne istedenfor kun IDen
                                utbetaling = it
                                it.id
                            }
                        }.map {
                            vedtakRepo.lagre(Vedtak.from(it, utbetaling!!.id, clock))
                            it
                        }
                    }
                    is RevurderingTilAttestering.Opphørt -> {
                        revurdering.tilIverksatt(attestant, clock) {
                            utbetalingService.opphør(
                                sakId = revurdering.sakId,
                                attestant = attestant,
                                opphørsdato = revurdering.periode.fraOgMed,
                                simulering = revurdering.simulering,
                            ).mapLeft {
                                RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(it)
                            }.map {
                                // Dersom vi skal unngå denne hacken må Iverksatt.Innvilget innholde denne istedenfor kun IDen
                                utbetaling = it
                                it.id
                            }
                        }.map {
                            vedtakRepo.lagre(Vedtak.from(it, utbetaling!!.id, clock))
                            it
                        }
                    }
                }.getOrHandle {
                    return when (it) {
                        RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                        is RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale -> KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(it.utbetalingFeilet)
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
                    when (it.forhåndsvarsel) {
                        null ->
                            Either.Left(FortsettEtterForhåndsvarselFeil.RevurderingErIkkeForhåndsvarslet)
                        is Forhåndsvarsel.SkalForhåndsvarsles.Besluttet ->
                            Either.Left(FortsettEtterForhåndsvarselFeil.AlleredeBesluttet)
                        is Forhåndsvarsel.IngenForhåndsvarsel ->
                            Either.Left(FortsettEtterForhåndsvarselFeil.AlleredeBesluttet)
                        is Forhåndsvarsel.SkalForhåndsvarsles.Sendt ->
                            Either.Right(it)
                    }
                }
            }
            .map { revurdering ->
                val forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
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

        val dokument = LagBrevRequest.Forhåndsvarsel(
            person = person,
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
        ).tilDokument {
            brevService.lagBrev(it).mapLeft { LagBrevRequest.KunneIkkeGenererePdf }
        }.map {
            it.leggTilMetadata(
                metadata = Dokument.Metadata(
                    sakId = revurdering.sakId,
                    revurderingId = revurdering.id,
                    bestillBrev = true,
                ),
            )
        }.getOrHandle { return KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument.left() }

        // TODO jah: Det hadde vært tryggere om dette gikk som en transaksjon. Dette kan ikke føre til duplikate dokumentutsender, men det kan føre til ghost-utsendinger, dersom lagreDokument feiler.
        revurdering.forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt
        revurderingRepo.lagre(revurdering)
        brevService.lagreDokument(dokument)

        log.info("Forhåndsvarsel sendt for revurdering ${revurdering.id}")

        oppgaveService.oppdaterOppgave(
            oppgaveId = revurdering.oppgaveId,
            beskrivelse = "Forhåndsvarsel er sendt.",
        ).mapLeft {
            log.error("Kunne ikke oppdatere oppgave: ${revurdering.oppgaveId} for revurdering: ${revurdering.id}")
            return KunneIkkeForhåndsvarsle.KunneIkkeOppretteOppgave.left()
        }

        return revurdering.right()
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
