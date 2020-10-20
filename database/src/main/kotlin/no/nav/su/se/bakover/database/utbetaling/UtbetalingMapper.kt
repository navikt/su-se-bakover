package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

data class UtbetalingMapper(
    val id: UUID30,
    val opprettet: Tidspunkt,
    val fnr: Fnr,
    val utbetalingslinjer: List<Utbetalingslinje>,
    val type: Utbetaling.UtbetalingsType,
    val avstemmingsnøkkel: Avstemmingsnøkkel,
    val simulering: Simulering,
    val utbetalingsrequest: Utbetalingsrequest,
    val kvittering: Kvittering?,
    val avstemmingId: UUID30?,
    val oppdragId: UUID30,
    val behandler: NavIdentBruker
) {
    fun map() = when {
        kvittering == null -> {
            Utbetaling.OversendtUtbetaling(
                id = id,
                opprettet = opprettet,
                fnr = fnr,
                utbetalingslinjer = utbetalingslinjer,
                type = type,
                oppdragId = oppdragId,
                behandler = behandler,
                avstemmingsnøkkel = avstemmingsnøkkel,
                simulering = simulering,
                utbetalingsrequest = utbetalingsrequest
            )
        }
        avstemmingId == null -> {
            Utbetaling.KvittertUtbetaling(
                id = id,
                opprettet = opprettet,
                fnr = fnr,
                utbetalingslinjer = utbetalingslinjer,
                type = type,
                oppdragId = oppdragId,
                behandler = behandler,
                avstemmingsnøkkel = avstemmingsnøkkel,
                simulering = simulering,
                oppdragsmelding = utbetalingsrequest,
                kvittering = kvittering
            )
        }
        else -> Utbetaling.AvstemtUtbetaling(
            id = id,
            opprettet = opprettet,
            fnr = fnr,
            utbetalingslinjer = utbetalingslinjer,
            type = type,
            oppdragId = oppdragId,
            behandler = behandler,
            avstemmingsnøkkel = avstemmingsnøkkel,
            simulering = simulering,
            oppdragsmelding = utbetalingsrequest,
            kvittering = kvittering,
            avstemmingId = avstemmingId
        )
    }
}
