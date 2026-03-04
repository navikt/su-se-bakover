package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.regulering.domain.beregning.KunneIkkeBeregneRegulering
import behandling.regulering.domain.simulering.KunneIkkeSimulereRegulering
import beregning.domain.Beregning
import beregning.domain.BeregningStrategyFactory
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.simulering.simulerUtbetaling
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeBehandleRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.revurdering.iverksett.IverksettTransactionException
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.fromRegulering
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.simulering.Simuleringsresultat
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val utbetalingService: UtbetalingService,
    private val vedtakService: VedtakService,
    private val satsFactory: SatsFactory,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : ReguleringService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun behandleReguleringAutomatisk(
        regulering: ReguleringUnderBehandling,
        sak: Sak,
        isLiveRun: Boolean,
    ): Either<KunneIkkeBehandleRegulering, IverksattRegulering> {
        val (simulertRegulering, simulertUtbetaling) = beregnOgSimulerRegulering(regulering, sak, clock).getOrElse {
            return it.left()
        }

        val iverksattRegulering = simulertRegulering.tilAttestering(regulering.saksbehandler)
            .godkjenn(NavIdentBruker.Attestant(regulering.saksbehandler.navIdent), clock)

        if (isLiveRun) {
            ferdigstillRegulering(iverksattRegulering, simulertUtbetaling).getOrElse { return it.left() }
        }

        return iverksattRegulering.right()
    }

    override fun beregnOgSimulerRegulering(
        regulering: ReguleringUnderBehandling,
        sak: Sak,
        clock: Clock,
    ): Either<KunneIkkeBehandleRegulering, Pair<ReguleringUnderBehandling.BeregnetRegulering, Utbetaling.SimulertUtbetaling>> {
        val beregning = beregn(
            satsFactory = satsFactory,
            begrunnelse = null,
            regulering = regulering,
            clock = clock,
        ).getOrElse { kunneikkeBeregne ->
            log.error(
                "Ferdigstilling/iverksetting regulering: Beregning feilet for regulering ${regulering.id} for sak ${regulering.saksnummer} og reguleringstype: ${regulering.reguleringstype::class.simpleName}",
                kunneikkeBeregne.feil,
            )
            return KunneIkkeBehandleRegulering.KunneIkkeBeregne.left()
        }
        val simulertUtbetaling = simulerReguleringOgUtbetaling(
            regulering,
            sak,
            beregning,
        ).getOrElse {
            log.error("Ferdigstilling/iverksetting regulering: Simulering feilet for regulering ${regulering.id} for sak ${regulering.saksnummer} og reguleringstype: ${regulering.reguleringstype::class.simpleName}")
            return KunneIkkeBehandleRegulering.KunneIkkeSimulere.left()
        }
        return Pair(regulering.tilBeregnet(beregning, simulertUtbetaling.simulering), simulertUtbetaling).right()
    }

    fun beregn(
        satsFactory: SatsFactory,
        begrunnelse: String?,
        regulering: ReguleringUnderBehandling,
        clock: Clock,
    ): Either<KunneIkkeBeregneRegulering.BeregningFeilet, Beregning> {
        return Either.catch {
            BeregningStrategyFactory(
                clock = clock,
                satsFactory = satsFactory,
            ).beregn(
                grunnlagsdataOgVilkårsvurderinger = regulering.grunnlagsdataOgVilkårsvurderinger,
                begrunnelse = begrunnelse,
                sakstype = regulering.sakstype,
            )
        }.mapLeft {
            KunneIkkeBeregneRegulering.BeregningFeilet(feil = it)
        }
    }

    fun simulerReguleringOgUtbetaling(
        regulering: ReguleringUnderBehandling,
        sak: Sak,
        beregning: Beregning,
    ): Either<KunneIkkeSimulereRegulering, Utbetaling.SimulertUtbetaling> {
        val uføregrunnlag = when (sak.type) {
            Sakstype.ALDER -> null
            Sakstype.UFØRE -> regulering.vilkårsvurderinger.uføreVilkår()
                .getOrElse {
                    log.error("Mangler med uførevilkår ved simulering av regulering=${regulering.id}")
                    return KunneIkkeSimulereRegulering.ManglerUføreGrunnlag.left()
                }
                .grunnlag
                .toNonEmptyList()
        }
        val simulering = Either.catch {
            sak.lagNyUtbetaling(
                saksbehandler = regulering.saksbehandler,
                beregning = beregning,
                clock = clock,
                utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling,
                uføregrunnlag = uføregrunnlag,
            ).let {
                simulerUtbetaling(
                    tidligereUtbetalinger = sak.utbetalinger,
                    utbetalingForSimulering = it,
                    simuler = utbetalingService::simulerUtbetaling,
                )
            }
        }.fold(
            {
                log.error(
                    "Ferdigstilling/iverksetting regulering: Fikk exception ved generering av ny utbetaling / simulering av utbetaling for regulering ${regulering.id} for sak ${regulering.saksnummer} og reguleringstype: ${regulering.reguleringstype::class.simpleName}",
                    it,
                )
                SimuleringFeilet.TekniskFeil.left()
            },
            { it },
        )
        return simulering.mapLeft { KunneIkkeSimulereRegulering.SimuleringFeilet }
            .map {
                when (it) {
                    is Simuleringsresultat.UtenForskjeller -> it.simulertUtbetaling
                    is Simuleringsresultat.MedForskjeller -> return KunneIkkeSimulereRegulering.Forskjeller(it.forskjeller)
                        .left()
                }
            }
    }

    override fun ferdigstillRegulering(
        regulering: IverksattRegulering,
        simulertUtbetaling: Utbetaling.SimulertUtbetaling,
        sessionContext: TransactionContext?,
    ): Either<KunneIkkeBehandleRegulering.KunneIkkeUtbetale, VedtakInnvilgetRegulering> {
        return Either.catch {
            sessionFactory.withTransactionContext(sessionContext) { tx ->
                val nyUtbetaling = utbetalingService.klargjørUtbetaling(
                    simulertUtbetaling,
                    tx,
                ).getOrElse {
                    throw IverksettTransactionException(
                        "Kunne ikke klargjøre utbetaling. Underliggende feil:$it.",
                        KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeKlargjøreUtbetaling(it),
                    )
                }

                val vedtak = VedtakSomKanRevurderes.fromRegulering(
                    regulering = regulering,
                    utbetalingId = nyUtbetaling.utbetaling.id,
                    clock = clock,
                )

                reguleringRepo.lagre(regulering, tx)
                vedtakService.lagreITransaksjon(vedtak, tx)

                nyUtbetaling.sendUtbetaling()
                    .getOrElse {
                        throw IverksettTransactionException(
                            "Kunne ikke sende utbetaling til oppdrag (legge den på kø). Underliggende feil:$it.",
                            KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeLeggeUtbetalingPåKø(it),
                        )
                    }
                vedtak
            }
        }.mapLeft {
            log.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering. Underliggende feil: $it. Se sikkerlogg for mer context.",
            )
            sikkerLogg.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering. Underliggende feil: $it. Se sikkerlogg for mer context.",
                it,
            )
            if (it is IverksettTransactionException) {
                KunneIkkeBehandleRegulering.KunneIkkeUtbetale(it.feil)
            } else {
                KunneIkkeBehandleRegulering.KunneIkkeUtbetale(
                    KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil(it),
                )
            }
        }
    }
}
