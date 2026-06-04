package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.regulering.domain.simulering.KunneIkkeSimulereRegulering
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.simulering.simulerUtbetaling
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeBehandleRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringRetryService
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringer
import no.nav.su.se.bakover.domain.regulering.beregnRegulering
import no.nav.su.se.bakover.domain.revurdering.iverksett.IverksettTransactionException
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
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
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock
import java.time.Year
import java.util.UUID

class ReguleringServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val utbetalingService: UtbetalingService,
    private val vedtakService: VedtakService,
    private val sessionFactory: SessionFactory,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val clock: Clock,
) : ReguleringService,
    ReguleringRetryService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun behandleReguleringAutomatisk(
        regulering: ReguleringUnderBehandling,
        sakInfo: SakInfo,
        utbetalinger: Utbetalinger,
        satsFactory: SatsFactory,
        isLiveRun: Boolean,
    ): Either<KunneIkkeBehandleRegulering, IverksattRegulering> {
        val (simulertRegulering, simulertUtbetaling) = beregnOgSimulerRegulering(regulering, sakInfo, utbetalinger, satsFactory, clock).getOrElse {
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
        sakInfo: SakInfo,
        utbetalinger: Utbetalinger,
        satsFactory: SatsFactory,
        clock: Clock,
    ): Either<KunneIkkeBehandleRegulering, Pair<ReguleringUnderBehandling.BeregnetRegulering, Utbetaling.SimulertUtbetaling>> {
        val beregning = beregnRegulering(
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
            sakInfo,
            utbetalinger,
            beregning,
        ).getOrElse { simuleringFeil ->
            log.error(
                "Ferdigstilling/iverksetting regulering: Simulering feilet for regulering ${regulering.id} for sak ${regulering.saksnummer}, " +
                    "reguleringstype=${regulering.reguleringstype::class.simpleName}, " +
                    "underliggende=$simuleringFeil",
            )
            return KunneIkkeBehandleRegulering.KunneIkkeSimulere(simuleringFeil).left()
        }
        return Pair(regulering.tilBeregnet(beregning, simulertUtbetaling.simulering), simulertUtbetaling).right()
    }

    fun simulerReguleringOgUtbetaling(
        regulering: ReguleringUnderBehandling,
        sakInfo: SakInfo,
        utbetalinger: Utbetalinger,
        beregning: Beregning,
    ): Either<KunneIkkeSimulereRegulering, Utbetaling.SimulertUtbetaling> {
        val uføregrunnlag = when (sakInfo.type) {
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
            sakInfo.lagNyUtbetaling(
                saksbehandler = regulering.saksbehandler,
                beregning = beregning,
                utbetalinger = utbetalinger,
                clock = clock,
                utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling,
                uføregrunnlag = uføregrunnlag,
            ).let {
                simulerUtbetaling(
                    tidligereUtbetalinger = utbetalinger,
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
        return simulering.mapLeft { KunneIkkeSimulereRegulering.SimuleringFeilet(it) }
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
        // sendUtbetaling (IBM MQ) kalles bevisst ETTER at DB-transaksjonen er committed.
        // Slik unngår vi at MQ-meldingen er sendt til økonomi mens DB rulles tilbake.
        // Feiler MQ-sendingen etter commit, finnes utbetalingsrekorden i DB og kan resendes via ResendUtbetalingService.
        val (vedtak, sendUtbetaling) = Either.catch {
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

                Pair(vedtak, nyUtbetaling::sendUtbetaling)
            }
        }.mapLeft {
            log.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket for regulering. Underliggende feil: $it. Se sikkerlogg for mer context.",
            )
            sikkerLogg.error(
                "Regulering for saksnummer ${regulering.saksnummer}: En feil skjedde mens vi prøvde lagre utbetalingen og vedtaket for regulering. Underliggende feil: $it. Se sikkerlogg for mer context.",
                it,
            )
            if (it is IverksettTransactionException) {
                KunneIkkeBehandleRegulering.KunneIkkeUtbetale(it.feil)
            } else {
                KunneIkkeBehandleRegulering.KunneIkkeUtbetale(
                    KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil(it),
                )
            }
        }.getOrElse { return it.left() }

        return sendUtbetaling()
            .map { vedtak }
            .mapLeft { feil ->
                val msg =
                    "Regulering for saksnummer ${regulering.saksnummer}: Regulering og vedtak ble lagret i databasen, men utbetalingen ble ikke sendt til oppdrag (IBM MQ). Kan resendes via retry-jobb. Underliggende feil: $feil."
                log.error(msg)
                sikkerLogg.error(msg)
                reguleringRepo.markerSomIkkeSendtTilOppdrag(regulering.id)
                KunneIkkeBehandleRegulering.KunneIkkeUtbetale(
                    KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeLeggeUtbetalingPåKø(feil),
                )
            }
    }

    override fun retrySendUtbetalingForIkkeOversendte() {
        val ikkeOversendte = reguleringRepo.hentIverksatteReguleringerSomIkkeErSendtTilOppdrag(Year.now(clock))
        if (ikkeOversendte.isEmpty()) return

        log.info("RetryIverksettRegulering: Fant ${ikkeOversendte.size} iverksatte reguleringer som ikke er sendt til Oppdrag")
        ikkeOversendte.forEach { regulering ->
            Either.catch {
                val vedtak = vedtakService.hentForReguleringId(regulering.id)
                if (vedtak == null) {
                    log.error("RetryIverksettRegulering: Fant ikke vedtak for regulering ${regulering.id} (saksnummer ${regulering.saksnummer}). Hopper over.")
                    return@forEach
                }
                utbetalingService.sendUkvittertUtbetaling(vedtak.utbetalingId)
                    .onRight {
                        reguleringRepo.markerSomSendtTilOppdrag(regulering.id)
                        log.info("RetryIverksettRegulering: Utbetaling for regulering ${regulering.id} (saksnummer ${regulering.saksnummer}) sendt til Oppdrag på nytt")
                    }
                    .onLeft { feil ->
                        log.error("RetryIverksettRegulering: Kunne ikke sende utbetaling for regulering ${regulering.id} (saksnummer ${regulering.saksnummer}) til Oppdrag. Feil: $feil")
                    }
            }.onLeft { throwable ->
                log.error("RetryIverksettRegulering: Ukjent feil for regulering ${regulering.id} (saksnummer ${regulering.saksnummer})", throwable)
            }
        }
    }

    override fun hentReguleringerForSak(sakId: UUID): Reguleringer = reguleringRepo.hentForSakId(sakId)

    override fun hentRelatertId(sakId: UUID, tx: SessionContext) = søknadsbehandlingRepo.hentForSak(sakId, tx).filter { it.erIverksatt }.maxByOrNull { it.opprettet }?.id?.value

    fun hentUtbetalinger(sakId: UUID) = utbetalingService.hentUtbetalingerForSakId(sakId)
}
