package no.nav.su.se.bakover.database.avstemming

import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.deserializeMapNullable
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.inClauseWith
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.oppdatering
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.uuid30
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.database.utbetaling.toUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Fagområde
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.toSakstype
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.time.LocalDate

internal class AvstemmingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : AvstemmingRepo {
    override fun opprettGrensesnittsavstemming(avstemming: Avstemming.Grensesnittavstemming) {
        return dbMetrics.timeQuery("opprettGrensesnittsavstemming") {
            sessionFactory.withSession { session ->
                """
            insert into avstemming (id, opprettet, fom, tom, utbetalinger, avstemmingXmlRequest, fagområde)
            values (:id, :opprettet, :fom, :tom, to_json(:utbetalinger::json), :avstemmingXmlRequest, :fagomrade)
                """.insert(
                    mapOf(
                        "id" to avstemming.id,
                        "opprettet" to avstemming.opprettet,
                        "fom" to avstemming.fraOgMed,
                        "tom" to avstemming.tilOgMed,
                        "utbetalinger" to serialize(
                            avstemming.utbetalinger
                                .map { it.id.toString() },
                        ),
                        "avstemmingXmlRequest" to avstemming.avstemmingXmlRequest,
                        "fagomrade" to avstemming.fagområde.toString(),
                    ),
                    session,
                )
            }
        }
    }

    override fun opprettKonsistensavstemming(avstemming: Avstemming.Konsistensavstemming.Ny) {
        return dbMetrics.timeQuery("opprettKonsistensavstemming") {
            sessionFactory.withSession { session ->
                """
            insert into konsistensavstemming (id, opprettet, løpendeFraOgMed, opprettetTilOgMed, utbetalinger, avstemmingXmlRequest, fagområde)
            values (:id, :opprettet, :lopendeFraOgMed, :opprettetTilOgMed, to_json(:utbetalinger::json), :avstemmingXmlRequest, :fagomrade)
                """.insert(
                    mapOf(
                        "id" to avstemming.id,
                        "opprettet" to avstemming.opprettet,
                        "lopendeFraOgMed" to avstemming.løpendeFraOgMed,
                        "opprettetTilOgMed" to avstemming.opprettetTilOgMed,
                        "utbetalinger" to serialize(
                            avstemming.løpendeUtbetalinger
                                .map { oppdrag ->
                                    mapOf(oppdrag.saksnummer to oppdrag.utbetalingslinjer.map { it.id })
                                }
                                .fold(emptyMap<Saksnummer, List<UUID30>>()) { acc, map -> acc + map },
                        ),
                        "avstemmingXmlRequest" to avstemming.avstemmingXmlRequest,
                        "fagomrade" to avstemming.fagområde.toString(),
                    ),
                    session,
                )
            }
        }
    }

    override fun hentGrensesnittsavstemming(avstemmingId: UUID30): Avstemming.Grensesnittavstemming? {
        return dbMetrics.timeQuery("hentGrensesnittsavstemming") {
            sessionFactory.withSession { session ->
                "select * from avstemming where id=:id".hent(mapOf("id" to avstemmingId), session) {
                    it.toGrensesnittsavstemming(session)
                }
            }
        }
    }

    override fun hentKonsistensavstemming(avstemmingId: UUID30): Avstemming.Konsistensavstemming.Fullført? {
        return dbMetrics.timeQuery("hentKonsistensavstemming") {
            sessionFactory.withSession { session ->
                "select * from konsistensavstemming where id=:id".hent(mapOf("id" to avstemmingId), session) {
                    it.toKonsistensavstemming(session)
                }
            }
        }
    }

    override fun oppdaterUtbetalingerEtterGrensesnittsavstemming(avstemming: Avstemming.Grensesnittavstemming) {
        dbMetrics.timeQuery("oppdaterUtbetalingerEtterGrensesnittsavstemming") {
            sessionFactory.withSession { session ->
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
    }

    override fun hentSisteGrensesnittsavstemming(fagområde: Fagområde): Avstemming.Grensesnittavstemming? {
        return dbMetrics.timeQuery("hentSisteGrensesnittsavstemming") {
            sessionFactory.withSession { session ->
                """
                select * from avstemming where fagområde = :fagomrade order by tom desc limit 1
                """.hent(
                    mapOf(
                        "fagomrade" to fagområde.toString(),
                    ),
                    session,
                ) {
                    it.toGrensesnittsavstemming(session)
                }
            }
        }
    }

    override fun hentUtbetalingerForGrensesnittsavstemming(
        fraOgMed: Tidspunkt,
        tilOgMed: Tidspunkt,
        fagområde: Fagområde,
    ): List<Utbetaling.OversendtUtbetaling> =
        dbMetrics.timeQuery("hentUtbetalingerForGrensesnittsavstemming") {
            sessionFactory.withSession { session ->
                val fraOgMedCondition = """(u.avstemmingsnøkkel ->> 'opprettet')::timestamptz >= :fom"""
                val tilOgMedCondition = """(u.avstemmingsnøkkel ->> 'opprettet')::timestamptz <= :tom"""
                """select u.*, s.saksnummer, s.type as sakstype from utbetaling u inner join sak s on s.id = u.sakId where s.type = :fagomrade and $fraOgMedCondition and $tilOgMedCondition"""
                    .hentListe(
                        mapOf(
                            "fom" to fraOgMed,
                            "tom" to tilOgMed,
                            "fagomrade" to fagområde.toSakstype().value,
                        ),
                        session,
                    ) {
                        it.toUtbetaling(session)
                    }
            }
        }

    override fun hentUtbetalingerForKonsistensavstemming(
        løpendeFraOgMed: Tidspunkt,
        opprettetTilOgMed: Tidspunkt,
        fagområde: Fagområde,
    ): List<Utbetaling.OversendtUtbetaling> {
        return dbMetrics.timeQuery("hentUtbetalingerForKonsistensavstemming") {
            sessionFactory.withSession { session ->
                """
                select distinct
                    s.saksnummer,
                    s.type as sakstype,
                    u.*
                from utbetaling u
                join utbetalingslinje ul on ul.utbetalingid = u.id
                join sak s on s.id = u.sakid
                where s.type = :fagomrade and ul.tom >= :lopendeFraOgMed
                    and (u.avstemmingsnøkkel ->> 'opprettet')::timestamptz <= :opprettetTilOgMed
                """.trimIndent()
                    .hentListe(
                        mapOf(
                            "lopendeFraOgMed" to løpendeFraOgMed.toLocalDate(zoneIdOslo),
                            "opprettetTilOgMed" to opprettetTilOgMed,
                            "fagomrade" to fagområde.toSakstype().value,
                        ),
                        session,
                    ) {
                        it.toUtbetaling(session)
                    }
            }
        }
    }

    override fun konsistensavstemmingUtførtForOgPåDato(dato: LocalDate, fagområde: Fagområde): Boolean {
        return dbMetrics.timeQuery("konsistensavstemmingUtførtForOgPåDato") {
            val opprettetOgLøpendeFraOgMed = sessionFactory.withSession { session ->
                """select opprettet, løpendeFraOgMed from konsistensavstemming where fagområde = :fagomrade""".hentListe(
                    mapOf(
                        "fagomrade" to fagområde.toString(),
                    ),
                    session,
                ) {
                    it.tidspunkt("opprettet") to it.tidspunkt("løpendeFraOgMed")
                }
            }
            opprettetOgLøpendeFraOgMed
                .map { (opprettet, løpendeFraOgMed) ->
                    opprettet.toLocalDate(zoneIdOslo) to løpendeFraOgMed.toLocalDate(zoneIdOslo)
                }.any {
                    it.first == dato && it.second == dato
                }
        }
    }

    private fun Row.toKonsistensavstemming(session: Session): Avstemming.Konsistensavstemming.Fullført {
        val id = uuid30("id")
        val opprettet = tidspunkt("opprettet")
        val løpendeFraOgMed = tidspunkt("løpendeFraOgMed")
        val oppretettTilOgMed = tidspunkt("opprettetTilOgMed")
        val avstemmingXmlRequest = stringOrNull("avstemmingXmlRequest")

        val utbetalingerPerSak: Map<Long, List<String>> = deserializeMapNullable(stringOrNull("utbetalinger")) ?: emptyMap()

        val utbetalinger = utbetalingerPerSak
            .mapKeys { Saksnummer(it.key) }
            .mapValues { utbetalingslinjeId -> utbetalingslinjeId.value.map { UUID30.fromString(it) } }
            .mapValues {
                it.value.map { UtbetalingInternalRepo.hentUtbetalingslinje(it, session)!! }
            }

        val fagområde = Fagområde.valueOf(string("fagområde"))

        return Avstemming.Konsistensavstemming.Fullført(
            id = id,
            opprettet = opprettet,
            løpendeFraOgMed = løpendeFraOgMed,
            opprettetTilOgMed = oppretettTilOgMed,
            utbetalinger = utbetalinger,
            avstemmingXmlRequest = avstemmingXmlRequest,
            fagområde = fagområde,
        )
    }

    private fun Row.toGrensesnittsavstemming(session: Session): Avstemming.Grensesnittavstemming {
        val id = uuid30("id")
        val opprettet = tidspunkt("opprettet")
        val fraOgMed = tidspunkt("fom")
        val tilOgMed = tidspunkt("tom")
        val avstemmingXmlRequest = stringOrNull("avstemmingXmlRequest")
        val fagområde = Fagområde.valueOf(string("fagområde"))

        return Avstemming.Grensesnittavstemming(
            id = id,
            opprettet = opprettet,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            utbetalinger = stringOrNull("utbetalinger")?.let {
                it.deserializeList<String>().map { utbetalingId ->
                    UtbetalingInternalRepo.hentOversendtUtbetaling(
                        UUID30(utbetalingId),
                        session,
                    )!!
                }
            }!!,
            avstemmingXmlRequest = avstemmingXmlRequest,
            fagområde = fagområde,
        )
    }
}
