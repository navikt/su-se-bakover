package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
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
    override fun startRegulering(reguleringsjobb: Reguleringsjobb) {
        sakRepo.hentAlleIdFnrOgSaksnummer().forEach { (sakid, saksnummer, fnr) ->
            // TODO kanskje endre hentAlle til å gi oss de som mangler regulering i stedet for å sjekke etterpå
            // og da kanskje flytte den til reguleringsrepo...
            if (reguleringRepo.hent(saksnummer, reguleringsjobb.jobbnavn) != null) return@forEach

            val request =
                OpprettRequest(sakId = sakid, saksnummer = saksnummer, fnr = fnr, fraOgMed = reguleringsjobb.dato)

            opprettRegulering(request, reguleringsjobb).map { regulering ->
                reguleringRepo.lagre(regulering)
                if (regulering.reguleringType == ReguleringType.AUTOMATISK) {
                    regulering.beregn(clock = clock, begrunnelse = null).map { beregnetRegulering ->
                        simulerPrivat(
                            beregnetRegulering,
                            NavIdentBruker.Saksbehandler("supstonad"),
                        ).map { simulertRegulering ->
                            simulertRegulering.tilIverksatt().let { iverksattRegulering ->
                                lagVedtakOgUtbetal(iverksattRegulering).map {
                                    reguleringRepo.lagre(it)
                                }.mapLeft {
                                    throw IllegalStateException("Hva gjør vi her?")
                                }
                            }
                        }.mapLeft {
                            log.error("Simulering feilet")
                            regulering.copy(
                                reguleringType = ReguleringType.MANUELL,
                            ).let {
                                reguleringRepo.lagre(it)
                            }
                        }
                    }.mapLeft { kunneIkkeBeregne ->
                        when (kunneIkkeBeregne) {
                            Regulering.KunneIkkeBeregne.BeregningFeilet -> log.error("Beregning feilet")
                            is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> log.error("Ikke lov til å beregne når regulering allerede er iverksatt")
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

        return regulering.beregn(
            clock = clock,
            begrunnelse = request.begrunnelse,
        ).mapLeft {
            when (it) {
                Regulering.KunneIkkeBeregne.BeregningFeilet -> KunneIkkeBeregne.BeregningFeilet
                is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> KunneIkkeBeregne.UgyldigTilstand(
                    regulering::class,
                )
            }
        }.tap {
            reguleringRepo.lagre(it).right()
        }
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

    override fun hentSakerMedÅpneBehandlinger(): List<Saksnummer> {
        return reguleringRepo.hentSakerMedBehandlingerTilAttestering()
    }

    override fun iverksett(reguleringId: UUID): Either<KunneIkkeIverksetteRegulering, Regulering> {
        reguleringRepo.hent(reguleringId)?.let { regulering ->
            return when (regulering) {
                is Regulering.IverksattRegulering -> {
                    KunneIkkeIverksetteRegulering.ReguleringErAlleredeIverksatt.left()
                }
                is Regulering.OpprettetRegulering -> {
                    regulering.tilIverksatt().also { iverksattRegulering ->
                        reguleringRepo.lagre(iverksattRegulering)
                    }.right()
                }
            }
        }
        return KunneIkkeIverksetteRegulering.FantIkkeRegulering.left()
    }

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
        val fraOgMed = maxOf(gjeldendeVedtaksdata.vilkårsvurderinger.periode!!.fraOgMed, reguleringsjobb.dato)
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
            grunnlagsdata = Grunnlagsdata.tryCreate(
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
