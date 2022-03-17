package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatMap
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
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.harÅpenRegulering
import no.nav.su.se.bakover.domain.regulering.hentÅpenRegulering
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
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

    private fun blirBeregningEndret(regulering: Regulering): Boolean {
        val reguleringMedBeregning = regulering.beregn(clock = clock, begrunnelse = null)
            .getOrHandle { throw RuntimeException("Vi klarte ikke å beregne") }

        regulering.vilkårsvurderinger.resultat

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
            val reguleringer = reguleringRepo.hentForSakId(sakid)
            val regulering = if (reguleringer.harÅpenRegulering()) {
                oppdaterRegulering(reguleringer.hentÅpenRegulering(), startDato)
            } else {
                val request = OpprettRequest(sakId = sakid, saksnummer = saksnummer, fnr = fnr, fraOgMed = startDato)
                Either.catch {
                    opprettRegulering(request)
                }.fold(
                    ifLeft = {
                        log.error("Feil skjedde ved oppretting av regulering for saksnummer ${request.saksnummer}", it)
                        KunneIkkeOppretteRegulering.FantIkkeRegulering.left()
                    },
                    ifRight = { it }
                )
            }

            // TODO ai: Se om det går å forenkle
            regulering.flatMap { reg ->
                reguleringRepo.lagre(reg)
                if (reg.reguleringType == ReguleringType.AUTOMATISK) {
                    if (blirBeregningEndret(reg)) {
                        regulerAutomatisk(reg).mapLeft { KunneIkkeOppretteRegulering.FantIkkeRegulering } // kanske mapLeft returnerer reg bare?
                    } else reg.right()
                } else reg.right()
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
                if (blirBeregningEndret(iverksattRegulering)) {
                    lagVedtakOgUtbetal(iverksattRegulering).map { reguleringRepo.lagre(it) }
                }
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
            reguleringRepo.hent(request.behandlingId) ?: return BeregnOgSimulerFeilet.FantIkkeRegulering.left()

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

    private fun opprettRegulering(
        request: OpprettRequest,
    ): Either<KunneIkkeOppretteRegulering, Regulering.OpprettetRegulering> {
        val gjeldendeVedtaksdata = hentGjeldendeVedtaksdata(
            sakId = request.sakId,
            fraOgMed = request.fraOgMed,
        ).getOrHandle {
            return when (it) {
                KunneIkkeHenteGjeldendeVedtaksdata.FantIkkeSak -> KunneIkkeOppretteRegulering.FantIkkeSak.left()
                KunneIkkeHenteGjeldendeVedtaksdata.TidslinjeForVedtakErIkkeKontinuerlig -> KunneIkkeOppretteRegulering.TidslinjeForVedtakErIkkeKontinuerlig.left()
                KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode -> KunneIkkeOppretteRegulering.UgyldigPeriode.left()
                KunneIkkeHenteGjeldendeVedtaksdata.FantIngenVedtak -> KunneIkkeOppretteRegulering.FantIngenVedtak.left()
                is KunneIkkeHenteGjeldendeVedtaksdata.GrunnlagErIkkeKonsistent -> {
                    val fraOgMed =
                        maxOf(it.gjeldendeVedtaksdata.vilkårsvurderinger.periode!!.fraOgMed, request.fraOgMed)
                    val tilOgMed = it.gjeldendeVedtaksdata.vilkårsvurderinger.periode!!.tilOgMed

                    Regulering.OpprettetRegulering(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        sakId = request.sakId,
                        saksnummer = request.saksnummer,
                        saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
                        fnr = request.fnr,
                        periode = Periode.create(fraOgMed, tilOgMed),
                        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                            grunnlagsdata = it.gjeldendeVedtaksdata.grunnlagsdata,
                            vilkårsvurderinger = it.gjeldendeVedtaksdata.vilkårsvurderinger,
                        ),
                        beregning = null,
                        simulering = null,
                        reguleringType = ReguleringType.MANUELL,
                    ).right()
                }
            }
        }
        val reguleringType = utledAutomatiskEllerManuellRegulering(gjeldendeVedtaksdata)

        val fraOgMed = maxOf(gjeldendeVedtaksdata.vilkårsvurderinger.periode!!.fraOgMed, request.fraOgMed)
        val tilOgMed = gjeldendeVedtaksdata.vilkårsvurderinger.periode!!.tilOgMed

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
            saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
            fnr = request.fnr,
            periode = Periode.create(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            beregning = null,
            simulering = null,
            reguleringType = reguleringType,
        ).right()
    }

    // Fjern og bruk opprett med nye verdier (p.g.a datoene)
    private fun oppdaterRegulering(
        regulering: Regulering.OpprettetRegulering,
        nyReguleringsDato: LocalDate,
    ): Either<KunneIkkeOppretteRegulering, Regulering.OpprettetRegulering> {
        if (!nyReguleringsDato.isBefore(regulering.periode.fraOgMed)) {
            return regulering.right()
        }

        val gjeldendeVedtaksdata = hentGjeldendeVedtaksdata(
            sakId = regulering.sakId,
            fraOgMed = regulering.periode.fraOgMed,
        ).getOrHandle {
            return when (it) {
                KunneIkkeHenteGjeldendeVedtaksdata.FantIkkeSak -> KunneIkkeOppretteRegulering.FantIkkeSak.left()
                KunneIkkeHenteGjeldendeVedtaksdata.FantIngenVedtak -> KunneIkkeOppretteRegulering.FantIngenVedtak.left()
                KunneIkkeHenteGjeldendeVedtaksdata.TidslinjeForVedtakErIkkeKontinuerlig -> KunneIkkeOppretteRegulering.TidslinjeForVedtakErIkkeKontinuerlig.left()
                KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode -> KunneIkkeOppretteRegulering.UgyldigPeriode.left()
                is KunneIkkeHenteGjeldendeVedtaksdata.GrunnlagErIkkeKonsistent -> KunneIkkeOppretteRegulering.GrunnlagErIkkeKonsistent.left()
            }
        }
        // Se om man kan prøve utlede den på nytt og kanskje kjøre den automatisk
        val reguleringType = regulering.reguleringType

        val grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = Grunnlagsdata.tryCreate(
                fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
                bosituasjon = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon,
            ).getOrHandle { return KunneIkkeOppretteRegulering.KunneIkkeLageFradragsgrunnlag.left() },

            vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
        )

        return regulering.copy(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            reguleringType = reguleringType,
        ).right()
    }

    private fun hentGjeldendeVedtaksdata(
        sakId: UUID,
        fraOgMed: LocalDate,
    ): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata> {
        val gjeldendeVedtaksdata: GjeldendeVedtaksdata = vedtakService.kopierGjeldendeVedtaksdata(
            sakId = sakId,
            fraOgMed = fraOgMed,
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
            return KunneIkkeHenteGjeldendeVedtaksdata.GrunnlagErIkkeKonsistent(gjeldendeVedtaksdata).left()
        }

        return gjeldendeVedtaksdata.right()
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

        if (gjeldendeVedtaksdata.vilkårsvurderinger.vilkår.mapNotNull { it.hentTidligesteDatoForAvslag() }
            .isNotEmpty()
        ) {
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
