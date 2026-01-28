package no.nav.su.se.bakover.database.mottaker

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.mottaker.MottakerDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerRepo
import no.nav.su.se.bakover.domain.mottaker.ReferanseType
import java.util.UUID

data class MottakerRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : MottakerRepo {

    /**
     * Skal bare være en mottaker per "behandling" + type, skal vi støtte flere må denne skrives om til å hente ut flere
     */
    override fun hentMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
    ): MottakerDomain? =
        dbMetrics.timeQuery("hentMottaker") {
            sessionFactory.withSession { session ->
                """
                select *
                from mottaker
                where referanse_type = :referanse_type
                  and referanse_id = :referanse_id
                """.trimIndent().hent(
                    mapOf(
                        "referanse_type" to mottakerIdentifikator.referanseType.name,
                        "referanse_id" to mottakerIdentifikator.referanseId,
                    ),
                    session,
                ) { rowToMottaker(it) }
            }
        }

    override fun lagreMottaker(mottaker: MottakerDomain) {
        dbMetrics.timeQuery("lagreMottaker") {
            sessionFactory.withSession { session ->
                """
                insert into mottaker (
                    id,
                    navn,
                    foedselsnummer,
                    adresse,
                    sakid,
                    referanse_type,
                    referanse_id,
                    dokument_id
                ) values (
                    :id,
                    :navn,
                    :foedselsnummer,
                    :adresse::jsonb,
                    :sakid,
                    :referanse_type,
                    :referanse_id,
                    :dokument_id
                )
                """.trimIndent().insert(
                    mapOf(
                        "id" to mottaker.id,
                        "navn" to mottaker.navn,
                        "foedselsnummer" to mottaker.foedselsnummer.toString(), // important
                        "adresse" to serialize(mottaker.adresse),
                        "sakid" to mottaker.sakId,
                        "referanse_type" to mottaker.referanseType.name,
                        "referanse_id" to mottaker.referanseId,
                        "dokument_id" to mottaker.dokumentId,
                    ),
                    session,
                )
            }
        }
    }

    override fun oppdaterMottaker(mottaker: MottakerDomain) {
        dbMetrics.timeQuery("oppdaterMottaker") {
            sessionFactory.withSession { session ->
                """
                update mottaker
                set navn = :navn,
                    foedselsnummer = :foedselsnummer,
                    adresse = :adresse::jsonb,
                    sakid = :sakid,
                    referanse_type = :referanse_type,
                    referanse_id = :referanse_id,
                    dokument_id = :dokument_id
                where id = :id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to mottaker.id,
                        "navn" to mottaker.navn,
                        "foedselsnummer" to mottaker.foedselsnummer.toString(),
                        "adresse" to serialize(mottaker.adresse),
                        "sakid" to mottaker.sakId,
                        "referanse_type" to mottaker.referanseType.name,
                        "referanse_id" to mottaker.referanseId,
                        "dokument_id" to mottaker.dokumentId,
                    ),
                    session,
                )
            }
        }
    }

    override fun slettMottaker(mottakerId: UUID) {
        dbMetrics.timeQuery("slettMottaker") {
            sessionFactory.withSession { session ->
                """
                    delete from mottaker
                    where id = :id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to mottakerId,
                    ),
                    session,
                )
            }
        }
    }

    private fun rowToMottaker(row: Row): MottakerDomain =
        MottakerDomain(
            id = row.uuid("id"),
            navn = row.string("navn"),
            foedselsnummer = Fnr(row.string("foedselsnummer")),
            adresse = deserialize(row.string("adresse")),
            sakId = row.uuid("sakid"),
            referanseId = row.uuid("referanse_id"),
            referanseType = ReferanseType.valueOf(row.string("referanse_type")),
            dokumentId = row.uuidOrNull("dokument_id"),
        )
}
