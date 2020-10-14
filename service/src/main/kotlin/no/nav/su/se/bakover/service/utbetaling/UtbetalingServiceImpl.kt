package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.slf4j.LoggerFactory
import java.util.UUID

internal class UtbetalingServiceImpl(
    private val utbetalingRepo: UtbetalingRepo,
    private val sakRepo: SakRepo
) : UtbetalingService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling> {
        return utbetalingRepo.hentUtbetaling(utbetalingId)?.right() ?: FantIkkeUtbetaling.left()
    }

    override fun oppdaterMedKvittering(
        avstemmingsnøkkel: Avstemmingsnøkkel,
        kvittering: Kvittering
    ): Either<FantIkkeUtbetaling, Utbetaling> {
        return utbetalingRepo.hentUtbetaling(avstemmingsnøkkel)
            ?.let {
                if (it.erKvittert()) {
                    log.info("Kvittering er allerede mottatt for utbetaling: ${it.id}")
                    it
                } else {
                    utbetalingRepo.oppdaterMedKvittering(it.id, kvittering)
                }.right()
            } ?: FantIkkeUtbetaling.left()
    }

    override fun slettUtbetaling(utbetaling: Utbetaling) {
        return utbetalingRepo.slettUtbetaling(utbetaling)
    }

    override fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling {
        return utbetalingRepo.opprettUtbetaling(oppdragId, utbetaling)
    }

    override fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Utbetaling {
        return utbetalingRepo.addSimulering(utbetalingId, simulering)
    }

    override fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Utbetaling {
        return utbetalingRepo.addOppdragsmelding(utbetalingId, oppdragsmelding)
    }

    override fun lagUtbetalingForSimulering(sakId: UUID, beregning: Beregning): NyUtbetaling {
        val sak = sakRepo.hentSak(sakId = sakId)!!
        val utbetaling = sak.oppdrag.genererUtbetaling(Oppdrag.UtbetalingStrategy.Ny(beregning), sak.fnr)
        return NyUtbetaling(sak.oppdrag, utbetaling, Attestant("SU"))
    }
}
