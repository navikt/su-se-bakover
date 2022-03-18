package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.SjekkOmGrunnlagErKonsistent
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.inneholderAvslag
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val sakRepo: SakRepo,
    private val utbetalingService: UtbetalingService,
    private val vedtakService: VedtakService,
    private val clock: Clock,
) : ReguleringService {

    private fun blirBeregningEndret(regulering: Regulering.OpprettetRegulering): Boolean {
        if (regulering.inneholderAvslag()) return true

        val reguleringMedBeregning = regulering.beregn(clock = clock, begrunnelse = null)
            .getOrHandle { throw RuntimeException("Vi klarte ikke å beregne") }

        return !reguleringMedBeregning.beregning!!.getMånedsberegninger().all { månedsberegning ->
            utbetalingService.hentGjeldendeUtbetaling(
                regulering.sakId,
                månedsberegning.periode.fraOgMed,
            ).fold(
                { false },
                { månedsberegning.getSumYtelse() == it.beløp },
            )
        }
    }

    override fun startRegulering(startDato: LocalDate): List<Either<KunneIkkeOppretteRegulering, Regulering>> {
        return sakRepo.hentAlleIdFnrOgSaksnummer().map { (sakid, saksnummer, fnr) ->
            log.info("Starter på : $saksnummer")

            val sak = Either.catch {
                sakRepo.hentSak(sakId = sakid) ?: return@map KunneIkkeOppretteRegulering.FantIkkeSak.left()
                    .also { log.error("Klarte ikke hente sak : $saksnummer", RuntimeException("Inkluderer stacktrace")) }
            }
                .getOrHandle {
                    log.error("Klarte ikke hente sak: $saksnummer", it)
                    return@map KunneIkkeOppretteRegulering.FantIkkeSak.left()
                }

            val gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(fraOgMed = startDato, clock = clock).getOrHandle {
                return@map when (it) {
                    Sak.KunneIkkeHenteGjeldendeVedtaksdata.FantIngenVedtak -> {
                        log.info("Sak $saksnummer har ingen vedtak å regulere for denne perioden ($startDato -> Inf)")
                        KunneIkkeOppretteRegulering.KunneIkkeHenteGjeldendeVedtaksdata(feil = it).left()
                    }
                    is Sak.KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode -> {
                        log.info("Finnes ingen vedtak etter fradato")
                        return@map KunneIkkeOppretteRegulering.FantIngenVedtak.left()
                    }
                }
            }.also {
                if (it.helePeriodenErOpphør()) return@map KunneIkkeOppretteRegulering.HelePeriodenErOpphør.left().also { log.info("Hele perioden er opphør. Lager ingen regulering for denne") }
            }

            val reguleringType = utledAutomatiskEllerManuellRegulering(gjeldendeVedtaksdata)

            val regulering = sak.opprettEllerOppdaterRegulering(startDato, reguleringType, gjeldendeVedtaksdata, clock).getOrHandle { feil ->
                return@map KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(feil).left().also { log.info("Klarte ikke å lage regulering pga : $feil") }
            }

            SjekkOmGrunnlagErKonsistent(
                formuegrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.formue.grunnlag,
                uføregrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.uføre.grunnlag,
                bosituasjongrunnlag = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon,
                fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
            ).resultat.getOrHandle {
                return@map Regulering.opprettRegulering(
                    id = regulering.id,
                    opprettet = regulering.opprettet,
                    startDato = startDato,
                    sakId = sakid,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    gjeldendeVedtaksdata = gjeldendeVedtaksdata,
                    reguleringType = ReguleringType.MANUELL,
                    clock = clock,
                ).orNull()!!.right().tap {
                    reguleringRepo.lagre(it)
                    log.info("Grunnlag er ikke konsistente. Vi kan derfor ikke beregne denne. Vi klarer derfor ikke å bestemme om denne allerede er regulert")
                }
            }

            if (!blirBeregningEndret(regulering)) {
                return@map KunneIkkeOppretteRegulering.FørerIkkeTilEnEndring.left().also { log.info("Lager ikke regulering for $saksnummer, da den ikke fører til noen endring i utbetaling") }
            }

            reguleringRepo.lagre(regulering)

            if (regulering.reguleringType == ReguleringType.AUTOMATISK) {
                regulerAutomatisk(regulering).mapLeft { feil ->
                    KunneIkkeOppretteRegulering.KunneIkkeRegulereAutomatisk(feil = feil)
                }
            } else {
                regulering.right()
            }
        }
    }

    private fun regulerAutomatisk(regulering: Regulering.OpprettetRegulering): Either<KunneIkkeRegulereAutomatiskt, Regulering.IverksattRegulering> {
        val reguleringMedBeregningOgSimulering = regulering.beregn(clock = clock, begrunnelse = null)
            .mapLeft { KunneIkkeRegulereAutomatiskt.KunneIkkeBeregne }
            .flatMap { beregnetRegulering ->
                beregnetRegulering.simuler(utbetalingService::simulerUtbetaling)
                    .mapLeft { KunneIkkeRegulereAutomatiskt.KunneIkkeSimulere }
            }
            .map { simulertRegulering -> simulertRegulering.tilIverksatt() }

        return when (reguleringMedBeregningOgSimulering) {
            is Either.Left -> {
                when (reguleringMedBeregningOgSimulering.value) {
                    KunneIkkeRegulereAutomatiskt.KunneIkkeBeregne -> log.error("Regulering feilet. Beregning feilet for saksnummer: ${regulering.saksnummer}.")
                    KunneIkkeRegulereAutomatiskt.KunneIkkeSimulere -> log.error("Regulering feilet. Simulering feilet for ${regulering.saksnummer}.")
                    KunneIkkeRegulereAutomatiskt.KunneIkkeUtbetale -> log.error("Regulering feilet. Utbetaling feilet for ${regulering.saksnummer}.")
                }
                reguleringRepo.lagre(regulering.copy(reguleringType = ReguleringType.MANUELL))
                reguleringMedBeregningOgSimulering.value.left()
            }
            is Either.Right -> {
                val iverksattRegulering = reguleringMedBeregningOgSimulering.value
                lagVedtakOgUtbetal(iverksattRegulering).map { reguleringRepo.lagre(it) }
                iverksattRegulering.right()
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

    override fun beregnOgSimuler(request: BeregnRequest): Either<BeregnOgSimulerFeilet, Regulering.OpprettetRegulering> {
        val regulering =
            (reguleringRepo.hent(request.behandlingId) as? Regulering.OpprettetRegulering) ?: return BeregnOgSimulerFeilet.FantIkkeRegulering.left()

        val beregnetRegulering = regulering.beregn(clock = clock, begrunnelse = request.begrunnelse).getOrHandle {
            when (it) {
                Regulering.KunneIkkeBeregne.BeregningFeilet -> log.error("Regulering feilet. Kunne ikke beregne")
                is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> log.error("Regulering feilet. Kan ikke beregne i status ${it.status}")
            }
            return BeregnOgSimulerFeilet.KunneIkkeBeregne.left()
        }

        return beregnetRegulering.simuler(utbetalingService::simulerUtbetaling)
            .mapLeft { BeregnOgSimulerFeilet.KunneIkkeSimulere }
    }

    override fun hentStatus(): List<Regulering> {
        return reguleringRepo.hentReguleringerSomIkkeErIverksatt()
    }

    override fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer> {
        return reguleringRepo.hentSakerMedÅpenBehandlingEllerStans()
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

    private fun utledAutomatiskEllerManuellRegulering(gjeldendeVedtaksdata: GjeldendeVedtaksdata): ReguleringType {
        if (gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag.any { (it.fradrag.fradragstype == Fradragstype.NAVytelserTilLivsopphold) || (it.fradrag.fradragstype == Fradragstype.OffentligPensjon) }) {
            return ReguleringType.MANUELL
        }

        if (gjeldendeVedtaksdata.harStans()) {
            return ReguleringType.MANUELL
        }

        if (gjeldendeVedtaksdata.vilkårsvurderinger.uføre.grunnlag.harForventetInntektStørreEnn0()) {
            return ReguleringType.MANUELL
        }

        if (gjeldendeVedtaksdata.delerAvPeriodenErOpphør()) {
            return ReguleringType.MANUELL
        }

        if (!gjeldendeVedtaksdata.tidslinjeForVedtakErSammenhengende()) {
            return ReguleringType.MANUELL
        }

        return ReguleringType.AUTOMATISK
    }

    private fun lagVedtakOgUtbetal(regulering: Regulering.IverksattRegulering): Either<KunneIkkeUtbetale, Regulering.IverksattRegulering> {
        val utbetaling = utbetalingService.utbetal(
            UtbetalRequest.NyUtbetaling(
                request = SimulerUtbetalingRequest.NyUtbetaling(
                    sakId = regulering.sakId,
                    saksbehandler = regulering.saksbehandler,
                    beregning = regulering.beregning,
                    uføregrunnlag = regulering.vilkårsvurderinger.tilVilkårsvurderingerRevurdering().uføre.grunnlag,
                ),
                simulering = regulering.simulering,
            ),
        ).getOrHandle {
            return KunneIkkeUtbetale.left()
        }
        val vedtak = VedtakSomKanRevurderes.from(regulering, utbetaling.id, clock)
        vedtakService.lagre(vedtak)
        return regulering.right()
    }
}
