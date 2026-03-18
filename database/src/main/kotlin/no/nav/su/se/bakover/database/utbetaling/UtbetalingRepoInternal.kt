package no.nav.su.se.bakover.database.utbetaling

import kotliquery.Row
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.inClauseWith
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.uuid30
import no.nav.su.se.bakover.common.infrastructure.persistence.uuidInClauseWith
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.database.simulering.deserializeSimulering
import vilkår.uføre.domain.Uføregrad
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import økonomi.domain.utbetaling.Utbetalingslinje
import økonomi.domain.utbetaling.Utbetalingsrequest
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

    fun hentOversendteUtbetalingerForSakIder(
        sakIder: List<UUID>,
        session: Session,
    ): Map<UUID, Utbetalinger> {
        if (sakIder.isEmpty()) return emptyMap()

        val utbetalinger = """
            select u.*, s.saksnummer, s.type as sakstype
            from utbetaling u
            join sak s on s.id = u.sakId
            where u.sakId = any(:sakIder)
            order by u.sakId, u.opprettet
        """.hentListe(
            mapOf("sakIder" to session.uuidInClauseWith(sakIder)),
            session,
        ) {
            it.toUtbetalingData()
        }

        if (utbetalinger.isEmpty()) return emptyMap()

        val utbetalingslinjerPerUtbetalingId = hentUtbetalingslinjerForUtbetalinger(
            utbetalinger.map { it.id },
            session,
        )

        return utbetalinger.groupBy { it.sakId }.mapValues { (_, utbetalingerForSak) ->
            Utbetalinger(
                utbetalingerForSak.map { utbetaling ->
                    utbetaling.toUtbetaling(
                        utbetalingslinjer = utbetalingslinjerPerUtbetalingId[utbetaling.id]
                            ?: error("Fant ikke utbetalingslinjer for utbetaling ${utbetaling.id}"),
                    )
                },
            )
        }
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

    private fun hentUtbetalingslinjerForUtbetalinger(
        utbetalingIder: List<UUID30>,
        session: Session,
    ): Map<UUID30, List<Utbetalingslinje>> {
        if (utbetalingIder.isEmpty()) return emptyMap()

        return """
            select *
            from utbetalingslinje
            where utbetalingId = any(:utbetalingIder)
            order by utbetalingId, rekkefølge
        """.hentListe(
            mapOf("utbetalingIder" to session.inClauseWith(utbetalingIder.map { it.toString() })),
            session,
        ) {
            it.string("utbetalingId") to it.toUtbetalingslinje()
        }.groupBy(
            keySelector = { UUID30.fromString(it.first) },
            valueTransform = { it.second },
        )
    }
}

internal fun Row.toUtbetaling(session: Session): Utbetaling.OversendtUtbetaling {
    val utbetaling = toUtbetalingData()
    return utbetaling.toUtbetaling(
        utbetalingslinjer = UtbetalingInternalRepo.hentUtbetalingslinjer(utbetaling.id, session),
    )
}

private data class UtbetalingData(
    val id: UUID30,
    val opprettet: no.nav.su.se.bakover.common.tid.Tidspunkt,
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val avstemmingsnøkkel: Avstemmingsnøkkel,
    val simulering: økonomi.domain.simulering.Simulering,
    val utbetalingsrequest: Utbetalingsrequest,
    val kvittering: Kvittering?,
    val avstemmingId: UUID30?,
    val behandler: NavIdentBruker,
    val sakstype: Sakstype,
)

private fun Row.toUtbetalingData(): UtbetalingData {
    return UtbetalingData(
        id = uuid30("id"),
        opprettet = tidspunkt("opprettet"),
        sakId = uuid("sakId"),
        saksnummer = Saksnummer(long("saksnummer")),
        fnr = Fnr(string("fnr")),
        avstemmingsnøkkel = deserialize<Avstemmingsnøkkel>(string("avstemmingsnøkkel")),
        simulering = string("simulering").deserializeSimulering(),
        utbetalingsrequest = deserialize<Utbetalingsrequest>(string("utbetalingsrequest")),
        kvittering = deserializeNullable<Kvittering>(stringOrNull("kvittering")),
        avstemmingId = stringOrNull("avstemmingId")?.let { UUID30.fromString(it) },
        behandler = NavIdentBruker.Attestant(string("behandler")),
        sakstype = Sakstype.from(string("sakstype")),
    )
}

private fun UtbetalingData.toUtbetaling(
    utbetalingslinjer: List<Utbetalingslinje>,
): Utbetaling.OversendtUtbetaling {
    return UtbetalingMapper(
        id = id,
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
