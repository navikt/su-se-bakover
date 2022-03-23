package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.SjekkOmGrunnlagErKonsistent
import no.nav.su.se.bakover.domain.grunnlag.erGyldigTilstand
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.inneholderAvslag
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val sakRepo: SakRepo,
    private val utbetalingService: UtbetalingService,
    private val vedtakService: VedtakService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
    private val tilbakekrevingService: TilbakekrevingService,
) : ReguleringService {

    private fun blirBeregningEndret(regulering: Regulering.OpprettetRegulering): Boolean {
        if (regulering.inneholderAvslag()) return true

        val reguleringMedBeregning = regulering.beregn(clock = clock, begrunnelse = null)
            .getOrHandle {
                when (it) {
                    is Regulering.KunneIkkeBeregne.BeregningFeilet -> {
                        throw RuntimeException("Vi klarte ikke å beregne. Underliggende grunn ${it.feil}")
                    }
                    is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> {
                        throw RuntimeException("Vi klarte ikke å beregne. Feil status")
                    }
                }
            }

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
                return@map KunneIkkeOppretteRegulering.KunneIkkeHenteEllerOppretteRegulering(feil).left()
                    .also { log.error("Klarte ikke å lage regulering pga : $feil") }
            }

            SjekkOmGrunnlagErKonsistent(
                formuegrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.formue.grunnlag,
                uføregrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.uføre.grunnlag,
                bosituasjongrunnlag = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon,
                fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
            ).resultat.getOrHandle { konsistensproblemer ->
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
                    val message =
                        "Grunnlag er ikke konsistente. Vi kan derfor ikke beregne denne. Vi klarer derfor ikke å bestemme om denne allerede er regulert"
                    if (konsistensproblemer.erGyldigTilstand()) {
                        log.info(message)
                    } else {
                        log.error(message)
                    }
                }
            }

            if (!blirBeregningEndret(regulering)) {
                return@map KunneIkkeOppretteRegulering.FørerIkkeTilEnEndring.left()
                    .also { log.info("Lager ikke regulering for $saksnummer, da den ikke fører til noen endring i utbetaling") }
            }

            tilbakekrevingService.hentAvventerKravgrunnlag(sak.id)
                .ifNotEmpty {
                    // TODO Jacob Meidell: Støtte at kravgrunnlag annuleres :p
                    log.info("Kan ikke sende oppdragslinjer mens vi venter på et kravgrunnlag, siden det kan annulere nåværende kravgrunnlag.")
                    return@map regulering.copy(
                        reguleringType = ReguleringType.MANUELL,
                    ).right().tap {
                        reguleringRepo.lagre(regulering)
                    }
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
        return regulering.beregn(clock = clock, begrunnelse = null)
            .mapLeft { kunneikkeBeregne ->
                when (kunneikkeBeregne) {
                    is Regulering.KunneIkkeBeregne.BeregningFeilet -> {
                        log.error("Regulering feilet. Beregning feilet for saksnummer: ${regulering.saksnummer}", kunneikkeBeregne.feil)
                    }
                    is Regulering.KunneIkkeBeregne.IkkeLovÅBeregneIDenneStatusen -> {
                        log.error("Regulering feilet. Beregning feilet for saksnummer: ${regulering.saksnummer}. Ikke lov å beregne i denne statusen")
                    }
                }
                KunneIkkeRegulereAutomatiskt.KunneIkkeBeregne
            }
            .flatMap { beregnetRegulering ->
                beregnetRegulering.simuler(utbetalingService::simulerUtbetaling)
                    .mapLeft {
                        log.error("Regulering feilet. Simulering feilet for saksnummer: ${regulering.saksnummer}.")
                        KunneIkkeRegulereAutomatiskt.KunneIkkeSimulere
                    }.flatMap {
                        if (it.simulering!!.harFeilutbetalinger()) {
                            log.error("Regulering feilet. Simuleringen inneholdt feilutbetalinger for saksnummer: ${regulering.saksnummer}.")
                            KunneIkkeRegulereAutomatiskt.KanIkkeAutomatiskRegulereSomFørerTilFeilutbetaling.left()
                        } else {
                            it.right()
                        }
                    }
            }
            .map { simulertRegulering -> simulertRegulering.tilIverksatt() }
            .flatMap {
                lagVedtakOgUtbetal(it).tap {
                    reguleringRepo.lagre(it)
                }
            }
            .tapLeft {
                reguleringRepo.lagre(regulering.copy(reguleringType = ReguleringType.MANUELL))
            }
    }

    /**
     * Denne brukes kun i den manuelle flyten som ikke er implementert ferdig enda.
     */
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

    /**
     * Denne brukes kun i den manuelle flyten som ikke er implementert ferdig enda.
     */
    override fun beregnOgSimuler(request: BeregnRequest): Either<BeregnOgSimulerFeilet, Regulering.OpprettetRegulering> {
        val regulering =
            (reguleringRepo.hent(request.behandlingId) as? Regulering.OpprettetRegulering)
                ?: return BeregnOgSimulerFeilet.FantIkkeRegulering.left()

        val beregnetRegulering = regulering.beregn(clock = clock, begrunnelse = request.begrunnelse).getOrHandle {
            when (it) {
                is Regulering.KunneIkkeBeregne.BeregningFeilet -> log.error("Regulering feilet. Kunne ikke beregne", it.feil)
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

    /**
     * Denne brukes kun i den manuelle flyten som ikke er implementert ferdig enda.
     */
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

        if (gjeldendeVedtaksdata.harAvkortingsvarselEllerUteståendeAvkorting) {
            return ReguleringType.MANUELL
        }

        return ReguleringType.AUTOMATISK
    }

    private fun lagVedtakOgUtbetal(regulering: Regulering.IverksattRegulering): Either<KunneIkkeRegulereAutomatiskt.KunneIkkeUtbetale, Regulering.IverksattRegulering> {
        return utbetalingService.verifiserOgSimulerUtbetaling(
            request = UtbetalRequest.NyUtbetaling(
                request = SimulerUtbetalingRequest.NyUtbetaling(
                    sakId = regulering.sakId,
                    saksbehandler = regulering.saksbehandler,
                    beregning = regulering.beregning,
                    uføregrunnlag = regulering.vilkårsvurderinger.tilVilkårsvurderingerRevurdering().uføre.grunnlag,
                ),
                simulering = regulering.simulering,
            ),
        ).mapLeft {
            log.error("Kunne ikke verifisere og simulere utbetaling for regulering med saksnummer ${regulering.saksnummer} med underliggende grunn: $it")
            KunneIkkeRegulereAutomatiskt.KunneIkkeUtbetale
        }.flatMap {
            Either.catch {
                sessionFactory.withTransactionContext { tx ->
                    utbetalingService.lagreUtbetaling(it, tx)
                    vedtakService.lagre(
                        vedtak = VedtakSomKanRevurderes.from(regulering, it.id, clock),
                        sessionContext = tx,
                    )
                    utbetalingService.publiserUtbetaling(it).mapLeft {
                        throw KunneIkkeSendeTilUtbetalingException(it)
                    }
                    regulering
                }
            }.mapLeft {
                log.error(
                    "En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering med saksnummer ${regulering.saksnummer}",
                    it,
                )
                KunneIkkeRegulereAutomatiskt.KunneIkkeUtbetale
            }
        }
    }

    private data class KunneIkkeSendeTilUtbetalingException(val feil: UtbetalingFeilet) : RuntimeException()
}
