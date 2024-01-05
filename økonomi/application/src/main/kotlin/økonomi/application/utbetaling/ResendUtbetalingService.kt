package økonomi.application.utbetaling

import arrow.core.Either
import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForOpphør
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.slf4j.LoggerFactory
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock
import java.util.UUID

/**
 * Brukes for å sende en utbetaling (som er knyttet til et vedtak) på nytt til oppdrag.
 * Dette er en manuell operasjon som kan være nødvendig dersom en utbetaling har feilet.
 *
 * [no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService] vil trigges ved mottak av kvittering.
 * Dersom det er en stans/gjenopptak vil den ikke gjøre noe.
 * Dersom det er en regulering, vil den kjøre, men vi sender ikke brev i disse tilfellene.
 * Dersom den forrige kvitteringen var OK, vil den ikke sende brev igjen, men den vil prøve lukke oppgaven på nytt (idempotent).
 * Dersom den forrige kvitteringen ikke var OK, vil ikke brevet ha vært sendt før og forhåpentligvis vil den generere og sende brev. TODO jah: Test i preprod.
 */
class ResendUtbetalingService(
    private val utbetalingService: UtbetalingService,
    private val vedtakRepo: VedtakRepo,
    private val sakService: SakService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
    private val serviceUser: String,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun resendUtbetalinger(
        utbetalingsIder: List<UUID30>,
    ): List<Either<KunneIkkeSendeUtbetalingPåNytt, UUID30>> {
        return utbetalingsIder.map { utbetalingId ->
            Either.catch {
                log.info("Resend utbetaling: Resender utbetalingId $utbetalingId")
                resendUtbetaling(utbetalingId)
            }.mapLeft {
                log.error(
                    "Resend utbetaling: Ukjent feil. Kunne ikke resende utbetalingId $utbetalingId",
                    RuntimeException("Trigger stacktrace for enklere debugging"),
                )
                sikkerLogg.error("Resend utbetaling: Ukjent feil. Kunne ikke resende utbetalingId $utbetalingId", it)
                KunneIkkeSendeUtbetalingPåNytt.UkjentFeil(utbetalingId)
            }.flatten()
        }.also {
            log.info("Resend utbetaling: ${it.count { it.isRight() }} av ${utbetalingsIder.size} utbetalinger ble sendt på nytt. $it")
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun resendUtbetaling(
        utbetalingId: UUID30,
    ): Either<KunneIkkeSendeUtbetalingPåNytt, UUID30> {
        val sak = sakService.hentSakForUtbetalingId(utbetalingId).getOrElse {
            return KunneIkkeSendeUtbetalingPåNytt.FantIkkeSak(utbetalingId).left().also {
                log.error("Resend utbetaling: Fant ikke sak for utbetalingId $utbetalingId")
            }
        }
        val utbetaling = (
            sak.utbetalinger.singleOrNull { it.id == utbetalingId }
                ?: return KunneIkkeSendeUtbetalingPåNytt.FantIkkeUtbetalingPåSak(utbetalingId)
                    .left()
            ) as? Utbetaling.OversendtUtbetaling.MedKvittering
            ?: return KunneIkkeSendeUtbetalingPåNytt.UtbetalingErIkkeKvittert(utbetalingId, sak.id).left().also {
                log.error("Resend utbetaling: Fant ikke utbetaling på sak ${sak.id} for utbetalingId $utbetalingId")
            }

        val vedtak =
            sak.vedtakListe.filterIsInstance<VedtakEndringIYtelse>().singleOrNull { it.utbetalingId == utbetalingId }
                ?: return KunneIkkeSendeUtbetalingPåNytt.FantIkkeVedtakKnyttetTilUtbetaling(utbetalingId, sak.id).left()
                    .also {
                        log.error("Resend utbetaling: Fant ikke vedtak på sak ${sak.id} for utbetalingId $utbetalingId")
                    }

        if (sak.utbetalinger.sisteUtbetaling() != utbetaling) {
            return KunneIkkeSendeUtbetalingPåNytt.IkkeSisteUtbetaling(utbetalingId, sak.id, vedtak.id).left().also {
                // TODO jah: Denne oppstår dersom man sender samme utbetalingId flere ganger. Trenger ikke error på sikt, men er greit å få med seg de første gangene
                log.error("Resend utbetaling: Utbetalingen er ikke siste utbetaling på sak ${sak.id} for utbetalingId $utbetalingId og vedtakId ${vedtak.id}")
            }
        }

        val nyUtbetaling: Utbetaling.UtbetalingForSimulering = lagNyUtbetaling(vedtak, utbetalingId, sak)
            .getOrElse { return it.left() }

        val simulertUtbetaling = utbetalingService.simulerUtbetaling(nyUtbetaling).getOrElse {
            log.error("Resend utbetaling: Kunne ikke simulere utbetalingen for sak ${sak.id} og utbetalingId $utbetalingId. Underliggende grunn: $it")
            return KunneIkkeSendeUtbetalingPåNytt.KunneIkkeSimulere(utbetalingId, sak.id, vedtak.id).left()
        }

        return Either.catch {
            sessionFactory.withTransactionContext { tx ->
                // klargjørUtbetaling(...) persisterer også utbetalingen.
                val response = utbetalingService.klargjørUtbetaling(
                    utbetaling = simulertUtbetaling,
                    transactionContext = tx,
                ).getOrElse {
                    // TODO jah: Klargjøringen har ikke noen naturlig left (annet enn persistering), så det bør vi rydde opp.
                    //  Men inntil left er fjernet, må vi kaste her for å rulle tilbake transaksjonen.
                    throw KunneIkkeKlaregjøreUtbetaling()
                }
                // Knytter den nye utbetalingen til det eksisterende vedtaket. Den forrige utbetalingen vil nå bli en stray.
                vedtakRepo.oppdaterUtbetalingId(vedtak.id, response.utbetaling.id, tx)
                // Oversender til oppdrag. Dette bør være det siste som skjer i transaksjonen.
                response.sendUtbetaling()
                response.utbetaling.id
            }
        }.mapLeft {
            if (it is KunneIkkeKlaregjøreUtbetaling) {
                log.error("Resend utbetaling: Kunne ikke klargjøre utbetalingen for oppdrag for sak ${sak.id} og utbetalingId $utbetalingId")
                KunneIkkeSendeUtbetalingPåNytt.KunneIkkeKlargjøreUtbetaling(utbetalingId, sak.id, vedtak.id)
            } else {
                log.error(
                    "Resend utbetaling: Kunne ikke sende utbetalingen til oppdrag for sak ${sak.id} og utbetalingId $utbetalingId. Se sikkerlogg for mer context.",
                    RuntimeException("Trigger stacktrace for enklere debugging"),
                )
                sikkerLogg.error(
                    "Resend utbetaling: Kunne ikke sende utbetalingen til oppdrag for sak ${sak.id} og utbetalingId $utbetalingId",
                    it,
                )
                KunneIkkeSendeUtbetalingPåNytt.KunneIkkeSendeUtbetalingTilOppdrag(utbetalingId, sak.id, vedtak.id)
            }
        }
    }

    private class KunneIkkeKlaregjøreUtbetaling : RuntimeException()

    private fun lagNyUtbetaling(
        vedtak: VedtakEndringIYtelse,
        utbetalingId: UUID30,
        sak: Sak,
    ): Either<KunneIkkeSendeUtbetalingPåNytt, Utbetaling.UtbetalingForSimulering> {
        return when (vedtak) {
            is VedtakInnvilgetRevurdering -> {
                sak.lagNyUtbetaling(
                    saksbehandler = NavIdentBruker.Attestant(serviceUser),
                    beregning = vedtak.beregning,
                    clock = clock,
                    utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                    uføregrunnlag = vedtak.behandling.hentUføregrunnlag(),
                ).right()
            }

            is VedtakInnvilgetSøknadsbehandling -> {
                sak.lagNyUtbetaling(
                    saksbehandler = NavIdentBruker.Attestant(serviceUser),
                    beregning = vedtak.beregning,
                    clock = clock,
                    utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                    uføregrunnlag = vedtak.behandling.hentUføregrunnlag(),
                ).right()
            }

            is VedtakOpphørMedUtbetaling -> {
                sak.lagUtbetalingForOpphør(
                    opphørsperiode = vedtak.behandling.periode,
                    behandler = NavIdentBruker.Attestant(serviceUser),
                    clock = clock,
                ).right()
            }

            is VedtakGjenopptakAvYtelse -> KunneIkkeSendeUtbetalingPåNytt.IkkeStøtteForGjenopptak(
                utbetalingId,
                sak.id,
                vedtak.id,
            ).also {
                log.error("Resend utbetaling: Gjenopptak av ytelse er ikke støttet for sak ${sak.id} og utbetalingId $utbetalingId og vedtakId ${vedtak.id}")
            }.left()

            is VedtakInnvilgetRegulering -> KunneIkkeSendeUtbetalingPåNytt.IkkeStøtteForRegulering(
                utbetalingId,
                sak.id,
                vedtak.id,
            ).also {
                log.error("Resend utbetaling: Regulering er ikke støttet for sak ${sak.id} og utbetalingId $utbetalingId og vedtakId ${vedtak.id}")
            }.left()

            is VedtakStansAvYtelse -> return KunneIkkeSendeUtbetalingPåNytt.IkkeStøtteForStans(
                utbetalingId,
                sak.id,
                vedtak.id,
            ).also {
                log.error("Resend utbetaling: Stans av ytelse er ikke støttet for sak ${sak.id} og utbetalingId $utbetalingId og vedtakId ${vedtak.id}")
            }.left()
        }
    }
}

sealed interface KunneIkkeSendeUtbetalingPåNytt {
    val utbetalingId: UUID30

    data class FantIkkeSak(
        override val utbetalingId: UUID30,
    ) : KunneIkkeSendeUtbetalingPåNytt

    data class FantIkkeUtbetalingPåSak(
        override val utbetalingId: UUID30,
    ) : KunneIkkeSendeUtbetalingPåNytt

    data class FantIkkeVedtakKnyttetTilUtbetaling(
        override val utbetalingId: UUID30,
        val sakId: UUID,
    ) : KunneIkkeSendeUtbetalingPåNytt

    data class UtbetalingErIkkeKvittert(
        override val utbetalingId: UUID30,
        val sakId: UUID,
    ) : KunneIkkeSendeUtbetalingPåNytt

    /** Det finnes en utbetaling etter denne utbetalingen. */
    data class IkkeSisteUtbetaling(
        override val utbetalingId: UUID30,
        val sakId: UUID,
        val vedtakId: UUID,
    ) : KunneIkkeSendeUtbetalingPåNytt

    data class KunneIkkeSimulere(
        override val utbetalingId: UUID30,
        val sakId: UUID,
        val vedtakId: UUID,
    ) : KunneIkkeSendeUtbetalingPåNytt

    data class KunneIkkeKlargjøreUtbetaling(
        override val utbetalingId: UUID30,
        val sakId: UUID,
        val vedtakId: UUID,
    ) : KunneIkkeSendeUtbetalingPåNytt

    data class KunneIkkeSendeUtbetalingTilOppdrag(
        override val utbetalingId: UUID30,
        val sakId: UUID,
        val vedtakId: UUID,
    ) : KunneIkkeSendeUtbetalingPåNytt

    data class IkkeStøtteForRegulering(
        override val utbetalingId: UUID30,
        val sakId: UUID,
        val vedtakId: UUID,
    ) : KunneIkkeSendeUtbetalingPåNytt

    data class IkkeStøtteForStans(
        override val utbetalingId: UUID30,
        val sakId: UUID,
        val vedtakId: UUID,
    ) : KunneIkkeSendeUtbetalingPåNytt

    /**
     * TODO jah: Dette må vi støtte snarest, men vi fikser søknadsbehandlingene+revurderingene først.
     *  Vi har lagt inn en del regler i koden som gjør at vi ikke kan ha 2 gjenopptak på rad. Men vi kan revurdere den.
     */
    data class IkkeStøtteForGjenopptak(
        override val utbetalingId: UUID30,
        val sakId: UUID,
        val vedtakId: UUID,
    ) : KunneIkkeSendeUtbetalingPåNytt

    data class UkjentFeil(
        override val utbetalingId: UUID30,
    ) : KunneIkkeSendeUtbetalingPåNytt
}
