package no.nav.su.se.bakover.database.mottaker

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.mottaker.MottakerDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerRepo
import java.util.UUID

// TODO: tester for disse
data class MottakerRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : MottakerRepo {
    override fun hentMottaker(dokumentId: UUID, sessionContext: SessionContext?): MottakerDomain? =
        dbMetrics.timeQuery("hentMottaker") {
            sessionFactory.withSession(sessionContext) { session ->
                """
                select * from mottaker where dokument_id = :dokument_id
                """.trimIndent().hent(
                    mapOf("dokument_id" to dokumentId),
                    session,
                ) { rowToMottaker(it) }
            }
        }

    override fun lagreMottaker(mottaker: MottakerDomain, dokumentId: UUID) {
        dbMetrics.timeQuery("lagreMottaker") {
            sessionFactory.withSession { session ->
                """
                    insert into mottaker (id, navn, foedselsnummer, adresse, dokument_id)
                    values (:id, :navn, :foedselsnummer, :adresse::jsonb, :dokument_id)
                """.trimIndent().insert(
                    mapOf(
                        "id" to mottaker.id,
                        "navn" to mottaker.navn,
                        "foedselsnummer" to mottaker.foedselsnummer,
                        "adresse" to serialize(mottaker.adresse),
                        "dokument_id" to dokumentId,
                    ),
                    session,
                )
            }
        }
    }

    override fun oppdaterMottaker(mottaker: MottakerDomain, dokumentId: UUID) {
        dbMetrics.timeQuery("oppdaterMottaker") {
            sessionFactory.withSession { session ->
                """
                    update mottaker
                    set navn = :navn,
                        foedselsnummer = :foedselsnummer,
                        adresse = :adresse::jsonb
                    where id = :id and dokument_id = :dokument_id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to mottaker.id,
                        "navn" to mottaker.navn,
                        "foedselsnummer" to mottaker.foedselsnummer.toString(),
                        "adresse" to mottaker.adresse,
                        "dokument_id" to dokumentId,
                    ),
                    session,
                )
            }
        }
    }

    override fun slettMottaker(mottakerId: UUID, dokumentId: UUID) {
        dbMetrics.timeQuery("slettMottaker") {
            sessionFactory.withSession { session ->
                """
                    delete from mottaker
                    where id = :id and dokument_id = :dokument_id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to mottakerId,
                        "dokument_id" to dokumentId,
                    ),
                    session,
                )
            }
        }
    }

    private fun rowToMottaker(row: Row) = MottakerDomain(
        id = row.uuid("id"),
        navn = row.string("navn"),
        foedselsnummer = row.stringOrNull("foedselsnummer")?.let { Fnr.tryCreate(it) }!!, // bad
        adresse = deserialize(row.string("adresse")),
        dokumentId = row.uuid("dokumentId"),
    )
}
