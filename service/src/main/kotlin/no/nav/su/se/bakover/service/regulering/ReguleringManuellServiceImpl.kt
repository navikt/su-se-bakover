package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.ktor.util.date.Month
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.domain.regulering.KunneIkkeHenteReguleringsgrunnlag
import no.nav.su.se.bakover.domain.regulering.KunneIkkeOppretteManuellRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.ManuellReguleringVisning
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringManuellService
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.opprettManuellRegulering
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.uføre.domain.Uføregrunnlag
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class ReguleringManuellServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val sakService: SakService,
    private val reguleringService: ReguleringServiceImpl,
    private val statistikkService: SakStatistikkService,
    private val satsFactory: SatsFactory,
    private val oppgaveService: OppgaveService,
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

    /**
     * Benyttes ikke av reguleringsjobb men av saksbehandler der jobb har vurdert at sak må revurderes unødvendig
     */
    override fun opprettManuellRegulering(
        sakId: UUID,
        begrunnelse: String,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeOppretteManuellRegulering, ManuellReguleringVisning> {
        val idag = LocalDate.now(clock)
        val førsteMai = LocalDate.of(idag.year, Month.MAY.ordinal, 1)
        if (idag.isBefore(førsteMai)
        ) {
            return KunneIkkeOppretteManuellRegulering.FørMai.left()
        }

        val sak = sakService.hentSak(sakId).getOrElse {
            return KunneIkkeOppretteManuellRegulering.FantIkkeSak.left()
        }

        val sisteTilOgMed = sak.vedtakstidslinje()?.lastOrNull()?.periode?.tilOgMed
            ?: return KunneIkkeOppretteManuellRegulering.UgyldigTilstand("Feil med vedtakslinje").left()
        val gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
            periode = Periode.create(førsteMai, sisteTilOgMed),
            clock = clock,
        ).getOrElse {
            return KunneIkkeOppretteManuellRegulering.UgyldigTilstand("Feil med gjeldende vedtaksdata").left()
        }

        val regulering = SakTilRegulering(
            sakInfo = sak.info(),
            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
        ).opprettManuellRegulering(
            saksbehandler = saksbehandler,
            begrunnelse = begrunnelse,
            clock = clock,
        )

        reguleringRepo.lagre(regulering)

        return ManuellReguleringVisning(
            gjeldendeVedtaksdata = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
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

        val (simulertRegulering, _) = reguleringService.beregnOgSimulerRegulering(
            reguleringNyttGrunnlag,
            sak.info(),
            sak.utbetalinger,
            satsFactory,
            clock,
        )
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
        val sak =
            sakService.hentSakInfo(regulering.sakId).getOrElse { return KunneIkkeRegulereManuelt.FantIkkeSak.left() }
        if (regulering !is ReguleringUnderBehandling.BeregnetRegulering) return KunneIkkeRegulereManuelt.FeilTilstandForAttestering.left()

        val oppgave = regulering.oppgaveId?.let {
            oppgaveService.hentOppgave(it).getOrElse {
                KunneIkkeOppretteManuellRegulering.KunneIkkeOppretteOppgave.left()
            }
        } ?: oppgaveService.opprettOppgave(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                sakstype = sak.type,
                tilordnetRessurs = null,
                clock = clock,
            ),
        ).getOrElse {
            KunneIkkeOppretteManuellRegulering.KunneIkkeOppretteOppgave.left()
        }

        val tilAttestering = regulering.tilAttestering(saksbehandler, oppgave.oppgaveId)
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
            sak.info(),
            sak.utbetalinger,
            regulering.beregning,
        ).getOrElse {
            return KunneIkkeRegulereManuelt.BeregningOgSimuleringFeilet.left()
        }
        val iverksattRegulering = regulering.godkjenn(attestant, clock)
        val vedtak = reguleringService.ferdigstillRegulering(iverksattRegulering, simulering).getOrElse {
            return KunneIkkeRegulereManuelt.KunneIkkeFerdigstille(it).left()
        }
        statistikkService.lagre(StatistikkEvent.Behandling.Regulering.Iverksatt(iverksattRegulering, vedtak), null)

        avsluttOppgave(regulering.id, regulering.oppgaveId, attestant)
        return iverksattRegulering.right()
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

        oppgaveService.oppdaterOppgave(
            regulering.oppgaveId,
            oppdaterOppgaveInfo = OppdaterOppgaveInfo(
                beskrivelse = "Reguleringen er blitt underkjent",
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(regulering.saksbehandler.navIdent),
            ),
        ).mapLeft {
            log.error("Kunne ikke oppdatere oppgave ${regulering.oppgaveId} for regulering ${regulering.id} med informasjon om at den er underkjent. Feilen var $it")
        }
        return underkjentRegulering.right()
    }

    override fun avslutt(
        reguleringId: ReguleringId,
        avsluttetAv: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeAvslutte, AvsluttetRegulering> {
        val regulering = reguleringRepo.hent(reguleringId) ?: return KunneIkkeAvslutte.FantIkkeRegulering.left()

        if (regulering is ReguleringUnderBehandling && regulering.oppgaveId != null) {
            avsluttOppgave(
                reguleringId = regulering.id,
                oppgaveId = regulering.oppgaveId!!,
                saksbehandler = avsluttetAv,
            )
        }
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

    private fun avsluttOppgave(
        reguleringId: ReguleringId,
        oppgaveId: OppgaveId,
        saksbehandler: NavIdentBruker,
    ) {
        oppgaveService.lukkOppgave(
            oppgaveId = oppgaveId,
            tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(saksbehandler.navIdent),
        ).onLeft {
            if (it.feilPgaAlleredeFerdigstilt()) {
                log.warn("Oppgave $oppgaveId er allerede ferdigstilt for regulering reguleringId")
            } else {
                log.error("Kunne ikke lukke oppgave $oppgaveId ved avslutting av regulering $reguleringId. Dette må gjøres manuelt.")
            }
        }.onRight {
            log.info("Lukket oppgave $oppgaveId ved avslutting av regulering $reguleringId")
        }
    }
}
