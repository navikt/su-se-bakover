package no.nav.su.se.bakover.database.utbetaling

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

internal object UtbetalingInternalRepo {
    fun hentUtbetalingInternal(utbetalingId: UUID30, session: Session): Utbetaling? =
        "select * from utbetaling where id = :id".hent(
            mapOf(
                "id" to utbetalingId
            ),
            session
        ) { it.toUtbetaling(session) }

    fun hentUtbetalinger(oppdragId: UUID30, session: Session) =
        "select * from utbetaling where oppdragId=:oppdragId".hentListe(
            mapOf("oppdragId" to oppdragId.toString()),
            session
        ) {
            it.toUtbetaling(session)
        }.toMutableList()

    fun hentUtbetalingslinjer(utbetalingId: UUID30, session: Session): List<Utbetalingslinje> =
        "select * from utbetalingslinje where utbetalingId=:utbetalingId".hentListe(
            mapOf("utbetalingId" to utbetalingId.toString()),
            session
        ) {
            it.toUtbetalingslinje()
        }
}

internal fun Row.toUtbetaling(session: Session): Utbetaling {
    val utbetalingId = uuid30("id")
    val opprettet = tidspunkt("opprettet")
    val simulering = string("simulering").let { objectMapper.readValue(it, Simulering::class.java) }
    val kvittering = stringOrNull("kvittering")?.let { objectMapper.readValue(it, Kvittering::class.java) }
    val utbetalingsrequest = string("utbetalingsrequest").let {
        objectMapper.readValue<Utbetalingsrequest>(it)
    }
    val utbetalingslinjer = UtbetalingInternalRepo.hentUtbetalingslinjer(utbetalingId, session)
    val avstemmingId = stringOrNull("avstemmingId")?.let { UUID30.fromString(it) }
    val fnr = Fnr(string("fnr"))
    val type = Utbetaling.UtbetalingsType.valueOf(string("type"))
    val oppdragId = uuid30("oppdragId")
    val behandler = NavIdentBruker.Attestant(string("behandler"))
    val avstemmingsnøkkel =
        string("avstemmingsnøkkel").let { objectMapper.readValue(it, Avstemmingsnøkkel::class.java) }

    return UtbetalingMapper(
        id = utbetalingId,
        opprettet = opprettet,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        type = type,
        avstemmingsnøkkel = avstemmingsnøkkel,
        simulering = simulering,
        utbetalingsrequest = utbetalingsrequest,
        kvittering = kvittering,
        avstemmingId = avstemmingId,
        oppdragId = oppdragId,
        behandler = behandler
    ).map()
}

internal fun Row.toUtbetalingslinje(): Utbetalingslinje {
    return Utbetalingslinje(
        id = uuid30("id"),
        fraOgMed = localDate("fom"),
        tilOgMed = localDate("tom"),
        opprettet = tidspunkt("opprettet"),
        forrigeUtbetalingslinjeId = stringOrNull("forrigeUtbetalingslinjeId")?.let { uuid30("forrigeUtbetalingslinjeId") },
        beløp = int("beløp")
    )
}
