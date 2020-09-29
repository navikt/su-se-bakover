package no.nav.su.se.bakover.database.avstemming

import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo.hentUtbetalingInternal
import no.nav.su.se.bakover.database.uuid30
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming

internal fun Row.toAvstemming(session: Session) = Avstemming(
    id = uuid30("id"),
    opprettet = tidspunkt("opprettet"),
    fraOgMed = tidspunkt("fom"),
    tilOgMed = tidspunkt("tom"),
    utbetalinger = stringOrNull("utbetalinger")?.let { utbetalingListAsString ->
        objectMapper.readValue(utbetalingListAsString, List::class.java).map { utbetalingId ->
            hentUtbetalingInternal(UUID30(utbetalingId as String), session)!!
        }
    }!!,
    avstemmingXmlRequest = stringOrNull("avstemmingXmlRequest")
)
