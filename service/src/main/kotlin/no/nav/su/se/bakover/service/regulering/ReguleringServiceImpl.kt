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
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.SjekkOmGrunnlagErKonsistent
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.VedtakSomKanReguleres
import no.nav.su.se.bakover.domain.regulering.VedtakType
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class OpprettRequest(
    val sakId: UUID,
    val fraOgMed: LocalDate,
)

sealed class KunneIkkeOppretteRegulering {
    object FantIkkeSak : KunneIkkeOppretteRegulering()
    object FantIkkeRegulering : KunneIkkeOppretteRegulering()
    object FantIngenVedtak : KunneIkkeOppretteRegulering()
    object UgyldigPeriode : KunneIkkeOppretteRegulering()
    object TidslinjeForVedtakErIkkeKontinuerlig : KunneIkkeOppretteRegulering()
    object GrunnlagErIkkeKonsistent : KunneIkkeOppretteRegulering()
    object KunneIkkeLageFradragsgrunnlag : KunneIkkeOppretteRegulering()
    object ReguleringErAlleredeIverksatt : KunneIkkeOppretteRegulering()
}

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val utbetalingService: UtbetalingService,
    private val vedtakService: VedtakService,
    private val vilkårsvurderingService: VilkårsvurderingService,
    private val grunnlagService: GrunnlagService,
    private val clock: Clock,
) : ReguleringService {

    override fun hentAlleSakerSomKanReguleres(fraDato: LocalDate?): SakerSomKanReguleres {
        return SakerSomKanReguleres(
            saker = hentAlleSaker(fraDato ?: 1.mai(LocalDate.now().year)),
        )
    }

    override fun kjørAutomatiskRegulering(fraDato: LocalDate?): List<Regulering> {
        // TODO Denne er midlertidig. Her må vi bestemme oss for om vi skal fylle opp en tabell, eller klare oss uten tabell
        return (fraDato ?: 1.mai(LocalDate.now().year)).let { fra ->
            hentAlleSaker(fra).map {
                val req = OpprettRequest(sakId = it.sakId, fraOgMed = fra)
                automatiskRegulerEnSak(req)
            }
        }
    }

    override fun opprettRegulering(sakId: UUID, fraDato: LocalDate?): Either<KunneIkkeOppretteRegulering, Regulering> {
        val regulering = (fraDato ?: 1.mai(LocalDate.now().year)).let { fra ->
            opprettRegulering(OpprettRequest(sakId = sakId, fraOgMed = fra))
        }.getOrHandle { return it.left() }

        reguleringRepo.lagre(regulering)
        vilkårsvurderingService.lagre(
            behandlingId = regulering.id,
            vilkårsvurderinger = regulering.vilkårsvurderinger,
        )
        grunnlagService.lagreFradragsgrunnlag(
            behandlingId = regulering.id,
            fradragsgrunnlag = regulering.grunnlagsdata.fradragsgrunnlag,
        )
        grunnlagService.lagreBosituasjongrunnlag(regulering.id, regulering.grunnlagsdata.bosituasjon)

        return regulering.right()
    }

    override fun leggTilFradrag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeOppretteRegulering, Regulering> {
        reguleringRepo.hent(request.behandlingId)?.let { regulering ->
            return when (regulering) {
                is Regulering.IverksattRegulering -> {
                    KunneIkkeOppretteRegulering.ReguleringErAlleredeIverksatt.left()
                }
                is Regulering.OpprettetRegulering -> {
                    regulering.leggTilFradrag(request.fradragsgrunnlag)
                    grunnlagService.lagreFradragsgrunnlag(regulering.id,
                        fradragsgrunnlag = regulering.grunnlagsdata.fradragsgrunnlag)
                    reguleringRepo.lagre(regulering)
                    regulering.right()
                }
            }
        }
        return KunneIkkeOppretteRegulering.FantIkkeRegulering.left()
    }

    override fun iverksett(reguleringId: UUID): Either<KunneIkkeOppretteRegulering, Regulering> {
        reguleringRepo.hent(reguleringId)?.let { regulering ->
            return when (regulering) {
                is Regulering.IverksattRegulering -> {
                    KunneIkkeOppretteRegulering.ReguleringErAlleredeIverksatt.left()
                }
                is Regulering.OpprettetRegulering -> {
                    regulering.tilIverksatt(ReguleringType.MANUELL)
                    reguleringRepo.lagre(regulering)
                    regulering.right()
                }
            }
        }
        return KunneIkkeOppretteRegulering.FantIkkeRegulering.left()
    }

    // TODO Er det bedre å slå sammen beregn og simuler?
    override fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, Regulering.OpprettetRegulering> {
        val regulering = reguleringRepo.hent(request.behandlingId)
            ?: return KunneIkkeBeregne.FantIkkeRegulering.left()

        return beregnPrivat(
            regulering = regulering as Regulering.OpprettetRegulering,
            begrunnelse = request.begrunnelse,
        ).getOrHandle { throw IllegalStateException("") }
            .let {
                reguleringRepo.lagre(it)
                it.right()
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

    private fun oppdaterUføregrunnlag() {
        // TODO vi må beregne ny forventet inntekt, og oppdatere grunnlaget...
    }

    private fun opprettRegulering(request: OpprettRequest): Either<KunneIkkeOppretteRegulering, Regulering.OpprettetRegulering> {
        val gjeldendeVedtaksdata: GjeldendeVedtaksdata = vedtakService.kopierGjeldendeVedtaksdata(
            sakId = request.sakId,
            fraOgMed = request.fraOgMed,
        ).getOrHandle {
            return when (it) {
                KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak -> KunneIkkeOppretteRegulering.FantIkkeSak.left()
                KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak -> KunneIkkeOppretteRegulering.FantIngenVedtak.left()
                is KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode -> KunneIkkeOppretteRegulering.UgyldigPeriode.left()
            }
        }.also {
            if (!it.tidslinjeForVedtakErSammenhengende()) return KunneIkkeOppretteRegulering.TidslinjeForVedtakErIkkeKontinuerlig.left()
        }

        SjekkOmGrunnlagErKonsistent(
            formuegrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.formue.grunnlag,
            uføregrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.uføre.grunnlag,
            bosituasjongrunnlag = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon,
            fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
        ).resultat.getOrHandle {
            return KunneIkkeOppretteRegulering.GrunnlagErIkkeKonsistent.left()
        }

        return Regulering.OpprettetRegulering(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = request.sakId,
            saksnummer = Saksnummer(nummer = 2021),
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            fnr = Fnr(fnr = "11037429742"),
            periode = Periode.create(fraOgMed = request.fraOgMed, tilOgMed = gjeldendeVedtaksdata.periode.tilOgMed),
            grunnlagsdata = Grunnlagsdata.tryCreate(
                fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
                bosituasjon = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon,
            ).getOrHandle { return KunneIkkeOppretteRegulering.KunneIkkeLageFradragsgrunnlag.left() },
            vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
            beregning = null,
            simulering = null,
        ).right()
    }

    private fun automatiskRegulerEnSak(request: OpprettRequest): Regulering {
        val regulering = opprettRegulering(request).getOrHandle { throw IllegalStateException("sdfdsf") }

        val reguleringMedBeregningOgSimulering =
            beregnPrivat(regulering = regulering, begrunnelse = "begrunnelse").map {
                simulerPrivat(regulering = it, saksbehandler = NavIdentBruker.Saksbehandler("tull"))
                    .getOrHandle { throw IllegalStateException("sdfsdf") }
            }.getOrHandle { throw IllegalStateException("dsfsdf") }.also {
                reguleringRepo.lagre(it)
                vilkårsvurderingService.lagre(
                    behandlingId = it.id,
                    vilkårsvurderinger = it.vilkårsvurderinger,
                )
                grunnlagService.lagreFradragsgrunnlag(
                    behandlingId = it.id,
                    fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag,
                )
                grunnlagService.lagreBosituasjongrunnlag(it.id, it.grunnlagsdata.bosituasjon)
            }

        reguleringMedBeregningOgSimulering.tilIverksatt(ReguleringType.AUTOMATISK).let {
            val utbetaling = utbetalingService.utbetal(
                UtbetalRequest.NyUtbetaling(
                    request = SimulerUtbetalingRequest.NyUtbetaling(
                        sakId = it.sakId,
                        saksbehandler = it.saksbehandler,
                        beregning = it.beregning!!,
                        uføregrunnlag = it.vilkårsvurderinger.tilVilkårsvurderingerRevurdering().uføre.grunnlag,
                    ),
                    simulering = it.simulering!!,
                ),
            ).getOrHandle { throw IllegalStateException("dsfds") }
            val vedtak = VedtakSomKanRevurderes.from(it, utbetaling.id, clock)
            reguleringRepo.lagre(it)
            vedtakService.lagre(vedtak)
        }
        return reguleringMedBeregningOgSimulering
    }

    private fun beregnPrivat(
        regulering: Regulering.OpprettetRegulering,
        begrunnelse: String?,
    ): Either<KunneIkkeBeregne, Regulering.OpprettetRegulering> {
        return regulering.beregn(
            begrunnelse = begrunnelse,
            clock = clock,
        ).getOrHandle { feil ->
            return when (feil) {
                is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> {
                    KunneIkkeBeregne.UgyldigTilstand(status = feil.status)
                }
            }.left()
        }.right()
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
}
