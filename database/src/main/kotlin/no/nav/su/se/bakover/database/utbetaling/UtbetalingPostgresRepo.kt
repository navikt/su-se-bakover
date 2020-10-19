package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import javax.sql.DataSource

internal class UtbetalingPostgresRepo(
    private val dataSource: DataSource
) : UtbetalingRepo {
    override fun hentUtbetaling(utbetalingId: UUID30): Utbetaling? =
        dataSource.withSession { session -> UtbetalingInternalRepo.hentUtbetalingInternal(utbetalingId, session) }

    override fun hentUtbetaling(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling? =
        dataSource.withSession { session ->
            "select * from utbetaling where oppdragsmelding -> 'avstemmingsnøkkel' ->> 'nøkkel' = :nokkel".hent(
                mapOf(
                    "nokkel" to avstemmingsnøkkel.toString()
                ),
                session
            ) { it.toUtbetaling(session) }
        }

    override fun oppdaterMedKvittering(utbetalingId: UUID30, kvittering: Kvittering): Utbetaling {
        dataSource.withSession { session ->
            "update utbetaling set kvittering = to_json(:kvittering::json) where id = :id".oppdatering(
                mapOf(
                    "id" to utbetalingId,
                    "kvittering" to objectMapper.writeValueAsString(kvittering)
                ),
                session
            )
        }
        return hentUtbetaling(utbetalingId)!!
    }

    override fun opprettUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling) {
        dataSource.withSession { session ->
            """
            insert into utbetaling (id, opprettet, oppdragId, fnr, type, avstemmingsnøkkel, simulering, oppdragsmelding, behandler)
            values (:id, :opprettet, :oppdragId, :fnr, :type, :avstemmingsnokkel, to_json(:simulering::json), to_json(:oppdragsmelding::json), :behandler)
         """.oppdatering(
                mapOf(
                    "id" to utbetaling.id,
                    "opprettet" to utbetaling.opprettet,
                    "oppdragId" to utbetaling.oppdragId,
                    "fnr" to utbetaling.fnr,
                    "type" to utbetaling.type.name,
                    "avstemmingsnokkel" to objectMapper.writeValueAsString(utbetaling.avstemmingsnøkkel),
                    "simulering" to objectMapper.writeValueAsString(utbetaling.simulering),
                    "oppdragsmelding" to objectMapper.writeValueAsString(utbetaling.oppdragsmelding),
                    "behandler" to utbetaling.behandler.navIdent
                ),
                session
            )
        }
        utbetaling.utbetalingslinjer.forEach { opprettUtbetalingslinje(utbetaling.id, it) }
    }

    internal fun opprettUtbetalingslinje(utbetalingId: UUID30, utbetalingslinje: Utbetalingslinje): Utbetalingslinje {
        dataSource.withSession { session ->
            """
            insert into utbetalingslinje (id, opprettet, fom, tom, utbetalingId, forrigeUtbetalingslinjeId, beløp)
            values (:id, :opprettet, :fom, :tom, :utbetalingId, :forrigeUtbetalingslinjeId, :belop)
        """.oppdatering(
                mapOf(
                    "id" to utbetalingslinje.id,
                    "opprettet" to utbetalingslinje.opprettet,
                    "fom" to utbetalingslinje.fraOgMed,
                    "tom" to utbetalingslinje.tilOgMed,
                    "utbetalingId" to utbetalingId,
                    "forrigeUtbetalingslinjeId" to utbetalingslinje.forrigeUtbetalingslinjeId,
                    "belop" to utbetalingslinje.beløp,
                ),
                session
            )
        }
        return utbetalingslinje
    }
}
