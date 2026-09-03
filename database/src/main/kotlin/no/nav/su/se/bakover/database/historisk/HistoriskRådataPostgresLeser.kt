package no.nav.su.se.bakover.database.historisk

import kotliquery.queryOf
import no.nav.su.se.bakover.common.deserializeMap
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.historisk.HistoriskImport
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataLeser
import no.nav.su.se.bakover.domain.historisk.InfotrygdTabeller
import java.util.UUID

class HistoriskRådataPostgresLeser(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : HistoriskRådataLeser {

    override fun verifiserFullførtImport(importId: UUID) {
        sessionFactory.withSession { session ->
            val status = session.run(
                queryOf(
                    "SELECT status FROM historisk_import WHERE id = :id",
                    mapOf("id" to importId),
                ).map { it.string("status") }.asSingle,
            )
            checkNotNull(status) { "Historisk import $importId finnes ikke" }
            check(status == HistoriskImport.Status.FULLFØRT.name) {
                "Historisk import $importId har status $status, forventet ${HistoriskImport.Status.FULLFØRT.name}"
            }
        }
    }

    override fun hentReferansetabell(importId: UUID, tabellnavn: String): List<Map<String, String?>> {
        return dbMetrics.timeQuery("hentHistoriskReferansetabell") {
            sessionFactory.withSession { session ->
                session.run(
                    queryOf(
                        """
                        SELECT data
                        FROM historisk_import_rad
                        WHERE import_id = :import_id
                          AND tabellnavn = :tabellnavn
                        ORDER BY side, radnummer
                        """.trimIndent(),
                        mapOf("import_id" to importId, "tabellnavn" to tabellnavn),
                    ).map { deserializeMap<String, String?>(it.string("data")) }.asList,
                )
            }
        }
    }

    override fun hentStønaderBatchvis(
        importId: UUID,
        batchSize: Int,
        maksAntallRader: Int?,
        handler: (List<Map<String, String?>>) -> Boolean,
    ) {
        dbMetrics.timeQuery("hentHistoriskeStønaderBatchvis") {
            sessionFactory.withSession { session ->
                var offset = 0L
                while (true) {
                    val gjenstående = maksAntallRader?.minus(offset.toInt())
                    if (gjenstående != null && gjenstående <= 0) break
                    val grense = gjenstående?.let { minOf(batchSize, it) } ?: batchSize
                    val batch = session.run(
                        queryOf(
                            """
                            SELECT data
                            FROM historisk_import_rad
                            WHERE import_id = :import_id
                              AND tabellnavn = :tabellnavn
                            ORDER BY side, radnummer
                            LIMIT :limit OFFSET :offset
                            """.trimIndent(),
                            mapOf(
                                "import_id" to importId,
                                "tabellnavn" to STONAD_TABELL,
                                "limit" to grense,
                                "offset" to offset,
                            ),
                        ).map { deserializeMap<String, String?>(it.string("data")) }.asList,
                    )
                    if (batch.isEmpty()) break
                    if (!handler(batch)) break
                    offset += batch.size
                }
            }
        }
    }

    override fun hentVedtakForStønader(importId: UUID, stønadIder: Set<String>): List<Map<String, String?>> {
        if (stønadIder.isEmpty()) return emptyList()
        return dbMetrics.timeQuery("hentHistoriskeVedtakForStønader") {
            sessionFactory.withSession { session ->
                session.run(
                    queryOf(
                        """
                        SELECT data
                        FROM historisk_import_rad
                        WHERE import_id = :import_id
                          AND tabellnavn = :tabellnavn
                          AND data ->> 'STONAD_ID' = ANY(:stonad_ider)
                        ORDER BY side, radnummer
                        """.trimIndent(),
                        mapOf(
                            "import_id" to importId,
                            "tabellnavn" to VEDTAK_TABELL,
                            "stonad_ider" to session.connection.underlying.createArrayOf(
                                "text",
                                stønadIder.toTypedArray(),
                            ),
                        ),
                    ).map { deserializeMap<String, String?>(it.string("data")) }.asList,
                )
            }
        }
    }

    override fun hentRaderForVedtak(
        importId: UUID,
        tabellnavn: String,
        vedtakIder: Set<String>,
    ): List<Map<String, String?>> {
        if (vedtakIder.isEmpty()) return emptyList()
        return dbMetrics.timeQuery("hentHistoriskeRaderForVedtak") {
            sessionFactory.withSession { session ->
                session.run(
                    queryOf(
                        """
                        SELECT data
                        FROM historisk_import_rad
                        WHERE import_id = :import_id
                          AND tabellnavn = :tabellnavn
                          AND data ->> 'VEDTAK_ID' = ANY(:vedtak_ider)
                        ORDER BY side, radnummer
                        """.trimIndent(),
                        mapOf(
                            "import_id" to importId,
                            "tabellnavn" to tabellnavn,
                            "vedtak_ider" to session.connection.underlying.createArrayOf(
                                "text",
                                vedtakIder.toTypedArray(),
                            ),
                        ),
                    ).map { deserializeMap<String, String?>(it.string("data")) }.asList,
                )
            }
        }
    }

    override fun hentPersonerForLopenummer(importId: UUID, lopenummer: Set<String>): Map<String, Map<String, String?>> {
        if (lopenummer.isEmpty()) return emptyMap()
        return dbMetrics.timeQuery("hentHistoriskePersonerForLopenummer") {
            sessionFactory.withSession { session ->
                session.run(
                    queryOf(
                        """
                        SELECT data
                        FROM historisk_import_rad
                        WHERE import_id = :import_id
                          AND tabellnavn = :tabellnavn
                          AND data ->> 'PERSON_LOPENR' = ANY(:lopenummer)
                        ORDER BY side, radnummer
                        """.trimIndent(),
                        mapOf(
                            "import_id" to importId,
                            "tabellnavn" to InfotrygdTabeller.T_LOPENR_FNR,
                            "lopenummer" to session.connection.underlying.createArrayOf(
                                "text",
                                lopenummer.toTypedArray(),
                            ),
                        ),
                    ).map { row ->
                        deserializeMap<String, String?>(row.string("data"))
                            .entries.associate { (k, v) -> k.uppercase() to v }
                    }.asList,
                ).associateBy { it["PERSON_LOPENR"] ?: "" }.filterKeys { it.isNotEmpty() }
            }
        }
    }

    companion object {
        private val STONAD_TABELL = InfotrygdTabeller.T_STONAD
        private val VEDTAK_TABELL = InfotrygdTabeller.T_VEDTAK
    }
}
