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
import no.nav.su.se.bakover.domain.mottaker.Mottaker
import no.nav.su.se.bakover.domain.mottaker.MottakerRepo
import java.util.UUID

data class MottakerRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : MottakerRepo {
    fun hentMottaker(dokumentId: UUID, sessionContext: SessionContext? = null): Mottaker? =
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

    fun lagreMottaker(mottaker: Mottaker, dokumentId: UUID) {
        dbMetrics.timeQuery("lagreMottaker") {
            sessionFactory.withSession { session ->
                """
                    insert into mottaker (id, navn, foedselsnummer, orgnummer, adresse, dokument_id)
                    values (:id, :navn, :foedselsnummer, :orgnummer, :adresse::jsonb, :dokument_id)
                    on conflict (dokument_id) do update
                    set navn = :navn,
                        foedselsnummer = :foedselsnummer,
                        orgnummer = :orgnummer,
                        adresse = :adresse::jsonb
                """.trimIndent().insert(
                    mapOf(
                        "id" to mottaker.id,
                        "navn" to mottaker.navn,
                        "foedselsnummer" to mottaker.foedselsnummer,
                        "orgnummer" to mottaker.orgnummer?.takeIf { it.isNotBlank() },
                        "adresse" to serialize(mottaker.adresse),
                        "dokument_id" to dokumentId,
                    ),
                    session,
                )
            }
        }
    }

    fun oppdaterMottaker(mottaker: Mottaker, dokumentId: UUID) {
        dbMetrics.timeQuery("oppdaterMottaker") {
            sessionFactory.withSession { session ->
                """
                    update mottaker
                    set navn = :navn,
                        foedselsnummer = :foedselsnummer,
                        orgnummer = :orgnummer,
                        adresse = :adresse::jsonb
                    where id = :id and dokument_id = :dokument_id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to mottaker.id,
                        "navn" to mottaker.navn,
                        "foedselsnummer" to mottaker.foedselsnummer.toString(),
                        "orgnummer" to mottaker.orgnummer?.takeIf { it.isNotBlank() },
                        "adresse" to mottaker.adresse,
                        "dokument_id" to dokumentId,
                    ),
                    session,
                )
            }
        }
    }

    fun slettMottaker(mottakerId: UUID, dokumentId: UUID) {
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

    private fun rowToMottaker(row: Row) = Mottaker(
        id = row.uuid("id"),
        navn = row.string("navn"),
        foedselsnummer = row.stringOrNull("foedselsnummer")?.let { Fnr.tryCreate(it) }!!, // bad
        orgnummer = row.stringOrNull("orgnummer"),
        adresse = deserialize(row.string("adresse")),
        dokumentId = row.uuid("dokumentId"),
    )
}
