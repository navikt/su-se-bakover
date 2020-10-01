package no.nav.su.se.bakover.database.utbetaling

import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import javax.sql.DataSource

internal class UtbetalingPostgresRepo(
    private val dataSource: DataSource
) : UtbetalingRepo {
    override fun hentUtbetaling(utbetalingId: UUID30): Utbetaling? =
        using(sessionOf(dataSource)) { session -> UtbetalingInternalRepo.hentUtbetalingInternal(utbetalingId, session) }

    override fun oppdaterMedKvittering(utbetalingId: UUID30, kvittering: Kvittering): Utbetaling {
        "update utbetaling set kvittering = to_json(:kvittering::json) where id = :id".oppdatering(
            mapOf(
                "id" to utbetalingId,
                "kvittering" to objectMapper.writeValueAsString(kvittering)
            )
        )
        return hentUtbetaling(utbetalingId)!!
    }

    override fun slettUtbetaling(utbetaling: Utbetaling) {
        check(utbetaling.kanSlettes()) { "Utbetaling har kommet for langt i utbetalingsløpet til å kunne slettes" }
        "delete from utbetaling where id=:id".oppdatering(
            mapOf(
                "id" to utbetaling.id
            )
        )
    }

    override fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling {
        """
            insert into utbetaling (id, opprettet, oppdragId, fnr)
            values (:id, :opprettet, :oppdragId, :fnr)
         """.oppdatering(
            mapOf(
                "id" to utbetaling.id,
                "opprettet" to utbetaling.opprettet,
                "oppdragId" to oppdragId,
                "fnr" to utbetaling.fnr
            )
        )
        utbetaling.utbetalingslinjer.forEach { opprettUtbetalingslinje(utbetaling.id, it) }
        return hentUtbetaling(utbetaling.id)!!
    }

    override fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Utbetaling {
        "update utbetaling set simulering = to_json(:simulering::json) where id = :id".oppdatering(
            mapOf(
                "id" to utbetalingId,
                "simulering" to objectMapper.writeValueAsString(simulering)
            )
        )
        return hentUtbetaling(utbetalingId)!!
    }

    fun opprettUtbetalingslinje(utbetalingId: UUID30, utbetalingslinje: Utbetalingslinje): Utbetalingslinje {
        """
            insert into utbetalingslinje (id, opprettet, fom, tom, utbetalingId, forrigeUtbetalingslinjeId, beløp)
            values (:id, :opprettet, :fom, :tom, :utbetalingId, :forrigeUtbetalingslinjeId, :belop)
        """.oppdatering(
            mapOf(
                "id" to utbetalingslinje.id,
                "opprettet" to utbetalingslinje.opprettet,
                "fom" to utbetalingslinje.fom,
                "tom" to utbetalingslinje.tom,
                "utbetalingId" to utbetalingId,
                "forrigeUtbetalingslinjeId" to utbetalingslinje.forrigeUtbetalingslinjeId,
                "belop" to utbetalingslinje.beløp,
            )
        )
        return utbetalingslinje
    }

    private fun String.oppdatering(params: Map<String, Any?>) {
        using(sessionOf(dataSource)) {
            this.oppdatering(params, it)
        }
    }
}
