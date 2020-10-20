package no.nav.su.se.bakover.database.avstemming

import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.inClauseWith
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.database.utbetaling.toUtbetaling
import no.nav.su.se.bakover.database.uuid30
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import javax.sql.DataSource

internal class AvstemmingPostgresRepo(
    private val dataSource: DataSource
) : AvstemmingRepo {
    override fun opprettAvstemming(avstemming: Avstemming): Avstemming {
        dataSource.withSession { session ->
            """
            insert into avstemming (id, opprettet, fom, tom, utbetalinger, avstemmingXmlRequest)
            values (:id, :opprettet, :fom, :tom, to_json(:utbetalinger::json), :avstemmingXmlRequest)
        """.oppdatering(
                mapOf(
                    "id" to avstemming.id,
                    "opprettet" to avstemming.opprettet,
                    "fom" to avstemming.fraOgMed,
                    "tom" to avstemming.tilOgMed,
                    "utbetalinger" to objectMapper.writeValueAsString(avstemming.utbetalinger.map { it.id.toString() }),
                    "avstemmingXmlRequest" to avstemming.avstemmingXmlRequest
                ),
                session
            )
        }
        return hentAvstemming(avstemming.id)!!
    }

    override fun hentAvstemming(id: UUID30): Avstemming? =
        dataSource.withSession { session ->
            "select * from avstemming where id=:id".hent(mapOf("id" to id), session) {
                it.toAvstemming(session)
            }
        }

    override fun oppdaterAvstemteUtbetalinger(avstemming: Avstemming) {
        dataSource.withSession { session ->
            """
                update utbetaling set avstemmingId=:avstemmingId where id = ANY(:in)
            """.oppdatering(
                mapOf(
                    "avstemmingId" to avstemming.id,
                    "in" to session.inClauseWith(avstemming.utbetalinger.map { it.id.toString() })
                ),
                session
            )
        }
    }

    override fun hentSisteAvstemming() =
        dataSource.withSession { session ->
            """
            select * from avstemming order by tom desc limit 1
        """.hent(emptyMap(), session) {
                it.toAvstemming(session)
            }
        }

    override fun hentUtbetalingerForAvstemming(
        fraOgMed: Tidspunkt,
        tilOgMed: Tidspunkt
    ): List<Utbetaling.OversendtUtbetaling> =
        dataSource.withSession { session ->
            val fraOgMedCondition = """(avstemmingsnøkkel ->> 'opprettet')::timestamptz >= :fom"""
            val tilOgMedCondition = """(avstemmingsnøkkel ->> 'opprettet')::timestamptz <= :tom"""
            """select * from utbetaling where $fraOgMedCondition and $tilOgMedCondition"""
                .hentListe(
                    mapOf(
                        "fom" to fraOgMed,
                        "tom" to tilOgMed
                    ),
                    session
                ) {
                    it.toUtbetaling(session)
                }.filterIsInstance<Utbetaling.OversendtUtbetaling>()
        }
}

private fun Row.toAvstemming(session: Session) = Avstemming(
    id = uuid30("id"),
    opprettet = tidspunkt("opprettet"),
    fraOgMed = tidspunkt("fom"),
    tilOgMed = tidspunkt("tom"),
    utbetalinger = stringOrNull("utbetalinger")?.let { utbetalingListAsString ->
        objectMapper.readValue(utbetalingListAsString, List::class.java).map { utbetalingId ->
            UtbetalingInternalRepo.hentUtbetalingInternal(
                UUID30(utbetalingId as String),
                session
            )!! as Utbetaling.OversendtUtbetaling
        }
    }!!,
    avstemmingXmlRequest = stringOrNull("avstemmingXmlRequest")
)
