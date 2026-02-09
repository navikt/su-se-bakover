package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Måned
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
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.opprettEllerOppdaterRegulering
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import satser.domain.supplerendestønad.grunnbeløpsendringer
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.vurderinger.domain.VilkårsvurderingerHarUlikePeriode
import java.time.Clock

class ReguleringManuellServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val sakService: SakService,
    private val reguleringService: ReguleringServiceImpl,
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
        val reguleringNyttGrunnlag = regulering.leggTilSaksbehandler(saksbehandler)
            .leggTilUføre(uføregrunnlag, clock)
            .leggTilFradrag(fradrag)

        val (simulertRegulering, _) = reguleringService.beregnOgSimulerRegulering(reguleringNyttGrunnlag, sak, clock).getOrElse {
            return KunneIkkeRegulereManuelt.BeregningOgSimuleringFeilet.left()
        }

        reguleringRepo.lagre(simulertRegulering)
        return simulertRegulering.right()
    }

    override fun reguleringTilAttestering(
        reguleringId: ReguleringId,
        uføregrunnlag: List<Uføregrunnlag>,
        fradrag: List<Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, OpprettetRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeRegulereManuelt.FantIkkeRegulering.left()
        reguleringRepo.lagre(regulering)
        TODO("Not yet implemented")
    }

    override fun godkjennRegulering(
        reguleringId: ReguleringId,
        attestant: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering> {
        // TODO må det kontrollsimuleres noe?
        TODO("Not yet implemented")
    }

    override fun underkjennRegulering(
        reguleringId: ReguleringId,
        attestant: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, OpprettetRegulering> {
        TODO("Not yet implemented")
    }

    override fun regulerManuelt(
        reguleringId: ReguleringId,
        uføregrunnlag: List<Uføregrunnlag>,
        fradrag: List<Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeRegulereManuelt.FantIkkeRegulering.left()
        if (regulering.erFerdigstilt) return KunneIkkeRegulereManuelt.AlleredeFerdigstilt.left()

        val sak = sakService.hentSak(sakId = regulering.sakId)
            .getOrElse { return KunneIkkeRegulereManuelt.FantIkkeSak.left() }
        val fraOgMed = regulering.periode.fraOgMed
        val gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
            fraOgMed = fraOgMed,
            clock = clock,
        )
            .getOrElse { throw RuntimeException("Feil skjedde under manuell regulering for saksnummer ${sak.saksnummer}. $it") }

        if (gjeldendeVedtaksdata.harStans()) {
            return KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres.left()
        }

        try {
            return sak.opprettEllerOppdaterRegulering(
                Måned.fra(fraOgMed),
                clock,
                Reguleringssupplement.empty(clock),
                (grunnbeløpsendringer.last().verdi - grunnbeløpsendringer[grunnbeløpsendringer.size - 1].verdi).toBigDecimal(),
            ).mapLeft {
                throw RuntimeException("Feil skjedde under manuell regulering for saksnummer ${sak.saksnummer}. $it")
            }.map { oppdatertRegulering ->
                return oppdatertRegulering
                    .copy(reguleringstype = oppdatertRegulering.reguleringstype)
                    .leggTilFradrag(fradrag)
                    .leggTilUføre(uføregrunnlag, clock)
                    .leggTilSaksbehandler(saksbehandler)
                    .let {
                        reguleringService.behandleRegulering(it, sak)
                            .mapLeft { feil -> KunneIkkeRegulereManuelt.KunneIkkeFerdigstille(feil = feil) }
                    }
            }
        } catch (e: VilkårsvurderingerHarUlikePeriode) {
            // Saksbehandler løser dette manuelt. Hvis det skjer ofte kan det vurderes en automatisk løsning.
            log.error("Manuell regulering for sak=${sak.id} feilet på grunn av VilkårsvurderingerHarUlikePeriode", e)
            return KunneIkkeRegulereManuelt.ReguleringHarUtdatertePeriode.left()
        }
    }

    override fun avslutt(
        reguleringId: ReguleringId,
        avsluttetAv: NavIdentBruker,
    ): Either<KunneIkkeAvslutte, AvsluttetRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeAvslutte.FantIkkeRegulering.left()

        return when (regulering) {
            is ReguleringUnderBehandling -> {
                val avsluttetRegulering = regulering.avslutt(avsluttetAv, clock)
                reguleringRepo.lagre(avsluttetRegulering)

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
