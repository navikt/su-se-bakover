package no.nav.su.se.bakover.database.avstemming

import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.readMap
import no.nav.su.se.bakover.common.zoneIdOslo
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
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import java.time.LocalDate
import javax.sql.DataSource

internal class AvstemmingPostgresRepo(
    private val dataSource: DataSource,
) : AvstemmingRepo {
    override fun opprettGrensesnittsavstemming(avstemming: Avstemming.Grensesnittavstemming) {
        return dataSource.withSession { session ->
            """
            insert into avstemming (id, opprettet, fom, tom, utbetalinger, avstemmingXmlRequest)
            values (:id, :opprettet, :fom, :tom, to_json(:utbetalinger::json), :avstemmingXmlRequest)
        """.insert(
                mapOf(
                    "id" to avstemming.id,
                    "opprettet" to avstemming.opprettet,
                    "fom" to avstemming.fraOgMed,
                    "tom" to avstemming.tilOgMed,
                    "utbetalinger" to objectMapper.writeValueAsString(
                        avstemming.utbetalinger
                            .map { it.id.toString() },
                    ),
                    "avstemmingXmlRequest" to avstemming.avstemmingXmlRequest,
                ),
                session,
            )
        }
    }

    override fun opprettKonsistensavstemming(avstemming: Avstemming.Konsistensavstemming.Ny) {
        return dataSource.withSession { session ->
            """
            insert into konsistensavstemming (id, opprettet, løpendeFraOgMed, opprettetTilOgMed, utbetalinger, avstemmingXmlRequest)
            values (:id, :opprettet, :lopendeFraOgMed, :opprettetTilOgMed, to_json(:utbetalinger::json), :avstemmingXmlRequest)
        """.insert(
                mapOf(
                    "id" to avstemming.id,
                    "opprettet" to avstemming.opprettet,
                    "lopendeFraOgMed" to avstemming.løpendeFraOgMed,
                    "opprettetTilOgMed" to avstemming.opprettetTilOgMed,
                    "utbetalinger" to objectMapper.writeValueAsString(
                        avstemming.løpendeUtbetalinger
                            .map { oppdrag ->
                                mapOf(oppdrag.saksnummer to oppdrag.utbetalingslinjer.map { it.id })
                            }
                            .fold(emptyMap<Saksnummer, List<UUID30>>()) { acc, map -> acc + map },
                    ),
                    "avstemmingXmlRequest" to avstemming.avstemmingXmlRequest,
                ),
                session,
            )
        }
    }

    override fun hentGrensesnittsavstemming(avstemmingId: UUID30): Avstemming.Grensesnittavstemming? {
        return dataSource.withSession { session ->
            "select * from avstemming where id=:id".hent(mapOf("id" to avstemmingId), session) {
                it.toGrensesnittsavstemming(session)
            }
        }
    }

    override fun hentKonsistensavstemming(avstemmingId: UUID30): Avstemming.Konsistensavstemming.Fullført? {
        return dataSource.withSession { session ->
            "select * from konsistensavstemming where id=:id".hent(mapOf("id" to avstemmingId), session) {
                it.toKonsistensavstemming(session)
            }
        }
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
            select * from avstemming order by tom desc limit 1
        """.hent(emptyMap(), session) {
                it.toGrensesnittsavstemming(session)
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

    override fun hentUtbetalingerForKonsistensavstemming(
        løpendeFraOgMed: Tidspunkt,
        opprettetTilOgMed: Tidspunkt,
    ): List<Utbetaling.OversendtUtbetaling> {
        return dataSource.withSession { session ->
            """
                select distinct
                    s.saksnummer,
                    u.*
                from utbetaling u    
                join utbetalingslinje ul on ul.utbetalingid = u.id
                join sak s on s.id = u.sakid
                where ul.tom >= :lopendeFraOgMed
                    and (u.avstemmingsnøkkel ->> 'opprettet')::timestamptz <= :opprettetTilOgMed
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "lopendeFraOgMed" to løpendeFraOgMed.toLocalDate(zoneIdOslo),
                        "opprettetTilOgMed" to opprettetTilOgMed,
                    ),
                    session,
                ) {
                    it.toUtbetaling(session)
                }
        }
    }

    override fun konsistensavstemmingUtførtForOgPåDato(dato: LocalDate): Boolean {
        val opprettetOgLøpendeFraOgMed = dataSource.withSession { session ->
            """select opprettet, løpendeFraOgMed from konsistensavstemming""".hentListe(emptyMap(), session) {
                it.tidspunkt("opprettet") to it.tidspunkt("løpendeFraOgMed")
            }
        }
        return opprettetOgLøpendeFraOgMed
            .map { (opprettet, løpendeFraOgMed) ->
                opprettet.toLocalDate(zoneIdOslo) to løpendeFraOgMed.toLocalDate(zoneIdOslo)
            }.any {
                it.first == dato && it.second == dato
            }
    }

    private fun Row.toKonsistensavstemming(session: Session): Avstemming.Konsistensavstemming.Fullført {
        val id = uuid30("id")
        val opprettet = tidspunkt("opprettet")
        val løpendeFraOgMed = tidspunkt("løpendeFraOgMed")
        val oppretettTilOgMed = tidspunkt("opprettetTilOgMed")
        val avstemmingXmlRequest = stringOrNull("avstemmingXmlRequest")

        val utbetalingerPerSak: Map<Long, List<String>> = stringOrNull("utbetalinger")
            ?.let { objectMapper.readMap(it) } ?: emptyMap()

        val utbetalinger = utbetalingerPerSak
            .mapKeys { Saksnummer(it.key) }
            .mapValues { utbetalingslinjeId -> utbetalingslinjeId.value.map { UUID30.fromString(it) } }
            .mapValues {
                it.value.map { UtbetalingInternalRepo.hentUtbetalingslinje(it, session)!! }
            }

        return Avstemming.Konsistensavstemming.Fullført(
            id = id,
            opprettet = opprettet,
            løpendeFraOgMed = løpendeFraOgMed,
            opprettetTilOgMed = oppretettTilOgMed,
            utbetalinger = utbetalinger,
            avstemmingXmlRequest = avstemmingXmlRequest,
        )
    }

    private fun Row.toGrensesnittsavstemming(session: Session): Avstemming.Grensesnittavstemming {
        val id = uuid30("id")
        val opprettet = tidspunkt("opprettet")
        val fraOgMed = tidspunkt("fom")
        val tilOgMed = tidspunkt("tom")
        val avstemmingXmlRequest = stringOrNull("avstemmingXmlRequest")

        return Avstemming.Grensesnittavstemming(
            id = id,
            opprettet = opprettet,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            utbetalinger = stringOrNull("utbetalinger")?.let {
                it.deserializeList<String>().map { utbetalingId ->
                    UtbetalingInternalRepo.hentUtbetalingInternal(
                        UUID30(utbetalingId),
                        session,
                    )!!
                }
            }!!,
            avstemmingXmlRequest = avstemmingXmlRequest,
        )
    }
}
