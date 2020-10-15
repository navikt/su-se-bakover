package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

data class UtbetalingMapper(
    val id: UUID30,
    val opprettet: Tidspunkt,
    val fnr: Fnr,
    val utbetalingslinjer: List<Utbetalingslinje>,
    val type: Utbetaling.UtbetalingType,
    val simulering: Simulering?,
    val oppdragsmelding: Oppdragsmelding?,
    val kvittering: Kvittering?,
    val avstemmingId: UUID30?
) {
    fun map() = when {
        simulering == null -> {
            Utbetaling.UtbetalingForSimulering(id, opprettet, fnr, utbetalingslinjer, type)
        }
        oppdragsmelding == null -> {
            Utbetaling.SimulertUtbetaling(id, opprettet, fnr, utbetalingslinjer, type, simulering)
        }
        kvittering == null -> {
            Utbetaling.OversendtUtbetaling(id, opprettet, fnr, utbetalingslinjer, type, simulering, oppdragsmelding)
        }
        avstemmingId == null -> {
            Utbetaling.KvittertUtbetaling(
                id,
                opprettet,
                fnr,
                utbetalingslinjer,
                type,
                simulering,
                oppdragsmelding,
                kvittering
            )
        }
        else -> Utbetaling.AvstemtUtbetaling(
            id,
            opprettet,
            fnr,
            utbetalingslinjer,
            type,
            simulering,
            oppdragsmelding,
            kvittering,
            avstemmingId
        )
    }
}
