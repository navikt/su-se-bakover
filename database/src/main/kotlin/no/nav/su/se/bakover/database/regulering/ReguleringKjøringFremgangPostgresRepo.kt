package no.nav.su.se.bakover.database.regulering

import kotliquery.Row
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgang
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgangRepo
import java.util.UUID

/**
 * Persisterer delvis fremgang for automatisk regulering per batch.
 *
 * Eksempler på direkte SQL-oppslag — kjør i en DB-klient når jobben krasjer:
 *
 * ```sql
 * -- Alle batcher for en kjøring, sortert kronologisk:
 * SELECT batch_nummer, tidspunkt, saker_i_batch
 * FROM regulering_kjoring_fremgang
 * WHERE kjoering_id = '<kjoering-id>'
 * ORDER BY batch_nummer;
 *
 * -- Totalt antall behandlede saker så langt:
 * SELECT
 *   COUNT(*)          AS batcher_fullført,
 *   SUM(saker_i_batch) AS saker_behandlet,
 *   MIN(tidspunkt)     AS startet,
 *   MAX(tidspunkt)     AS siste_oppdatering
 * FROM regulering_kjoring_fremgang
 * WHERE kjoering_id = '<kjoering-id>';
 *
 * -- Alle individuelle resultater (saksnummer + utfall), flatpakket fra jsonb-arrayene:
 * SELECT
 *   batch_nummer,
 *   resultat ->> 'saksnummer'  AS saksnummer,
 *   resultat ->> 'utfall'      AS utfall,
 *   resultat ->> 'beskrivelse' AS beskrivelse
 * FROM regulering_kjoring_fremgang,
 *      jsonb_array_elements(resultater) AS resultat
 * WHERE kjoering_id = '<kjoering-id>'
 * ORDER BY batch_nummer;
 *
 * -- Aggregert telling per utfall for én kjøring:
 * SELECT
 *   resultat ->> 'utfall' AS utfall,
 *   COUNT(*)              AS antall
 * FROM regulering_kjoring_fremgang,
 *      jsonb_array_elements(resultater) AS resultat
 * WHERE kjoering_id = '<kjoering-id>'
 * GROUP BY resultat ->> 'utfall'
 * ORDER BY antall DESC;
 * ```
 */
class ReguleringKjøringFremgangPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : ReguleringKjøringFremgangRepo {

    override fun lagre(fremgang: ReguleringKjøringFremgang) {
        dbMetrics.timeQuery("lagreReguleringKjøringFremgang") {
            sessionFactory.withSession { session ->
                """
                    insert into regulering_kjoring_fremgang
                        (kjoering_id, batch_nummer, tidspunkt, saker_i_batch, resultater)
                    values
                        (:kjoering_id, :batch_nummer, :tidspunkt, :saker_i_batch, to_jsonb(:resultater::jsonb))
                    on conflict (kjoering_id, batch_nummer) do nothing
                """.trimIndent().insert(
                    mapOf(
                        "kjoering_id" to fremgang.kjøringId,
                        "batch_nummer" to fremgang.batchNummer,
                        "tidspunkt" to fremgang.tidspunkt,
                        "saker_i_batch" to fremgang.sakerIBatch,
                        "resultater" to serialize(fremgang.resultater),
                    ),
                    session,
                )
            }
        }
    }

    override fun hentForKjøring(kjøringId: UUID): List<ReguleringKjøringFremgang> {
        return dbMetrics.timeQuery("hentReguleringKjøringFremgang") {
            sessionFactory.withSession { session ->
                """
                    select kjoering_id, batch_nummer, tidspunkt, saker_i_batch, resultater
                    from regulering_kjoring_fremgang
                    where kjoering_id = :kjoering_id
                    order by batch_nummer asc
                """.trimIndent().hentListe(
                    mapOf("kjoering_id" to kjøringId),
                    session,
                ) { it.toReguleringKjøringFremgang() }
            }
        }
    }
}

private fun Row.toReguleringKjøringFremgang() = ReguleringKjøringFremgang(
    kjøringId = uuid("kjoering_id"),
    batchNummer = int("batch_nummer"),
    tidspunkt = sqlTimestamp("tidspunkt").toInstant(),
    sakerIBatch = int("saker_i_batch"),
    resultater = string("resultater").deserializeList(),
)
