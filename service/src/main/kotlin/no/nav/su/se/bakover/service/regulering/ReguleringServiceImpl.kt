package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import behandling.regulering.domain.beregning.KunneIkkeBeregneRegulering
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.simulering.simulerUtbetaling
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeFerdigstilleOgIverksette
import no.nav.su.se.bakover.domain.regulering.LiveRun
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val utbetalingService: UtbetalingService,
    private val vedtakService: VedtakService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) : ReguleringService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun ferdigstillOgIverksettRegulering(
        regulering: OpprettetRegulering,
        sak: Sak,
        isLiveRun: Boolean,
        satsFactory: SatsFactory,
    ): Either<KunneIkkeFerdigstilleOgIverksette, IverksattRegulering> {
        return regulering.beregn(
            satsFactory = satsFactory,
            begrunnelse = null,
            clock = clock,
        ).mapLeft { kunneikkeBeregne ->
            when (kunneikkeBeregne) {
                is KunneIkkeBeregneRegulering.BeregningFeilet -> {
                    log.error(
                        "Ferdigstilling/iverksetting regulering: Beregning feilet for regulering ${regulering.id} for sak ${regulering.saksnummer} og eguleringstype: ${regulering.reguleringstype::class.simpleName}",
                        kunneikkeBeregne.feil,
                    )
                }
            }
            KunneIkkeFerdigstilleOgIverksette.KunneIkkeBeregne
        }
            .flatMap { beregnetRegulering ->
                beregnetRegulering.simuler { beregning, uføregrunnlag ->
                    Either.catch {
                        sak.lagNyUtbetaling(
                            saksbehandler = beregnetRegulering.saksbehandler,
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
                }.mapLeft {
                    log.error("Ferdigstilling/iverksetting regulering: Simulering feilet for regulering ${regulering.id} for sak ${regulering.saksnummer} og reguleringstype: ${regulering.reguleringstype::class.simpleName}")
                    KunneIkkeFerdigstilleOgIverksette.KunneIkkeSimulere
                }
            }
            .map { (simulertRegulering, simulertUtbetaling) ->
                simulertRegulering.tilIverksatt() to simulertUtbetaling
            }.flatMap { (iverksattRegulering, simulertUtbetaling) ->
                lagVedtakOgUtbetal(iverksattRegulering, simulertUtbetaling, isLiveRun)
            }
            .onLeft {
                val message = when (it) {
                    is KunneIkkeFerdigstilleOgIverksette.KunneIkkeBeregne -> "Klarte ikke å beregne reguleringen."
                    is KunneIkkeFerdigstilleOgIverksette.KunneIkkeSimulere -> "Klarte ikke å simulere utbetalingen."
                    is KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale -> "Klarte ikke å utbetale. Underliggende feil: ${it.feil}"
                }
                if (isLiveRun) {
                    LiveRun.Opprettet(
                        sessionFactory = sessionFactory,
                        lagreRegulering = reguleringRepo::lagre,
                        lagreVedtak = vedtakService::lagreITransaksjon,
                        klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
                    ).kjørSideffekter(
                        regulering.copy(
                            reguleringstype = Reguleringstype.MANUELL(
                                setOf(
                                    ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet(begrunnelse = message),
                                ),
                            ),
                        ),
                    )
                }
            }
            .map {
                it
            }
    }

    private fun lagVedtakOgUtbetal(
        regulering: IverksattRegulering,
        simulertUtbetaling: Utbetaling.SimulertUtbetaling,
        isLiveRun: Boolean,
    ): Either<KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale, IverksattRegulering> {
        return Either.catch {
            if (isLiveRun) {
                LiveRun.Iverksatt(
                    sessionFactory = sessionFactory,
                    lagreRegulering = reguleringRepo::lagre,
                    lagreVedtak = vedtakService::lagreITransaksjon,
                    klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
                ).kjørSideffekter(regulering, simulertUtbetaling, clock)
            }
        }.mapLeft {
            log.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering. Underliggende feil: $it. Se sikkerlogg for mer context.",
            )
            sikkerLogg.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering. Underliggende feil: $it. Se sikkerlogg for mer context.",
                it,
            )
            if (it is KunneIkkeFerdigstilleIverksettelsestransaksjon) {
                KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale(it)
            } else {
                KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale(
                    KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil(
                        it,
                    ),
                )
            }
        }.map {
            regulering
        }
    }
}
