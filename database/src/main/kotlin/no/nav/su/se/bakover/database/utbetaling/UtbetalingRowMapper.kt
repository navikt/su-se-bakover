package no.nav.su.se.bakover.database.utbetaling

import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.OppdragsmeldingJson
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo.hentUtbetalingslinjer
import no.nav.su.se.bakover.database.uuid30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

internal fun Row.toUtbetaling(session: Session): Utbetaling {
    val utbetalingId = uuid30("id")
    return Utbetaling(
        id = utbetalingId,
        opprettet = tidspunkt("opprettet"),
        simulering = stringOrNull("simulering")?.let { objectMapper.readValue(it, Simulering::class.java) },
        kvittering = stringOrNull("kvittering")?.let { objectMapper.readValue(it, Kvittering::class.java) },
        oppdragsmelding = stringOrNull("oppdragsmelding")?.let {
            objectMapper.readValue(
                it,
                OppdragsmeldingJson::class.java
            )
        }?.toOppdragsmelding(),
        utbetalingslinjer = hentUtbetalingslinjer(utbetalingId, session),
        avstemmingId = stringOrNull("avstemmingId")?.let { UUID30.fromString(it) },
        fnr = Fnr(string("fnr"))
    )
}
