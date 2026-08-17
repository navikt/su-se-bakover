package no.nav.su.se.bakover.database.historisk

import kotliquery.Row
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.historisk.HistoriskImport
import no.nav.su.se.bakover.domain.historisk.HistoriskImportRepo
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataSide
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import java.util.UUID

class HistoriskImportPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : HistoriskImportRepo {

    override fun hentPågåendeImport(): HistoriskImport? {
        return dbMetrics.timeQuery("hentPågåendeHistoriskImport") {
            sessionFactory.withSession { session ->
                """
                    SELECT id, status
                    FROM historisk_import
                    WHERE status = 'PÅGÅR'
                """.trimIndent().hent(session = session) {
                    hentImport(it.uuid("id"), HistoriskImport.Status.valueOf(it.string("status")), session)
                }
            }
        }
    }

    override fun opprettImport(tabeller: List<NyHistoriskTabellimport>): HistoriskImport {
        require(tabeller.isNotEmpty()) { "En historisk import må inneholde minst én tabell" }
        require(tabeller.map { it.tabellnavn }.distinct().size == tabeller.size) {
            "En historisk import kan ikke inneholde duplikate tabellnavn"
        }

        return dbMetrics.timeQuery("opprettHistoriskImport") {
            sessionFactory.withTransaction { tx ->
                val importId = UUID.randomUUID()
                """
                    INSERT INTO historisk_import (id, status)
                    VALUES (:id, 'PÅGÅR')
                """.trimIndent().insert(mapOf("id" to importId), tx)

                tabeller.forEach { tabell ->
                    require(tabell.kolonner.isNotEmpty()) { "${tabell.tabellnavn} mangler kolonner" }
                    require(tabell.kolonner.distinct().size == tabell.kolonner.size) {
                        "${tabell.tabellnavn} inneholder duplikate kolonnenavn"
                    }
                    """
                        INSERT INTO historisk_import_tabell (
                            import_id,
                            tabellnavn,
                            status,
                            forventet_antall,
                            kolonner
                        ) VALUES (
                            :import_id,
                            :tabellnavn,
                            :status,
                            :forventet_antall,
                            to_jsonb(:kolonner::jsonb)
                        )
                    """.trimIndent().insert(
                        mapOf(
                            "import_id" to importId,
                            "tabellnavn" to tabell.tabellnavn,
                            "status" to if (tabell.forventetAntall == 0L) {
                                HistoriskImport.Status.FULLFØRT.name
                            } else {
                                HistoriskImport.Status.PÅGÅR.name
                            },
                            "forventet_antall" to tabell.forventetAntall,
                            "kolonner" to serialize(tabell.kolonner),
                        ),
                        tx,
                    )
                }
                hentImport(importId, HistoriskImport.Status.PÅGÅR, tx)
            }
        }
    }

    override fun lagreSide(side: HistoriskRådataSide): HistoriskImport.Tabell {
        return dbMetrics.timeQuery("lagreHistoriskImportside") {
            sessionFactory.withTransaction { tx ->
                val tabell = hentTabellForOppdatering(side.importId, side.tabellnavn, tx)
                    ?: throw IllegalArgumentException(
                        "Fant ikke tabell ${side.tabellnavn} for historisk import ${side.importId}",
                    )
                require(tabell.status == HistoriskImport.Status.PÅGÅR) {
                    "Kan ikke lagre side for ${side.tabellnavn} med status ${tabell.status}"
                }
                require(tabell.nesteSide == side.side) {
                    "Forventet side ${tabell.nesteSide} for ${side.tabellnavn}, men mottok ${side.side}"
                }

                val nyttAntall = tabell.importertAntall + side.rader.size
                require(nyttAntall <= tabell.forventetAntall) {
                    "Importert antall $nyttAntall overstiger forventet antall ${tabell.forventetAntall} " +
                        "for ${side.tabellnavn}"
                }
                val erSisteSide = side.nesteIterator.isNullOrBlank()
                require(!erSisteSide || nyttAntall == tabell.forventetAntall) {
                    "Siste side for ${side.tabellnavn} ga $nyttAntall rader, forventet ${tabell.forventetAntall}"
                }

                if (side.rader.isNotEmpty()) {
                    """
                        INSERT INTO historisk_import_rad (
                            import_id,
                            tabellnavn,
                            side,
                            radnummer,
                            data
                        )
                        SELECT
                            :import_id,
                            :tabellnavn,
                            :side,
                            (radnummer - 1)::integer,
                            data
                        FROM jsonb_array_elements(:rader::jsonb) WITH ORDINALITY AS rad(data, radnummer)
                        ON CONFLICT (import_id, tabellnavn, side, radnummer) DO NOTHING
                    """.trimIndent().insert(
                        mapOf(
                            "import_id" to side.importId,
                            "tabellnavn" to side.tabellnavn,
                            "side" to side.side,
                            "rader" to serialize(side.rader),
                        ),
                        tx,
                    )
                }

                val oppdaterteRader = """
                    UPDATE historisk_import_tabell
                    SET status = :status,
                        importert_antall = :importert_antall,
                        neste_iterator = :neste_iterator,
                        neste_side = neste_side + 1
                    WHERE import_id = :import_id
                      AND tabellnavn = :tabellnavn
                      AND status = 'PÅGÅR'
                      AND neste_side = :side
                """.trimIndent().oppdatering(
                    mapOf(
                        "status" to if (erSisteSide) {
                            HistoriskImport.Status.FULLFØRT.name
                        } else {
                            HistoriskImport.Status.PÅGÅR.name
                        },
                        "importert_antall" to nyttAntall,
                        "neste_iterator" to side.nesteIterator?.takeUnless { it.isBlank() },
                        "import_id" to side.importId,
                        "tabellnavn" to side.tabellnavn,
                        "side" to side.side,
                    ),
                    tx,
                )
                check(oppdaterteRader == 1) {
                    "Checkpoint for ${side.tabellnavn} side ${side.side} ble ikke oppdatert"
                }

                tabell.copy(
                    status = if (erSisteSide) {
                        HistoriskImport.Status.FULLFØRT
                    } else {
                        HistoriskImport.Status.PÅGÅR
                    },
                    importertAntall = nyttAntall,
                    nesteIterator = side.nesteIterator?.takeUnless { it.isBlank() },
                    nesteSide = side.side + 1,
                )
            }
        }
    }

    override fun fullførImport(importId: UUID) {
        dbMetrics.timeQuery("fullførHistoriskImport") {
            sessionFactory.withSession { session ->
                val oppdaterteRader = """
                    UPDATE historisk_import
                    SET status = 'FULLFØRT',
                        fullført = NOW(),
                        feilbeskrivelse = NULL
                    WHERE id = :id
                      AND status = 'PÅGÅR'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM historisk_import_tabell
                          WHERE import_id = :id
                            AND status <> 'FULLFØRT'
                      )
                """.trimIndent().oppdatering(mapOf("id" to importId), session)
                check(oppdaterteRader == 1) {
                    "Historisk import $importId kunne ikke fullføres fordi én eller flere tabeller ikke er fullført"
                }
            }
        }
    }

    override fun markerFeilet(importId: UUID, beskrivelse: String) {
        dbMetrics.timeQuery("markerHistoriskImportFeilet") {
            sessionFactory.withTransaction { tx ->
                """
                    UPDATE historisk_import_tabell
                    SET status = 'FEILET'
                    WHERE import_id = :id
                      AND status = 'PÅGÅR'
                """.trimIndent().oppdatering(mapOf("id" to importId), tx)
                """
                    UPDATE historisk_import
                    SET status = 'FEILET',
                        feilbeskrivelse = :feilbeskrivelse
                    WHERE id = :id
                      AND status = 'PÅGÅR'
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to importId,
                        "feilbeskrivelse" to beskrivelse.take(1000),
                    ),
                    tx,
                )
            }
        }
    }

    private fun hentImport(
        importId: UUID,
        status: HistoriskImport.Status,
        session: Session,
    ): HistoriskImport {
        return HistoriskImport(
            id = importId,
            status = status,
            tabeller = """
                SELECT *
                FROM historisk_import_tabell
                WHERE import_id = :import_id
                ORDER BY tabellnavn
            """.trimIndent().hentListe(mapOf("import_id" to importId), session) { it.toHistoriskImportTabell() },
        )
    }

    private fun hentTabellForOppdatering(
        importId: UUID,
        tabellnavn: String,
        session: Session,
    ): HistoriskImport.Tabell? {
        return """
            SELECT *
            FROM historisk_import_tabell
            WHERE import_id = :import_id
              AND tabellnavn = :tabellnavn
            FOR UPDATE
        """.trimIndent().hent(
            mapOf(
                "import_id" to importId,
                "tabellnavn" to tabellnavn,
            ),
            session,
        ) { it.toHistoriskImportTabell() }
    }
}

private fun Row.toHistoriskImportTabell() = HistoriskImport.Tabell(
    tabellnavn = string("tabellnavn"),
    status = HistoriskImport.Status.valueOf(string("status")),
    forventetAntall = long("forventet_antall"),
    importertAntall = long("importert_antall"),
    nesteIterator = stringOrNull("neste_iterator"),
    nesteSide = long("neste_side"),
    kolonner = string("kolonner").deserializeList(),
)
