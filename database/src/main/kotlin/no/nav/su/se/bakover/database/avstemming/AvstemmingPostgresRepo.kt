package no.nav.su.se.bakover.database.avstemming

import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.inClauseWith
import no.nav.su.se.bakover.database.insert
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
    private val dataSource: DataSource,
) : AvstemmingRepo {
    override fun opprettAvstemming(avstemming: Avstemming): Avstemming {
        return dataSource.withSession { session ->
            """
            insert into avstemming (id, opprettet, fom, tom, utbetalinger, avstemmingXmlRequest, type)
            values (:id, :opprettet, :fom, :tom, to_json(:utbetalinger::json), :avstemmingXmlRequest, :type)
        """.insert(
                mapOf(
                    "id" to avstemming.id,
                    "opprettet" to avstemming.opprettet,
                    "fom" to avstemming.fraOgMed,
                    "tom" to avstemming.tilOgMed,
                    "utbetalinger" to objectMapper.writeValueAsString(avstemming.utbetalinger.map { it.id.toString() }),
                    "avstemmingXmlRequest" to avstemming.avstemmingXmlRequest,
                    "type" to when (avstemming) {
                        is Avstemming.Grensesnittavstemming -> AvstemmingType.GRENSESNITT.name
                        is Avstemming.Konsistensavstemming -> AvstemmingType.KONSISTENS.name
                    },
                ),
                session,
            ).let {
                hentAvstemming(avstemming.id, session)!!
            }
        }
    }

    private fun hentAvstemming(id: UUID30, session: Session): Avstemming? =
        "select * from avstemming where id=:id".hent(mapOf("id" to id), session) {
            it.toAvstemming(session)
        }

    override fun oppdaterUtbetalingerEtterGrensesnittsavstemming(avstemming: Avstemming.Grensesnittavstemming) {
        dataSource.withSession { session ->
            """
                update utbetaling set avstemmingId=:avstemmingId where id = ANY(:in)
            """.oppdatering(
                mapOf(
                    "avstemmingId" to avstemming.id,
                    "in" to session.inClauseWith(avstemming.utbetalinger.map { it.id.toString() }),
                ),
                session,
            )
        }
    }

    override fun hentSisteGrensesnittsavstemming(): Avstemming.Grensesnittavstemming? =
        dataSource.withSession { session ->
            """
            select * from avstemming where type = '${AvstemmingType.GRENSESNITT}' order by tom desc limit 1
        """.hent(emptyMap(), session) {
                it.toAvstemming(session) as? Avstemming.Grensesnittavstemming
            }
        }

    override fun hentUtbetalingerForGrensesnittsavstemming(
        fraOgMed: Tidspunkt,
        tilOgMed: Tidspunkt,
    ): List<Utbetaling.OversendtUtbetaling> =
        dataSource.withSession { session ->
            val fraOgMedCondition = """(u.avstemmingsnøkkel ->> 'opprettet')::timestamptz >= :fom"""
            val tilOgMedCondition = """(u.avstemmingsnøkkel ->> 'opprettet')::timestamptz <= :tom"""
            """select u.*, s.saksnummer from utbetaling u inner join sak s on s.id = u.sakId where $fraOgMedCondition and $tilOgMedCondition"""
                .hentListe(
                    mapOf(
                        "fom" to fraOgMed,
                        "tom" to tilOgMed,
                    ),
                    session,
                ) {
                    it.toUtbetaling(session)
                }
        }
}

private fun Row.toAvstemming(session: Session): Avstemming {
    val id = uuid30("id")
    val opprettet = tidspunkt("opprettet")
    val fraOgMed = tidspunkt("fom")
    val tilOgMed = tidspunkt("tom")
    val utbetalinger = stringOrNull("utbetalinger")?.let { utbetalingListAsString ->
        objectMapper.readValue(utbetalingListAsString, List::class.java).map { utbetalingId ->
            UtbetalingInternalRepo.hentUtbetalingInternal(
                UUID30(utbetalingId as String),
                session,
            )!!
        }
    }!!
    val avstemmingXmlRequest = stringOrNull("avstemmingXmlRequest")

    return when (AvstemmingType.valueOf(string("type"))) {
        AvstemmingType.GRENSESNITT -> {
            Avstemming.Grensesnittavstemming(
                id = id,
                opprettet = opprettet,
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                utbetalinger = utbetalinger,
                avstemmingXmlRequest = avstemmingXmlRequest,
            )
        }
        AvstemmingType.KONSISTENS -> {
            Avstemming.Konsistensavstemming(
                id = id,
                opprettet = opprettet,
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                utbetalinger = utbetalinger,
                avstemmingXmlRequest = avstemmingXmlRequest,
            )
        }
    }
}

internal enum class AvstemmingType {
    GRENSESNITT,
    KONSISTENS
}
