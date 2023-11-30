package no.nav.su.se.bakover.database.utbetaling

import kotliquery.Row
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.uuid30
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.database.simulering.deserializeSimulering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import vilkår.uføre.domain.Uføregrad
import økonomi.domain.kvittering.Kvittering
import java.util.UUID

internal data object UtbetalingInternalRepo {
    fun hentOversendtUtbetaling(utbetalingId: UUID30, session: Session): Utbetaling.OversendtUtbetaling? =
        "select u.*, s.saksnummer, s.type as sakstype from utbetaling u join sak s on s.id = u.sakId where u.id = :id".hent(
            mapOf(
                "id" to utbetalingId,
            ),
            session,
        ) { it.toUtbetaling(session) }

    fun hentOversendteUtbetalinger(
        sakId: UUID,
        session: Session,
    ): Utbetalinger {
        return "select u.*, s.saksnummer, s.type as sakstype from utbetaling u join sak s on s.id = u.sakId where s.id = :id order by u.opprettet".hentListe(
            mapOf(
                "id" to sakId,
            ),
            session,
        ) { it.toUtbetaling(session) }.let { Utbetalinger(it) }
    }

    fun hentUtbetalingslinjer(utbetalingId: UUID30, session: Session): List<Utbetalingslinje> =
        "select * from utbetalingslinje where utbetalingId=:utbetalingId order by rekkefølge".hentListe(
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
    val simulering = string("simulering").deserializeSimulering()
    val kvittering = deserializeNullable<Kvittering>(stringOrNull("kvittering"))
    val utbetalingsrequest = deserialize<Utbetalingsrequest>(string("utbetalingsrequest"))
    val utbetalingslinjer = UtbetalingInternalRepo.hentUtbetalingslinjer(utbetalingId, session)
    val avstemmingId = stringOrNull("avstemmingId")?.let { UUID30.fromString(it) }
    val sakId = uuid("sakId")
    val saksnummer = Saksnummer(long("saksnummer"))
    val fnr = Fnr(string("fnr"))
    val behandler = NavIdentBruker.Attestant(string("behandler"))
    val avstemmingsnøkkel = deserialize<Avstemmingsnøkkel>(string("avstemmingsnøkkel"))
    val sakstype = Sakstype.from(string("sakstype"))

    return UtbetalingMapper(
        id = utbetalingId,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer.toNonEmptyList(),
        avstemmingsnøkkel = avstemmingsnøkkel,
        simulering = simulering,
        utbetalingsrequest = utbetalingsrequest,
        kvittering = kvittering,
        avstemmingId = avstemmingId,
        behandler = behandler,
        sakstype = sakstype,
    ).map()
}

private fun Row.toUtbetalingslinje(): Utbetalingslinje {
    val status = stringOrNull("status")
    val statusFraOgMed = localDateOrNull("statusFraOgMed")
    val statusTilOgMed = localDateOrNull("statusTilOgMed")

    val linje = Utbetalingslinje.Ny(
        id = uuid30("id"),
        fraOgMed = localDate("fom"),
        tilOgMed = localDate("tom"),
        opprettet = tidspunkt("opprettet"),
        rekkefølge = Rekkefølge(long("rekkefølge")),
        forrigeUtbetalingslinjeId = stringOrNull("forrigeUtbetalingslinjeId")?.let { uuid30("forrigeUtbetalingslinjeId") },
        beløp = int("beløp"),
        uføregrad = intOrNull("uføregrad")?.let { Uføregrad.parse(it) },
        utbetalingsinstruksjonForEtterbetalinger = toKjøreplan(),
    )

    return if (status != null && statusFraOgMed != null && statusTilOgMed != null) {
        when (Utbetalingslinje.Endring.LinjeStatus.valueOf(status)) {
            Utbetalingslinje.Endring.LinjeStatus.OPPHØR -> {
                Utbetalingslinje.Endring.Opphør(
                    id = linje.id,
                    opprettet = linje.opprettet,
                    rekkefølge = linje.rekkefølge,
                    fraOgMed = linje.originalFraOgMed(),
                    tilOgMed = linje.originalTilOgMed(),
                    forrigeUtbetalingslinjeId = linje.forrigeUtbetalingslinjeId,
                    beløp = linje.beløp,
                    virkningsperiode = Periode.create(statusFraOgMed, statusTilOgMed),
                    uføregrad = linje.uføregrad,
                    utbetalingsinstruksjonForEtterbetalinger = toKjøreplan(),
                )
            }

            Utbetalingslinje.Endring.LinjeStatus.STANS -> {
                Utbetalingslinje.Endring.Stans(
                    id = linje.id,
                    opprettet = linje.opprettet,
                    rekkefølge = linje.rekkefølge,
                    fraOgMed = linje.originalFraOgMed(),
                    tilOgMed = linje.originalTilOgMed(),
                    forrigeUtbetalingslinjeId = linje.forrigeUtbetalingslinjeId,
                    beløp = linje.beløp,
                    virkningsperiode = Periode.create(statusFraOgMed, statusTilOgMed),
                    uføregrad = linje.uføregrad,
                    utbetalingsinstruksjonForEtterbetalinger = toKjøreplan(),
                )
            }

            Utbetalingslinje.Endring.LinjeStatus.REAKTIVERING -> {
                Utbetalingslinje.Endring.Reaktivering(
                    id = linje.id,
                    opprettet = linje.opprettet,
                    rekkefølge = linje.rekkefølge,
                    fraOgMed = linje.originalFraOgMed(),
                    tilOgMed = linje.originalTilOgMed(),
                    forrigeUtbetalingslinjeId = linje.forrigeUtbetalingslinjeId,
                    beløp = linje.beløp,
                    virkningsperiode = Periode.create(statusFraOgMed, statusTilOgMed),
                    uføregrad = linje.uføregrad,
                    utbetalingsinstruksjonForEtterbetalinger = toKjøreplan(),
                )
            }
        }
    } else {
        linje
    }
}

private fun Row.toKjøreplan() = when (boolean("kjøreplan")) {
    true -> UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling
    false -> UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig
}
