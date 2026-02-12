package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.domain.regulering.KunneIkkeHenteReguleringsgrunnlag
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.ManuellReguleringVisning
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringManuellService
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import org.slf4j.LoggerFactory
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.Uføregrunnlag
import java.time.Clock

class ReguleringManuellServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val sakService: SakService,
    private val reguleringService: ReguleringServiceImpl,
    private val statistikkService: SakStatistikkService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : ReguleringManuellService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentRegulering(
        reguleringId: ReguleringId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeHenteReguleringsgrunnlag, ManuellReguleringVisning> {
        val regulering =
            reguleringRepo.hent(reguleringId) ?: return KunneIkkeHenteReguleringsgrunnlag.FantIkkeRegulering.left()
        val gjeldendeVedtaksdata = sakService.hentGjeldendeVedtaksdata(
            sakId = regulering.sakId,
            periode = regulering.periode,
        ).getOrNull() ?: return KunneIkkeHenteReguleringsgrunnlag.FantIkkeGjeldendeVedtaksdata.left()
        return ManuellReguleringVisning.create(
            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
            regulering = regulering,
        ).right()
    }

    override fun beregnReguleringManuelt(
        reguleringId: ReguleringId,
        uføregrunnlag: List<Uføregrunnlag>,
        fradrag: List<Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, ReguleringUnderBehandling.BeregnetRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeRegulereManuelt.FantIkkeRegulering.left()
        if (regulering !is ReguleringUnderBehandling) return KunneIkkeRegulereManuelt.Beregne.IkkeUnderBehandling.left()
        if (regulering.reguleringstype !is Reguleringstype.MANUELL) return KunneIkkeRegulereManuelt.Beregne.ReguleringstypeAutomatisk.left()
        val sak = sakService.hentSak(regulering.sakId).getOrElse {
            return KunneIkkeRegulereManuelt.FantIkkeSak.left()
        }
        val reguleringNyttGrunnlag = regulering.leggTilBeregningsgrunnlag(
            saksbehandler = saksbehandler,
            fradragsgrunnlag = fradrag,
            uføregrunnlag = uføregrunnlag,
            clock = clock,
        ).getOrElse {
            log.error("Feilet under leggTilBeregningsgrunnlag: ${it.error}")
            return KunneIkkeRegulereManuelt.Beregne.FeilMedBeregningsgrunnlag.left()
        }

        val (simulertRegulering, _) = reguleringService.beregnOgSimulerRegulering(reguleringNyttGrunnlag, sak, clock)
            .getOrElse {
                return KunneIkkeRegulereManuelt.BeregningOgSimuleringFeilet.left()
            }

        reguleringRepo.lagre(simulertRegulering)
        return simulertRegulering.right()
    }

    override fun reguleringTilAttestering(
        reguleringId: ReguleringId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, ReguleringUnderBehandling.TilAttestering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeRegulereManuelt.FantIkkeRegulering.left()
        if (regulering !is ReguleringUnderBehandling.BeregnetRegulering) return KunneIkkeRegulereManuelt.FeilTilstandForAttestering.left()
        val tilAttestering = regulering.tilAttestering(saksbehandler)
        sessionFactory.withTransactionContext { tx ->
            reguleringRepo.lagre(tilAttestering, tx)
            statistikkService.lagre(StatistikkEvent.Behandling.Regulering.TilAttestering(tilAttestering), tx)
        }
        return tilAttestering.right()
    }

    override fun godkjennRegulering(
        reguleringId: ReguleringId,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeRegulereManuelt.FantIkkeRegulering.left()
        if (regulering !is ReguleringUnderBehandling.TilAttestering) return KunneIkkeRegulereManuelt.FeilTilstandForIverksettelse.left()
        if (regulering.saksbehandler.navIdent == attestant.navIdent) return KunneIkkeRegulereManuelt.SaksbehandlerKanIkkeAttestere.left()
        val sak = sakService.hentSak(sakId = regulering.sakId)
            .getOrElse { return KunneIkkeRegulereManuelt.FantIkkeSak.left() }
        if (sak.erStanset()) {
            return KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres.left()
        }
        val simulering = reguleringService.simulerReguleringOgUtbetaling(
            regulering,
            sak,
            regulering.beregning,
        ).getOrElse {
            return KunneIkkeRegulereManuelt.BeregningOgSimuleringFeilet.left()
        }
        return sessionFactory.withTransactionContext { tx ->
            val iverksattRegulering = regulering.godkjenn(attestant, clock)
            reguleringService.ferdigstillRegulering(iverksattRegulering, simulering, tx).fold(
                ifLeft = { KunneIkkeRegulereManuelt.KunneIkkeFerdigstille(it).left() },
                ifRight = { vedtak ->
                    statistikkService.lagre(StatistikkEvent.Behandling.Regulering.Iverksatt(iverksattRegulering, vedtak), tx)
                    iverksattRegulering.right()
                },
            )
        }
    }

    override fun underkjennRegulering(
        reguleringId: ReguleringId,
        attestant: NavIdentBruker.Attestant,
        kommentar: String,
    ): Either<KunneIkkeRegulereManuelt, ReguleringUnderBehandling.BeregnetRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeRegulereManuelt.FantIkkeRegulering.left()
        if (regulering !is ReguleringUnderBehandling.TilAttestering) return KunneIkkeRegulereManuelt.FeilTilstandForUnderkjennelse.left()
        if (regulering.saksbehandler.navIdent == attestant.navIdent) return KunneIkkeRegulereManuelt.SaksbehandlerKanIkkeAttestere.left()
        val underkjentRegulering = regulering.underkjenn(attestant, kommentar, clock)
        sessionFactory.withTransactionContext { tx ->
            reguleringRepo.lagre(underkjentRegulering, tx)
            statistikkService.lagre(StatistikkEvent.Behandling.Regulering.Underkjent(underkjentRegulering), tx)
        }
        return underkjentRegulering.right()
    }

    override fun avslutt(
        reguleringId: ReguleringId,
        avsluttetAv: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeAvslutte, AvsluttetRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeAvslutte.FantIkkeRegulering.left()

        return when (regulering) {
            is ReguleringUnderBehandling -> {
                val avsluttetRegulering = regulering.avslutt(avsluttetAv, clock)
                sessionFactory.withTransactionContext { tx ->
                    reguleringRepo.lagre(avsluttetRegulering, tx)
                    statistikkService.lagre(
                        StatistikkEvent.Behandling.Regulering.Avsluttet(
                            avsluttetRegulering,
                            avsluttetAv,
                        ),
                        tx,
                    )
                }
                avsluttetRegulering.right()
            }

            else -> KunneIkkeAvslutte.UgyldigTilstand.left()
        }
    }

    override fun hentStatusForÅpneManuelleReguleringer(): List<ReguleringSomKreverManuellBehandling> {
        return reguleringRepo.hentStatusForÅpneManuelleReguleringer()
    }

    override fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer> {
        return reguleringRepo.hentSakerMedÅpenBehandlingEllerStans()
    }
}
