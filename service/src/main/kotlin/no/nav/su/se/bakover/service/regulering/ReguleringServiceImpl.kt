package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.SjekkOmGrunnlagErKonsistent
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.Reguleringsjobb
import no.nav.su.se.bakover.domain.regulering.VedtakSomKanReguleres
import no.nav.su.se.bakover.domain.regulering.VedtakType
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class OpprettRequest(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val fraOgMed: LocalDate,
)

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val sakRepo: SakRepo,
    private val utbetalingService: UtbetalingService,
    private val vedtakService: VedtakService,
    private val clock: Clock,
) : ReguleringService {

    override fun hentAlleSakerSomKanReguleres(fraDato: LocalDate?): SakerSomKanReguleres {
        return SakerSomKanReguleres(
            saker = hentAlleSaker(fraDato ?: 1.mai(LocalDate.now().year)),
        )
    }

    override fun startRegulering(reguleringsjobb: Reguleringsjobb) {
        sakRepo.hentAlleIdFnrOgSaksnummer().forEach { (sakid, saksnummer, fnr) ->
            // TODO kanskje endre hentAlle til å gi oss de som mangler regulering i stedet for å sjekke etterpå
            // og da kanskje flytte den til reguleringsrepo...
            if (reguleringRepo.hent(saksnummer, reguleringsjobb.jobbnavn) != null) return@forEach

            val request =
                OpprettRequest(sakId = sakid, saksnummer = saksnummer, fnr = fnr, fraOgMed = reguleringsjobb.dato)

            opprettRegulering(request, reguleringsjobb).map { regulering ->
                lagreHeleRegulering(regulering)
                if (regulering.reguleringType == ReguleringType.AUTOMATISK) {
                    beregnPrivat(regulering).map { beregnetRegulering ->
                        simulerPrivat(
                            beregnetRegulering,
                            NavIdentBruker.Saksbehandler("supstonad"),
                        ).map { simulertRegulering ->
                            reguleringRepo.lagre(simulertRegulering)
                            val iverksattRegulering = iverksettPrivat(simulertRegulering)
                            lagVedtakOgUtbetal(iverksattRegulering).getOrHandle {
                                throw IllegalStateException("Hva gjør vi her?")
                            }
                        }.mapLeft {
                            println("Simulering feilet")
                            regulering.copy(
                                reguleringType = ReguleringType.MANUELL,
                            ).let {
                                reguleringRepo.lagre(it)
                            }
                        }
                    }.mapLeft {
                        when (it) {
                            KunneIkkeBeregne.BeregningFeilet -> println("Beregningen feilet")
                            KunneIkkeBeregne.FantIkkeRegulering -> println("Fant ikke reguleringen")
                            is KunneIkkeBeregne.UgyldigTilstand -> println("Ugyldig tilstand")
                        }

                        regulering.copy(
                            reguleringType = ReguleringType.MANUELL,
                        ).let {
                            reguleringRepo.lagre(it)
                        }
                    }
                }
            }
        }
    }

    override fun leggTilFradrag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradrag, Regulering> {
        reguleringRepo.hent(request.behandlingId)?.let { regulering ->
            return when (regulering) {
                is Regulering.IverksattRegulering -> {
                    KunneIkkeLeggeTilFradrag.ReguleringErAlleredeIverksatt.left()
                }
                is Regulering.OpprettetRegulering -> {
                    regulering.leggTilFradrag(request.fradragsgrunnlag)
                    reguleringRepo.lagre(regulering)
                    regulering.right()
                }
            }
        }
        return KunneIkkeLeggeTilFradrag.FantIkkeRegulering.left()
    }

    // TODO Er det bedre å slå sammen beregn og simuler?
    override fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, Regulering.OpprettetRegulering> {
        val regulering = reguleringRepo.hent(request.behandlingId)
            ?: return KunneIkkeBeregne.FantIkkeRegulering.left()

        if (regulering !is Regulering.OpprettetRegulering) return KunneIkkeBeregne.UgyldigTilstand(regulering::class)
            .left()

        return beregnPrivat(
            regulering = regulering,
            begrunnelse = request.begrunnelse,
        )
            .tapLeft { println("Error in beregning :_-)))") } // TODO ai: Hva gjør vi med feil?
            .tap { reguleringRepo.lagre(it) }
    }

    override fun simuler(request: SimulerRequest): Either<KunneIkkeSimulere, Regulering.OpprettetRegulering> {
        val regulering = reguleringRepo.hent(request.behandlingId)
            ?: return KunneIkkeSimulere.FantIkkeRegulering.left()

        if (regulering !is Regulering.OpprettetRegulering) return KunneIkkeSimulere.UgyldigTilstand(regulering::class)
            .left()

        return simulerPrivat(
            regulering = regulering,
            saksbehandler = request.saksbehandler,
        ).getOrHandle { throw IllegalStateException("") }
            .let {
                reguleringRepo.lagre(it)
                it.right()
            }
    }

    override fun hentStatus(reguleringsjobb: Reguleringsjobb): List<Regulering> {
        return reguleringRepo.hent(reguleringsjobb)
    }

    override fun iverksett(reguleringId: UUID): Either<KunneIkkeIverksetteRegulering, Regulering> {
        reguleringRepo.hent(reguleringId)?.let { regulering ->
            return when (regulering) {
                is Regulering.IverksattRegulering -> {
                    KunneIkkeIverksetteRegulering.ReguleringErAlleredeIverksatt.left()
                }
                is Regulering.OpprettetRegulering -> {
                    iverksettPrivat(regulering).right()
                }
            }
        }
        return KunneIkkeIverksetteRegulering.FantIkkeRegulering.left()
    }

    private fun iverksettPrivat(regulering: Regulering.OpprettetRegulering): Regulering.IverksattRegulering {

        val iverksattRegulering = regulering.tilIverksatt()
        reguleringRepo.lagre(iverksattRegulering)
        return iverksattRegulering
    }

    // private fun oppdaterUføregrunnlag(regulering: Regulering.OpprettetRegulering) =
    //     regulering.vilkårsvurderinger.uføre.grunnlag.map {
    //         it.forventetInntekt * 1.01
    //     }
    //
    // override fun leggTilUføregrunnlag(
    //     request: LeggTilUførevurderingerRequest,
    // ): Either<KunneIkkeLeggeTilGrunnlag, RevurderingOgFeilmeldingerResponse> {
    //     val revurdering = hent(request.behandlingId)
    //         .getOrHandle { return KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling.left() }
    //
    //     val uførevilkår = request.toVilkår(revurdering.periode, clock).getOrHandle {
    //         return when (it) {
    //             LeggTilUførevurderingerRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> KunneIkkeLeggeTilGrunnlag.PeriodeForGrunnlagOgVurderingErForskjellig.left()
    //             LeggTilUførevurderingerRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> KunneIkkeLeggeTilGrunnlag.UføregradOgForventetInntektMangler.left()
    //             LeggTilUførevurderingerRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> KunneIkkeLeggeTilGrunnlag.OverlappendeVurderingsperioder.left()
    //             LeggTilUførevurderingerRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> KunneIkkeLeggeTilGrunnlag.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden.left()
    //             LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat -> KunneIkkeLeggeTilGrunnlag.AlleVurderingeneMåHaSammeResultat.left()
    //             LeggTilUførevurderingerRequest.UgyldigUførevurdering.HeleBehandlingsperiodenMåHaVurderinger -> KunneIkkeLeggeTilGrunnlag.HeleBehandlingsperiodenMåHaVurderinger.left()
    //         }
    //     }
    //     return revurdering.oppdaterUføreOgMarkerSomVurdert(uførevilkår).mapLeft {
    //         KunneIkkeLeggeTilGrunnlag.UgyldigTilstand(fra = it.fra, til = it.til)
    //     }.map {
    //         // TODO jah: Flytt denne inn i revurderingRepo.lagre
    //         vilkårsvurderingService.lagre(it.id, it.vilkårsvurderinger)
    //         revurderingRepo.lagre(it)
    //         identifiserFeilOgLagResponse(it)
    //     }
    // }

    private fun opprettRegulering(
        request: OpprettRequest,
        reguleringsjobb: Reguleringsjobb,
    ): Either<KunneIkkeOppretteRegulering, Regulering.OpprettetRegulering> {
        val gjeldendeVedtaksdata = hentGjeldendeVedtaksdata(request).getOrHandle {
            return when (it) {
                KunneIkkeHenteGjeldendeVedtaksdata.FantIkkeSak -> KunneIkkeOppretteRegulering.FantIkkeSak.left()
                KunneIkkeHenteGjeldendeVedtaksdata.FantIngenVedtak -> KunneIkkeOppretteRegulering.FantIngenVedtak.left()
                KunneIkkeHenteGjeldendeVedtaksdata.GrunnlagErIkkeKonsistent -> KunneIkkeOppretteRegulering.GrunnlagErIkkeKonsistent.left()
                KunneIkkeHenteGjeldendeVedtaksdata.TidslinjeForVedtakErIkkeKontinuerlig -> KunneIkkeOppretteRegulering.TidslinjeForVedtakErIkkeKontinuerlig.left()
                KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode -> KunneIkkeOppretteRegulering.UgyldigPeriode.left()
            }
        }
        val reguleringType = utledAutomatiskEllerManuellRegulering(gjeldendeVedtaksdata)

        // TODO ai: Ta i bruk annen funksjonalitet for å gjøre dette
        val fraOgMed = maxOf(gjeldendeVedtaksdata.periode.fraOgMed, reguleringsjobb.dato)
        val tilOgMed =
            (
                (gjeldendeVedtaksdata.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).vurderingsperioder.filter { it.resultat == Resultat.Innvilget }
                    .map { it.periode.tilOgMed } +
                    (gjeldendeVedtaksdata.vilkårsvurderinger.formue as Vilkår.Formue.Vurdert).vurderingsperioder.filter { it.resultat == Resultat.Innvilget }
                        .map { it.periode.tilOgMed } +
                    (gjeldendeVedtaksdata.vilkårsvurderinger.utenlandsopphold as UtenlandsoppholdVilkår.Vurdert).vurderingsperioder.filter { it.resultat == Resultat.Innvilget }
                        .map { it.periode.tilOgMed }
                ).minByOrNull { it }!!

        val grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata =
            Grunnlagsdata.tryCreate(
                fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
                bosituasjon = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon,
            ).getOrHandle { return KunneIkkeOppretteRegulering.KunneIkkeLageFradragsgrunnlag.left() },
            vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
        )
        return Regulering.OpprettetRegulering(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = request.sakId,
            saksnummer = request.saksnummer,
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            fnr = request.fnr,
            periode = Periode.create(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
            grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
            vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            beregning = null,
            simulering = null,
            reguleringType = reguleringType,
            jobbnavn = reguleringsjobb,
        ).right()
    }

    private fun beregnPrivat(
        regulering: Regulering.OpprettetRegulering,
        begrunnelse: String? = null,
    ): Either<KunneIkkeBeregne, Regulering.OpprettetRegulering> {

        (regulering.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).regulerForventetInntekt()

        return regulering.beregn(
            begrunnelse = begrunnelse,
            clock = clock,
        ).mapLeft { feil ->
            when (feil) {
                is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> {
                    KunneIkkeBeregne.UgyldigTilstand(status = feil.status)
                }
                Regulering.KunneIkkeBeregne.BeregningFeilet -> KunneIkkeBeregne.BeregningFeilet
            }
        }
    }

    private fun simulerPrivat(
        regulering: Regulering.OpprettetRegulering,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSimulere, Regulering.OpprettetRegulering> {
        if (regulering.beregning == null) {
            return KunneIkkeSimulere.FantIkkeBeregning.left()
        }

        val simulertUtbetaling = utbetalingService.simulerUtbetaling(
            SimulerUtbetalingRequest.NyUtbetaling(
                sakId = regulering.sakId,
                saksbehandler = saksbehandler,
                beregning = regulering.beregning!!,
                uføregrunnlag = regulering.vilkårsvurderinger.uføre.grunnlag,
            ),
        ).getOrHandle {
            return KunneIkkeSimulere.SimuleringFeilet.left()
        }

        return regulering.leggTilSimulering(simulertUtbetaling.simulering).right()
    }

    private fun hentVedtakSomKanReguleres(fraOgMed: LocalDate): List<VedtakSomKanReguleres> {
        return reguleringRepo.hentVedtakSomKanReguleres(fraOgMed)
    }

    private fun hentAlleSaker(fraDato: LocalDate): List<SakSomKanReguleres> {
        return hentVedtakSomKanReguleres(fraDato)
            .filterNot { it.vedtakType == VedtakType.AVSLAG || it.vedtakType == VedtakType.AVVIST_KLAGE }
            .groupBy { it.sakId }
            .mapNotNull { (sakid, vedtakSomKanReguleres) ->
                val minFra: LocalDate = maxOf(vedtakSomKanReguleres.minOf { it.fraOgMed }, fraDato)
                val maxTil: LocalDate = vedtakSomKanReguleres.maxOf { it.tilOgMed }

                val gjeldendeVedtakPrMnd = Periode.create(minFra, maxTil).tilMånedsperioder().map { mnd ->
                    mnd to vedtakSomKanReguleres.filter {
                        val vedtaksperiode = Periode.create(it.fraOgMed, it.tilOgMed)
                        vedtaksperiode.inneholder(mnd)
                    }.maxByOrNull { it.opprettet.instant }!!
                }.filterNot { it.second.vedtakType == VedtakType.OPPHØR }.ifEmpty {
                    return@mapNotNull null
                }

                val type =
                    if (gjeldendeVedtakPrMnd.any { it.second.reguleringType == ReguleringType.MANUELL || it.second.vedtakType == VedtakType.STANS_AV_YTELSE }) {
                        ReguleringType.MANUELL
                    } else ReguleringType.AUTOMATISK

                SakSomKanReguleres(
                    sakId = sakid,
                    saksnummer = vedtakSomKanReguleres.first().saksnummer,
                    reguleringType = type,
                )
            }
    }

    private fun hentGjeldendeVedtaksdata(request: OpprettRequest): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata> {
        val gjeldendeVedtaksdata: GjeldendeVedtaksdata = vedtakService.kopierGjeldendeVedtaksdata(
            sakId = request.sakId,
            fraOgMed = request.fraOgMed,
        ).getOrHandle {
            return when (it) {
                KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak -> KunneIkkeHenteGjeldendeVedtaksdata.FantIkkeSak.left()
                KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak -> KunneIkkeHenteGjeldendeVedtaksdata.FantIngenVedtak.left()
                is KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode -> KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode.left()
            }
        }.also {
            if (!it.tidslinjeForVedtakErSammenhengende()) return KunneIkkeHenteGjeldendeVedtaksdata.TidslinjeForVedtakErIkkeKontinuerlig.left()
        }

        SjekkOmGrunnlagErKonsistent(
            formuegrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.formue.grunnlag,
            uføregrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.uføre.grunnlag,
            bosituasjongrunnlag = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon,
            fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
        ).resultat.getOrHandle {
            return KunneIkkeHenteGjeldendeVedtaksdata.GrunnlagErIkkeKonsistent.left()
        }

        return gjeldendeVedtaksdata.right()
    }

    private fun lagreHeleRegulering(regulering: Regulering.OpprettetRegulering) {
        reguleringRepo.lagre(regulering)
    }

    private fun utledAutomatiskEllerManuellRegulering(gjeldendeVedtaksdata: GjeldendeVedtaksdata): ReguleringType =
        if (gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag.any { (it.fradrag.fradragstype == Fradragstype.NAVytelserTilLivsopphold) || (it.fradrag.fradragstype == Fradragstype.OffentligPensjon) })
            ReguleringType.MANUELL else ReguleringType.AUTOMATISK

    private fun lagVedtakOgUtbetal(regulering: Regulering.IverksattRegulering): Either<KunneIkkeUtbetale, Regulering.IverksattRegulering> {
        val utbetaling = utbetalingService.utbetal(
            UtbetalRequest.NyUtbetaling(
                request = SimulerUtbetalingRequest.NyUtbetaling(
                    sakId = regulering.sakId,
                    saksbehandler = regulering.saksbehandler,
                    beregning = regulering.beregning!!,
                    uføregrunnlag = regulering.vilkårsvurderinger.tilVilkårsvurderingerRevurdering().uføre.grunnlag,
                ),
                simulering = regulering.simulering!!,
            ),
        ).getOrHandle {
            return KunneIkkeUtbetale.left()
        }
        val vedtak = VedtakSomKanRevurderes.from(regulering, utbetaling.id, clock)
        vedtakService.lagre(vedtak)
        return regulering.right()
    }
}

// private fun utledAutomatiskEllerManuellRegulering(saksnummer: Saksnummer, fraDato: LocalDate): ReguleringType? =
//     hentAlleSaker(fraDato).find { it.saksnummer == saksnummer }?.reguleringType

// override fun opprettRegulering(sakId: UUID, fraDato: LocalDate?): Either<KunneIkkeOppretteRegulering, Regulering> {
//     val sak = sakRepo.hentSak(sakId = sakId) ?: return KunneIkkeOppretteRegulering.FantIkkeSak.left()
//     val regulering = (fraDato ?: 1.mai(LocalDate.now().year)).let { fra ->
//         opprettRegulering(OpprettRequest(sakId = sakId, saksnummer = sak.saksnummer, fnr = sak.fnr, fraOgMed = fra))
//     }.getOrHandle { return it.left() }
//
//     // TODO Kan vi ikke flytte disse inn i repo??
//     reguleringRepo.lagre(regulering)
//     vilkårsvurderingService.lagre(
//         behandlingId = regulering.id,
//         vilkårsvurderinger = regulering.vilkårsvurderinger,
//     )
//     grunnlagService.lagreFradragsgrunnlag(
//         behandlingId = regulering.id,
//         fradragsgrunnlag = regulering.grunnlagsdata.fradragsgrunnlag,
//     )
//     grunnlagService.lagreBosituasjongrunnlag(regulering.id, regulering.grunnlagsdata.bosituasjon)
//
//     return regulering.right()
// }

// private fun automatiskRegulerEnSak(request: OpprettRequest): Either<KunneIkkeOppretteRegulering, Regulering> {
//     val gjeldendeVedtaksdata = hentGjeldendeVedtaksdata(request).getOrHandle {
//         return when (it) {
//             KunneIkkeHenteGjeldendeVedtaksdata.FantIkkeSak -> KunneIkkeOppretteRegulering.FantIkkeSak.left()
//             KunneIkkeHenteGjeldendeVedtaksdata.FantIngenVedtak -> KunneIkkeOppretteRegulering.FantIngenVedtak.left()
//             KunneIkkeHenteGjeldendeVedtaksdata.GrunnlagErIkkeKonsistent -> KunneIkkeOppretteRegulering.GrunnlagErIkkeKonsistent.left()
//             KunneIkkeHenteGjeldendeVedtaksdata.TidslinjeForVedtakErIkkeKontinuerlig -> KunneIkkeOppretteRegulering.TidslinjeForVedtakErIkkeKontinuerlig.left()
//             KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode -> KunneIkkeOppretteRegulering.UgyldigPeriode.left()
//         }
//     }
//
//     val regulering = opprettRegulering(request, gjeldendeVedtaksdata).getOrHandle { return KunneIkkeOppretteRegulering.FantIkkeRegulering.left() }
//
//     val reguleringMedBeregningOgSimulering =
//         beregnPrivat(regulering = regulering, begrunnelse = "begrunnelse").map {
//             simulerPrivat(regulering = it, saksbehandler = NavIdentBruker.Saksbehandler("tull"))
//                 .getOrHandle { throw IllegalStateException("sdfsdf") }
//         }.getOrHandle { throw IllegalStateException("dsfsdf") }.also {
//             reguleringRepo.lagre(it)
//             vilkårsvurderingService.lagre(
//                 behandlingId = it.id,
//                 vilkårsvurderinger = it.vilkårsvurderinger,
//             )
//             grunnlagService.lagreFradragsgrunnlag(
//                 behandlingId = it.id,
//                 fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
//             )
//             grunnlagService.lagreBosituasjongrunnlag(it.id, it.grunnlagsdata.bosituasjon)
//         }
//
//     reguleringMedBeregningOgSimulering.tilIverksatt().let {
//         val utbetaling = utbetalingService.utbetal(
//             UtbetalRequest.NyUtbetaling(
//                 request = SimulerUtbetalingRequest.NyUtbetaling(
//                     sakId = it.sakId,
//                     saksbehandler = it.saksbehandler,
//                     beregning = it.beregning!!,
//                     uføregrunnlag = it.vilkårsvurderinger.tilVilkårsvurderingerRevurdering().uføre.grunnlag,
//                 ),
//                 simulering = it.simulering!!,
//             ),
//         ).getOrHandle { throw IllegalStateException("dsfds") }
//         val vedtak = VedtakSomKanRevurderes.from(it, utbetaling.id, clock)
//         reguleringRepo.lagre(it)
//         vedtakService.lagre(vedtak)
//     }
//     return reguleringMedBeregningOgSimulering.right()
// }
