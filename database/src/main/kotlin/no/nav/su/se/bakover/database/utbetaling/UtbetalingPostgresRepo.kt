package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import java.util.UUID
import javax.sql.DataSource

internal class UtbetalingPostgresRepo(
    private val dataSource: DataSource,
    private val dbMetrics: DbMetrics,
) : UtbetalingRepo {
    override fun hentUtbetaling(utbetalingId: UUID30): Utbetaling.OversendtUtbetaling? {
        return dbMetrics.timeQuery("hentUtbetalingId") {
            dataSource.withSession { session -> UtbetalingInternalRepo.hentUtbetalingInternal(utbetalingId, session) }
        }
    }

    override fun hentUtbetaling(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling.OversendtUtbetaling? {
        return dataSource.withSession { session ->
            "select u.*, s.saksnummer from utbetaling u left join sak s on s.id = u.sakId where u.avstemmingsnøkkel ->> 'nøkkel' = :nokkel".hent(
                mapOf(
                    "nokkel" to avstemmingsnøkkel.toString(),
                ),
                session,
            ) { it.toUtbetaling(session) }
        }
    }

    override fun hentUtbetalinger(sakId: UUID): List<Utbetaling> {
        return dataSource.withSession { session -> UtbetalingInternalRepo.hentUtbetalinger(sakId, session) }
    }

    override fun hentUkvitterteUtbetalinger(): List<Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return dataSource.withSession { session ->
            "select u.*, s.saksnummer from utbetaling u left join sak s on s.id = u.sakId where u.kvittering is null".hentListe(
                session = session,
            ) { it.toUtbetaling(session) as Utbetaling.OversendtUtbetaling.UtenKvittering }
        }
    }

    override fun oppdaterMedKvittering(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering) {
        dataSource.withSession { session ->
            "update utbetaling set kvittering = to_json(:kvittering::json) where id = :id".oppdatering(
                mapOf(
                    "id" to utbetaling.id,
                    "kvittering" to objectMapper.writeValueAsString(utbetaling.kvittering),
                ),
                session,
            )
        }
    }

    override fun opprettUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering) {
        dataSource.withTransaction { session ->
            """
            insert into utbetaling (id, opprettet, sakId, fnr, type, avstemmingsnøkkel, simulering, utbetalingsrequest, behandler)
            values (:id, :opprettet, :sakId, :fnr, :type, to_json(:avstemmingsnokkel::json), to_json(:simulering::json), to_json(:utbetalingsrequest::json), :behandler)
         """.insert(
                mapOf(
                    "id" to utbetaling.id,
                    "opprettet" to utbetaling.opprettet,
                    "sakId" to utbetaling.sakId,
                    "fnr" to utbetaling.fnr,
                    "type" to utbetaling.type.name,
                    "avstemmingsnokkel" to objectMapper.writeValueAsString(utbetaling.avstemmingsnøkkel),
                    "simulering" to objectMapper.writeValueAsString(utbetaling.simulering),
                    "utbetalingsrequest" to objectMapper.writeValueAsString(utbetaling.utbetalingsrequest),
                    "behandler" to utbetaling.behandler.navIdent,
                ),
                session,
            )
            utbetaling.utbetalingslinjer.forEach { opprettUtbetalingslinje(utbetaling.id, it, session) }
        }
    }

    private fun opprettUtbetalingslinje(
        utbetalingId: UUID30,
        utbetalingslinje: Utbetalingslinje,
        session: Session,
    ): Utbetalingslinje {
        val baseParams = mapOf(
            "id" to utbetalingslinje.id,
            "opprettet" to utbetalingslinje.opprettet,
            "fom" to utbetalingslinje.fraOgMed,
            "tom" to utbetalingslinje.tilOgMed,
            "utbetalingId" to utbetalingId,
            "forrigeUtbetalingslinjeId" to utbetalingslinje.forrigeUtbetalingslinjeId,
            "belop" to utbetalingslinje.beløp,
        )

        val params = when (utbetalingslinje) {
            is Utbetalingslinje.Endring -> {
                baseParams.plus(
                    mapOf(
                        "status" to utbetalingslinje.statusendring.status,
                        "statusFraOgMed" to utbetalingslinje.statusendring.fraOgMed,
                    ),
                )
            }
            is Utbetalingslinje.Ny -> baseParams
        }
        """
            insert into utbetalingslinje (id, opprettet, fom, tom, utbetalingId, forrigeUtbetalingslinjeId, beløp, status, statusFraOgMed)
            values (:id, :opprettet, :fom, :tom, :utbetalingId, :forrigeUtbetalingslinjeId, :belop, :status, :statusFraOgMed)
        """.insert(params, session)

        return utbetalingslinje
    }
}
