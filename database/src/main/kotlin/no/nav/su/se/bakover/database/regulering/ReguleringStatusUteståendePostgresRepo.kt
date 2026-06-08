package no.nav.su.se.bakover.database.regulering

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.ProdusertReguleringStatus
import no.nav.su.se.bakover.domain.regulering.ReguleringStatus
import no.nav.su.se.bakover.domain.regulering.ReguleringStatusUteståendeRepo
import java.util.UUID

class ReguleringStatusUteståendePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : ReguleringStatusUteståendeRepo {
    override fun hent(): List<ProdusertReguleringStatus> {
        return dbMetrics.timeQuery("hentReguleringStatusUtestaaende") {
            sessionFactory.withSession { session ->
                """
                select * from regulering_status_utestaaende
                order by opprettet desc
                limit 2
                """.trimIndent().hentListe(
                    params = emptyMap(),
                    session = session,
                ) { row -> row.toProdusertReguleringStatus() }
            }
        }
    }

    override fun hentPågående(): List<ProdusertReguleringStatus> {
        return dbMetrics.timeQuery("hentReguleringStatusUtestaaendePågående") {
            sessionFactory.withSession { session ->
                """
                select * from regulering_status_utestaaende where produser_status = 'Pågående'
                """.trimIndent().hentListe(
                    params = emptyMap(),
                    session = session,
                ) { row -> row.toProdusertReguleringStatus() }
            }
        }
    }

    override fun lagreOppstartet(): UUID {
        val id = UUID.randomUUID()
        dbMetrics.timeQuery("lagreReguleringStatusUteståendeOppstartet") {
            sessionFactory.withSession { session ->
                """
                    insert into regulering_status_utestaaende (id, produser_status, regulering_status)
                    values (:id, :produser_status, null)
                """.trimIndent().insert(
                    mapOf(
                        "id" to id,
                        "produser_status" to ProdusertReguleringStatus.ProduserStatus.Pågående.name,
                    ),
                    session,
                )
            }
        }
        return id
    }

    override fun lagreProdusert(
        idPågående: UUID,
        reguleringStatus: ReguleringStatus,
    ) {
        dbMetrics.timeQuery("lagreReguleringStatusUteståendeProdusert") {
            sessionFactory.withSession { session ->
                """
                    insert into regulering_status_utestaaende (id, produser_status, regulering_status)
                    values (:id, :produser_status, to_jsonb(:regulering_status::jsonb))
                    on conflict (id) do update
                    set produser_status = excluded.produser_status, regulering_status = excluded.regulering_status
                """.trimIndent().insert(
                    mapOf(
                        "id" to idPågående,
                        "produser_status" to ProdusertReguleringStatus.ProduserStatus.Fullført.name,
                        "regulering_status" to serialize(reguleringStatus),
                    ),
                    session,
                )
            }
        }
    }

    override fun lagreFeilet(idPågående: UUID) {
        dbMetrics.timeQuery("lagreReguleringStatusUteståendeFeilet") {
            sessionFactory.withSession { session ->
                """
                    update regulering_status_utestaaende
                    set produser_status = :produser_status
                    where id = :id
                """.trimIndent().insert(
                    mapOf(
                        "id" to idPågående,
                        "produser_status" to ProdusertReguleringStatus.ProduserStatus.Feilet.name,
                    ),
                    session,
                )
            }
        }
    }
}

private fun Row.toProdusertReguleringStatus(): ProdusertReguleringStatus {
    return ProdusertReguleringStatus(
        id = uuid("id"),
        produserStatus = ProdusertReguleringStatus.ProduserStatus.valueOf(string("produser_status")),
        reguleringStatus = stringOrNull("regulering_status")?.let { deserialize(it) },
    )
}
