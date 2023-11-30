package no.nav.su.se.bakover.database.utbetaling

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.Simulering
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalingslinje
import økonomi.domain.utbetaling.Utbetalingsrequest
import java.util.UUID

/**
 * Brukes for å mappe en utbetaling fra databasen til en [Utbetaling.OversendtUtbetaling]
 */
internal data class UtbetalingMapper(
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
