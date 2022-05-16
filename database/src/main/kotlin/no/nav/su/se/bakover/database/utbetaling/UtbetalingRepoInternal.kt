package no.nav.su.se.bakover.database.utbetaling

import arrow.core.NonEmptyList
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
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingskjøreplan
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

internal object UtbetalingInternalRepo {
    fun hentUtbetalingInternal(utbetalingId: UUID30, session: Session): Utbetaling.OversendtUtbetaling? =
        "select u.*, s.saksnummer from utbetaling u inner join sak s on s.id = u.sakId where u.id = :id".hent(
            mapOf(
                "id" to utbetalingId,
            ),
            session,
        ) { it.toUtbetaling(session) }

    fun hentUtbetalinger(sakId: UUID, session: Session): List<Utbetaling.OversendtUtbetaling> =
        "select u.*, s.saksnummer from utbetaling u inner join sak s on s.id = u.sakId where s.id = :id".hentListe(
            mapOf(
                "id" to sakId,
            ),
            session,
        ) { it.toUtbetaling(session) }

    fun hentUtbetalingslinjer(utbetalingId: UUID30, session: Session): List<Utbetalingslinje> =
        "select * from utbetalingslinje where utbetalingId=:utbetalingId".hentListe(
            mapOf("utbetalingId" to utbetalingId.toString()),
            session,
        ) {
            it.toUtbetalingslinje()
        }

    fun hentUtbetalingslinje(utbetalingslinjeId: UUID30, session: Session): Utbetalingslinje? =
        """select * from utbetalingslinje where id = :id""".hent(
            mapOf("id" to utbetalingslinjeId),
            session,
        ) {
            it.toUtbetalingslinje()
        }
}

internal fun Row.toUtbetaling(session: Session): Utbetaling.OversendtUtbetaling {
    val utbetalingId = uuid30("id")
    val opprettet = tidspunkt("opprettet")
    val simulering = string("simulering").let { objectMapper.readValue<Simulering>(it) }
    val kvittering = stringOrNull("kvittering")?.let { objectMapper.readValue<Kvittering>(it) }
    val utbetalingsrequest = string("utbetalingsrequest").let {
        objectMapper.readValue<Utbetalingsrequest>(it)
    }
    val utbetalingslinjer = UtbetalingInternalRepo.hentUtbetalingslinjer(utbetalingId, session)
    val avstemmingId = stringOrNull("avstemmingId")?.let { UUID30.fromString(it) }
    val sakId = uuid("sakId")
    val saksnummer = Saksnummer(long("saksnummer"))
    val fnr = Fnr(string("fnr"))
    val type = Utbetaling.UtbetalingsType.valueOf(string("type"))
    val behandler = NavIdentBruker.Attestant(string("behandler"))
    val avstemmingsnøkkel = string("avstemmingsnøkkel").let { objectMapper.readValue<Avstemmingsnøkkel>(it) }

    return UtbetalingMapper(
        id = utbetalingId,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = NonEmptyList.fromListUnsafe(utbetalingslinjer),
        type = type,
        avstemmingsnøkkel = avstemmingsnøkkel,
        simulering = simulering,
        utbetalingsrequest = utbetalingsrequest,
        kvittering = kvittering,
        avstemmingId = avstemmingId,
        behandler = behandler,
    ).map()
}

internal fun Row.toUtbetalingslinje(): Utbetalingslinje {
    val status = stringOrNull("status")
    val statusFraOgMed = localDateOrNull("statusFraOgMed")

    val linje = Utbetalingslinje.Ny(
        id = uuid30("id"),
        fraOgMed = localDate("fom"),
        tilOgMed = localDate("tom"),
        opprettet = tidspunkt("opprettet"),
        forrigeUtbetalingslinjeId = stringOrNull("forrigeUtbetalingslinjeId")?.let { uuid30("forrigeUtbetalingslinjeId") },
        beløp = int("beløp"),
        uføregrad = intOrNull("uføregrad")?.let { Uføregrad.parse(it) },
        kjøreplan = toKjøreplan(),
    )

    return if (status != null && statusFraOgMed != null) {
        when (Utbetalingslinje.Endring.LinjeStatus.valueOf(status)) {
            Utbetalingslinje.Endring.LinjeStatus.OPPHØR -> {
                Utbetalingslinje.Endring.Opphør(
                    id = linje.id,
                    opprettet = linje.opprettet,
                    fraOgMed = linje.fraOgMed,
                    tilOgMed = linje.tilOgMed,
                    forrigeUtbetalingslinjeId = linje.forrigeUtbetalingslinjeId,
                    beløp = linje.beløp,
                    virkningstidspunkt = statusFraOgMed,
                    uføregrad = linje.uføregrad,
                    kjøreplan = toKjøreplan(),
                )
            }
            Utbetalingslinje.Endring.LinjeStatus.STANS -> {
                Utbetalingslinje.Endring.Stans(
                    id = linje.id,
                    opprettet = linje.opprettet,
                    fraOgMed = linje.fraOgMed,
                    tilOgMed = linje.tilOgMed,
                    forrigeUtbetalingslinjeId = linje.forrigeUtbetalingslinjeId,
                    beløp = linje.beløp,
                    virkningstidspunkt = statusFraOgMed,
                    uføregrad = linje.uføregrad,
                    kjøreplan = toKjøreplan(),
                )
            }
            Utbetalingslinje.Endring.LinjeStatus.REAKTIVERING -> {
                Utbetalingslinje.Endring.Reaktivering(
                    id = linje.id,
                    opprettet = linje.opprettet,
                    fraOgMed = linje.fraOgMed,
                    tilOgMed = linje.tilOgMed,
                    forrigeUtbetalingslinjeId = linje.forrigeUtbetalingslinjeId,
                    beløp = linje.beløp,
                    virkningstidspunkt = statusFraOgMed,
                    uføregrad = linje.uføregrad,
                    kjøreplan = toKjøreplan(),
                )
            }
        }
    } else {
        linje
    }
}

private fun Row.toKjøreplan() = when (boolean("kjøreplan")) {
    true -> Utbetalingskjøreplan.JA
    false -> Utbetalingskjøreplan.NEI
}
