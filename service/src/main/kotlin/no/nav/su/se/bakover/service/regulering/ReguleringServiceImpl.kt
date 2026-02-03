package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.simulering.simulerUtbetaling
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeFerdigstilleOgIverksette
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.revurdering.iverksett.IverksettTransactionException
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.vedtak.fromRegulering
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vedtak.domain.VedtakSomKanRevurderes
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

    override fun behandleRegulering(
        regulering: OpprettetRegulering,
        sak: Sak,
        satsFactory: SatsFactory,
        isLiveRun: Boolean,
    ): Either<KunneIkkeFerdigstilleOgIverksette, IverksattRegulering> {
        val beregnetRegulering = beregnRegulering(
            regulering = regulering,
            satsFactory = satsFactory,
            clock = clock,
        ).getOrElse { return it.left() }

        val (simulertRegulering, simulertUtbetaling) = simulerReguleringOgUtbetaling(
            beregnetRegulering,
            sak,
        ).getOrElse { return it.left() }

        val iverksattRegulering = simulertRegulering.tilIverksatt()

        if (isLiveRun) {
            lagreReguleringOgVedtakOgUtbetal(iverksattRegulering, simulertUtbetaling).getOrElse { return it.left() }
        }

        return iverksattRegulering.right()
    }

    private fun beregnRegulering(
        regulering: OpprettetRegulering,
        satsFactory: SatsFactory,
        clock: Clock,
    ): Either<KunneIkkeFerdigstilleOgIverksette.KunneIkkeBeregne, OpprettetRegulering> =
        regulering.beregn(
            satsFactory = satsFactory,
            begrunnelse = null,
            clock = clock,
        ).mapLeft { kunneikkeBeregne ->
            log.error(
                "Ferdigstilling/iverksetting regulering: Beregning feilet for regulering ${regulering.id} for sak ${regulering.saksnummer} og eguleringstype: ${regulering.reguleringstype::class.simpleName}",
                kunneikkeBeregne.feil,
            )
            KunneIkkeFerdigstilleOgIverksette.KunneIkkeBeregne
        }

    private fun simulerReguleringOgUtbetaling(
        regulering: OpprettetRegulering,
        sak: Sak,
    ) = regulering.simuler { beregning, uføregrunnlag ->
        Either.catch {
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
    }.mapLeft {
        log.error("Ferdigstilling/iverksetting regulering: Simulering feilet for regulering ${regulering.id} for sak ${regulering.saksnummer} og reguleringstype: ${regulering.reguleringstype::class.simpleName}")
        KunneIkkeFerdigstilleOgIverksette.KunneIkkeSimulere
    }

    private fun lagreReguleringOgVedtakOgUtbetal(
        regulering: IverksattRegulering,
        simulertUtbetaling: Utbetaling.SimulertUtbetaling,
    ): Either<KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale, Unit> {
        return Either.catch {
            sessionFactory.withTransactionContext { tx ->
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
            }
            Unit
        }.mapLeft {
            log.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering. Underliggende feil: $it. Se sikkerlogg for mer context.",
            )
            sikkerLogg.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket; og sende utbetalingen til oppdrag for regulering. Underliggende feil: $it. Se sikkerlogg for mer context.",
                it,
            )
            if (it is IverksettTransactionException) {
                KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale(it.feil)
            } else {
                KunneIkkeFerdigstilleOgIverksette.KunneIkkeUtbetale(
                    KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil(it),
                )
            }
        }
    }
}
