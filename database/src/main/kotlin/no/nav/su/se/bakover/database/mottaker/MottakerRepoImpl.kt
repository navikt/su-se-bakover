package no.nav.su.se.bakover.database.mottaker

import dokument.domain.DokumentFormaal
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.mottaker.MottakerDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerFnrDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerOrgnummerDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerRepo
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import java.util.UUID

data class MottakerRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : MottakerRepo {

    /**
     * Skal bare være en mottaker per "behandling" + type, skal vi støtte flere må denne skrives om til å hente ut flere
     */
    override fun hentMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
        transactionContext: TransactionContext?,
    ): MottakerDomain? =
        dbMetrics.timeQuery("hentMottaker") {
            sessionFactory.withSession(transactionContext) { session ->
                """
                select *
                from mottaker
                where referanse_type = :referanse_type
                  and referanse_id = :referanse_id
                  and brevtype = :brevtype
                """.trimIndent().hent(
                    mapOf(
                        "referanse_type" to mottakerIdentifikator.referanseType.name,
                        "referanse_id" to mottakerIdentifikator.referanseId,
                        "brevtype" to mottakerIdentifikator.brevtype.name,
                    ),
                    session,
                ) { rowToMottaker(it) }
            }
        }

    override fun lagreMottaker(mottaker: MottakerDomain) {
        dbMetrics.timeQuery("lagreMottaker") {
            sessionFactory.withSession { session ->
                val (fnr, orgnr) = when (mottaker) {
                    is MottakerFnrDomain ->
                        mottaker.foedselsnummer.toString() to null

                    is MottakerOrgnummerDomain ->
                        null to mottaker.orgnummer
                }

                """
            insert into mottaker (
                id,
                navn,
                foedselsnummer,
                orgnummer,
                adresse,
                sakid,
                referanse_type,
                referanse_id,
                brevtype
            ) values (
                :id,
                :navn,
                :foedselsnummer,
                :orgnummer,
                :adresse::jsonb,
                :sakid,
                :referanse_type,
                :referanse_id,
                :brevtype
            )
                """.trimIndent().insert(
                    mapOf(
                        "id" to mottaker.id,
                        "navn" to mottaker.navn,
                        "foedselsnummer" to fnr,
                        "orgnummer" to orgnr,
                        "adresse" to serialize(mottaker.adresse),
                        "sakid" to mottaker.sakId,
                        "referanse_type" to mottaker.referanseType.name,
                        "referanse_id" to mottaker.referanseId,
                        "brevtype" to mottaker.brevtype.name,
                    ),
                    session,
                )
            }
        }
    }

    override fun oppdaterMottaker(mottaker: MottakerDomain) {
        dbMetrics.timeQuery("oppdaterMottaker") {
            sessionFactory.withSession { session ->
                val (fnr, orgnr) = when (mottaker) {
                    is MottakerFnrDomain ->
                        mottaker.foedselsnummer.toString() to null

                    is MottakerOrgnummerDomain ->
                        null to mottaker.orgnummer
                }

                """
            update mottaker
            set navn = :navn,
                foedselsnummer = :foedselsnummer,
                orgnummer = :orgnummer,
                adresse = :adresse::jsonb,
                sakid = :sakid,
                referanse_type = :referanse_type,
                referanse_id = :referanse_id,
                brevtype = :brevtype
            where id = :id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to mottaker.id,
                        "navn" to mottaker.navn,
                        "foedselsnummer" to fnr,
                        "orgnummer" to orgnr,
                        "adresse" to serialize(mottaker.adresse),
                        "sakid" to mottaker.sakId,
                        "referanse_type" to mottaker.referanseType.name,
                        "referanse_id" to mottaker.referanseId,
                        "brevtype" to mottaker.brevtype.name,
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

    private fun rowToMottaker(row: Row): MottakerDomain {
        val fnr = row.stringOrNull("foedselsnummer")
        val orgnr = row.stringOrNull("orgnummer")

        return when {
            fnr != null -> MottakerFnrDomain(
                id = row.uuid("id"),
                navn = row.string("navn"),
                foedselsnummer = Fnr(fnr),
                adresse = deserialize(row.string("adresse")),
                sakId = row.uuid("sakid"),
                referanseId = row.uuid("referanse_id"),
                referanseType = ReferanseTypeMottaker.valueOf(row.string("referanse_type")),
                brevtype = row.stringOrNull("brevtype")
                    ?.let { DokumentFormaal.valueOf(it) }
                    ?: DokumentFormaal.VEDTAK,
            )

            orgnr != null -> MottakerOrgnummerDomain(
                id = row.uuid("id"),
                navn = row.string("navn"),
                orgnummer = orgnr,
                adresse = deserialize(row.string("adresse")),
                sakId = row.uuid("sakid"),
                referanseId = row.uuid("referanse_id"),
                referanseType = ReferanseTypeMottaker.valueOf(row.string("referanse_type")),
                brevtype = row.stringOrNull("brevtype")
                    ?.let { DokumentFormaal.valueOf(it) }
                    ?: DokumentFormaal.VEDTAK,
            )

            else -> error(
                "Ugyldig mottaker i DB: ${row.uuid("id")} mangler både foedselsnummer og orgnummer",
            )
        }
    }
}
