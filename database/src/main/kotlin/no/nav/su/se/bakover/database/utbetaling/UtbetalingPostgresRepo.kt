package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
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

    override fun slettUtbetaling(utbetaling: Utbetaling) {
        check(utbetaling.kanSlettes()) { "Utbetaling har kommet for langt i utbetalingsløpet til å kunne slettes" }
        dataSource.withSession { session ->
            "delete from utbetaling where id=:id".oppdatering(
                mapOf(
                    "id" to utbetaling.id
                ),
                session
            )
        }
    }

    override fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling {
        dataSource.withSession { session ->
            """
            insert into utbetaling (id, opprettet, oppdragId, fnr)
            values (:id, :opprettet, :oppdragId, :fnr)
         """.oppdatering(
                mapOf(
                    "id" to utbetaling.id,
                    "opprettet" to utbetaling.opprettet,
                    "oppdragId" to oppdragId,
                    "fnr" to utbetaling.fnr
                ),
                session
            )
        }
        utbetaling.utbetalingslinjer.forEach { opprettUtbetalingslinje(utbetaling.id, it) }
        return hentUtbetaling(utbetaling.id)!!
    }

    override fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Utbetaling {
        dataSource.withSession { session ->
            "update utbetaling set simulering = to_json(:simulering::json) where id = :id".oppdatering(
                mapOf(
                    "id" to utbetalingId,
                    "simulering" to objectMapper.writeValueAsString(simulering)
                ),
                session
            )
        }
        return hentUtbetaling(utbetalingId)!!
    }

    override fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Utbetaling {
        dataSource.withSession { session ->
            "update utbetaling set oppdragsmelding = to_json(:oppdragsmelding::json) where id = :id".oppdatering(
                mapOf(
                    "id" to utbetalingId,
                    "oppdragsmelding" to objectMapper.writeValueAsString(oppdragsmelding)
                ),
                session
            )
        }
        return hentUtbetaling(utbetalingId)!!
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
