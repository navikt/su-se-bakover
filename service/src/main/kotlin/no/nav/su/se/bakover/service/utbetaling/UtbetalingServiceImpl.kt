package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.slf4j.LoggerFactory

internal class UtbetalingServiceImpl(
    private val repo: UtbetalingRepo
) : UtbetalingService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling> {
        return repo.hentUtbetaling(utbetalingId)?.right() ?: FantIkkeUtbetaling.left()
    }

    override fun oppdaterMedKvittering(utbetalingId: UUID30, kvittering: Kvittering): Either<FantIkkeUtbetaling, Utbetaling> {
        return hentUtbetaling(utbetalingId)
            .mapLeft { FantIkkeUtbetaling }
            .map {
                if (it.erKvittert()) {
                    log.info("Kvittering er allerede mottatt for utbetaling: ${it.id}")
                    it
                } else {
                    repo.oppdaterMedKvittering(utbetalingId, kvittering)
                }
            }
    }

    override fun slettUtbetaling(utbetaling: Utbetaling) {
        return repo.slettUtbetaling(utbetaling)
    }

    override fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling {
        return repo.opprettUtbetaling(oppdragId, utbetaling)
    }

    override fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Utbetaling {
        return repo.addSimulering(utbetalingId, simulering)
    }

    override fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Utbetaling {
        return repo.addOppdragsmelding(utbetalingId, oppdragsmelding)
    }
}
