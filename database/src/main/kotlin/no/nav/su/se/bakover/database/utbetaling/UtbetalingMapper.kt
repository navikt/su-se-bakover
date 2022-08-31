package no.nav.su.se.bakover.database.utbetaling

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

data class UtbetalingMapper(
    val id: UUID30,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
    val avstemmingsnøkkel: Avstemmingsnøkkel,
    val simulering: Simulering,
    val utbetalingsrequest: Utbetalingsrequest,
    val kvittering: Kvittering?,
    val avstemmingId: UUID30?,
    val behandler: NavIdentBruker,
    val sakstype: Sakstype,
) {
    fun map(): Utbetaling.OversendtUtbetaling {
        return Utbetaling.UtbetalingForSimulering(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = utbetalingslinjer,
            behandler = behandler,
            avstemmingsnøkkel = avstemmingsnøkkel,
            sakstype = sakstype,
        ).toSimulertUtbetaling(
            simulering = simulering,
        ).toOversendtUtbetaling(
            oppdragsmelding = utbetalingsrequest,
        ).let {
            when (kvittering) {
                null -> {
                    it
                }
                else -> {
                    it.toKvittertUtbetaling(kvittering = kvittering)
                }
            }
        }
    }
}
