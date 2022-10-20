package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.oppdrag.FantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkSaksbehandlersOgAttestantsSimulering
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkTidslinjerOgSimulering
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.hentGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class UtbetalingServiceImpl(
    private val utbetalingRepo: UtbetalingRepo,
    private val sakService: SakService,
    private val simuleringClient: SimuleringClient,
    private val utbetalingPublisher: UtbetalingPublisher,
    private val clock: Clock,
) : UtbetalingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling> {
        return utbetalingRepo.hentUtbetaling(utbetalingId)?.right() ?: FantIkkeUtbetaling.left()
    }

    override fun hentUtbetalingerForSakId(sakId: UUID): List<Utbetaling> {
        return utbetalingRepo.hentUtbetalinger(sakId)
    }

    override fun oppdaterMedKvittering(
        utbetalingId: UUID30,
        kvittering: Kvittering,
    ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering> {
        return utbetalingRepo.hentUtbetaling(utbetalingId)
            ?.let { utbetaling ->
                when (utbetaling) {
                    is Utbetaling.OversendtUtbetaling.MedKvittering -> {
                        log.info("Kvittering er allerede mottatt for utbetaling: ${utbetaling.id}")
                        utbetaling
                    }

                    is Utbetaling.OversendtUtbetaling.UtenKvittering -> {
                        log.info("Oppdaterer utbetaling med kvittering fra Oppdrag")
                        utbetaling.toKvittertUtbetaling(kvittering).also {
                            utbetalingRepo.oppdaterMedKvittering(it)
                        }
                    }
                }.right()
            } ?: FantIkkeUtbetaling.left()
            .also { log.warn("Fant ikke utbetaling med id: $utbetalingId") }
    }

    override fun simulerUtbetaling(utbetaling: Utbetaling.UtbetalingForSimulering, beregningsperiode: Periode): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        return simulerUtbetaling(
            SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = beregningsperiode,
            ),
        )
    }

    override fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
        return hentUtbetalingerForSakId(sakId).hentGjeldendeUtbetaling(
            forDato,
            clock,
        )
    }

    override fun klargjørNyUtbetaling(utbetaling: Utbetaling.SimulertUtbetaling, transactionContext: TransactionContext): Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>> {
        return UtbetalingKlargjortForOversendelse(
            utbetaling = utbetaling.forberedOversendelse(transactionContext),
            callback = { utbetalingsrequest ->
                sendUtbetalingTilOS(utbetalingsrequest)
                    .mapLeft { UtbetalingFeilet.Protokollfeil }
            },
        ).right()
    }

    override fun klargjørOpphør(
        utbetaling: Utbetaling.UtbetalingForSimulering,
        eksisterendeUtbetalinger: List<Utbetaling>,
        opphørsperiode: Periode,
        saksbehandlersSimulering: Simulering,
        transactionContext: TransactionContext,
    ): Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>> {
        return simulerOpphør(
            utbetaling = utbetaling,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            opphørsperiode = opphørsperiode,
        ).mapLeft {
            UtbetalingFeilet.KunneIkkeSimulere(it)
        }.flatMap { simulertOpphør ->
            KryssjekkSaksbehandlersOgAttestantsSimulering(
                saksbehandlersSimulering = saksbehandlersSimulering,
                attestantsSimulering = simulertOpphør,
            ).sjekk().getOrHandle {
                return UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(it).left()
            }

            UtbetalingKlargjortForOversendelse(
                utbetaling = simulertOpphør.forberedOversendelse(transactionContext),
                callback = { utbetalingsrequest ->
                    sendUtbetalingTilOS(utbetalingsrequest)
                        .mapLeft { UtbetalingFeilet.Protokollfeil }
                },
            ).right()
        }
    }

    override fun simulerOpphør(
        utbetaling: Utbetaling.UtbetalingForSimulering,
        eksisterendeUtbetalinger: List<Utbetaling>,
        opphørsperiode: Periode,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        val simuleringsperiode = opphørsperiode

        KryssjekkTidslinjerOgSimulering.sjekkNyEllerOpphør(
            underArbeidEndringsperiode = simuleringsperiode,
            underArbeid = utbetaling,
            eksisterende = eksisterendeUtbetalinger,
            simuler = this::simulerUtbetaling,
            clock = clock,
        ).getOrHandle {
            return SimuleringFeilet.KontrollAvSimuleringFeilet(it).left()
        }

        return simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        )
    }

    private fun simulerUtbetaling(
        request: no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingRequest,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        return simuleringClient.simulerUtbetaling(request = request)
            .map { request.utbetaling.toSimulertUtbetaling(it) }
    }

    override fun simulerStans(utbetaling: Utbetaling.UtbetalingForSimulering): Either<SimulerStansFeilet, Utbetaling.SimulertUtbetaling> {
        val simuleringsperiode = Periode.create(
            fraOgMed = utbetaling.tidligsteDato(),
            tilOgMed = utbetaling.senesteDato(),
        )

        return simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        ).mapLeft {
            SimulerStansFeilet.KunneIkkeSimulere(it)
        }.map { simulertUtbetaling ->
            KryssjekkTidslinjerOgSimulering.sjekkStans(underArbeid = simulertUtbetaling)
                .getOrHandle { return SimulerStansFeilet.KontrollFeilet(it).left() }
        }
    }

    override fun klargjørStans(
        utbetaling: Utbetaling.UtbetalingForSimulering,
        saksbehandlersSimulering: Simulering,
        transactionContext: TransactionContext,
    ): Either<UtbetalStansFeil, UtbetalingKlargjortForOversendelse<UtbetalStansFeil.KunneIkkeUtbetale>> {
        return simulerStans(utbetaling = utbetaling)
            .mapLeft {
                UtbetalStansFeil.KunneIkkeSimulere(it)
            }.flatMap { simulertStans ->
                KryssjekkSaksbehandlersOgAttestantsSimulering(
                    saksbehandlersSimulering = saksbehandlersSimulering,
                    attestantsSimulering = simulertStans,
                ).sjekk().getOrHandle {
                    return UtbetalStansFeil.KunneIkkeUtbetale(UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(it)).left()
                }

                UtbetalingKlargjortForOversendelse(
                    utbetaling = simulertStans.forberedOversendelse(transactionContext),
                    callback = { utbetalingsrequest ->
                        sendUtbetalingTilOS(utbetalingsrequest)
                            .mapLeft { UtbetalStansFeil.KunneIkkeUtbetale(it) }
                    },
                ).right()
            }
    }

    private fun sendUtbetalingTilOS(utbetalingsRequest: Utbetalingsrequest): Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest> {
        return utbetalingPublisher.publishRequest(utbetalingsRequest)
            .mapLeft {
                UtbetalingFeilet.Protokollfeil
            }
    }

    private fun Utbetaling.SimulertUtbetaling.forberedOversendelse(transactionContext: TransactionContext): Utbetaling.OversendtUtbetaling.UtenKvittering {
        return toOversendtUtbetaling(utbetalingPublisher.generateRequest(this)).also {
            utbetalingRepo.opprettUtbetaling(
                utbetaling = it,
                transactionContext = transactionContext,
            )
        }
    }

    override fun simulerGjenopptak(utbetaling: Utbetaling.UtbetalingForSimulering, eksisterendeUtbetalinger: List<Utbetaling>): Either<SimulerGjenopptakFeil, Utbetaling.SimulertUtbetaling> {
        val simuleringsperiode = Periode.create(
            fraOgMed = utbetaling.tidligsteDato(),
            tilOgMed = utbetaling.senesteDato(),
        )

        return simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        ).map { simulertUtbetaling ->
            KryssjekkTidslinjerOgSimulering.sjekkGjenopptak(
                underArbeid = simulertUtbetaling,
                eksisterende = eksisterendeUtbetalinger,
                clock = clock,
            ).getOrHandle {
                return SimulerGjenopptakFeil.KontrollFeilet(it).left()
            }
            simulertUtbetaling
        }.mapLeft {
            SimulerGjenopptakFeil.KunneIkkeSimulere(it)
        }
    }

    override fun klargjørGjenopptak(
        utbetaling: Utbetaling.UtbetalingForSimulering,
        eksisterendeUtbetalinger: List<Utbetaling>,
        saksbehandlersSimulering: Simulering,
        transactionContext: TransactionContext,
    ): Either<UtbetalGjenopptakFeil, UtbetalingKlargjortForOversendelse<UtbetalGjenopptakFeil.KunneIkkeUtbetale>> {
        return simulerGjenopptak(
            utbetaling = utbetaling,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        ).mapLeft {
            UtbetalGjenopptakFeil.KunneIkkeSimulere(it)
        }.flatMap { simulertGjenopptak ->
            KryssjekkSaksbehandlersOgAttestantsSimulering(
                saksbehandlersSimulering = saksbehandlersSimulering,
                attestantsSimulering = simulertGjenopptak,
            ).sjekk().getOrHandle {
                return UtbetalGjenopptakFeil.KunneIkkeUtbetale(
                    UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(
                        it,
                    ),
                ).left()
            }

            UtbetalingKlargjortForOversendelse(
                utbetaling = simulertGjenopptak.forberedOversendelse(transactionContext),
                callback = { utbetalingsrequest ->
                    sendUtbetalingTilOS(utbetalingsrequest)
                        .mapLeft { UtbetalGjenopptakFeil.KunneIkkeUtbetale(it) }
                },
            ).right()
        }
    }
}
