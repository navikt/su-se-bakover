package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

data class UtbetalingMapper(
    val id: UUID30,
    val opprettet: Tidspunkt,
    val fnr: Fnr,
    val utbetalingslinjer: List<Utbetalingslinje>,
    val type: Utbetaling.UtbetalingsType,
    val avstemmingsnøkkel: Avstemmingsnøkkel,
    val simulering: Simulering?,
    val oppdragsmelding: Oppdragsmelding?,
    val kvittering: Kvittering?,
    val avstemmingId: UUID30?,
    val oppdragId: UUID30,
    val behandler: NavIdentBruker
) {
    fun map() = when {
        simulering == null -> {
            // TODO: Vurdere kaste IllegalStateException eller vente til vi har gjort simulerings-kolonnen i utbetaling-tabellen non-null
            Utbetaling.UtbetalingForSimulering(
                id = id,
                opprettet = opprettet,
                fnr = fnr,
                utbetalingslinjer = utbetalingslinjer,
                type = type,
                oppdragId = oppdragId,
                behandler = behandler,
                avstemmingsnøkkel = avstemmingsnøkkel
            )
        }
        oppdragsmelding == null -> {
            Utbetaling.SimulertUtbetaling(
                id = id,
                opprettet = opprettet,
                fnr = fnr,
                utbetalingslinjer = utbetalingslinjer,
                type = type,
                oppdragId = oppdragId,
                behandler = behandler,
                avstemmingsnøkkel = avstemmingsnøkkel,
                simulering = simulering
            )
        }
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
                oppdragsmelding = oppdragsmelding
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
                oppdragsmelding = oppdragsmelding,
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
            oppdragsmelding = oppdragsmelding,
            kvittering = kvittering,
            avstemmingId = avstemmingId
        )
    }
}
