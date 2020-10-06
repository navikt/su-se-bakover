package no.nav.su.se.bakover.database.avstemming

import kotliquery.using
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.inClauseWith
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.database.utbetaling.toUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import javax.sql.DataSource

internal class AvstemmingPostgresRepo(
    private val dataSource: DataSource
) : AvstemmingRepo {
    override fun opprettAvstemming(avstemming: Avstemming): Avstemming {
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
            )
        )
        return hentAvstemming(avstemming.id)!!
    }

    override fun hentAvstemming(id: UUID30): Avstemming? = using(sessionOf(dataSource)) { session ->
        "select * from avstemming where id=:id".hent(mapOf("id" to id), session) {
            it.toAvstemming(session)
        }
    }

    override fun oppdaterAvstemteUtbetalinger(avstemming: Avstemming) {
        using(sessionOf(dataSource)) { session ->
            """
                update utbetaling set avstemmingId=:avstemmingId where id = ANY(:in)
            """.oppdatering(
                mapOf(
                    "avstemmingId" to avstemming.id,
                    "in" to session.inClauseWith(avstemming.utbetalinger.map { it.id.toString() })
                )
            )
        }
    }

    override fun hentSisteAvstemming() = using(sessionOf(dataSource)) { session ->
        """
            select * from avstemming order by tom desc limit 1
        """.hent(emptyMap(), session) {
            it.toAvstemming(session)
        }
    }

    override fun hentUtbetalingerForAvstemming(fraOgMed: Tidspunkt, tilOgMed: Tidspunkt): List<Utbetaling> =
        using(sessionOf(dataSource)) { session ->
            // Bakoverkompatibel med gammel json-modell
            val fraOgMedCondition = """((oppdragsmelding -> 'avstemmingsnøkkel' ->> 'opprettet')::timestamptz >= :fom or (oppdragsmelding ->> 'tidspunkt')::timestamptz >= :fom)"""
            val tilOgMedCondition = """((oppdragsmelding -> 'avstemmingsnøkkel' ->> 'opprettet')::timestamptz <= :tom or (oppdragsmelding ->> 'tidspunkt')::timestamptz <= :tom)"""
            """select * from utbetaling where oppdragsmelding is not null and $fraOgMedCondition and $tilOgMedCondition and oppdragsmelding ->> 'status' = :status""".trimMargin()
                .hentListe(
                    mapOf(
                        "fom" to fraOgMed,
                        "tom" to tilOgMed,
                        "status" to Oppdragsmelding.Oppdragsmeldingstatus.SENDT.name
                    ),
                    session
                ) {
                    it.toUtbetaling(session)
                }
        }

    private fun String.oppdatering(params: Map<String, Any?>) {
        using(sessionOf(dataSource)) {
            this.oppdatering(params, it)
        }
    }
}
